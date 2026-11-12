
package com.pocketai.assistant

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.google.ai.edge.litertlm.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class WorkspaceDocument(
    val name: String,
    val file: File
)

@Composable
fun FileWorkspace(
    conversation: Conversation?,
    isGenerating: Boolean,
    onGeneratingChange: (Boolean) -> Unit,
    onStatus: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var document by remember {
        mutableStateOf<WorkspaceDocument?>(null)
    }

    var instruction by remember {
        mutableStateOf("")
    }

    var generatedContent by remember {
        mutableStateOf<String?>(null)
    }

    var showPreview by remember {
        mutableStateOf(false)
    }

    val allowedExtensions = setOf(
        "kt", "java", "py", "js", "ts",
        "html", "css", "json", "xml",
        "md", "txt", "gradle", "kts",
        "yaml", "yml", "properties",
        "sh", "sql", "c", "cpp", "h"
    )

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->

        if (uri != null) {
            scope.launch {
                try {
                    onStatus("Importing file...")

                    val imported = withContext(Dispatchers.IO) {
                        val resolver = context.contentResolver

                        val name = resolver.query(
                            uri,
                            arrayOf(OpenableColumns.DISPLAY_NAME),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                cursor.getString(0)
                            } else null
                        } ?: error("Cannot determine filename")

                        val extension =
                            name.substringAfterLast(
                                ".",
                                ""
                            ).lowercase()

                        require(extension in allowedExtensions) {
                            "Unsupported file type: .$extension"
                        }

                        val directory = File(
                            context.filesDir,
                            "workspace"
                        )

                        check(directory.exists() || directory.mkdirs()) {
                            "Cannot create workspace"
                        }

                        val safeName = name.replace(
                            Regex("[^a-zA-Z0-9._-]"),
                            "_"
                        )

                        val destination = File(
                            directory,
                            safeName
                        )

                        val temporary = File(
                            directory,
                            "$safeName.tmp"
                        )

                        resolver.openInputStream(uri)?.use { input ->
                            temporary.outputStream().use { output ->
                                input.copyTo(output, 8192)
                            }
                        } ?: error("Cannot read selected file")

                        require(temporary.length() <= 256 * 1024) {
                            "File is too large (maximum 256 KB)"
                        }

                        val content = temporary.readText(Charsets.UTF_8)

                        require(!content.contains('\u0000')) {
                            "Binary files are not supported"
                        }

                        require(content.length <= 40000) {
                            "File is too large for the current AI editing prompt"
                        }

                        temporary.renameTo(destination).also { success ->
                            if (!success) {
                                temporary.delete()
                                error("Cannot save workspace file")
                            }
                        }

                        WorkspaceDocument(name, destination)
                    }

                    document = imported
                    generatedContent = null
                    instruction = ""

                    onStatus("File imported: ${imported.name}")

                } catch (e: Exception) {
                    onStatus("File import error: ${e.message}")
                }
            }
        }
    }

    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        val selected = document

        if (uri != null && selected != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver
                            .openOutputStream(uri, "w")
                            ?.use { output ->
                                selected.file.inputStream().use { input ->
                                    input.copyTo(output)
                                }
                            } ?: error("Cannot write exported file")
                    }

                    onStatus("Export successful: ${selected.name}")

                } catch (e: Exception) {
                    onStatus("Export error: ${e.message}")
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "File Workspace",
                style = MaterialTheme.typography.titleMedium
            )

            Button(
                onClick = {
                    filePicker.launch(arrayOf("*/*"))
                },
                enabled = !isGenerating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("＋ Attach File")
            }

            document?.let { selected ->
                Text(
                    "Selected: ${selected.name}",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Editing instructions") },
                    placeholder = {
                        Text("Example: Fix bugs and improve readability")
                    },
                    minLines = 2,
                    maxLines = 4
                )

                Button(
                    onClick = {
                        val activeConversation = conversation
                        val selectedDocument = document

                        if (
                            activeConversation != null &&
                            selectedDocument != null &&
                            instruction.isNotBlank() &&
                            !isGenerating
                        ) {
                            scope.launch {
                                onGeneratingChange(true)
                                onStatus("AI is editing ${selectedDocument.name}...")

                                try {
                                    val source = withContext(Dispatchers.IO) {
                                        selectedDocument.file.readText(
                                            Charsets.UTF_8
                                        )
                                    }

                                    val prompt = """
You are PocketAI, a file editing assistant.

Modify the provided file according to the user's instructions.

STRICT RULES:
- Return the COMPLETE modified file.
- Return only the file contents.
- Do not use Markdown code fences.
- Do not add explanations before or after the file.
- Preserve existing functionality unless instructed otherwise.
- Do not omit unchanged sections.
- Do not invent missing file contents.

FILENAME:
${selectedDocument.name}

USER INSTRUCTIONS:
${instruction}

ORIGINAL FILE:
<file>
$source
</file>

Return the complete modified file now.
""".trimIndent()

                                    var result = ""

                                    activeConversation
                                        .sendMessageAsync(
                                            prompt,
                                            maxOutputToken = 512
                                        )
                                        .collect { chunk ->
                                            result += chunk.toString()
                                            onStatus(
                                                "Generating edit... ${result.length} characters"
                                            )
                                        }

                                    require(result.isNotBlank()) {
                                        "The model returned an empty edit"
                                    }

                                    result = result.trim()

                                    if (result.startsWith("```")) {
                                        result = result.substringAfter('\n')
                                        if (result.endsWith("```")) {
                                            result = result.dropLast(3).trimEnd()
                                        }
                                    }

                                    generatedContent = result
                                    showPreview = true

                                    onStatus("Edit ready for review")

                                } catch (e: Exception) {
                                    onStatus("Editing error: ${e.message}")
                                } finally {
                                    onGeneratingChange(false)
                                }
                            }
                        }
                    },
                    enabled = conversation != null &&
                        instruction.isNotBlank() &&
                        !isGenerating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isGenerating) "Generating..."
                        else "Generate File Edit"
                    )
                }

                                OutlinedButton(
                    onClick = {
                        document = null
                        generatedContent = null
                        onStatus("Workspace file detached")
                    },
                    enabled = !isGenerating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Detach File")
                }

                OutlinedButton(
                    onClick = {
                        val selected = document
                        if (selected != null) {
                            exportPicker.launch(selected.name)
                        }
                    },
                    enabled = !isGenerating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Export File")
                }

                OutlinedButton(
                    onClick = {
                        document = null
                        generatedContent = null
                        onStatus("Workspace file removed")
                    },
                    enabled = !isGenerating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Remove File")
                }
} ?: Text(
                "No file attached",
                style = MaterialTheme.typography.bodySmall
            )

        }
    }

    if (showPreview && generatedContent != null) {
        AlertDialog(
            onDismissRequest = {
                showPreview = false
            },
            title = {
                Text("Review File Changes")
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Preview of the generated file. Original workspace file has not been changed yet."
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        generatedContent ?: "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = document
                        val content = generatedContent

                        if (selected != null && content != null) {
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        val target = selected.file

                                        val backup = File(
                                            target.parentFile,
                                            "${target.name}.bak"
                                        )

                                        target.copyTo(
                                            backup,
                                            overwrite = true
                                        )

                                        val temporary = File(
                                            target.parentFile,
                                            "${target.name}.new"
                                        )

                                        temporary.writeText(
                                            content,
                                            Charsets.UTF_8
                                        )

                                        if (!temporary.renameTo(target)) {
                                            temporary.delete()
                                            error("Could not apply file changes")
                                        }
                                    }

                                    showPreview = false
                                    generatedContent = null
                                    onStatus(
                                        "File updated: ${selected.name}. Backup saved."
                                    )

                                } catch (e: Exception) {
                                    onStatus("Save error: ${e.message}")
                                }
                            }
                        }
                    }
                ) {
                    Text("Apply Changes")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPreview = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

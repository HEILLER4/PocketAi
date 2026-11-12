
package com.pocketai.assistant

import android.os.Bundle
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.runtime.DisposableEffect
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect

data class Message(val text: String, val user: Boolean)

private data class PocketRuntime(
    val engine: Engine,
    val conversation: Conversation
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PocketAI() }
    }
}

@Composable
fun PocketAI() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val prefs = remember {
        context.getSharedPreferences("pocketai", 0)
    }

    val messages = remember {
        mutableStateListOf(
            Message("Hello! Welcome to PocketAI. How can I help you?", false)
        )
    }

    var input by remember { mutableStateOf("") }
    var modelName by remember {
        mutableStateOf(prefs.getString("model_name", null))
    }
    var modelPath by remember {
        mutableStateOf(prefs.getString("model_path", null))
    }
    var status by remember { mutableStateOf("No model selected") }
    var copying by remember { mutableStateOf(false) }
    var loadingModel by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
var runtime by remember {
    mutableStateOf<PocketRuntime?>(null)
}

DisposableEffect(runtime) {
    val activeRuntime = runtime

    onDispose {
        if (activeRuntime != null) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching { activeRuntime.conversation.close() }
                runCatching { activeRuntime.engine.close() }
            }
        }
    }
}
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->

        if (uri != null) {
            scope.launch {
                copying = true
                status = "Copying model..."

                try {
                    val result = withContext(Dispatchers.IO) {
                        val resolver = context.contentResolver

                        val displayName = resolver.query(
                            uri,
                            arrayOf(OpenableColumns.DISPLAY_NAME),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                cursor.getString(0)
                            } else null
                        } ?: "model.litertlm"

                        require(
                            displayName.endsWith(
                                ".litertlm",
                                ignoreCase = true
                            )
                        ) {
                            "Please select a .litertlm model file."
                        }

                        val directory = File(
                            context.filesDir,
                            "models"
                        )

                        if (!directory.exists()) {
                            directory.mkdirs()
                        }

                        val destination = File(
                            directory,
                            "model.litertlm"
                        )

                        val temporary = File(
                            directory,
                            "model.litertlm.tmp"
                        )

                        val inputStream =
                            resolver.openInputStream(uri)
                                ?: error("Cannot open selected file")

                        inputStream.use { input ->
                            temporary.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        if (destination.exists()) {
                            destination.delete()
                        }

                        if (!temporary.renameTo(destination)) {
                            temporary.copyTo(
                                destination,
                                overwrite = true
                            )
                            temporary.delete()
                        }

                        Triple(
                            displayName,
                            destination.absolutePath,
                            destination.length()
                        )
                    }

                    modelName = result.first
                    modelPath = result.second

                    prefs.edit()
                        .putString("model_name", result.first)
                        .putString("model_path", result.second)
                        .apply()

                    val sizeMB = result.third / (1024.0 * 1024.0)

                    status = "Model copied successfully (${String.format("%.1f", sizeMB)} MB)"

                } catch (e: Exception) {
                    status = "Error: ${e.message ?: "Could not copy model"}"
                } finally {
                    copying = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF315FCB),
            background = Color(0xFFF7F8FC)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F8FC))
                .padding(12.dp)
        ) {
            Text(
                "✦ PocketAI",
                fontSize = 25.sp,
                color = Color(0xFF315FCB)
            )

            Text("Your personal AI assistant")

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "AI Model",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        modelName ?: "No model selected",
                        color = Color.DarkGray
                    )

                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status.startsWith("Error:"))
                            MaterialTheme.colorScheme.error
                        else Color.Gray
                    )

                    Button(
                        onClick = {
                            picker.launch(arrayOf("*/*"))
                        },
                        enabled = !copying && runtime == null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (copying) "Copying model..."
                            else "Choose .litertlm Model"
                        )
                    }

                    if (copying) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
		  Button(
    onClick = {
        val selectedPath = modelPath

        if (selectedPath != null) {
            scope.launch {
                loadingModel = true
                status = "Initializing CPU engine..."

                try {
                    val loadedRuntime = withContext(Dispatchers.IO) {
                        val file = File(selectedPath)

                        require(file.isFile && file.length() > 0) {
                            "Model file is missing or empty."
                        }

                        val newEngine = Engine(
                            EngineConfig(
                                modelPath = selectedPath,
                                backend = Backend.CPU(),
                                cacheDir = context.cacheDir.absolutePath
                            )
                        )

                        try {
                            newEngine.initialize()

                            val newConversation =
                                newEngine.createConversation()

                            PocketRuntime(
                                newEngine,
                                newConversation
                            )
                        } catch (e: Exception) {
                            runCatching { newEngine.close() }
                            throw e
                        }
                    }

                    runtime = loadedRuntime
                    status = "AI engine ready! (CPU)"

                } catch (e: Exception) {
                    status = "Error loading model: ${e.message}"
                } finally {
                    loadingModel = false
                }
            }
        }
    },
    enabled = modelPath != null &&
        !loadingModel &&
        runtime == null,
    modifier = Modifier.fillMaxWidth()
) {
    Text(
        when {
            loadingModel -> "Loading AI Model..."
            runtime != null -> "AI Model Ready"
            else -> "Load AI Model (CPU)"
        }
    )
}

if (loadingModel) {
    LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth()
    )
}
                }
            }
            FileWorkspace(
                conversation = runtime?.conversation,
                isGenerating = generating,
                onGeneratingChange = { generating = it },
                onStatus = { status = it }
            )
            Spacer(Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { message ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            if (message.user) Arrangement.End
                            else Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (message.user)
                                Color(0xFF315FCB)
                            else Color.White
                        ) {
                            Text(
                                message.text,
                                modifier = Modifier.padding(14.dp),
                                color = if (message.user)
                                    Color.White
                                else Color.DarkGray
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask something...") }
                )

                Spacer(Modifier.width(8.dp))

                Button(
    onClick = {
        val question = input.trim()
        val activeRuntime = runtime

        if (question.isNotEmpty() &&
            activeRuntime != null &&
            !generating
        ) {
            messages.add(Message(question, true))
            val responseIndex = messages.size

            messages.add(Message("Thinking...", false))
            input = ""
            generating = true
            status = "Generating response..."

            scope.launch {
                try {
                    var response = ""
                    val formattedPrompt = """You are PocketAI, a helpful and concise AI assistant.

Response rules:
- Answer directly and clearly.
- Keep answers short, usually 2-5 sentences.
- Use simple language.
- Avoid unnecessary explanations.
- Use bullet points only when helpful.
- For technical questions, give the essential steps.
- Never stop in the middle of a sentence.
- If a question requires detail, prioritize the most important information.
- Do not repeat the user's question.
: $question"""
                    activeRuntime.conversation
                        .sendMessageAsync(
                            formattedPrompt,
                            maxOutputToken = 512
                        )
                        .collect { chunk ->
                            response += chunk.toString()

                            messages[responseIndex] =
                                Message(response, false)
                        }

                    if (response.isBlank()) {
                        messages[responseIndex] =
                            Message("The model returned an empty response.", false)
                    }

                    status = "AI engine ready! (CPU)"

                } catch (e: Exception) {
                    messages[responseIndex] = Message(
                        "Generation error: ${e.message}",
                        false
                    )
                    status = "Generation failed"
                } finally {
                    generating = false
                }
            }
        }
    },
    enabled = input.isNotBlank() &&
        !generating &&
        runtime != null
) {
    Text(if (generating) "..." else "Send")
}
            }
        }
    }
}


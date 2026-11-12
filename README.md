# PocketAI

PocketAI is an experimental Android AI assistant designed to run language models locally using Google's LiteRT-LM runtime.

The goal is to provide a lightweight, private, on-device AI experience without requiring a cloud-based AI service.

## Features

- Local AI inference using LiteRT-LM
- CPU backend
- `.litertlm` model selection
- Streaming AI responses
- File workspace for importing and editing source files
- Preview generated changes before applying them
- Export edited files

## Requirements

- Android 8.0 (API 26) or newer
- Compatible `.litertlm` model
- Sufficient RAM and storage
- JDK 17 and Android SDK for development

## Installation

1. Download the APK from GitHub Releases.
2. Install it on your Android device.
3. Obtain a compatible model separately.
4. Open PocketAI and select your model.
5. Load the model and start chatting.

Model files are not included. Follow their respective licenses.

## Building

Run `./gradlew assembleDebug` to build the debug APK.

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Current Limitations

- CPU inference only.
- Performance depends on device hardware and model size.
- File editing is experimental.
- Always review generated changes before applying them.
- Imported files are copied into PocketAI's private workspace.
- Editing does not automatically modify the original files.
- Model compatibility depends on LiteRT-LM.

## Privacy

Inference is intended to run locally.

Imported files and selected model copies are stored in application-private storage. Review the implementation before using sensitive data.

## License

Licensed under the MIT License. See the LICENSE file.

## Project Status

- Version: 0.1.0-beta
- Status: Experimental / Active Development

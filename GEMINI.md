# Wasm - Project Instructions

## Tech Stack
- **Platform:** Android
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3 Expressive)
- **Database:** Room (SQLite)
- **Preferences:** Jetpack DataStore
- **Parser:** Regex-based WhatsApp .txt parser

## Architecture
- **Pattern:** MVVM (Model-View-ViewModel)
- **Data Flow:** Unidirectional Data Flow (UDF)

## Build Mandates
- **No Local Builds:** All builds must be performed via GitHub Actions.
- **Memory Limit:** Host machine is limited to 4 GB RAM. Avoid heavy IDE processes.

## Coding Standards
- **Surgical Precision:** Modify code with minimal impact.
- **Documentation:** Heavily comment all logic, especially Regex and UI modifiers.

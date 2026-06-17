# Version History - Wasm

## [0.1.0] - 2026-06-17
### Added
- Initial project scaffolding for Wasm (com.mrdartsidetm.wasm).
- Room database integration for offline-first chat storage.
- Jetpack DataStore for user identity persistence.
- WhatsApp chat parser with Regex support for multi-line messages.
- Material 3 Expressive UI with custom WhatsApp-style bubbles.
- GitHub Actions workflow for remote build verification.

### Optimized
- Configured `gradle.properties` for 4GB RAM (1GB Gradle Daemon limit).
- Enabled Room Write-Ahead Logging (WAL) for better database performance.
- Enabled `android:largeHeap="true"` in AndroidManifest.xml for large file parsing.
- Fixed `gemini-cli` hook parsing error in `settings.json` using the PowerShell call operator.

### Status
- 100% Initial implementation and optimization completed.

---

## [0.2.0] - 2026-06-17
### Added
- ZIP file import capability: extracts chat transcripts and attached media from `.zip` export archives.
- Media rendering support: Asynchronous downsampled image decoding using native Compose `produceState` and `BitmapFactory` to avoid UI blocking or OutOfMemory crashes.
- File attachment indicator: Custom file attachment UI element for non-image files (e.g. documents, audio).
- Attachment name tracking: Added `mediaName` to `MessageEntity` Room schema and bumped Database version to `2` with destructive migration fallback.
- Redundant text suppression: Automatically hides the "(file attached)" placeholder text inside the chat bubble when the actual media is successfully loaded.

### Status
- 100% ZIP import and media rendering features implemented.

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

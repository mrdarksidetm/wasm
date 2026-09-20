# Wasm (Android)

<p align="center">
  <img src="assests/app-icons/Wasm - Original AppIcon.png" width="96" height="96" alt="Wasm App Icon" />
</p>

<p align="center">
  <a href="https://github.com/mrdarksidetm/wasm/actions/workflows/main.yml"><img src="https://github.com/mrdarksidetm/wasm/actions/workflows/main.yml/badge.svg" alt="CI Build Status" /></a>
  <a href="https://m3.material.io/"><img src="https://img.shields.io/badge/Design-Material_3_Expressive-0061A4?style=flat-square" alt="Material 3 Expressive" /></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" /></a>
  <a href="https://developer.android.com/training/data-storage/room"><img src="https://img.shields.io/badge/Storage-Room_SQLite-3DDC84?style=flat-square&logo=sqlite&logoColor=white" alt="Room SQLite" /></a>
  <a href="https://github.com/mrdarksidetm/wasm/releases/tag/latest-apk"><img src="https://img.shields.io/badge/Release-v0.7.0-blue?style=flat-square" alt="Latest Release" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/Privacy-100%25_Offline-success?style=flat-square" alt="100% Offline" /></a>
</p>

<p align="center">
  A modern, high-performance Android application built with <b>Jetpack Compose</b> and <b>Material 3 Expressive</b> for offline-first chat archive management, multi-platform message visualization, and multimedia playback.
</p>

---

## 📱 Download Latest Production APK

<p align="center">
  <a href="https://github.com/mrdarksidetm/wasm/releases/download/latest-apk/wasm-universal-release.apk">
    <img src="assests/images/Direct%20Link%20Frame%20Badge.svg" alt="Direct APK Download" height="100" />
  </a>
</p>

> [!NOTE]
> **Direct Standalone APK (No Unzipping Required)**:
> Click the **Direct Download** badge above to download `wasm-universal-release.apk` directly to your phone ready to install.
> If downloading via the GitHub Actions "Artifacts" table at the bottom of the workflow page, GitHub automatically bundles artifacts into `.zip` archives. Use the direct link above for the pure `.apk`.

- 🔒 **SHA-256 Checksum**: [wasm-universal-release.apk.sha256](https://github.com/mrdarksidetm/wasm/releases/download/latest-apk/wasm-universal-release.apk.sha256)
- 📦 **GitHub Releases Portal**: [Browse `latest-apk` Release](https://github.com/mrdarksidetm/wasm/releases/tag/latest-apk)

---

## 🌟 Key Features

### 1. 💬 Multi-Platform Chat Archive Engine
* **WhatsApp Chat Archive Parsing**:
  - Full-fidelity parsing of exported `.txt` chat logs and associated media files.
  - Automatically reconstructs chat threads with chronological timeline markers, sender identification, timestamps, and delivery bubble alignment.
* **Instagram Direct Archive Parsing**:
  - High-performance extraction of official Instagram ZIP and HTML data export packages (`InstagramZipExtractor`, `InstagramHtmlParser`, `InstagramDao`).
  - Seamlessly indexes direct conversations and interactive message histories into offline Room SQLite storage.
* **Platform Chooser Hub**:
  - Clean selector interface (`PlatformChooserScreen`) allowing instant switching between WhatsApp and Instagram archives.

### 2. 🎵 Embedded Audio & Voice Note Player
* **Native In-App Playback**: Integrated `AudioPlayerManager` supporting playback of voice notes, audio memos, and attached audio files directly inside the conversation timeline.
* **Real-Time Playback Controls**: Interactive seekbars, elapsed time indicators, and play/pause states.

### 3. 🖼️ Media Exporter & Gallery Downloader
* **Zero-Permission Export**: Securely save images, videos, audio clips, and documents to device storage via Android's **Storage Access Framework (SAF)** and `GalleryDownloader`.
* **Zero Blanket Storage Permissions**: No legacy storage permissions required; media is handled purely through scoped SAF user selections.

### 4. 🎨 Material 3 Expressive UI & Glassmorphism
* **Pristine Blank Canvas Home**: Clean, uncluttered canvas respecting system window insets (`statusBarsPadding`).
* **Dynamic Time Greetings**: Contextual greetings (`"Good Morning"`, `"Good Afternoon"`, `"Good Evening"`) based on device local time.
* **Multi-Layer Frosted Glass Card**:
  - Genuine layered glassmorphism architecture.
  - Ambient glow diffused in the background based on dynamic primary and tertiary color tokens.
  - Translucent frosted glass panel with specular reflection and delicate hairline border.
  - Animated 0-to-total message count running smoothly on the foreground layer above the glass.

### 5. 👤 "Let's Personalize" Screen
* **Modern Android Photo Picker**: Select custom profile pictures using `ActivityResultContracts.PickVisualMedia` with zero runtime permission popups.
* **Persistent Preferences**: Local persistence of user profile name and avatar via `UserPreferencesRepository`.
* **Hovering Back Navigation**: Elevated circular action buttons with tonal containers.

### 6. ℹ️ Material 3 Expressive About Screen
* **Developer Profile & Philosophy**: Direct developer links and architectural vision.
* **Hardware & Software Specs**: Live reporting of device architecture, Android API level, and app build details.
* **Official Vector Brand Marks**: Authentic vector logos for Android and Jetpack Compose (`ic_android_logo.xml` and `ic_jetpack_compose_logo.xml`).

### 7. 🛡️ Zero-Permission Privacy Mandate
* **100% Offline by Design**: Absolutely no `INTERNET` permission in `AndroidManifest.xml`.
* **Zero Tracking & Analytics**: No third-party SDKs, telemetry, crash reporters, or external network calls.
* **Zero Broad Media Permissions**: No `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`, or `POST_NOTIFICATIONS` requested.

---

## 🛠️ Architecture & Specifications

| Component | Technology | Description |
| :--- | :--- | :--- |
| **Platform** | Native Android | 100% Kotlin & Jetpack Compose |
| **Design Language** | **Material 3 Expressive** | Layered glassmorphism, dynamic color, custom typography |
| **Target / Min SDK** | Android 14 (API 34) / Android 8.0 (API 26) | Full edge-to-edge transparent system insets |
| **Local Database** | Room (SQLite) with KSP | Fast offline indexing of conversations and messages |
| **State Management** | Kotlin Coroutines & `StateFlow` | Lifecycle-aware reactive streams |
| **Parsers** | WhatsApp & Instagram Parsers | Custom streaming text, HTML, and ZIP archive extractors |
| **Audio Playback** | `AudioPlayerManager` | Scoped media playback for voice notes |
| **Media Export** | Storage Access Framework (SAF) | Privacy-preserving scoped file downloads |
| **CI / CD** | GitHub Actions | Automated keystore signing and release deployment |

---

## 📂 Project Structure

```
Wasm/
├── .github/workflows/build.yml     # Automated CI/CD release workflow
├── app/
│   ├── build.gradle.kts            # Application dependencies & configuration
│   └── src/main/
│       ├── AndroidManifest.xml     # Zero-permission manifest
│       ├── java/com/mrdartsidetm/wasm/
│       │   ├── MainActivity.kt     # Single-activity Compose host
│       │   ├── data/               # Room DB, DAOs, Entities, UserPreferences
│       │   ├── ui/                 # Screens: Home, Chat, Personalize, PlatformChooser, About, Settings
│       │   ├── ui/instagram/       # Instagram archive viewer & ViewModel
│       │   ├── ui/theme/           # Material 3 Expressive color, typography, shapes
│       │   └── util/               # WhatsAppParser, InstagramHtmlParser, InstagramZipExtractor, AudioPlayerManager
│       └── res/                    # Vector drawables, launcher icons, strings, colors
├── assests/                        # Brand vectors, badge graphics, fonts
└── Version.md                      # Source of truth changelog
```

---

## 👤 Developer & Philosophy

Built with ❤️ by **Abhijeet Yadav** ([@mrdarksidetm](https://github.com/mrdarksidetm)).

Wasm is built on the **Local-First Privacy Philosophy**: personal chat archives and memories belong solely on the user's device, never on a third-party server.

---

## 📄 License

This project is licensed under the [Apache License 2.0](LICENSE).

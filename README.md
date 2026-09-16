# Wasm (Android)

A modern, high-performance Android application built with **Jetpack Compose** and **Material 3 Expressive** design system for offline-first chat archive management and message visualization.

## 📱 Download Latest Production APK

> ⚡ **DIRECT STANDALONE APK (NO UNZIPPING REQUIRED)**:
> 
> ⬇️ **[Download wasm-universal-release.apk](https://github.com/mrdarksidetm/wasm/releases/download/latest-apk/wasm-universal-release.apk)**
> 
> 🔍 **[Download SHA-256 Checksum](https://github.com/mrdarksidetm/wasm/releases/download/latest-apk/wasm-universal-release.apk.sha256)**
> 
> 📦 **[Browse GitHub Releases](https://github.com/mrdarksidetm/wasm/releases/tag/latest-apk)**

### ⚠️ Important Download Notice (APK vs Zip)
- **Direct Link / Releases**: Always download the APK directly from the **[GitHub Releases](https://github.com/mrdarksidetm/wasm/releases/tag/latest-apk)** link above. This provides the pure, raw `.apk` binary directly without any extraction needed.
- **GitHub Actions "Artifacts" Section**: If you download builds via the "Artifacts" table on the GitHub Actions workflow summary page, GitHub's web interface **automatically packages all artifacts into a `.zip` archive**. This is a built-in behavior of GitHub's web platform. Simply use the Direct Download link above for the pure `.apk`.

---

## 🎨 Features & Design Architecture

### 1. Pristine Blank Canvas Home Screen
- **Material 3 Expressive**: Clean, uncluttered home canvas respecting system window insets (`statusBarsPadding`).
- **Dynamic Time Greeting**: Automatically displays `"Good Morning"` (04:00–11:59), `"Good Afternoon"` (12:00–16:59), or `"Good Evening"` (17:00–03:59).
- **Horizontal Profile Avatar**: Positioned on the right horizon, opening the personalization flow with a smooth top-to-bottom slide and fade transition.
- **Full-Body Frosted Glass Card**:
  - Genuine multi-layer glassmorphism architecture.
  - Subtle primary and tertiary dynamic color ambient glow diffused in the background.
  - Translucent frosted glass panel with specular reflection and delicate hairline rim.
  - Animated 0-to-total message count running smoothly on the foreground layer above the glass.

### 2. "Let's Personalize" Screen
- **Hovering Back Navigation**: Elevated circular back button in a distinct container color (`secondaryContainer`, 6.dp shadow elevation).
- **Centered Identity Title**: Clear, bold `"Let's Personalize"` heading.
- **Profile Photo Avatar**:
  - Large circular avatar (140.dp) paired with an overlapping circular edit badge (42.dp) containing the pencil icon.
  - Tapping anywhere triggers the modern **Android Photo Picker flow** (`ActivityResultContracts.PickVisualMedia`).
  - Zero runtime permissions required.
- **Persistent Preferences**: Saves customized name and profile photo locally with `UserPreferencesRepository`.
- **Muted Settings Trigger**: Large bottom button with settings icon styled with dynamic `tertiaryContainer` container color.

### 3. Square App Icon Architecture
- Strictly faithful to `assests/app-icons/Wasm - Original AppIcon.svg`.
- Vector background (`ic_launcher_background.xml`) and vector foreground (`ic_launcher_foreground.xml`) layered in `res/mipmap-anydpi-v26/ic_launcher.xml`.
- Multi-density raster PNGs generated across all density buckets (`mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`) as 100% square images.
- Completely removed `android:roundIcon` to prevent OEM launchers from imposing unwanted circular cutouts.

### 4. Zero-Permission Privacy Mandate
- **Zero broad permissions**: No `INTERNET`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`, `POST_NOTIFICATIONS`, or `WRITE_EXTERNAL_STORAGE` permissions declared in `AndroidManifest.xml`.
- Media selection leverages system Photo Picker and Storage Access Framework (SAF).

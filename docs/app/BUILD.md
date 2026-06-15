# Building the Android App

How to build the Hermes Companion Android app from source.

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34
- Kotlin 1.9.22+

## Project Structure

```
app/
├── build.gradle              # App-level build config
├── proguard-rules.pro        # Release build rules
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── java/com/atlas/hermescompanion/
    │   │   ├── MainActivity.kt          # Entry point, navigation
    │   │   ├── MainViewModel.kt         # Shared state
    │   │   ├── ChatScreen.kt            # Chat UI
    │   │   ├── KanbanScreen.kt          # Kanban board UI
    │   │   ├── SettingsScreen.kt        # Connection settings
    │   │   ├── Composer.kt              # Message input
    │   │   ├── MessageList.kt           # Message display
    │   │   ├── MarkdownText.kt          # Markdown rendering
    │   │   ├── Theme.kt                 # Material3 theme
    │   │   └── data/
    │   │       ├── ApiClient.kt         # HTTP client
    │   │       ├── Models.kt            # Data classes
    │   │       └── SessionManager.kt    # Credential storage
    │   └── res/                         # Resources
    └── test/                            # Unit tests
```

## Build Variants

| Variant | Minify | Signing | Use |
|---|---|---|---|
| `debug` | No | Debug keystore | Development |
| `release` | Yes (ProGuard) | Release keystore | Distribution |

## Build from Android Studio

1. Open the project root in Android Studio.
2. Wait for Gradle sync to complete.
3. Select the `debug` or `release` build variant.
4. Click **Run** ▶ or **Build → Build Bundle(s) / APK(s)**.

## Build from Command Line

```bash
# Debug APK
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Release APK (requires signing config)
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk

# Release AAB (for Play Store)
./gradlew bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

## Signing the Release APK

Create `keystore.properties` in the project root:

```properties
storeFile=/path/to/your.keystore
storePassword=your-store-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

The release build type in `build.gradle` already has ProGuard enabled:

```groovy
release {
    minifyEnabled true
    proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
}
```

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| Compose BOM | 2025.01.00 | UI framework |
| Kotlin | 1.9.22 | Language |
| Kotlinx Serialization | 1.6.3 | JSON parsing |
| OkHttp | 4.12.0 | HTTP client |
| OkHttp SSE | 4.12.0 | Server-Sent Events |
| Coil | 2.6.0 | Image loading |
| DataStore | 1.0.0 | Preferences storage |
| Security Crypto | 1.0.0 | Encrypted preferences |
| Navigation Compose | 2.7.7 | Screen navigation |
| Coroutines | 1.7.3 | Async operations |
| JUnit | 4.13.2 | Unit testing |
| Mockito | 5.2.0 | Mocking |
| Turbine | 1.1.0 | Flow testing |
| Robolectric | 4.11.1 | Android unit tests |

## Flavor Dimensions

Currently the app has no product flavors. Future versions may add:
- `fdroid` — F-Droid build without Google dependencies
- `play` — Play Store build with analytics

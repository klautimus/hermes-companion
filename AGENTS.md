# AGENTS.md — Hermes Companion Android App

## Agent skills

### Issue tracker

GitHub Issues at `https://github.com/klautimus/hermes-companion/issues`. External PRs are NOT a triage surface (solo project). See `docs/agents/issue-tracker.md`.

### Triage labels

Standard label vocabulary. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context — one `CONTEXT.md` at repo root + `docs/adr/` for architectural decisions. See `docs/agents/domain.md`.

## Project overview

The Hermes Companion Android app is a Jetpack Compose (Kotlin) application providing a mobile interface to the Hermes Agent system. It features:

- Chat interface with session management (drawer, search, create, delete)
- Kanban board viewer with task cards, columns, bulk selection, inline create
- Settings screen with server URL, credentials, board selection
- First-time setup wizard (QR code scanning, server connection, credential entry)
- EncryptedSharedPreferences for credential storage (fail-closed)
- Markdown rendering in chat bubbles (Markwon)
- Attachment composer (image picker, camera, file picker)

**Package**: `org.hermes.community.companion`
**Min SDK**: 28 (Android 9)
**Target SDK**: 33 (Android 13)
**Test device**: Pixel 4 XL (serial 9B071FFBA0003R)

### Key files

- `app/src/main/java/org/hermes/community/companion/MainViewModel.kt` — central ViewModel (~714 lines)
- `app/src/main/java/org/hermes/community/companion/data/ApiClient.kt` — HTTP client
- `app/src/main/java/org/hermes/community/companion/data/SessionManager.kt` — credential storage
- `app/src/main/java/org/hermes/community/companion/data/Models.kt` — data classes
- `app/src/main/java/org/hermes/community/companion/KanbanScreen.kt` — kanban UI (~1800+ lines)
- `app/src/main/java/org/hermes/community/companion/ChatScreen.kt` — chat UI
- `app/src/main/java/org/hermes/community/companion/MarkdownText.kt` — markdown rendering
- `app/src/main/java/org/hermes/community/companion/Composer.kt` — message composer with attachments

### Build commands

```bash
# Build debug APK
./gradlew assembleDebug --no-daemon

# Run unit tests
./gradlew test --no-daemon

# Install on device (from WSL, using Windows ADB)
powershell.exe -Command "& 'C:\Users\kevin\Downloads\platform-tools-latest-windows\platform-tools\adb.exe' -s 'adb-9B071FFBA0003R-zeEKU6 (2)._adb-tls-connect._tcp' install -r app/build/outputs/apk/debug/app-debug.apk"
```

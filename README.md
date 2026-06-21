# Hermes Companion

A Jetpack Compose Android app that provides a mobile interface for the [Hermes Agent](https://github.com/nousresearch/hermes-agent) system — chat with your AI agent, manage kanban boards, and monitor tasks from your phone.

| Chat | Kanban | Settings |
|------|--------|----------|
| Session drawer, search, streaming responses | Board columns, task cards, drag status | Server config, board selection, 2FA |

## Download

Download the latest APK from the [Releases page](https://github.com/klautimus/hermes-companion/releases).

## Requirements

- **Android 9.0+** (API 28)
- A running [Hermes Companion Daemon](https://github.com/klautimus/hermes-companion-daemon) on a machine with Hermes Agent installed
- Network access to the daemon (local network or via tunnel)

## Setup

1. **Install the daemon** on the machine running Hermes Agent:
   ```bash
   curl -fsSL https://raw.githubusercontent.com/klautimus/hermes-companion-daemon/main/install.sh | bash
   ```

2. **Install the app** on your phone:
   - Download the APK from [Releases](https://github.com/klautimus/hermes-companion/releases)
   - Or build from source: `./gradlew assembleDebug`

3. **Connect:**
   - Open the app → enter your daemon's URL (e.g., `http://192.168.1.100:8777`)
   - Enter your credentials (set during daemon setup)
   - Optional: Scan QR code for automatic configuration

## Features

### 💬 Chat
- Browse and search session history
- Create new sessions
- Send messages with streaming responses (SSE)
- Markdown rendering with syntax highlighting, code blocks, and links
- Attach and view images and files

### 📋 Kanban
- Full board visualization with 8 status columns (triage → done)
- Task cards showing title, assignee, priority, and age
- Create, edit, assign, and delete tasks
- Block/unblock, complete, archive, and reclaim tasks
- Add comments
- Link parent-child task dependencies
- Bulk status updates and reassignment
- Board statistics dashboard
- Profile/assignee management

### 🔒 Security
- EncryptedSharedPreferences for credential storage (fail-closed)
- Email-based 2FA support
- QR code setup token flow for secure first-run pairing

## Building from Source

```bash
# Prerequisites: Android Studio or JDK 17 + Android SDK
git clone https://github.com/klautimus/hermes-companion.git
cd hermes-companion

# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The APK output is at `app/build/outputs/apk/debug/app-debug.apk`.

## CI/CD

GitHub Actions automatically builds the APK on every push to `main` and attaches it to releases. See [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml).

## Architecture

```
┌─────────────────────────────────────────┐
│          Hermes Companion (this app)     │
│                                         │
│  ┌──────────┐  ┌──────────┐  ┌────────┐ │
│  │   Chat   │  │  Kanban  │  │Settings│ │
│  │  Screen  │  │  Screen  │  │ Screen │ │
│  └────┬─────┘  └────┬─────┘  └───┬────┘ │
│       │              │            │      │
│       └──────────┬───┘────────────┘      │
│                  ▼                       │
│         ┌────────────────┐               │
│         │  MainViewModel │               │
│         │    ApiClient   │               │
│         └───────┬────────┘               │
└─────────────────┼────────────────────────┘
                  │ HTTP (Basic Auth + 2FA)
                  ▼
    ┌─────────────────────────────┐
    │   Hermes Companion Daemon    │
    │   (separate repo + machine)  │
    └─────────────┬───────────────┘
                  │
                  ▼
    ┌─────────────────────────────┐
    │       Hermes Agent          │
    │   (API port 8642 + CLI)     │
    └─────────────────────────────┘
```

### Key Files

| File | Description |
|------|-------------|
| `MainViewModel.kt` | Central ViewModel — session/kanban state management |
| `data/ApiClient.kt` | HTTP client with Basic Auth |
| `data/SessionManager.kt` | Encrypted credential storage |
| `data/Models.kt` | Data classes (Task, Session, Board, etc.) |
| `ChatScreen.kt` | Chat UI with session drawer |
| `KanbanScreen.kt` | Kanban board UI (~1800 lines) |
| `MarkdownText.kt` | Markwon-based markdown renderer |
| `Composer.kt` | Message composer with attachments |
| `SetupWizardScreen.kt` | First-run setup wizard |
| `SettingsScreen.kt` | Settings and board selection |

## Tech Stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **Markwon** for markdown rendering
- **OkHttp** for networking
- **EncryptedSharedPreferences** for secure credential storage
- **DataStore** for app preferences
- Min SDK 28 (Android 9), Target SDK 33 (Android 13)

## Companion Daemon

The daemon is required for the app to function. It runs on the same machine as Hermes Agent:

**Repo:** [klautimus/hermes-companion-daemon](https://github.com/klautimus/hermes-companion-daemon)

## License

MIT

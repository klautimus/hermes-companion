# Context — Hermes Companion Android App

## Domain vocabulary

- **Companion** — this Android app. Provides chat + kanban + settings interface to Hermes Agent.
- **Daemon** — the Python server the app connects to. Typically at `android.kevlarscreations.com` or a user-configured URL.
- **Session** — a Hermes Agent conversation thread. Shown in the SessionDrawer. Has messages with role (user/assistant).
- **KanbanTask** — a work item. Displayed in KanbanScreen columns by status. Has title, body, assignee, priority, status.
- **Board** — a named kanban board. The app tracks the active board slug.
- **ApiClient** — the OkHttp-based HTTP client. Methods: get, post, patch, delete, chat, uploadAttachment, redeemSetupToken, registerUser.
- **SessionManager** — manages credential storage via EncryptedSharedPreferences. Fail-closed on encryption unavailability.
- **MainViewModel** — central state holder. Exposes StateFlows for chat messages, sessions, kanban tasks, boards, errors.
- **SetupWizard** — first-run flow: QR scan → server connection → credential entry → board selection.
- **MarkdownText** — Markwon-based composable for rendering markdown in chat bubbles.

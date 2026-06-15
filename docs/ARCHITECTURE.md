# Architecture

System architecture for the Hermes Companion project.

## Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        Android Device                          │
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │  ChatScreen   │  │ KanbanScreen │  │   SettingsScreen     │  │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬───────────┘  │
│         │                 │                      │              │
│  ┌──────┴─────────────────┴──────────────────────┴───────────┐  │
│  │                    MainViewModel                          │  │
│  └──────┬─────────────────┬──────────────────────┬───────────┘  │
│         │                 │                      │              │
│  ┌──────┴───────┐  ┌──────┴───────┐  ┌──────────┴───────────┐  │
│  │  ApiClient   │  │ SessionManager│  │   DataStore          │  │
│  │  (OkHttp)    │  │ (DataStore)  │  │   (Preferences)      │  │
│  └──────┬───────┘  └──────────────┘  └──────────────────────┘  │
│         │                                                       │
└─────────┼───────────────────────────────────────────────────────┘
          │ HTTP Basic Auth
          │ Port 8777
          ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Companion Server                            │
│                     (Python / aiohttp)                          │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    BasicAuth Middleware                    │   │
│  │              (HTTP Basic + scrypt passwords)              │   │
│  └──────────────────────────┬───────────────────────────────┘   │
│                             │                                   │
│  ┌──────────────┬───────────┴───────────┬───────────────────┐   │
│  │              │                       │                   │   │
│  ▼              ▼                       ▼                   ▼   │
│ Health    HermesProxy              Kanban CLI           Attach  │
│ Endpoint  (HTTP forward)           (subprocess)         ments   │
│           Port 8642                                     (files) │
│                             │                                   │
└─────────────────────────────┼───────────────────────────────────┘
                              │
          ┌───────────────────┴───────────────────┐
          ▼                                       ▼
┌─────────────────────┐               ┌─────────────────────┐
│    Hermes API       │               │   Hermes CLI        │
│    (port 8642)      │               │   (hermes kanban)   │
│                     │               │                     │
│  - Sessions         │               │  - boards           │
│  - Chat             │               │  - tasks            │
│  - Messages         │               │  - comments         │
└─────────────────────┘               └─────────────────────┘
```

## Data Flow

### Chat Flow

1. User types message in `ChatScreen`
2. `MainViewModel` calls `ApiClient.chat()` with message list
3. `ApiClient` sends `POST /v1/chat/completions` to companion server (HTTP Basic auth)
4. Companion server forwards to Hermes API (`http://127.0.0.1:8642/v1/chat/completions`) with Bearer token
5. Hermes API returns response → companion → app
6. App parses response and displays in `MessageList`

### Kanban Flow

1. User opens `KanbanScreen`
2. `MainViewModel` calls `ApiClient.get("/api/kanban/tasks?board=default")`
3. Companion server runs `hermes kanban list --json` subprocess
4. CLI output (JSON) is parsed and returned to the app
5. App renders task columns (triage, todo, ready, running, blocked, done)

### Session Flow

1. User opens session drawer
2. `MainViewModel` calls `ApiClient.get("/api/sessions")`
3. Companion server forwards to Hermes API `GET /api/sessions`
4. Response is normalized: `{ "session": {...} }` → `{ "data": [{...}] }`
5. App displays session list

## Security Model

1. **App → Companion:** HTTP Basic Auth over the network. Use HTTPS (via reverse proxy) in production.
2. **Companion → Hermes API:** Bearer token (`API_SERVER_KEY`). Never exposed to the app.
3. **Password Storage:** Scrypt-hashed passwords in `auth.json`. Auto-reloaded on change.
4. **Android Credentials:** Stored in DataStore (preferences). Future: EncryptedSharedPreferences.
5. **No Session State:** The companion server is stateless. All session state lives in Hermes.

## Key Design Decisions

- **Companion as Shim:** The companion server is a thin wrapper — it adds auth and a mobile-friendly API but doesn't hold state.
- **CLI Wrapper for Kanban:** Kanban operations go through the Hermes CLI (`hermes kanban`) rather than direct database access, ensuring consistency with the Hermes ecosystem.
- **Non-Streaming Chat (v1):** Chat uses request/response rather than SSE streaming for simplicity. Streaming is planned for v2.
- **Hardcoded Paths (v1):** Server paths are hardcoded for Kevin's setup. Configurable paths are planned for v0.2.0.

# Hermes Companion

A phone-first companion app for the [Hermes Agent](https://hermes-agent.nousresearch.com/docs) — chat, session management, and full kanban board control from your Android device.

## What It Is

Hermes Companion consists of two components:

- **Companion Server** — a lightweight Python HTTP daemon that wraps the Hermes API and Kanban CLI, adding HTTP Basic auth and a mobile-friendly REST API.
- **Android App** — a Kotlin/Compose app providing ChatGPT-style messaging, session management, and full kanban board parity with the Hermes server.

```
Android App  ───  Companion Server  ───  Hermes API + CLI
 (Compose)        (Python/aiohttp)        (port 8642)
 :8777              :8777
```

## Features

- **Chat** — Send messages to Hermes and receive responses. Non-streaming (v1).
- **Sessions** — Browse, search, create, and delete Hermes sessions from your phone.
- **Kanban** — Full kanban board management: create boards, manage tasks (create, complete, comment, assign, link), multi-select, bulk operations.
- **Attachments** — Upload and serve file attachments through the companion server.
- **HTTP Basic Auth** — Scrypt-hashed passwords, auto-reloading auth file.

## Quick Start

### Prerequisites

- A running Hermes Agent instance (local or remote)
- Hermes API key (`API_SERVER_KEY`)
- Android 8.0+ (API 26+) device for the app
- Python 3.10+ for the server

### Server (Docker — Recommended)

```bash
# Clone the repo
git clone https://github.com/hermes-community/hermes-companion.git
cd hermes-companion

# Set your API key
export API_SERVER_KEY="your-hermes-api-key"

# Start with Docker Compose
docker compose up -d

# Verify it's running
curl http://localhost:8777/health
```

### Server (Bare Metal)

```bash
# Clone and enter the repo
git clone https://github.com/hermes-community/hermes-companion.git
cd hermes-companion/server

# Create a virtual environment
python3 -m venv venv
source venv/bin/activate

# Install dependencies
pip install aiohttp

# Set required environment variable
export API_SERVER_KEY="your-hermes-api-key"

# Run the server
python server.py
# → Companion daemon starting on 127.0.0.1:8777
```

### Android App

1. Download the APK from the [Releases](https://github.com/hermes-community/hermes-companion/releases) page.
2. Install on your Android device (allow "Install from unknown sources" if prompted).
3. Open the app → enter your server URL, username, and password.
4. Tap **Test Connection** → **Save**.

> **First-run tip:** If you used the default setup, the server URL is `http://<your-server-ip>:8777`. The default credentials are whatever you configured during server setup.

## Server Configuration

The companion server is configured via environment variables:

| Variable | Default | Description |
|---|---|---|
| `API_SERVER_KEY` | *(required)* | Hermes API bearer token |
| `COMPANION_HOST` | `127.0.0.1` | Bind address |
| `COMPANION_PORT` | `8777` | Bind port |
| `HERMES_API_URL` | `http://127.0.0.1:8642` | Hermes API base URL |

See [docs/server/CONFIG_REFERENCE.md](docs/server/CONFIG_REFERENCE.md) for the full configuration reference.

## Project Structure

```
hermes-companion/
├── server/                  # Python companion server
│   ├── server.py            # Main server (aiohttp)
│   ├── test_server.py       # Server tests (pytest)
│   └── Dockerfile           # Server container image
├── app/                     # Android app (Kotlin/Compose)
│   ├── build.gradle         # App-level build config
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/org/hermes/community/companion/
│           ├── MainActivity.kt
│           ├── MainViewModel.kt
│           ├── ChatScreen.kt
│           ├── KanbanScreen.kt
│           ├── SettingsScreen.kt
│           └── data/
│               ├── ApiClient.kt
│               ├── Models.kt
│               └── SessionManager.kt
├── docs/
│   ├── server/              # Server deployment docs
│   ├── app/                 # App build & setup docs
│   ├── ARCHITECTURE.md      # System architecture
│   └── SECURITY.md          # Security model
├── docker-compose.yml       # Docker deployment
├── LICENSE
└── README.md
```

## Troubleshooting

| Problem | Solution |
|---|---|
| "Can't reach server" | Verify the companion server is running: `curl http://localhost:8777/health` |
| "Invalid credentials" | Check your username/password in `auth.json` on the server |
| "Hermes API unreachable" | Verify Hermes is running on port 8642 and `API_SERVER_KEY` is set |
| App shows no sessions | Check that the Hermes API is reachable from the server |
| Kanban tasks not loading | Ensure `?board=<slug>` is set in the app's board settings |
| Connection timeout on chat | Normal for long-running agent tasks; the read timeout is 300s |

See [docs/server/TROUBLESHOOTING.md](docs/server/TROUBLESHOOTING.md) for more.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on submitting issues and pull requests.

## License

[MIT](LICENSE) — see the LICENSE file for details.

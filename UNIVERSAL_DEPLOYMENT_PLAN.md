# Hermes Companion — Universal Deployment Plan

## Executive Summary

The Hermes Companion project currently consists of a Python companion server (`server/server.py`) and an Android app (Kotlin/Compose). Both components contain **hardcoded Kevin-specific paths, credentials, and defaults** that prevent universal deployment. This document provides a detailed assessment, implementation plan, and task breakdown to make the package open-source ready.

---

## Current State Analysis

### Server (`server/server.py`)

| Hardcoded Value | Line | Impact |
|-----------------|------|--------|
| `AUTH_FILE = Path("/home/kevin/.hermes/companion/auth.json")` | 35 | Only works on Kevin's machine |
| `HERMES_BIN = "/home/kevin/.hermes/hermes-agent/venv/bin/hermes"` | 36 | Assumes specific venv location |
| `ATTACHMENTS_DIR = Path("/home/kevin/.hermes/companion/attachments")` | 38 | Hardcoded data directory |
| `API_KEY` from `API_SERVER_KEY` env var | 30 | Required but no fallback/config |
| No config file support | — | All settings via env vars only |
| No first-run setup | — | Manual `auth.json` creation required |

**Auth System**: Uses `auth.json` with scrypt-hashed passwords. Current file at `/home/kevin/.hermes/companion/auth.json` contains only user "kevin".

### Android App

| Hardcoded Value | File | Line | Impact |
|-----------------|------|------|--------|
| `DEFAULT_URL = "https://android.kevlarscreations.com"` | `SessionManager.kt` | 14 | Points to Kevin's Cloudflare tunnel |
| `DEFAULT_USERNAME = "kevin"` | `SessionManager.kt` | 15 | Username hardcoded |
| `DEFAULT_PASSWORD = "Kevi67n!1991!"` | `SessionManager.kt` | 16 | **Security issue** — real password in source |
| `applicationId "com.atlas.hermescompanion"` | `app/build.gradle` | 11 | Personal package name |
| `namespace 'com.atlas.hermescompanion'` | `app/build.gradle` | 8 | Personal namespace |

**Settings Screen**: Already has connection test, but falls back to `DEFAULT_PASSWORD` if empty (line 92 in `SettingsScreen.kt`).

### Connection Flow
```
Android App → Cloudflare Tunnel → Companion Server (port 8777) → Hermes API (port 8642) + Hermes CLI
```

---

## Required Changes

### 1. Server: Config File + Auto-Detection (Complexity: M)

**Changes needed:**
- Add YAML/JSON config file support (`~/.config/hermes-companion/config.yaml` or `/etc/hermes-companion/config.yaml`)
- Config schema:
  ```yaml
  server:
    host: "127.0.0.1"
    port: 8777
  hermes:
    api_url: "http://127.0.0.1:8642"
    api_key: "${HERMES_API_KEY}"  # env var substitution
    cli_path: "auto"  # auto-detect or explicit path
  auth:
    file: "~/.config/hermes-companion/auth.json"
  storage:
    attachments_dir: "~/.local/share/hermes-companion/attachments"
  ```
- Auto-detect Hermes CLI:
  1. Check `PATH` for `hermes`
  2. Check common locations: `~/.hermes/hermes-agent/venv/bin/hermes`, `/usr/local/bin/hermes`, `~/.local/bin/hermes`
  3. Fallback to `hermes` in PATH
- Environment variable overrides for all config values
- Validate config on startup with clear error messages

**Files to modify:** `server/server.py` (config loading), add `server/config.py`

---

### 2. Server: First-Run Setup Wizard (Complexity: M)

**Changes needed:**
- New CLI entry point: `hermes-companion setup` (or `python -m server.setup`)
- Interactive wizard:
  1. Detect/install Hermes CLI
  2. Prompt for Hermes API URL and API key
  3. Create admin user (username + password) → generate `auth.json` with scrypt hash
  4. Choose data directory
  5. Write config file
  6. Optionally generate systemd service / Docker compose
- Non-interactive mode: `hermes-companion setup --non-interactive --username X --password Y --api-key Z`
- Generate random secure password if not provided
- Print connection info (URL, username, password) for app configuration

**Files to create:** `server/setup.py`, `server/__main__.py` (entry point)

---

### 3. Server: pip installable package (Complexity: S)

**Changes needed:**
- `pyproject.toml`:
  ```toml
  [project]
  name = "hermes-companion-server"
  version = "0.1.0"
  requires-python = ">=3.10"
  dependencies = ["aiohttp>=3.9", "pyyaml>=6.0"]
  
  [project.scripts]
  hermes-companion = "hermes_companion.cli:main"
  
  [tool.setuptools.packages.find]
  where = ["."]
  include = ["hermes_companion*"]
  ```
- `python -m pip install -e .` for development
- `pip install hermes-companion-server` for users
- Include `hermes_companion/` package with: `__main__.py`, `server.py`, `setup.py`, `config.py`
- Generate systemd user service file on setup: `hermes-companion install-service`
- Document: `pip install hermes-companion-server && hermes-companion setup && hermes-companion start`

**Files to create:** `pyproject.toml`, `hermes_companion/__init__.py`, `hermes_companion/cli.py`

---

### 4. App: First-Run Setup Wizard + QR Code (Complexity: L)

**Changes needed:**
- **Remove all `DEFAULT_*` constants** from `SessionManager.kt`
- On first launch (no saved config), show setup wizard:
  1. **Server URL** input (with validation)
  2. **Username** input
  3. **Password** input (no default!)
  4. **Test Connection** button (mandatory before proceeding)
  5. **Save & Continue**
- **QR Code scanning**:
  - Server generates QR code with config: `hermes-companion://config?url=...&user=...&pass=...`
  - App uses `CameraX` + `ML Kit Barcode Scanning` or `ZXing`
  - Scan → auto-fill wizard → test → save
- **Settings Screen** updates:
  - Remove placeholder text referencing Kevin's URL
  - Show "Not configured" state when empty
  - Better error messages (timeout, auth failed, TLS, wrong path)
  - Connection test shows detailed diagnostics

**Files to modify:** `SessionManager.kt`, `SettingsScreen.kt`, `MainViewModel.kt`, new `SetupWizardScreen.kt`, `MainActivity.kt` (navigation)

**Dependencies to add:** `com.google.mlkit:barcode-scanning`, `androidx.camera:camera-core`, `androidx.camera:camera-view`

---

### 5. App: Generic Package Name + Branding (Complexity: S)

**Changes needed:**
- **Package name**: `com.atlas.hermescompanion` → `org.hermes.companion` (or `com.hermescompanion.app`)
- **Namespace**: Same as package name
- **App name**: "Hermes Companion" (keep)
- **Application ID**: Update in `build.gradle`
- **Directory structure**: Rename `com/atlas/hermescompanion/` → `org/hermes/companion/`
- **All imports**: Update across all `.kt` files
- **Icon/branding**: Generic Hermes logo (not personal)

**Files to modify:** `app/build.gradle`, `settings.gradle`, all `.kt` files, `AndroidManifest.xml`

---

### 6. Documentation (Complexity: S)

**Files to create/update:**
- `README.md` — Project overview, quick start, architecture
- `docs/SETUP_SERVER.md` — Server installation (pip, Docker, systemd, binary)
- `docs/SETUP_APP.md` — App installation (Play Store, F-Droid, APK), QR code setup
- `docs/CONFIGURATION.md` — Config file reference, env vars, auth management
- `docs/TROUBLESHOOTING.md` — Common issues (connection, auth, TLS, CLI not found)
- `docs/DEVELOPMENT.md` — Building, testing, contributing
- `systemd/hermes-companion.service` — Systemd unit file
- `config.example.yaml` — Example config file

---

## Recommended Open-Source Package Structure

```
hermes-companion/
├── server/
│   ├── pyproject.toml              # Pip package config
│   ├── Dockerfile
│   ├── docker-compose.yml
│   ├── .dockerignore
│   ├── config.example.yaml
│   ├── hermes_companion/
│   │   ├── __init__.py
│   │   ├── __main__.py             # Entry point: hermes-companion
│   │   ├── server.py               # Main server (refactored)
│   │   ├── config.py               # Config loading/validation
│   │   ├── setup.py                # Setup wizard
│   │   ├── auth.py                 # Auth utilities
│   │   └── cli.py                  # Kanban CLI wrapper
│   ├── tests/
│   └── systemd/
│       └── hermes-companion.service
├── app/
│   ├── build.gradle.kts            # Migrate to KTS
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/org/hermes/companion/
│   │   │   ├── data/
│   │   │   │   ├── SessionManager.kt
│   │   │   │   ├── ApiClient.kt
│   │   │   │   └── SetupConfig.kt  # QR code config data class
│   │   │   ├── ui/
│   │   │   │   ├── SettingsScreen.kt
│   │   │   │   ├── SetupWizardScreen.kt
│   │   │   │   ├── KanbanScreen.kt
│   │   │   │   └── components/
│   │   │   ├── MainViewModel.kt
│   │   │   └── MainActivity.kt
│   │   └── res/
│   └── proguard-rules.pro
├── docs/
│   ├── SETUP_SERVER.md
│   ├── SETUP_APP.md
│   ├── CONFIGURATION.md
│   ├── TROUBLESHOOTING.md
│   └── DEVELOPMENT.md
├── README.md
├── LICENSE
├── CONTRIBUTING.md
└── .github/
    └── workflows/
        ├── server-ci.yml
        └── app-ci.yml
```

**Package Names:**
- PyPI: `hermes-companion` (check availability)
- Docker: `ghcr.io/hermes-community/companion-server`
- Android: `org.hermes.companion` (Play Store) / `org.hermes.companion.fdroid` (F-Droid variant)

---

## Setup Flow (End-to-End)

### Server Admin (One-time)
```bash
# Option 1: Docker (recommended)
docker run -d \
  --name hermes-companion \
  -v ./config:/config \
  -v ./data:/data \
  -p 8777:8777 \
  ghcr.io/hermes-community/companion-server

# Option 2: Pip install
pip install hermes-companion
hermes-companion setup  # Interactive wizard
hermes-companion serve  # Run server

# Option 3: Systemd
sudo cp systemd/hermes-companion.service /etc/systemd/system/
sudo systemctl enable --now hermes-companion
```

### Server Output (after setup)
```
✓ Hermes Companion configured successfully!

Server URL:  http://your-server:8777
Username:    admin
Password:    xK9#mP2$vL5@ (save this!)

QR Code:     [████████████████]
             Scan with Hermes Companion app

Config:      ~/.config/hermes-companion/config.yaml
Auth:        ~/.config/hermes-companion/auth.json
Data:        ~/.local/share/hermes-companion/
```

### App User (First Launch)
1. Install app from Play Store / F-Droid / APK
2. Open app → **Setup Wizard** appears
3. **Option A**: Tap "Scan QR Code" → point at server terminal
4. **Option B**: Manual entry → URL, username, password
5. Tap "Test Connection" → ✓ Connected
6. Tap "Save & Continue" → Main screen

---

## Complexity Summary

| Task | Complexity | Est. Days | Dependencies |
|------|------------|-----------|--------------|
| Server: Config file + auto-detection | M | 2-3 | — |
| Server: First-run setup wizard + pip package | M | 2-3 | Config file |
| App: Generic package name + branding | S | 1 | — |
| App: First-run wizard + QR code | L | 4-5 | Server QR format |
| Documentation | S | 2 | All above |

**Total estimated effort: 11-14 days**

---

## Implementation Order (Recommended)

1. **Server config + auto-detection** (foundation)
2. **Server setup wizard + pip package** (enables easy deployment)
3. **App package rename** (independent, can parallelize)
4. **App setup wizard + QR** (depends on server QR format)
5. **Documentation** (last, reflects final state)

---

## Security Considerations

- [ ] Remove hardcoded password from app source (CRITICAL)
- [ ] Generate random password on server setup
- [ ] Support TLS/HTTPS for server (reverse proxy docs)
- [ ] Rate limiting on auth endpoints
- [ ] Secure default file permissions (0600 for auth.json)
- [ ] Input validation on all API endpoints (already mostly done)
- [ ] Dependency scanning (Dependabot/GitHub Actions)

---

## Testing Strategy

- **Server**: Unit tests for config, auth, CLI wrapper; integration tests with mock Hermes API
- **App**: Unit tests for SessionManager, ViewModel; UI tests for setup wizard
- **E2E**: Docker compose with test Hermes instance + app on emulator

---

*Generated: 2026-06-14 | Project: HermesCompanion | Author: Atlas*
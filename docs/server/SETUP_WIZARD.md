# Setup Wizard Guide

The Hermes Companion server includes a first-run setup wizard to simplify initial configuration.

> **Note:** The setup wizard is planned for v0.2.0. Currently, initial setup is done manually (see below).

## Current Setup Process (v0.1.0)

### 1. Set the API Key

```bash
export API_SERVER_KEY="your-hermes-api-key"
```

### 2. Create the Auth File

Create `auth.json` with an initial admin user:

```python
#!/usr/bin/env python3
"""Create an auth.json file with a scrypt-hashed password."""
import base64
import hashlib
import json
import os

password = input("Enter password: ")
salt = os.urandom(16)
hash_bytes = hashlib.scrypt(password.encode(), salt=salt, n=16384, r=8, p=1, dklen=32)

auth = {
    "users": {
        "admin": {
            "password_hash": f"scrypt$16384$8$1${salt.hex()}${base64.b64encode(hash_bytes).decode()}",
            "created_at": "2026-01-01"
        }
    }
}

with open("auth.json", "w") as f:
    json.dump(auth, f, indent=2)

print("auth.json created. Keep this file secure (chmod 600).")
```

```bash
python create_auth.py
chmod 600 auth.json
```

### 3. Start the Server

```bash
python server.py
```

### 4. Verify

```bash
curl http://localhost:8777/health
```

## Managing Users

To add or modify users, edit `auth.json` directly. The server auto-reloads the file when it changes.

### Generate a Password Hash

```python
import base64, hashlib, os
password = "newpassword"
salt = os.urandom(16)
h = hashlib.scrypt(password.encode(), salt=salt, n=16384, r=8, p=1, dklen=32)
print(f"scrypt$16384$8$1${salt.hex()}${base64.b64encode(h).decode()}")
```

### Reset a Password

1. Stop the server (or edit live — it auto-reloads).
2. Replace the `password_hash` for the user in `auth.json`.
3. If the server was running, it picks up the change within seconds.

## Default Paths

| File | Path | Description |
|---|---|---|
| Auth file | `~/.hermes/companion/auth.json` | User credentials |
| Attachments | `~/.hermes/companion/attachments/` | Uploaded files |
| Hermes CLI | `~/.hermes/hermes-agent/venv/bin/hermes` | Kanban CLI binary |

> These paths are currently hardcoded. A future version will support configurable paths.

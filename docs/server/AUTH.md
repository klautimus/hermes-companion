# Auth System

How authentication works in the Hermes Companion server.

## Overview

The companion server uses HTTP Basic Authentication (RFC 7617). Every request (except to `/health`) must include:

```
Authorization: Basic base64(username:password)
```

## Auth File

Credentials are stored in `auth.json` as a JSON object with scrypt-hashed passwords.

### File Location

```
~/.hermes/companion/auth.json
```

### File Format

```json
{
  "users": {
    "username": {
      "password_hash": "scrypt$16384$8$1$<salt-hex>$<hash-b64>",
      "created_at": "2026-01-01"
    }
  },
  "note": "Format: scrypt$N$r$p$<salt-hex>$<hash-b64>"
}
```

### Password Hash Format

```
scrypt$N$r$p$<salt-hex>$<base64-hash>
```

| Field | Value | Description |
|---|---|---|
| N | 16384 | CPU/memory cost parameter |
| r | 8 | Block size |
| p | 1 | Parallelization parameter |
| salt-hex | 32 hex chars | 16-byte random salt |
| base64-hash | Base64 string | 32-byte derived key |

### Auto-Reload

The server watches the auth file's mtime and automatically reloads credentials when the file changes. No server restart needed to add users or change passwords.

## Creating Users

Use Python to generate a valid hash:

```python
import base64, hashlib, os

password = "my-secure-password"
salt = os.urandom(16)
hash_bytes = hashlib.scrypt(password.encode(), salt=salt, n=16384, r=8, p=1, dklen=32)

hash_str = f"scrypt$16384$8$1${salt.hex()}${base64.b64encode(hash_bytes).decode()}"
print(hash_str)
```

Then add the user to `auth.json`:

```json
{
  "users": {
    "newuser": {
      "password_hash": "<hash-from-above>",
      "created_at": "2026-01-01"
    }
  }
}
```

## Password Reset

1. Generate a new hash (see above).
2. Edit `auth.json` — replace the `password_hash` for the user.
3. The change takes effect within seconds (auto-reload).

## Security Notes

- Set file permissions to `600` on `auth.json`.
- The server never logs or echoes passwords.
- Failed auth returns a generic "Invalid credentials" message (no user enumeration).
- Plaintext password comparison is supported as a fallback (for non-scrypt hashes), but scrypt is strongly recommended.

## Multi-User (Future)

The auth system already supports multiple users in `auth.json`. The Android app currently uses a single set of credentials, but the server can authenticate any user in the file. Future app versions may support per-user sessions.

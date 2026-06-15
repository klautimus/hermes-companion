# Configuration Reference

Complete reference for the Hermes Companion server configuration.

## Environment Variables

All configuration is done via environment variables:

| Variable | Required | Default | Description |
|---|---|---|---|
| `API_SERVER_KEY` | **Yes** | — | Hermes API bearer token. Without this, the server refuses to start. |
| `COMPANION_HOST` | No | `127.0.0.1` | Address to bind the HTTP server. Use `0.0.0.0` to listen on all interfaces. |
| `COMPANION_PORT` | No | `8777` | Port to bind the HTTP server. |
| `HERMES_API_URL` | No | `http://127.0.0.1:8642` | Base URL of the Hermes API. |

## Server Behavior

### Authentication

- All endpoints except `/health` and `/healthz` require HTTP Basic Auth.
- Credentials are read from `auth.json` (path is hardcoded at server startup).
- The auth file is automatically reloaded when its mtime changes — no restart needed.
- Passwords are hashed using scrypt: `scrypt$N$r$p$<salt-hex>$<hash-b64>`.

### Auth File Format

```json
{
  "users": {
    "username": {
      "password_hash": "scrypt$16384$8$1$<salt-hex>$<base64-hash>",
      "created_at": "2026-01-01"
    }
  }
}
```

### Timeouts

| Operation | Timeout |
|---|---|
| Hermes API connection | 10 seconds |
| Hermes API read | 300 seconds (5 minutes) |
| Kanban CLI subprocess | 60 seconds |
| Health check to Hermes | 5 seconds |

### Request Size Limits

| Endpoint | Limit |
|---|---|
| File upload (`POST /api/attachments`) | 10 MB |
| Comment text (`POST /api/kanban/tasks/{id}/comment`) | 10 KB |

### Slug Validation

Board slugs must match: `^[a-z0-9-]+$` (lowercase alphanumeric + hyphens, max 64 chars, no leading/trailing hyphens).

## Ports

| Port | Service | Direction |
|---|---|---|
| 8777 | Companion server | Inbound from app |
| 8642 | Hermes API | Outbound from companion |

## Logging

The companion server logs to stdout in the format:

```
2026-01-01 12:00:00 [companion] INFO Companion daemon starting on 127.0.0.1:8777
2026-01-01 12:00:01 [companion] INFO Hermes API error: Connection refused
```

Log levels: `INFO` (default), set `logging.basicConfig(level=logging.DEBUG)` for debug output.

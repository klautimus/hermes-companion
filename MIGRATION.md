# Migration Guide

Migrating from Kevin's personal setup to the universal Hermes Companion package.

## Overview

The Hermes Companion project was originally built for a single-user setup with hardcoded paths and credentials. This guide covers the changes needed to make it work for any user.

## Breaking Changes

### Server

| Change | Old (Kevin-specific) | New (Universal) |
|---|---|---|
| Auth file path | `/home/kevin/.hermes/companion/auth.json` | `~/.hermes/companion/auth.json` (or configurable) |
| Hermes CLI path | `/home/kevin/.hermes/hermes-agent/venv/bin/hermes` | Auto-detected or configurable |
| Attachments dir | `/home/kevin/.hermes/companion/attachments` | `~/.hermes/companion/attachments` (or configurable) |
| Config method | Environment variables only | Environment variables + config file |

### App

| Change | Old (Kevin-specific) | New (Universal) |
|---|---|---|
| Default server URL | `https://android.kevlarscreations.com` | Empty (user must configure) |
| Default username | `kevin` | Empty (user must configure) |
| Default password | `atlas2026` | Empty (user must configure) |
| Package name | `com.atlas.hermescompanion` | `com.atlas.hermescompanion` (unchanged) |

## Migration Steps

### Server Migration

1. **Stop the existing server:**
   ```bash
   pkill -f "python server.py"
   ```

2. **Back up your auth file:**
   ```bash
   cp ~/.hermes/companion/auth.json ~/.hermes/companion/auth.json.bak
   ```

3. **Update the server code** to the latest version.

4. **Set the API key via environment:**
   ```bash
   export API_SERVER_KEY="your-key"
   ```
   Add to `~/.hermes/.env` or your shell profile for persistence.

5. **Start the new server:**
   ```bash
   python server.py
   ```

6. **Verify:**
   ```bash
   curl http://localhost:8777/health
   ```

### App Migration

1. **Update the app** to the latest version (APK or Play Store).
2. **Open Settings** → clear the old defaults.
3. **Enter your new server URL and credentials.**
4. **Test Connection** → **Save**.

## Config Path Changes

If you've been using the hardcoded paths, the new version may use different defaults. Override with environment variables:

```bash
# If you want to keep the old paths
export COMPANION_HOST=127.0.0.1
export COMPANION_PORT=8777
export HERMES_API_URL=http://127.0.0.1:8642
```

## Auth File Compatibility

The auth file format has not changed. Existing `auth.json` files with scrypt-hashed passwords continue to work.

## Troubleshooting Migration

| Problem | Solution |
|---|---|
| Server won't start | Check `API_SERVER_KEY` is set |
| Auth fails | Verify auth.json path and format |
| App can't connect | Check firewall, URL, and credentials |
| Kanban CLI not found | Verify hermes binary is in PATH |

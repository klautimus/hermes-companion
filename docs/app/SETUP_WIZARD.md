# App Setup Wizard

Guide to the Hermes Companion app's first-run setup flow.

## First-Run Flow

When the app is launched for the first time (no saved credentials), the user must configure the connection:

1. **Server URL** — The companion server address (e.g., `https://companion.example.com` or `http://192.168.1.100:8777`)
2. **Username** — HTTP Basic auth username
3. **Password** — HTTP Basic auth password
4. **Test Connection** — Verifies the server is reachable and credentials are valid
5. **Save** — Stores credentials and connects

> **Note:** The current version (v1.2.0) uses a Settings screen for configuration rather than a dedicated wizard flow. A first-run wizard with QR code scanning is planned for v2.0.

## Current Configuration (v1.2.0)

Configuration is done through the **Settings** tab:

1. Open the app → tap the **Settings** tab
2. Enter the Server URL, Username, and Password
3. Tap **Test Connection** to verify
4. Tap **Save** to persist

### Default Values

The app ships with hardcoded defaults (for Kevin's setup). These should be removed or made configurable before open-source release:

| Setting | Current Default | Should Be |
|---|---|---|
| Server URL | `https://android.kevlarscreations.com` | Empty (required) |
| Username | `kevin` | Empty (required) |
| Password | `atlas2026` | Empty (required) |
| Board | `default` | `default` |

## QR Code Setup (Planned)

A future version will support QR code scanning for zero-config setup:

1. Server admin runs `hermes-companion setup` → generates a QR code
2. App user taps "Scan QR Code" on first launch
3. App scans the code → auto-fills URL, username, password
4. App tests connection → saves

### QR Code Format

```
hermescompanion://config?url=https://companion.example.com&user=admin&pass=generated-password
```

## Connection Test Details

The test connection button sends `GET /health` to the companion server and checks:

- **Network reachability** — Can the server be contacted?
- **Authentication** — Are the credentials valid?
- **Hermes API status** — Is the Hermes API reachable from the companion server?

### Error Messages

| Error | Meaning | Fix |
|---|---|---|
| "Can't reach server" | Network unreachable | Check URL, firewall, VPN |
| "Invalid credentials" | Auth failed | Check username/password |
| "Server address not found" | DNS resolution failed | Check URL spelling |
| "Secure connection failed" | TLS error | Check https vs http |
| "Request timed out" | Server too slow | Check server load |

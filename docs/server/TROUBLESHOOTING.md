# Troubleshooting

Common issues and solutions for the Hermes Companion project.

## Top 10 Issues

### 1. "Can't reach server — is Companion running?"

**Cause:** The companion server is not running or the URL is wrong.

**Fix:**
```bash
# Check if the server is running
curl http://localhost:8777/health

# If not running, start it
cd hermes-companion/server
export API_SERVER_KEY="your-key"
python server.py
```

### 2. "Invalid credentials"

**Cause:** Wrong username or password, or auth file not found.

**Fix:**
- Verify the auth file exists: `ls -la ~/.hermes/companion/auth.json`
- Check the username exists in the auth file
- Generate a new password hash if needed (see [AUTH.md](AUTH.md))

### 3. "Hermes API unreachable" (status: degraded)

**Cause:** The companion server can't reach Hermes on port 8642.

**Fix:**
```bash
# Check if Hermes is running
curl http://127.0.0.1:8642/health

# Check the HERMES_API_URL environment variable
echo $HERMES_API_URL
```

### 4. App shows no sessions

**Cause:** The Hermes API is reachable but returning no sessions, or pagination is not handled.

**Fix:**
- Verify Hermes has sessions: `curl -H "Authorization: Bearer $API_SERVER_KEY" http://127.0.0.1:8642/api/sessions`
- The companion server forwards pagination params — check the Hermes API response

### 5. Kanban tasks not loading

**Cause:** Board slug not set, or the board doesn't exist.

**Fix:**
- In the app Settings, verify the board slug is set (default: `default`)
- On the server, list boards: `hermes kanban boards list --json`

### 6. "Secure connection failed" (SSL error)

**Cause:** Using `https://` but the server doesn't have TLS configured.

**Fix:**
- Either set up a reverse proxy with TLS (see [DEPLOY_BAREMETAL.md](DEPLOY_BAREMETAL.md))
- Or use `http://` with the direct server IP/port (only for local/trusted networks)

### 7. Chat request times out

**Cause:** The agent task takes longer than the read timeout.

**Fix:**
- The companion server has a 300s (5 min) read timeout for Hermes API calls
- The app has a 300s read timeout for companion server calls
- If your tasks regularly exceed 5 minutes, consider breaking them into smaller tasks

### 8. "hermes binary not found"

**Cause:** The Hermes CLI is not at the expected path.

**Fix:**
```bash
# Find the binary
which hermes

# Either add to PATH or set the full path in the server config
export PATH="$PATH:/path/to/hermes/bin"
```

### 9. Attachment upload fails

**Cause:** File too large, or attachments directory not writable.

**Fix:**
- Max file size is 10 MB
- Ensure the attachments directory exists and is writable:
  ```bash
  mkdir -p ~/.hermes/companion/attachments
  chmod 700 ~/.hermes/companion/attachments
  ```

### 10. Auth file changes not taking effect

**Cause:** The auth file mtime hasn't changed (e.g., some editors write to a temp file and rename).

**Fix:**
```bash
# Force a touch to update mtime
touch ~/.hermes/companion/auth.json

# Or restart the server
pkill -f "python server.py" && python server.py
```

## Debugging Tips

### Server Logs

The companion server logs to stdout:
```
2026-01-01 12:00:00 [companion] INFO Companion daemon starting on 127.0.0.1:8777
2026-01-01 12:00:01 [companion] ERROR Failed to load auth.json: [Errno 2] No such file...
2026-01-01 12:00:02 [companion] INFO Hermes API error: Connection refused
```

### Test Auth

```bash
curl -u username:password http://localhost:8777/api/sessions
```

### Test with Verbose Output

```bash
curl -v -u username:password http://localhost:8777/health
```

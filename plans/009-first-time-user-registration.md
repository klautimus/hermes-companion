# Plan 009: First-Time User Registration Flow

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat e44d810..HEAD -- app/src/main/java/org/hermes/community/companion/SetupWizardScreen.kt app/src/main/java/org/hermes/community/companion/data/ApiClient.kt`
> Also: `cd ~/.hermes/companion && git diff --stat 98f270d..HEAD -- server.py`

## Status

- **Priority**: P0
- **Effort**: L
- **Risk**: MED
- **Depends on**: none (this plan includes both daemon + Android changes)
- **Category**: feature (public/open-source requirement)
- **Planned at**: commit `e44d810` (Android), `98f270d` (daemon), 2026-06-19

## Why this matters

This app is being open-sourced. Currently it only works if someone has already run `hermes-companion setup` on the server and manually pre-provisioned credentials in `auth.json`. A fresh deployment has no way for a first-time user to create their username and password from the app. The setup wizard's credential entry screen assumes credentials already exist — it tests the connection with dummy `test`/`test` credentials (SetupWizardScreen.kt line 369).

Additionally, the daemon has NO `/api/setup/redeem` endpoint. The Android app's `redeemSetupToken()` calls `POST /api/setup/redeem` (ApiClient.kt line 195) but the daemon returns 404. The QR setup token flow is completely broken.

For public use, we need:
1. A daemon endpoint `POST /api/setup/register` that creates the FIRST user (only works when no users exist yet)
2. A daemon endpoint `POST /api/setup/redeem` that exchanges a setup token for real credentials (fixes QR flow)
3. An Android screen that lets first-time users choose a username and password

## Current state

### Android SetupWizardScreen.kt ServerConnectionScreen (lines 330-389)
- URL entry field + QR scan button + test connection button
- Test connection uses hardcoded `ApiClient(config.serverUrl, "test", "test")` (line 369)
- No registration flow — no way to CREATE a user from the app

### Android ApiClient.kt redeemSetupToken (lines 193-223)
- Calls `POST /api/setup/redeem` with a JSON body containing the token
- Expects response with username, password, host, port, board
- This endpoint DOES NOT EXIST on the daemon — returns 404

### Daemon server.py (lines 752-783)
Routes registered: `/api/sessions`, `/v1/chat/completions`, `/api/kanban/*`, `/api/attachments`, `/healthz`, `/health`
NO `/api/setup/register` or `/api/setup/redeem` endpoint.

### Daemon setup_wizard.py
- `register_setup_token_wizard()` (line 243): writes `setup_token.json` with `{"tokens": [{"token": ..., "username": ..., "password": ..., "board": ..., "created_at": ...}]}`
- `generate_qr_code()` (line 163): generates `hermescompanion://configure?url=...&user=...&token=...&board=...`
- The token is stored alongside the plaintext password in `setup_token.json`
- No daemon endpoint reads this file — the token is orphaned

### Daemon auth.json format
```json
{"users": {"<username>": {"password_hash": "scrypt$16384$8$1$<salt_hex>$<b64_hash>", "created_at": "..."}}}
```

### Daemon BasicAuth class (server.py lines 1-50)
- `load_auth()` reads auth.json
- `check_password()` uses `hashlib.scrypt` with parameters from the stored hash
- mtime-based reload: re-reads auth.json if file mtime changed

## Commands you will need

| Purpose   | Command                          | Expected on success |
|-----------|----------------------------------|---------------------|
| Build Android | `cd ~/.hermes/projects/HermesCompanion && ./gradlew assembleDebug` | BUILD SUCCESSFUL |
| Test daemon   | `cd ~/.hermes/companion && python3 -m pytest tests/` | all pass |
| Manual: register | `curl -s http://127.0.0.1:8777/api/setup/register -d '{"username":"test","password":"testpass123"}' -H 'Content-Type: application/json'` | 201 Created |
| Manual: redeem | `curl -s http://127.0.0.1:8777/api/setup/redeem -d '{"token":"<token>"}' -H 'Content-Type: application/json'` | 200 with credentials |

## Suggested executor toolkit

### CodeGraph-first codebase exploration (mandatory)

**Daemon**:
1. `mcp_codegraph_codegraph_status(projectPath="/home/kevin/.hermes/companion")`
2. `mcp_codegraph_codegraph_explore(query="BasicAuth auth_users check_password load_auth create_app", projectPath="/home/kevin/.hermes/companion")`

**Android**:
1. `mcp_codegraph_codegraph_status(projectPath="/home/kevin/.hermes/projects/HermesCompanion")`
2. `mcp_codegraph_codegraph_explore(query="SetupWizardScreen redeemSetupToken ApiClient", projectPath="/home/kevin/.hermes/projects/HermesCompanion")`

## Scope

**In scope**:
- `~/.hermes/companion/server.py` — add `handle_setup_register` + `handle_setup_redeem` handlers + route registration
- `~/.hermes/projects/HermesCompanion/app/src/main/java/org/hermes/community/companion/SetupWizardScreen.kt` — add CreateAccountScreen composable
- `~/.hermes/projects/HermesCompanion/app/src/main/java/org/hermes/community/companion/data/ApiClient.kt` — add `registerUser()` function

**Out of scope**:
- Multi-user management (not needed for v1)
- Password change/reset flows (deferred)
- Daemon systemd deployment (plan 012)
- Removing the CLI setup wizard (keep it for headless deployments)

## Git workflow

**Daemon** (`~/.hermes/companion/`):
- Branch: `feature/setup-register-redeem`
- Commit: `feat(daemon): add /api/setup/register and /api/setup/redeem endpoints`

**Android** (`~/.hermes/projects/HermesCompanion/`):
- Branch: `feature/user-registration`
- Commit: `feat(android): add first-time user registration screen`

## Steps

### Step 1: Add `handle_setup_register` to daemon server.py

Add this handler function in `~/.hermes/companion/server.py` BEFORE the `create_app()` function:

```python
async def handle_setup_register(request):
    """Create the first user account. Only works when no users exist."""
    import json as _json
    import hashlib, base64, secrets

    config = request.app["config"]
    paths = config.get_expanded_paths()
    auth_file = paths["auth_file"]

    # Read existing auth.json
    try:
        auth_data = _json.loads(auth_file.read_text())
    except (FileNotFoundError, _json.JSONDecodeError):
        auth_data = {"users": {}}

    # SECURITY: Only allow registration if no users exist
    if auth_data.get("users"):
        return web.json_response(
            {"error": {"message": "Registration is closed. Ask your administrator for credentials."}},
            status=403
        )

    try:
        body = await request.json()
    except Exception:
        return web.json_response({"error": {"message": "Invalid JSON body"}}, status=400)

    username = body.get("username", "").strip()
    password = body.get("password", "")

    if not username or not password:
        return web.json_response({"error": {"message": "Username and password are required"}}, status=400)
    if len(username) < 3 or len(password) < 8:
        return web.json_response({"error": {"message": "Username must be >=3 chars, password >=8 chars"}}, status=400)

    # Hash password with scrypt (same format as server.py BasicAuth)
    salt = secrets.token_bytes(16)
    hash_bytes = hashlib.scrypt(password.encode(), salt=salt, n=16384, r=8, p=1, dklen=32)
    b64hash = base64.b64encode(hash_bytes).decode()
    password_hash = f"scrypt$16384$8$1${salt.hex()}${b64hash}"

    auth_data = {"users": {username: {"password_hash": password_hash, "created_at": "2026-01-01"}}}
    auth_file.parent.mkdir(parents=True, exist_ok=True)
    auth_file.write_text(_json.dumps(auth_data, indent=2))
    auth_file.chmod(0o600)

    return web.json_response({"status": "ok", "message": f"User '{username}' created"}, status=201)
```

**Verify**: `python3 -c "import ast; ast.parse(open('/home/kevin/.hermes/companion/server.py').read()); print('syntax ok')"`

### Step 2: Add `handle_setup_redeem` to daemon server.py

Add this handler AFTER `handle_setup_register`:

```python
async def handle_setup_redeem(request):
    """Redeem a one-time setup token for actual credentials."""
    import json as _json
    from datetime import datetime, timezone

    config = request.app["config"]
    paths = config.get_expanded_paths()
    token_file = paths["config_dir"] / "setup_token.json"

    if not token_file.exists():
        return web.json_response(
            {"error": {"message": "No setup tokens. Run hermes-companion setup on the server first."}},
            status=404
        )

    try:
        body = await request.json()
        token = body.get("token", "")
    except Exception:
        return web.json_response({"error": {"message": "Invalid request"}}, status=400)

    try:
        token_data = _json.loads(token_file.read_text())
    except (FileNotFoundError, _json.JSONDecodeError):
        return web.json_response({"error": {"message": "Token file corrupted"}}, status=500)

    for entry in token_data.get("tokens", []):
        if entry.get("token") == token:
            created_at = datetime.fromisoformat(entry["created_at"].replace("Z", "+00:00"))
            age = (datetime.now(timezone.utc) - created_at).total_seconds()
            if age > 300:
                return web.json_response({"error": {"message": "Token expired. Regenerate QR code."}}, status=410)
            return web.json_response({
                "username": entry["username"],
                "password": entry["password"],
                "host": config.server.host,
                "port": config.server.port,
                "board": entry.get("board", "default"),
            })

    return web.json_response({"error": {"message": "Invalid setup token"}}, status=403)
```

### Step 3: Register both routes in create_app()

In `~/.hermes/companion/server.py` inside `create_app()`, add these routes AFTER the health routes (line 753) and BEFORE the auth-protected routes (line 756):

```python
    # Setup routes (unauthenticated — must be before @web.middleware auth)
    app.router.add_post("/api/setup/register", handle_setup_register)
    app.router.add_post("/api/setup/redeem", handle_setup_redeem)
```

**IMPORTANT**: Check the auth middleware. The `@web.middleware` BasicAuth middleware likely exempts `/healthz` and `/health`. You MUST also add `/api/setup/register` and `/api/setup/redeem` to the exemption list so they work without authentication.

Look for code like:
```python
if request.path in ("/healthz", "/health"):
    return await handler(request)
```
Change to:
```python
if request.path.startswith("/api/setup/") or request.path in ("/healthz", "/health"):
    return await handler(request)
```

**Verify**: Restart daemon (`systemctl --user restart hermes-companion` or kill + restart). Then:
```bash
curl -s http://127.0.0.1:8777/api/setup/register -d '{"username":"test","password":"testpass123"}' -H 'Content-Type: application/json'
```
Expected: `{"status":"ok","message":"User 'test' created"}`

**IMPORTANT**: After testing registration, RESTORE the original auth.json — registration created a test user that will prevent future registrations:
```bash
# Save the test auth, restore original
cp ~/.hermes/companion/auth.json ~/.hermes/companion/auth.json.test
cp ~/.hermes/companion/auth.json.bak ~/.hermes/companion/auth.json
# Or manually remove the test user from auth.json
```

### Step 4: Add `registerUser()` to Android ApiClient.kt

In `ApiClient.kt`, add after the existing `redeemSetupToken()` function (around line 223):

```kotlin
/**
 * Register the first user account on a fresh daemon.
 * This call is UNAUTHENTICATED.
 */
suspend fun registerUser(baseUrl: String, username: String, password: String): Boolean = withContext(Dispatchers.IO) {
    val url = baseUrl.removeSuffix("/") + "/api/setup/register"
    val jsonBody = """{"username":"$username","password":"$password"}"""
    val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
    val request = Request.Builder()
        .url(url)
        .post(body)
        .header("Accept", "application/json")
        .build()
    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    client.newCall(request).execute().use { response ->
        if (response.code == 201) return@use true
        val respBody = response.body?.string() ?: ""
        throw ApiException(response.code, "Registration failed (${response.code})")
    }
}
```

**Verify**: `cd ~/.hermes/projects/HermesCompanion && ./gradlew assembleDebug 2>&1 | tail -3` → BUILD SUCCESSFUL

### Step 5: Add CreateAccountScreen to SetupWizardScreen.kt

In `SetupWizardScreen.kt`, add a new composable. Place it near the other screen composables (after `ServerConnectionScreen`):

```kotlin
@Composable
fun CreateAccountScreen(
    serverUrl: String,
    onAccountCreated: (username: String, password: String) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Create Your Account", style = MaterialTheme.typography.headlineSmall)
        Text(
            "This server has no users yet. Create the first admin account.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username (min 3 chars)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (min 8 chars)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = {
                scope.launch {
                    loading = true
                    error = null
                    try {
                        if (password != confirmPassword) {
                            error = "Passwords do not match"
                            return@launch
                        }
                        ApiClient.registerUser(serverUrl, username, password)
                        onAccountCreated(username, password)
                    } catch (e: Exception) {
                        error = e.message
                    } finally {
                        loading = false
                    }
                }
            },
            enabled = username.length >= 3 && password.length >= 8 &&
                      password == confirmPassword && !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Create Account")
            }
        }

        TextButton(onClick = onBack) { Text("Back") }
    }
}
```

Then wire it into the wizard's screen state. The wizard should show CreateAccountScreen when:
1. The user has entered a server URL
2. The server connection test succeeded (health endpoint OK)
3. But credential entry/test failed with 401

Add a "Create Account" button on the credential entry screen that navigates to this new screen.

**Verify**: `cd ~/.hermes/projects/HermesCompanion && ./gradlew assembleDebug 2>&1 | tail -3` → BUILD SUCCESSFUL

### Step 6: Commit both repos

```bash
# Daemon
cd ~/.hermes/companion
git add server.py
git commit -m "feat(daemon): add /api/setup/register and /api/setup/redeem endpoints

- /api/setup/register: creates first user when no users exist (open-source requirement)
- /api/setup/redeem: exchanges setup token for credentials (fixes broken QR flow)
Both endpoints are unauthenticated and exempted from auth middleware."

# Android
cd ~/.hermes/projects/HermesCompanion
git add app/src/main/java/org/hermes/community/companion/SetupWizardScreen.kt app/src/main/java/org/hermes/community/companion/data/ApiClient.kt
git commit -m "feat(android): add first-time user registration + fix QR setup token flow

Add CreateAccountScreen for fresh deployments where no users exist yet.
Add registerUser() API client method.
Wire into setup wizard so users can create credentials from the app."
```

## Done criteria

- [ ] Daemon: `POST /api/setup/register` returns 201 for valid first-user registration with no existing users
- [ ] Daemon: `POST /api/setup/register` returns 403 when users already exist
- [ ] Daemon: `POST /api/setup/redeem` returns 200 with credentials for valid non-expired token
- [ ] Daemon: `POST /api/setup/redeem` returns 410 for expired token (>5 min old)
- [ ] Daemon: both endpoints work WITHOUT authentication (exempted from BasicAuth middleware)
- [ ] Android: `registerUser()` function exists in ApiClient.kt
- [ ] Android: `CreateAccountScreen` composable exists and compiles
- [ ] `./gradlew assembleDebug` exits 0
- [ ] Daemon `python3 -c "import ast; ast.parse(open('server.py').read())"` exits 0
- [ ] `git status` is CLEAN in both repos
- [ ] `plans/README.md` status row updated

## STOP conditions

- If `config.get_expanded_paths()` doesn't exist or returns different keys — check `config_schema.py` for the actual method. The key for auth_file may be named differently.
- If the auth middleware exemption pattern is different from `request.path in (...)` — adapt accordingly. Read the `@web.middleware` function in server.py lines 1-50.
- If the Android wizard's screen navigation doesn't support adding a new screen — check how screens are currently switched (likely a `WizardStep` enum or manual state variable).

## Maintenance notes

- After this lands, the CLI `setup_wizard.py` is still useful for headless/server-side setups. Keep it.
- The `setup_token.json` stores plaintext password — the 5-minute TTL mitigates risk. Future: use token as decryption key instead of lookup.
- The register endpoint creates ONLY the first user. Future: admin-only user management API for multi-user.

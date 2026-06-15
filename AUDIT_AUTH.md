# Auth & Credentials Deep Audit — Hermes Companion

## Credential Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        ANDROID APP                                   │
│                                                                      │
│  First Launch:                                                       │
│    SessionManager.DEFAULT_USERNAME = "kevin"                         │
│    SessionManager.DEFAULT_PASSWORD = ""  (EMPTY STRING)              │
│                                                                      │
│  SettingsScreen:                                                     │
│    passInput starts as "" (line 36)                                  │
│    Test button: ApiClient(url, user, passInput.ifBlank { DEFAULT })  │
│    Save button: viewModel.saveSettings(url, user, passInput)         │
│                                                                      │
│  MainViewModel.client():                                             │
│    Uses _password (MutableStateFlow, initialized to DEFAULT_PASSWORD)│
│    Updated ONLY via saveSettings()                                   │
│                                                                      │
│  MainViewModel.saveSettings():                                       │
│    session.setBaseUrl(url)                                           │
│    session.setUsername(user)                                         │
│    if (password.isNotBlank()) session.setPassword(password)          │
│    _password.value = password.ifBlank { _password.value }            │
│    ─── KEY: if password is blank, KEEP the old _password.value ────  │
└──────────────────────────┬──────────────────────────────────────────┘
                           │ HTTP Basic Auth
                           │ Authorization: Basic base64("username:password")
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     COMPANION SERVER (port 8777)                     │
│                                                                      │
│  BasicAuth.check():                                                  │
│    1. Decode Base64 from Authorization header                        │
│    2. Split on ":" → username, password                              │
│    3. Look up username in auth.json users dict                       │
│    4. If hash starts with "scrypt$":                                 │
│         - Parse N, r, p, salt_hex, expected_hash                     │
│         - Compute scrypt(password, salt, N, r, p, dklen=32)         │
│         - Base64 encode result                                       │
│         - Compare to expected                                        │
│    5. If hash does NOT start with "scrypt$":                         │
│         - Plain string comparison (hash == password)                 │
└──────────────────────────┬──────────────────────────────────────────┘
                           │ reads
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     /home/kevin/.hermes/companion/auth.json          │
│                                                                      │
│  {                                                                   │
│    "users": {                                                        │
│      "kevin": {                                                      │
│        "password_hash": "scrypt$16384$8$1$<salt>$<b64hash>",        │
│        "created_at": "2026-06-14"                                    │
│      },                                                              │
│      "admin": { ... }                                                │
│    }                                                                 │
│  }                                                                   │
│                                                                      │
│  Format: scrypt$N$r$p$<salt-hex>$<hash-b64>                         │
└─────────────────────────────────────────────────────────────────────┘
```

## Findings

### F-01: CRITICAL — Empty password sent by app does NOT match auth.json hash

**File:** `SessionManager.kt:16` / `server.py:81` / `auth.json:4`

**Severity:** CRITICAL — This is the root cause of "Invalid credentials" on first launch.

**What happens:**
1. App launches for the first time. `SessionManager.DEFAULT_PASSWORD = ""` (empty string).
2. User taps "Test Connection" or any API call. `ApiClient` encodes `Basic base64("kevin:")` — note the empty password after the colon.
3. Server decodes this, gets `username="kevin"`, `password=""`.
4. Server looks up `auth.json`, finds `kevin`'s hash starts with `scrypt$`.
5. Server runs `hashlib.scrypt("".encode(), salt=..., n=16384, r=8, p=1, dklen=32)`.
6. The computed hash is `46lKxbiTo5SSJA1rzooQf8tytc+qaUpx68i7itZlHzI=`.
7. The stored hash is `tHKMMDq+SDl2O/lyucRrXd+/gByGE6OidMhd/6B9UsQ=`.
8. **MISMATCH → 401 "Invalid credentials"**.

**Verified by:** Running the actual scrypt computation against the stored hash with an empty password. Result: MISMATCH.

**Root cause:** The scrypt hash in `auth.json` was generated from a non-empty password, but the app's `DEFAULT_PASSWORD` is `""`. The two are inconsistent.

**Fix options:**
- **Option A (recommended):** Change `DEFAULT_PASSWORD` in `SessionManager.kt` to match whatever password was used to generate the auth.json hash. This requires knowing that password.
- **Option B:** Regenerate `auth.json` with a hash derived from an empty password (not recommended — empty password = no auth).
- **Option C (best):** Remove the default password entirely. Force the user to set a password on first launch before any API calls can be made. Show a setup screen.

---

### F-02: CRITICAL — Test Connection button silently falls back to empty password

**File:** `SettingsScreen.kt:93`

**Severity:** CRITICAL — Makes diagnosis nearly impossible for the user.

**Code:**
```kotlin
val c = ApiClient(urlInput, userInput, passInput.ifBlank { SessionManager.DEFAULT_PASSWORD })
```

When the user leaves the password field empty (which it is by default), `passInput.ifBlank { ... }` returns `DEFAULT_PASSWORD` which is `""`. The test connection then sends an empty password, which fails with 401. The user sees "Failed: Invalid credentials" but has no way to know *why* — the error message doesn't distinguish between "wrong password" and "no password configured."

**Fix:** Show a clear message when the password is empty: "Please set a password first" instead of attempting a connection that is guaranteed to fail.

---

### F-03: MAJOR — saveSettings() does NOT persist blank passwords correctly

**File:** `MainViewModel.kt:452-462`

**Severity:** MAJOR — Password persistence is broken at the boundary.

**Code:**
```kotlin
fun saveSettings(url: String, user: String, password: String) {
    viewModelScope.launch {
        session.setBaseUrl(url)
        session.setUsername(user)
        if (password.isNotBlank()) session.setPassword(password)
        _password.value = password.ifBlank { _password.value }
        ...
    }
}
```

Two issues:
1. If the user clears the password field and saves, `password.isNotBlank()` is false, so `setPassword()` is **never called**. The old password remains in DataStore.
2. `_password.value = password.ifBlank { _password.value }` — if password is blank, keeps the old value. This is *intentional* (don't overwrite with empty), but combined with F-01, it means the password is **always** empty on first launch and can never be "cleared" to empty.

**Fix:** This behavior is actually reasonable (don't let users accidentally clear their password), but it needs to be paired with a proper first-run setup flow.

---

### F-04: MAJOR — No first-run setup flow

**File:** `SessionManager.kt:14-18`, `SettingsScreen.kt:36`

**Severity:** MAJOR — App is unusable out of the box.

The app ships with `DEFAULT_PASSWORD = ""` and `DEFAULT_USERNAME = "kevin"`. There is no onboarding screen, no "set your password" prompt, no indication that credentials need to be configured. The user is expected to know they need to go to Settings and enter a password — but the password they enter must match whatever was used to generate the auth.json hash, which is undocumented.

**Fix:** Add a first-run setup screen that:
1. Detects if DataStore has no password set (first launch)
2. Prompts the user to set a username and password
3. Optionally: generate the auth.json hash from the user's chosen password

---

### F-05: MINOR — Test Connection uses a throwaway ApiClient, not the ViewModel's client

**File:** `SettingsScreen.kt:93`

**Severity:** MINOR — Inconsistent auth state.

**Code:**
```kotlin
val c = ApiClient(urlInput, userInput, passInput.ifBlank { SessionManager.DEFAULT_PASSWORD })
```

The test button creates a *new* `ApiClient` with the raw input values. But the ViewModel's `client()` method uses `_password` (the persisted MutableStateFlow). This means:
- Test Connection tests with the *input field* password
- Actual API calls (chat, kanban, sessions) use the *persisted* password
- If the user tests successfully but doesn't save, the app still uses the old password
- If the user saves but the test used a different password, the test result is misleading

**Fix:** Either use the ViewModel's `client()` for testing, or make the test button save settings first.

---

### F-06: MINOR — ApiClient constructor takes password as plain String, stored in memory

**File:** `ApiClient.kt:26-30`

**Severity:** MINOR — Security concern.

The password is stored as a plain `String` in the `ApiClient` object and used to construct the auth header on every request. In Kotlin/JVM, strings are immutable and can't be zeroed out. This is a minor concern for a local Android app but worth noting.

**Fix:** Use `CharArray` instead of `String` for the password field, or use a credential store.

---

### F-07: MINOR — Server auth.json is world-readable

**File:** `auth.json` (file permissions)

**Severity:** MINOR — The auth.json file containing password hashes is readable by all users on the system.

**Fix:** `chmod 600 /home/kevin/.hermes/companion/auth.json`

---

### F-08: INFO — scrypt hash format uses `$` delimiter but doesn't URL-encode

**File:** `server.py:78`, `auth.json:13`

**Severity:** INFO — No current issue, but fragile.

The hash format is `scrypt$N$r$p$salt_hex$hash_b64`. The `$` delimiter works because neither hex nor base64 encode `$`. But if the format ever changes to include characters that overlap with delimiters, parsing will break. This is fine as-is.

---

## Root Cause Summary

**The "Invalid credentials" error is caused by a password mismatch between the app defaults and the server's auth.json:**

1. `SessionManager.kt` sets `DEFAULT_PASSWORD = ""` (empty string)
2. On first launch, the app sends `Authorization: Basic base64("kevin:")` — empty password
3. `auth.json` contains a scrypt hash that was generated from a **non-empty** password
4. Server computes `scrypt("")` and compares to the stored hash → **MISMATCH → 401**

**The password that was used to generate the auth.json hash is unknown.** Neither `"kevin"` nor `"password"` match (verified by computation). The hash was likely generated by a setup script or manually with a specific password that was never communicated to the app's default configuration.

## Verdict: MUST-FIX

Three issues must be resolved before the app can work:

1. **F-01:** Align `DEFAULT_PASSWORD` with the actual password used to generate auth.json, OR regenerate auth.json with a known password
2. **F-04:** Add a first-run setup flow so users configure credentials before first API call
3. **F-02:** Improve error messaging so users understand *why* auth failed

## Test Log

```
=== Auth.json contents ===
  user: kevin, hash_prefix: scrypt$16384$8$1$53c...
  user: admin, hash_prefix: scrypt$16384$8$1$795...

Testing username='kevin', password='' (empty)
Hash from auth.json: scrypt$16384$8$1$53c73b6a190d28ca0febf2d3b9d4608c4c043b203c2...
Scrypt verification result: MISMATCH
  Expected: tHKMMDq+SDl2O/lyucRrXd+/gByGE6OidMhd/6B9UsQ=
  Computed: 46lKxbiTo5SSJA1rzooQf8tytc+qaUpx68i7itZlHzI=

=== Testing with 'kevin' as password ===
password='kevin': MISMATCH

=== Testing with 'password' as password ===
password='password': MISMATCH
```

Full test script: `/home/kevin/.hermes/kanban/boards/companion-audit/workspaces/t_ab5e2bd3/test_auth.py`
Test output: `/home/kevin/.hermes/kanban/boards/companion-audit/workspaces/t_ab5e2bd3/auth_test.log`

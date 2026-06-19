# Plan 014: Fix Connection Test Using Dummy Credentials + Other Bugs

> **Executor instructions**: Follow this plan step by step.
>
> **Drift check (run first)**: `cd ~/.hermes/projects/HermesCompanion && git diff --stat e44d810..HEAD -- app/src/main/java/org/hermes/community/companion/SetupWizardScreen.kt app/src/main/java/org/hermes/community/companion/data/ApiClient.kt`

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: bug
- **Planned at**: commit `e44d810`, 2026-06-19

## Why this matters

Multiple bugs found during the audit that are individually small but collectively degrade the user experience:

1. **Connection test uses dummy credentials**: SetupWizardScreen.kt line 369 tests connection with `ApiClient(config.serverUrl, "test", "test")`. This sends an authenticated request with fake credentials. The daemon returns 401, but the health endpoint doesn't require auth, so the test appears to succeed even though credentials are wrong. The test should hit `/health` WITHOUT any auth header.

2. **Daemon password hash stored in auth.json.bak2**: The file `~/.hermes/companion/auth.json.bak2` exists on disk with credential data. Backup files should be in `.gitignore` and cleaned up.

3. **setup_token.json stores plaintext password**: The file `setup_token.json` at line 252 stores the actual password alongside the token. When the redeem endpoint (Plan 009) serves this, the plaintext password is exposed via API. This is the design trade-off for the token flow — the 5-minute TTL mitigates risk.

4. **MarkdownText.kt is a complete stub**: Covered in Plan 007 — not duplicated here.

5. **MessageList doesn't call renderMarkdown**: Even the existing stub `renderMarkdown()` is never called from MessageList. Covered in Plan 007.

## Current state

### SetupWizardScreen.kt line 369
```kotlin
val c = ApiClient(config.serverUrl, "test", "test")
val raw = c.get("/health")
```
The health endpoint `/health` doesn't require auth, so even with fake credentials the request succeeds. But this is misleading — the user thinks their credentials work when they might not.

### ApiClient.kt get() method (line 44-51)
Every request adds an `Authorization: Basic <base64>` header. Even health checks send credentials. The health endpoint ignores them, but the request log on the daemon shows auth failures for "test:test".

## Scope

**In scope**:
- `app/src/main/java/org/hermes/community/companion/SetupWizardScreen.kt` — fix connection test to use unauthenticated health check
- `app/src/main/java/org/hermes/community/companion/data/ApiClient.kt` — add `healthCheck()` method that sends no auth header

**Out of scope**:
- Daemon changes
- auth.json backup cleanup (server-side maintenance)

## Steps

### Step 1: Add unauthenticated healthCheck to ApiClient

In `ApiClient.kt`, add a standalone function (outside the ApiClient class, near `redeemSetupToken`):

```kotlin
/**
 * Check server health WITHOUT authentication.
 * Used by the setup wizard to test connectivity before credentials are known.
 */
suspend fun checkServerHealth(baseUrl: String): CompanionHealth = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    val request = Request.Builder()
        .url("${baseUrl.removeSuffix("/")}/health")
        .header("Accept", "application/json")
        .build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw ApiException(response.code, "Server returned ${response.code}")
        }
        val body = response.body?.string() ?: throw ApiException(0, "Empty response")
        val json = Json { ignoreUnknownKeys = true }
        json.decodeFromString<CompanionHealth>(body)
    }
}
```

**Verify**: `./gradlew assembleDebug 2>&1 | tail -3` → BUILD SUCCESSFUL

### Step 2: Fix SetupWizardScreen connection test

In `SetupWizardScreen.kt` line 363-389, replace the test connection handler:

Change from:
```kotlin
val c = ApiClient(config.serverUrl, "test", "test")
val raw = c.get("/health")
val health = Json { ignoreUnknownKeys = true }
    .decodeFromString<CompanionHealth>(raw)
```

To:
```kotlin
val health = checkServerHealth(config.serverUrl)
```

**Verify**: `./gradlew assembleDebug 2>&1 | tail -3` → BUILD SUCCESSFUL

### Step 3: Add .gitignore entries for backup files

In `~/.hermes/companion/.gitignore`, ensure these are listed:
```
auth.json.bak
auth.json.bak2
setup_token.json
```

**Verify**: `cd ~/.hermes/companion && git status --porcelain | grep auth.json.bak` → empty (files are ignored)

### Step 4: Commit

```bash
# Android
cd ~/.hermes/projects/HermesCompanion
git add app/src/main/java/org/hermes/community/companion/data/ApiClient.kt app/src/main/java/org/hermes/community/companion/SetupWizardScreen.kt
git commit -m "fix: use unauthenticated health check instead of dummy credentials

The connection test in the setup wizard was using ApiClient with fake
test/test credentials. Replace with checkServerHealth() that sends no
auth header, matching the actual use case (testing connectivity before
credentials are known)."

# Daemon
cd ~/.hermes/companion
git add .gitignore
git commit -m "chore: add auth.json backups and setup_token.json to .gitignore"
```

## Done criteria

- [ ] `./gradlew assembleDebug` exits 0
- [ ] `checkServerHealth()` function exists in ApiClient.kt and sends NO Authorization header
- [ ] SetupWizardScreen uses `checkServerHealth()` instead of `ApiClient(url, "test", "test")`
- [ ] `~/.hermes/companion/.gitignore` contains `auth.json.bak`, `auth.json.bak2`, `setup_token.json`
- [ ] `git status` is CLEAN in both repos

## STOP conditions

- If `CompanionHealth` is not importable from ApiClient.kt's package — check the import path. It may need `import org.hermes.community.companion.data.CompanionHealth`.
- If `checkServerHealth` needs to be a companion object method or static — place it at the file level (like `redeemSetupToken`).

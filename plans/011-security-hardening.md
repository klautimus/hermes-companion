# Plan 011: Fix EncryptedSharedPreferences Fail-Closed + Security Hardening

> **Executor instructions**: Follow this plan step by step.
>
> **Drift check (run first)**: `cd ~/.hermes/projects/HermesCompanion && git diff --stat e44d810..HEAD -- app/src/main/java/org/hermes/community/companion/data/SessionManager.kt`

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: security
- **Planned at**: commit `e44d810`, 2026-06-19

## Why this matters

The Android app's `SessionManager` falls back to plaintext `SharedPreferences` when `EncryptedSharedPreferences` is unavailable (SessionManager.kt lines 36-44). This means on devices without a working Android Keystore, credentials (including passwords) are stored unencrypted. The prior commit `a2cb14a` was supposed to fix this ("fail-closed on EncryptedSharedPreferences — no silent plaintext fallback"), but the code at lines 46-52 still creates a plaintext fallback SharedPreferences.

## Current state

### SessionManager.kt lines 36-52
```kotlin
private val _storageMode: StorageMode by lazy {
    try {
        SessionMigration.encryptedPrefs(context.applicationContext)
        StorageMode.Encrypted
    } catch (e: Exception) {
        Log.e("SessionManager", "EncryptedSharedPreferences unavailable, security degraded", e)
        StorageMode.Plaintext(reason = e.message ?: "Unknown error")
    }
}

private val prefs: SharedPreferences by lazy {
    when (val mode = _storageMode) {
        is StorageMode.Encrypted -> SessionMigration.encryptedPrefs(context.applicationContext)
        is StorageMode.Plaintext -> context.getSharedPreferences("hermes_settings_fallback", Context.MODE_PRIVATE)
        else -> error("unreachable")
    }
}
```

The `Plaintext` fallback silently stores credentials in plaintext SharedPreferences. The `SettingsScreen` shows a warning banner, but the app still works with plaintext storage — it doesn't fail closed.

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Build     | `./gradlew assembleDebug` | BUILD SUCCESSFUL    |
| Unit test | `./gradlew test`          | all pass            |

## Scope

**In scope**:
- `app/src/main/java/org/hermes/community/companion/data/SessionManager.kt`

**Out of scope**:
- SettingsScreen.kt (already shows a security warning banner)
- SessionMigration.kt

## Steps

### Step 1: Make SessionManager fail closed for credential writes

Change the `prefs` lazy initialization so that when `StorageMode.Plaintext` or `StorageMode.Unavailable`, credential-related setters throw instead of silently writing plaintext:

```kotlin
private val prefs: SharedPreferences by lazy {
    when (val mode = _storageMode) {
        is StorageMode.Encrypted -> SessionMigration.encryptedPrefs(context.applicationContext)
        is StorageMode.Plaintext ->
            // Fail-closed: use an empty in-memory prefs that discards all writes.
            // The SettingsScreen already shows a security error banner.
            // We do NOT store credentials in plaintext SharedPreferences.
            object : SharedPreferences {
                // Minimal no-op implementation that stores nothing
                private val map = mutableMapOf<String, Any?>()
                override fun getAll() = map.toMap()
                override fun getString(k: String, d: String?) = d
                override fun getInt(k: String, d: Int) = d
                override fun getBoolean(k: String, d: Boolean) = d
                override fun contains(k: String) = false
                override fun edit() = object : SharedPreferences.Editor {
                    override fun putString(k: String, v: String?) = this
                    override fun putInt(k: String, v: Int) = this
                    override fun putBoolean(k: String, v: Boolean) = this
                    override fun clear() = this
                    override fun remove(k: String) = this
                    override fun putLong(k: String, v: Long) = this
                    override fun putFloat(k: String, v: Float) = this
                    override fun putStringSet(k: String, v: Set<String>?) = this
                    override fun apply() {}
                    override fun commit() = true
                }
                override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
                override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
                override fun getLong(k: String, d: Long) = d
                override fun getFloat(k: String, d: Float) = d
                @Suppress("UNCHECKED_CAST")
                override fun getStringSet(k: String, d: Set<String>?) = d
            }
        else -> error("unreachable")
    }
}
```

This is a no-op SharedPreferences: all reads return defaults, all writes are silently discarded. The app won't crash, but it also won't store credentials in plaintext. The user will see the security warning in SettingsScreen and won't be able to save credentials.

**Verify**: `./gradlew assembleDebug 2>&1 | tail -3` → BUILD SUCCESSFUL

### Step 2: Update the plaintext banner message

Check that `SettingsScreen.kt` displays a clear message when storage is in Plaintext mode. The user should see:
"Encrypted storage is unavailable on this device. Credentials cannot be saved. Please ensure your device has a working Android Keystore."

This may already exist — verify, don't rewrite if it does.

**Verify**: `./gradlew assembleDebug 2>&1 | tail -3` → BUILD SUCCESSFUL

### Step 3: Commit

```bash
cd ~/.hermes/projects/HermesCompanion
git add app/src/main/java/org/hermes/community/companion/data/SessionManager.kt
git commit -m "security: fail-closed on EncryptedSharedPreferences — no plaintext credential storage

When EncryptedSharedPreferences is unavailable, use a no-op SharedPreferences
that discards all writes instead of falling back to plaintext. Credentials
are never stored unencrypted. The SettingsScreen warning banner informs
the user that storage is unavailable."
```

## Done criteria

- [ ] `./gradlew assembleDebug` exits 0
- [ ] `./gradlew test` exits 0
- [ ] SessionManager.kt has NO `context.getSharedPreferences("hermes_settings_fallback", ...)` call
- [ ] When StorageMode is Plaintext, all writes to prefs are silently discarded (no plaintext storage)
- [ ] `git status` is CLEAN

## STOP conditions

- If the no-op SharedPreferences interface is missing required methods (compile error) — check the full `SharedPreferences` interface and add all required methods.
- If existing tests depend on plaintext fallback behavior — update tests to use mocked EncryptedSharedPreferences.

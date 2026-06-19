# Plan 008: Add "Save Server URL" Checkbox on Initial Login Screen

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat e44d810..HEAD -- app/src/main/java/org/hermes/community/companion/SetupWizardScreen.kt app/src/main/java/org/hermes/community/companion/data/SessionManager.kt`

## Status

- **Priority**: P0
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: feature (UX improvement)
- **Planned at**: commit `e44d810`, 2026-06-19

## Why this matters

When a user enters the Hermes server URL on the initial setup screen, they currently must re-enter it every time they load the app if they haven't completed the full setup wizard. The URL should be optionally saved so the user doesn't have to type it repeatedly. This is especially important for first-time users who may close the app before completing the full wizard flow.

## Current state

### SetupWizardScreen.kt ServerConnectionScreen (lines 330-389)
The `ServerConnectionScreen` composable has:
- `OutlinedTextField` for server URL (line 333-342)
- QR scan button (line 353-360)
- Test connection button (line 363-389)
- No option to save/remember the URL

The URL is only persisted when the user reaches the final "Finish" screen of the setup wizard and clicks complete. If the user exits before finishing, the URL is lost.

### SessionManager.kt
Has `setBaseUrl(url: String)` method (line 76-78) and `baseUrl: Flow<String>` (line 70).
The key is stored in EncryptedSharedPreferences.
There is no separate "remember URL" flag — the URL is always saved or never saved depending on wizard completion.

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Build     | `./gradlew assembleDebug` | BUILD SUCCESSFUL    |
| Unit test | `./gradlew test`          | all pass            |

## Suggested executor toolkit

### CodeGraph-first codebase exploration (mandatory)

1. `mcp_codegraph_codegraph_status(projectPath="/home/kevin/.hermes/projects/HermesCompanion")`
2. `mcp_codegraph_codegraph_explore(query="ServerConnectionScreen SetupWizardScreen baseUrl setBaseUrl", projectPath="/home/kevin/.hermes/projects/HermesCompanion")`

## Scope

**In scope**:
- `app/src/main/java/org/hermes/community/companion/SetupWizardScreen.kt` — add checkbox to ServerConnectionScreen
- `app/src/main/java/org/hermes/community/companion/data/SessionManager.kt` — add `setRememberUrl` / `rememberUrl` flow

**Out of scope**:
- Do NOT modify the rest of the setup wizard flow
- Do NOT modify the daemon

## Git workflow

- Branch: `feature/save-url-checkbox`
- Commit per step; message style: `feat(ui): <description>`

## Steps

### Step 1: Add rememberUrl state to SessionManager

In `SessionManager.kt`, add a new preference key and flow after the existing `board` flow (around line 73):

```kotlin
    private val KEY_REMEMBER_URL = "remember_url"
    val rememberUrl: Flow<Boolean> = prefs.flowForBooleanKey(KEY_REMEMBER_URL, false)
```

Add the setter after `setBoard()` (around line 90):

```kotlin
    suspend fun setRememberUrl(remember: Boolean) {
        prefs.edit().putBoolean(KEY_REMEMBER_URL, remember).apply()
    }
```

**Verify**: `./gradlew assembleDebug 2>&1 | tail -3` → BUILD SUCCESSFUL

### Step 2: Add checkbox to ServerConnectionScreen

In `SetupWizardScreen.kt`, in the `ServerConnectionScreen` composable (around line 332), after the URL validation text and before the QR scan button (around line 352), add:

```kotlin
        // Save URL checkbox
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            androidx.compose.material3.Checkbox(
                checked = config.rememberUrl,
                onCheckedChange = { onConfigChange(config.copy(rememberUrl = it)) }
            )
            Text(
                "Remember this server URL",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
```

**Note**: The `rememberUrl` boolean needs to be added to whatever config data class the wizard uses. Check `WizardConfig` or equivalent — add `val rememberUrl: Boolean = true` as a default-true field.

**Verify**: `./gradlew assembleDebug 2>&1 | tail -3` → BUILD SUCCESSFUL

### Step 3: Persist URL when checkbox is checked and test connection succeeds

In the test connection success handler (around line 373), add:

```kotlin
                        // Auto-save URL if checkbox is checked
                        if (config.rememberUrl) {
                            sessionManager.setBaseUrl(config.serverUrl)
                        }
```

This requires passing `sessionManager` into the composable, or using the ViewModel. Check how other screens access SessionManager — follow the existing pattern (likely via MainViewModel).

**Verify**: `./gradlew assembleDebug 2>&1 | tail -3` → BUILD SUCCESSFUL

### Step 4: Commit

```bash
cd /home/kevin/.hermes/projects/HermesCompanion
git add app/src/main/java/org/hermes/community/companion/SetupWizardScreen.kt app/src/main/java/org/hermes/community/companion/data/SessionManager.kt
git commit -m "feat(ui): add save URL checkbox on initial login screen

Users can now optionally save the server URL so it persists across app
launches without completing the full setup wizard."
```

## Done criteria

- [ ] `./gradlew assembleDebug` exits 0
- [ ] `./gradlew test` exits 0
- [ ] ServerConnectionScreen has a Checkbox labeled "Remember this server URL"
- [ ] SessionManager has `rememberUrl` flow and `setRememberUrl` setter
- [ ] When checkbox is checked and connection succeeds, URL persists across app restarts
- [ ] `git status` is CLEAN

## STOP conditions

- If the `WizardConfig` data class doesn't exist or has a different name — search for the config data class used by SetupWizardScreen and add `rememberUrl` there.
- If `sessionManager` isn't accessible from within `ServerConnectionScreen` — use the ViewModel pattern instead.

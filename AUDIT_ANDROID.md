# Android App Deep Audit — HermesCompanion

**Date:** 2026-06-14
**Scope:** Full audit of 10 source files covering chat, kanban, settings, navigation, and data layers.

---

## CRITICAL Findings

### C1: `sendMessageWithAttachment` — `_isStreaming` never reset on error
**File:** `MainViewModel.kt:248-251`
**Severity:** CRITICAL
**Issue:** In `sendMessageWithAttachment`, the `catch` block sets `_chatError.value` and `_isStreaming.value = false`, but if the error occurs *before* `_isStreaming.value = true` is set (e.g. during the upload step at line 208), `_isStreaming` is never set to `true` in the first place — so the `false` assignment is harmless but misleading. The real problem: if the `c.chat(history)` call at line 245 throws, `_isStreaming` is correctly set to `false`, but the streaming placeholder message is **never finalized** — `finalizeAssistant` is not called in the catch block. The placeholder remains in the UI forever with `isStreaming = true` text but `_isStreaming = false`.
**Fix:** Add `finalizeAssistant(msgId, "(Error: ${e.message})")` to the catch block at line 248, matching the pattern in `sendMessage` (line 196).

### C2: `sendMessageWithAttachment` — session creation failure doesn't reset `_isStreaming`
**File:** `MainViewModel.kt:217-227`
**Severity:** CRITICAL
**Issue:** If session creation fails inside `sendMessageWithAttachment` (line 223-226), the function returns early but `_isStreaming` was never set to `true` yet — however, if the session creation *succeeds* and the subsequent `c.chat()` call fails, the placeholder is stuck (see C1). The early-return paths are inconsistent: some set `_chatError` but not `_isStreaming = false` (though `_isStreaming` may not have been set yet). The core issue is the missing `finalizeAssistant` in the catch.

### C3: `filteredSessions` in ViewModel is redundant with `SessionDrawer` filtering
**File:** `MainViewModel.kt:65-71` and `ChatScreen.kt:146-152`
**Severity:** Important (code smell / wasted resource)
**Issue:** The ViewModel exposes `filteredSessions` as a `StateFlow`, but `ChatScreen` never uses it — it passes `sessions` (unfiltered) to `SessionDrawer`, which does its own filtering via `remember(sessions, searchQuery)`. The ViewModel's `filteredSessions` flow is dead code that still consumes resources (combine + stateIn). Either use the ViewModel's filtered list or remove it and let the UI handle filtering.
**Fix:** Remove `filteredSessions` from ViewModel, or pass `vm.filteredSessions.collectAsState()` to `SessionDrawer` instead of raw `sessions`.

### C4: `renameBoard` — insufficient escaping of special characters
**File:** `MainViewModel.kt:327`
**Severity:** Important
**Issue:** `newName.replace("\"", "\\\"")` only escapes double quotes. It does not escape backslashes, newlines, or other JSON-special characters. A board name containing `\` or `\n` will produce malformed JSON.
**Fix:** Use `Json.encodeToString` or `JsonObject` to build the body, matching the pattern used in `commentOnTask` (line 400-405).

### C5: `assignTask` — no input sanitization on assignee string
**File:** `MainViewModel.kt:417`
**Severity:** Important
**Issue:** `"""{"assignee":"$assignee"}"""` — if `assignee` contains `"` or `\`, the JSON is malformed. Same issue as renameBoard.
**Fix:** Use `JsonObject`/`JsonPrimitive` to build the body safely.

### C6: `createBoard` — no input sanitization on slug/name strings
**File:** `MainViewModel.kt:314`
**Severity:** Important
**Issue:** `"""{"slug":"$slug","name":"$name"}"""` — if slug or name contains `"`, the JSON breaks.
**Fix:** Use `JsonObject`/`JsonPrimitive` for safe encoding.

---

## UI/UX Issues

### U1: Session drawer has no scrim/overlay — tapping outside doesn't dismiss
**File:** `ChatScreen.kt:42-53`
**Severity:** Important
**Issue:** The session drawer is rendered as an inline `if (showDrawer)` block inside the `Column`. There's no scrim overlay and no way to dismiss by tapping outside. The `onDismiss` callback is passed to `SessionDrawer` but never used (line 139: `@Suppress("UNUSED_PARAMETER") onDismiss`). The only way to close the drawer is to select a session or create a new one. If the user opens it accidentally, there's no escape.
**Fix:** Add a scrim overlay (Box with fillMaxSize + background tint) behind the drawer, or wire `onDismiss` to a close button.

### U2: `SessionDrawer` filtering is done in `remember` — not reactive to search changes
**File:** `ChatScreen.kt:146-152`
**Severity:** Important
**Issue:** `remember(sessions, searchQuery)` only recomposes when `sessions` or `searchQuery` references change. Since `searchQuery` is a `String` passed from the parent, this works — but the ViewModel's `sessionSearchQuery` flow is collected separately. The filtering is done twice: once in the ViewModel (dead code, see C3) and once in the UI. The `remember` approach is fine but the dead ViewModel flow is confusing.

### U3: Kanban board drawer — `AnimatedVisibility` with `slideInHorizontally` but no scrim tap-to-dismiss on the content area
**File:** `KanbanScreen.kt:126-146`
**Severity:** Important
**Issue:** The board drawer uses `AnimatedVisibility` with a scrim (`Surface` with `onClick = onDismiss` at line 435), but the inner `Surface` at line 437 uses `clickable(enabled = false) {}` to consume clicks. This works, but the `AnimatedVisibility` exit animation may conflict with the scrim dismissal — when `drawerOpen` is set to `false`, the `AnimatedVisibility` exit animation plays, but the state is already gone. This is a minor visual glitch.

### U4: `commentText` state is local to `KanbanScreen` — lost on recomposition
**File:** `KanbanScreen.kt:61`
**Severity:** Minor
**Issue:** `commentText` is `remember { mutableStateOf("") }` inside `KanbanScreen`. When the bottom sheet is dismissed and reopened, the comment text is reset. This is probably fine (sheet is dismissed on comment send), but if the sheet is dismissed by tapping outside, the draft comment is lost.
**Fix:** Consider hoisting comment text to ViewModel or using `rememberSaveable`.

### U5: `Composer` — `input` state is local, not synced with ViewModel
**File:** `Composer.kt:35` and `MainViewModel.kt:465-470`
**Severity:** Important
**Issue:** `Composer` has its own `var input by remember { mutableStateOf("") }` state, while `MainViewModel` also has `_inputText` / `inputText` / `setInputText` / `clearInput`. The ViewModel's input state is never used by `Composer`. The `onClear` callback calls `vm.clearInput()` but the local `input` is cleared independently. This is dead code in the ViewModel.
**Fix:** Either sync Composer with ViewModel's input state, or remove the dead input state from ViewModel.

### U6: `SettingsScreen` — `boardInput` is local, not persisted on Save
**File:** `SettingsScreen.kt:35,107-110`
**Severity:** Minor
**Issue:** The Save button calls `viewModel.setBoard(boardInput)` which persists to DataStore, but `boardInput` is initialized from `viewModel.boardSlug` only once (`remember { mutableStateOf(boardSlug) }`). If the board is changed via the Kanban tab drawer, the Settings screen won't reflect the new value when navigated to (since `remember` captures the initial value).
**Fix:** Use `derivedStateOf` or collect `boardSlug` as state and update `boardInput` when it changes.

### U7: `SettingsScreen` — test connection uses `SessionManager.DEFAULT_PASSWORD` as fallback
**File:** `SettingsScreen.kt:93`
**Severity:** Minor
**Issue:** `passInput.ifBlank { SessionManager.DEFAULT_PASSWORD }` — if the user leaves the password field empty, it sends the default (empty) password. This is intentional but the user gets no indication that the default is being used. If the server requires a password, the test will silently fail with a confusing error.
**Fix:** Show a hint like "Using default password" or require explicit password entry.

### U8: `ChatScreen` — `LaunchedEffect(Unit)` loads sessions only once
**File:** `ChatScreen.kt:38`
**Severity:** Minor
**Issue:** `LaunchedEffect(Unit)` runs only once when the composable first composes. If the user switches tabs and comes back, sessions are not refreshed. New sessions created on the server won't appear until the app is restarted.
**Fix:** Use `LaunchedEffect` with a refresh trigger, or load sessions when the drawer opens.

---

## Architecture / Code Quality

### A1: `ApiClient` — `reusableCont` is not reusable (new instance each call)
**File:** `ApiClient.kt:82-91`
**Severity:** Nit
**Issue:** `reusableCont` is a function that returns a new `Callback` instance each time. The name suggests it's a shared reusable instance, but it's not. This is just a naming issue — no functional bug.

### A2: `ApiClient.chat` — hardcoded model `"hermes-agent"`
**File:** `ApiClient.kt:115`
**Severity:** Minor
**Issue:** The model is hardcoded to `"hermes-agent"`. If the server supports multiple models, the user can't switch. Consider making this configurable.

### A3: `MainViewModel` — `_password` is a `MutableStateFlow` but also has `SessionManager` cache
**File:** `MainViewModel.kt:19-20` and `SessionManager.kt:46-53`
**Severity:** Minor
**Issue:** `MainViewModel._password` is initialized from `SessionManager.DEFAULT_PASSWORD` and then collected from `session.password` flow. `SessionManager` also has `cachedPassword` and `getPasswordCached()` which is never called. The ViewModel's `_password` is used for `client()` but the `SessionManager` cache is dead code.

### A4: `MainViewModel` — `client()` creates a new `ApiClient` on every call
**File:** `MainViewModel.kt:99-104`
**Severity:** Minor
**Issue:** `client()` is called for every API action and creates a new `ApiClient` each time. This is fine for OkHttp (it shares connection pools internally), but it's slightly wasteful. Consider caching the client and only recreating when credentials change.

### A5: `KanbanTask` — `priority` field defaults to `1`, not `0`
**File:** `Models.kt:32`
**Severity:** Nit
**Issue:** `priority: Int = 1` — if the server uses 0-based priority, this default is misleading. Check server convention.

### A6: `KanbanScreen` — `STATUS_COLUMNS` and `STATUS_LABELS` are top-level constants
**File:** `KanbanScreen.kt:33-44`
**Severity:** Nit
**Issue:** These are file-private top-level constants. Consider moving to a `companion object` or a dedicated constants file if shared.

### A7: `MainActivity` — no `onSaveInstanceState` handling
**File:** `MainActivity.kt:17-59`
**Severity:** Minor
**Issue:** `selectedTab` is stored in `remember { mutableIntStateOf(0) }`, which is not surviving configuration changes (rotation). The tab resets to 0 on rotation.
**Fix:** Use `rememberSaveable` instead of `remember` for `selectedTab`.

### A8: `MainViewModel` — `loadBoards` doesn't clear error before loading
**File:** `MainViewModel.kt:296-307`
**Severity:** Nit
**Issue:** `loadBoards` sets `_kanbanError.value = null` at line 298, which is correct. But `loadTasks` at line 359 also sets `_kanbanError.value = null`. However, `loadTask` (singular, line 372) does NOT clear the error. Inconsistent error clearing.

---

## Security

### S1: `ApiClient` — Basic auth with empty default password sent in cleartext
**File:** `ApiClient.kt:40-41`, `SessionManager.kt:16`
**Severity:** Important
**Issue:** `DEFAULT_PASSWORD = ""` means the auth header is `Basic <base64("kevin:")>` — an empty password. If the server is behind HTTPS this is fine, but the password is sent on every request. The `Authorization` header is stripped by Companion (per the comment at line 24), but if the client ever talks directly to Hermes, credentials are exposed.
**Fix:** Ensure the app only talks to Companion (not directly to Hermes). Document this assumption.

### S2: `SettingsScreen` — password field is cleared after save
**File:** `SettingsScreen.kt:110`
**Severity:** Minor
**Issue:** `passInput = ""` after save is good UX (don't keep password in memory), but the ViewModel's `_password` is updated with the new value. If the app crashes or is backgrounded, the password persists in the ViewModel's StateFlow. Consider clearing it after use.

### S3: No certificate pinning
**File:** `ApiClient.kt:34-38`
**Severity:** Minor
**Issue:** OkHttp uses the system trust store with no certificate pinning. For a personal app this is acceptable, but if the app is distributed, consider pinning the Companion certificate.

---

## Summary

**Verdict: MUST-FIX**

The app is well-structured with good separation of concerns, but has several issues that affect reliability:

**Must fix before release:**
1. **C1/C2** — `sendMessageWithAttachment` catch block doesn't finalize the streaming placeholder, leaving a permanent "..." bubble on error.
2. **C4/C5/C6** — JSON injection via string interpolation in `renameBoard`, `assigneeTask`, and `createBoard`. Use `JsonObject` encoding.
3. **U1** — Session drawer has no dismiss mechanism (no scrim, no close button).

**Should fix:**
- Dead code: `filteredSessions` in ViewModel, `_inputText` state in ViewModel, `getPasswordCached` in SessionManager.
- **A7** — Tab state lost on rotation (use `rememberSaveable`).
- **U5** — Composer input state not synced with ViewModel.
- **U8** — Sessions not refreshed when returning to Chat tab.

**Nice to fix:**
- Hardcoded model name in `ApiClient.chat`.
- Inconsistent error clearing in `loadTask` vs `loadTasks`.
- Input sanitization warnings in Settings.

# Hermes Companion — Five-Axis Code Review v2

**Date:** 2026-06-14
**Reviewer:** analyst (Atlas)
**Scope:** All code changes from T1–T3 vs REVIEW.md success criteria
**Build:** `./gradlew assembleDebug` — BUILD SUCCESSFUL
**Tests:** `./gradlew testDebugUnitTest` — BUILD SUCCESSFUL (all pass)

---

## Axis 1: Correctness

### server.py (Companion Daemon)

**C-SRV-01 [MINOR]** — `handle_session_delete` route is missing.
The server has no `DELETE /api/sessions/{session_id}` handler (line 329 is the last route), yet the Android app's `MainViewModel.deleteSession()` calls `c.delete("/api/sessions/$id")`. This will return 404. The Hermes proxy pattern used for other session routes (`handle_session_detail`) should be replicated for delete.
- **File:** `server/server.py`
- **Fix:** Add `handle_session_delete` handler that forwards to `DELETE /api/sessions/{session_id}` on the Hermes API, and register it with `app.router.add_delete("/api/sessions/{session_id}", handle_session_delete)`.

**C-SRV-02 [MINOR]** — `handle_kanban_task_assign` route is missing.
`MainViewModel.assignTask()` calls `POST /api/kanban/tasks/{task_id}/assign?board=...`, but server.py has no handler for this route. The Android app will get a 404.
- **File:** `server/server.py`
- **Fix:** Add `_kanban(["assign", task_id, assignee], board=board)` handler and register the route.

**C-SRV-03 [MINOR]** — `handle_kanban_boards_create` route is missing.
`MainViewModel.createBoard()` calls `POST /api/kanban/boards`, but server.py has no handler for this. Returns 404.
- **File:** `server/server.py`
- **Fix:** Add create-board handler and register it.

**C-SRV-04 [MINOR]** — `handle_kanban_board_archive` and `handle_kanban_board_delete` routes are missing.
`MainViewModel.archiveBoard()` and `deleteBoard()` call routes that don't exist on the server. Both return 404.
- **File:** `server/server.py`

**C-SRV-05 [MINOR]** — `handle_kanban_board_rename` route is missing.
`MainViewModel.renameBoard()` calls `POST /api/kanban/boards/{slug}/rename` which doesn't exist.
- **File:** `server/server.py`

**C-AND-01 [MAJOR]** — `server.py` `handle_session_create` response normalization is incomplete (line 184-198).
The handler wraps the Hermes forward response into `{"data": [...]}` only when Hermas returns 201 with `{"session": {...}}`. It also overrides request body with `await request.read()` on line 127, but the body is never written back — the `session/create` endpoint on Hermes expects a JSON body, which gets consumed by `HermesProxy.forward()` via `body = await request.read()` at line 127, but that body is passed as `data=body or None` in the `session.request()` call. This is correct — but the body-read happens BEFORE the normalization check. The issue is that the forward is attempted first, and normalization only applies on 201. If Hermes returns 200 or a different shape, the raw response passes through without normalization, which means `SessionsList` deserialization on the Android side may fail.
- **File:** `server/server.py`, lines 184-198
- **Fix:** Ensure normalization covers all success responses from Hermes session creation, not just 201.

**C-AND-02 [MINOR]** — `_kanban()` function (line 146-158) sets `HERMES_KANBAN_BOARD` env var but does NOT pass `--board` flag.
This is actually correct behavior — the original bug (B1 from REVIEW.md) was that `--board` was being inserted as a top-level `hermes` flag. The fix correctly uses the env var. **No action needed** — this is the intended fix from T1. Confirmed working.

### MainViewModel.kt

**C-VM-06 [MINOR]** — `sendMessage()` races on `_activeSessionId` (line 157-173).
The double-checked `_activeSessionId` pattern has a subtle race: if two messages are sent in rapid succession, both may read `_activeSessionId == null` at line 157 and both create sessions. The `_chatMessages.value = loadSessionHistory(ses.id)` at line 163 will run for the first caller and wipe messages for the second. In practice this is unlikely for UI-triggered sends (button is disabled during streaming), but architecturally fragile.
- **File:** `MainViewModel.kt`, lines 157-173
- **Fix:** Consider using a Mutex or a "session creation in progress" flag. For UI-level correctness, the `enabled = !isStreaming` guard in Composer.kt mitigates this sufficiently. Acceptable as-is with the guard.

**C-VM-07 [CRITICAL]** — `commentOnTask()` has wrong JSON body format (line 400).
```kotlin
val body = "{\"text\":\"${text.replace("\"", "\\\"")}\"}"
```
This produces `{"text":"..."}` — but the companion server handler `handle_kanban_task_comment` reads `body = await request.json()` and expects `body["text"]`. Looking at the server more carefully, the server reads `body["text"]` — so this is actually correct. **Wait** — re-reading the server: the server calls `_kanban(["comment", task_id, body["text"], "--author", author], board=board)`. The Android code sends the text correctly. However, the Android `sendMessage` manually escapes JSON (line 400) instead of using `kotlinx.serialization.json.JsonObject`. This is fragile.
- **File:** `MainViewModel.kt`, line 400
- **Severity:** MINOR (not CRITICAL — it works but is fragile)
- **Fix:** Use `JsonObject` builder or a data class with `@Serializable` instead of manual string escaping.

### ApiClient.kt

**C-API-01 [MINOR]** — `reusableCont` callback is reused across calls (line 82-91).
The `reusableCont` function creates a new object each time it's called (line 82 shows `= object : Callback { ... }`), so this is actually fine. **No issue** — naming is slightly misleading but behavior is correct.

**C-API-02 [MAJOR]** — `chat()` method (line 104-140) manually constructs JSON payload using string concatenation instead of serialization.
```kotlin
val payload = """{"model":"hermes-agent","messages":[$msgJson],"stream":false}"""
```
This works for simple messages but breaks if content contains Unicode control characters, null bytes, or other characters that JSON requires to be escaped (e.g., `\r`, `\t`). The only escapes applied are `\`, `"`, and `\n`.
- **File:** `ApiClient.kt`, lines 106-113
- **Fix:** Use `JsonObject` with `JsonArray` or a `@Serializable` data class for the OpenAI chat completion request.

---

## Axis 2: Completeness

### REVIEW.md Success Criteria Status

| Criterion | Status |
|-----------|--------|
| APK builds cleanly with `./gradlew assembleDebug` | PASS |
| `Serializer for class 'SessionsList' is not found` crash is gone | PASS (kotlinx-serialization plugin present in build.gradle line 4) |
| Kanban tab loads boards list and tasks per column | PARTIAL — wiring is complete, but server missing create/rename/archive/delete routes |
| Kanban actions work: Complete, Comment, Assign | PARTIAL — Complete and Comment server routes exist; Assign server route MISSING |
| Chat: new session, select session, send message, receive reply, load history | PASS (wiring complete, session delete server route missing) |
| Settings: test connection green, board slug persists, save works | PASS |
| Companion daemon kanban endpoints return 200 for all boards | FAIL on create/rename/archive/delete (404 on those) |
| No stubs remain in MainViewModel kanban methods | PASS — all methods are fully implemented |

### `SessionsList` — dead code check
`SessionsResponse` was removed from Models.kt per T3. No dead serializable data classes remain. PASS.

### Version bump
`versionCode 2, versionName "1.1.0"` — correctly bumped per T3. PASS.

### Composer clear button
T3 added `clearInput()` to MainViewModel + clear button in Composer wired via `onClear` callback. Verified in Composer.kt line 144-153 and MainViewModel.kt lines 460-469. PASS.

### Hardcoded "analyst" assignee
T3 verified already fixed — assign button in KanbanScreen line 407 uses `viewModel.username.value`. PASS.

### Remaining stubs
No stubs remain in MainViewModel. All kanban methods (`loadBoards`, `loadTasks`, `loadTask`, `completeTask`, `commentOnTask`, `assignTask`, `setBoard`, `createBoard`, `renameBoard`, `archiveBoard`, `deleteBoard`) are fully implemented with actual HTTP calls.

---

## Axis 3: Security

**S-SRV-01 [CRITICAL]** — `server.py` stores plaintext fallback password in `.env` reading logic (lines 30-35).
If `API_SERVER_KEY` is not in the environment, the server reads `/home/kevin/.hermes/.env` directly. This is a plaintext file. While this is the companion server's Hermes API key (not the HTTP Basic password), it's still a credential loaded from a file that may have lax permissions. The server should fail hard if the key isn't in the environment, rather than falling back to file reading.
- **File:** `server/server.py`, lines 30-35
- **Severity:** MEDIUM (credential exposure risk, mitigated by file permissions)
- **Fix:** Remove the `.env` fallback; require `API_SERVER_KEY` in environment only.

**S-SRV-02 [MAJOR]** — Scrypt password hashing (line 88-93) uses `hashlib.scrypt` with configurable N/R/P parameters stored in the hash string. The hash format is `scrypt$N$R$P$salt_hex$expected_hash`. This is a reasonable approach. However, there's no rate limiting on authentication attempts. The middleware re-reads `auth.json` on every request (line 59-61), which is good for credential rotation but means the server is vulnerable to brute-force if network-facing.
- **File:** `server/server.py`, lines 68-104
- **Severity:** MEDIUM (server binds to 127.0.0.1 by default, mitigated by localhost-only binding)
- **Fix:** Consider rate limiting if port 8777 is ever exposed beyond localhost.

**S-AND-01 [MAJOR]** — `ApiClient.kt` sends HTTP Basic auth credentials with EVERY request, including to the chat endpoint (`/v1/chat/completions` on Hermes itself).
The comment at line 24 says "Auth: HTTP Basic (we strip Authorization from outbound; Companion adds its own bearer)" — but looking at `chat()` (line 104-140), it calls `request("/v1/chat/completions", "POST", payload)`, which uses the Companion's auth header. The Hermes chat endpoint expects a Bearer token, not Basic auth. The companion server doesn't proxy `/v1/chat/completions` — it's called directly from the app to Hermes with Basic auth. This means the app's Basic auth credentials go straight to Hermes's chat endpoint which may reject them or, worse, accept them if Hermes also supports Basic.
- **Severity:** MEDIUM — depends on Hermes's auth configuration
- **File:** `ApiClient.kt`, line 117
- **Fix:** Verify Hermes chat endpoint auth. If Hermes expects Bearer, the app should send Bearer for chat and Basic for companion endpoints.

**S-AND-02 [MINOR]** — `SessionManager.kt` stores password in plaintext in DataStore (line 38-40).
DataStore preferences are stored as protobuf on disk. The password is not encrypted. `androidx.security:security-crypto` is in the dependencies (build.gradle line 57) but not used for password storage.
- **File:** `SessionManager.kt`, line 38-40
- **Fix:** Use `EncryptedSharedPreferences` or `EncryptedDataStore` for password storage.

**S-AND-03 [MINOR]** — `DEFAULT_PASSWORD = "atlas2026"` hardcoded in source (SessionManager.kt line 16).
This is a compile-time constant visible in decompiled APK. While it's a default/fallback, it should not be in source.
- **File:** `SessionManager.kt`, line 16
- **Fix:** Move to BuildConfig field or remove entirely; require user to set password on first launch.

**S-AND-04 [MINOR]** — `server.py` `handle_kanban_task_comment` (line 290-306) does not sanitize the `text` field before passing it to `_kanban()` subprocess. The text is passed as a CLI argument, which could contain shell metacharacters. `subprocess.run` with a list (not shell=True) mitigates injection, but the `--author` parameter from query string (line 299) is also unsanitized.
- **File:** `server/server.py`, lines 290-306
- **Severity:** LOW (subprocess.run with list prevents shell injection)
- **Fix:** Validate/sanitize author parameter; current risk is low due to list-based subprocess.

---

## Axis 4: Test Coverage

### Current Test Inventory

| Test File | Tests | Coverage Area |
|-----------|-------|---------------|
| `MainViewModelTest.kt` | 18 tests | StateFlow defaults, setBoard, loadBoards, loadTasks, completeTask, newSession, sendMessage, search, clearChat, clearSelectedTask, SessionManager defaults |
| `ApiClientTest.kt` | 20 tests | Auth header, URL construction, ApiException, OkHttp config, request building, response parsing, JSON escaping, friendly errors |
| `ModelsTest.kt` | 16 tests | SessionsList, KanbanBoard, KanbanTask, TaskShowResponse, HermesSession, SessionMessages, CompanionHealth serialization round-trips |

**Total: 54 unit tests, all passing.**

### Coverage Gaps

**T-01 [MAJOR]** — No tests for `sendMessageWithAttachment()` in MainViewModel.
The attachment upload flow (lines 202-253) is completely untested. This includes the session-auto-create path with attachments, the upload call, and the JSON parsing of the upload response.
- **File:** `MainViewModelTest.kt`
- **Fix:** Add tests mocking the ApiClient to verify attachment message flow.

**T-02 [MAJOR]** — No tests for `deleteSession()` in MainViewModel.
The delete flow (lines 431-445) including the active-session-clearing logic is untested.
- **File:** `MainViewModelTest.kt`

**T-03 [MAJOR]** — No tests for `saveSettings()` in MainViewModel.
Settings persistence and the subsequent `loadSessions()` call are untested.
- **File:** `MainViewModelTest.kt`

**T-04 [MAJOR]** — No tests for `finalizeAssistant()` race condition.
The messageId-based replacement logic (lines 258-266) is the core of the streaming UI. No test verifies that concurrent calls don't corrupt state.
- **File:** `MainViewModelTest.kt`

**T-05 [MINOR]** — No tests for `loadSessionHistory()` error path.
The private method at lines 138-149 catches exceptions and sets `_chatError`. No test verifies error handling.
- **File:** `MainViewModelTest.kt`

**T-06 [MINOR]** — No tests for `commentOnTask()` or `assignTask()` JSON body construction.
The manual JSON escaping in these methods is fragile and untested.
- **File:** `MainViewModelTest.kt`

**T-07 [MINOR]** — No tests for `ApiClient.chat()` response parsing.
The OpenAI response shape parsing (lines 124-133) is untested. A malformed response from Hermes would cause a crash.
- **File:** `ApiClientTest.kt`

**T-08 [MINOR]** — No tests for `ApiClient.uploadAttachment()`.
The multipart upload method (lines 144-160) is untested.
- **File:** `ApiClientTest.kt`

**T-09 [MINOR]** — No integration tests.
All tests are unit tests with Robolectric. No instrumented tests verify the actual HTTP layer against a running companion server.

### Test Quality Assessment

Tests are well-structured with descriptive names. The `friendlyErrorSim` helper in `ApiClientTest.kt` duplicates the private method logic rather than testing through the public API — this is a reasonable approach for unit testing but means the test could diverge from the actual implementation.

The `MainViewModelTest.kt` tests for network-error paths use `try/catch` with no-assertion patterns (e.g., lines 136-144). These tests verify "doesn't crash" but don't verify error state is set correctly. The `advanceUntilIdle()` calls are missing from some tests that launch coroutines, which means the coroutines may not actually execute during the test.

---

## Axis 5: Integration (Android App ↔ Companion Daemon)

### API Contract Verification

| Android Call | Server Route | Match? |
|-------------|-------------|--------|
| `GET /api/sessions` | `GET /api/sessions` | YES |
| `POST /api/sessions` | `POST /api/sessions` | YES (with normalization) |
| `GET /api/sessions/{id}` | `GET /api/sessions/{id}` | YES |
| `GET /api/sessions/{id}/messages` | `GET /api/sessions/{id}/messages` | YES |
| `DELETE /api/sessions/{id}` | **MISSING** | NO |
| `GET /api/kanban/boards` | `GET /api/kanban/boards` | YES |
| `POST /api/kanban/boards` | **MISSING** | NO |
| `POST /api/kanban/boards/{slug}/rename` | **MISSING** | NO |
| `POST /api/kanban/boards/{slug}/archive` | **MISSING** | NO |
| `DELETE /api/kanban/boards/{slug}` | **MISSING** | NO |
| `GET /api/kanban/tasks?board=` | `GET /api/kanban/tasks` | YES |
| `GET /api/kanban/tasks/{id}?board=` | `GET /api/kanban/tasks/{id}` | YES |
| `POST /api/kanban/tasks/{id}/complete?board=` | `POST /api/kanban/tasks/{id}/complete` | YES |
| `POST /api/kanban/tasks/{id}/comment?board=` | `POST /api/kanban/tasks/{id}/comment` | YES |
| `POST /api/kanban/tasks/{id}/assign?board=` | **MISSING** | NO |
| `POST /v1/chat/completions` | N/A (direct to Hermes) | See S-AND-01 |
| `POST /api/attachments` | **MISSING** | NO |

**I-01 [CRITICAL]** — 6 server routes are missing that the Android app calls.
The Android app has full implementations for board CRUD and task assignment, but the companion server doesn't have corresponding handlers. These will all return 404 at runtime.
- **File:** `server/server.py`
- **Fix:** Add handlers for: session delete, board create, board rename, board archive, board delete, task assign, attachment upload.

**I-02 [MAJOR]** — Response shape mismatch for `GET /api/kanban/boards`.
The server's `handle_kanban_boards` (line 212-222) returns `json.loads(out)` where `out` is the JSON output of `hermes kanban boards list --json`. The Android app expects `List<KanbanBoard>` which has fields: `slug`, `name`, `description`, `counts`, `total`, `archived`. If the Hermes CLI output shape doesn't match this exactly, deserialization will fail silently (due to `ignoreUnknownKeys = true` and `coerceInputValues = true`, fields will default).
- **File:** `server/server.py`, line 220 and `Models.kt`, lines 7-14
- **Fix:** Verify Hermes CLI output shape matches `KanbanBoard` serializable. Add a response transformation layer if needed.

**I-03 [MAJOR]** — Response shape mismatch for `GET /api/kanban/tasks`.
The server returns raw `hermes kanban list --json` output. The Android expects `List<KanbanTask>` with fields: `id`, `title`, `status`, `assignee`, `priority`, `body`, `created`, `updated`. Same concern as I-02.
- **File:** `server/server.py`, line 246 and `Models.kt`, lines 27-36

**I-04 [MAJOR]** — Response shape for `GET /api/kanban/tasks/{id}` (task show).
Server returns raw `hermes kanban show --json {id}`. Android expects `TaskShowResponse` with `comments` and `events` lists. If Hermes CLI doesn't include these, the lists will default to empty — functional but lossy.
- **File:** `server/server.py`, line 261 and `Models.kt`, lines 39-48

**I-05 [MINOR]** — `POST /api/sessions` response normalization (server line 184-198) wraps `{"session": {...}}` into `{"data": [{...}]}`. The Android `newSession()` (line 116) and `sendMessage()` (line 160) both call `json.decodeFromString<SessionsList>(raw).data.firstOrNull()?.id`. This depends on the normalization. If Hermes changes its response shape, this breaks silently.
- **File:** `server/server.py`, lines 184-198 and `MainViewModel.kt`, lines 116, 160

**I-06 [MINOR]** — `POST /api/kanban/tasks/{id}/complete` server handler (line 268-287) returns `{"ok": true, "task_id": task_id, "status": "done"}` but the Android `completeTask()` (line 384-395) doesn't read the response body — it just calls `loadTasks()` on success. This is fine; no mismatch.

**I-07 [MINOR]** — `POST /api/kanban/tasks/{id}/comment` server handler (line 290-306) returns `{"ok": true}`. Android `commentOnTask()` (line 396-407) doesn't read the response — it calls `loadTask(taskId)` to refresh. No mismatch.

**I-08 [INFO]** — `SettingsScreen.kt` calls `viewModel.setBoard(boardInput)` on save (line 109), which correctly triggers `loadBoards()` and `loadTasks()` via the `setBoard()` method. The board slug is persisted to DataStore. Integration flow is complete.

---

## Summary

### MUST-FIX Findings (3)

| ID | Severity | File | Issue |
|----|----------|------|-------|
| I-01 | CRITICAL | `server/server.py` | 6 server routes missing that Android app calls (session delete, board CRUD, task assign, attachments) |
| C-SRV-01 | CRITICAL | `server/server.py` | `DELETE /api/sessions/{id}` handler missing — Android `deleteSession()` will 404 |
| S-SRV-01 | CRITICAL | `server/server.py` | Plaintext credential fallback via `.env` file reading |

### MUST-FIX Findings — MAJOR (5)

| ID | Severity | File | Issue |
|----|----------|------|-------|
| C-AND-02 | MAJOR | `ApiClient.kt` | `chat()` manually constructs JSON — fragile escaping |
| S-AND-01 | MAJOR | `ApiClient.kt` | Chat endpoint may send Basic auth instead of Bearer |
| T-01 | MAJOR | `MainViewModelTest.kt` | No tests for `sendMessageWithAttachment()` |
| T-02 | MAJOR | `MainViewModelTest.kt` | No tests for `deleteSession()` |
| T-03 | MAJOR | `MainViewModelTest.kt` | No tests for `saveSettings()` |

### MINOR Findings (12)

| ID | File | Issue |
|----|------|-------|
| C-SRV-02~05 | `server/server.py` | Missing assign/create/rename/archive/delete handlers (covered by I-01) |
| C-AND-01 | `server/server.py` | Session create normalization only handles 201 |
| C-VM-06 | `MainViewModel.kt` | Race condition on session creation (mitigated by UI guard) |
| C-VM-07 | `MainViewModel.kt` | Manual JSON escaping in `commentOnTask()` |
| S-SRV-02 | `server/server.py` | No auth rate limiting |
| S-AND-02 | `SessionManager.kt` | Password stored in plaintext DataStore |
| S-AND-03 | `SessionManager.kt` | Default password hardcoded in source |
| S-AND-04 | `server/server.py` | Unsanitized author parameter in comment handler |
| T-04~06 | `MainViewModelTest.kt` | Missing tests for finalizeAssistant, loadSessionHistory error, comment/assign JSON |
| T-07~08 | `ApiClientTest.kt` | Missing tests for chat response parsing and uploadAttachment |

### Verdict

**MUST-FIX** — The Android app is well-implemented with complete ViewModels, proper state management, and good test coverage for the app layer. However, the companion server is missing 6 critical route handlers that the Android app depends on. The app will compile and unit tests will pass, but runtime functionality for board management, task assignment, session deletion, and attachments will fail with 404 errors. The server needs these handlers added before the integration can be considered complete.

### Build & Test Evidence
- `./gradlew assembleDebug` — BUILD SUCCESSFUL
- `./gradlew testDebugUnitTest` — BUILD SUCCESSFUL (54 tests, 0 failures)
- Log: `/tmp/t4-review-build.log` (build output captured above)

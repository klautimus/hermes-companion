# Test Coverage Audit — HermesCompanion

**Date:** 2026-06-14
**Test run:** `./gradlew testDebugUnitTest` — BUILD SUCCESSFUL (84 tests, 0 failures, 0 errors)
**Log:** `/tmp/t4-unit-tests.log`

---

## 1. Test Inventory

### MainViewModelTest (41 tests)

| # | Test Name | What It Covers |
|---|-----------|----------------|
| 1 | `boards_defaultEmpty` | `boards` StateFlow defaults to empty list |
| 2 | `tasks_defaultEmpty` | `tasks` StateFlow defaults to empty list |
| 3 | `tasksByStatus_defaultEmpty` | `tasksByStatus` StateFlow defaults to empty map |
| 4 | `selectedTask_defaultNull` | `selectedTask` defaults to null |
| 5 | `sessions_defaultEmpty` | `sessions` defaults to empty list |
| 6 | `filteredSessions_defaultEmpty` | `filteredSessions` defaults to empty list |
| 7 | `isStreaming_defaultFalse` | `isStreaming` defaults to false |
| 8 | `activeMessages_defaultEmpty` | `activeMessages` defaults to empty list |
| 9 | `activeSessionId_defaultNull` | `activeSessionId` defaults to null |
| 10 | `chatMessages_defaultEmpty` | `chatMessages` defaults to empty list |
| 11 | `chatError_defaultNull` | `chatError` defaults to null |
| 12 | `kanbanError_defaultNull` | `kanbanError` defaults to null |
| 13 | `setBoard_doesNotCrash` | `setBoard()` doesn't throw without server |
| 14 | `setBoard_multipleCalls` | Multiple `setBoard()` calls don't crash |
| 15 | `loadBoards_doesNotCrash` | `loadBoards()` doesn't throw without server |
| 16 | `loadTasks_doesNotCrash` | `loadTasks()` doesn't throw without server |
| 17 | `loadTasks_usesDefaultBoard` | Default board slug matches `SessionManager.DEFAULT_BOARD` |
| 18 | `completeTask_doesNotCrash` | `completeTask()` doesn't throw without server |
| 19 | `newSession_noServer_setsError` | `newSession()` doesn't crash without server |
| 20 | `sendMessage_noServer_setsError` | `sendMessage()` doesn't crash without server |
| 21 | `setSessionSearchQuery_updatesQuery` | `setSessionSearchQuery()` updates the query StateFlow |
| 22 | `setSessionSearchQuery_filtersSessions` | Filtering with empty sessions returns empty |
| 23 | `clearChat_clearsMessagesAndError` | `clearChat()` empties messages and error |
| 24 | `clearSelectedTask_clearsSelection` | `clearSelectedTask()` nulls selected task |
| 25 | `defaultBaseUrl` | `SessionManager.DEFAULT_URL` value |
| 26 | `defaultUsername` | `SessionManager.DEFAULT_USERNAME` value |
| 27 | `defaultPassword` | `SessionManager.DEFAULT_PASSWORD` is empty string (S-AND-03) |
| 28 | `defaultBoard` | `SessionManager.DEFAULT_BOARD` is "default" |
| 29 | `baseUrl_hasCorrectScheme` | URL starts with `https://` |
| 30 | `tasksByStatus_groupsEmptyTasks` | `tasksByStatus` groups empty task list |
| 31 | `defaultBoardSlug_matchesSessionManagerDefault` | `boardSlug` matches SessionManager default |
| 32 | `deleteSession_noServer_setsError` | `deleteSession()` doesn't crash without server |
| 33 | `deleteSession_clearsActiveSession` | `deleteSession()` on active session doesn't crash |
| 34 | `sendMessageWithAttachment_noServer_setsError` | `sendMessageWithAttachment()` doesn't crash |
| 35 | `sendMessageWithAttachment_emptyContent` | `sendMessageWithAttachment()` with empty content doesn't crash |
| 36 | `saveSettings_doesNotCrash` | `saveSettings()` doesn't crash without server |
| 37 | `saveSettings_emptyPassword_keepsExisting` | `saveSettings()` with empty password doesn't crash |
| 38 | `commentOnTask_noServer_setsError` | `commentOnTask()` doesn't crash without server |
| 39 | `commentOnTask_jsonEscaping` | JSON encoding handles special chars (quotes, backslash, newline) |
| 40 | `assignTask_noServer_setsError` | `assignTask()` doesn't crash without server |
| 41 | `saveSettings_persistsToDataStore` | `saveSettings()` doesn't crash |

### ApiClientTest (27 tests)

| # | Test Name | What It Covers |
|---|-----------|----------------|
| 1 | `authHeader_containsBasicPrefix` | Auth header starts with "Basic " and round-trips |
| 2 | `authHeader_encodesColonSeparatedCredentials` | Base64 encodes `user:pass` correctly |
| 3 | `authHeader_noExtraWhitespace` | Encoded string has no newlines or spaces |
| 4 | `authHeader_matchesOkHttpCredential` | Manual encoding matches `Credentials.basic()` |
| 5 | `urlConstruction_simplePath` | URL concatenation without trailing slash |
| 6 | `urlConstruction_withTrailingSlash` | Documents double-slash bug with trailing slash baseUrl |
| 7 | `urlConstruction_withoutTrailingSlash` | Correct URL without trailing slash |
| 8 | `urlConstruction_withQueryParams` | URL with query parameters |
| 9 | `apiException_storesCodeAndMessage` | `ApiException(401, "Unauthorized")` stores values |
| 10 | `apiException_500` | `ApiException(500, ...)` stores values |
| 11 | `apiException_isException` | `ApiException` is an `Exception` subtype |
| 12 | `okHttpClient_defaultTimeouts` | OkHttp client configured: 15s connect, 300s read, 30s write |
| 13 | `requestBuilder_getRequest` | GET request has correct URL, method, auth, accept |
| 14 | `requestBuilder_postRequestWithBody` | POST request preserves JSON body |
| 15 | `requestBuilder_deleteRequest` | DELETE request has correct URL |
| 16 | `parseSessionsList_validJson` | Parses `SessionsList` JSON (data + hasMore) |
| 17 | `parseSessionsList_empty` | Parses empty sessions list |
| 18 | `chatMessageJson_escaping` | Manual escaping of backslash, quote, newline |
| 19 | `chatMessageJson_escaping_emptyString` | Empty string passes through unchanged |
| 20 | `chatMessageJson_escaping_noSpecialChars` | Plain text passes through unchanged |
| 21 | `friendlyError_timeout` | `SocketTimeoutException` -> timeout message |
| 22 | `friendlyError_connectionRefused` | `ConnectException` -> "can't reach" message |
| 23 | `friendlyError_unknownHost` | `UnknownHostException` -> "not found" message |
| 24 | `friendlyError_socketException` | `SocketException` -> "connection lost" message |
| 25 | `friendlyError_sslException` | `SSLException` -> "secure connection" message |
| 26 | `friendlyError_genericIOException` | Generic `IOException` -> message or "Network error" |
| 27 | `friendlyError_nullMessage` | IOException with null message -> "Network error" |

### ModelsTest (16 tests)

| # | Test Name | What It Covers |
|---|-----------|----------------|
| 1 | `sessionsList_serialize_roundTrip` | `SessionsList` encode->decode round-trip |
| 2 | `sessionsList_deserialize_fromJson` | Parses full JSON response |
| 3 | `sessionsList_emptyData` | Parses empty data array |
| 4 | `sessionsList_missingOptionalFields` | Missing fields get defaults (null/0) |
| 5 | `kanbanBoard_withAllFields` | Parses KanbanBoard with counts |
| 6 | `kanbanBoard_missingOptionalFields` | Parses minimal KanbanBoard |
| 7 | `kanbanBoard_archived` | Parses archived=true board |
| 8 | `kanbanBoard_serialize_roundTrip` | Encode->decode round-trip |
| 9 | `kanbanTask_withAllFields` | Parses KanbanTask with all fields |
| 10 | `kanbanTask_missingOptionalFields` | Parses minimal KanbanTask |
| 11 | `kanbanTask_variousStatuses` | All 6 statuses parse correctly |
| 12 | `kanbanTask_serialize_roundTrip` | Encode->decode round-trip |
| 13 | `taskShowResponse_withComments` | Parses TaskShowResponse with comments + events |
| 14 | `hermesSession_roundTrip` | HermesSession round-trip |
| 15 | `sessionMessages_roundTrip` | SessionMessages round-trip |
| 16 | `companionHealth_roundTrip` | CompanionHealth round-trip |

---

## 2. Coverage Gaps (Ranked by Importance)

### CRITICAL — No tests for actual API interaction behavior

The vast majority of `MainViewModel` tests (13, 14, 15, 16, 18, 19, 20, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40) all follow the same pattern: call a method, wrap in try/catch, assert nothing meaningful. They verify "doesn't crash" but don't verify:

- State mutation after operations (e.g., does `deleteSession` actually remove from the list?)
- Error propagation (e.g., does `chatError` get set on failure?)
- Coroutine sequencing (e.g., does `newSession` set `activeSessionId` before sending a message?)

**Missing integration-level verification:**
- Mock `ApiClient` to verify correct URLs, headers, and body content are sent
- Verify state flows update correctly after mock responses
- Verify error flows populate `chatError`/`kanbanError` StateFlows on API failures
- Verify `selectSession` loads history and populates `chatMessages`
- Verify `sendMessage` adds user message to `chatMessages` before network call
- Verify `finalizeAssistant` correctly replaces streaming placeholder by messageId
- Verify `deleteSession` removes from list AND clears active session when deleting active
- Verify `clearInput` resets `inputText`

### HIGH — Untested source files

| File | Coverage | Notes |
|------|----------|-------|
| `SessionManager.kt` | **0 tests** | DataStore persistence (setBaseUrl, setUsername, setPassword, setBoard), caching (getPasswordCached, getPasswordSnapshot), Flow emissions |
| `MainActivity.kt` | **0 tests** | Activity lifecycle, compose setup |
| `Composer.kt` | **0 tests** | Input state management |
| `ChatScreen.kt` | **0 tests** | UI state observation, message rendering |
| `KanbanScreen.kt` | **0 tests** | Board/task selection UI state |
| `SettingsScreen.kt` | **0 tests** | Settings form state, save action |
| `MessageList.kt` | **0 tests** | Message list rendering, streaming indicator |
| `Theme.kt` | **0 tests** | Theme configuration (low risk) |
| `server/server.py` | **0 tests** | Entire server is untested (see section 5) |

### HIGH — ApiClient integration with real HTTP patterns

- `ApiClient.uploadAttachment()` — no tests at all
- `ApiClient.chat()` — no tests at all
- `ApiClient.get()` / `post()` / `delete()` — no tests with mocked responses
- Actual URL construction through `ApiClient.request()` (auth header + accept header) — tested indirectly via `ApiClientTest.requestBuilder_*` but only through raw `Request.Builder`, not through the actual `ApiClient` class
- `parseErr()` error body parsing — not tested (the `reusableCont` callback)
- Race condition handling in `finalizeAssistant` — messageId-based replacement not tested with concurrent sends

### MEDIUM — ViewModel methods only tested for "doesn't crash"

- `createBoard()`, `renameBoard()`, `archiveBoard()`, `deleteBoard()` — no tests at all
- `loadTask()` — no test
- `selectSession()` — no test
- `loadSessions()` pagination logic — no test (the while loop with offset)
- `inputText` / `setInputText()` / `clearInput()` — no tests
- `saveSettings` with empty password logic — only tested for "doesn't crash", not for actual password-preservation behavior
- `CommentOnTask` JSON body — only the `commentOnTask_jsonEscaping` test covers the JSON encoding, but doesn't verify the ViewModel sends the correct task ID and board slug

### MEDIUM — Models not exhaustively tested

- `CompanionHealth` — only round-trip, no deserialization from realistic JSON
- `KanbanComment` / `KanbanEvent` — only tested as part of `TaskShowResponse`, not individually
- `SessionMessages` with empty data — not tested
- `TaskShowResponse` with empty comments/events — not tested
- `TaskShowResponse` round-trip — not tested
- Malformed JSON handling — no negative tests for any model

### LOW — Edge cases not covered

- Empty string inputs to `sendMessage`, `completeTask`, `commentOnTask`, `assignTask`
- Very long strings (boundary testing)
- Unicode/special characters in task titles, comments
- `setSessionSearchQuery` with special regex characters
- `filteredSessions` with actual session data (test only covers empty case)
- `tasksByStatus` with actual task data (test only covers empty case)
- `SessionManager` concurrent access patterns

---

## 3. Test Quality Assessment

### Strengths

1. **All 84 tests pass** — no flaky or broken tests
2. **Good use of Robolectric** — `MainViewModelTest` properly sets up `InstantTaskExecutorRule`, `StandardTestDispatcher`, and cleans up DataStore between tests
3. **ModelsTest is solid** — round-trip + deserialization + missing-fields coverage for all data classes
4. **ApiClientTest covers auth, URL construction, error mapping, request building, and response parsing** — good breadth for the data layer
5. **JSON escaping test** (`commentOnTask_jsonEscaping`) is a good catch for a real bug class
6. **Security-conscious** — `defaultPassword` test verifies empty default (S-AND-03)

### Weaknesses

1. **"Doesn't crash" anti-pattern** — 19 of 41 `MainViewModelTest` tests follow the pattern:
   ```kotlin
   try { viewModel.someMethod() } catch (_: Exception) { }
   // No assertion about state
   ```
   These tests will pass even if the method does nothing at all. They provide false confidence.

2. **No mocking framework** — No MockK, Mockito, or fake `ApiClient`. This means:
   - ViewModel tests can't verify correct API calls
   - ViewModel tests can't simulate success/failure responses
   - All network-dependent tests are effectively no-ops

3. **No test for the actual `ApiClient` class** — `ApiClientTest` tests the *logic that ApiClient uses* (Base64 encoding, URL string concatenation, OkHttp request building) but never instantiates `ApiClient` or calls its methods. The `friendlyError` function is tested via a local copy (`friendlyErrorSim`), not the actual private method.

4. **No Compose UI tests** — No `@Composable` tests for any screen. The UI layer (ChatScreen, KanbanScreen, SettingsScreen, MessageList, Composer) is entirely untested.

5. **No coroutine error path testing** — `advanceUntilIdle()` is used but never with error-producing scenarios. The `catch` blocks in ViewModel coroutines are untested.

---

## 4. Server-Side Tests

**Finding: `server/server.py` has ZERO tests.**

The server is a 481-line aiohttp application with:
- `BasicAuth` class (scrypt password hashing, auth.json reloading, middleware)
- `HermesProxy` class (HTTP forwarding to Hermes API)
- 15+ route handlers (health, sessions CRUD, kanban boards/tasks CRUD, attachments)
- Input validation (slug sanitization, author sanitization, required field checks)
- Error handling (JSON parse errors, subprocess timeouts, Hermes API unreachability)

**No `test_server.py` exists.** No pytest, no aiohttp test client, nothing.

**Critical server behaviors that need testing:**
- `BasicAuth.check()` — valid credentials, invalid credentials, missing auth header, malformed Base64, scrypt verification
- `BasicAuth._reload()` — file change detection, malformed auth.json handling
- `HermesProxy.forward()` — header stripping, Bearer token injection, error handling
- `_kanban()` — subprocess timeout, binary not found, JSON parse failure
- `handle_kanban_boards_create` — slug validation, missing slug
- `handle_kanban_task_comment` — text required, author sanitization
- `handle_attachment_upload` — file required, multipart parsing
- `handle_healthz` — Hermes reachable vs. unreachable
- `handle_session_create` — response normalization (`{session:}` -> `{data:[]}`)

---

## 5. Integration Tests

**Finding: No integration tests exist anywhere.**

- No end-to-end test that wires `MainViewModel` + `ApiClient` + a mock server
- No test that exercises the full flow: create session -> send message -> receive response
- No test that exercises: load boards -> load tasks -> complete task -> verify state update
- No instrumented tests (`androidTest` directory doesn't exist)

---

## 6. Missing Tests That Should Be Written

### Priority 1 (Critical — enables real coverage)

1. **Mock-based ViewModel tests** — Introduce MockK, create a fake `ApiClient`, verify:
   - `newSession()` calls `post("/api/sessions", "{}")` and sets `activeSessionId` from response
   - `sendMessage()` adds user message, calls `chat()`, finalizes assistant message
   - `deleteSession()` removes from list and clears active session when deleting active
   - `setBoard()` persists to DataStore and triggers loadSessions/loadBoards/loadTasks
   - Error paths set `chatError`/`kanbanError` StateFlows

2. **Server test suite** — Create `server/test_server.py` with aiohttp test client:
   - Auth middleware: valid/invalid/missing credentials
   - All CRUD routes: boards, tasks, sessions, attachments
   - Input validation: missing required fields, sanitization
   - Error handling: Hermes down, subprocess timeout, malformed JSON

### Priority 2 (High — fills major gaps)

3. **SessionManager tests** — Test DataStore persistence round-trips, Flow emissions, caching

4. **ApiClient integration tests** — Use MockWebServer (OkHttp) to test:
   - `get()`, `post()`, `delete()` with real HTTP responses
   - `uploadAttachment()` multipart upload
   - `chat()` response parsing
   - Error code handling (401, 500, etc.)
   - `friendlyError()` through actual `ApiClient` methods

5. **ViewModel state mutation tests** — Without mocking, at minimum verify:
   - `selectSession(id)` sets `activeSessionId`
   - `clearChat()` empties `chatMessages` and nulls `chatError`
   - `setInputText()` / `clearInput()` update `inputText`
   - `setSessionSearchQuery()` updates query and triggers filtering

### Priority 3 (Medium — edge cases and robustness)

6. **Model negative tests** — Malformed JSON, missing required fields, type mismatches

7. **Compose UI tests** — Screen rendering, user interaction, state observation

8. **End-to-end flow tests** — Full create-session-send-message-verify flow with mock server

---

## 7. Summary: Test Suite Quality Rating

| Dimension | Rating | Notes |
|-----------|--------|-------|
| **Pass Rate** | Excellent | 84/84 pass, 0 failures |
| **Coverage Breadth** | Poor | 3 of 16 source files tested; 0 of 1 server file |
| **Coverage Depth** | Weak | Most ViewModel tests are "doesn't crash" no-ops |
| **Test Quality** | Mixed | ModelsTest is good; MainViewModelTest is mostly hollow |
| **Error Path Coverage** | Minimal | Only `friendlyError` mapping tested; no error state verification |
| **Integration** | None | No integration tests at all |
| **Server Coverage** | None | 0 tests for 481-line server |
| **UI Coverage** | None | 0 tests for 6 Compose UI files |

### Overall Rating: **3/10**

The test suite has a solid foundation (all tests pass, good build setup, Robolectric configured correctly) but provides minimal actual coverage. The biggest issue is that the majority of `MainViewModelTest` tests assert nothing meaningful — they catch exceptions but don't verify state changes, API calls, or error propagation. The server has zero tests. The UI layer has zero tests. `SessionManager` has zero tests. The `ApiClient` class itself is never directly tested.

The suite gives a false sense of security: 84 passing tests that mostly verify "the app doesn't crash when you call methods without a server running."

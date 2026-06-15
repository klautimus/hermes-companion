# Server & API Deep Audit — Hermes Companion Daemon

**Date:** 2026-06-14
**Auditor:** analyst (Atlas)
**Scope:** server.py, ApiClient.kt, MainViewModel.kt — all routes, error handling, edge cases
**Method:** Five-axis review (Correctness, Readability, Architecture, Security, Performance) per code-review-and-quality skill

---

## 1. Route Completeness

### Android API calls vs Server routes

| Android Call | Server Route | Status |
|---|---|---|
| `GET /api/sessions` | `GET /api/sessions` (L450) | OK |
| `POST /api/sessions` | `POST /api/sessions` (L451) | OK |
| `GET /api/sessions/{id}` | `GET /api/sessions/{session_id}` (L452) | OK |
| `GET /api/sessions/{id}/messages` | `GET /api/sessions/{session_id}/messages` (L453) | OK |
| `DELETE /api/sessions/{id}` | `DELETE /api/sessions/{session_id}` (L454) | OK |
| `GET /api/kanban/boards` | `GET /api/kanban/boards` (L457) | OK |
| `POST /api/kanban/boards` | `POST /api/kanban/boards` (L458) | OK |
| `POST /api/kanban/boards/{slug}/rename` | `POST /api/kanban/boards/{slug}/rename` (L459) | OK |
| `POST /api/kanban/boards/{slug}/archive` | `POST /api/kanban/boards/{slug}/archive` (L460) | OK |
| `DELETE /api/kanban/boards/{slug}` | `DELETE /api/kanban/boards/{slug}` (L461) | OK |
| `GET /api/kanban/tasks?board=` | `GET /api/kanban/tasks` (L462) | OK |
| `GET /api/kanban/tasks/{id}?board=` | `GET /api/kanban/tasks/{task_id}` (L463) | OK |
| `POST /api/kanban/tasks/{id}/complete` | `POST /api/kanban/tasks/{task_id}/complete` (L464) | OK |
| `POST /api/kanban/tasks/{id}/comment` | `POST /api/kanban/tasks/{task_id}/comment` (L465) | OK |
| `POST /api/kanban/tasks/{id}/assign` | `POST /api/kanban/tasks/{task_id}/assign` (L466) | OK |
| `POST /api/attachments` | `POST /api/attachments` (L469) | OK |
| `GET /api/attachments/{id}` | **MISSING** | **FINDING F-01** |
| `POST /v1/chat/completions` | N/A (direct to Hermes) | OK (bypasses companion) |

### F-01 [CRITICAL] — GET /api/attachments/{id} serving route missing

**File:** `server/server.py`
**Detail:** The Android app uploads attachments via `POST /api/attachments` and receives a response with `{"id": "att_...", "url": "/api/attachments/att_..."}`. The app then constructs a full URL (`MainViewModel.kt:211`) and passes it to `AsyncImage` for display. However, there is **no GET /api/attachments/{id}** route on the server. Every image load will return 404.

The upload handler (L409-437) saves files to `/home/kevin/.hermes/companion/attachments/{att_id}_{filename}` but never serves them back.

**Fix:** Add a GET route that reads the file from disk and returns it with the correct Content-Type. Must validate the att_id to prevent path traversal (e.g., reject IDs containing `/` or `..`).

---

## 2. Error Handling

### F-02 [MAJOR] — handle_session_delete forwards DELETE to Hermes without checking support

**File:** `server/server.py`, L308-311
**Detail:** `handle_session_delete` blindly forwards `DELETE /api/sessions/{sid}` to the Hermes API. If Hermes doesn't support session deletion (many APIs don't), the companion will return whatever Hermes returns (likely 405 or 404) with no graceful handling. The Android app's `deleteSession()` (MainViewModel.kt:436-450) expects a successful response and removes the session from the local list regardless.

**Fix:** Either confirm Hermes supports DELETE /api/sessions/{id}, or add a fallback that returns 200 with `{"ok": true}` if Hermes returns 405.

### F-03 [MINOR] — handle_kanban_task_assign calls non-existent CLI subcommand

**File:** `server/server.py`, L399
**Detail:** The handler calls `_kanban(["assign", task_id, assignee], board=board)`. The `hermes kanban` CLI may not have an `assign` subcommand — the typical pattern is `hermes kanban create ... --assignee <profile>` at task creation time, or `hermes kanban update <id> --assignee <profile>`. If the CLI doesn't support `assign` as a standalone subcommand, this will always fail.

**Fix:** Verify `hermes kanban assign <id> <assignee>` exists in the CLI. If not, use the correct update command.

### F-04 [MINOR] — handle_kanban_task_complete doesn't pass metadata from request body

**File:** `server/server.py`, L261-280
**Detail:** The handler reads `body.get("result")` and `body.get("summary")` but the Android app never sends these fields — `completeTask()` (MainViewModel.kt:384-395) just calls `c.post(...)` with no body. The server's metadata extraction is dead code. Not a bug, but misleading.

**Fix:** Remove the dead code or document it as server-side extensibility.

### F-05 [MINOR] — handle_kanban_task_comment doesn't validate text length

**File:** `server/server.py`, L283-303
**Detail:** No maximum length on comment text. A malicious or buggy client could send megabyte-scale text, which gets passed as a CLI argument to `hermes kanban comment`. CLI argument length limits (typically 128KB on Linux) would cause a crash.

**Fix:** Add a text length check (e.g., max 10KB) and return 422 if exceeded.

---

## 3. Response Shape Normalization

### F-06 [MAJOR] — session_create normalization only covers {"session": {...}} shape

**File:** `server/server.py`, L177-191
**Detail:** The normalization at L186 (`if "session" in data`) only handles the case where Hermes returns `{"session": {...}}`. If Hermes returns `{"data": [...]}` (which is the companion's own normalized format — a potential double-normalization issue if the proxy is ever pointed at another companion), or `{"id": "...", "title": "..."}` as a flat object, the normalization is skipped and the raw response passes through. The Android app expects `{"data": [{"id": "..."}]}` and will fail to extract the session ID.

**Fix:** Add a fallback: if the response has a top-level `id` field and no `data` array, wrap it in `{"data": [{"id": ...}]}`.

### F-07 [MINOR] — Kanban board/task responses pass through raw CLI output with no transformation

**File:** `server/server.py`, L205-258
**Detail:** The kanban handlers return raw `hermes kanban boards list --json` and `hermes kanban list --json` output. The Android app uses `ignoreUnknownKeys = true` and `coerceInputValues = true`, so extra fields are tolerated and missing fields default. However, if the CLI output field names don't match the Kotlin data class field names (e.g., `created_at` vs `created`), the values will silently default. The `KanbanTask` model uses `created` and `updated` — if the CLI returns `created_at` and `updated_at`, these fields will always be null on the client.

**Fix:** Either verify CLI output field names match the Kotlin models, or add a response transformation layer in the server.

---

## 4. Attachment Serving

### F-08 [CRITICAL] — No GET /api/attachments/{id} route (same as F-01, expanded)

**File:** `server/server.py`
**Detail:** See F-01. Additionally, the attachment upload handler (L409-437) has no size limit on uploaded files. A client could upload a multi-gigabyte file, exhausting disk space. The `att_id` is generated with `os.urandom(8).hex()` (16 hex chars = 64 bits of entropy) which is sufficient against guessing.

**Fix:**
1. Add GET /api/attachments/{id} with path traversal protection
2. Add upload size limit (e.g., 10MB) — check `Content-Length` header or use aiohttp's `max_upload_size`
3. Validate att_id format (only hex chars) in the serving handler

---

## 5. Input Validation

### F-09 [MAJOR] — Board CRUD handlers don't validate slug format

**File:** `server/server.py`, L314-381
**Detail:** The `handle_kanban_boards_create` handler (L314-334) only checks that `slug` is non-empty. It doesn't validate the slug format. The Android app sanitizes with `it.lowercase().replace(Regex("[^a-z0-9-]"), "-")` (KanbanScreen.kt:205), but the server should also validate since it's the authority. An invalid slug like `../../etc` would be passed to `_kanban(["boards", "create", "--slug", "../../etc", ...])` — while subprocess.run with a list prevents shell injection, the slug value goes into the HERMES_KANBAN_BOARD env var and also into `--slug` CLI arg, which could confuse the hermes CLI.

**Fix:** Add server-side slug validation: only allow `[a-z0-9-]`, max 64 chars, no leading/trailing hyphens.

### F-10 [MINOR] — handle_attachment_upload doesn't validate filename

**File:** `server/server.py`, L409-437
**Detail:** The uploaded file's `filename` (from the multipart form field) is used directly in the path `att_dir / f"{att_id}_{filename}"`. A filename like `../../etc/passwd` would create a path traversal. While `att_dir` is an absolute path and `pathlib.Path` concatenation with `/` won't escape the parent on its own, a filename containing `/` could create subdirectories.

**Fix:** Sanitize filename: strip directory separators, replace `..` with safe chars, or generate a server-side filename and discard the client-provided one.

### F-11 [MINOR] — handle_kanban_board_delete doesn't check if board is "default"

**File:** `server/server.py`, L372-381
**Detail:** The Android app prevents deletion of the "default" board (KanbanScreen.kt:175: `if (board.slug != "default")`), but the server doesn't enforce this. A direct API call could delete the default board.

**Fix:** Add server-side check: if slug == "default", return 403.

---

## 6. HermesProxy.forward()

### F-12 [MINOR] — forward() doesn't strip Content-Length header

**File:** `server/server.py`, L111-135
**Detail:** The `forward()` method strips `Host` and `Authorization` headers but doesn't strip `Content-Length`. When the request body is read at L120 (`body = await request.read()`), the original Content-Length header may not match the body length after aiohttp re-encodes it. In practice, aiohttp's `web.Response` handles Content-Length automatically, but the upstream request at L122-124 passes `data=body or None` — if body is empty bytes, `data=None` is correct, but the Content-Length from the original request may still be present in `headers`, causing a mismatch.

**Fix:** Also strip `Content-Length` and `Transfer-Encoding` from the forwarded headers, letting aiohttp compute them.

### F-13 [INFO] — forward() correctly strips/replaces auth headers

**File:** `server/server.py`, L117-119
**Detail:** The method correctly removes the `Host` and `Authorization` headers from the incoming request and replaces `Authorization` with `Bearer {API_KEY}`. This is correct — the companion authenticates to Hermes with its own API key, not the user's Basic auth. **No issue.**

---

## 7. Kanban CLI Wrapper (_kanban)

### F-14 [MINOR] — _kanban() default timeout of 30s may be too short for slow operations

**File:** `server/server.py`, L139
**Detail:** The default timeout is 30 seconds. Operations like `hermes kanban show <id>` that need to spawn an agent worker could take longer. The `handle_kanban_task_show` handler (L244-258) uses the default timeout. If the Hermes CLI hangs or is slow, the request will timeout with a generic error.

**Fix:** Increase the default timeout to 60s, or make it configurable per-route.

### F-15 [INFO] — _kanban() correctly handles edge cases

**File:** `server/server.py`, L139-151
**Detail:** The function handles `TimeoutExpired` (returns -1 with message) and `FileNotFoundError` (returns -1 with message). The callers all check `code != 0` and return appropriate HTTP error responses. **No issue.**

---

## 8. Health Endpoint

### F-16 [MINOR] — Health endpoint uses wrong aiohttp ClientSession context manager pattern

**File:** `server/server.py`, L155-169
**Detail:** At L161, the code uses `async with session.get(...) as resp:` — this is the correct aiohttp pattern. However, the `session` is obtained from `HermesProxy.get_session()` which returns the shared singleton. The `async with` usage here is fine for a GET request. The health check correctly reports `degraded` when Hermes is unreachable. **No issue — this is correct.**

---

## Five-Axis Review Summary

### Axis 1: Correctness

| ID | Severity | File:Line | Finding |
|---|---|---|---|
| F-01 | CRITICAL | server.py (missing) | No GET /api/attachments/{id} — uploaded images can't be displayed |
| F-02 | MAJOR | server.py:308-311 | DELETE /api/sessions/{id} forwarded without confirming Hermes support |
| F-03 | MINOR | server.py:399 | `assign` CLI subcommand may not exist in hermes |
| F-04 | MINOR | server.py:261-280 | Dead code: metadata extraction from body never triggered by client |
| F-05 | MINOR | server.py:283-303 | No text length limit on comments |
| F-06 | MAJOR | server.py:177-191 | Session create normalization only handles one response shape |
| F-07 | MINOR | server.py:205-258 | Kanban responses pass through raw CLI output — field name mismatches possible |

### Axis 2: Readability & Simplicity

The server code is well-structured with clear handler functions, consistent error handling patterns, and good separation of concerns (auth, proxy, routes). The docstrings on each handler are helpful. No significant readability issues.

### Axis 3: Architecture

| ID | Severity | File:Line | Finding |
|---|---|---|---|
| F-08 | CRITICAL | server.py (missing) | Attachment upload without serving is an incomplete feature |
| F-09 | MAJOR | server.py:314-381 | No server-side slug validation — relies on client-side only |
| F-10 | MINOR | server.py:409-437 | Filename used in path without sanitization |
| F-11 | MINOR | server.py:372-381 | No server-side protection for "default" board deletion |

### Axis 4: Security

| ID | Severity | File:Line | Finding |
|---|---|---|---|
| F-09 | MAJOR | server.py:314-381 | Slug not validated — potential for confusing hermes CLI |
| F-10 | MINOR | server.py:409-437 | Filename path traversal risk |
| F-11 | MINOR | server.py:372-381 | Default board deletable via direct API call |
| F-05 | MINOR | server.py:283-303 | No comment text length limit |
| — | INFO | server.py:117-119 | Auth header stripping/replacement is correct |
| — | INFO | server.py:139-151 | subprocess.run with list (not shell=True) — no shell injection |

### Axis 5: Performance

| ID | Severity | File:Line | Finding |
|---|---|---|---|
| F-14 | MINOR | server.py:139 | 30s default timeout may be too short for slow kanban operations |
| — | INFO | server.py:106-108 | ClientSession singleton with 300s total timeout — appropriate |
| — | INFO | server.py:50-57 | Auth file reloaded on every request — good for rotation, minor I/O cost |

---

## API Contract Mismatches (Android Client vs Server)

| # | Android Expects | Server Returns | Match? | Impact |
|---|---|---|---|---|
| 1 | `POST /api/sessions` → `SessionsList` with `data[0].id` | Normalizes `{"session":{...}}` to `{"data":[{...}]}` | PARTIAL | Works only if Hermes returns `{"session":...}` shape |
| 2 | `POST /api/attachments` → `{"id":"att_...","url":"/api/attachments/att_..."}` | Returns that shape, but GET on the URL returns 404 | BROKEN | Images never display |
| 3 | `POST /api/kanban/tasks/{id}/complete` → ignores body, refreshes | Returns `{"ok":true}` | OK | No issue |
| 4 | `POST /api/kanban/tasks/{id}/comment` → ignores body, refreshes | Returns `{"ok":true}` | OK | No issue |
| 5 | `POST /api/kanban/tasks/{id}/assign` → ignores body, refreshes | Returns `{"ok":true}` | OK | No issue (if CLI subcommand exists) |
| 6 | `GET /api/kanban/boards` → `List<KanbanBoard>` | Raw CLI output | PARTIAL | Works only if CLI field names match Kotlin model |
| 7 | `GET /api/kanban/tasks` → `List<KanbanTask>` | Raw CLI output | PARTIAL | Works only if CLI field names match Kotlin model |
| 8 | `DELETE /api/sessions/{id}` → removes from local list | Forwards to Hermes | PARTIAL | Works only if Hermes supports DELETE |

---

## Findings Summary

### MUST-FIX (2 CRITICAL + 2 MAJOR)

| ID | Severity | Issue |
|---|---|---|
| F-01/F-08 | CRITICAL | No GET /api/attachments/{id} — uploaded images can't be displayed |
| F-02 | MAJOR | DELETE /api/sessions/{id} forwarded without Hermes support check |
| F-06 | MAJOR | Session create normalization only covers one response shape |
| F-09 | MAJOR | No server-side slug validation in board CRUD handlers |

### SHOULD-FIX (6 MINOR)

| ID | Severity | Issue |
|---|---|---|
| F-03 | MINOR | `assign` CLI subcommand may not exist |
| F-04 | MINOR | Dead metadata extraction code in complete handler |
| F-05 | MINOR | No comment text length limit |
| F-07 | MINOR | Kanban responses pass through raw CLI output |
| F-10 | MINOR | Attachment filename not sanitized |
| F-11 | MINOR | Default board deletable via direct API |

### NICE-TO-HAVE (3 MINOR + 2 INFO)

| ID | Severity | Issue |
|---|---|---|
| F-12 | MINOR | Content-Length header not stripped in forward() |
| F-14 | MINOR | 30s default timeout may be too short |
| F-13 | INFO | Auth header handling — confirmed correct |
| F-15 | INFO | _kanban() edge cases — confirmed correct |
| F-16 | INFO | Health endpoint — confirmed correct |

---

## Verdict

**MUST-FIX** — The server has good route coverage (all 16 Android API calls now have matching server handlers, a significant improvement from REVIEW_v2.md which found 6 missing). However, 2 critical issues remain:

1. **Attachment serving is broken** — files are uploaded but never served back. The Android app will always see broken image icons.
2. **Session delete may fail silently** — forwarding DELETE to Hermes without checking support could cause 502 errors.

The 2 major issues (response shape normalization and slug validation) are important for robustness but won't cause immediate runtime failures in the happy path.

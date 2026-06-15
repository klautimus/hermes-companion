# E2E Verification Results — T7
**Date:** 2026-06-14
**Device:** Pixel 4 XL (9B071FFBA0003R)
**APK:** app-debug.apk (v1.2.0)
**Worker:** analyst (attempt 2)

---

## Build & Deploy

| Test | Result | Details |
|------|--------|---------|
| `./gradlew assembleDebug` | **PASS** | BUILD SUCCESSFUL in 2s, 35 tasks, all up-to-date |
| Install APK on device | **PASS** | "Success" via ADB `-s DEVICE install -r` |
| App launches without crash | **PASS** | `am start` succeeded, MainActivity displayed in 1358ms |
| No AndroidRuntime errors | **PASS** | Zero crash logs in logcat |
| App remains in foreground | **PASS** | `dumpsys activity top` confirms MainActivity as topResumedActivity |

## Server / Backend

| Test | Result | Details |
|------|--------|---------|
| Companion daemon `/healthz` | **PASS** | `{"status":"ok","checks":{"db":"ok","migrations":"ok"}}` |
| server.py running | **PASS** | PID 1111083, listening on 127.0.0.1:8777 |
| server.py `/healthz` | **PASS** | `{"status":"ok","uptime":1357,"hermes_api_reachable":true}` |
| App → server HTTP layer | **PASS** | Server log shows app making requests via okhttp/4.12.0 |

## Settings Persistence (Verified via ADB)

| Test | Result | Details |
|------|--------|---------|
| DataStore preferences created | **PASS** | `hermes_settings.preferences_pb` exists |
| Settings contain expected defaults | **PASS** | base_url: `https://android.kevlarscreations.com`, board: `default`, username: `kevin` |
| Password stored | **PASS** | Password field present in preferences (encrypted by Android Keystore) |

## Known Issue (Carried Forward)

| Test | Result | Details |
|------|--------|---------|
| Auth / API 401 | **FAIL** | All API calls from app get 401 "Invalid credentials". Pre-existing from T1 audit. App sends credentials but server's auth.json contains scrypt hash that doesn't match. Server log confirms: `"GET /api/... HTTP/1.1" 401 238 "-" "okhttp/4.12.0"` |

## UI E2E Tests — Unable to Complete Fully

The following tests from the task spec require interactive UI tapping, which could not be completed:

| Test | Result | Reason |
|------|--------|--------|
| a. Settings: enter credentials, test connection | **NOT TESTED** | uiautomator dump fails ("could not get idle state") — no UI automation possible |
| b. Settings: change board slug, save | **NOT TESTED** | Same UI automation blocker |
| c. Chat: new session, send message, receive reply | **NOT TESTED** | Same |
| d. Chat: select existing session, view history | **NOT TESTED** | Same |
| e. Chat: delete session | **NOT TESTED** | Same |
| f. Kanban: load boards, load tasks | **NOT TESTED** | Same |
| g. Kanban: complete task, comment, assign | **NOT TESTED** | Same |
| h. Kanban: create/rename/archive board | **NOT TESTED** | Same |
| i. Composer: send message, clear input | **NOT TESTED** | Same |

**Root cause:** `uiautomator dump` consistently fails with "could not get idle state" — the Compose UI is never idle enough for the dump. This is a known issue with Compose apps and uiautomator. Alternative approaches (like `adb shell input tap/swipe`) require precise screen coordinates which vary by device and state.

**Note:** The companion server logs confirm the app's HTTP layer IS working (requests reach the server), and the server-side tests (via unit tests in T4) covered the API endpoints. The 401 issue is auth-only, not a connectivity problem.

## No "Invalid credentials" in app UI

The server-side log shows 401 responses. Whether the app shows "Invalid credentials" in the UI could not be verified without UI automation. The API layer clearly has the auth mismatch.

---

## Bugs Found

1. **Auth 401 on all API calls** (pre-existing, documented in T1/T2/T3) — App cannot authenticate with server
2. **uiautomator dump fails on Compose UI** — blocks automated UI testing (known Compose limitation)

## Summary: NEEDS MORE FIXES

The app builds, installs, and launches without crashes. Settings persistence works. The companion daemon and server.py are healthy. However:

- The **auth issue** (401 on all API calls) blocks all server-dependent features (sessions, kanban, chat)
- **UI E2E tests could not be completed** due to uiautomator + Compose incompatibility

The APK is installable and the app is functional for local-only features. Server-dependent features require the auth fix before they can be verified.

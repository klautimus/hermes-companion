# Intent: Hermes Companion Chat Interface — Full ChatGPT-Style App

**Confirmed:** 2026-06-14 | **Source:** interview-me session with Kevin
**Status:** Confirmed by user — autonomous execution until fully working

## Outcome
A fully functioning phone-first companion to the Hermes agent. ChatGPT-style messaging with searchable session management, full kanban board parity with the Hermes server, and attachment support.

## User
Kevin (klauts) — solo user on Pixel 4 XL in Kitimat BC, accessing his personal Hermes agent from his phone.

## Why now
The app is a non-functional skeleton. Session list shows ~4 sessions (paginated at 50 from Hermes, companion ignores pagination params). No search. New session creation errors. Messages fail to send. Kanban screen has no board search/create/switch UI.

## Success Criteria
- **Chat:** Send/receive works reliably. User message appears immediately. Response arrives after network round-trip.
- **Sessions:** ALL sessions visible in scrollable sidebar drawer. Searchable by title. Tap to switch. Swipe or menu to delete. New session creates cleanly.
- **Kanban:** Board list in sidebar drawer. Search boards. Create new boards. Switch boards. Full task management: create, complete, comment, assign, link. Everything the Hermes web kanban can do.
- **Attachments:** Send and receive attachments in chat.
- **Error handling:** Graceful error states that never permanently block the UI.

## Constraint
Streaming responses deferred to v2. Current implementation: non-streaming chat (send message, wait for full response).

## Out of scope
- Voice calling
- Token-by-token streaming
- E2EE / Signal Protocol (server-side)
- Multi-device relay (Telos scope)

## Execution mode
Fully autonomous kanban orchestration with review/debug/fix loops. Only stops when the final fully working app is finished and verified on device.

## Known bugs (from debugging)
1. **Companion route regex wrong:** `(?P<session_id>...)` Django-style — aiohttp needs `{session_id:...}`. Fixed in server.py but needs companion restart.
2. **Session pagination:** Hermes returns 50 sessions/page with `has_more`. App defaults to 50, companion ignores `limit`/`offset` query params. Need to fetch all pages and pass params through.
3. **Error parsing newSession:** Companion returns `{"session": {...}}` on 201 but app expects `{"data": [...]}`. ViewModel tries to decode wrong shape → exception → error banner.
4. **Chat send timeout:** Works through companion but read timeout (180s) may be too short for long responses. Need investigation.
5. **Companion pagination passthrough:** `sfwd()` strips `/android` prefix but doesn't forward query params like `limit`/`offset`.

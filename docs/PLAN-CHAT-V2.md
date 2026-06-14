# Implementation Plan: Hermes Companion Chat Interface v2

**Created:** 2026-06-14 | **Spec:** docs/SPEC-CHAT-V2.md

## Overview

Fix two fatal bugs preventing the chat interface from functioning, then verify end-to-end on device. The bugs are isolated — ViewModel state already works, MessageList and Composer composables render correctly — but the ChatScreen layout prevents rendering and the history load can race with message insertion.

## Architecture Decisions

1. **Layout: Column-only, no Box wrapping** — Remove the Box wrapper in ChatScreen. Use a single Column with TopAppBar → MessageList(weight 1f) → Composer. This is the standard Compose pattern for chat UIs.
2. **Data race: await history load before adding messages** — Convert `loadSessionHistory` from a fire-and-forget coroutine launch to a suspend function that returns the loaded messages. The caller awaits it before adding user/assistant messages to the list.
3. **Empty state: keep inside LazyColumn** — The "Start a conversation" placeholder stays as an item when messages list is empty. It renders correctly once the layout is fixed (no overlap).

## Task List

### Phase 1: Data Layer Fix (Foundation)
- [ ] **Task 1: Fix sendMessage() data race**

### Checkpoint: Data Layer
- [ ] Messages survive concurrent history load
- [ ] Existing tests pass

### Phase 2: UI Fix (Layout)
- [ ] **Task 2: Fix ChatScreen.kt layout**

### Checkpoint: UI
- [ ] Messages visible on screen after sending
- [ ] Composer pinned to bottom

### Phase 3: verify (End-to-End)
- [ ] **Task 3: Build APK, install, E2E verify**

### Checkpoint: Complete
- [ ] Chat works: send message → appears → response arrives
- [ ] Session drawer works
- [ ] New session works
- [ ] No crashes

## Task Details

---

### Task 1: Fix sendMessage() data race

**Description:** `sendMessage()` calls `loadSessionHistory()` in a separate `viewModelScope.launch` coroutine. When creating a new session, `loadSessionHistory` does `_chatMessages.value = data.map{...}` — replacing the entire list. This can race with the user message added at line 143. The user message gets wiped if history load finishes after the message was added.

**Fix approach:**
1. Convert `loadSessionHistory` to return the messages as a `List<ChatMessage>` instead of directly setting `_chatMessages`
2. In `sendMessage()`, await the history load FIRST (if creating new session), THEN add user + assistant messages
3. `selectSession()` can continue calling `loadSessionHistory` directly (no race there since it clears messages first)

**Acceptance criteria:**
- [ ] First message in a new session appears and is NOT wiped by history load
- [ ] Existing session messages continue to work
- [ ] No compilation errors

**Verification:**
- [ ] Build: `./gradlew assembleDebug` succeeds
- [ ] Manual: Send first message in new session → message stays visible

**Dependencies:** None

**Files touched:**
- `app/src/main/java/com/atlas/hermescompanion/MainViewModel.kt`

**Estimated scope:** Small (1 file)

---

### Task 2: Fix ChatScreen.kt layout

**Description:** `ChatScreen` wraps content in a `Box` containing a `Column` (TopAppBar + MessageList) and `Composer` as siblings. In a Box, children stack on top of each other. The Column lacks `fillMaxSize()`, so it wraps content. The MessageList's `weight(1f)` has no effect inside a Box-wrapped Column. The Composer overlays at top-left, covering the interface. Messages are never visible.

**Fix approach:**
Replace the Box+Column structure with a single Column:
```kotlin
Column(modifier = modifier.fillMaxSize()) {
    if (showDrawer) SessionDrawer(...)
    
    TopAppBar(...)
    error?.let { ErrorBanner(it) }
    MessageList(
        messages = messages,
        isStreaming = isStreaming,
        modifier = Modifier.weight(1f),
    )
    Composer(
        onSend = { text -> vm.sendMessage(text) },
        enabled = !isStreaming,
        modifier = Modifier.fillMaxWidth(),
    )
}
```

Keep the Composer import and function signature unchanged — only restructure the layout tree.

**Acceptance criteria:**
- [ ] MessageList fills available space between TopAppBar and Composer
- [ ] Composer is pinned to bottom, never overlaps messages
- [ ] Session drawer appears above the chat (drawer → Column layout preserved)
- [ ] TopAppBar visible and functional
- [ ] Error banner visible when present
- [ ] No visual regressions in empty state ("Start a conversation with Atlas.")
- [ ] Works in portrait and landscape

**Verification:**
- [ ] Build: `./gradlew assembleDebug` succeeds
- [ ] Manual: Open app → "Start a conversation" visible with input bar at bottom
- [ ] Manual: Type message → send → user message appears in chat area
- [ ] Manual: Rotate device → layout adapts, messages preserved

**Dependencies:** Task 1

**Files touched:**
- `app/src/main/java/com/atlas/hermescompanion/ChatScreen.kt`

**Estimated scope:** Small (1 file)

---

### Task 3: Build APK, install, E2E verify

**Description:** Build the debug APK, install on Pixel 4 XL via ADB, run verification checklist against live companion daemon. Confirm all success criteria from SPEC-CHAT-V2.md.

**Acceptance criteria:**
- [ ] APK builds clean with `./gradlew assembleDebug`
- [ ] APK installs and launches on device
- [ ] User message appears immediately after pressing Send
- [ ] Assistant response appears after network round-trip
- [ ] Messages auto-scroll to bottom
- [ ] Session drawer opens from hamburger, lists sessions
- [ ] New session button works
- [ ] Error banner shows when companion unreachable
- [ ] No crashes in logcat

**Verification:**
- [ ] Build succeeds
- [ ] `adb install -r` succeeds
- [ ] `adb shell am start` launches app
- [ ] Manual E2E checklist passed

**Dependencies:** Task 2

**Files touched:** None (build + deploy only)

**Estimated scope:** XS (no code)

---

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Companion daemon not running | Can't test E2E | Check `systemctl --user status companion` first |
| Composer stays inside Box in ChatScreen after refactor | Layout still broken | Verify Composer is direct child of Column, not Box |
| Datastore preferences wipe on reinstall | Session lost | Expected — install with `-r` (keep data) |

## Open Questions

None — spec is clear, bugs are isolated.

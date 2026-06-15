# Spec: Hermes Companion Chat Interface v2

**Created:** 2026-06-14 | **Author:** Atlas | **Status:** Awaiting review

## Objective

Build a fully functional ChatGPT-style chat interface in the Hermes Companion Android app. Users must see their outgoing messages appear immediately on screen, and see assistant responses stream in. The current UI has a fatal layout bug (Composer overlays message area) and a data race (messages wiped by concurrent history load). Both must be fixed. The UI must match the ChatGPT interaction model: messages fill the screen, input is pinned to the bottom, auto-scroll keeps the latest message visible.

## Scope

- Fix ChatScreen.kt layout so messages render in a scrollable area with composer pinned below
- Fix sendMessage() data race between history load and user message insertion
- Verify end-to-end: type message → see it appear → see response appear
- Verify session drawer, new session, error handling
- Verify portrait and landscape orientations

## Out of Scope

- Kanban screen (already working from review board)
- Settings screen (already working)
- Streaming responses (future; current is non-streaming)
- Companion daemon changes (server.py is fine)
- Cloudflare tunnel config

## Tech Stack

- Kotlin 1.9.22
- Jetpack Compose (BOM 2025.01.00, Material 3)
- kotlinx.serialization (JSON)
- DataStore (preferences)
- OkHttp (via ApiClient)
- Gradle 8.2.2, Android Gradle Plugin 8.2.2
- Min SDK 26, Target SDK 34

## Commands

```
Build debug:    cd ~/.hermes/projects/HermesCompanion && ./gradlew assembleDebug
Build release:  cd ~/.hermes/projects/HermesCompanion && ./gradlew assembleRelease
Install:        cmd.exe /c 'adb.exe install -r app/build/outputs/apk/debug/app-debug.apk'
Launch:         cmd.exe /c 'adb.exe shell am start -n org.hermes.community.companion/.MainActivity'
Logs:           cmd.exe /c 'adb.exe logcat -d -t 50 HermesCompanion:D AndroidRuntime:E *:S'
Test:           ./gradlew test
```

## Project Structure

```
app/src/main/java/org/hermes/community/companion/
  ChatScreen.kt       — Chat UI composable (LAYOUT BUG HERE)
  MessageList.kt       — LazyColumn + ChatBubble (renders fine)
  Composer.kt          — Input bar (renders fine)
  MainViewModel.kt     — Chat + Kanban state (DATA RACE HERE)
  KanbanScreen.kt      — Kanban tab
  SettingsScreen.kt    — Settings tab
  MainActivity.kt      — Bottom nav host
  data/
    ApiClient.kt       — OkHttp wrapper
    Models.kt          — Serializable data classes
    SessionManager.kt  — DataStore preferences
```

## Code Style

Bubble alignment: user right, assistant left.
Colors: user = `primary.copy(alpha=0.15f)`, assistant = `surfaceVariant`.
Radius: user (topStart=16, topEnd=16, bottomStart=16, bottomEnd=4), assistant inverted.
Spacing: 8dp between messages, 12dp horizontal padding.
Max bubble width: 320dp.

```kotlin
// Correct layout structure:
Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(...)
    error?.let { ErrorBanner(it) }
    MessageList(
        messages = messages,
        modifier = Modifier.weight(1f),  // fills remaining space
    )
    Composer(
        onSend = { vm.sendMessage(it) },
        modifier = Modifier.fillMaxWidth(),
    )
}
```

## Testing Strategy

- Manual E2E on device: type message → observe appearance → observe response
- Verify session drawer opens/closes with hamburger icon
- Verify new session button clears and starts fresh
- Verify error banner appears when companion is unreachable
- Verify rotate between portrait and landscape preserves message state
- Logcat monitoring for crash-free operation

## Success Criteria

- [ ] User message appears IMMEDIATELY in chat after pressing Send (within one frame)
- [ ] Assistant response appears after network round-trip
- [ ] Messages scroll naturally, auto-scroll to bottom on new message
- [ ] Input bar is pinned to bottom, never overlaps messages
- [ ] Session drawer opens from hamburger, lists sessions, tapping one switches
- [ ] New session button clears messages and creates fresh context
- [ ] Error banner shows when companion unreachable, doesn't block UI
- [ ] No crashes in 5-minute usage session
- [ ] No data race between history load and message insertion

## Open Questions

1. Should we add a loading spinner during network round-trip? (Current: streaming cursor `▌`)
2. Should error banner auto-dismiss after N seconds?
3. Should we persist chat messages across app restarts? (Current: ephemeral)

## Boundaries

- **Always do:** Run `./gradlew assembleDebug` after any code change, install APK to verify
- **Ask first:** Changes to data layer (ApiClient, Models), changes to companion daemon, adding new dependencies
- **Never do:** Modify KanbanScreen or SettingsScreen (out of scope), change API contract without syncing daemon

# Plan 024: Streaming Chat Display

> **Executor**: Use CodeGraph MCP tools for Android at `/home/kevin/.hermes/projects/HermesCompanion`.

## Status
- **Priority**: P1 | **Effort**: M | **Risk**: MED | **Depends on**: daemon 019 | **Category**: feature
- **Planned at**: commit `4a85552`, 2026-06-19

## Why this matters
Chat currently blocks until the full response arrives. Streaming gives incremental text display like ChatGPT — a much better UX.

## Current state
- MainViewModel.sendMessage() uses blocking POST to `/v1/chat/completions`
- Full response arrives, then displayed
- No streaming infrastructure on Android side

## Scope
**In scope**: `MainViewModel.kt`, `ApiClient.kt`, `ChatScreen.kt`
**Out of scope**: Daemon streaming endpoint (Plan 019)

## Steps

### Step 1: Add streaming consumer to ApiClient
Add a method that opens a streaming connection and calls back per chunk:
```kotlin
fun chatStream(messages: String, onChunk: (String) -> Unit, onError: (String) -> Unit) {
    // POST to /v1/chat/completions/stream
    // Read SSE events line by line
    // Parse data: {...} JSON chunks
    // Extract content delta, call onChunk(text)
}
```

### Step 2: Modify MainViewModel.sendMessage for streaming
1. Create an assistant ChatMessage immediately (empty)
2. Start streaming
3. On each chunk: append text to the assistant message
4. On complete: finalize the message
5. Add `isStreaming` StateFlow for cursor display

### Step 3: Add streaming cursor in ChatScreen
When `isStreaming` is true and the last message is from assistant:
- Show a blinking cursor at the end of the text
- Disable the send button while streaming

## Done criteria
- [ ] Text appears incrementally as the model generates
- [ ] Cursor shows while streaming
- [ ] Send button disabled during stream
- [ ] Stream completes and message is finalized
- [ ] `./gradlew assembleDebug` succeeds
- [ ] `git status` clean; `git log -1` shows commit

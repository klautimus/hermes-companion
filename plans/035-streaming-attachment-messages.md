# Plan 035: Use Streaming Chat for Attachment Messages

## Status
- **Priority**: P2 | **Effort**: S | **Risk**: LOW | **Depends on**: none | **Category**: tech-debt
- **Planned at**: commit `b09390e`, 2026-06-19

## Why this matters
`sendMessageWithAttachment()` uses non-streaming `c.chat()` while regular messages use `c.chatStream()`. Messages with attachments show a blank streaming placeholder until the full response arrives — no progress indicator.

## Current state
**MainViewModel.kt:288**: `val reply = c.chat(history, sessionId = sid, attachmentIds = listOf(attId))` — non-streaming
vs line 217: `c.chatStream(messages = history, sessionId = sid, onChunk = { delta -> ... })` — streaming

## Scope
**In scope**: `MainViewModel.kt` only

## Steps

### Step 1: Replace `c.chat()` with `c.chatStream()` in sendMessageWithAttachment

```kotlin
c.chatStream(
    messages = history,
    sessionId = sid,
    attachmentIds = listOf(attId),
    onChunk = { delta ->
        _chatMessages.value = _chatMessages.value.map { msg ->
            if (msg.messageId == msgId && msg.isStreaming) {
                msg.copy(content = msg.content + delta)
            } else msg
        }
    },
)
_isStreaming.value = false
finalizeAssistant(msgId, _chatMessages.value.firstOrNull { it.messageId == msgId }?.content ?: "")
```

**Verify**: `./gradlew assembleDebug --no-daemon` → BUILD SUCCESSFUL

## Done criteria
- [ ] Attachment messages stream chunk-by-chunk like regular messages
- [ ] APK builds clean
- [ ] `git status` CLEAN

# Plan 023: Attachment Display in Chat Messages

> **Executor**: Use CodeGraph MCP tools for Android at `/home/kevin/.hermes/projects/HermesCompanion`.

## Status
- **Priority**: P0 | **Effort**: M | **Risk**: LOW | **Depends on**: daemon 018 | **Category**: feature
- **Planned at**: commit `4a85552`, 2026-06-19

## Why this matters
The Composer has attachment UI but sent attachments don't appear in the chat. Users need to see images they sent and receive.

## Current state
- Composer has image picker, camera, file picker → calls `onSendAttachment(filename, bytes, contentType)`
- ApiClient has `uploadAttachment()` that POSTs to `/api/attachments`
- MessageList.ChatBubble has `message.attachmentUrl` field
- BUT: attachment upload is not wired to message sending, and `attachmentUrl` is never populated

## Scope
**In scope**: `MainViewModel.kt`, `MessageList.kt`, `Composer.kt`
**Out of scope**: Daemon backend (Plan 018)

## Steps

### Step 1: Wire attachment upload in sendMessage
In MainViewModel.sendMessage():
1. Check for pending attachment bytes
2. Upload via `apiClient.uploadAttachment()` → get attachment_id
3. Include attachment reference in chat message
4. Create a ChatMessage with `attachmentUrl` set to `/api/attachments/{id}`

### Step 2: Display inline images in ChatBubble
In MessageList.ChatBubble:
1. Check `message.attachmentUrl`
2. If URL ends with image extension or content type is image:
   - Render with `AsyncImage(model = "$baseUrl${message.attachmentUrl}")` 
   - Add auth headers via Coil's OkHttpClient
3. If non-image: show filename + download icon

### Step 3: Handle Coil auth
Attachments require Basic Auth. Configure Coil's ImageLoader with an OkHttp client that adds the auth header:
```kotlin
val imageLoader = ImageLoader.Builder(context)
    .okHttpClient { authedHttpClient }
    .build()
```

## Done criteria
- [ ] Select image in Composer → send → image appears inline in chat
- [ ] Image persists across session reload
- [ ] Non-image files show filename + download icon
- [ ] `./gradlew assembleDebug` succeeds
- [ ] `git status` clean; `git log -1` shows commit

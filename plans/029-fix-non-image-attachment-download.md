# Plan 029: Fix Non-Image Attachment Download in Chat

> **Executor instructions**: Follow this plan step by step.

## Status
- **Priority**: P2 | **Effort**: S | **Risk**: LOW | **Depends on**: nothing
- **Category**: bug
- **Planned at**: commit `492da45`, 2026-06-19

## Why this matters

Non-image attachments in chat show a download button that does nothing. Users can see
a file was attached but can't access it.

## Current state
`MessageList.kt:84`: `IconButton(onClick = { /* download handled by Coil */ })` — no-op.
Coil only loads images, not arbitrary files.

## Fix

Open the attachment URL in the browser via Intent:
```kotlin
val context = LocalContext.current
IconButton(onClick = {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}) {
    Icon(Icons.Filled.Download, "Download")
}
```

## Scope
- `app/src/main/java/org/hermes/community/companion/MessageList.kt`

## Done criteria
- [ ] `./gradlew assembleDebug --no-daemon` exits 0
- [ ] No `/* download handled by Coil */` comment remains
- [ ] `git status` is CLEAN

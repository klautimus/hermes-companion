# Plan 034: Add Emoji Shortcode Support to Markdown

## Status
- **Priority**: P2 | **Effort**: S | **Risk**: LOW | **Depends on**: none | **Category**: tech-debt
- **Planned at**: commit `b09390e`, 2026-06-19

## Why this matters
Markwon doesn't expand emoji shortcodes (`:smile:` → 😄). AI responses commonly include shortcodes. User requirement: "full emoji support."

## Current state
**MarkdownText.kt** — Markwon configured with strikethrough, tables, tasklist, linkify, syntax highlighting. No emoji plugin.

## Scope
**In scope**: `MarkdownText.kt`, `app/build.gradle.kts`

## Steps

### Step 1: Add emoji plugin dependency
```kotlin
implementation("io.noties:markwon-emoji:4.6.2")
```

### Step 2: Register emoji plugin in Markwon builder
```kotlin
.usePlugin(io.noties.markwon.emoji.EmojiPlugin.create(
    io.noties.markwon.emoji.j.EmojiJ.create()
))
```

EmojiJ uses the system emoji font for shortcode → glyph mapping. Android 9+ (minSdk 28) has full emoji support.

**Verify**: `./gradlew assembleDebug --no-daemon` → BUILD SUCCESSFUL

## Done criteria
- [ ] `:smile:` renders as 😄 in chat
- [ ] `:rocket:` renders as 🚀
- [ ] Unknown shortcodes render as-is (no crash)
- [ ] APK builds clean
- [ ] `git status` CLEAN

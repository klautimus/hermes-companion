# Plan 025: Markdown Rendering Polish

> **Executor**: Use CodeGraph MCP tools for Android at `/home/kevin/.hermes/projects/HermesCompanion`.

## Status
- **Priority**: P1 | **Effort**: S | **Risk**: LOW | **Depends on**: nothing | **Category**: polish
- **Planned at**: commit `4a85552`, 2026-06-19

## Why this matters
Kevin wants "a beautiful and easy to read experience with full support for emojis." MarkdownText.kt exists (Plan 007) but needs verification and polish for emoji rendering, code blocks, and visual quality.

## Current state
MarkdownText.kt uses Markwon 4.6.2 with core + strikethrough extensions. Converts markdown to Spanned → AnnotatedString. Works for finalized messages.

Known limitations (from prior research):
- `.blockquoteColor()` not resolvable in Kotlin (Java-only API)
- Streaming text uses plain Text() (deferred to v2)
- Emoji rendering depends on system font — Pixel 4 XL has good emoji support

## Scope
**In scope**: `MarkdownText.kt`, `build.gradle.kts` (app level)
**Out of scope**: Streaming markdown rendering

## Steps

### Step 1: Verify emoji rendering
Test with common emojis (🚀✅❌🎯💡 etc.) in a markdown message. If system font renders them correctly on Pixel 4 XL (it should), no additional work needed. If not, add an emoji compat library.

### Step 2: Add code block rendering
Add Markwon syntax-highlighting extension:
```kotlin
implementation("io.noties:markwon-ext-latex:4.6.2")  // if needed
implementation("io.noties:markwon-syntax-highlight:4.6.2")  // prism4j based
```
Configure with a dark theme for code blocks.

### Step 3: Add table rendering
```kotlin
implementation("io.noties:markwon-ext-table:4.6.2")
```

### Step 4: Add link rendering
Ensure links in markdown are clickable. Add `markwon-linkify:4.6.2` if not already included.

### Step 5: Visual polish
- Adjust line height for readability
- Ensure proper text wrapping in chat bubbles (max width constraint)
- Add subtle padding around code blocks
- Verify dark mode rendering

## Done criteria
- [ ] Emojis render correctly in chat bubbles
- [ ] Code blocks render with syntax highlighting
- [ ] Links are clickable
- [ ] Tables render properly
- [ ] Dark mode looks good
- [ ] `./gradlew assembleDebug` succeeds
- [ ] `git status` clean; `git log -1` shows commit

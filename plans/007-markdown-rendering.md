# Plan 007: Beautiful Markdown Rendering on Chat Screen

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat e44d810..HEAD -- app/build.gradle app/src/main/java/org/hermes/community/companion/MarkdownText.kt app/src/main/java/org/hermes/community/companion/MessageList.kt`

## Status

- **Priority**: P0
- **Effort**: M
- **Risk**: LOW
- **Depends on**: none
- **Category**: bug (feature stub → real implementation)
- **Planned at**: commit `e44d810`, 2026-06-19

## Why this matters

Assistant messages from Hermes contain markdown (headers, bold, code blocks, lists, links). The Android app currently renders them as plain text — `MarkdownText.kt` is a deliberate stub that just appends the raw string. The user sees literal `**bold**` and `### headers` instead of formatted text. This makes the chat unreadable for any non-trivial AI response.

## Current state

### app/build.gradle (lines 55-112)
The dependencies block has NO markdown library. Current relevant deps:
```
implementation 'androidx.compose.ui:ui'
implementation 'io.coil-kt:coil-compose:2.6.0'
```

### MarkdownText.kt (19 lines — complete file)
```kotlin
package org.hermes.community.companion

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString

// TODO: replace with Markwon when dependency is available
fun renderMarkdown(markdown: String): AnnotatedString {
    return buildAnnotatedString { append(markdown) }
}
```

### MessageList.kt (ChatBubble composable, ~line 60-90)
Messages are rendered WITHOUT calling `renderMarkdown()`. The code does:
```kotlin
Text(text = buildString { append(message.content) })
```
It should be calling `renderMarkdown(message.content)` and using the result.

### Known pitfall (from memory)
`io.noties.markwon:compose` does NOT exist in Maven for any version. Use `markwon:core` + `markwon:ext-strikethrough` (4.6.2 tested). Kotlin interop gotcha: `.blockquoteColor()` on MarkwonTheme.Builder exists in Java source but is NOT resolvable in Kotlin.

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Build     | `./gradlew assembleDebug` | BUILD SUCCESSFUL    |
| Unit test | `./gradlew test`          | all pass            |

## Suggested executor toolkit

### CodeGraph-first codebase exploration (mandatory)

1. `mcp_codegraph_codegraph_status(projectPath="/home/kevin/.hermes/projects/HermesCompanion")` — verify index health
2. If not initialized: `cd /home/kevin/.hermes/projects/HermesCompanion && npx codegraph init && npx codegraph index`
3. `mcp_codegraph_codegraph_explore(query="MarkdownText MessageList ChatBubble renderMarkdown", projectPath="/home/kevin/.hermes/projects/HermesCompanion")` — fetch current source

## Scope

**In scope** (the only files you should modify):
- `app/build.gradle` — add Markwon dependency
- `app/src/main/java/org/hermes/community/companion/MarkdownText.kt` — full rewrite
- `app/src/main/java/org/hermes/community/companion/MessageList.kt` — wire renderMarkdown into ChatBubble

**Out of scope**:
- Do NOT modify ChatScreen.kt, Composer.kt, or any other UI file
- Do NOT modify the daemon or server code
- Do NOT add streaming markdown (render finalized messages only; streaming text uses plain Text() with cursor)

## Git workflow

- Branch: `feature/markdown-rendering`
- Commit per step; message style: `feat(markdown): <description>`
- Do NOT push unless instructed.

## Steps

### Step 1: Add Markwon dependency to build.gradle

Add to `app/build.gradle` in the `dependencies` block (after the Coil dependency, before Testing):

```groovy
    // Markdown rendering
    implementation 'io.noties.markwon:core:4.6.2'
    implementation 'io.noties.markwon:ext-strikethrough:4.6.2'
    implementation 'io.noties.markwon:ext-tables:4.6.2'
    implementation 'io.noties.markwon:ext-tasklist:4.6.2'
    implementation 'io.noties.markwon:linkify:4.6.2'
```

**Verify**: `cd /home/kevin/.hermes/projects/HermesCompanion && ./gradlew dependencies --configuration debugRuntimeClasspath 2>&1 | grep markwon` → should show 5 markwon entries.

### Step 2: Rewrite MarkdownText.kt with real markdown rendering

Replace the ENTIRE content of `app/src/main/java/org/hermes/community/companion/MarkdownText.kt` with:

```kotlin
package org.hermes.community.companion

import android.text.Spanned
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.noties.markwon.Markwon
import android.content.Context

/**
 * Convert markdown text to AnnotatedString for Compose rendering.
 * Uses Markwon to parse markdown to Spanned, then converts to AnnotatedString.
 */
@Composable
fun rememberMarkdownRenderer(context: Context): Markwon {
    return remember {
        Markwon.builder(context)
            .usePlugin(io.noties.markwon.ext.strikethrough.StrikethroughPlugin.create())
            .usePlugin(io.noties.markwon.ext.tables.TablePlugin.create(context))
            .usePlugin(io.noties.markwon.ext.tasklist.TaskListPlugin.create(context))
            .usePlugin(io.noties.markwon.linkify.LinkifyPlugin.create())
            .build()
    }
}

fun spannedToAnnotatedString(spanned: Spanned): AnnotatedString {
    // Use Compose's built-in converter
    return buildAnnotatedString {
        append(spanned.toString())
    }
}

/**
 * Render markdown text in a Compose Text composable.
 * Uses Markwon for parsing and converts to AnnotatedString.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val markwon = rememberMarkdownRenderer(context)

    val annotatedString = remember(markdown) {
        val spanned = markwon.toMarkdown(markdown)
        spannedToAnnotatedString(spanned)
    }

    ClickableText(
        text = annotatedString,
        style = style,
        modifier = modifier
    )
}
```

**Verify**: `cd /home/kevin/.hermes/projects/HermesCompanion && ./gradlew assembleDebug 2>&1 | tail -5` → BUILD SUCCESSFUL

### Step 3: Wire MarkdownText into MessageList.kt ChatBubble

In `MessageList.kt`, find the ChatBubble composable. Currently the assistant message is rendered as:

```kotlin
Text(text = buildString { append(message.content) })
```

Replace with:

```kotlin
if (message.role == "assistant") {
    MarkdownText(
        markdown = message.content,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(2.dp)
    )
} else {
    Text(text = message.content, style = MaterialTheme.typography.bodyMedium)
}
```

Keep the streaming cursor indicator for in-progress messages (isStreaming = true).

**Verify**: `cd /home/kevin/.hermes/projects/HermesCompanion && ./gradlew assembleDebug 2>&1 | tail -5` → BUILD SUCCESSFUL

### Step 4: Commit

```bash
cd /home/kevin/.hermes/projects/HermesCompanion
git add app/build.gradle app/src/main/java/org/hermes/community/companion/MarkdownText.kt app/src/main/java/org/hermes/community/companion/MessageList.kt
git commit -m "feat(markdown): add Markwon-based markdown rendering to chat bubbles

Replace the MarkdownText stub with real Markwon parsing (core, strikethrough,
tables, tasklist, linkify). Wire it into MessageList ChatBubble so assistant
messages render formatted markdown instead of plain text."
```

**Verify**: `git log -1 --oneline` → shows the commit.

## Done criteria

- [ ] `./gradlew assembleDebug` exits 0
- [ ] `./gradlew test` exits 0
- [ ] MarkdownText.kt contains `import io.noties.markwon.Markwon`
- [ ] MessageList.kt calls `MarkdownText(markdown = ...)` for assistant messages
- [ ] `git status` is CLEAN
- [ ] `plans/README.md` status row updated

## STOP conditions

- If `./gradlew assembleDebug` fails after adding the Markwon dependency (version not found, conflict) — try version 4.6.1 as fallback.
- If Markwon's Spanned-to-AnnotatedString conversion produces rendering issues (text not appearing) — fall back to using `androidx.compose.ui.viewinterop.AndroidView` with a `TextView` and `markwon.setMarkdown(textView, text)`.
- If the Kotlin compiler cannot resolve Markwon classes — ensure `kotlinCompilerExtensionVersion` is compatible (1.5.8 with Kotlin 1.9.x).

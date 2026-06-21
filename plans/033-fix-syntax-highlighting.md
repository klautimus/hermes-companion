# Plan 033: Fix Syntax Highlighting (Prism4j Grammar Definitions)

## Status
- **Priority**: P2 | **Effort**: S | **Risk**: LOW | **Depends on**: none | **Category**: tech-debt
- **Planned at**: commit `b09390e`, 2026-06-19

## Why this matters
Prism4j's grammar locator returns `null` for ALL languages. Code blocks render with dark background but no syntax coloring. SyntaxHighlightPlugin is registered but non-functional — it's a stub.

## Current state
**MarkdownText.kt:39-43**:
```kotlin
val prism4j = io.noties.prism4j.Prism4j(
    object : io.noties.prism4j.GrammarLocator {
        override fun grammar(prism4j: io.noties.prism4j.Prism4j, language: String) = null  // ← STUB
        override fun languages(): Set<String> = Collections.emptySet()
    }
)
```

## Scope
**In scope**: `MarkdownText.kt`, `app/build.gradle.kts`

## Steps

### Step 1: Add Prism4j grammar dependencies
In `app/build.gradle.kts`:
```kotlin
implementation("io.noties:prism4j-bundler:4.6.2")
```

### Step 2: Replace null GrammarLocator with real grammar definitions
```kotlin
val prism4j = io.noties.prism4j.Prism4j(object : io.noties.prism4j.GrammarLocator {
    override fun grammar(prism4j: io.noties.prism4j.Prism4j, language: String) = when (language) {
        "kotlin" -> Prism_kotlin.create(prism4j)
        "python" -> Prism_python.create(prism4j)
        "json" -> Prism_json.create(prism4j)
        "bash" -> Prism_bash.create(prism4j)
        "yaml" -> Prism_yaml.create(prism4j)
        "java" -> Prism_java.create(prism4j)
        "go" -> Prism_go.create(prism4j)
        "javascript", "js" -> Prism_javascript.create(prism4j)
        "markdown", "md" -> Prism_markdown.create(prism4j)
        else -> null
    }
    override fun languages() = setOf("kotlin","python","json","bash","yaml","java","go","javascript","markdown")
})
```

**Verify**: `./gradlew assembleDebug --no-daemon` → BUILD SUCCESSFUL

## Done criteria
- [ ] Code blocks in chat render with syntax colors for supported languages
- [ ] APK builds clean
- [ ] `git status` CLEAN

## STOP conditions
- `prism4j-bundler` artifact not found (use manual `Prism_*.create()` classes from `io.noties:prism4j:4.6.2`)

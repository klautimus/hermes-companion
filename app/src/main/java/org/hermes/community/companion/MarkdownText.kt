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
        modifier = modifier,
        onClick = { /* no-op: links handled by LinkifyPlugin */ }
    )
}

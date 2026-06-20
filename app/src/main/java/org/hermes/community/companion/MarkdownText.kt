package org.hermes.community.companion

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import io.noties.markwon.Markwon
import java.util.Collections

/**
 * Build a Markwon instance with all desired extensions.
 */
@Composable
fun rememberMarkdownRenderer(context: Context): Markwon {
    return remember {
        val prism4j = io.noties.prism4j.Prism4j(
            object : io.noties.prism4j.GrammarLocator {
                override fun grammar(prism4j: io.noties.prism4j.Prism4j, language: String) = null
                override fun languages(): Set<String> = Collections.emptySet()
            }
        )
        Markwon.builder(context)
            .usePlugin(io.noties.markwon.ext.strikethrough.StrikethroughPlugin.create())
            .usePlugin(io.noties.markwon.ext.tables.TablePlugin.create(context))
            .usePlugin(io.noties.markwon.ext.tasklist.TaskListPlugin.create(context))
            .usePlugin(io.noties.markwon.linkify.LinkifyPlugin.create())
            .usePlugin(io.noties.markwon.syntax.SyntaxHighlightPlugin.create(prism4j, io.noties.markwon.syntax.Prism4jThemeDarkula.create()))
            .build()
    }
}

/**
 * Convert an Android Spanned to a Compose AnnotatedString,
 * preserving span styling from Markwon (bold, italic, code,
 * strikethrough, links, etc.).
 */
fun spannedToAnnotatedString(spanned: Spanned): AnnotatedString {
    return buildAnnotatedString {
        append(spanned.toString())

        // Copy the full text first, then apply spans
        val text = spanned.toString()
        if (text.isEmpty()) return@buildAnnotatedString

        // Walk all spans in the Spanned and convert to Compose SpanStyle
        val spans = spanned.getSpans(0, text.length, Any::class.java)
        for (span in spans) {
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)
            when (span) {
                is StyleSpan -> {
                    when (span.style) {
                        Typeface.BOLD -> addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold),
                            start, end
                        )
                        Typeface.ITALIC -> addStyle(
                            SpanStyle(fontStyle = FontStyle.Italic),
                            start, end
                        )
                        Typeface.BOLD_ITALIC -> addStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic
                            ),
                            start, end
                        )
                    }
                }
                is UnderlineSpan -> addStyle(
                    SpanStyle(textDecoration = TextDecoration.Underline),
                    start, end
                )
                is StrikethroughSpan -> addStyle(
                    SpanStyle(textDecoration = TextDecoration.LineThrough),
                    start, end
                )
                is ForegroundColorSpan -> addStyle(
                    SpanStyle(color = Color(span.foregroundColor)),
                    start, end
                )
                is BackgroundColorSpan -> addStyle(
                    SpanStyle(background = Color(span.backgroundColor)),
                    start, end
                )
                is RelativeSizeSpan -> addStyle(
                    SpanStyle(fontSize = (span.sizeChange * 16).sp),
                    start, end
                )
                is URLSpan -> {
                    addStyle(
                        SpanStyle(
                            color = Color(0xFF1A73E8),
                            textDecoration = TextDecoration.Underline
                        ),
                        start, end
                    )
                    addStringAnnotation(
                        tag = "URL",
                        annotation = span.url,
                        start = start,
                        end = end
                    )
                }
                is ClickableSpan -> {
                    addStyle(
                        SpanStyle(
                            color = Color(0xFF1A73E8),
                            textDecoration = TextDecoration.Underline
                        ),
                        start, end
                    )
                }
            }
        }
    }
}

/**
 * Render markdown text in a Compose Text composable.
 * Uses Markwon for parsing and converts to AnnotatedString.
 * Links are clickable and open in the browser.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val context = LocalContext.current
    val markwon = rememberMarkdownRenderer(context)

    val annotatedString = remember(markdown) {
        val spanned = markwon.toMarkdown(markdown)
        spannedToAnnotatedString(spanned)
    }

    ClickableText(
        text = annotatedString,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        // No browser available or invalid URL — ignore
                    }
                }
        }
    )
}

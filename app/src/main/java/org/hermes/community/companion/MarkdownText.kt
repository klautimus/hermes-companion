package org.hermes.community.companion

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString

/**
 * Simple markdown rendering without external dependencies.
 * Returns plain text for now - replace with Markwon when dependency is available.
 */
@Composable
fun renderMarkdown(markdown: String): AnnotatedString {
    return remember(markdown) {
        buildAnnotatedString {
            append(markdown)
        }
    }
}
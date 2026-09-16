package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.LightPrimary

/**
 * Data model for a detected hyperlink within episode show notes.
 */
data class ExtractedNoteLink(
    val title: String,
    val url: String
)

/**
 * Safely normalizes and opens a URL in an external browser.
 */
fun openUrlInBrowser(context: Context, rawUrl: String) {
    try {
        var url = rawUrl.trim()
        if (url.isEmpty()) return

        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            url = "https://$url"
        }

        val parsedUri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, parsedUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Throwable) {
        Log.e("NotesHyperlink", "Failed to open link: $rawUrl", e)
    }
}

/**
 * Normalizes URL string by ensuring http/https protocol prefix.
 */
fun normalizeNoteUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    return if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
        "https://$trimmed"
    } else {
        trimmed
    }
}

/**
 * Helper that parses episode show notes (HTML or plain text) into an AnnotatedString
 * with clickable hyperlinks and extracts all unique links.
 */
object NotesHyperlinkParser {

    private val HTML_LINK_REGEX = Regex(
        """<a\s+[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val RAW_URL_REGEX = Regex(
        """\b(?:https?://[^\s<>"'{}|\\^`]+|www\.[^\s<>"'{}|\\^`]+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Unescapes common HTML entities.
     */
    fun unescapeHtml(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
    }

    /**
     * Extracts all unique links from either HTML or plain text notes.
     */
    fun extractLinks(rawText: String): List<ExtractedNoteLink> {
        val results = mutableListOf<ExtractedNoteLink>()
        val seenUrls = mutableSetOf<String>()

        // 1. Extract from <a href="...">...</a>
        HTML_LINK_REGEX.findAll(rawText).forEach { match ->
            val url = match.groupValues[1].trim()
            val rawLabel = match.groupValues[2].replace(Regex("<[^>]*>"), "").trim()
            val cleanLabel = unescapeHtml(rawLabel)
            val finalUrl = normalizeNoteUrl(url)

            if (finalUrl.isNotBlank() && seenUrls.add(finalUrl)) {
                val displayTitle = if (cleanLabel.isNotBlank()) cleanLabel else finalUrl
                results.add(ExtractedNoteLink(displayTitle, finalUrl))
            }
        }

        // 2. Extract from raw URLs in text (excluding already found)
        RAW_URL_REGEX.findAll(rawText).forEach { match ->
            var url = match.value.trim()
            while (url.isNotEmpty() && (url.endsWith(".") || url.endsWith(",") || url.endsWith(")") || url.endsWith(";"))) {
                url = url.dropLast(1)
            }
            val finalUrl = normalizeNoteUrl(url)
            if (finalUrl.isNotBlank() && seenUrls.add(finalUrl)) {
                // Shorten title for display if needed
                val displayTitle = url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
                results.add(ExtractedNoteLink(displayTitle, finalUrl))
            }
        }

        return results
    }

    /**
     * Builds an AnnotatedString where all URLs and HTML links are converted to clickable link annotations.
     */
    fun parseToAnnotatedString(
        rawText: String,
        linkColor: Color,
        onUrlClick: ((String) -> Unit)? = null
    ): AnnotatedString {
        if (rawText.isBlank()) return AnnotatedString("")

        // Pre-clean standard HTML block tags to newlines
        var processed = rawText
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""</p\s*>""", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("""<p\s*>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<li\s*>""", RegexOption.IGNORE_CASE), "\n• ")
            .replace(Regex("""</li\s*>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<ul\s*>|<ol\s*>|</ul\s*>|</ol\s*>""", RegexOption.IGNORE_CASE), "")

        val linkStyle = TextLinkStyles(
            style = SpanStyle(
                color = linkColor,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline
            )
        )

        // Intermediate representation: find all links (HTML <a> or raw URL) with their exact ranges
        val tokens = mutableListOf<TokenModel>()

        var cursor = 0
        // Find HTML links
        val aMatches = HTML_LINK_REGEX.findAll(processed).toList()

        if (aMatches.isNotEmpty()) {
            for (m in aMatches) {
                val start = m.range.first
                val end = m.range.last + 1

                if (start > cursor) {
                    val plainChunk = processed.substring(cursor, start)
                    tokenizePlainUrls(plainChunk, tokens)
                }

                val linkUrl = normalizeNoteUrl(m.groupValues[1].trim())
                val rawLinkText = m.groupValues[2].replace(Regex("<[^>]*>"), "").trim()
                val cleanLinkText = unescapeHtml(rawLinkText).ifBlank { linkUrl }

                tokens.add(TokenModel(cleanLinkText, isLink = true, url = linkUrl))
                cursor = end
            }

            if (cursor < processed.length) {
                val remaining = processed.substring(cursor)
                tokenizePlainUrls(remaining, tokens)
            }
        } else {
            tokenizePlainUrls(processed, tokens)
        }

        // Build the final AnnotatedString
        return buildAnnotatedString {
            for (token in tokens) {
                if (token.isLink && token.url != null) {
                    val linkAnnotation = LinkAnnotation.Url(
                        url = token.url,
                        styles = linkStyle,
                        linkInteractionListener = onUrlClick?.let { callback ->
                            { callback(token.url) }
                        }
                    )
                    pushLink(linkAnnotation)
                    append(token.text)
                    pop()
                } else {
                    // Strip any other lingering HTML tags
                    val cleanText = unescapeHtml(token.text.replace(Regex("<[^>]*>"), ""))
                    append(cleanText)
                }
            }
        }
    }

    private fun tokenizePlainUrls(
        chunk: String,
        tokens: MutableList<TokenModel>
    ) {
        val matches = RAW_URL_REGEX.findAll(chunk).toList()
        var pos = 0

        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1

            if (start > pos) {
                tokens.add(TokenModel(chunk.substring(pos, start), isLink = false))
            }

            var matchedUrl = match.value
            // Remove trailing punctuation if mistakenly captured (e.g. "https://example.com.")
            var trailingPunct = ""
            while (matchedUrl.isNotEmpty() && (matchedUrl.endsWith(".") || matchedUrl.endsWith(",") || matchedUrl.endsWith(")") || matchedUrl.endsWith(";"))) {
                trailingPunct = matchedUrl.takeLast(1) + trailingPunct
                matchedUrl = matchedUrl.dropLast(1)
            }

            val finalUrl = normalizeNoteUrl(matchedUrl)
            tokens.add(TokenModel(matchedUrl, isLink = true, url = finalUrl))

            if (trailingPunct.isNotEmpty()) {
                tokens.add(TokenModel(trailingPunct, isLink = false))
            }

            pos = end
        }

        if (pos < chunk.length) {
            tokens.add(TokenModel(chunk.substring(pos), isLink = false))
        }
    }

    private data class TokenModel(val text: String, val isLink: Boolean, val url: String? = null)
}

/**
 * Composable that displays show notes with clickable hyperlinks and a Quick Links bar.
 */
@Composable
fun NotesHyperlinkText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    linkColor: Color = CyberGreen,
    fontSize: TextUnit = 14.sp,
    lineHeight: TextUnit = 22.sp,
    showQuickLinksBar: Boolean = true
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val onOpenUrl: (String) -> Unit = { targetUrl ->
        try {
            uriHandler.openUri(targetUrl)
        } catch (e: Throwable) {
            openUrlInBrowser(context, targetUrl)
        }
    }

    val extractedLinks = remember(text) {
        NotesHyperlinkParser.extractLinks(text)
    }

    val annotatedString = remember(text, linkColor) {
        NotesHyperlinkParser.parseToAnnotatedString(
            rawText = text,
            linkColor = linkColor,
            onUrlClick = onOpenUrl
        )
    }

    Column(modifier = modifier) {
        // Quick Links Bar if there are multiple links discovered
        if (showQuickLinksBar && extractedLinks.isNotEmpty()) {
            Text(
                text = "LINKS IN DIESER EPISODE (${extractedLinks.size})",
                color = linkColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                extractedLinks.take(12).forEach { link ->
                    Surface(
                        onClick = { onOpenUrl(link.url) },
                        shape = RoundedCornerShape(8.dp),
                        color = linkColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, linkColor.copy(alpha = 0.35f)),
                        modifier = Modifier.testTag("note_link_chip_${link.url.hashCode()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Link,
                                contentDescription = null,
                                tint = linkColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = link.title.take(30) + if (link.title.length > 30) "…" else "",
                                color = linkColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Öffnen",
                                tint = linkColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Full Notes Text with inline clickable hyperlinks
        Text(
            text = annotatedString,
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            modifier = Modifier.testTag("notes_hyperlink_content")
        )
    }
}

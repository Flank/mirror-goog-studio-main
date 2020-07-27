/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.detector.api

import com.android.utils.SdkUtils
import com.android.utils.XmlUtils

/**
 * Lint error message, issue explanations and location descriptions
 * are described in a [.RAW] format which looks similar to text
 * but which can contain bold, symbols and links. These issues can
 * also be converted to plain text and to HTML markup, using the
 * [.convertTo] method.
 *
 * @see Issue.getExplanation
 * @see Issue.getBriefDescription
 */
enum class TextFormat {
    /**
     * Raw output format which is similar to text but allows some markup:
     *
     *  * HTTP urls (http://...)
     *  * Sentences immediately surrounded by * will be shown as italics.
     *  * Sentences immediately surrounded by ** will be shown as bold.
     *  * Sentences immediately surrounded by *** will be shown as bold italics.
     *  * Sentences immediately surrounded by ` will be shown using monospace
     *     fonts
     *  * You can escape the previous characters with a backslash, \. Backslash
     *    characters must themselves be escaped with a backslash, e.g. use \\.
     *  * If you want to use bold or italics within a word, you can use the
     *    trick of putting a zero-width space between the characters by entering
     *    a \\u200b unicode character.
     *  * Blocks of lines surrounded with ``` will be formatted as code; you
     *    can optionally add "xml", "java", "kotlin" etc immediately after ``` on
     *    the opening line to get syntax highlighting when lint supports it
     *    (such as in HTML reports.)
     *
     * Furthermore, newlines are converted to br's when converting newlines.
     * Note: It does not insert `<html>` tags around the fragment for HTML output.
     */
    RAW,

    /**
     * Plain text output
     */
    TEXT,

    /**
     * HTML formatted output (note: does not include surrounding `<html></html>` tags)
     */
    HTML,

    /**
     * HTML formatted output (note: does not include surrounding `<html></html>` tags).
     * This is like [.HTML], but it does not escape unicode characters with entities.
     *
     *
     * (This is used for example in the IDE, where some partial HTML support in some
     * label widgets support some HTML markup, but not numeric code character entities.)
     */
    HTML_WITH_UNICODE;

    /**
     * Converts the given text to HTML
     *
     * @param text the text to format
     * @return the corresponding text formatted as HTML
     */
    fun toHtml(text: String): String = convertTo(text, HTML)

    /**
     * Converts the given text to plain text
     *
     * @param text the text to format
     * @return the corresponding text formatted as HTML
     */
    fun toText(text: String): String = convertTo(text, TEXT)

    /**
     * Converts the given message to the given format. Note that some
     * conversions are lossy; e.g. once converting away from the raw format
     * (which contains all the markup) you can't convert back to it.
     * Note that you can convert to the format it's already in; that just
     * returns the same string.
     *
     * @param message the message to convert
     * @param to the format to convert to
     * @return a converted message
     */
    fun convertTo(message: String, to: TextFormat): String {
        if (this == to) {
            return message
        }

        when (this) {
            RAW -> {
                return when (to) {
                    RAW -> message
                    TEXT, HTML, HTML_WITH_UNICODE -> to.fromRaw(message)
                }
            }

            TEXT -> {
                return when (to) {
                    RAW -> textToRaw(message)
                    TEXT -> message
                    HTML, HTML_WITH_UNICODE -> XmlUtils.toXmlTextValue(message)
                }
            }
            HTML -> {
                return when (to) {
                    HTML -> message
                    HTML_WITH_UNICODE -> removeNumericEntities(message)
                    RAW, TEXT -> {
                        to.fromHtml(message)
                    }
                }
            }
            HTML_WITH_UNICODE -> {
                return when (to) {
                    HTML, HTML_WITH_UNICODE -> message
                    RAW, TEXT -> {
                        to.fromHtml(message)
                    }
                }
            }
        }
    }

    /** Converts to this output format from the given HTML-format text  */
    private fun fromHtml(html: String): String {
        assert(this == RAW || this == TEXT) { this }

        // Drop all tags; replace all entities, insert newlines
        // (this won't do wrapping)
        val sb = StringBuilder(html.length)
        var inPre = false
        var i = 0
        val n = html.length
        while (i < n) {
            val c = html[i]
            if (c == '<') {
                // Strip comments
                if (html.startsWith("<!--", i)) {
                    val end = html.indexOf("-->", i)
                    if (end == -1) {
                        break // Unclosed comment
                    } else {
                        i = end + 2
                    }
                    i++
                    continue
                }
                // Tags: scan forward to the end
                val begin: Int
                var isEndTag = false
                if (html.startsWith("</", i)) {
                    begin = i + 2
                    isEndTag = true
                } else {
                    begin = i + 1
                }
                i = html.indexOf('>', i)
                if (i == -1) {
                    // Unclosed tag
                    break
                }
                var end = i
                if (html[i - 1] == '/') {
                    end--
                    isEndTag = true
                }
                // TODO: Handle <pre> such that we don't collapse spaces and reformat there!
                // (We do need to strip out tags and expand entities)
                val tag = html.substring(begin, end).trim { it <= ' ' }
                if (tag.equals("br", ignoreCase = true)) {
                    sb.append('\n')
                } else if (tag.equals("p", ignoreCase = true) || // Most common block tags
                    tag.equals("div", ignoreCase = true) ||
                    tag.equals("pre", ignoreCase = true) ||
                    tag.equals("blockquote", ignoreCase = true) ||
                    tag.equals("dl", ignoreCase = true) ||
                    tag.equals("dd", ignoreCase = true) ||
                    tag.equals("dt", ignoreCase = true) ||
                    tag.equals("ol", ignoreCase = true) ||
                    tag.equals("ul", ignoreCase = true) ||
                    tag.equals("li", ignoreCase = true) ||
                    (
                        tag.length == 2 && tag.startsWith("h") &&
                            Character.isDigit(tag[1])
                        )
                ) {
                    // Block tag: ensure new line
                    if (sb.isNotEmpty() && sb[sb.length - 1] != '\n') {
                        sb.append('\n')
                    }
                    if (tag == "li" && !isEndTag) {
                        sb.append("* ")
                    }
                    if (tag.equals("pre", ignoreCase = true)) {
                        inPre = !isEndTag
                    }
                }
            } else if (c == '&') {
                val end = html.indexOf(';', i)
                if (end > i) {
                    val entity = html.substring(i, end + 1)
                    var s = XmlUtils.fromXmlAttributeValue(entity)
                    if (s.startsWith("&")) {
                        // Not an XML entity; for example, &nbsp;
                        // Sadly Guava's HtmlEscapes don't handle this either.
                        if (entity.equals("&nbsp;", ignoreCase = true)) {
                            s = " "
                        } else if (entity.startsWith("&#")) {
                            try {
                                val value = Integer.parseInt(entity.substring(2))
                                s = Character.toString(value.toChar())
                            } catch (ignore: NumberFormatException) {
                            }
                        }
                    }
                    sb.append(s)
                    i = end
                } else {
                    sb.append(c)
                }
            } else if (Character.isWhitespace(c)) {
                if (inPre) {
                    sb.append(c)
                } else if (sb.isEmpty() || !Character.isWhitespace(sb[sb.length - 1])) {
                    sb.append(' ')
                }
            } else {
                sb.append(c)
            }
            i++
        }

        var s = sb.toString()

        // Line-wrap
        s = SdkUtils.wrap(s, 60, null)

        return s
    }

    /** Converts to this output format from the given raw-format text  */
    private fun fromRaw(text: String): String {
        assert(this == HTML || this == HTML_WITH_UNICODE || this == TEXT) { this }
        val sb = StringBuilder(3 * text.length / 2)
        val html = this == HTML || this == HTML_WITH_UNICODE
        val escapeUnicode = this == HTML

        var prev: Char = 0.toChar()
        var flushIndex = 0
        val n = text.length
        var escaped = false
        var i = 0
        while (i < n) {
            val c = text[i]
            if (c == '\\' && !escaped) {
                escaped = true
                if (i > flushIndex) {
                    appendEscapedText(sb, text, html, flushIndex, i, escapeUnicode)
                }
                flushIndex = i + 1
                i++
                continue
            }

            if (!escaped && (c == '*' || c == '`') && i < n - 1) {
                // Preformatted text?
                if (i == 0 || i > 0 && text[i - 1] == '\n' && text.regionMatches(
                    i,
                    "```",
                    0,
                    3,
                    false
                )
                ) {
                    // Yes. Find end
                    val end = text.indexOf("\n```", i + 3)
                    if (end != -1) {
                        val nextLineStart = text.indexOf('\n', i) + 1
                        if (i > flushIndex) {
                            appendEscapedText(sb, text, html, flushIndex, i - 1, escapeUnicode)
                        }

                        // In the future, when we have syntax highlighting available outside
                        // of the CLI, run the highlighter here if the language is recognized
                        // (the language is text.substring(i + 3, nextLineStart - 1))
                        sb.append("\n")
                        if (html) {
                            sb.append("<pre>\n")
                        }
                        appendEscapedText(
                            sb, text, html, nextLineStart, end + 1,
                            escapeUnicode, newlinesAsBr = false
                        )
                        if (html) {
                            sb.append("</pre>\n")
                        }

                        // Skip past the final ``` (and possibly \n if end of line)
                        i = if (end + 4 < n && text[end + 4] == '\n') {
                            end + 5
                        } else {
                            end + 4
                        }
                        flushIndex = i
                        continue
                    }
                }

                // Scout ahead for range end
                if (!Character.isLetterOrDigit(prev) && !Character.isWhitespace(text[i + 1])) {
                    // Found * or ` immediately before a letter, and not in the middle of a word
                    // Find end
                    var end = text.indexOf(c, i + 1)
                    var bold = false
                    if (end == i + 1 && c == '*') {
                        var end2 = text.indexOf('*', end + 1)
                        if (end2 == end + 1) {
                            end2 = text.indexOf("***", end2 + 1)
                            if (end2 != -1) {
                                // *** means bold italics
                                if (i > flushIndex) {
                                    appendEscapedText(sb, text, html, flushIndex, i, escapeUnicode)
                                }
                                if (html) {
                                    sb.append("<b><i>")
                                    appendEscapedText(sb, text, true, i + 3, end2, escapeUnicode)
                                    sb.append("</i></b>")
                                } else {
                                    appendEscapedText(sb, text, false, i + 3, end2, escapeUnicode)
                                }
                                flushIndex = end2 + 3
                                i = flushIndex - 1 // -1: account for the i++ in the loop
                            }
                            i++
                            continue
                        } else if (end2 != -1 && end2 > end + 1 && end2 < n - 1 &&
                            text[end2 + 1] == '*'
                        ) {
                            end = end2
                            bold = true
                        }
                    }

                    if (end != -1 && (end == n - 1 || !Character.isLetter(text[end + 1]))) {
                        if (i > flushIndex) {
                            appendEscapedText(sb, text, html, flushIndex, i, escapeUnicode)
                        }
                        if (bold) {
                            i++
                        }
                        if (html) {
                            val tag = if (bold) "b" else if (c == '*') "i" else "code"
                            sb.append('<').append(tag).append('>')
                            appendEscapedText(sb, text, true, i + 1, end, escapeUnicode)
                            sb.append('<').append('/').append(tag).append('>')
                        } else {
                            appendEscapedText(sb, text, false, i + 1, end, escapeUnicode)
                        }
                        flushIndex = end + 1
                        if (bold) {
                            flushIndex++
                        }
                        i = flushIndex - 1 // -1: account for the i++ in the loop
                    }
                }
            } else if (html &&
                c == 'h' && i < n - 1 && text[i + 1] == 't' &&
                (text.startsWith(HTTP_PREFIX, i) || text.startsWith(HTTPS_PREFIX, i)) &&
                !Character.isLetterOrDigit(prev)
            ) {
                val length = if (text.startsWith(HTTP_PREFIX, i))
                    HTTP_PREFIX.length
                else HTTPS_PREFIX.length
                val end = findUrlEnd(text, i)
                if (end > i + length) {
                    if (i > flushIndex) {
                        appendEscapedText(sb, text, true, flushIndex, i, escapeUnicode)
                    }

                    val url = text.substring(i, end)
                    sb.append("<a href=\"")
                    sb.append(url)
                    sb.append('"').append('>')
                    sb.append(url)
                    sb.append("</a>")

                    flushIndex = end
                    i = flushIndex - 1 // -1: account for the i++ in the loop
                }
            } else if (c == '\n' && escaped) {
                flushIndex++
            }
            prev = c
            escaped = false
            i++
        }

        if (flushIndex < n) {
            appendEscapedText(sb, text, html, flushIndex, n, escapeUnicode)
        }

        return sb.toString()
    }

    companion object {
        const val HTTP_PREFIX = "http://"
        const val HTTPS_PREFIX = "https://"

        /**
         * Given an http URL starting at [start] in [text], find the position right after the
         * URL end
         */
        fun findUrlEnd(text: String, start: Int): Int {
            // Find url end
            val length = if (text.startsWith(HTTP_PREFIX, start))
                HTTP_PREFIX.length
            else HTTPS_PREFIX.length
            var end = start + length
            val n = text.length
            while (end < n) {
                val d = text[end]
                if (terminatesUrl(d)) {
                    break
                }
                end++
            }
            val last = text[end - 1]
            if (last == '.' || last == ')' || last == '!') {
                end--
            }
            return end
        }

        private fun terminatesUrl(c: Char): Boolean {
            return when (c) {
                in 'a'..'z' -> false
                in 'A'..'Z' -> false
                in '0'..'9' -> false
                '-', '_', '.', '*', '+', '%', '/', '#', ':', '@', '!', '$', '&', '\'' -> false
                else -> true
            }
        }

        private fun textToRaw(message: String): String {
            var mustEscape = false
            val n = message.length
            for (i in 0 until n) {
                val c = message[i]
                if (c == '\\' || c == '*' || c == '`') {
                    mustEscape = true
                    break
                }
            }

            if (!mustEscape) {
                return message
            }

            val sb = StringBuilder(message.length * 2)
            for (i in 0 until n) {
                val c = message[i]
                if (c == '\\' || c == '*' || c == '`') {
                    sb.append('\\')
                }
                sb.append(c)
            }

            return sb.toString()
        }

        private fun removeNumericEntities(html: String): String {
            if (!html.contains("&#")) {
                return html
            }

            val sb = StringBuilder(html.length)
            var i = 0
            val n = html.length
            while (i < n) {
                var c = html[i]
                if (c == '&' && i < n - 1 && html[i + 1] == '#') {
                    val end = html.indexOf(';', i + 2)
                    if (end != -1) {
                        val decimal = html.substring(i + 2, end)
                        try {
                            c = Integer.parseInt(decimal).toChar()
                            sb.append(c)
                            i = end
                            i++
                            continue
                        } catch (ignore: NumberFormatException) {
                            // fall through to not escape this
                        }
                    }
                }
                sb.append(c)
                i++
            }

            return sb.toString()
        }

        private fun appendEscapedText(
            sb: StringBuilder,
            text: String,
            html: Boolean,
            start: Int,
            end: Int,
            escapeUnicode: Boolean,
            newlinesAsBr: Boolean = true
        ) {
            if (html) {
                for (i in start until end) {
                    val c = text[i]
                    if (c == '<') {
                        sb.append("&lt;")
                    } else if (c == '&') {
                        sb.append("&amp;")
                    } else if (c == '\n') {
                        if (newlinesAsBr) {
                            sb.append("<br/>\n")
                        } else {
                            sb.append("\n")
                        }
                    } else {
                        if (c.toInt() > 255 && escapeUnicode) {
                            if (c == '\u200b') {
                                // Skip zero-width spaces; they're there to let you insert "word"
                                // separators when you want to use * characters for formatting,
                                // e.g. to get italics for "NN" in "values-vNN" you can't use
                                // "values-v*NN*" since "*" is in the middle of the word, but you
                                // can use "values-v\u200b*NN*"
                                continue
                            }
                            sb.append("&#")
                            sb.append(Integer.toString(c.toInt()))
                            sb.append(';')
                        } else if (c == '\u00a0') {
                            sb.append("&nbsp;")
                        } else {
                            sb.append(c)
                        }
                    }
                }
            } else {
                for (i in start until end) {
                    val c = text[i]
                    if (c == '\u200b') {
                        // See comment under HTML section
                        continue
                    }
                    sb.append(c)
                }
            }
        }
    }
}

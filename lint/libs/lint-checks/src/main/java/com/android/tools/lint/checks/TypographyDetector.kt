/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_TRANSLATABLE
import com.android.SdkConstants.TAG_PLURALS
import com.android.SdkConstants.TAG_STRING
import com.android.SdkConstants.TAG_STRING_ARRAY
import com.android.SdkConstants.VALUE_TRUE
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue.Companion.create
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.utils.SdkUtils
import com.android.utils.childrenIterator
import com.google.common.annotations.VisibleForTesting
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.Node.ELEMENT_NODE
import org.w3c.dom.Node.TEXT_NODE
import java.util.regex.Pattern

/** Checks for various typographical issues in string definitions. */
class TypographyDetector : ResourceXmlDetector() {
    private var checkDashes = false
    private var checkQuotes = false
    private var checkFractions = false
    private var checkEllipsis = false
    private var checkMisc = false

    override fun appliesTo(folderType: ResourceFolderType) = folderType == ResourceFolderType.VALUES

    override fun getApplicableElements() =
        listOf(TAG_STRING, TAG_STRING_ARRAY, TAG_PLURALS)

    override fun beforeCheckRootProject(context: Context) {
        checkDashes = context.isEnabled(DASHES)
        checkQuotes = context.isEnabled(QUOTES)
        checkFractions = context.isEnabled(FRACTIONS)
        checkEllipsis = context.isEnabled(ELLIPSIS)
        checkMisc = context.isEnabled(OTHER)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Don't make typography suggestions on strings that are either
        // service keys, or are non-translatable (these are typically also
        // service keys)
        if (SdkUtils.isServiceKey(element.getAttribute(ATTR_NAME))) {
            return
        }
        val translatable = element.getAttributeNode(ATTR_TRANSLATABLE)
        if (translatable != null && translatable.value != VALUE_TRUE) {
            return
        }
        for (child in element.childrenIterator()) {
            if (child.nodeType == TEXT_NODE) {
                val text = child.nodeValue
                checkText(context, element, child, text)
            } else if (child.nodeType == ELEMENT_NODE &&
                (
                    child.parentNode.nodeName == TAG_STRING_ARRAY ||
                        child.parentNode.nodeName == TAG_PLURALS
                    )
            ) {
                // String array or plural item children
                for (item in child.childrenIterator()) {
                    if (item.nodeType == TEXT_NODE) {
                        val text = item.nodeValue
                        checkText(context, child as Element, item, text)
                    }
                }
            }
        }
    }

    private fun checkText(context: XmlContext, element: Element, textNode: Node, text: String) {
        if (checkEllipsis) {
            // Replace ... with ellipsis character?
            val ellipsis = text.indexOf("...")
            if (ellipsis != -1 && !text.startsWith(".", ellipsis + 3)) {
                context.report(ELLIPSIS, element, context.getLocation(textNode), ELLIPSIS_MESSAGE)
            }
        }

        // Dashes
        if (checkDashes) {
            val hyphen = text.indexOf('-')
            if (hyphen != -1) {
                // n dash
                val matcher = HYPHEN_RANGE_PATTERN.matcher(text)
                if (matcher.matches()) {
                    // Make sure that if there is no space before digit there isn't
                    // one on the left either -- since we don't want to consider
                    // "1 2 -3" as a range from 2 to 3
                    val isNegativeNumber =
                        !Character.isWhitespace(matcher.group(2)[0]) &&
                            Character.isWhitespace(
                                matcher.group(1)[matcher.group(1).length - 1]
                            )
                    if (!isNegativeNumber && !isAnalyticsTrackingId(element)) {
                        context.report(
                            DASHES, element, context.getLocation(textNode), EN_DASH_MESSAGE
                        )
                    }
                }

                // m dash
                val emdash = text.indexOf("--")
                // Don't suggest replacing -- or "--" with an m dash since these are sometimes
                // used as digit marker strings
                if (emdash > 1 && !text.startsWith("-", emdash + 2)) {
                    context.report(DASHES, element, context.getLocation(textNode), EM_DASH_MESSAGE)
                }
            }
        }
        if (checkQuotes) {
            // Check for single quotes that can be replaced with directional quotes
            var quoteStart = text.indexOf('\'')
            if (quoteStart != -1) {
                val quoteEnd = text.indexOf('\'', quoteStart + 1)
                if (quoteEnd != -1 && quoteEnd > quoteStart + 1 && (quoteEnd < text.length - 1 || quoteStart > 0) &&
                    SINGLE_QUOTE.matcher(text).matches()
                ) {
                    context.report(
                        QUOTES, element, context.getLocation(textNode), SINGLE_QUOTE_MESSAGE
                    )
                    return
                }

                // Check for apostrophes that can be replaced by typographic apostrophes
                if (quoteEnd == -1 && quoteStart > 0 && Character.isLetterOrDigit(text[quoteStart - 1])) {
                    context.report(
                        QUOTES,
                        element,
                        context.getLocation(textNode),
                        TYPOGRAPHIC_APOSTROPHE_MESSAGE
                    )
                    return
                }
            }

            // Check for double quotes that can be replaced by directional double quotes
            quoteStart = text.indexOf('"')
            if (quoteStart != -1) {
                val quoteEnd = text.indexOf('"', quoteStart + 1)
                if (quoteEnd != -1 && quoteEnd > quoteStart + 1) {
                    if (quoteEnd < text.length - 1 || quoteStart > 0) {
                        context.report(
                            QUOTES, element, context.getLocation(textNode), DBL_QUOTES_MESSAGE
                        )
                        return
                    }
                }
            }

            // Check for grave accent quotations
            if (text.indexOf('`') != -1 && GRAVE_QUOTATION.matcher(text).matches()) {
                // Are we indenting ``like this'' or `this' ? If so, complain
                context.report(QUOTES, element, context.getLocation(textNode), GRAVE_QUOTE_MESSAGE)
                return
            }

            // Consider suggesting other types of directional quotes, such as guillemets, in
            // other languages?
            // There are a lot of exceptions and special cases to be considered so
            // this will need careful implementation and testing.
            // See https://en.wikipedia.org/wiki/Non-English_usage_of_quotation_marks
        }

        // Fraction symbols?
        if (checkFractions && text.indexOf('/') != -1) {
            val matcher = FRACTION_PATTERN.matcher(text)
            if (matcher.matches()) {
                val top = matcher.group(1) // Numerator
                val bottom = matcher.group(2) // Denominator
                when {
                    top == "1" && bottom == "2" ->
                        context.report(
                            FRACTIONS,
                            element,
                            context.getLocation(textNode), String.format(FRACTION_MESSAGE, '\u00BD', "&#189;", "1/2")
                        )
                    top == "1" && bottom == "4" ->
                        context.report(
                            FRACTIONS,
                            element,
                            context.getLocation(textNode), String.format(FRACTION_MESSAGE, '\u00BC', "&#188;", "1/4")
                        )
                    top == "3" && bottom == "4" ->
                        context.report(
                            FRACTIONS,
                            element,
                            context.getLocation(textNode), String.format(FRACTION_MESSAGE, '\u00BE', "&#190;", "3/4")
                        )
                    top == "1" && bottom == "3" ->
                        context.report(
                            FRACTIONS,
                            element,
                            context.getLocation(textNode), String.format(FRACTION_MESSAGE, '\u2153', "&#8531;", "1/3")
                        )
                    top == "2" && bottom == "3" ->
                        context.report(
                            FRACTIONS,
                            element,
                            context.getLocation(textNode), String.format(FRACTION_MESSAGE, '\u2154', "&#8532;", "2/3")
                        )
                }
            }
        }
        if (checkMisc) {
            // Fix copyright symbol?
            if (text.indexOf('(') != -1 && (text.contains("(c)") || text.contains("(C)"))) {
                // Suggest replacing with copyright symbol?
                context.report(OTHER, element, context.getLocation(textNode), COPYRIGHT_MESSAGE)
                // Replace (R) and TM as well? There are unicode characters for these but they
                // are probably not very common within Android app strings.
            }
        }
    }

    /**
     * An object describing a single edit to be made. The offset points to a location to start
     * editing; the length is the number of characters to delete, and the replaceWith string points
     * to a string to insert at the offset. Note that this can model not just replacement edits but
     * deletions (empty replaceWith) and insertions (replace length = 0) too.
     */
    class ReplaceEdit
    /**
     * Creates a new replace edit
     *
     * @param offset the offset of the edit
     * @param length the number of characters to delete at the offset
     * @param replaceWith the characters to insert at the offset
     */(
        /** The offset of the edit */
        @JvmField val offset: Int,
        /** The number of characters to delete at the offset */
        @JvmField val length: Int,
        /** The characters to insert at the offset */
        @JvmField val replaceWith: String
    )

    companion object {
        private val IMPLEMENTATION = Implementation(
            TypographyDetector::class.java, Scope.RESOURCE_FILE_SCOPE
        )

        /** Replace hyphens with dashes? */
        @JvmField
        val DASHES = create(
            id = "TypographyDashes",
            briefDescription = "Hyphen can be replaced with dash",
            explanation = """
                        The "n dash" (\u2013, &#8211;) and the "m dash" (\u2014, &#8212;) \
                        characters are used for ranges (n dash) and breaks (m dash). Using these \
                        instead of plain hyphens can make text easier to read and your application \
                        will look more polished.
                        """,
            category = Category.TYPOGRAPHY,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            moreInfo = "https://en.wikipedia.org/wiki/Dash"
        )

        /** Replace plain quotes with smart quotes? */
        @JvmField
        val QUOTES = create(
            id = "TypographyQuotes",
            briefDescription = "Straight quotes can be replaced with curvy quotes",
            explanation = """
                        Straight single quotes and double quotes, when used as a pair, can be replaced by \
                        "curvy quotes" (or directional quotes). This can make the text more readable. Note that you \
                        should never use grave accents and apostrophes to quote, `like this'. (Also note that you \
                        should not use curvy quotes for code fragments.)
                        """,
            category = Category.TYPOGRAPHY,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            moreInfo = "https://en.wikipedia.org/wiki/Quotation_mark",
            // This feature is apparently controversial: recent apps have started using
            // straight quotes to avoid inconsistencies. Disabled by default for now.
            enabledByDefault = false
        )

        /** Replace fraction strings with fraction characters? */
        @JvmField
        val FRACTIONS = create(
            id = "TypographyFractions",
            briefDescription = "Fraction string can be replaced with fraction character",
            explanation = """
                        You can replace certain strings, such as 1/2, and 1/4, with dedicated \
                        characters for these, such as ½ (&#189;) and ¼ (&#188;). \
                        This can help make the text more readable.
                        """,
            category = Category.TYPOGRAPHY,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            moreInfo = "https://en.wikipedia.org/wiki/Number_Forms"
        )

        /** Replace ... with the ellipsis character? */
        @JvmField
        val ELLIPSIS = create(
            id = "TypographyEllipsis",
            briefDescription = "Ellipsis string can be replaced with ellipsis character",
            explanation = """
                    You can replace the string "..." with a dedicated ellipsis character, \
                    ellipsis character (\u2026, &#8230;). This can help make the text more readable.
                    """,
            category = Category.TYPOGRAPHY,
            priority = 5,
            severity = Severity.WARNING,
            moreInfo = "https://en.wikipedia.org/wiki/Ellipsis",
            implementation = IMPLEMENTATION
        )

        /** The main issue discovered by this detector */
        @JvmField
        val OTHER = create(
            id = "TypographyOther",
            briefDescription = "Other typographical problems",
            explanation = """This check looks for miscellaneous typographical problems and offers replacement \
                    sequences that will make the text easier to read and your application more \
                    polished.
                    """,
            category = Category.TYPOGRAPHY,
            priority = 3,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        private const val GRAVE_QUOTE_MESSAGE =
            "Avoid quoting with grave accents; use apostrophes or better yet directional quotes instead"
        private const val ELLIPSIS_MESSAGE = "Replace \"...\" with ellipsis character (\u2026, &#8230;) ?"
        private const val EN_DASH_MESSAGE = "Replace \"-\" with an \"en dash\" character (\u2013, &#8211;) ?"
        private const val EM_DASH_MESSAGE = "Replace \"--\" with an \"em dash\" character (\u2014, &#8212;) ?"
        private const val TYPOGRAPHIC_APOSTROPHE_MESSAGE =
            "Replace apostrophe (') with typographic apostrophe (\u2019, &#8217;) ?"
        private const val SINGLE_QUOTE_MESSAGE =
            "Replace straight quotes ('') with directional quotes (\u2018\u2019, &#8216; and &#8217;) ?"
        private const val DBL_QUOTES_MESSAGE =
            "Replace straight quotes (\") with directional quotes (\u201C\u201D, &#8220; and &#8221;) ?"
        private const val COPYRIGHT_MESSAGE = "Replace (c) with copyright symbol \u00A9 (&#169;) ?"

        /**
         * Pattern used to detect scenarios which can be replaced with n dashes: a numeric range with a
         * hyphen in the middle (and possibly spaces)
         */
        @VisibleForTesting
        val HYPHEN_RANGE_PATTERN = Pattern.compile(".*(\\d+\\s*)-(\\s*\\d+).*")

        /**
         * Pattern used to detect scenarios where a grave accent mark is used to do ASCII quotations of
         * the form `this'' or ``this'', which is frowned upon. This pattern tries to avoid falsely
         * complaining about strings like "Type Option-` then 'Escape'."
         */
        @VisibleForTesting
        val GRAVE_QUOTATION = Pattern.compile("(^[^`]*`[^'`]+'[^']*$)|(^[^`]*``[^'`]+''[^']*$)")

        /**
         * Pattern used to detect common fractions, e.g. 1/2, 1/3, 2/3, 1/4, 3/4 and variations like 2 /
         * 3, but not 11/22 and so on.
         */
        @VisibleForTesting
        val FRACTION_PATTERN = Pattern.compile(".*\\b([13])\\s*/\\s*([234])\\b.*")

        /**
         * Pattern used to detect single quote strings, such as 'hello', but not just quoted strings
         * like 'Double quote: "', and not sentences where there are multiple apostrophes but not in a
         * quoting context such as "Mind Your P's and Q's".
         */
        @VisibleForTesting
        val SINGLE_QUOTE = Pattern.compile(".*\\W*'[^']+'(\\W.*)?", Pattern.UNICODE_CHARACTER_CLASS)

        private const val FRACTION_MESSAGE = "Use fraction character %1\$c (%2\$s) instead of %3\$s ?"
        private const val FRACTION_MESSAGE_PATTERN = "Use fraction character (.+) \\((.+)\\) instead of (.+) \\?"
        private fun isAnalyticsTrackingId(element: Element): Boolean {
            val name = element.getAttribute(ATTR_NAME)
            return "ga_trackingId" == name
        }

        /**
         * Returns a list of edits to be applied to fix the suggestion made by the given warning. The
         * specific issue id and message should be the message provided by this detector in an earlier
         * run.
         *
         *
         * This is intended to help tools implement automatic fixes of these warnings. The reason
         * only the message and issue id can be provided instead of actual state passed in the data
         * field to a reporter is that fix operation can be run much later than the lint is processed
         * (for example, in a subsequent run of the IDE when only the warnings have been persisted),
         *
         * @param issueId the issue id, which should be the id for one of the typography issues
         * @param message the actual error message, which should be a message provided by this detector
         * @param textNode a text node which corresponds to the text node the warning operated on
         * @return a list of edits, which is never null but could be empty. The offsets in the edit
         * objects are relative to the text node.
         */
        @JvmStatic
        fun getEdits(issueId: String?, message: String, textNode: Node): List<ReplaceEdit> {
            return getEdits(issueId, message, textNode.nodeValue)
        }

        /**
         * Returns a list of edits to be applied to fix the suggestion made by the given warning. The
         * specific issue id and message should be the message provided by this detector in an earlier
         * run.
         *
         *
         * This is intended to help tools implement automatic fixes of these warnings. The reason
         * only the message and issue id can be provided instead of actual state passed in the data
         * field to a reporter is that fix operation can be run much later than the lint is processed
         * (for example, in a subsequent run of the IDE when only the warnings have been persisted),
         *
         * @param issueId the issue id, which should be the id for one of the typography issues
         * @param message the actual error message, which should be a message provided by this detector
         * @param text the text of the XML node where the warning appeared
         * @return a list of edits, which is never null but could be empty. The offsets in the edit
         * objects are relative to the text node.
         */
        @JvmStatic
        fun getEdits(issueId: String?, message: String, text: String): List<ReplaceEdit> {
            val edits = ArrayList<ReplaceEdit>()
            if (message == ELLIPSIS_MESSAGE) {
                val offset = text.indexOf("...")
                if (offset != -1) {
                    edits.add(ReplaceEdit(offset, 3, "\u2026"))
                }
            } else if (message == EN_DASH_MESSAGE) {
                val offset = text.indexOf('-')
                if (offset != -1) {
                    edits.add(ReplaceEdit(offset, 1, "\u2013"))
                }
            } else if (message == EM_DASH_MESSAGE) {
                val offset = text.indexOf("--")
                if (offset != -1) {
                    edits.add(ReplaceEdit(offset, 2, "\u2014"))
                }
            } else if (message == TYPOGRAPHIC_APOSTROPHE_MESSAGE) {
                val offset = text.indexOf('\'')
                if (offset != -1) {
                    edits.add(ReplaceEdit(offset, 1, "\u2019"))
                }
            } else if (message == COPYRIGHT_MESSAGE) {
                var offset = text.indexOf("(c)")
                if (offset == -1) {
                    offset = text.indexOf("(C)")
                }
                if (offset != -1) {
                    edits.add(ReplaceEdit(offset, 3, "\u00A9"))
                }
            } else if (message == SINGLE_QUOTE_MESSAGE) {
                val offset = text.indexOf('\'')
                if (offset != -1) {
                    val endOffset = text.indexOf('\'', offset + 1)
                    if (endOffset != -1) {
                        edits.add(ReplaceEdit(offset, 1, "\u2018"))
                        edits.add(ReplaceEdit(endOffset, 1, "\u2019"))
                    }
                }
            } else if (message == DBL_QUOTES_MESSAGE) {
                val offset = text.indexOf('"')
                if (offset != -1) {
                    val endOffset = text.indexOf('"', offset + 1)
                    if (endOffset != -1) {
                        edits.add(ReplaceEdit(offset, 1, "\u201C"))
                        edits.add(ReplaceEdit(endOffset, 1, "\u201D"))
                    }
                }
            } else if (message == GRAVE_QUOTE_MESSAGE) {
                val offset = text.indexOf('`')
                if (offset != -1) {
                    val endOffset = text.indexOf('\'', offset + 1)
                    if (endOffset != -1) {
                        edits.add(ReplaceEdit(offset, 1, "\u2018"))
                        edits.add(ReplaceEdit(endOffset, 1, "\u2019"))
                    }
                }
            } else {
                val matcher = Pattern.compile(FRACTION_MESSAGE_PATTERN).matcher(message)
                if (matcher.find()) {
                    //  "Use fraction character %1$c (%2$s) instead of %3$s ?";
                    val replace = matcher.group(3)
                    val offset = text.indexOf(replace)
                    if (offset != -1) {
                        val replaceWith = matcher.group(2)
                        edits.add(ReplaceEdit(offset, replace.length, replaceWith))
                    }
                }
            }
            return edits
        }
    }
}

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
import kotlin.text.CharDirectionality.RIGHT_TO_LEFT
import kotlin.text.CharDirectionality.RIGHT_TO_LEFT_ARABIC
import kotlin.text.CharDirectionality.RIGHT_TO_LEFT_EMBEDDING
import kotlin.text.CharDirectionality.RIGHT_TO_LEFT_OVERRIDE

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
                context.report(
                    ELLIPSIS, element, context.getLocation(textNode), ELLIPSIS_MESSAGE,
                    fix().replace().text("...").with("…").build()
                )
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
                            DASHES, element, context.getLocation(textNode), EN_DASH_MESSAGE,
                            if (isRtl(text)) null else fix().replace().text("-").with("–").build()
                        )
                    }
                }

                // m dash
                val emDash = text.indexOf("--")
                // Don't suggest replacing -- or "--" with an m dash since these are sometimes
                // used as digit marker strings
                if (emDash > 1 && !text.startsWith("-", emDash + 2)) {
                    context.report(
                        DASHES, element, context.getLocation(textNode), EM_DASH_MESSAGE,
                        if (isRtl(text)) null else fix().replace().text("--").with("—").build()
                    )
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
                        QUOTES, element, context.getLocation(textNode), SINGLE_QUOTE_MESSAGE,
                        fix().replace().text(text.substring(quoteStart, quoteEnd + 1))
                            .with("‘${text.substring(quoteStart + 1, quoteEnd)}’").build()
                    )
                    return
                }

                // Check for apostrophes that can be replaced by typographic apostrophes
                if (quoteEnd == -1 && quoteStart > 0 && Character.isLetterOrDigit(text[quoteStart - 1])) {
                    context.report(
                        QUOTES,
                        element,
                        context.getLocation(textNode),
                        TYPOGRAPHIC_APOSTROPHE_MESSAGE,
                        fix().replace().text("'").with("’").build()
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
                            QUOTES, element, context.getLocation(textNode), DBL_QUOTES_MESSAGE,
                            fix().replace().text(text.substring(quoteStart, quoteEnd + 1))
                                .with("“${text.substring(quoteStart + 1, quoteEnd)}”").build()
                        )
                        return
                    }
                }
            }

            val graveStart = text.indexOf('`')
            // Check for grave accent quotations
            if (graveStart != -1 && GRAVE_QUOTATION.matcher(text).matches()) {
                val quoteEnd = text.indexOf("'")
                // Are we indenting ``like this'' or `this' ? If so, complain
                val quickfix = if (text[graveStart + 1] == '`') { // Double quotes
                    fix().replace().text(text.substring(graveStart, quoteEnd + 2))
                        .with("“${text.substring(graveStart + 2, quoteEnd)}”").build()
                } else { // Single quotes
                    fix().replace().text(text.substring(graveStart, quoteEnd + 1))
                        .with("‘${text.substring(graveStart + 1, quoteEnd)}’").build()
                }
                context.report(QUOTES, element, context.getLocation(textNode), GRAVE_QUOTE_MESSAGE, quickfix)
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
                            context.getLocation(textNode), String.format(FRACTION_MESSAGE, '\u00BD', "&#189;", "1/2"),
                            fix().replace().text("1/2").with("½").build()
                        )
                    top == "1" && bottom == "4" ->
                        context.report(
                            FRACTIONS,
                            element,
                            context.getLocation(textNode), String.format(FRACTION_MESSAGE, '\u00BC', "&#188;", "1/4"),
                            fix().replace().text("1/4").with("¼").build()
                        )
                    top == "3" && bottom == "4" ->
                        context.report(
                            FRACTIONS,
                            element,
                            context.getLocation(textNode), String.format(FRACTION_MESSAGE, '\u00BE', "&#190;", "3/4"),
                            fix().replace().text("3/4").with("¾").build()
                        )
                    top == "1" && bottom == "3" ->
                        context.report(
                            FRACTIONS,
                            element,
                            context.getLocation(textNode), String.format(FRACTION_MESSAGE, '\u2153', "&#8531;", "1/3"),
                            fix().replace().text("1/3").with("\u2153").build()
                        )
                    top == "2" && bottom == "3" ->
                        context.report(
                            FRACTIONS,
                            element,
                            context.getLocation(textNode), String.format(FRACTION_MESSAGE, '\u2154', "&#8532;", "2/3"),
                            fix().replace().text("2/3").with("\u2154").build()
                        )
                }
            }
        }
        if (checkMisc) {
            // Fix copyright symbol?
            if (text.indexOf('(') != -1 && (text.contains("(c)") || text.contains("(C)"))) {
                // Suggest replacing with copyright symbol?
                context.report(
                    OTHER, element, context.getLocation(textNode), COPYRIGHT_MESSAGE,
                    fix().replace().text(if (text.contains("(c)")) "(c)" else "(C)").with("©").build()
                )
                // Replace (R) and TM as well? There are unicode characters for these but they
                // are probably not very common within Android app strings.
            }
        }
    }

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
         * Pattern used to detect scenarios which can be replaced with
         * n dashes: a numeric range with a hyphen in the middle (and
         * possibly spaces)
         */
        @VisibleForTesting
        val HYPHEN_RANGE_PATTERN = Pattern.compile(".*(\\d+\\s*)-(\\s*\\d+).*")

        /**
         * Pattern used to detect scenarios where a grave accent mark is
         * used to do ASCII quotations of the form `this'' or ``this'',
         * which is frowned upon. This pattern tries to avoid falsely
         * complaining about strings like "Type Option-` then 'Escape'."
         */
        @VisibleForTesting
        val GRAVE_QUOTATION = Pattern.compile("(^[^`]*`[^'`]+'[^']*$)|(^[^`]*``[^'`]+''[^']*$)")

        /**
         * Pattern used to detect common fractions, e.g. 1/2, 1/3, 2/3,
         * 1/4, 3/4 and variations like 2 / 3, but not 11/22 and so on.
         */
        @VisibleForTesting
        val FRACTION_PATTERN = Pattern.compile(".*\\b([13])\\s*/\\s*([234])\\b.*")

        /**
         * Pattern used to detect single quote strings, such as 'hello',
         * but not just quoted strings like 'Double quote: "', and
         * not sentences where there are multiple apostrophes but
         * not in a quoting context such as "Mind Your P's and Q's".
         */
        @VisibleForTesting
        val SINGLE_QUOTE = Pattern.compile(".*\\W*'[^']+'(\\W.*)?", Pattern.UNICODE_CHARACTER_CLASS)

        private const val FRACTION_MESSAGE = "Use fraction character %1\$c (%2\$s) instead of %3\$s ?"
        private const val FRACTION_MESSAGE_PATTERN = "Use fraction character (.+) \\((.+)\\) instead of (.+) \\?"
        private fun isAnalyticsTrackingId(element: Element): Boolean {
            val name = element.getAttribute(ATTR_NAME)
            return "ga_trackingId" == name
        }

        private fun isRtl(string: String) = string.any { char ->
            val directionality = char.directionality
            directionality == RIGHT_TO_LEFT || directionality == RIGHT_TO_LEFT_ARABIC ||
                directionality == RIGHT_TO_LEFT_EMBEDDING || directionality == RIGHT_TO_LEFT_OVERRIDE
        }
    }
}

/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.SdkConstants.TAG_STRING
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.getLocale
import com.android.tools.lint.detector.api.Location.Handle
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.utils.Pair
import java.util.ArrayList
import java.util.HashMap
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.Locale

/** Constructs a new [StringCasingDetector] check  */
class StringCasingDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION_XML =
            Implementation(StringCasingDetector::class.java, Scope.ALL_RESOURCES_SCOPE)

        /** Whether there are any duplicate strings, including capitalization adjustments.  */
        @JvmField
        val DUPLICATE_STRINGS = Issue.create(
            id = "DuplicateStrings",
            briefDescription = "Duplicate Strings",
            explanation = """
                Duplicate strings can make applications larger unnecessarily.

                This lint check looks for duplicate strings, including differences for strings \
                where the only difference is in capitalization. Title casing and all uppercase can \
                all be adjusted in the layout or in code.
                """,
            implementation = IMPLEMENTATION_XML,
            moreInfo = "https://developer.android.com/reference/android/widget/TextView.html#attr_android:inputType",
            category = Category.APP_SIZE,
            priority = 2,
            severity = Severity.WARNING,
            enabledByDefault = false
        )
    }

    /*
     * Map of all locale,strings in lower case, to their raw elements to ensure that there are no
     * duplicate strings.
     */
    private val allStrings = HashMap<Pair<String, String>, MutableList<Pair<String, Handle>>>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(TAG_STRING)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        if (childNodes.length > 0) {
            if (childNodes.length == 1) {
                val child = childNodes.item(0)
                if (child.nodeType == Node.TEXT_NODE) {
                    checkTextNode(
                        context,
                        element,
                        StringFormatDetector.stripQuotes(child.nodeValue)
                    )
                }
            } else {
                val sb = StringBuilder()
                StringFormatDetector.addText(sb, element)
                if (sb.isNotEmpty()) {
                    checkTextNode(context, element, sb.toString())
                }
            }
        }
    }

    private fun checkTextNode(context: XmlContext, element: Element, text: String) {
        val locale = getLocale(context)
        val key = if (locale != null)
            Pair.of(locale.full, text.toLowerCase(Locale.forLanguageTag(locale.tag)))
        else Pair.of("default", text.toLowerCase(Locale.US))
        val handle = context.createLocationHandle(element)
        handle.clientData = element
        val handleList = allStrings.getOrDefault(key, ArrayList())
        handleList.add(Pair.of(text, handle))
        allStrings[key] = handleList
    }

    override fun afterCheckRootProject(context: Context) {
        for ((key, duplicates) in allStrings) {
            if (duplicates.size > 1) {
                // we have detected a duplicated string in the string resources, notify the developer.
                for (dup in duplicates) {
                    val message =
                        """
                        Duplicate string detected `${dup.first}`; Please use android:inputType or \
                        android:capitalize to avoid string duplication in resources.
                        """.trimIndent()
                    context.report(DUPLICATE_STRINGS, dup.second.resolve(), message)
                }
            }
        }
    }
}

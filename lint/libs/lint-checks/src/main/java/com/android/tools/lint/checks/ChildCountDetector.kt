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

import com.android.SdkConstants.AAPT_URI
import com.android.SdkConstants.GRID_VIEW
import com.android.SdkConstants.HORIZONTAL_SCROLL_VIEW
import com.android.SdkConstants.LIST_VIEW
import com.android.SdkConstants.REQUEST_FOCUS
import com.android.SdkConstants.SCROLL_VIEW
import com.android.SdkConstants.TAG_ATTR
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.utils.iterator
import org.w3c.dom.Element
import java.util.Arrays

/**
 * Check which makes sure that views have the expected number of declared
 * children (e.g. at most one in ScrollViews and none in AdapterViews)
 */
/** Constructs a new [ChildCountDetector]  */
class ChildCountDetector : LayoutDetector() {
    companion object Issues {
        private val IMPLEMENTATION = Implementation(
            ChildCountDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE
        )

        /** The main issue discovered by this detector  */
        @JvmField
        val SCROLLVIEW_ISSUE = Issue.create(
            id = "ScrollViewCount",
            briefDescription = "ScrollViews can have only one child",
            explanation = """
            ScrollViews can only have one child widget. If you want more children, wrap them \
            in a container layout.""",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        /** The main issue discovered by this detector  */
        @JvmField
        val ADAPTER_VIEW_ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterViews cannot have children in XML",
            explanation = """
            AdapterViews such as ListViews must be configured with data from Java code, such as a \
            ListAdapter.""",
            moreInfo = "http://developer.android.com/reference/android/widget/AdapterView.html",
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }

    override fun getApplicableElements(): Collection<String>? = Arrays.asList(
        SCROLL_VIEW,
        HORIZONTAL_SCROLL_VIEW,
        LIST_VIEW,
        GRID_VIEW
        // TODO: Shouldn't Spinner be in this list too? (Was not there in layoutopt)
    )

    override fun visitElement(context: XmlContext, element: Element) {
        var childCount = 0
        for (child in element) {
            val tagName = child.localName
            if (REQUEST_FOCUS == tagName) {
                continue
            } else if (tagName == TAG_ATTR && child.namespaceURI == AAPT_URI) {
                continue
            } else {
                childCount++
            }
        }
        val tagName = element.tagName
        if (tagName == SCROLL_VIEW || tagName == HORIZONTAL_SCROLL_VIEW) {
            if (childCount > 1) {
                context.report(
                    SCROLLVIEW_ISSUE, element,
                    context.getNameLocation(element), "A scroll view can have only one child"
                )
            }
        } else {
            // Adapter view
            if (childCount > 0) {
                context.report(
                    ADAPTER_VIEW_ISSUE, element,
                    context.getNameLocation(element),
                    "A list/grid should have no children declared in XML"
                )
            }
        }
    }
}

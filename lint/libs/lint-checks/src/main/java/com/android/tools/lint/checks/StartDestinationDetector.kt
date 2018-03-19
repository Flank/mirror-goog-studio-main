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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_START_DESTINATION
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TAG_NAVIGATION
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

/**
 * Check to make sure the startDestination attribute on navigation elements is set and valid.
 */
class StartDestinationDetector : ResourceXmlDetector() {
    companion object Issues {

        @JvmField
        val ISSUE = Issue.create(
            "InvalidNavigation",
            "No start destination specified",

            """
All `<navigation>` elements must have a start destination specified, and it must be a direct child of
that `<navigation>`
""",
            Category.CORRECTNESS,
            3,
            Severity.WARNING,
            Implementation(
                StartDestinationDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.NAVIGATION

    override fun getApplicableElements() = listOf(TAG_NAVIGATION)

    override fun visitElement(context: XmlContext, navigation: Element) {
        val destinationAttr = navigation.getAttributeNodeNS(AUTO_URI, ATTR_START_DESTINATION)
        val destinationAttrValue = destinationAttr?.value
        // smart cast to non-null doesn't seem to work with isNullOrBlank?
        if (destinationAttrValue == null || destinationAttrValue.isBlank()) {
            context.report(
                ISSUE,
                navigation,
                context.getNameLocation(navigation),
                "No start destination specified"
            )
        } else {
            // TODO(namespaces): Support namespaces in ids
            val url = ResourceUrl.parse(destinationAttrValue)
            if (url == null || url.type != ResourceType.ID) {
                context.report(
                    ISSUE,
                    navigation,
                    context.getNameLocation(navigation),
                    "startDestination must be an id"
                )
                return
            }
            val children = navigation.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i) as? Element ?: continue
                val childId = child.getAttributeNS(ANDROID_URI, ATTR_ID)
                val childUrl = ResourceUrl.parse(childId) ?: continue
                if (url.name == childUrl.name) {
                    return
                }
            }
            context.report(
                ISSUE,
                navigation,
                context.getValueLocation(destinationAttr),
                "Invalid start destination $destinationAttrValue"
            )
        }
    }
}

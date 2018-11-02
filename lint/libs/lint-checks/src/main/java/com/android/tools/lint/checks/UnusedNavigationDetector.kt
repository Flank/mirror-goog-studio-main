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

import com.android.SdkConstants
import com.android.SdkConstants.NAVIGATION_PREFIX
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.getBaseName
import org.w3c.dom.Document
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

class UnusedNavigationDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION_XML =
            Implementation(UnusedNavigationDetector::class.java, Scope.RESOURCE_FILE_SCOPE)

        @JvmField
        val ISSUE = Issue.create(
            id = "UnusedNavigation",
            briefDescription = "Unused Navigation",
            explanation = """
                Navigation resource files must be referenced from a `NavHostFragment` \
                in a layout in order to be relevant.
                """,
            implementation = IMPLEMENTATION_XML,
            moreInfo = "https://developer.android.com/topic/libraries/architecture/navigation/navigation-implementing#Modify-activity",
            category = Category.CORRECTNESS,
            priority = 2,
            severity = Severity.ERROR
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.NAVIGATION
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        // Only checks in the base project
        val client = context.client

        val resources = client.getResourceRepository(context.mainProject, true, false)
            ?: return
        val items = resources.getResources(ResourceNamespace.TODO(), ResourceType.LAYOUT)
        if (items.size() == 0 || items.size() > 25) {
            // For large projects, don't bother; this could be done on the fly in the editor
            // so we don't want to (a) take too long and (b) for larger projects it's
            // not super likely that they're confused about NavHostFragments to begin with;
            // this lint check is mainly intended as a helper when you're getting started
            // with navigation and you're confused about why it's not working.
            return
        }

        val target = NAVIGATION_PREFIX + getBaseName(context.file.name)

        for (item in items.values()) {
            val file = item.source ?: continue
            try {
                val parser = client.createXmlPullParser(file)
                if (parser != null) {
                    if (referencesThis(parser, target)) {
                        return
                    }
                }
            } catch (ignore: XmlPullParserException) {
                // Users might be editing these files in the IDE; don't flag
            } catch (ignore: IOException) {
                // Users might be editing these files in the IDE; don't flag
            }
        }

        context.report(
            ISSUE, document.documentElement, context.getElementLocation(document.documentElement),
            "This navigation graph is not referenced from any layout files (expected to find it in at least one layout " +
                    "file with a `NavHostFragment` with `app:navGraph=\"$target\"` attribute)."
        )
    }

    private fun referencesThis(parser: XmlPullParser, target: String): Boolean {
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.START_TAG) {
                val tag = parser.name ?: continue
                if (tag == SdkConstants.VIEW_FRAGMENT) {
                    val cls: String? = parser.getAttributeValue(
                        SdkConstants.ANDROID_URI,
                        SdkConstants.ATTR_NAME
                    )
                    if (cls == SdkConstants.FQCN_NAV_HOST_FRAGMENT) {
                        val navGraph: String? = parser.getAttributeValue(
                            ResourceNamespace.TODO().xmlNamespaceUri,
                            SdkConstants.ATTR_NAV_GRAPH
                        ) ?: continue
                        if (navGraph == target) {
                            return true
                        }
                    }
                }
            } else if (event == XmlPullParser.END_DOCUMENT) {
                return false
            }
        }
    }
}
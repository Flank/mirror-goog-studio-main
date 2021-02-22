/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.SdkConstants.ANDROID_PKG
import com.android.SdkConstants.REQUEST_FOCUS
import com.android.SdkConstants.VIEW_INCLUDE
import com.android.SdkConstants.VIEW_MERGE
import com.android.ide.common.rendering.api.ResourceNamespace.TODO
import com.android.resources.ResourceType.LAYOUT
import com.android.tools.lint.client.api.ResourceReference
import com.android.tools.lint.client.api.ResourceRepositoryScope.LOCAL_DEPENDENCIES
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

/** Checks related to RemoteViews. */
class RemoteViewDetector : Detector(), SourceCodeScanner {
    companion object Issues {
        private val IMPLEMENTATION = Implementation(
            RemoteViewDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        /** Unsupported views in a remote view. */
        @JvmField
        val ISSUE = Issue.create(
            id = "RemoteViewLayout",
            briefDescription = "Unsupported View in RemoteView",
            explanation = """
            In a `RemoteView`, only some layouts and views are allowed.
            """,
            moreInfo = "https://developer.android.com/reference/android/widget/RemoteViews",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf("android.widget.RemoteViews")
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        val arguments = node.valueArguments
        if (arguments.size != 2) return
        val argument = arguments[1]
        val resource = ResourceReference.get(argument) ?: return
        if (resource.`package` == ANDROID_PKG || resource.type != LAYOUT) {
            return
        }

        val client = context.client
        val full = context.isGlobalAnalysis()
        val project = if (full) context.mainProject else context.project
        val resources = context.client.getResources(project, LOCAL_DEPENDENCIES)

        // See if the associated resource references propertyValuesHolder, and if so
        // suggest switching to AnimatorInflaterCompat.loadAnimator.
        val items =
            resources.getResources(TODO(), resource.type, resource.name)
        var tags: MutableSet<String>? = null
        val paths = items.asSequence().mapNotNull { it.source }.toSet()
        for (path in paths) {
            try {
                val parser = client.createXmlPullParser(path) ?: continue
                while (true) {
                    val event = parser.next()
                    if (event == XmlPullParser.START_TAG) {
                        val tag = parser.name ?: continue
                        if (!isSupportedTag(tag)) {
                            (tags ?: HashSet<String>().also { tags = it }).add(tag)
                        }
                    } else if (event == XmlPullParser.END_DOCUMENT) {
                        break
                    }
                }
            } catch (ignore: XmlPullParserException) {
                // Users might be editing these files in the IDE; don't flag
            } catch (ignore: IOException) {
                // Users might be editing these files in the IDE; don't flag
            }
        }
        tags?.let { set ->
            val sorted = set.toSortedSet()
            context.report(
                ISSUE, node, context.getLocation(node),
                "`@layout/${resource.name}` includes views not allowed in a `RemoteView`: ${sorted.joinToString()}"
            )
        }
    }

    private fun isSupportedTag(tag: String): Boolean {
        return when (tag) {
            "AdapterViewFlipper",
            "FrameLayout",
            "GridLayout",
            "GridView",
            "LinearLayout",
            "ListView",
            "RelativeLayout",
            "StackView",
            "ViewFlipper",
            "AnalogClock",
            "Button",
            "Chronometer",
            "ImageButton",
            "ImageView",
            "ProgressBar",
            "TextClock",
            "TextView" -> true

            // These are not listed in the docs for RemoteView, but are annotated with
            // @RemoteView in the source code:
            "AbsoluteLayout",
            "ViewStub" -> true // b/2541651, fixed in 2012

            // meta tags handled by inflater
            VIEW_MERGE,
            REQUEST_FOCUS,
            VIEW_INCLUDE -> true

            else -> false
        }
    }
}

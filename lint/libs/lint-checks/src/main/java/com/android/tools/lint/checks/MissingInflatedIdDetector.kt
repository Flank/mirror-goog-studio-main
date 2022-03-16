/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.SdkConstants.VIEW_INCLUDE
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.util.PathString
import com.android.resources.ResourceUrl
import com.android.tools.lint.checks.ViewTypeDetector.Companion.FIND_VIEW_BY_ID
import com.android.tools.lint.checks.ViewTypeDetector.Companion.REQUIRE_VIEW_BY_ID
import com.android.tools.lint.client.api.ResourceRepositoryScope.LOCAL_DEPENDENCIES
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.ResourceEvaluator
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.findSelector
import com.android.tools.lint.detector.api.stripIdPrefix
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.tryResolve
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.util.EnumSet

/**
 * Detector for finding layout inflation paired with a find/require view
 * by id looking for an id not in that layout.
 *
 * TODO: Instead of just making sure that the id is found in at least
 *     *one* of the overridden layouts, make sure that it's
 *     present in *all* the layouts (and if not, list which
 *     ones it's missing from). If the view is looked up via
 *     `requireViewById`, this is an unconditional error.
 *     Otherwise, see whether we're null checking the result.
 */
class MissingInflatedIdDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf(FIND_VIEW_BY_ID, REQUIRE_VIEW_BY_ID)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val layoutUrl = findLayout(context, node) ?: return
        val idUrl = getFirstArgAsResource(node, context) ?: return

        val full = context.isGlobalAnalysis()
        val project = if (full) context.mainProject else context.project
        val resources = context.client.getResources(project, LOCAL_DEPENDENCIES)
        val items = resources.getResources(ResourceNamespace.TODO(), layoutUrl.type, layoutUrl.name)
        if (items.isEmpty()) return
        val id = idUrl.name
        if (items.none { definesId(context, it.source, id) }) {
            val message = "`$layoutUrl` does not contain a declaration with id `$id`"
            val idArgument = node.valueArguments.first()
            context.report(ISSUE, idArgument, context.getLocation(idArgument), message)
        }
    }

    /**
     * From a `findByViewId` call, try to locate the layout resource
     * it is inflating from. E.g. in an activity, if we simply call
     * `findViewById(id)`, it's probably a preeeding `setContentView`
     * call; if it's something like `root.findViewById`, see if we can
     * find inflation of the root view.
     */
    private fun findLayout(context: JavaContext, call: UCallExpression): ResourceUrl? {
        val receiver = call.receiver?.skipParenthesizedExprDown()
        if (receiver != null) {
            val variable = receiver.tryResolve()?.toUElement() as? ULocalVariable ?: return null
            val inflation = variable.uastInitializer?.findSelector() as? UCallExpression ?: return null
            if (inflation.methodName != "inflate") return null
            return getFirstArgAsResource(inflation, context)
        } else {
            // See if there's some local reference to setting the content view here.
            val block = call.getParentOfType<UBlockExpression>(true) ?: return null
            for (expression in block.expressions) {
                val setContentView = expression.skipParenthesizedExprDown() as? UCallExpression ?: continue
                if (setContentView.methodIdentifier?.name != "setContentView") continue
                return getFirstArgAsResource(setContentView, context)
            }
            return null
        }
    }

    /**
     * For a call like `inflate(R.layout.foo, null)` or
     * `setContentView(R.layout.foo)`, returns `@layout/foo`.
     * Deliberately ignores resources like `android.R.id.some_id` since
     * we don't want to initialize the resource repository for all the
     * framework resources.
     */
    private fun getFirstArgAsResource(
        setContentView: UCallExpression,
        context: JavaContext
    ): ResourceUrl? {
        val resourceArgument = setContentView.valueArguments.firstOrNull()?.skipParenthesizedExprDown() ?: return null
        val url = ResourceEvaluator.getResource(context.evaluator, resourceArgument) ?: return null
        return if (!url.isFramework) url else null
    }

    /**
     * Returns true if the given layout [file] contains a definition of
     * the given [targetId], **and** does not contain an `<include>`
     * tag.
     */
    private fun definesId(context: JavaContext, file: PathString?, targetId: String): Boolean {
        file ?: return false
        val parser = try {
            context.client.createXmlPullParser(file) ?: return false
        } catch (ignore: IOException) {
            return true
        }
        try {
            while (true) {
                val event = parser.next()
                if (event == XmlPullParser.START_TAG) {
                    if (parser.name == VIEW_INCLUDE) {
                        // this layout contains an <include> tag; in that case we're not certain
                        // so just assume the id exists
                        return true
                    }
                    val id: String? = parser.getAttributeValue(ANDROID_URI, ATTR_ID)
                    @Suppress("DEPRECATION")
                    if (id != null && id.endsWith(targetId) && stripIdPrefix(id) == targetId) {
                        return true
                    }
                } else if (event == XmlPullParser.END_DOCUMENT) {
                    return false
                }
            }
        } catch (ignore: XmlPullParserException) {
            // Users might be editing these files in the IDE; don't flag
            return true
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MissingInflatedId",
            briefDescription = "ID not found in inflated resource",
            explanation = """
                Checks calls to layout inflation and makes sure that the referenced ids \
                are found in the corresponding layout (or at least one of them, if the \
                layout has multiple configurations.)
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            androidSpecific = true,
            implementation = Implementation(
                MissingInflatedIdDetector::class.java,
                EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.ALL_JAVA_FILES),
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}

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

package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.TYPE_STRING
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.resolveManifestName
import com.android.utils.XmlUtils
import com.intellij.psi.PsiClass
import org.jetbrains.uast.UClass
import org.w3c.dom.Element
import java.util.EnumSet
import java.util.HashMap

/** Ensures that PreferenceActivity and its subclasses are never exported.  */
class PreferenceActivityDetector : Detector(), XmlScanner, SourceCodeScanner {

    private val exportedActivities = HashMap<String, Location.Handle>()

    // ---- Implements XmlScanner ----

    override fun getApplicableElements(): Collection<String>? {
        return listOf(TAG_ACTIVITY)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (SecurityDetector.getExported(element)) {
            val fqcn = resolveManifestName(element)
            if (fqcn == PREFERENCE_ACTIVITY && !context.driver.isSuppressed(
                    context,
                    ISSUE,
                    element
                )
            ) {
                val message = "`PreferenceActivity` should not be exported"
                context.report(ISSUE, element, context.getLocation(element), message)
            }
            exportedActivities[fqcn] = context.createLocationHandle(element)
        }
    }

    // ---- implements SourceCodeScanner ----

    override fun applicableSuperClasses(): List<String>? {
        return listOf(PREFERENCE_ACTIVITY)
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        if (!context.project.reportIssues) {
            return
        }
        val evaluator = context.evaluator
        val className = declaration.qualifiedName
        if (evaluator.extendsClass(declaration, PREFERENCE_ACTIVITY, false) &&
            isExported(
                context,
                className
            )
        ) {
            // Ignore the issue if we target an API greater than 19 and the class in
            // question specifically overrides isValidFragment() and thus knowingly white-lists
            // valid fragments.
            if (context.mainProject.targetSdk >= 19 && overridesIsValidFragment(
                    evaluator,
                    declaration
                )
            ) {
                return
            }

            var message = "`PreferenceActivity` subclass $className should not be exported"
            val location: Location
            if (context.scope.contains(Scope.MANIFEST)) {
                location = exportedActivities[className]?.resolve() ?: return
            } else {
                // When linting incrementally just in the Java class, place the error on
                // the class itself rather than the export line in the manifest
                location = context.getNameLocation(declaration)
                message += " in the manifest"
            }
            context.report(ISSUE, declaration, location, message)
        }
    }

    private fun isExported(context: JavaContext, className: String?): Boolean {
        if (className == null) {
            return false
        }

        // If analyzing manifest files directly, we've already recorded the available
        // activities
        if (context.scope.contains(Scope.MANIFEST)) {
            return exportedActivities.containsKey(className)
        }
        val mainProject = context.mainProject

        val mergedManifest = mainProject.mergedManifest
        if (mergedManifest == null || mergedManifest.documentElement == null) {
            return false
        }
        val application =
            XmlUtils.getFirstSubTagByName(mergedManifest.documentElement, TAG_APPLICATION)
        if (application != null) {
            for (element in XmlUtils.getSubTags(application)) {
                if (TAG_ACTIVITY == element.tagName) {
                    val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    if (className.endsWith(name)) {
                        val fqn = resolveManifestName(element)
                        if (fqn == className) {
                            return SecurityDetector.getExported(element)
                        }
                    }
                }
            }
        }

        return false
    }

    private fun overridesIsValidFragment(
        evaluator: JavaEvaluator,
        resolvedClass: PsiClass
    ): Boolean {
        for (method in resolvedClass.findMethodsByName(IS_VALID_FRAGMENT, false)) {
            if (evaluator.parametersMatch(method, TYPE_STRING)) {
                return true
            }
        }
        return false
    }

    companion object {
        private val IMPLEMENTATION = Implementation(
            PreferenceActivityDetector::class.java,
            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
            Scope.MANIFEST_SCOPE,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ExportedPreferenceActivity",
            briefDescription = "PreferenceActivity should not be exported",
            explanation = """
                Fragment injection gives anyone who can send your PreferenceActivity an intent \
                the ability to load any fragment, with any arguments, in your process.""",
            moreInfo = "http://securityintelligence.com/new-vulnerability-android-framework-fragment-injection",
            category = Category.SECURITY,
            priority = 8,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        private const val PREFERENCE_ACTIVITY = "android.preference.PreferenceActivity"
        private const val IS_VALID_FRAGMENT = "isValidFragment"
    }
}

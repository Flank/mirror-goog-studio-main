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
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ClassContext
import com.android.tools.lint.detector.api.ClassScanner
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.EnumSet

/**
 * Makes sure that when targeting T or above an application which posts
 * notifications also declares the POST_NOTIFICATIONS permission.
 */
class NotificationPermissionDetector : Detector(), SourceCodeScanner, ClassScanner {
    companion object Issues {
        private val IMPLEMENTATION = Implementation(
            NotificationPermissionDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.JAVA_LIBRARIES),
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "NotificationPermission",
            briefDescription = "Notifications Without Permission",
            explanation = """
                When targeting Android 13 and higher, posting permissions requires holding the runtime permission \
                `android.permission.POST_NOTIFICATIONS`.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        /**
         * Boolean property: whether we've found at least one
         * notification call in the source code.
         */
        private const val KEY_SOURCE = "source"

        /**
         * Location property: the first location of a .class file
         * notification.
         */
        private const val KEY_CLASS = "class"

        private const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"

        private const val MIN_TARGET = 33
    }

    override fun getApplicableMethodNames(): List<String> = listOf("notify")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, "android.app.NotificationManager")) {
            if (!evaluator.isMemberInClass(method, "androidx.core.app.NotificationCompat")) {
                return
            }
        }

        // In the IDE we can immediately check manifest and target sdk version and bail out if permission
        // is held or not yet targeting T
        if (context.isGlobalAnalysis()) {
            if (context.mainProject.targetSdk < MIN_TARGET || isHoldingPostNotifications(context.mainProject) != false) {
                return
            }
        } else {
            val map = context.getPartialResults(ISSUE).map()
            if (!map.containsKey(KEY_SOURCE)) {
                map.put(KEY_SOURCE, true)
            }
        }

        val incident = Incident(ISSUE, node, context.getLocation(node), getWarningMessage(), createFix())
        if (context.isGlobalAnalysis()) {
            context.report(incident)
            if (context.driver.scope.contains(Scope.JAVA_LIBRARIES)) {
                context.getPartialResults(ISSUE).map().put(KEY_SOURCE, true)
            }
        } else {
            context.report(incident, map())
        }
    }

    private fun getWarningMessage() =
        "When targeting Android 13 or higher, posting a permission requires holding the `POST_NOTIFICATIONS` permission"

    override fun getApplicableCallNames(): List<String> = listOf("notify")

    override fun checkCall(context: ClassContext, classNode: ClassNode, method: MethodNode, call: MethodInsnNode) {
        if (call.owner == "android/app/NotificationManager" || call.owner == "androidx/core/app/NotificationCompat") {
            val map = context.getPartialResults(ISSUE).map()
            if (!map.containsKey(KEY_CLASS)) {
                map.put(KEY_CLASS, context.getLocation(call))
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        if (context.isGlobalAnalysis() && context.driver.scope.contains(Scope.JAVA_LIBRARIES)) {
            checkClassReference(context.getPartialResults(ISSUE).map(), context)
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        assert(!context.isGlobalAnalysis())
        if (context.project === context.mainProject) {
            val map = partialResults.map()
            checkClassReference(map, context)
        }
    }

    private fun checkClassReference(map: LintMap, context: Context) {
        if (context.mainProject.targetSdk < MIN_TARGET) {
            return
        }
        if (map.getBoolean(KEY_SOURCE) == true) {
            // Have already found source reference and reported it there (from visitCall); no need to also report
            // from bytecode
            return
        }
        if (isHoldingPostNotifications(context.mainProject) != false) {
            return
        }
        val location = map.getLocation(KEY_CLASS) ?: return
        context.report(ISSUE, location, getWarningMessage(), createFix())
    }

    private fun isHoldingPostNotifications(project: Project): Boolean? {
        val mergedManifest = project.mergedManifest ?: return null
        var curr = mergedManifest.documentElement?.firstChild ?: return null
        while (true) {
            if (curr.nodeType == Node.ELEMENT_NODE && curr.nodeName == TAG_USES_PERMISSION) {
                val element = curr as Element
                val permission = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (permission == POST_NOTIFICATIONS_PERMISSION) {
                    return true
                }
            }
            curr = curr.nextSibling ?: return false
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        if (context.mainProject.targetSdk >= MIN_TARGET && isHoldingPostNotifications(context.mainProject) == false) {
            map.put(KEY_SOURCE, true)
            return true
        }

        return false
    }

    private fun createFix(): LintFix {
        return fix().data(
            PermissionDetector.KEY_MISSING_PERMISSIONS,
            listOf(POST_NOTIFICATIONS_PERMISSION)
        )
    }
}

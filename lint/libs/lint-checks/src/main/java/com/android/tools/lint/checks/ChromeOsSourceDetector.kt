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
import org.jetbrains.uast.UReferenceExpression

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String>? {
        return listOf(
            "setRequestedOrientation",
            "hasSystemFeature"
        )
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val name = method.name
        if ("setRequestedOrientation" == name) {
            reportUnsupportedOrientationViolations(node, context)
        } else if ("hasSystemFeature" == name) {
            reportRearCameraOnlySystemFeatureViolations(node, context)
        }
    }

    private fun reportRearCameraOnlySystemFeatureViolations(
        node: UCallExpression,
        context: JavaContext
    ) {
        val cameraFeatureRequested = determinePropertyString(node)
        if (FEATURE_CAMERA_STRING == cameraFeatureRequested) {
            val message = "You should look for any camera on the device, not just the rear"
            val fix = fix().name("Switch to look for FEATURE_CAMERA_ANY")
                .replace()
                .text(cameraFeatureRequested)
                .with(FEATURE_CAMERA_ANY_STRING)
                .build()

            context.report(
                UNSUPPORTED_CAMERA_FEATURE,
                node,
                context.getLocation(node),
                message,
                fix
            )
        }
    }

    private fun reportUnsupportedOrientationViolations(
        node: UCallExpression,
        context: JavaContext
    ) {
        val orientationValue = determinePropertyString(node) ?: return
        if (UNSUPPORTED_ORIENTATIONS.contains(orientationValue)) {
            val message = "You should not lock orientation of your activities, so that you can " +
                    "support a good user experience for any device or orientation"
            val fix = fix().name("Set the orientation to SCREEN_ORIENTATION_UNSPECIFIED")
                .replace()
                .text(orientationValue)
                .with(UNSPECIFIED_ORIENTATION_VALUE)
                .build()

            context.report(
                UNSUPPORTED_LOCKED_ORIENTATION,
                node,
                context.getLocation(node),
                message,
                fix
            )
        }
    }

    private fun determinePropertyString(node: UCallExpression): String? {
        val firstArgument = node.valueArguments.firstOrNull()
        val reference = firstArgument as? UReferenceExpression ?: return null
        return reference.resolvedName
    }

    companion object {
        private val IMPLEMENTATION =
            Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE)

        @JvmField
        val UNSUPPORTED_LOCKED_ORIENTATION = Issue.create(
            id = "SourceLockedOrientationActivity",
            briefDescription = "Activity is locked to an orientation",
            explanation = """
                The `Activity` should not be locked to any orientation so that users
                can take advantage of the multi-window environments and larger screens
                available on Chrome OS. To fix the issue, consider calling `setRequestedOrientation`
                with the `ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR` or
                `ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED` options or removing the call
                all together.
                """,
            category = Category.CHROME_OS,
            priority = 4,
            severity = Severity.ERROR,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        ).setEnabledByDefault(false)

        @JvmField
        val UNSUPPORTED_CAMERA_FEATURE = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Looking for Rear Camera only feature",
            explanation = """
                You should look for the `FEATURE_CAMERA_ANY` features to include all
                possible cameras that may be on the device. Looking for `FEATURE_CAMERA`
                only looks for a rear facing camera, which certain tablets or Chrome OS
                devices don't have. To fix the issue, look for `FEATURE_CAMERA_ANY` instead.
                """,
            category = Category.CHROME_OS,
            priority = 4,
            severity = Severity.ERROR,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        ).setEnabledByDefault(false)

        val UNSUPPORTED_ORIENTATIONS = setOf(
            "SCREEN_ORIENTATION_LANDSCAPE",
            "SCREEN_ORIENTATION_PORTRAIT",
            "SCREEN_ORIENTATION_REVERSE_LANDSCAPE",
            "SCREEN_ORIENTATION_REVERSE_PORTRAIT",
            "SCREEN_ORIENTATION_SENSOR_LANDSCAPE",
            "SCREEN_ORIENTATION_SENSOR_PORTRAIT",
            "SCREEN_ORIENTATION_USER_LANDSCAPE",
            "SCREEN_ORIENTATION_USER_PORTRAIT"
        )

        const val UNSPECIFIED_ORIENTATION_VALUE = "SCREEN_ORIENTATION_UNSPECIFIED"

        const val FEATURE_CAMERA_ANY_STRING = "FEATURE_CAMERA_ANY"

        const val FEATURE_CAMERA_STRING = "FEATURE_CAMERA"
    }
}

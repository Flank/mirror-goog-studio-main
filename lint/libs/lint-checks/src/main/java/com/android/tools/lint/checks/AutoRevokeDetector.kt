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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.TAG_APPLICATION
import com.android.sdklib.AndroidVersion
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Element

/**
 * This detector enforces that the 'autoRevokePermissions' attribute is declared in apps
 * targeting API 30+. It also discourages the use of autoRevokePermissions="disallowed".
 */
class AutoRevokeDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf(TAG_APPLICATION)

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.mainProject.targetSdk < INITIAL_API) return
        if (element.tagName != TAG_APPLICATION) return

        val tolerance: Attr? = element.getAttributeNodeNS(ANDROID_URI, "autoRevokePermissions")
        if (tolerance != null && tolerance.value != "disallowed") return

        val setAllowed = fix().set(ANDROID_URI, "autoRevokePermissions", "allowed").build()
        val setDiscouraged = fix().set(ANDROID_URI, "autoRevokePermissions", "discouraged").build()

        if (tolerance == null) {
            context.report(
                MISSING_AUTO_REVOKE_TOLERANCE,
                context.getNameLocation(element),
                "Missing required attribute: `autoRevokePermissions`",
                fix().alternatives(setAllowed, setDiscouraged)
            )
        } else if (tolerance.value == "disallowed") {
            context.report(
                DISALLOWED_AUTO_REVOKE,
                context.getLocation(tolerance),
                "Most apps should not require `autoRevokePermissions=\"disallowed\"`",
                fix().alternatives(setDiscouraged, setAllowed)
            )
        }
    }

    companion object {
        /** The API version in which auto-revoke was introduced. */
        private const val INITIAL_API = AndroidVersion.VersionCodes.R

        @JvmField
        val MISSING_AUTO_REVOKE_TOLERANCE = Issue.create(
            id = "MissingAutoRevokeTolerance",
            briefDescription = "Missing attribute `autoRevokePermissions`",
            explanation = """
            Apps targeting API 30 or above must declare their tolerance to having their \
            permissions automatically revoked. To declare a tolerance, specify \
            the `android:autoRevokePermissions` attribute on the `<application>` element.
            """,
            category = Category.COMPLIANCE,
            priority = 8,
            severity = Severity.WARNING,
            implementation = Implementation(
                AutoRevokeDetector::class.java,
                Scope.MANIFEST_SCOPE
            ),
            androidSpecific = true
            // TODO: Add a moreInfo link.
        )

        @JvmField
        val DISALLOWED_AUTO_REVOKE = Issue.create(
            id = "DisabledAutoRevoke",
            briefDescription = "Using `autoRevokePermissions=\"disallowed\"`",
            explanation = """
            Using `autoRevokePermissions="disallowed"` to disable auto revoke is rarely needed. \
            Most apps on Google Play are not allowed to have this permission.

            Instead consider using:
            - `android:autoRevokePermissions="discouraged"`
            - `android:autoRevokePermissions="allowed"`
            """,
            category = Category.COMPLIANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AutoRevokeDetector::class.java,
                Scope.MANIFEST_SCOPE
            ),
            androidSpecific = true
            // TODO: Add a moreInfo link.
        )
    }
}

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
import com.android.SdkConstants.ATTR_PERMISSION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_ACTIVITY_ALIAS
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_PERMISSION
import com.android.SdkConstants.TAG_PROVIDER
import com.android.SdkConstants.TAG_RECEIVER
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.tools.lint.checks.SystemPermissionsDetector.SYSTEM_PERMISSIONS
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.editDistance
import org.w3c.dom.Element
import java.util.Arrays

/**
 * looks for obvious errors in the guarding of components with a permission via the android:permission attribute
 */
class PermissionErrorDetector : Detector(), XmlScanner {
    override fun getApplicableElements(): Collection<String> = listOf(
        TAG_APPLICATION,
        TAG_ACTIVITY,
        TAG_ACTIVITY_ALIAS,
        TAG_RECEIVER,
        TAG_SERVICE,
        TAG_PROVIDER,
        TAG_PERMISSION,
        TAG_USES_PERMISSION
    )

    override fun visitElement(context: XmlContext, element: Element) {
        getIncident(context, element)?.let { context.report(it) }
    }

    private fun getIncident(context: XmlContext, element: Element): Incident? {
        val tagName = element.tagName
        if (tagName == TAG_PERMISSION) {
            element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)?.let {
                if (isSystemPermission(it.value))
                    return Incident(
                        RESERVED_SYSTEM_PERMISSION,
                        element,
                        context.getValueLocation(it),
                        "`${it.value}` is a reserved permission for the system"
                    )
            }
        } else {
            element
                .getAttributeNodeNS(
                    ANDROID_URI,
                    if (tagName == TAG_USES_PERMISSION) ATTR_NAME else ATTR_PERMISSION
                )?.let { attr ->
                    if (KNOWN_PERMISSION_ERROR_VALUES.any { it.equals(attr.value, ignoreCase = true) }) {
                        return Incident(
                            KNOWN_PERMISSION_ERROR,
                            element,
                            context.getValueLocation(attr),
                            "`${attr.value}` is not a valid permission value"
                        )
                    }

                    findAlmostSystemPermission(attr.value)?.let {
                        return Incident(
                            SYSTEM_PERMISSION_TYPO,
                            element,
                            context.getValueLocation(attr),
                            "Did you mean `$it`?",
                            fix().replace().text(attr.value).with(it).build()
                        )
                    }
                }
        }

        return null
    }

    companion object {
        private val IMPLEMENTATION = Implementation(
            PermissionErrorDetector::class.java,
            Scope.MANIFEST_SCOPE
        )

        private val KNOWN_PERMISSION_ERROR_VALUES = listOf("true", "false") // TODO: additional obvious values?

        @JvmField
        val KNOWN_PERMISSION_ERROR: Issue = Issue.create(
            id = "KnownPermissionError",
            briefDescription = "Value specified for permission is a known error",
            explanation = """
                This check looks for values specified in component permissions that are known errors, such as \
                `android:permission="true"`.

                 Please double check the permission value you have supplied.  The value is expected to be a \
                 permission string from the system, another app, or your own, NOT a boolean.
                """,
            category = Category.SECURITY,
            priority = 5,
            severity = Severity.ERROR,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        val RESERVED_SYSTEM_PERMISSION: Issue = Issue.create(
            id = "ReservedSystemPermission",
            briefDescription = "Permission name is a reserved system permission",
            explanation = """
                This check looks for custom permission declarations whose names are reserved values \
                for system permissions.

                Please double check the permission name you have supplied.  Attempting to redeclare a system \
                permission will be ignored.
                """,
            category = Category.SECURITY,
            priority = 5,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        @JvmField
        val SYSTEM_PERMISSION_TYPO: Issue = Issue.create(
            id = "SystemPermissionTypo",
            briefDescription = "Permission appears to be a system permission with a typo",
            explanation = """
                This check looks for required permissions that *look* like well-known system permissions, but aren't, \
                 and may be typos.

                 Please double check the permission value you have supplied.
                """,
            category = Category.SECURITY,
            priority = 5,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        // the edit distance at which we have reasonable confidence that there is a typo
        const val MAX_EDIT_DISTANCE = 4

        // purely to optimize the LintUtils.editDistance call
        const val EDIT_DISTANCE_ESCAPE = MAX_EDIT_DISTANCE + 1

        val UNEXPECTED_CHAR_REGEX = Regex("[^a-zA-Z0-9_.]+")

        /**
         * Crude implementation to detect an incorrectly specified system permission
         */
        fun findAlmostSystemPermission(requiredPermission: String): String? {
            if (isSystemPermission(requiredPermission)) return null
            /**
             * The convention followed by system permissions (and most permissions, for that matter) is to use letters,
             * period, and underscore characters. Remove characters that do not follow the system permission convention
             * to catch simple typos, such as a block of spaces, or other infrequently used characters.
             */
            val trimmedLowerRequiredPermission = requiredPermission
                .replace(UNEXPECTED_CHAR_REGEX, "")
                .lowercase()
            return SYSTEM_PERMISSIONS.firstOrNull { systemPermission ->
                val lowerSystemPermission = systemPermission.lowercase()
                // catches a common mistake - attempting to specify multiple permissions separated by some character
                if (lowerSystemPermission in trimmedLowerRequiredPermission) return systemPermission
                /*
                 * The most interesting information from most permissions is the part AFTER the "path".
                 * Split the permissions into "prefix" and "suffix" so we can evaluate the two independently.
                 */
                val (requiredPrefix, requiredSuffix) = permissionToPrefixAndSuffix(trimmedLowerRequiredPermission)
                val (systemPrefix, systemSuffix) = permissionToPrefixAndSuffix(lowerSystemPermission)

                editDistance(requiredSuffix, systemSuffix, EDIT_DISTANCE_ESCAPE) <= MAX_EDIT_DISTANCE && (
                    // If the suffixes are reasonably close, we can additionally check for this known bad pattern in the prefix.
                    requiredPrefix == "android.manifest.permission" ||
                        editDistance(requiredPrefix, systemPrefix, EDIT_DISTANCE_ESCAPE) <= MAX_EDIT_DISTANCE
                    )
            }
        }

        fun permissionToPrefixAndSuffix(permission: String): Pair<String, String> = Pair(
            permission.split(".").dropLast(1).joinToString("."),
            permission.split(".").last()
        )

        fun isSystemPermission(permissionName: String): Boolean =
            Arrays.binarySearch(SYSTEM_PERMISSIONS, permissionName) >= 0
    }
}

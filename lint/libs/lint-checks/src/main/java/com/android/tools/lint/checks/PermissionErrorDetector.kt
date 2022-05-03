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
import com.android.SdkConstants.ATTR_PACKAGE
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
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LocationType
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.editDistance
import com.android.utils.XmlUtils.getFirstSubTag
import com.android.utils.XmlUtils.getNextTag
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.util.Arrays

/**
 * looks for obvious errors in the guarding of components with a permission via the android:permission attribute
 */
class PermissionErrorDetector : Detector(), XmlScanner {
    override fun checkMergedProject(context: Context) {
        val root = context.mainProject.mergedManifest?.documentElement ?: return
        checkDocument(context, root, true)
    }
    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        checkDocument(context, root, false)
    }

    private fun checkDocument(context: Context, root: Element, isMergedManifest: Boolean) {
        var customPermissions: MutableList<String>? = null
        var customPermissionUsages: MutableList<Attr>? = null

        fun reportOrAddCustomPermissions(attr: Attr) {
            val evaluateCustomPermission = reportPermissionDefinitionIncidents(context, attr, isMergedManifest)
            if (evaluateCustomPermission)
                (customPermissions ?: mutableListOf<String>().also { customPermissions = it })
                    .add(attr.value)
        }

        fun reportOrAddPermissionUsages(attr: Attr) {
            val evaluateCustomPermissionUsage = reportPermissionUsageIncidents(context, attr, isMergedManifest)
            if (evaluateCustomPermissionUsage)
                (customPermissionUsages ?: mutableListOf<Attr>().also { customPermissionUsages = it })
                    .add(attr)
        }

        var topLevel = getFirstSubTag(root)
        while (topLevel != null) {
            when (topLevel.tagName) {
                TAG_PERMISSION -> {
                    topLevel.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)?.let(::reportOrAddCustomPermissions)
                }
                TAG_USES_PERMISSION -> {
                    topLevel.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)?.let(::reportOrAddPermissionUsages)
                }
                TAG_APPLICATION -> {
                    topLevel.getAttributeNodeNS(ANDROID_URI, ATTR_PERMISSION)?.let(::reportOrAddPermissionUsages)

                    var componentLevel = getFirstSubTag(topLevel)
                    while (componentLevel != null) {
                        when (componentLevel.tagName) {
                            TAG_APPLICATION,
                            TAG_ACTIVITY,
                            TAG_ACTIVITY_ALIAS,
                            TAG_RECEIVER,
                            TAG_SERVICE,
                            TAG_PROVIDER -> {
                                componentLevel.getAttributeNodeNS(ANDROID_URI, ATTR_PERMISSION)?.let(::reportOrAddPermissionUsages)
                            }
                        }
                        componentLevel = getNextTag(componentLevel)
                    }
                }
            }
            topLevel = getNextTag(topLevel)
        }

        if (customPermissions != null && customPermissionUsages != null) {
            for (potentialCustomPermissionUsage in customPermissionUsages ?: emptyList()) {
                val permissionName = potentialCustomPermissionUsage.value
                findAlmostCustomPermission(permissionName, customPermissions ?: emptyList())?.let {
                    context.report(
                        Incident(
                            CUSTOM_PERMISSION_TYPO,
                            potentialCustomPermissionUsage.ownerElement,
                            context.getLocation(potentialCustomPermissionUsage, LocationType.VALUE),
                            "Did you mean `$it`?",
                            fix().replace().text(permissionName).with(it).build()
                        )
                    )
                }
            }
        }
    }

    /**
     * report incidents related to permission definitions that are NOT related to custom permission typos
     * @return boolean representing whether this attribute should be evaluated for a custom permission typo
     */
    private fun reportPermissionDefinitionIncidents(context: Context, attr: Attr, isMergedManifest: Boolean): Boolean {
        val packageName =
            (context.project.buildVariant?.`package` ?: attr.ownerDocument.documentElement?.getAttribute(ATTR_PACKAGE))
                .orEmpty()

        if (isSystemPermission(attr.value)) {
            if (!isMergedManifest) context.report(
                Incident(
                    RESERVED_SYSTEM_PERMISSION,
                    attr.ownerElement,
                    context.getLocation(attr, LocationType.VALUE),
                    "`${attr.value}` is a reserved permission for the system"
                )
            )
            if (context.isEnabled(RESERVED_SYSTEM_PERMISSION)) return false
        }

        if (!followsCustomPermissionNamingConvention(packageName, attr.value)) {
            if (!isMergedManifest) context.report(
                Incident(
                    PERMISSION_NAMING_CONVENTION,
                    attr.ownerElement,
                    context.getLocation(attr, LocationType.VALUE),
                    "`${attr.value} does not follow recommended naming convention`"
                )
            )
            if (context.isEnabled(PERMISSION_NAMING_CONVENTION)) return false
        }

        return isMergedManifest
    }

    /**
     * report incidents related to permission usages that are NOT related to custom permission typos
     * @return boolean representing whether this attribute should be evaluated for a custom permission typo
     */
    private fun reportPermissionUsageIncidents(context: Context, attr: Attr, isMergedManifest: Boolean): Boolean {
        if (KNOWN_PERMISSION_ERROR_VALUES.any { it.equals(attr.value, ignoreCase = true) }) {
            if (!isMergedManifest) context.report(
                Incident(
                    KNOWN_PERMISSION_ERROR,
                    attr.ownerElement,
                    context.getLocation(attr, LocationType.VALUE),
                    "`${attr.value}` is not a valid permission value"
                )
            )
            // signal that the attribute would have already been reported
            if (context.isEnabled(KNOWN_PERMISSION_ERROR)) return false
        }

        findAlmostSystemPermission(attr.value)?.let {
            if (!isMergedManifest) context.report(
                Incident(
                    SYSTEM_PERMISSION_TYPO,
                    attr.ownerElement,
                    context.getLocation(attr, LocationType.VALUE),
                    "Did you mean `$it`?",
                    fix().replace().text(attr.value).with(it).build()
                )
            )
            // signal that the attribute would have already been reported
            if (context.isEnabled(SYSTEM_PERMISSION_TYPO)) return false
        }

        return isMergedManifest
    }

    companion object {
        private val IMPLEMENTATION = Implementation(
            PermissionErrorDetector::class.java,
            Scope.MANIFEST_SCOPE
        )

        @JvmField
        val PERMISSION_NAMING_CONVENTION: Issue = Issue.create(
            id = "PermissionNamingConvention",
            briefDescription = "Permission name does not follow recommended convention",
            explanation = """
                Permissions should be prefixed with an app's package name, using \
                reverse-domain-style naming. This prefix should be followed by `.permission.`, \
                and then a description of the capability that the permission represents, in \
                upper SNAKE_CASE. For example, `com.example.myapp.permission.ENGAGE_HYPERSPACE`.

                Following this recommendation avoids naming collisions, and helps clearly \
                identify the owner and intention of a custom permission.
                """,
            category = Category.SECURITY,
            priority = 5,
            severity = Severity.WARNING,
            enabledByDefault = false,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        private val PERMISSION_SUFFIX_REGEX = Regex("[A-Z0-9_]+")

        fun followsCustomPermissionNamingConvention(packageName: String, permissionName: String): Boolean {
            if (packageName.isEmpty()) return true

            val (prefix, suffix) = permissionToPrefixAndSuffix(permissionName)
            return (
                prefix.startsWith(packageName) &&
                    prefix.endsWith("permission") &&
                    suffix.matches(PERMISSION_SUFFIX_REGEX)
                )
        }

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

        private val KNOWN_PERMISSION_ERROR_VALUES = listOf("true", "false") // TODO: additional obvious values?

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
            severity = Severity.ERROR,
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

        @JvmField
        val CUSTOM_PERMISSION_TYPO: Issue = Issue.create(
            id = "CustomPermissionTypo",
            briefDescription = "Permission appears to be a custom permission with a typo",
            explanation = """
                This check looks for required permissions that *look* like custom permissions defined in the same \
                 manifest, but aren't, and may be typos.

                 Please double check the permission value you have supplied.
                """,
            category = Category.SECURITY,
            priority = 5,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        // the edit distance at which we have reasonable confidence that there is a typo
        private const val MAX_EDIT_DISTANCE = 4

        // purely to optimize the LintUtils.editDistance call
        private const val EDIT_DISTANCE_ESCAPE = MAX_EDIT_DISTANCE + 1

        fun findAlmostCustomPermission(requiredPermission: String, customPermissions: List<String>): String? {
            if (customPermissions.contains(requiredPermission)) return null
            return customPermissions.firstOrNull { editDistance(requiredPermission, it, EDIT_DISTANCE_ESCAPE) in 1..MAX_EDIT_DISTANCE }
        }

        private val UNEXPECTED_CHAR_REGEX = Regex("[^a-zA-Z0-9_.]+")

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

        fun isSystemPermission(permissionName: String): Boolean =
            Arrays.binarySearch(SYSTEM_PERMISSIONS, permissionName) >= 0

        fun permissionToPrefixAndSuffix(permission: String): Pair<String, String> = Pair(
            permission.split(".").dropLast(1).joinToString("."),
            permission.split(".").last()
        )
    }
}

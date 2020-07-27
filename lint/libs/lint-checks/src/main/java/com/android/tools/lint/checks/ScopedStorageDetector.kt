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
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.SdkConstants.VALUE_TRUE
import com.android.sdklib.AndroidVersion.VersionCodes
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.iterator
import org.w3c.dom.Element

/**
 * Helps apps transition to using scoped storage, which is described at
 * https://developer.android.com/preview/privacy/storage#scoped-storage.
 *
 * Warns about WRITE_EXTERNAL_STORAGE, which no longer provides write access to files.
 * Warns about MANAGE_EXTERNAL_STORAGE, which is disallowed for most apps.
 */
class ScopedStorageDetector : Detector(), XmlScanner {
    private var cachedStoragePermissions: StoragePermissions? = null

    private data class StoragePermissions(
        val canManageStorage: Boolean,
        val requestedLegacyStorage: Boolean
    )

    override fun getApplicableElements() = listOf(TAG_USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        val permission = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: return

        // WRITE_EXTERNAL_STORAGE.
        if (permission.value == "android.permission.WRITE_EXTERNAL_STORAGE") {
            val sdk = context.mainProject.targetSdk
            if (sdk < VersionCodes.Q) {
                return
            }
            val permissions = getStoragePermissions(context) ?: return
            if (permissions.canManageStorage) {
                return
            }
            if (sdk == VersionCodes.Q && permissions.requestedLegacyStorage) {
                return
            }
            var msg = "WRITE_EXTERNAL_STORAGE no longer provides write access when targeting "
            msg += when {
                permissions.requestedLegacyStorage -> "Android 11+, even when using `requestLegacyExternalStorage`"
                sdk == VersionCodes.Q -> "Android 10, unless you use `requestLegacyExternalStorage`"
                else -> "Android 10+"
            }
            context.report(ISSUE, context.getValueLocation(permission), msg)
        }

        // MANAGE_EXTERNAL_STORAGE.
        if (permission.value == "android.permission.MANAGE_EXTERNAL_STORAGE") {
            context.report(
                ISSUE,
                context.getValueLocation(permission),
                "Most apps are not allowed to use MANAGE_EXTERNAL_STORAGE"
            )
        }
    }

    private fun getStoragePermissions(context: Context): StoragePermissions? {
        cachedStoragePermissions?.let { return it }

        val manifest = context.mainProject.mergedManifest ?: return null
        var canManageStorage = false
        var requestedLegacyStorage = false

        for (tag in manifest.documentElement) {
            when (tag.nodeName) {
                TAG_APPLICATION -> {
                    val legacy = tag.getAttributeNS(ANDROID_URI, "requestLegacyExternalStorage")
                    if (legacy == VALUE_TRUE) {
                        requestedLegacyStorage = true
                    }
                }
                TAG_USES_PERMISSION -> {
                    val permission = tag.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    if (permission == "android.permission.MANAGE_EXTERNAL_STORAGE") {
                        canManageStorage = true
                    }
                }
            }
        }

        return StoragePermissions(canManageStorage, requestedLegacyStorage)
            .also { cachedStoragePermissions = it }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ScopedStorage",
            briefDescription = "Affected by scoped storage",
            explanation =
                """
                Scoped storage is enforced on Android 10+ (or Android 11+ if using \
                `requestLegacyExternalStorage`). In particular, `WRITE_EXTERNAL_STORAGE` \
                will no longer provide write access to all files; it will provide the \
                equivalent of `READ_EXTERNAL_STORAGE` instead.

                The `MANAGE_EXTERNAL_STORAGE` permission can be used to manage all files, but \
                it is rarely necessary and most apps on Google Play are not allowed to use it. \
                Most apps should instead migrate to use scoped storage. To modify or delete files, \
                apps should request write access from the user as described at \
                https://developer.android.com/reference/android/provider/MediaStore#createWriteRequest.
            """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = Implementation(
                ScopedStorageDetector::class.java,
                Scope.MANIFEST_SCOPE
            ),
            moreInfo = "https://developer.android.com/preview/privacy/storage#scoped-storage"
        )
    }
}

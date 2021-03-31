/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
import org.w3c.dom.Element

class TileProviderDetector : Detector(), XmlScanner {
    companion object Issues {
        @JvmField
        val ISSUE = Issue.create(
            id = "TileProviderPermissions",
            briefDescription = "TileProvider does not set permission",
            explanation = """
                TileProviders should require the `com.google.android.wearable.permission.BIND_TILE_PROVIDER` \
                permission to prevent arbitrary apps from binding to it.
            """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                TileProviderDetector::class.java,
                Scope.MANIFEST_SCOPE
            ),
            androidSpecific = true
        )

        const val BIND_TILE_PROVIDER_PERMISSION = "com.google.android.wearable.permission.BIND_TILE_PROVIDER"
        const val BIND_TILE_PROVIDER_ACTION = "androidx.wear.tiles.action.BIND_TILE_PROVIDER"
    }

    override fun getApplicableElements() = listOf(TAG_SERVICE)

    override fun visitElement(context: XmlContext, service: Element) {
        var intentFilter = getFirstSubTagByName(service, TAG_INTENT_FILTER)
        while (intentFilter != null) {
            var action = getFirstSubTagByName(intentFilter, TAG_ACTION)
            while (action != null) {
                if (action.getAttributeNS(ANDROID_URI, ATTR_NAME) == BIND_TILE_PROVIDER_ACTION) {
                    checkTileProvider(context, service)
                    return
                }
                action = getNextTagByName(action, TAG_ACTION)
            }
            intentFilter = getNextTagByName(intentFilter, TAG_INTENT_FILTER)
        }
    }

    private fun checkTileProvider(context: XmlContext, service: Element) {
        val permission = service.getAttributeNS(ANDROID_URI, ATTR_PERMISSION)
        if (permission != BIND_TILE_PROVIDER_PERMISSION) {
            val fix = fix().set()
                .attribute(ATTR_PERMISSION)
                .value(BIND_TILE_PROVIDER_PERMISSION)
                .android()
                .name(if (permission.isEmpty()) "Add BIND_TILE_PROVIDER permission" else "Change permission to BIND_TILE_PROVIDER")
                .build()
            context.report(
                Incident(
                    ISSUE,
                    service,
                    context.getNameLocation(service),
                    "TileProvider does not specify BIND_TILE_PROVIDER permission",
                    fix
                )
            )
        }
    }
}

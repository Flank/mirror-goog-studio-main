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
import com.android.SdkConstants.ATTR_REQUIRED
import com.android.SdkConstants.TAG_MANIFEST
import com.android.SdkConstants.TAG_USES_FEATURE
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.iterator
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), XmlScanner {
    override fun getApplicableElements() = setOf(TAG_MANIFEST)
    private var hasLeanBack: Boolean? = null
    private var wifiFeatureNode: Element? = null
    private var wifiFeatureNodeSearched = false
    private var wifiFeatureNodeRequired: Boolean? = null

    override fun visitElement(context: XmlContext, element: Element) {
        if (hasLeanBack == null) {
            hasLeanBack = context.mainProject.mergedManifest
                ?.documentElement?.iterator()?.asSequence()?.any { node ->
                node.localName == TAG_USES_FEATURE && node.attributes.getNamedItemNS(
                    ANDROID_URI,
                    ATTR_NAME
                )?.nodeValue == LEANBACK_ATTR_NAME
            }
        }
        if (hasLeanBack != true) return
        if (!wifiFeatureNodeSearched) {
            wifiFeatureNode = context.mainProject.mergedManifest
                ?.documentElement?.iterator()?.asSequence()?.firstOrNull { node ->
                when (node.localName) {
                    TAG_USES_FEATURE -> {
                        with(node.attributes) {
                            getNamedItemNS(
                                ANDROID_URI,
                                ATTR_NAME
                            )?.nodeValue == WIFI_FEATURE_NAME
                        }
                    }
                    else -> {
                        false
                    }
                }
            }
            wifiFeatureNodeRequired = wifiFeatureNode?.let { wifiNode ->
                wifiNode.attributes.getNamedItemNS(ANDROID_URI, ATTR_REQUIRED)?.nodeValue != "false"
            } ?: false
            if (wifiFeatureNode != null && wifiFeatureNodeRequired == true) {
                context.report(
                    ISSUE,
                    wifiFeatureNode,
                    context.getLocation(wifiFeatureNode!!),
                    "Requiring `android.hardware.wifi` limits app availability on TVs that support only Ethernet"
                )
            }
            wifiFeatureNodeSearched = true
        }
        val wifiPermissionsNode = element.iterator().asSequence().firstOrNull { node ->
            when (node.localName) {
                TAG_USES_PERMISSION -> {
                    node.attributes.getNamedItemNS(
                        ANDROID_URI,
                        ATTR_NAME
                    )?.let { attr -> WIFI_STATE_PERMISSIONS.contains(attr.nodeValue) } ?: false
                }
                else -> {
                    false
                }
            }
        }
        if (wifiPermissionsNode != null && wifiFeatureNode == null) {
            context.report(
                ISSUE,
                wifiPermissionsNode,
                context.getLocation(wifiPermissionsNode),
                "Requiring Wifi permissions limits app availability on TVs that support only Ethernet"
            )
        }
    }

    companion object {
        private const val LEANBACK_ATTR_NAME = "android.software.leanback"
        private const val WIFI_FEATURE_NAME = "android.hardware.wifi"
        private val WIFI_STATE_PERMISSIONS = setOf(
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.CHANGE_WIFI_MULTICAST_STATE"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using android.hardware.wifi on TV",
            explanation =
                """
                WiFi is not required for Android TV and many devices connect to the internet via \
                alternative methods e.g. Ethernet.

                If your app is not focused specifically on WiFi functionality and only wishes to \
                connect to the internet, please modify your Manifest to contain: \
                `<uses-feature android:name="android.hardware.wifi" android:required="false" />`

                Un-metered or non-roaming connections can be detected in software using
                `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and \
                `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING.`
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                LeanbackWifiUsageDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}

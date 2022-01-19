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

package com.android.manifmerger.unit

import com.android.manifmerger.ManifestMerger2
import com.android.manifmerger.ManifestMerger2SmallTest.parse
import com.android.manifmerger.MergingReport
import com.android.manifmerger.TestUtils
import com.android.manifmerger.testutils.attr_tools_replace
import com.android.manifmerger.testutils.get
import com.android.manifmerger.testutils.getNamedAttributeAndroidNS
import com.android.manifmerger.testutils.manifest
import com.android.testutils.MockLog
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocaleConfigTagTest {

    @Test
    fun `Value from lib does not override value from app`() {
        val mockLog = MockLog()
        val appManifest =
            manifest("com.example.test.locales") {
                application(android_label = "Blerg") {
                    attr_android_localeConfig("@xml/locales_config")
                }
            }
        val libManifest =
            manifest("com.example.test.locales.lib") {
                application(android_label = "Blerg") {
                    attr_android_localeConfig("@xml/locales_config_from_lib")
                }
            }
        val appFile = TestUtils.inputAsFile("appFile", appManifest.toString())
        val libFile = TestUtils.inputAsFile("libFile", libManifest.toString())

        try {
            val mergingReport =
                ManifestMerger2.newMerger(appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                    .addLibraryManifest(libFile)
                    .merge()
            assertThat(mergingReport.result).isEqualTo(MergingReport.Result.SUCCESS)
            val document =
                parse(mergingReport.getMergedDocument(MergingReport.MergedManifestKind.MERGED))

            assertThat(
                    document
                        .getElementsByTagName("application")
                        .item(0)
                        .attributes
                        .getNamedItemNS(
                            "http://schemas.android.com/apk/res/android", "localeConfig")
                        .nodeValue)
                .isEqualTo("@xml/locales_config")
        } finally {
            assertThat(appFile.delete()).named("appFile was deleted").isTrue()
            assertThat(libFile.delete()).named("libFile was deleted").isTrue()
        }
    }

    @Test
    fun `Value from lib is never included in merge result`() {
        val mockLog = MockLog()
        val appManifest =
            manifest("com.example.test.locales") {
                application(android_label = "Blerg") {
                    // No android:localeConfig set here
                }
            }
        val libManifest =
            manifest("com.example.test.locales.lib") {
                application(android_label = "Blerg") {
                    attr_android_localeConfig("@xml/locales_config_from_lib")
                }
            }
        val appFile = TestUtils.inputAsFile("appFile", appManifest.toString())
        val libFile = TestUtils.inputAsFile("libFile", libManifest.toString())

        try {
            val mergingReport =
                ManifestMerger2.newMerger(appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                    .addLibraryManifest(libFile)
                    .merge()
            assertThat(mergingReport.result).isEqualTo(MergingReport.Result.SUCCESS)
            val document =
                parse(mergingReport.getMergedDocument(MergingReport.MergedManifestKind.MERGED))

            assertThat(
                    document.getElementsByTagName("application")[0].getNamedAttributeAndroidNS(
                        "localeConfig"))
                .isNull()
        } finally {
            assertThat(appFile.delete()).named("appFile was deleted").isTrue()
            assertThat(libFile.delete()).named("libFile was deleted").isTrue()
        }
    }

    @Test
    fun `Value from overlay must match value from app`() {
        val mockLog = MockLog()
        val appManifest =
            manifest("com.example.test.locales") {
                application(android_label = "Blerg") {
                    attr_android_localeConfig("@xml/locales_config")
                }
            }
        val overlayManifest =
            manifest("com.example.test.locales") {
                application(android_label = "Blerg") {
                    attr_android_localeConfig("@xml/locales_config_from_overlay")
                }
            }
        val appFile = TestUtils.inputAsFile("appFile", appManifest.toString())
        val overlayFile = TestUtils.inputAsFile("overlayFile", overlayManifest.toString())

        try {
            val mergingReport =
                ManifestMerger2.newMerger(appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                    .addFlavorAndBuildTypeManifest(overlayFile)
                    .merge()
            assertThat(mergingReport.result).isEqualTo(MergingReport.Result.ERROR)

            assertThat(mergingReport.loggingRecords.single().message)
                .containsMatch(
                    "Attribute application@localeConfig value=\\(@xml/locales_config_from_overlay\\) from (?s).*" +
                        "is also present at(?s).*" +
                        "value=\\(@xml/locales_config\\)")
        } finally {
            assertThat(appFile.delete()).named("appFile was deleted").isTrue()
            assertThat(overlayFile.delete()).named("overlayFile was deleted").isTrue()
        }
    }

    @Test
    fun `Value from overlay can override app value via tools_replace`() {
        val mockLog = MockLog()
        val appManifest =
                manifest("com.example.test.locales") {
                    application(android_label = "Blerg") {
                        attr_android_localeConfig("@xml/locales_config")
                    }
                }
        val overlayManifest =
                manifest("com.example.test.locales", includeXmlnsTools = true) {
                    application(android_label = "Blerg") {
                        attr_tools_replace("android:localeConfig")
                        attr_android_localeConfig("@xml/locales_config_from_overlay")
                    }
                }
        val appFile = TestUtils.inputAsFile("appFile", appManifest.toString())
        val overlayFile = TestUtils.inputAsFile("overlayFile", overlayManifest.toString())

        try {
            val mergingReport =
                    ManifestMerger2.newMerger(appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .addFlavorAndBuildTypeManifest(overlayFile)
                            .merge()
            assertThat(mergingReport.result).isEqualTo(MergingReport.Result.SUCCESS)
            val document =
                    parse(mergingReport.getMergedDocument(MergingReport.MergedManifestKind.MERGED))

            assertThat(
                    document.getElementsByTagName("application")[0].getNamedAttributeAndroidNS(
                            "localeConfig")
                            .nodeValue)
                    .isEqualTo("@xml/locales_config_from_overlay")
        } finally {
            assertThat(appFile.delete()).named("appFile was deleted").isTrue()
            assertThat(overlayFile.delete()).named("overlayFile was deleted").isTrue()
        }
    }

    @Test
    fun `Value from overlay is used if app does not provide one`() {
        val mockLog = MockLog()
        val appManifest =
            manifest("com.example.test.locales") {
                application(android_label = "Blerg") {
                    // No android:localeConfig set here
                }
            }
        val overlayManifest =
            manifest("com.example.test.locales") {
                application(android_label = "Blerg") {
                    attr_android_localeConfig("@xml/locales_config_from_overlay")
                }
            }
        val appFile = TestUtils.inputAsFile("appFile", appManifest.toString())
        val overlayFile = TestUtils.inputAsFile("overlayFile", overlayManifest.toString())

        try {
            val mergingReport =
                ManifestMerger2.newMerger(appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                    .addFlavorAndBuildTypeManifest(overlayFile)
                    .merge()
            assertThat(mergingReport.result).isEqualTo(MergingReport.Result.SUCCESS)
            val document =
                parse(mergingReport.getMergedDocument(MergingReport.MergedManifestKind.MERGED))

            assertThat(
                    document.getElementsByTagName("application")[0].getNamedAttributeAndroidNS(
                            "localeConfig")
                        .nodeValue)
                .isEqualTo("@xml/locales_config_from_overlay")
        } finally {
            assertThat(appFile.delete()).named("appFile was deleted").isTrue()
            assertThat(overlayFile.delete()).named("overlayFile was deleted").isTrue()
        }
    }
}

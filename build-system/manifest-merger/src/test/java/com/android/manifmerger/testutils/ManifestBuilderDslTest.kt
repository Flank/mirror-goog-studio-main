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

package com.android.manifmerger.testutils

import com.android.SdkConstants
import com.android.manifmerger.ManifestMerger2
import com.android.manifmerger.ManifestMerger2SmallTest.parse
import com.android.manifmerger.MergingReport
import com.android.manifmerger.MergingReport.MergedManifestKind
import com.android.manifmerger.TestUtils
import com.android.testutils.MockLog
import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.Test
import org.w3c.dom.Node
import org.w3c.dom.NodeList

class ManifestBuilderDslTest {

    /* Verify basic functionality of builder DSL -- indentation, attribute formatting, etc. */
    @Test
    fun testBasicXmlGeneration() {
        val manifest =
            manifest("com.example.test") {
                application(android_label = "Blerg") {
                    // no android:exported attribute, despite specifying an intent filter
                    //   expected to cause an error during merging
                    activity(android_name = ".MyActivity") {
                        intentFilter { action(android_name = "android.intent.action.MAIN") {} }
                    }
                }
            }

        assertThat(manifest.toString())
            .isEqualTo(
                """
                    |<manifest
                    |        xmlns:android="http://schemas.android.com/apk/res/android"
                    |        package="com.example.test">
                    |    <application android:label="Blerg">
                    |        <activity android:name=".MyActivity">
                    |            <intent-filter>
                    |                <action android:name="android.intent.action.MAIN" />
                    |            </intent-filter>
                    |        </activity>
                    |    </application>
                    |</manifest>
                    |
                """.trimMargin())
    }

    /*
     * Proof of Concept #1: copied a test from ManifestMerger2SmallTest, but
     * specified manifest XML using DSL rather than string literals.
     * Test was easy to write and the Kotlin formatter keeps it looking nice
     */
    @Test
    @Throws(Exception::class)
    fun testAndroidExportedAttributeWithIntentFilterInActivity() {
        val mockLog = MockLog()
        val appManifest =
            manifest("com.example.myapplication") {
                usesSdk(android_minSdkVersion = "31", android_targetSdkVersion = "31") {}
                application("@string/app_name") {
                    // This Activity has an intentFilter, but no "android:exported" attribute
                    activity(android_name = ".MainActivity") {
                        intentFilter {
                            action(android_name = "android.intent.action.MAIN") {}
                            category(android_name = "android.intent.category.LAUNCHER") {}
                        }
                    }
                }
            }

        val appFile = TestUtils.inputAsFile("appFile", appManifest.toString())
        try {
            val mergingReport =
                ManifestMerger2.newMerger(appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                    .merge()
            assertThat(mergingReport.result).isEqualTo(MergingReport.Result.ERROR)
            assertThat(mergingReport.getMergedDocument(MergedManifestKind.MERGED)).isNull()
            val loggingRecordsString = mergingReport.loggingRecords.toString()
            assertThat(loggingRecordsString)
                .contains(
                    "android:exported needs to be explicitly specified for element <activity#com.example.myapplication.MainActivity>.")

            // FIXME problematic assertion; depends on implementation details of XML builder library
            assertThat(loggingRecordsString).contains(".xml:6:9-11:20 Error")
        } finally {
            assertThat(appFile.delete()).named("appFile was deleted").isTrue()
        }
    }

    /* Proof of Concept #2 */
    @Test
    fun testToolsAnnotationRemoval() {
        val mockLog = MockLog()
        val inputManifest =
            manifest("com.example.lib3", includeXmlnsTools = true) {
                application(android_label = "@string/lib_name") { attr_tools_replace("label") }
            }
        val tmpFile = TestUtils.inputAsFile("testToolsAnnotationRemoval", inputManifest.toString())
        Assert.assertTrue(tmpFile.exists())

        try {
            val mergingReport =
                ManifestMerger2.newMerger(tmpFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                    .withFeatures(ManifestMerger2.Invoker.Feature.REMOVE_TOOLS_DECLARATIONS)
                    .merge()
            Assert.assertEquals(MergingReport.Result.WARNING, mergingReport.result)
            // ensure tools annotation removal.
            val xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED))
            val applications = xmlDocument.getElementsByTagName(SdkConstants.TAG_APPLICATION)
            Assert.assertTrue(applications.length == 1)
            val replace =
                applications.item(0).attributes.getNamedItemNS(SdkConstants.TOOLS_URI, "replace")
            Assert.assertNull(replace)
        } finally {
            Assert.assertTrue(tmpFile.delete())
        }
    }

    /*
    * Proof of Concept #3
    * Note: Re-wrote all assertions for readability
    */
    @Test
    @Throws(java.lang.Exception::class)
    fun testNoFqcnsExtraction() {
        val inputManifest =
            manifest("com.foo.example") {
                application(android_label = null, android_name = ".applicationOne") {
                    attr_android_backupAgent(".myBackupAgent")
                    activity(android_name = "activityOne") {}
                    activity(android_name = "com.foo.bar.example.activityTwo") {}
                    activity(android_name = "com.foo.example.activityThree") {}
                }
            }
        val inputFile = TestUtils.inputAsFile("testFcqnsExtraction", inputManifest.toString())
        val mockLog = MockLog()
        val mergingReport =
            ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                .merge()
        Assert.assertTrue(mergingReport.result.isSuccess)
        val xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED))
        assertThat(
                xmlDocument
                    .getElementsByTagName("activity")
                    .asSequence()
                    .map { it.getNamedAttributeAndroidNS("name").nodeValue }
                    .toList())
            .containsExactly(
                "com.foo.example.activityOne",
                "com.foo.bar.example.activityTwo",
                "com.foo.example.activityThree")
            .inOrder()
        val applicationNode = xmlDocument.getElementsByTagName("application").item(0)
        assertThat(applicationNode.getNamedAttributeAndroidNS("name").nodeValue)
            .isEqualTo("com.foo.example.applicationOne")
        assertThat(applicationNode.getNamedAttributeAndroidNS("backupAgent").nodeValue)
            .isEqualTo("com.foo.example.myBackupAgent")
    }
}

private fun Node.getNamedAttributeAndroidNS(localName: String) =
    attributes.getNamedItemNS("http://schemas.android.com/apk/res/android", localName)

private fun NodeList.asSequence(): Sequence<Node> {
    var i = 0
    return generateSequence { if (i < length) item(i++) else null }
}

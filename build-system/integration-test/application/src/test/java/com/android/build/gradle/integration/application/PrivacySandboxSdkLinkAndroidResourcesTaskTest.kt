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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.DEFAULT_COMPILE_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.truth.ApkSubject
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.builder.core.ToolsRevisionUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.util.zip.ZipFile

/** Test for [PrivacySandboxSdkLinkAndroidResourcesTask] */
internal class PrivacySandboxSdkLinkAndroidResourcesTaskTest {

    @JvmField
    @Rule
    val project = createGradleProject {
        subProject(":androidLib1") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidLib1"
                minSdk = 12
            }
            addFile(
                    "src/main/res/values/strings.xml",
                    """<resources>
                <string name="string_from_androidLib1">androidLib1</string>
                <string name="package_name">pkg.name.androidLib1</string>
                <string name="permission_name">androidLib1 permission</string>
                <string name="permission_label">androidLib1 label</string>
              </resources>""")

            addFile(
                    "src/main/res/layout/layout.xml",
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                            "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                            "    android:layout_width=\"match_parent\"\n" +
                            "    android:layout_height=\"match_parent\">\n" +
                            "\n" +
                            "    <TextView\n" +
                            "        android:id=\"@+id/string_from_androidLib1\"\n" +
                            "        android:layout_width=\"match_parent\"\n" +
                            "        android:layout_height=\"wrap_content\"\n" +
                            "        android:layout_weight=\"1\"\n" +
                            "        android:text=\"TextView\" />\n" +
                            "</LinearLayout>"
            )
            addFile(
                    "src/main/AndroidManifest.xml",
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                            "package=\"@string/package_name\">\n" +
                            "          <permission\n" +
                            "          android:name = \"@string/permission_name\"\n" +
                            //"          android:label = \"@string/permission_label\"\n" +
                            "          android:protectionLevel = \"dangerous\" />" +
                            "    <application />\n" +
                            "</manifest>"
            )
        }
        subProject(":privacySdkSandbox1") {
            plugins.add(PluginType.PRIVACY_SANDBOX_SDK)
            android {
                defaultCompileSdk()
                buildToolsRevision = ToolsRevisionUtils.DEFAULT_BUILD_TOOLS_REVISION.toString()
                namespace = "com.example.privacySdkSandbox1"
                minSdk = 19
            }
            dependencies {
                include(project(":androidLib1"))
            }
            appendToBuildFile {
                """
                        android {
                            bundle {
                                packageName = "com.example.privacysandboxsdk"
                                sdkProviderClassName = "Test"
                                setVersion(1, 2, 3)
                            }
                        }
                    """.trimIndent()
            }
        }
    }

    @Test
    fun testGeneratesLinkedBundledResources() {
        project.executor().run(":privacySdkSandbox1:linkPrivacySandboxResources")
        val privacySandboxSdk = project.getSubproject("privacySdkSandbox1")
        val bundledResourcesFile = privacySandboxSdk.getIntermediateFile(
                PrivacySandboxSdkInternalArtifactType.LINKED_MERGE_RES_FOR_ASB.getFolderName(),
                "single",
                "bundled-res.ap_")
        assertThat(bundledResourcesFile.exists()).isTrue()
        ZipFile(bundledResourcesFile).use { zip ->
            val entries = zip.entries().toList().map { it.name }
            assertThat(
                    entries
            ).containsExactly(
                    "AndroidManifest.xml",
                    "res/layout/layout.xml",
                    "resources.pb"
            )
        }
    }
}

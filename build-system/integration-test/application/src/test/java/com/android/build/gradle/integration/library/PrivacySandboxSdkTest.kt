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

package com.android.build.gradle.integration.library

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProjectBuilder
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.apk.Apk
import com.android.testutils.apk.Dex
import com.android.testutils.apk.Zip
import com.android.testutils.truth.ZipFileSubject
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Objects
import kotlin.io.path.readText

/** Smoke integration tests for the privacy sandbox SDK production and consumption */
class PrivacySandboxSdkTest {

    @get:Rule
    val project = createGradleProjectBuilder {

        subProject(":android-lib1") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidLib1"
                minSdk = 12
            }
            dependencies {
                implementation("junit:junit:4.12")
            }
            addFile(
                    "src/main/res/values/strings.xml",
                    """<resources>
                <string name="string_from_android_lib_1">androidLib2</string>
              </resources>"""
            )
            addFile(
                    "src/main/java/com/example/androidlib1/Example.java",
                    // language=java
                    """
                package com.example.androidlib1;

                class Example {

                    public Example() {}

                    public void f1() {}
                }
            """.trimIndent()
            )
            subProject(":android-lib2") {
                plugins.add(PluginType.ANDROID_LIB)
                android {
                    defaultCompileSdk()
                    namespace = "com.example.androidLib2"
                    minSdk = 12
                }
                addFile(
                        "src/main/java/com/example/androidlib2/Example.java",
                        // language=java
                        """
                package com.example.androidlib2;

                class Example {

                    public Example() {}

                    public void f2() {}
                }
            """.trimIndent()
                )
            }
            subProject(":privacy-sandbox-sdk") {
                plugins.add(PluginType.PRIVACY_SANDBOX_SDK)
                android {
                    defaultCompileSdk()
                    minSdk = 12
                    namespace = "com.example.privacysandboxsdk"
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
                dependencies {
                    include(project(":android-lib1"))
                    include(project(":android-lib2"))
                }

            }
            subProject(":example-app") {
                plugins.add(PluginType.ANDROID_APP)
                android {
                    defaultCompileSdk()
                    minSdk = 12
                    namespace = "com.example.privacysandboxsdk.consumer"
                }
                dependencies {
                    implementation(project(":privacy-sandbox-sdk"))
                }
            }
        }
    }
            .addGradleProperties("${BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT.propertyName}=true")
            .create()

    private fun executor() = project.executor().withPerTestPrefsRoot(true)

    @Test
    fun testDexing() {
        val dexLocation = project.getSubproject(":privacy-sandbox-sdk")
                .getIntermediateFile("dex", "single", "classes.dex")

        executor().run(":privacy-sandbox-sdk:mergeDex")

        Dex(dexLocation).also { dex ->
            assertThat(dex.classes.keys).containsAtLeast(
                "Lcom/example/androidlib1/Example;",
                "Lcom/example/androidlib2/Example;",
            )
            assertThat(dex.classes["Lcom/example/androidlib1/Example;"]!!.methods.map { it.name }).contains("f1")
            assertThat(dex.classes["Lcom/example/androidlib2/Example;"]!!.methods.map { it.name }).contains("f2")
        }

        // Check incremental changes are handled
        TestFileUtils.searchAndReplace(
                project.getSubproject("android-lib1")
                        .file("src/main/java/com/example/androidlib1/Example.java"),
                "public void f1() {}",
                "public void g() {}"
        )

        executor().run(":privacy-sandbox-sdk:mergeDex")

        Dex(dexLocation).also { dex ->
            assertThat(dex.classes["Lcom/example/androidlib1/Example;"]!!.methods.map { it.name }).contains("g")
            assertThat(dex.classes["Lcom/example/androidlib2/Example;"]!!.methods.map { it.name }).contains("f2")
        }

    }

    @Test
    fun testAsb() {
        executor().run(":privacy-sandbox-sdk:assemble")

        val asbFile =
            project.getSubproject(":privacy-sandbox-sdk").getOutputFile("asb", "single", "privacy-sandbox-sdk.asb")

        assertThat(asbFile.exists()).isTrue()

        Zip(asbFile).use {
            assertThat(
                Objects.requireNonNull(it.getEntryAsFile(
                    "BUNDLE-METADATA/com.android.tools.build.gradle/app-metadata.properties"
                )).readText()
            ).let { metadataContent ->
                metadataContent.contains("appMetadataVersion=")
                metadataContent.contains("androidGradlePluginVersion=")
            }

            assertThat(it.getEntry("SdkBundleConfig.pb")).isNotNull()

            ZipFileSubject.assertThat(
                Objects.requireNonNull(it.getEntryAsFile("modules.resm"))
            ) { modules ->
                modules.contains("base/dex/classes.dex")
                modules.contains("base/manifest/AndroidManifest.xml")
                modules.contains("base/resources.pb")
                modules.contains("SdkModulesConfig.pb")
            }
        }
    }

    @Test
    fun testConsumption() {
        // TODO(b/235469089) expand this to verify installation also

        executor().run(
                ":example-app:buildPrivacySandboxSdkApksForDebug",
                ":example-app:assembleDebug",
        )

        val privacySandboxSdkApk = project.getSubproject(":example-app")
                .getIntermediateFile("extracted_apks_from_privacy_sandbox_sdks", "debug", "privacy-sandbox-sdk-standalone.apk")

        Apk(privacySandboxSdkApk).use {
            assertThat(it).containsClass("Lcom/example/androidlib1/Example;")
            assertThat(it).containsClass("Lcom/example/androidlib2/Example;")
        }

        project.getSubproject(":example-app").getApk(GradleTestProject.ApkType.DEBUG).use {
            assertThat(it).containsClass("Lcom/example/privacysandboxsdk/consumer/R;")
            assertThat(it).doesNotContainClass("Lcom/example/androidlib1/Example;")
        }
    }
}

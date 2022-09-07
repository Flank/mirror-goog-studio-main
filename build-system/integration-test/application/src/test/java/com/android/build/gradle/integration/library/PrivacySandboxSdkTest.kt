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
import com.android.build.gradle.integration.common.truth.ApkSubject
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.apk.Apk
import com.android.testutils.apk.Dex
import com.android.testutils.apk.Zip
import com.android.testutils.truth.PathSubject.assertThat
import com.android.testutils.truth.ZipFileSubject
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.util.Objects
import kotlin.io.path.readText

/** Smoke integration tests for the privacy sandbox SDK production and consumption */
class PrivacySandboxSdkTest {

    private val mavenRepo = MavenRepoGenerator(
            listOf(
                    MavenRepoGenerator.Library("com.externaldep:externaljar:1", "jar", TestInputsGenerator.jarWithEmptyClasses(
                            ImmutableList.of("com/externaldep/externaljar/ExternalClass")
                    ))
            )
    )

    @get:Rule
    val project = createGradleProjectBuilder {

        subProject(":android-lib1") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidlib1"
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
            addFile("src/main/resources/my_java_resource.txt", "some java resource")
            addFile("src/main/assets/asset_from_androidlib1.txt", "some asset")
            // Have an empty manifest as a regression test of b/237279793
            addFile("src/main/AndroidManifest.xml", """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                </manifest>
                """.trimIndent()
            )
        }
        subProject(":android-lib2") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidlib2"
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
            // Have an empty manifest as a regression test of b/237279793
            addFile("src/main/AndroidManifest.xml", """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                </manifest>
                """.trimIndent()
            )
        }
        subProject(":privacy-sandbox-sdk") {
            plugins.add(PluginType.PRIVACY_SANDBOX_SDK)
            android {
                defaultCompileSdk()
                minSdk = 12
            }
            appendToBuildFile {
                """
                        android {
                            bundle {
                                applicationId = "com.example.privacysandboxsdk"
                                sdkProviderClassName = "Test"
                                compatSdkProviderClassName = "Test"
                                setVersion(1, 2, 3)
                            }
                        }
                    """.trimIndent()
            }
            dependencies {
                include(project(":android-lib1"))
                include(project(":android-lib2"))
                include("com.externaldep:externaljar:1")
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
            appendToBuildFile { //language=groovy
                """
                    android {
                        defaultConfig {
                            versionCode = 1
                        }
                    }
                """.trimIndent()
            }
        }
    }
            .withAdditionalMavenRepo(mavenRepo)
            .addGradleProperties("${BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT.propertyName}=true")
            .create()

    private fun executor() = project.executor()
            .withPerTestPrefsRoot(true)
            .with(BooleanOption.ENABLE_PROFILE_JSON, true) // Regression test for b/237278679

    @Test
    fun testDexing() {
        val dexLocation = project.getSubproject(":privacy-sandbox-sdk")
                .getIntermediateFile("dex", "single", "classes.dex")

        executor().run(":privacy-sandbox-sdk:mergeDex")

        Dex(dexLocation).also { dex ->
            assertThat(dex.classes.keys).containsAtLeast(
                "Lcom/example/androidlib1/Example;",
                "Lcom/example/androidlib2/Example;",
                    "Lcom/externaldep/externaljar/ExternalClass;"
            )
            assertThat(dex.classes["Lcom/example/androidlib1/Example;"]!!.methods.map { it.name }).contains("f1")
            assertThat(dex.classes["Lcom/example/androidlib2/Example;"]!!.methods.map { it.name }).contains("f2")
            assertThat(dex.classes["Lcom/example/androidlib1/R\$string;"]!!.fields.map { it.name }).containsExactly("string_from_android_lib_1")
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
                modules.contains("base/assets/asset_from_androidlib1.txt")
                modules.contains("base/manifest/AndroidManifest.xml")
                modules.contains("base/resources.pb")
                modules.contains("base/root/my_java_resource.txt")
                modules.contains("SdkModulesConfig.pb")
            }
        }
    }

    @Test
    fun testConsumption() {
        // TODO(b/235469089) expand this to verify installation also

        // Check building the SDK itself
        executor().run(":example-app:buildPrivacySandboxSdkApksForDebug")

        val privacySandboxSdkApk = project.getSubproject(":example-app")
                .getIntermediateFile("extracted_apks_from_privacy_sandbox_sdks", "debug", "privacy-sandbox-sdk", "standalone.apk")

        Apk(privacySandboxSdkApk).use {
            assertThat(it).containsClass(ANDROID_LIB1_CLASS)
            assertThat(it).containsClass("Lcom/example/androidlib2/Example;")
            assertThat(it).containsClass("Lcom/externaldep/externaljar/ExternalClass;")
            val rPackageDex = it.secondaryDexFiles.last()
            assertThat(rPackageDex.classes.keys).containsExactly("Lcom/example/privacysandboxsdk/RPackage;")
        }

        // Check building the bundle to deploy to TiramisuPrivacySandbox
        val apkSelectConfig = project.file("apkSelectConfig.json")
        apkSelectConfig.writeText(
                """{"sdk_version":32,"codename":"TiramisuPrivacySandbox","screen_density":420,"supported_abis":["x86_64","arm64-v8a"],"supported_locales":["en"]}""")

        executor()
                .with(StringOption.IDE_APK_SELECT_CONFIG, apkSelectConfig.absolutePath)
                .run(":example-app:extractApksFromBundleForDebug")

        val extractedApks = project.getSubproject(":example-app")
                .getIntermediateFile("extracted_apks", "debug")
                .toPath()
        val baseMasterApk = extractedApks.resolve("base-master.apk")
        val baseMaster2Apk = extractedApks.resolve("base-master_2.apk")

        Apk(baseMasterApk).use {
            assertThat(it).doesNotExist()
        }
        Apk(baseMaster2Apk).use {
            assertThat(it).exists()
            assertThat(it).containsClass("Lcom/example/privacysandboxsdk/consumer/R;")
            assertThat(it).doesNotContainClass(ANDROID_LIB1_CLASS)
            val manifestContent = ApkSubject.getManifestContent(it.file).joinToString("\n")
            assertThat(manifestContent).contains(USES_SDK_LIBRARY_MANIFEST_ELEMENT)
            assertThat(manifestContent).contains(MY_PRIVACY_SANDBOX_SDK_MANIFEST_REFERENCE)
        }

        // Check building the bundle to deploy to a non-privacy sandbox device:
        apkSelectConfig.writeText(
                """{"sdk_version":32,"codename":"Tiramisu","screen_density":420,"supported_abis":["x86_64","arm64-v8a"],"supported_locales":["en"]}""")

        executor()
                .with(StringOption.IDE_APK_SELECT_CONFIG, apkSelectConfig.absolutePath)
                .run(":example-app:extractApksFromBundleForDebug")

        Apk(baseMasterApk).use {
            assertThat(it).exists()
            assertThat(it).containsClass("Lcom/example/privacysandboxsdk/consumer/R;")
            assertThat(it).doesNotContainClass(ANDROID_LIB1_CLASS)
            val manifestContent = ApkSubject.getManifestContent(it.file).joinToString("\n")
            assertThat(manifestContent).doesNotContain(USES_SDK_LIBRARY_MANIFEST_ELEMENT)
            assertThat(manifestContent).doesNotContain(MY_PRIVACY_SANDBOX_SDK_MANIFEST_REFERENCE)
        }
        Apk(baseMaster2Apk).use {
            assertThat(it).doesNotExist()
        }

        executor()
            .with(StringOption.IDE_APK_SELECT_CONFIG, apkSelectConfig.absolutePath)
            .run(":example-app:assembleDebug")
        Apk(project.getSubproject(":example-app").getApk(GradleTestProject.ApkType.DEBUG).file).use {
            assertThat(it).exists()
            val manifestContent = ApkSubject.getManifestContent(it.file)
            assertThat(manifestContent.normalizeManifestContent()).containsAtLeastElementsIn(
                listOf(
                "      E: application (line=14)",
                "          E: uses-sdk-library (line=15)",
                "            A: http://schemas.android.com/apk/res/android:name(0x01010003)=\"com.example.privacysandboxsdk\" (Raw: \"com.example.privacysandboxsdk\")",
                "            A: http://schemas.android.com/apk/res/android:certDigest(0x01010548)=\"15:D3:8B:C5:64:63:F1:BE:1E:BE:8C:FD:1F:E8:C9:AB:73:8C:5B:2F:68:2A:35:D7:54:F0:C2:7A:68:B3:3B:AF\" (Raw: \"15:D3:8B:C5:64:63:F1:BE:1E:BE:8C:FD:1F:E8:C9:AB:73:8C:5B:2F:68:2A:35:D7:54:F0:C2:7A:68:B3:3B:AF\")",
                "            A: http://schemas.android.com/apk/res/android:versionMajor(0x01010577)=10002"
                ).normalizeManifestContent()
            )
        }
    }

    private fun List<String>.normalizeManifestContent(): List<String> = map {
        certDigestPattern.replace(it, "CERT_DIGEST")
    }

    companion object {
        private val certDigestPattern = Regex("([0-9A-F]{2}:){31}[0-9A-F]{2}")
        private const val ANDROID_LIB1_CLASS = "Lcom/example/androidlib1/Example;"
        private const val USES_SDK_LIBRARY_MANIFEST_ELEMENT = "uses-sdk-library"
        private const val MY_PRIVACY_SANDBOX_SDK_MANIFEST_REFERENCE = "=\"com.example.privacysandboxsdk\""
    }
}


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

package com.android.build.gradle.integration.bundle

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.bundle.Config
import com.android.ide.common.signing.KeystoreHelper
import com.android.testutils.truth.PathSubject.assertThat
import com.android.testutils.truth.ZipFileSubject.assertThat
import com.android.tools.build.bundletool.model.AppBundle
import com.android.tools.build.bundletool.model.BundleModule
import com.android.tools.build.bundletool.model.BundleModuleName
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class AssetPackBundleTest {

    private val packageName = "com.test.assetpack.bundle"
    private val versionTag = "20210319.patch1"
    private val versionCodes = listOf(10, 20, 99034)

    private val assetFileOneContent = "This is an asset file from asset pack one."
    private val assetFileTwoContent = "This is an asset file from asset pack two."

    private val assetPackBundleTestApp = MultiModuleTestProject.builder().apply {
        val bundle = MinimalSubProject.assetPackBundle()
            .appendToBuild(
                """
                    bundle {
                      applicationId = '$packageName'
                      compileSdk = ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}

                      versionTag = '$versionTag'
                      versionCodes = $versionCodes
                      assetPacks = [':assetPackOne', ':assetPackTwo']

                      deviceTier {
                        enableSplit = true
                        defaultTier = 'medium'
                      }
                    }
                """.trimIndent()
            )

        val assetPackOne = MinimalSubProject.assetPack()
            .appendToBuild(
                """
                    assetPack {
                      packName = "assetPackOne"
                      dynamicDelivery {
                        deliveryType = "on-demand"
                        instantDeliveryType = "on-demand"
                      }
                    }
                """.trimIndent()
            )
            .withFile("src/main/assets/assetFileOne.txt", assetFileOneContent)

        val assetPackTwo = MinimalSubProject.assetPack()
            .appendToBuild(
                """
                    assetPack {
                      packName = "assetPackTwo"
                      dynamicDelivery {
                        deliveryType = "fast-follow"
                      }
                    }
                """.trimIndent()
            )
            .withFile("src/main/assets/assetFileTwo.txt", assetFileTwoContent)

        subproject(":assetPackBundle", bundle)
        subproject(":assetPackOne", assetPackOne)
        subproject(":assetPackTwo", assetPackTwo)
    }
        .build()

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestApp(assetPackBundleTestApp)
        .create()

    @get:Rule
    val tmpFile = TemporaryFolder()

    @Test
    fun `should build asset pack bundle successfully`() {
        project.executor().run(":assetPackBundle:bundle")

        val bundleFile = getResultBundle()
        assertThat(bundleFile.toPath()).exists()
        assertThat(bundleFile) {
            it.containsFileWithContent("assetPackOne/assets/assetFileOne.txt", assetFileOneContent)
            it.containsFileWithContent("assetPackTwo/assets/assetFileTwo.txt", assetFileTwoContent)
            it.contains("assetPackOne/manifest/AndroidManifest.xml")
            it.contains("assetPackTwo/manifest/AndroidManifest.xml")
            it.contains("BundleConfig.pb")
            it.doesNotContain("META-INF/KEY0.SF")
            it.doesNotContain("META-INF/KEY0.RSA")
        }

        ZipFile(bundleFile).use { zip ->
            val appBundle = AppBundle.buildFromZip(zip)
            assertThat(appBundle.bundleConfig.type).isEqualTo(
                Config.BundleConfig.BundleType.ASSET_ONLY
            )

            val splitsConfigBuilder = Config.SplitsConfig.newBuilder()
            splitsConfigBuilder
                .addSplitDimensionBuilder()
                .setValue(Config.SplitDimension.Value.DEVICE_TIER)
                .suffixStrippingBuilder
                .setEnabled(true)
                .setDefaultSuffix("medium")
            assertThat(appBundle.bundleConfig.optimizations.splitsConfig)
                .isEqualTo(splitsConfigBuilder.build())

            assertThat(appBundle.bundleConfig.assetModulesConfig).isEqualTo(
                Config.AssetModulesConfig.newBuilder()
                    .setAssetVersionTag(versionTag)
                    .addAllAppVersion(versionCodes.map { it.toLong() })
                    .build()
            )

            val moduleNames = appBundle.assetModules.keys.map { it.name }
            assertThat(moduleNames).containsExactly("assetPackOne", "assetPackTwo")

            val assetPackOneManifest =
                appBundle.assetModules[BundleModuleName.create("assetPackOne")]!!.androidManifest
            assertThat(assetPackOneManifest.moduleType).isEqualTo(
                BundleModule.ModuleType.ASSET_MODULE
            )
            assertThat(assetPackOneManifest.packageName).isEqualTo(packageName)
            assertThat(assetPackOneManifest.manifestDeliveryElement.get().hasOnDemandElement())
                .isTrue()

            val assetPackTwoManifest =
                appBundle.assetModules[BundleModuleName.create("assetPackTwo")]!!.androidManifest
            assertThat(assetPackTwoManifest.moduleType).isEqualTo(
                BundleModule.ModuleType.ASSET_MODULE
            )
            assertThat(assetPackTwoManifest.packageName).isEqualTo(packageName)
            assertThat(assetPackTwoManifest.manifestDeliveryElement.get().hasFastFollowElement())
                .isTrue()
        }
    }

    @Test
    fun `should build signed asset pack bundle successfully if signing config is provided`() {
        val storePassword = "storePassword"
        val keyPassword = "keyPassword"
        val keyAlias = "key0"

        val keyStoreFile = tmpFile.root.resolve("keystore")
        KeystoreHelper.createNewStore(
            "jks",
            keyStoreFile,
            storePassword,
            keyPassword,
            keyAlias,
            "CN=Bundle signing test",
            100
        )

        project.getSubproject("assetPackBundle").buildFile.appendText(
            """
                bundle {
                  signingConfig {
                    storeFile = file('${keyStoreFile.absolutePath.replace("\\", "\\\\")}')
                    storePassword = '$storePassword'
                    keyAlias = '$keyAlias'
                    keyPassword = '$keyPassword'
                  }
                }
            """.trimIndent()
        )

        project.executor().run(":assetPackBundle:bundle")

        val bundleFile = getResultBundle()
        assertThat(bundleFile.toPath()).exists()
        assertThat(bundleFile) {
            it.containsFileWithContent("assetPackOne/assets/assetFileOne.txt", assetFileOneContent)
            it.containsFileWithContent("assetPackTwo/assets/assetFileTwo.txt", assetFileTwoContent)
            it.contains("assetPackOne/manifest/AndroidManifest.xml")
            it.contains("assetPackTwo/manifest/AndroidManifest.xml")
            it.contains("BundleConfig.pb")
            it.contains("META-INF/KEY0.SF")
            it.contains("META-INF/KEY0.RSA")
        }
    }

    @Test
    fun `should fail if asset pack bundle is misconfigured`() {
        project.getSubproject("assetPackBundle").buildFile
            .appendText(
                """
                bundle {
                  applicationId = ""
                  versionTag = ""
                  versionCodes = []
                  assetPacks = []
                }
                """
            )

        val failure = project.executor().expectFailure().run(":assetPackBundle:bundle")
        failure.stdout.use {
            assertThat(it).contains("'applicationId' must be specified for asset pack bundle.")
            assertThat(it).contains("'versionTag' must be specified for asset pack bundle.")
            assertThat(it).contains("Asset pack bundle must target at least one version code.")
            assertThat(it).contains("Asset pack bundle must contain at least one asset pack.")
        }
    }

    @Test
    fun `should fail if requested asset pack is not available in project`() {
        project.getSubproject("assetPackBundle").buildFile
            .appendText("bundle.assetPacks += ':notAvailable'")

        val failure = project.executor().expectFailure().run(":assetPackBundle:bundle")
        failure.stderr.use {
            assertThat(it)
                .contains("Unable to find matching projects for Asset Packs: [:notAvailable]")
        }
    }

    @Test
    fun `should fail if requested compileSdk is not available`() {
        project.getSubproject("assetPackBundle").buildFile
            .appendText("bundle.compileSdk = 128")

        val failure = project.executor().expectFailure().run(":assetPackBundle:bundle")
        failure.stderr.use {
            assertThat(it)
                .contains(
                    "Could not determine the dependencies of task " +
                            "':assetPackBundle:linkManifestForAssetPacks'"
                )
            assertThat(it).contains("'android-128'")
        }
    }

    @Test
    fun `should fail if install-time asset pack is included in asset pack bundle`() {
        project.getSubproject("assetPackTwo").buildFile
            .appendText("assetPack.dynamicDelivery.deliveryType = 'install-time'")

        val failure = project.executor().expectFailure().run(":assetPackBundle:bundle")
        failure.stderr.use {
            assertThat(it)
                .contains("bundle contains an install-time asset module 'assetPackTwo'")
        }
    }

    @Test
    fun `should fail if keystore file is signing config is invalid`() {
        project.getSubproject("assetPackBundle").buildFile.appendText(
            """
                bundle {
                  signingConfig {
                    storeFile = file('./keystore.jks')
                    storePassword = ''
                    keyAlias = 'key'
                    keyPassword = ''
                  }
                }
            """.trimIndent()
        )

        val failure = project.executor().expectFailure().run(":assetPackBundle:bundle")
        failure.stderr.use {
            val expectedKeystoreFile =
                File(project.getSubproject("assetPackBundle").projectDir, "keystore.jks")
            assertThat(it)
                .contains("Keystore file '${expectedKeystoreFile.absolutePath}' not found")
        }
    }

    private fun getResultBundle(): File {
        val buildDir = project.getSubproject("assetPackBundle").buildDir
        return FileUtils.join(buildDir, "outputs", "bundle", "assetPackBundle.aab")
    }
}

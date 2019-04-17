/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal

import com.android.SdkConstants
import com.google.common.truth.Truth.assertThat

import com.android.build.gradle.internal.fixtures.FakeEvalIssueReporter
import com.android.builder.core.ToolsRevisionUtils
import com.android.builder.internal.compiler.RenderScriptProcessor
import com.android.repository.Revision
import com.android.sdklib.AndroidTargetHash
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.function.Supplier

class SdkDirectLoadingStrategyTest {

    private val PLATFORM_TOOLS_XML = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <ns2:repository
            xmlns:ns2="http://schemas.android.com/repository/android/common/01"
            xmlns:ns3="http://schemas.android.com/repository/android/generic/01"
            xmlns:ns4="http://schemas.android.com/sdk/android/repo/addon2/01"
            xmlns:ns5="http://schemas.android.com/sdk/android/repo/repository2/01"
            xmlns:ns6="http://schemas.android.com/sdk/android/repo/sys-img2/01">

            <license id="android-sdk-license" type="text">Very valid license</license>
            <localPackage path="platform-tools" obsolete="false">
                <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns3:genericDetailsType"/>
                <revision><major>28</major><minor>0</minor><micro>1</micro></revision>
                <display-name>Android SDK Platform-Tools</display-name>
                <uses-license ref="android-sdk-license"/>
            </localPackage>
        </ns2:repository>
    """.trimIndent()

    private val SUPPORT_TOOLS_XML = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <ns2:repository
            xmlns:ns2="http://schemas.android.com/repository/android/common/01"
            xmlns:ns3="http://schemas.android.com/repository/android/generic/01"
            xmlns:ns4="http://schemas.android.com/sdk/android/repo/addon2/01"
            xmlns:ns5="http://schemas.android.com/sdk/android/repo/repository2/01"
            xmlns:ns6="http://schemas.android.com/sdk/android/repo/sys-img2/01">

            <license id="android-sdk-license" type="text">Very valid license</license>
            <localPackage path="tools" obsolete="false">
                <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns3:genericDetailsType"/>
                <revision><major>26</major><minor>1</minor><micro>1</micro></revision>
                <display-name>Android SDK Tools</display-name>
                <uses-license ref="android-sdk-license"/>
                <dependencies>
                    <dependency path="patcher;v4"/>
                    <dependency path="emulator"/>
                    <dependency path="platform-tools">
                        <min-revision><major>20</major></min-revision>
                    </dependency>
                </dependencies>
            </localPackage>
        </ns2:repository>
    """.trimIndent()

    private val BUILD_TOOL_LATEST_XML = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <ns2:repository
            xmlns:ns2="http://schemas.android.com/repository/android/common/01"
            xmlns:ns3="http://schemas.android.com/repository/android/generic/01"
            xmlns:ns4="http://schemas.android.com/sdk/android/repo/addon2/01"
            xmlns:ns5="http://schemas.android.com/sdk/android/repo/repository2/01"
            xmlns:ns6="http://schemas.android.com/sdk/android/repo/sys-img2/01">

            <license id="android-sdk-license" type="text">Very valid license</license>
            <localPackage path="build-tools;${SdkConstants.CURRENT_BUILD_TOOLS_VERSION}" obsolete="false">
                <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns3:genericDetailsType"/>
                <revision>
                    <major>${ToolsRevisionUtils.MIN_BUILD_TOOLS_REV.major}</major>
                    <minor>${ToolsRevisionUtils.MIN_BUILD_TOOLS_REV.minor}</minor>
                    <micro>${ToolsRevisionUtils.MIN_BUILD_TOOLS_REV.micro}</micro>
                </revision>
                <display-name>Android SDK Build-Tools ${SdkConstants.CURRENT_BUILD_TOOLS_VERSION}</display-name>
                <uses-license ref="android-sdk-license"/>
            </localPackage>
        </ns2:repository>
    """.trimIndent()

    private val PLATFORM_28_XML = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <ns2:repository
            xmlns:ns2="http://schemas.android.com/repository/android/common/01"
            xmlns:ns3="http://schemas.android.com/repository/android/generic/01"
            xmlns:ns4="http://schemas.android.com/sdk/android/repo/addon2/01"
            xmlns:ns5="http://schemas.android.com/sdk/android/repo/repository2/01"
            xmlns:ns6="http://schemas.android.com/sdk/android/repo/sys-img2/01">

            <license id="android-sdk-license" type="text">Very valid license</license>
            <localPackage path="platforms;android-28" obsolete="false">
                <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns5:platformDetailsType">
                    <api-level>28</api-level>
                    <codename></codename>
                    <layoutlib api="15"/>
                </type-details>
                <revision><major>6</major></revision>
                <display-name>Android SDK Platform 28</display-name>
                <uses-license ref="android-sdk-license"/>
            </localPackage>
        </ns2:repository>
    """.trimIndent()

    private val PLATFORM_28_OPTIONAL_JSON = """
        [
          {
            "name": "org.apache.http.legacy",
            "jar": "org.apache.http.legacy.jar",
            "manifest": false
          },
          {
            "name": "android.test.mock",
            "jar": "android.test.mock.jar",
            "manifest": false
          },
          {
            "name": "android.test.base",
            "jar": "android.test.base.jar",
            "manifest": false
          },
          {
            "name": "android.test.runner",
            "jar": "android.test.runner.jar",
            "manifest": false
          }
        ]
    """.trimIndent()

    @get:Rule
    val testFolder = TemporaryFolder()

    @Before
    fun setup() {
        SdkDirectLoadingStrategy.clearCaches()
    }

    @After
    fun tearDown() {
        SdkDirectLoadingStrategy.clearCaches()
    }

    @Test
    fun load_ok() {
        val sdkDirectory = configureSdkDirectory()
        val directLoader = getDirectLoader(sdkDirectory)

        assertThat(directLoader.loadedSuccessfully()).isTrue()
        assertAllComponentsArePresent(directLoader)
    }

    @Test
    fun load_cacheHit() {
        val sdkDirectory = configureSdkDirectory()

        // Add it to the cache.
        getDirectLoader(sdkDirectory)

        // We delete the sdk files to make sure it's not going to the disk again.
        testFolder.root.resolve("platforms").deleteRecursively()

        // We request it again, should be fetched direct from the cache.
        val directLoader = getDirectLoader(sdkDirectory)
        assertThat(directLoader.loadedSuccessfully()).isTrue()
        assertAllComponentsArePresent(directLoader)
    }

    @Test
    fun load_badSdkDirectory() {
        val directLoader = getDirectLoader(testFolder.root)

        assertThat(directLoader.loadedSuccessfully()).isFalse()
        assertAllComponentsAreNull(directLoader)
    }

    @Test
    fun load_missingPlatform() {
        val sdkDirectory = configureSdkDirectory(configurePlatform = false)
        val directLoader = getDirectLoader(sdkDirectory)

        assertThat(directLoader.loadedSuccessfully()).isFalse()
        assertAllComponentsAreNull(directLoader)
    }

    @Test
    fun load_wrongPlatform() {
        // We put the 28 API in the "android-27" directory and request it.
        val sdkDirectory = configureSdkDirectory(platformDirectory = "android-27")
        val directLoader = getDirectLoader(sdkDirectory, platformHash = "android-27")

        assertThat(directLoader.loadedSuccessfully()).isFalse()
        assertAllComponentsAreNull(directLoader)
    }

    @Test
    fun load_missingBuildTools() {
        val sdkDirectory = configureSdkDirectory(configureBuildTools = false)
        val directLoader = getDirectLoader(sdkDirectory)

        assertThat(directLoader.loadedSuccessfully()).isFalse()
        assertAllComponentsAreNull(directLoader)
    }

    @Test
    fun load_oldBuildTools() {
        // Even if we request an older version, it should bump to the one in
        // ToolsRevisionUtils.DEFAULT_BUILD_TOOLS_REVISION and look for it.
        val sdkDirectory = configureSdkDirectory()
        val directLoader = getDirectLoader(sdkDirectory, buildTools = "27.0.0")

        assertThat(directLoader.loadedSuccessfully()).isTrue()
        assertAllComponentsArePresent(directLoader)
    }

    @Test
    fun load_missingPlatformTools() {
        val sdkDirectory = configureSdkDirectory(configurePlatformTools = false)
        val directLoader = getDirectLoader(sdkDirectory)

        assertThat(directLoader.loadedSuccessfully()).isFalse()
        assertAllComponentsAreNull(directLoader)
    }

    @Test
    fun load_missingSupportTools() {
        val sdkDirectory = configureSdkDirectory(configureSupportTools = false)
        val directLoader = getDirectLoader(sdkDirectory)

        assertThat(directLoader.loadedSuccessfully()).isFalse()
        assertAllComponentsAreNull(directLoader)
    }

    private fun getDirectLoader(
        sdkDir: File,
        platformHash: String = "android-28",
        buildTools: String = SdkConstants.CURRENT_BUILD_TOOLS_VERSION): SdkDirectLoadingStrategy {
        return SdkDirectLoadingStrategy(
            sdkDir,
            Supplier { platformHash },
            Supplier { Revision.parseRevision(buildTools) },
            true,
            FakeEvalIssueReporter(false))
    }

    // Configures the SDK Test directory and return the root of the SDK dir.
    private fun configureSdkDirectory(
        configurePlatform: Boolean = true,
        platformDirectory: String = "android-28",
        configureBuildTools: Boolean = true,
        buildToolsDirectory: String = SdkConstants.CURRENT_BUILD_TOOLS_VERSION,
        configurePlatformTools: Boolean = true,
        configureSupportTools: Boolean = true): File {

        val sdkDir = testFolder.newFolder("sdk")

        if (configurePlatform) {
            val platformRoot = sdkDir.resolve("platforms/$platformDirectory")
            platformRoot.mkdirs()

            val platformPackageXml = platformRoot.resolve("package.xml")
            platformPackageXml.createNewFile()
            platformPackageXml.writeText(PLATFORM_28_XML, Charsets.UTF_8)

            val optionalDir = platformRoot.resolve("optional")
            optionalDir.mkdir()
            val optionalJson = optionalDir.resolve("optional.json")
            optionalJson.createNewFile()
            optionalJson.writeText(PLATFORM_28_OPTIONAL_JSON)
        }

        if (configureBuildTools) {
            val buildToolsRoot = sdkDir.resolve("build-tools/$buildToolsDirectory")
            buildToolsRoot.mkdirs()

            val buildToolsPackageXml = buildToolsRoot.resolve("package.xml")
            buildToolsPackageXml.createNewFile()
            buildToolsPackageXml.writeText(BUILD_TOOL_LATEST_XML, Charsets.UTF_8)
        }

        if (configurePlatformTools) {
            val platformToolsRoot = sdkDir.resolve("platform-tools")
            platformToolsRoot.mkdirs()

            val platformToolsPackageXml = platformToolsRoot.resolve("package.xml")
            platformToolsPackageXml.createNewFile()
            platformToolsPackageXml.writeText(PLATFORM_TOOLS_XML, Charsets.UTF_8)
        }

        if (configureSupportTools) {
            val supportToolsRoot = sdkDir.resolve("tools")
            supportToolsRoot.mkdirs()

            val supportToolsPackageXml = supportToolsRoot.resolve("package.xml")
            supportToolsPackageXml.createNewFile()
            supportToolsPackageXml.writeText(SUPPORT_TOOLS_XML, Charsets.UTF_8)
        }

        return sdkDir
    }

    private fun assertAllComponentsAreNull(sdkDirectLoadingStrategy: SdkDirectLoadingStrategy) {
        assertThat(sdkDirectLoadingStrategy.getAdbExecutable()).isNull()
        assertThat(sdkDirectLoadingStrategy.getAnnotationsJar()).isNull()

        assertThat(sdkDirectLoadingStrategy.getAidlFramework()).isNull()
        assertThat(sdkDirectLoadingStrategy.getAndroidJar()).isNull()
        assertThat(sdkDirectLoadingStrategy.getAdditionalLibraries()).isNull()
        assertThat(sdkDirectLoadingStrategy.getOptionalLibraries()).isNull()
        assertThat(sdkDirectLoadingStrategy.getTargetPlatformVersion()).isNull()
        assertThat(sdkDirectLoadingStrategy.getTargetBootClasspath()).isNull()

        assertThat(sdkDirectLoadingStrategy.getBuildToolsRevision()).isNull()
        assertThat(sdkDirectLoadingStrategy.getAidlExecutable()).isNull()
        assertThat(sdkDirectLoadingStrategy.getCoreLambaStubs()).isNull()
        assertThat(sdkDirectLoadingStrategy.getSplitSelectExecutable()).isNull()

        assertThat(sdkDirectLoadingStrategy.getRenderScriptSupportJar()).isNull()
        assertThat(sdkDirectLoadingStrategy.getSupportNativeLibFolder()).isNull()
        assertThat(sdkDirectLoadingStrategy.getSupportBlasLibFolder()).isNull()
    }

    private fun assertAllComponentsArePresent(sdkDirectLoadingStrategy: SdkDirectLoadingStrategy) {
        val sdkRoot = testFolder.root.resolve("sdk")

        assertThat(sdkDirectLoadingStrategy.getAdbExecutable()).isEqualTo(
            sdkRoot.resolve("platform-tools/${SdkConstants.FN_ADB}"))

        assertThat(sdkDirectLoadingStrategy.getAnnotationsJar()).isEqualTo(
            sdkRoot.resolve("tools/support/${SdkConstants.FN_ANNOTATIONS_JAR}"))

        assertThat(sdkDirectLoadingStrategy.getAidlFramework()).isEqualTo(
            sdkRoot.resolve("platforms/android-28/${SdkConstants.FN_FRAMEWORK_AIDL}"))
        assertThat(sdkDirectLoadingStrategy.getAndroidJar()).isEqualTo(
            sdkRoot.resolve("platforms/android-28/${SdkConstants.FN_FRAMEWORK_LIBRARY}"))
        assertThat(sdkDirectLoadingStrategy.getAdditionalLibraries()).isEmpty()
        assertThat(sdkDirectLoadingStrategy.getOptionalLibraries()!!.map { it.jar })
            .containsExactlyElementsIn(getExpectedOptionalJars())
        assertThat(sdkDirectLoadingStrategy.getTargetPlatformVersion()!!).isEqualTo(
            AndroidTargetHash.getVersionFromHash("android-28"))
        assertThat(sdkDirectLoadingStrategy.getTargetBootClasspath()).containsExactly(
            sdkRoot.resolve("platforms/android-28/${SdkConstants.FN_FRAMEWORK_LIBRARY}"))

        val buildToolDirectory = sdkRoot.resolve("build-tools/28.0.3")
        assertThat(sdkDirectLoadingStrategy.getBuildToolsRevision()).isEqualTo(
            ToolsRevisionUtils.MIN_BUILD_TOOLS_REV)
        assertThat(sdkDirectLoadingStrategy.getAidlExecutable()).isEqualTo(
            buildToolDirectory.resolve(SdkConstants.FN_AIDL))
        assertThat(sdkDirectLoadingStrategy.getCoreLambaStubs()).isEqualTo(
            buildToolDirectory.resolve(SdkConstants.FN_CORE_LAMBDA_STUBS))
        assertThat(sdkDirectLoadingStrategy.getSplitSelectExecutable()).isEqualTo(
            buildToolDirectory.resolve(SdkConstants.FN_SPLIT_SELECT))

        assertThat(sdkDirectLoadingStrategy.getRenderScriptSupportJar()).isEqualTo(
            RenderScriptProcessor.getSupportJar(buildToolDirectory, true))
        assertThat(sdkDirectLoadingStrategy.getSupportNativeLibFolder()).isEqualTo(
            RenderScriptProcessor.getSupportNativeLibFolder(buildToolDirectory))
        assertThat(sdkDirectLoadingStrategy.getSupportBlasLibFolder()).isEqualTo(
            RenderScriptProcessor.getSupportBlasLibFolder(buildToolDirectory))
    }

    private fun getExpectedOptionalJars(): List<File> {
        val optionalDir = testFolder.root.resolve("sdk/platforms/android-28/optional/")
        return listOf(
            "org.apache.http.legacy.jar",
            "android.test.mock.jar",
            "android.test.base.jar",
            "android.test.runner.jar").map { optionalDir.resolve(it).absoluteFile }
    }
}
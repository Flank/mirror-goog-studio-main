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
import com.android.build.gradle.internal.fixtures.FakeSyncIssueReporter
import com.android.builder.core.ToolsRevisionUtils
import com.android.builder.internal.compiler.RenderScriptProcessor
import com.android.repository.Revision
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.BuildToolInfo
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties
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

    private val ADD_ON_XML = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <ns2:repository
            xmlns:ns2="http://schemas.android.com/repository/android/common/01"
            xmlns:ns3="http://schemas.android.com/repository/android/generic/01"
            xmlns:ns4="http://schemas.android.com/sdk/android/repo/addon2/01"
            xmlns:ns5="http://schemas.android.com/sdk/android/repo/repository2/01"
            xmlns:ns6="http://schemas.android.com/sdk/android/repo/sys-img2/01">

            <localPackage path="add-ons;addon-vendor_addon-name" obsolete="false">
                <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns4:addonDetailsType">
                    <api-level>28</api-level>
                    <vendor>
                        <id>addon-vendor</id>
                        <display>Add-On Vendor</display>
                    </vendor>
                    <tag>
                        <id>addon-name</id>
                        <display>Add-On Name</display>
                    </tag>
                    <libraries>
                        <library name="com.example.addon">
                            <description>Example Add-On.</description>
                        </library>
                    </libraries>
                </type-details>
                <revision>
                    <major>42</major>
                </revision>
                <display-name>Add-On Name</display-name>
            </localPackage>
        </ns2:repository>
    """.trimIndent()

    private fun getPlatformXml(version: Int = 28) = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <ns2:repository
            xmlns:ns2="http://schemas.android.com/repository/android/common/01"
            xmlns:ns3="http://schemas.android.com/repository/android/generic/01"
            xmlns:ns4="http://schemas.android.com/sdk/android/repo/addon2/01"
            xmlns:ns5="http://schemas.android.com/sdk/android/repo/repository2/01"
            xmlns:ns6="http://schemas.android.com/sdk/android/repo/sys-img2/01">

            <license id="android-sdk-license" type="text">Very valid license</license>
            <localPackage path="platforms;android-$version" obsolete="false">
                <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns5:platformDetailsType">
                    <api-level>$version</api-level>
                    <codename></codename>
                    <layoutlib api="15"/>
                </type-details>
                <revision><major>6</major></revision>
                <display-name>Android SDK Platform $version</display-name>
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
        SdkLocator.sdkTestDirectory = testFolder.newFolder("sdk")
    }

    @After
    fun tearDown() {
        SdkDirectLoadingStrategy.clearCaches()
        SdkLocator.sdkTestDirectory = null
        SdkLocator.resetCache()
    }

    @Test
    fun load_ok() {
        configureSdkDirectory()
        val directLoader = getDirectLoader()

        assertThat(directLoader.loadedSuccessfully()).isTrue()
        assertAllComponentsArePresent(directLoader)
    }

    @Test
    fun load_cacheHit() {
        configureSdkDirectory()

        // Add it to the cache.
        getDirectLoader()

        // We delete the sdk files to make sure it's not going to the disk again.
        testFolder.root.resolve("platforms").deleteRecursively()

        // We request it again, should be fetched direct from the cache.
        val directLoader = getDirectLoader()
        assertThat(directLoader.loadedSuccessfully()).isTrue()
        assertAllComponentsArePresent(directLoader)
    }

    @Test
    fun load_badSdkDirectory() {
        SdkLocator.sdkTestDirectory = testFolder.root.resolve("bad_sdk")
        val directLoader = getDirectLoader()

        assertThat(directLoader.loadedSuccessfully()).isFalse()
        assertAllComponentsAreNull(directLoader)
    }

    @Test
    fun load_missingPlatform() {
        configureSdkDirectory(configurePlatform = false)
        val directLoader = getDirectLoader()

        assertThat(directLoader.loadedSuccessfully()).isFalse()
        assertAllComponentsAreNull(directLoader)
    }

    @Test
    fun load_wrongPlatform() {
        // We put the 28 API in the "android-27" directory and request it.
        configureSdkDirectory(platformDirectory = "android-27")
        val directLoader = getDirectLoader(platformHash = "android-27")

        assertThat(directLoader.loadedSuccessfully()).isFalse()
        assertAllComponentsAreNull(directLoader)
    }

    @Test
    fun load_addOnSdk() {
        configureSdkDirectory()
        val directLoader = getDirectLoader(platformHash = "Add-On Vendor:Add-On Name:28")

        assertThat(directLoader.loadedSuccessfully()).isFalse()
        assertAllComponentsAreNull(directLoader)
    }

    @Test
    fun load_missingBuildTools() {
        configureSdkDirectory(configureBuildTools = false)
        val directLoader = getDirectLoader()

        assertThat(directLoader.loadedSuccessfully()).isFalse()
        assertAllComponentsAreNull(directLoader)
    }

    @Test
    fun load_oldBuildTools() {
        // Even if we request an older version, it should bump to the one in
        // ToolsRevisionUtils.DEFAULT_BUILD_TOOLS_REVISION and look for it.
        configureSdkDirectory()
        val directLoader = getDirectLoader(buildTools = "27.0.0")

        assertThat(directLoader.loadedSuccessfully()).isTrue()
        assertAllComponentsArePresent(directLoader)
    }

    @Test
    fun load_missingPlatformTools() {
        configureSdkDirectory(configurePlatformTools = false)
        val directLoader = getDirectLoader()

        assertThat(directLoader.loadedSuccessfully()).isFalse()
        assertAllComponentsAreNull(directLoader)
    }

    @Test
    fun load_missingSupportTools_apiGreaterThan16() {
        configureSdkDirectory(
            platformDirectory = "android-16", platformApiLevel = 16, configureSupportTools = false)
        val directLoader = getDirectLoader("android-16")

        assertThat(directLoader.loadedSuccessfully()).isTrue()
        assertAllComponentsArePresent(directLoader, "android-16")
    }

    @Test
    fun load_missingSupportTools_apiLessThan16() {
        configureSdkDirectory(
            platformDirectory = "android-15", platformApiLevel = 15, configureSupportTools = false)
        val directLoader = getDirectLoader("android-15")

        assertThat(directLoader.loadedSuccessfully()).isFalse()
        assertAllComponentsAreNull(directLoader)
    }

    private fun getDirectLoader(
        platformHash: String = "android-28",
        buildTools: String = SdkConstants.CURRENT_BUILD_TOOLS_VERSION): SdkDirectLoadingStrategy {
        return SdkDirectLoadingStrategy(
            SdkLocationSourceSet(testFolder.root, Properties(), Properties(), Properties()),
            Supplier { platformHash },
            Supplier { Revision.parseRevision(buildTools) },
            true,
            FakeSyncIssueReporter())
    }

    // Configures the SDK Test directory and return the root of the SDK dir.
    private fun configureSdkDirectory(
        configurePlatform: Boolean = true,
        platformDirectory: String = "android-28",
        platformApiLevel: Int = 28,
        configureBuildTools: Boolean = true,
        buildToolsDirectory: String = SdkConstants.CURRENT_BUILD_TOOLS_VERSION,
        configurePlatformTools: Boolean = true,
        configureSupportTools: Boolean = true,
        configureTestAddOn: Boolean = true) {

        val sdkDir = SdkLocator.sdkTestDirectory!!

        if (configurePlatform) {
            val platformRoot = sdkDir.resolve("platforms/$platformDirectory")
            platformRoot.mkdirs()

            val platformPackageXml = platformRoot.resolve("package.xml")
            platformPackageXml.createNewFile()
            platformPackageXml.writeText(getPlatformXml(platformApiLevel), Charsets.UTF_8)

            val optionalDir = platformRoot.resolve("optional")
            optionalDir.mkdir()
            val optionalJson = optionalDir.resolve("optional.json")
            optionalJson.createNewFile()
            optionalJson.writeText(PLATFORM_28_OPTIONAL_JSON)
        }

        if (configureBuildTools) {
            val buildToolsRoot = sdkDir.resolve("build-tools/$buildToolsDirectory")
            buildToolsRoot.mkdirs()

            val buildToolInfo = BuildToolInfo.fromStandardDirectoryLayout(
                ToolsRevisionUtils.DEFAULT_BUILD_TOOLS_REVISION, buildToolsRoot)
            for (id in BuildToolInfo.PathId.values()) {
                if (!id.isPresentIn(ToolsRevisionUtils.DEFAULT_BUILD_TOOLS_REVISION)) {
                    continue
                }
                val buildToolComponent = File(buildToolInfo.getPath(id))
                buildToolComponent.parentFile.mkdirs()
                buildToolComponent.createNewFile()
            }

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

        if (configureTestAddOn) {
            val testAddOnRoot = sdkDir.resolve("add-ons/addon-vendor_addon-name")
            testAddOnRoot.mkdirs()

            val testAddOnPackageXml = testAddOnRoot.resolve("package.xml")
            testAddOnPackageXml.createNewFile()
            testAddOnPackageXml.writeText(ADD_ON_XML)
        }
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

    private fun assertAllComponentsArePresent(sdkDirectLoadingStrategy: SdkDirectLoadingStrategy, platformHash: String = "android-28") {
        val sdkRoot = testFolder.root.resolve("sdk")

        assertThat(sdkDirectLoadingStrategy.getAdbExecutable()).isEqualTo(
            sdkRoot.resolve("platform-tools/${SdkConstants.FN_ADB}"))

        assertThat(sdkDirectLoadingStrategy.getAnnotationsJar()).isEqualTo(
            sdkRoot.resolve("tools/support/${SdkConstants.FN_ANNOTATIONS_JAR}"))

        assertThat(sdkDirectLoadingStrategy.getAidlFramework()).isEqualTo(
            sdkRoot.resolve("platforms/$platformHash/${SdkConstants.FN_FRAMEWORK_AIDL}"))
        assertThat(sdkDirectLoadingStrategy.getAndroidJar()).isEqualTo(
            sdkRoot.resolve("platforms/$platformHash/${SdkConstants.FN_FRAMEWORK_LIBRARY}"))
        assertThat(sdkDirectLoadingStrategy.getAdditionalLibraries()).isEmpty()
        assertThat(sdkDirectLoadingStrategy.getOptionalLibraries()!!.map { it.jar })
            .containsExactlyElementsIn(getExpectedOptionalJars(platformHash))
        assertThat(sdkDirectLoadingStrategy.getTargetPlatformVersion()!!).isEqualTo(
            AndroidTargetHash.getVersionFromHash(platformHash))
        assertThat(sdkDirectLoadingStrategy.getTargetBootClasspath()).containsExactly(
            sdkRoot.resolve("platforms/$platformHash/${SdkConstants.FN_FRAMEWORK_LIBRARY}"))

        val buildToolDirectory = sdkRoot.resolve("build-tools/29.0.2")
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

    private fun getExpectedOptionalJars(platformHash: String): List<File> {
        val optionalDir = testFolder.root.resolve("sdk/platforms/$platformHash/optional/")
        return listOf(
            "org.apache.http.legacy.jar",
            "android.test.mock.jar",
            "android.test.base.jar",
            "android.test.runner.jar").map { optionalDir.resolve(it).absoluteFile }
    }
}
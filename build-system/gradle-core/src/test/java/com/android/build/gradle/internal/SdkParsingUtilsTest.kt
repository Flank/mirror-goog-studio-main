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

import com.android.build.gradle.internal.fixtures.FakeSyncIssueReporter
import com.android.repository.Revision
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.AndroidVersion
import com.android.sdklib.BuildToolInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SdkParsingUtilsTest {

    private val BUILD_TOOL_28_0_2_XML = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <ns2:repository
            xmlns:ns2="http://schemas.android.com/repository/android/common/01"
            xmlns:ns3="http://schemas.android.com/repository/android/generic/01"
            xmlns:ns4="http://schemas.android.com/sdk/android/repo/addon2/01"
            xmlns:ns5="http://schemas.android.com/sdk/android/repo/repository2/01"
            xmlns:ns6="http://schemas.android.com/sdk/android/repo/sys-img2/01">

            <license id="android-sdk-license" type="text">Very valid license</license>
            <localPackage path="build-tools;28.0.2" obsolete="false">
                <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns3:genericDetailsType"/>
                <revision><major>28</major><minor>0</minor><micro>2</micro></revision>
                <display-name>Android SDK Build-Tools 28.0.2</display-name>
                <uses-license ref="android-sdk-license"/>
            </localPackage>
        </ns2:repository>
    """.trimIndent()

    private val BUILD_TOOL_28_0_3_XML = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <ns2:repository
            xmlns:ns2="http://schemas.android.com/repository/android/common/01"
            xmlns:ns3="http://schemas.android.com/repository/android/generic/01"
            xmlns:ns4="http://schemas.android.com/sdk/android/repo/addon2/01"
            xmlns:ns5="http://schemas.android.com/sdk/android/repo/repository2/01"
            xmlns:ns6="http://schemas.android.com/sdk/android/repo/sys-img2/01">

            <license id="android-sdk-license" type="text">Very valid license</license>
            <localPackage path="build-tools;28.0.3" obsolete="false">
                <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns3:genericDetailsType"/>
                <revision><major>28</major><minor>0</minor><micro>3</micro></revision>
                <display-name>Android SDK Build-Tools 28.0.3</display-name>
                <uses-license ref="android-sdk-license"/>
            </localPackage>
        </ns2:repository>
    """.trimIndent()

    private val ADDON_XML = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <ns2:repository
            xmlns:ns2="http://schemas.android.com/repository/android/common/01"
            xmlns:ns3="http://schemas.android.com/repository/android/generic/01"
            xmlns:ns4="http://schemas.android.com/sdk/android/repo/addon2/01"
            xmlns:ns5="http://schemas.android.com/sdk/android/repo/repository2/01"
            xmlns:ns6="http://schemas.android.com/sdk/android/repo/sys-img2/01">

            <license id="android-sdk-license" type="text">Very valid license</license>
            <localPackage path="add-ons;addon-google_apis-google-24" obsolete="false">
                <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns4:addonDetailsType">
                    <api-level>24</api-level>
                    <codename></codename>
                    <vendor><id>google</id><display>Google Inc.</display></vendor>
                    <tag><id>google_apis</id><display>Google APIs</display></tag>
                    <libraries>
                        <library localJarPath="maps.jar" name="com.google.android.maps">
                            <description>API for Google Maps</description>
                        </library>
                        <library localJarPath="usb.jar" name="com.android.future.usb.accessory">
                            <description>API for USB Accessories</description>
                        </library>
                        <library localJarPath="effects.jar" name="com.google.android.media.effects">
                            <description>Collection of video effects</description>
                        </library>
                    </libraries>
                </type-details>
                <revision><major>1</major></revision>
                <display-name>Google APIs</display-name>
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

    @Test
    fun buildBuildTools_ok() {
        val sdkDir = testFolder.newFolder("sdk")
        val buildToolDir = testFolder.newFolder("sdk", "build-tools", "28.0.3")
        val revision = Revision.parseRevision("28.0.3")
        populateBuildToolDirectory(buildToolDir, revision)

        val packageXml = buildToolDir.resolve("package.xml")
        packageXml.createNewFile()
        packageXml.writeText(BUILD_TOOL_28_0_3_XML, Charsets.UTF_8)

        val buildTool = buildBuildTools(sdkDir, revision)
        assertThat(buildTool).isNotNull()
        assertThat(buildTool!!.revision).isEqualTo(revision)
    }

    @Test
    fun buildBuildTools_wrongBuildTool() {
        val sdkDir = testFolder.newFolder("sdk")
        val buildToolDir = testFolder.newFolder("sdk", "build-tools", "28.0.3")
        populateBuildToolDirectory(buildToolDir, Revision.parseRevision("28.0.2"))

        val packageXml = buildToolDir.resolve("package.xml")
        packageXml.createNewFile()
        packageXml.writeText(BUILD_TOOL_28_0_2_XML, Charsets.UTF_8)

        val buildTool = buildBuildTools(sdkDir, Revision.parseRevision("28.0.3"))
        assertThat(buildTool).isNull()
    }

    @Test
    fun buildBuildTools_corruptBuildTool() {
        val sdkDir = testFolder.newFolder("sdk")
        val buildToolDir = testFolder.newFolder("sdk", "build-tools", "28.0.3")
        val revision = Revision.parseRevision("28.0.3")
        populateBuildToolDirectory(
            buildToolDir, revision, setOf(BuildToolInfo.PathId.CORE_LAMBDA_STUBS))

        val packageXml = buildToolDir.resolve("package.xml")
        packageXml.createNewFile()
        packageXml.writeText(BUILD_TOOL_28_0_3_XML, Charsets.UTF_8)

        val buildTool = buildBuildTools(sdkDir, revision)
        assertThat(buildTool).isNull()
    }

    @Test
    fun parsePackage_ok() {
        val xml = testFolder.newFile("package.xml")
        xml.writeText(BUILD_TOOL_28_0_3_XML, Charsets.UTF_8)

        val localPackage = parsePackage(xml)

        assertThat(localPackage).isNotNull()
        assertThat(localPackage!!.version).isEqualTo(Revision.parseRevision("28.0.3"))
        assertThat(localPackage!!.displayName).isEqualTo("Android SDK Build-Tools 28.0.3")
    }

    @Test
    fun parseAdditionalLibraries_ok() {
        val xml = testFolder.newFile("package.xml")
        xml.writeText(ADDON_XML, Charsets.UTF_8)

        val localPackage = parsePackage(xml)
        assertThat(localPackage).isNotNull()

        val expectedJars = listOf("maps.jar", "usb.jar", "effects.jar")
            .map { testFolder.root.resolve("libs").resolve(it).toPath() }

        val optionalLibraries = parseAdditionalLibraries(localPackage!!).map { it.jar }
        assertThat(optionalLibraries).containsExactlyElementsIn(expectedJars)
    }

    @Test
    fun parseAdditionalLibraries_nonAddon() {
        val xml = testFolder.newFile("package.xml")
        xml.writeText(PLATFORM_28_XML, Charsets.UTF_8)

        val localPackage = parsePackage(xml)
        assertThat(localPackage).isNotNull()

        val optionalLibraries = parseAdditionalLibraries(localPackage!!)
        assertThat(optionalLibraries).isEmpty()
    }

    @Test
    fun parseOptionalLibraries_ok() {
        val xml = testFolder.newFile("package.xml")
        xml.writeText(PLATFORM_28_XML, Charsets.UTF_8)

        val optionalDir = testFolder.newFolder("optional")
        val optionalJson = optionalDir.resolve("optional.json")
        optionalJson.createNewFile()
        optionalJson.writeText(PLATFORM_28_OPTIONAL_JSON, Charsets.UTF_8)

        val localPackage = parsePackage(xml)
        assertThat(localPackage).isNotNull()

        val expectedJars = listOf(
            "org.apache.http.legacy.jar",
            "android.test.mock.jar",
            "android.test.base.jar",
            "android.test.runner.jar").map { optionalDir.resolve(it).toPath() }

        val optionalLibraries = parseOptionalLibraries(localPackage!!).map { it.jar }
        assertThat(optionalLibraries).containsExactlyElementsIn(expectedJars)
    }

    @Test
    fun parseOptionalLibraries_missingOptionalJson() {
        val xml = testFolder.newFile("package.xml")
        xml.writeText(PLATFORM_28_XML, Charsets.UTF_8)

        val localPackage = parsePackage(xml)
        assertThat(localPackage).isNotNull()

        val optionalLibraries = parseOptionalLibraries(localPackage!!)
        assertThat(optionalLibraries).isEmpty()
    }

    private fun populateBuildToolDirectory(buildToolDir: File, buildToolRevision: Revision, skipSet: Set<BuildToolInfo.PathId> = emptySet()) {
        val buildToolInfo = BuildToolInfo.fromStandardDirectoryLayout(buildToolRevision, buildToolDir.toPath())
        for (id in BuildToolInfo.PathId.values()) {
            if (!id.isPresentIn(buildToolRevision) || skipSet.contains(id)) {
                continue
            }
            val buildToolComponent = File(buildToolInfo.getPath(id))
            buildToolComponent.parentFile.mkdirs()
            buildToolComponent.createNewFile()
        }
    }

    @Test
    fun `don't warn if compile sdk is not too new`() {
        val issueReporter = FakeSyncIssueReporter(throwOnError = true)
        warnIfCompileSdkTooNew(
            version = AndroidVersion(30),
            issueReporter = issueReporter,
            maxVersion = AndroidVersion(30),
            androidGradlePluginVersion = "7.0.0-beta01"
        )
        assertThat(issueReporter.messages).isEmpty()
    }

    @Test
    fun `warn when using a newer preview version`() {
        val issueReporter = FakeSyncIssueReporter(throwOnError = true)
        warnIfCompileSdkTooNew(
            version = AndroidVersion(31),
            issueReporter = issueReporter, maxVersion = AndroidVersion(30, "S"),
            androidGradlePluginVersion = "7.0.0-beta01"
        )
        assertThat(issueReporter.messages).containsExactly(
            """
                We recommend using a newer Android Gradle plugin to use compileSdk = 31

                This Android Gradle plugin (7.0.0-beta01) was tested up to compileSdk = 30 (and compileSdkPreview = "S")

                This warning can be suppressed by adding
                    android.suppressUnsupportedCompileSdk=31
                to this project's gradle.properties

                The build will continue, but you are strongly encouraged to update your project to
                use a newer Android Gradle Plugin that has been tested with compileSdk = 31
        """.trimIndent()
        )
    }

    @Test
    fun `warn when using a newer stable version`() {
        val issueReporter = FakeSyncIssueReporter(throwOnError = true)
        warnIfCompileSdkTooNew(
            version = AndroidVersion(30, "S"),
            issueReporter = issueReporter,
            maxVersion = AndroidVersion(30),
            androidGradlePluginVersion = "7.0.0-beta01"
        )
        assertThat(issueReporter.errors).isEmpty()
        assertThat(issueReporter.messages).containsExactly(
            """
                We recommend using a newer Android Gradle plugin to use compileSdkPreview = "S"

                This Android Gradle plugin (7.0.0-beta01) was tested up to compileSdk = 30

                This warning can be suppressed by adding
                    android.suppressUnsupportedCompileSdk=S
                to this project's gradle.properties

                The build will continue, but you are strongly encouraged to update your project to
                use a newer Android Gradle Plugin that has been tested with compileSdkPreview = "S"
        """.trimIndent())
    }

    @Test
    fun `suppress warning`() {
        val issueReporter = FakeSyncIssueReporter(throwOnError = true)
        warnIfCompileSdkTooNew(
            version = AndroidVersion(31),
            issueReporter = issueReporter,
            maxVersion = AndroidVersion(30),
            androidGradlePluginVersion = "7.0.0-beta01",
            suppressWarningIfTooNewForVersions = ",,,S,31,",
        )
        assertThat(issueReporter.messages).isEmpty()
    }

    @Test
    fun `suppress warning non matching version`() {
        val issueReporter = FakeSyncIssueReporter(throwOnError = true)
        warnIfCompileSdkTooNew(
            version = AndroidVersion(32),
            issueReporter = issueReporter,
            maxVersion = AndroidVersion(30),
            androidGradlePluginVersion = "7.0.0-beta01",
            suppressWarningIfTooNewForVersions = "S,31,",
        )
        assertThat(issueReporter.messages).containsExactly(
            """
                We recommend using a newer Android Gradle plugin to use compileSdk = 32

                This Android Gradle plugin (7.0.0-beta01) was tested up to compileSdk = 30

                This warning can be suppressed by updating
                    android.suppressUnsupportedCompileSdk=S,31,32
                to this project's gradle.properties

                The build will continue, but you are strongly encouraged to update your project to
                use a newer Android Gradle Plugin that has been tested with compileSdk = 32
        """.trimIndent()
        )
    }

}

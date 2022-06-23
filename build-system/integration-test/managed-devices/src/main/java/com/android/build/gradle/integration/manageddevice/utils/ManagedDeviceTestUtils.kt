package com.android.build.gradle.integration.manageddevice.utils

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.StringOption
import com.android.repository.api.RepoManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.TestUtils
import com.android.utils.FileUtils
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

private val systemImageZip = File(System.getProperty("sdk.repo.sysimage.android29.zip"))

private val emulatorZip = File(System.getProperty("sdk.repo.emulator.zip"))

private val sdkPatcherZip = File(System.getProperty("sdk.repo.sdkpatcher.zip"))

private val buildToolsZip = File(System.getProperty("sdk.repo.buildtools.zip"))

private val platform33Zip = File(System.getProperty("sdk.repo.platform.zip"))

private val platformToolsZip = File(System.getProperty("sdk.repo.platformtools.zip"))

private val sdkToolsZip = File(System.getProperty("sdk.repo.sdktools.zip"))

private val placeholderLicense = "A TOTALLY VALID LICENSE"

/**
 * Sets the given file as the sdkDir for the project, as well as sets up the directory to
 * be ready for download.
 */
fun setupSdkDir(project: GradleTestProject, sdkDir: File) {
    FileUtils.mkdirs(sdkDir)
    setupLicenses(sdkDir)

    TestFileUtils.appendToFile(
        project.localProp,
        System.lineSeparator()
            + "${SdkConstants.SDK_DIR_PROPERTY} = ${sdkDir.absolutePath.replace("\\", "\\\\")}")
}

/**
 * Sets up the licenses to allow for auto-download of Sdk components
 */
fun setupLicenses(sdkDirectory: File) {
    val licensesFolder = File(sdkDirectory, "licenses")
    FileUtils.mkdirs(licensesFolder)
    val licenseFile = File(licensesFolder, "android-sdk-license")
    val previewLicenseFile = File(licensesFolder, "android-sdk-preview-license")

    // noinspection SpellCheckingInspection SHAs.
    val licensesHash =
        String.format(
            "8933bad161af4178b1185d1a37fbf41ea5269c55%n"
                    + "d56f5187479451eabf01fb78af6dfcb131a6481e%n"
                    + "24333f8a63b6825ea9c5514f83c2829b004d1fee%n"
                    + "85435445a95c234340d05367a999a69d7b46701c%n")

    val previewLicenseHash =
        String.format("84831b9409646a918e30573bab4c9c91346d8abd")

    Files.write(licenseFile.toPath(), licensesHash.toByteArray(StandardCharsets.UTF_8))
    Files.write(
        previewLicenseFile.toPath(), previewLicenseHash.toByteArray(StandardCharsets.UTF_8))
}

/**
 * Sets up a facsimile of the online android repository for the purpose of downloading the
 * system image for managed devices locally.
 *
 * @param repositoryDir the directory where the repository should be set up in.
 */
fun setupSdkRepo(repositoryDir: File) {
    FileUtils.mkdirs(repositoryDir)

    // Setup toplevel components first
    setupTopLevelRepository(repositoryDir)

    // Setup manifest for all other necessary components
    setupManifestXml(repositoryDir)

    // Setup the system image repository
    setupAOSPImageRepository(repositoryDir)
}

/**
 * Returns a valid executor for running managed device tasks.
 *
 * Running managed devices require a custom location for the avds to be created. Needs to be able
 * to download the system-image from a local repo dir. Runs on the canary channel. Runs the emulator
 * in software-rendering mode.
 *
 * @param project The project the executor to be run on.
 * @param localPrefFolder A custom .android folder to create the avds into
 * @param repositoryDir the repository directory to "download" from. See [setupSdkRepo].
 */
fun getStandardExecutor(
    project: GradleTestProject,
    userHomeFolder: File,
    localPrefFolder: File,
    repositoryDir: File,
): GradleTaskExecutor {
    return project.executor()
        .withLocalPrefsRoot()
        .withEnvironmentVariables(mapOf(
            "HOME" to userHomeFolder.absolutePath,
            "ANDROID_USER_HOME" to localPrefFolder.absolutePath
        ))
        .withoutOfflineFlag()
        .withSdkAutoDownload()
        .with(IntegerOption.ANDROID_SDK_CHANNEL, 3)
        .with(StringOption.GRADLE_MANAGED_DEVICE_EMULATOR_GPU_MODE, "swiftshader_indirect")
        .withArgument(
            "-D${AndroidSdkHandler.SDK_TEST_BASE_URL_PROPERTY}="
                    + "file:///${repositoryDir.absolutePath}/")
}

/**
 * Sets up the toplevel repository directory, which contains the dependencies of system images,
 * most notably the emulator.
 *
 * This is grouped into two steps.
 * 1. The repository XMLs, which specify the versions, urls, and channel of sdk components needed
 * by the system image.
 * 2. The actual tools located at these relative urls.
 *
 * The repository XMLs are set up manually to ensure that the dependency versions are stable. As the
 * real XML file may update the available versions for download (and therefore require the
 * underlying bazel rules to change what target is available).
 *
 * @param repositoryDir the toplevel repository directory.
 */
private fun setupTopLevelRepository(repositoryDir: File) {
    val repositoryXml = File(repositoryDir, "repository2-3.xml")
    val repositoryXmlContents = """
            <?xml version="1.0" ?>
            <sdk:sdk-repository xmlns:common="http://schemas.android.com/repository/android/common/02" xmlns:generic="http://schemas.android.com/repository/android/generic/02" xmlns:sdk="http://schemas.android.com/sdk/android/repo/repository2/02" xmlns:sdk-common="http://schemas.android.com/sdk/android/repo/common/02" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <license id="android-sdk-license" type="text">$placeholderLicense</license>
                <channel id="channel-0">stable</channel>
                <remotePackage path="platforms;android-33">
                    <type-details xsi:type="sdk:platformDetailsType">
                        <api-level>33</api-level>
                        <codename></codename>
                        <layoutlib api="15"/>
                    </type-details>
                    <revision>
                        <major>1</major>
                    </revision>
                    <display-name>Android SDK Platform 33</display-name>
                    <uses-license ref="android-sdk-license"/>
                    <channelRef ref="channel-0"/>
                    <archives>
                        <archive>
                            <!--Built on: Fri Nov 19 00:59:43 2021.-->
                            <complete>
                                <size>66108299</size>
                                <checksum type="sha1">afae86ed55d29733d50996ffed832f2d1bd75b9a</checksum>
                                <url>platform-33_r01.zip</url>
                            </complete>
                        </archive>
                    </archives>
                </remotePackage>
                <remotePackage path="build-tools;30.0.3">
                    <type-details xsi:type="generic:genericDetailsType"/>
                    <revision>
                        <major>30</major>
                        <minor>0</minor>
                        <micro>3</micro>
                    </revision>
                    <display-name>Android SDK Build-Tools 30.0.3</display-name>
                    <uses-license ref="android-sdk-license"/>
                    <dependencies>
                        <dependency path="tools"/>
                    </dependencies>
                    <channelRef ref="channel-0"/>
                    <archives>
                        <archive>
                            <!--Built on: Wed Nov 11 21:35:18 2020.-->
                            <complete>
                                <size>53134793</size>
                                <checksum type="sha1">2076ea81b5a2fc298ef7bf85d666f496b928c7f1</checksum>
                                <url>build-tools_r30.0.3-linux.zip</url>
                            </complete>
                            <host-os>linux</host-os>
                        </archive>
                    </archives>
                </remotePackage>
                <remotePackage path="patcher;v4">
                    <type-details xsi:type="generic:genericDetailsType"/>
                    <revision>
                        <major>1</major>
                    </revision>
                    <display-name>SDK Patch Applier v4</display-name>
                    <uses-license ref="android-sdk-license"/>
                    <channelRef ref="channel-0"/>
                    <archives>
                        <archive>
                            <!--Built on: Wed Apr 29 10:04:35 2020.-->
                            <complete>
                                <size>1827327</size>
                                <checksum type="sha1">046699c5e2716ae11d77e0bad814f7f33fab261e</checksum>
                                <url>3534162-studio.sdk-patcher.zip</url>
                            </complete>
                        </archive>
                    </archives>
                </remotePackage>
                <remotePackage obsolete="true" path="tools">
                    <type-details xsi:type="generic:genericDetailsType"/>
                    <revision>
                        <major>26</major>
                        <minor>1</minor>
                        <micro>1</micro>
                    </revision>
                    <display-name>Android SDK Tools</display-name>
                    <uses-license ref="android-sdk-license"/>
                    <dependencies>
                        <dependency path="patcher;v4"/>
                        <dependency path="emulator"/>
                        <dependency path="platform-tools">
                            <min-revision>
                                <major>20</major>
                            </min-revision>
                        </dependency>
                    </dependencies>
                    <channelRef ref="channel-0"/>
                    <archives>
                        <archive>
                            <!--Built on: Tue Jan 26 12:44:23 2021.-->
                            <complete>
                                <size>154582459</size>
                                <checksum type="sha1">8c7c28554a32318461802c1291d76fccfafde054</checksum>
                                <url>sdk-tools-linux-4333796.zip</url>
                            </complete>
                            <host-os>linux</host-os>
                        </archive>
                    </archives>
                </remotePackage>
                <remotePackage path="platform-tools">
                    <type-details xsi:type="generic:genericDetailsType"/>
                    <revision>
                        <major>31</major>
                        <minor>0</minor>
                        <micro>3</micro>
                    </revision>
                    <display-name>Android SDK Platform-Tools</display-name>
                    <uses-license ref="android-sdk-license"/>
                    <channelRef ref="channel-0"/>
                    <archives>
                        <archive>
                            <!--Built on: Mon Jul 19 14:26:34 2021.-->
                            <complete>
                                <size>13302579</size>
                                <checksum type="sha1">f09581347ed39978abb3a99c6bb286de6adc98ef</checksum>
                                <url>platform-tools_r31.0.3-linux.zip</url>
                            </complete>
                            <host-os>linux</host-os>
                        </archive>
                    </archives>
                </remotePackage>
                <remotePackage path="emulator">
                    <type-details xsi:type="generic:genericDetailsType"/>
                    <revision>
                        <major>31</major>
                        <minor>1</minor>
                        <micro>4</micro>
                    </revision>
                    <display-name>Android Emulator</display-name>
                    <uses-license ref="android-sdk-license"/>
                    <dependencies>
                        <dependency path="patcher;v4"/>
                    </dependencies>
                    <channelRef ref="channel-0"/>
                    <archives>
                        <archive>
                            <!--Built on: Tue Nov 23 16:04:50 2021.-->
                            <complete>
                                <size>276082791</size>
                                <checksum type="sha1">fa3cc18914be8d89ee65e0428bc08efd9a8d9cce</checksum>
                                <url>emulator-linux_x64-7920983.zip</url>
                            </complete>
                            <host-os>linux</host-os>
                            <host-arch>x64</host-arch>
                        </archive>
                    </archives>
                </remotePackage>
            </sdk:sdk-repository>
        """.trimIndent()
    Files.write(
        repositoryXml.toPath(), repositoryXmlContents.toByteArray(StandardCharsets.UTF_8))
    val repositoryV2Xml = File(repositoryDir, "repository2-2.xml")
    val repositoryV2XmlContents = """
            <?xml version="1.0" ?>
            <sdk:sdk-repository xmlns:common="http://schemas.android.com/repository/android/common/02" xmlns:generic="http://schemas.android.com/repository/android/generic/02" xmlns:sdk="http://schemas.android.com/sdk/android/repo/repository2/03" xmlns:sdk-common="http://schemas.android.com/sdk/android/repo/common/03" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
            </sdk:sdk-repository>
        """.trimIndent()
    Files.write(
        repositoryV2Xml.toPath(), repositoryV2XmlContents.toByteArray(StandardCharsets.UTF_8))

    FileUtils.copyFileToDirectory(emulatorZip, repositoryDir)
    FileUtils.copyFileToDirectory(sdkPatcherZip, repositoryDir)
    FileUtils.copyFileToDirectory(buildToolsZip, repositoryDir)
    FileUtils.copyFileToDirectory(platform33Zip, repositoryDir)
    FileUtils.copyFileToDirectory(platformToolsZip, repositoryDir)
    FileUtils.copyFileToDirectory(sdkToolsZip, repositoryDir)
}

/**
 * Creates the addon list XML, which is a manifest of all other repository XML locations.
 *
 * Without the manifest, the [RepoManager] will not be able to find the system image urls.
 *
 * For downloading system-images, we only need the sys-img XML for AOSP images at present. We do
 * not want to download the full addons list from online with a bazel rule. There is no need to
 * include other XMLs, as this drowns the gradle output with useless XML parsing errors.
 */
private fun setupManifestXml(repsitoryDir: File) {
    val sourceList = File(repsitoryDir, "addons_list-5.xml")
    val sourceListContents = """
            <?xml version="1.0" ?>
            <common:site-list xmlns:common="http://schemas.android.com/repository/android/sites-common/1" xmlns:sdk="http://schemas.android.com/sdk/android/addons-list/5" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <site xsi:type="sdk:sysImgSiteType">
                    <displayName>Automated Test Device System Images</displayName>
                   <url>sys-img/android/sys-img2-3.xml</url>
                </site>
            </common:site-list>
        """.trimIndent()
    Files.write(sourceList.toPath(), sourceListContents.toByteArray(StandardCharsets.UTF_8))
}

/**
 * Sets up the repository in the sys-img/android subdirectory of the android repository.
 *
 * This is done in 2 steps like the toplevel repository:
 * 1. create the XML to specify where the system images are.
 * 2. insert the system-images into those locations.
 *
 * The repository XML is set up manually to ensure that the revision # of the system image does not
 * change, because the system image that is downloaded by bazel is contingent on the version.
 */
private fun setupAOSPImageRepository(repositoryDir: File) {
    val sysImgRepoFolder = FileUtils.join(repositoryDir, "sys-img", "android")
    FileUtils.mkdirs(sysImgRepoFolder)
    val sysImgXml = File(sysImgRepoFolder, "sys-img2-3.xml")
    val sysImgXmlContents = """
            <?xml version="1.0" ?>
            <sys-img:sdk-sys-img xmlns:sys-img="http://schemas.android.com/sdk/android/repo/sys-img2/03" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <license id="android-sdk-license" type="text">$placeholderLicense</license>
                <channel id="channel-0">stable</channel>
                <remotePackage path="system-images;android-29;default;x86">
                    <type-details xsi:type="sys-img:sysImgDetailsType">
                        <api-level>29</api-level>
                        <base-extension>true</base-extension>
                        <tag>
                            <id>default</id>
                            <display>Default Android System Image</display>
                        </tag>
                        <abi>x86</abi>
                    </type-details>
                    <revision>
                        <major>8</major>
                    </revision>
                    <display-name>Intel x86 Atom System Image</display-name>
                    <uses-license ref="android-sdk-license"/>
                    <dependencies>
                        <dependency path="emulator">
                            <min-revision>
                                <major>28</major>
                                <minor>1</minor>
                                <micro>9</micro>
                            </min-revision>
                        </dependency>
                    </dependencies>
                    <channelRef ref="channel-0"/>
                    <archives>
                        <archive>
                            <!--Built on: Sat Aug 21 09:29:46 2021.-->
                            <complete>
                                <size>516543600</size>
                                <checksum type="sha1">cc4fa13e49cb2e93770d4f2e90ea1dd2a81e315b</checksum>
                                <url>x86-29_r08-darwin.zip</url>
                            </complete>
                            <host-os>macosx</host-os>
                        </archive>
                        <archive>
                            <!--Built on: Sat Aug 21 09:29:43 2021.-->
                            <complete>
                                <size>516543600</size>
                                <checksum type="sha1">cc4fa13e49cb2e93770d4f2e90ea1dd2a81e315b</checksum>
                                <url>x86-29_r08-linux.zip</url>
                            </complete>
                            <host-os>linux</host-os>
                        </archive>
                    </archives>
                </remotePackage>
            </sys-img:sdk-sys-img>
        """.trimIndent()
    Files.write(sysImgXml.toPath(), sysImgXmlContents.toByteArray(StandardCharsets.UTF_8))
    FileUtils.copyFileToDirectory(systemImageZip, sysImgRepoFolder)
}

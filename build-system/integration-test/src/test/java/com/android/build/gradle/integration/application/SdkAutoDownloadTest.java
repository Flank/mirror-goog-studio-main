/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.integration.common.category.OnlineTests;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.RunGradleTasks;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.SdkHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.MavenRepositories;
import com.android.ide.common.repository.SdkMavenRepository;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Tests for automatic SDK download from Gradle. */
@Category(OnlineTests.class)
public class SdkAutoDownloadTest {

    private static String cmakeLists = "cmake_minimum_required(VERSION 3.4.1)"
            + System.lineSeparator()
            + "file(GLOB SRC src/main/cpp/hello-jni.cpp)"
            + System.lineSeparator()
            + "set(CMAKE_VERBOSE_MAKEFILE ON)"
            + System.lineSeparator()
            + "add_library(hello-jni SHARED ${SRC})"
            + System.lineSeparator()
            + "target_link_libraries(hello-jni log)";

    private static final String BUILD_TOOLS_VERSION = AndroidBuilder.MIN_BUILD_TOOLS_REV.toString();
    private static final String PLATFORM_VERSION = "25";

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldJniApp.builder()
                            .withNativeDir("cpp")
                            .useCppSource(true)
                            .build())
                    .addGradleProperties(AndroidGradleOptions.ANDROID_SDK_CHANNEL +"=3")
                    .create();

    private File mSdkHome;
    private File licenseFile;
    private File previewLicenseFile;

    @Before
    public void setUp() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator() + "apply plugin: 'com.android.application'");

        // TODO: Set System property {@code AndroidSdkHandler.SDK_TEST_BASE_URL_PROPERTY}.
        mSdkHome = project.file("local-sdk-for-test");
        FileUtils.mkdirs(mSdkHome);

        File licensesFolder = new File(mSdkHome, "licenses");
        FileUtils.mkdirs(licensesFolder);
        licenseFile = new File(licensesFolder, "android-sdk-license");
        previewLicenseFile = new File(licensesFolder, "android-sdk-preview-license");

        String licensesHash =
                "e6b7c2ab7fa2298c15165e9583d0acf0b04a2232"
                        + System.lineSeparator()
                        + "8933bad161af4178b1185d1a37fbf41ea5269c55";

        String previewLicenseHash =
                "84831b9409646a918e30573bab4c9c91346d8abd"
                        + System.lineSeparator()
                        + "79120722343a6f314e0719f863036c702b0e6b2a";


        Files.write(licenseFile.toPath(), licensesHash.getBytes(StandardCharsets.UTF_8));
        Files.write(
                previewLicenseFile.toPath(), previewLicenseHash.getBytes(StandardCharsets.UTF_8));
        TestFileUtils.appendToFile(
                project.getLocalProp(),
                System.lineSeparator()
                        + SdkConstants.SDK_DIR_PROPERTY
                        + " = "
                        + mSdkHome.getAbsolutePath());

        // Copy one version of build tools and one platform from the real SDK, so we have something
        // to start with.
        File realAndroidHome = SdkHelper.findSdkDir();

        File platform =
                FileUtils.join(
                        realAndroidHome,
                        SdkConstants.FD_PLATFORMS,
                        "android-" + PLATFORM_VERSION);
        File buildTools =
                FileUtils.join(realAndroidHome, SdkConstants.FD_BUILD_TOOLS, BUILD_TOOLS_VERSION);
        assertThat(platform).isDirectory();
        assertThat(buildTools).isDirectory();

        FileUtils.copyDirectoryToDirectory(
                platform,
                FileUtils.join(mSdkHome, SdkConstants.FD_PLATFORMS));

        FileUtils.copyDirectoryToDirectory(
                buildTools,
                FileUtils.join(mSdkHome, SdkConstants.FD_BUILD_TOOLS));

        FileUtils.copyDirectoryToDirectory(
                FileUtils.join(realAndroidHome, SdkConstants.FD_PLATFORM_TOOLS),
                FileUtils.join(mSdkHome, SdkConstants.FD_PLATFORM_TOOLS));

        TestFileUtils.appendToFile(
                project.getBuildFile(), "android.defaultConfig.minSdkVersion = 19");
    }

    /**
     * Tests that the compile SDK target was automatically downloaded in the case that the target
     * was a platform target and it wasn't already there.
     */
    @Test
    public void checkCompileSdkPlatformDownloading() throws Exception {
        deletePlatforms();

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\"");

        getExecutor().run("assembleDebug");

        File platformTarget = getPlatformFolder();
        assertThat(platformTarget).isDirectory();

        File androidJarFile = FileUtils.join(getPlatformFolder(), "android.jar");
        assertThat(androidJarFile).exists();
    }

    /**
     * Tests that the compile SDK target was automatically downloaded in the case that the target
     * was an addon target. It also checks that the platform that the addon is dependent on was
     * downloaded.
     */
    @Test
    public void checkCompileSdkAddonDownloading() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion \"Google Inc.:Google APIs:23\""
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\"");

        getExecutor().run("assembleDebug");

        File platformBase = getPlatformFolder();
        assertThat(platformBase).isDirectory();

        File addonTarget =
                FileUtils.join(mSdkHome, SdkConstants.FD_ADDONS, "addon-google_apis-google-23");
        assertThat(addonTarget).isDirectory();
    }

    /** Tests that we don't crash when a codename is used for the compile SDK level. */
    @Test
    public void checkCompileSdkCodename() throws Exception {
        deletePlatforms();

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion 'MadeUp'"
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\"");

        GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");
        assertThat(result.getFailureMessage())
                .contains("Failed to find target with hash string 'MadeUp'");
    }

    /** Tests that calling getBootClasspath() doesn't break auto-download. */
    @Test
    public void checkGetBootClasspath() throws Exception {
        deletePlatforms();

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\""
                        + System.lineSeparator()
                        + "println(android.bootClasspath)");

        getExecutor().run("assembleDebug");

        File platformTarget = getPlatformFolder();
        assertThat(platformTarget).isDirectory();

        File androidJarFile = FileUtils.join(getPlatformFolder(), "android.jar");
        assertThat(androidJarFile).exists();
    }

    /**
     * Tests that the build tools were automatically downloaded, when they weren't already installed
     */
    @Test
    public void checkBuildToolsDownloading() throws Exception {
        deleteBuildTools();

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\"");

        getExecutor().run("assembleDebug");

        File buildTools =
                FileUtils.join(mSdkHome, SdkConstants.FD_BUILD_TOOLS, BUILD_TOOLS_VERSION);
        assertThat(buildTools).isDirectory();

        File dxFile =
                FileUtils.join(mSdkHome, SdkConstants.FD_BUILD_TOOLS, BUILD_TOOLS_VERSION, "dx");
        assertThat(dxFile).exists();
    }

    /**
     * Tests that the platform tools were automatically downloaded, when they weren't already
     * installed.
     */
    @Test
    public void checkPlatformToolsDownloading() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\"");

        // Delete the platform-tools folder from the set-up.
        File platformTools = FileUtils.join(mSdkHome, SdkConstants.FD_PLATFORM_TOOLS);
        FileUtils.deletePath(platformTools);
        assertThat(platformTools).doesNotExist();

        getExecutor().run("assembleDebug");
        assertThat(platformTools).isDirectory();
    }

    /** Tests that missing platform tools don't break the build. */
    @Test
    public void checkMissingPlatformTools() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\"");

        // Delete the platform-tools folder from the set-up.
        File platformTools = FileUtils.join(mSdkHome, SdkConstants.FD_PLATFORM_TOOLS);
        FileUtils.deletePath(platformTools);
        assertThat(platformTools).doesNotExist();

        getOfflineExecutor().run("assembleDebug");
        assertThat(platformTools).doesNotExist();
    }

    @Test
    public void checkCmakeDownloading() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\""
                        + System.lineSeparator()
                        + "android.externalNativeBuild.cmake.path \"CMakeLists.txt\"");

        Files.write(project.file("CMakeLists.txt").toPath(),
                cmakeLists.getBytes(StandardCharsets.UTF_8));

        getExecutor().run("assembleDebug");

        File cmakeDirectory = FileUtils.join(mSdkHome, SdkConstants.FD_CMAKE);
        assertThat(cmakeDirectory).isDirectory();
    }

    @Test
    public void checkCmakeMissingLicense() throws Exception {
        FileUtils.delete(previewLicenseFile);
        deleteLicense();

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\""
                        + System.lineSeparator()
                        + "android.externalNativeBuild.cmake.path \"CMakeLists.txt\"");

        Files.write(project.file("CMakeLists.txt").toPath(),
                cmakeLists.getBytes(StandardCharsets.UTF_8));

        GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");

        assertThat(result.getStderr()).contains("not accepted the license agreements");
        assertThat(result.getStderr()).contains("CMake");
    }

    @Test
    public void checkDependencies_androidRepository() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\""
                        + System.lineSeparator()
                        + "dependencies { compile 'com.android.support:support-v4:"
                        + GradleTestProject.SUPPORT_LIB_VERSION
                        + "' }");

        getExecutor().run("assembleDebug");

        checkForLibrary(SdkMavenRepository.ANDROID, "com.android.support", "support-v4", "23.0.0");

        // Check that the Google repo is not automatically installed if an Android library is
        // missing.
        assertThat(SdkMavenRepository.GOOGLE.isInstalled(mSdkHome, FileOpUtils.create())).isFalse();
    }

    @Test
    public void checkDependencies_googleRepository() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\""
                        + System.lineSeparator()
                        + "android.defaultConfig.multiDexEnabled true"
                        + System.lineSeparator()
                        + "dependencies { compile 'com.google.android.gms:play-services:"
                        + GradleTestProject.PLAY_SERVICES_VERSION
                        + "' }");

        getExecutor().run("assembleDebug");

        checkForLibrary(
                SdkMavenRepository.GOOGLE,
                "com.google.android.gms",
                "play-services",
                GradleTestProject.PLAY_SERVICES_VERSION);
    }

    @Test
    public void checkDependencies_individualRepository() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\""
                        + System.lineSeparator()
                        + "dependencies { compile 'com.android.support.constraint:constraint-layout-solver:1.0.0-alpha4' }");

        getExecutor().run("assembleDebug");

        checkForLibrary(
                SdkMavenRepository.ANDROID,
                "com.android.support.constraint",
                "constraint-layout-solver",
                "1.0.0-alpha4");

        assertThat(SdkMavenRepository.GOOGLE.isInstalled(mSdkHome, FileOpUtils.create())).isFalse();
        assertThat(SdkMavenRepository.ANDROID.isInstalled(mSdkHome, FileOpUtils.create()))
                .isFalse();
    }

    @NonNull
    private RunGradleTasks getExecutor() {
        return getOfflineExecutor().withoutOfflineFlag();
    }

    private RunGradleTasks getOfflineExecutor() {
        return project.executor().withSdkAutoDownload();
    }

    private void checkForLibrary(
            @NonNull SdkMavenRepository oldRepository,
            @NonNull String groupId,
            @NonNull String artifactId,
            @NonNull String version) {
        FileOp fileOp = FileOpUtils.create();
        GradleCoordinate coordinate =
                new GradleCoordinate(
                        groupId, artifactId, new GradleCoordinate.StringComponent(version));

        // Try the new repository first.
        File repositoryLocation =
                FileUtils.join(mSdkHome, SdkConstants.FD_EXTRAS, SdkConstants.FD_M2_REPOSITORY);

        File artifactDirectory =
                MavenRepositories.getArtifactDirectory(repositoryLocation, coordinate);

        if (!artifactDirectory.exists()) {
            // Try the old repository it's supposed to be in.
            repositoryLocation = oldRepository.getRepositoryLocation(mSdkHome, true, fileOp);
            assertNotNull(repositoryLocation);
            artifactDirectory =
                    MavenRepositories.getArtifactDirectory(repositoryLocation, coordinate);
            assertThat(artifactDirectory).exists();
        }
    }

    @Test
    public void checkDependencies_invalidDependency() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\""
                        + System.lineSeparator()
                        + "dependencies { compile 'foo:bar:baz' }");

        GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");
        assertNotNull(result.getException());

        // Make sure the standard gradle error message is what the user sees.
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .startsWith("Could not find foo:bar:baz.");
    }

    @Test
    public void checkNoLicenseError_PlatformTarget() throws Exception {
        deleteLicense();
        deletePlatforms();

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\"");

        GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");
        assertNotNull(result.getException());

        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains(
                        "Android SDK Platform "
                                + AndroidBuilder.MIN_BUILD_TOOLS_REV.toShortString());
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("missing components");
    }

    private void deleteLicense() throws Exception {
        FileUtils.delete(licenseFile);
    }

    @Test
    public void checkNoLicenseError_AddonTarget() throws Exception {
        deleteLicense();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion \"Google Inc.:Google APIs:23\""
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\"");

        GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");
        assertNotNull(result.getException());

        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("Google APIs");
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("missing components");
    }

    @Test
    public void checkNoLicenseError_BuildTools() throws Exception {
        deleteLicense();
        deleteBuildTools();

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\"");

        GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");
        assertNotNull(result.getException());

        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains(
                        "Android SDK Build-Tools "
                                + AndroidBuilder.MIN_BUILD_TOOLS_REV.toShortString());
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("missing components");
    }

    @Test
    public void checkNoLicenseError_MultiplePackages() throws Exception {
        deleteLicense();
        deleteBuildTools();

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion \"Google Inc.:Google APIs:23\""
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\"");

        GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");
        assertNotNull(result.getException());

        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("missing components");
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains(
                        "Android SDK Build-Tools "
                                + AndroidBuilder.MIN_BUILD_TOOLS_REV.toShortString());
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("Android SDK Platform 23");
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("Google APIs");
    }

    @Test
    public void checkPermissions_BuildTools() throws Exception {
        Assume.assumeFalse(SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS);

        deleteBuildTools();

        // Change the permissions.
        Path sdkHomePath = mSdkHome.toPath();
        Set<PosixFilePermission> readOnlyDir =
                ImmutableSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE);

        Files.walk(sdkHomePath).forEach(path -> {
            try {
                Files.setPosixFilePermissions(path, readOnlyDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        try {
            // Request a new version of build tools.
            TestFileUtils.appendToFile(
                    project.getBuildFile(),
                    System.lineSeparator()
                            + "android.compileSdkVersion "
                            + PLATFORM_VERSION
                            + System.lineSeparator()
                            + "android.buildToolsVersion \""
                            + BUILD_TOOLS_VERSION
                            + "\"");

            GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");
            assertNotNull(result.getException());

            assertThat(Throwables.getRootCause(result.getException()).getMessage())
                    .contains(
                            "Android SDK Build-Tools "
                                    + AndroidBuilder.MIN_BUILD_TOOLS_REV.toShortString());
            assertThat(Throwables.getRootCause(result.getException()).getMessage())
                    .contains("not writeable");
        } finally {
            Set<PosixFilePermission> readWriteDir =
                    ImmutableSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE);

            //noinspection ThrowFromFinallyBlock
            Files.walk(sdkHomePath).forEach(path -> {
                try {
                    Files.setPosixFilePermissions(path, readWriteDir);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private File getPlatformFolder() {
        return FileUtils.join(mSdkHome, SdkConstants.FD_PLATFORMS, "android-" + PLATFORM_VERSION);
    }

    private void deleteBuildTools() throws Exception {
        FileUtils.deleteDirectoryContents(FileUtils.join(mSdkHome, SdkConstants.FD_BUILD_TOOLS));
    }

    private void deletePlatforms() throws Exception {
        FileUtils.deleteDirectoryContents(FileUtils.join(mSdkHome, SdkConstants.FD_PLATFORMS));
    }

}

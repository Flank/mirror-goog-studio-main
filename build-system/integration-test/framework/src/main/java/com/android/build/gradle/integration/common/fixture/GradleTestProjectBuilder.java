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

package com.android.build.gradle.integration.common.fixture;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.BazelIntegrationTestsSuite;
import com.android.build.gradle.integration.common.fixture.app.AbstractAndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.SdkHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.testutils.TestUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public final class GradleTestProjectBuilder {

    public static final Path DEFAULT_PROFILE_DIR = Paths.get("build", "android-profile");

    @Nullable private String name;
    @Nullable private TestProject testProject = null;
    @Nullable private String targetGradleVersion;
    @Nullable private String buildToolsVersion;
    private boolean withoutNdk = false;
    @NonNull private List<String> gradleProperties = Lists.newArrayList();
    @Nullable private String heapSize;
    @Nullable private Path profileDirectory;
    @Nullable private File testDir = null;
    private boolean withDependencyChecker = true;
    // Indicates if we need to create a project without setting cmake.dir in local.properties.
    private boolean withCmakeDirInLocalProp = false;
    @NonNull String cmakeVersion;
    @Nullable private List<Path> repoDirectories;
    @Nullable private File androidHome;
    @Nullable private File androidNdkHome;
    @Nullable private File gradleDistributionDirectory;
    @Nullable private String kotlinVersion;

    private boolean withDeviceProvider = true;
    private boolean withSdk = true;
    private boolean withAndroidGradlePlugin = true;
    // list of included builds, relative to the main testDir
    private List<String> withIncludedBuilds = Lists.newArrayList();

    /** Create a GradleTestProject. */
    @NonNull
    public GradleTestProject create() {
        if (targetGradleVersion == null) {
            targetGradleVersion = GradleTestProject.GRADLE_TEST_VERSION;
        }

        if (androidHome == null && withSdk) {
            androidHome = SdkHelper.findSdkDir();
        }

        if (androidNdkHome == null) {
            String envCustomAndroidNdkHome =
                    Strings.emptyToNull(System.getenv().get("CUSTOM_ANDROID_NDK_HOME"));
            if (envCustomAndroidNdkHome != null) {
                androidNdkHome = new File(envCustomAndroidNdkHome);
                Preconditions.checkState(
                        androidNdkHome.isDirectory(),
                        "CUSTOM_ANDROID_NDK_HOME must point to a directory, "
                                + androidNdkHome.getAbsolutePath()
                                + " is not a directory");
            } else {
                androidNdkHome =
                        TestUtils.runningFromBazel()
                                ? BazelIntegrationTestsSuite.NDK_IN_TMP.toFile()
                                : new File(androidHome, SdkConstants.FD_NDK);
            }
        }

        if (gradleDistributionDirectory == null) {
            gradleDistributionDirectory = TestUtils.getWorkspaceFile("tools/external/gradle");
        }

        if (kotlinVersion == null) {
            kotlinVersion = TestUtils.getKotlinVersionForTests();
        }

        return new GradleTestProject(
                name,
                testProject,
                targetGradleVersion,
                withoutNdk,
                withDependencyChecker,
                gradleProperties,
                heapSize,
                buildToolsVersion,
                profileDirectory,
                cmakeVersion,
                withCmakeDirInLocalProp,
                withDeviceProvider,
                withSdk,
                withAndroidGradlePlugin,
                withIncludedBuilds,
                testDir,
                repoDirectories,
                androidHome,
                androidNdkHome,
                gradleDistributionDirectory,
                kotlinVersion);
    }

    /**
     * Set the name of the project.
     *
     * <p>Necessary if you have multiple projects in a test class.
     */
    public GradleTestProjectBuilder withName(@NonNull String name) {
        this.name = name;
        return this;
    }

    /** Create a project without setting ndk.dir in local.properties. */
    public GradleTestProjectBuilder withoutNdk() {
        this.withoutNdk = true;
        return this;
    }

    public GradleTestProjectBuilder withAndroidHome(File androidHome) {
        this.androidHome = androidHome;
        return this;
    }

    public GradleTestProjectBuilder withGradleDistributionDirectory(
            File gradleDistributionDirectory) {
        this.gradleDistributionDirectory = gradleDistributionDirectory;
        return this;
    }

    public GradleTestProjectBuilder withKotlinVersion(String kotlinVersion) {
        this.kotlinVersion = kotlinVersion;
        return this;
    }

    public GradleTestProjectBuilder withDeviceProvider(boolean withDeviceProvider) {
        this.withDeviceProvider = withDeviceProvider;
        return this;
    }

    public GradleTestProjectBuilder withSdk(boolean withSdk) {
        this.withSdk = withSdk;
        return this;
    }

    public GradleTestProjectBuilder withRepoDirectories(List<Path> repoDirectories) {
        this.repoDirectories = repoDirectories;
        return this;
    }

    public GradleTestProjectBuilder withAndroidGradlePlugin(boolean withAndroidGradlePlugin) {
        this.withAndroidGradlePlugin = withAndroidGradlePlugin;
        return this;
    }

    public GradleTestProjectBuilder withTestDir(File testDir) {
        this.testDir = testDir;
        return this;
    }

    public GradleTestProjectBuilder withIncludedBuilds(String relativePath) {
        withIncludedBuilds.add(relativePath);
        return this;
    }

    public GradleTestProjectBuilder withIncludedBuilds(String... relativePaths) {
        withIncludedBuilds.addAll(Arrays.asList(relativePaths));
        return this;
    }

    /** Create GradleTestProject from a TestProject. */
    public GradleTestProjectBuilder fromTestApp(@NonNull TestProject testProject) {
        this.testProject = testProject;
        return this;
    }

    /** Create GradleTestProject from an existing test project. */
    public GradleTestProjectBuilder fromTestProject(@NonNull String project) {
        AndroidTestApp app = new EmptyTestApp();
        if (name == null) {
            name = project;
        }

        File projectDir = TestProjectPaths.getTestProjectDir(project);
        addAllFiles(app, projectDir);
        return fromTestApp(app);
    }

    public GradleTestProjectBuilder fromDir(@NonNull File dir) {
        Preconditions.checkArgument(dir.isDirectory(), "passed dir must be a directory");
        AndroidTestApp app = new EmptyTestApp();
        addAllFiles(app, dir);
        return fromTestApp(app);
    }

    /** Create GradleTestProject from an existing test project. */
    public GradleTestProjectBuilder fromExternalProject(@NonNull String project) {
        name = project;
        File parentDir = TestUtils.getWorkspaceFile("external");
        File projectDir = new File(parentDir, project);
        if (!projectDir.exists()) {
            projectDir = new File(parentDir, project.replace('-', '_'));
        }
        return fromDir(projectDir);
    }

    /** Create GradleTestProject from a data binding integration test. */
    public GradleTestProjectBuilder fromDataBindingIntegrationTest(@NonNull String project) {
        AndroidTestApp app = new EmptyTestApp();
        name = project;
        // compute the root folder of the checkout, based on test-projects.
        File parentDir = TestUtils.getWorkspaceFile("tools/data-binding/integration-tests");

        File projectDir = new File(parentDir, project);
        if (!projectDir.exists()) {
            throw new RuntimeException("Project " + project + " not found in " + projectDir + ".");
        }
        addAllFiles(app, projectDir);
        return fromTestApp(app);
    }

    /** Add a new file to the project. */
    public GradleTestProjectBuilder addFile(@NonNull TestSourceFile file) {
        return addFiles(Lists.newArrayList(file));
    }

    /** Add a new file to the project. */
    public GradleTestProjectBuilder addFiles(@NonNull List<TestSourceFile> files) {
        if (!(this.testProject instanceof AndroidTestApp)) {
            throw new IllegalStateException("addFile is only for AndroidTestApp");
        }
        AndroidTestApp app = (AndroidTestApp) this.testProject;
        for (TestSourceFile file : files) {
            app.addFile(file);
        }
        return this;
    }

    /** Add gradle properties. */
    public GradleTestProjectBuilder addGradleProperties(@NonNull String property) {
        gradleProperties.add(property);
        return this;
    }

    /**
     * Sets the test heap size requirement. Example values : 1024m, 2048m...
     *
     * @param heapSize the heap size in a format understood by the -Xmx JVM parameter
     * @return itself.
     */
    public GradleTestProjectBuilder withHeap(String heapSize) {
        this.heapSize = heapSize;
        return this;
    }

    public GradleTestProjectBuilder withDependencyChecker(
            boolean dependencyChecker) {
        this.withDependencyChecker = dependencyChecker;
        return this;
    }

    public GradleTestProjectBuilder withBuildToolsVersion(String buildToolsVersion) {
        this.buildToolsVersion = buildToolsVersion;
        return this;
    }


    /**
     * Enable profile output generation. Typically used in benchmark tests. By default, places the
     * outputs in build/android-profile.
     */
    public GradleTestProjectBuilder enableProfileOutput() {
        this.profileDirectory = DEFAULT_PROFILE_DIR;
        return this;
    }

    /**
     * Enable profile output generation in the given folder. Typically used in benchmark tests.
     *
     * <p>Allows both absolute and relative paths. If a relative path is given, the path will be
     * resolved against the root directory of the project.
     */
    public GradleTestProjectBuilder enableProfileOutputInDirectory(
            @Nullable Path profileDirectory) {
        this.profileDirectory = profileDirectory;
        return this;
    }

    /** Enables setting cmake.dir in local.properties */
    public GradleTestProjectBuilder setWithCmakeDirInLocalProp(boolean withCmakeDirInLocalProp) {
        this.withCmakeDirInLocalProp = withCmakeDirInLocalProp;
        return this;
    }

    /** Sets the cmake version to use */
    public GradleTestProjectBuilder setCmakeVersion(@NonNull String cmakeVersion) {
        this.cmakeVersion = cmakeVersion;
        return this;
    }

    private static class EmptyTestApp extends AbstractAndroidTestApp {
        @Override
        public boolean containsFullBuildScript() {
            return true;
        }
    }

    /** Add all files in a directory to an AndroidTestApp. */
    private static void addAllFiles(AndroidTestApp app, File projectDir) {
        try {
            for (String filePath : TestFileUtils.listFiles(projectDir.toPath())) {
                File file = new File(filePath);
                app.addFile(
                        new TestSourceFile(
                                file.getParent(),
                                file.getName(),
                                Files.toByteArray(new File(projectDir, filePath))));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


}

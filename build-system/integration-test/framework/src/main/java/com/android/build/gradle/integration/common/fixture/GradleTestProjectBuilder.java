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

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_COMPILE_SDK_VERSION;
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_TEST_PROJECT_NAME;
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.GRADLE_TEST_VERSION;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.BazelIntegrationTestsSuite;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.SdkHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.internal.packaging.ApkCreatorType;
import com.android.testutils.MavenRepoGenerator;
import com.android.testutils.TestUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
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
    @Nullable private String compileSdkVersion;
    @NonNull private List<String> gradleProperties = Lists.newArrayList();
    @Nullable private String heapSize;
    @Nullable private String metaspace;
    @Nullable private Path profileDirectory;
    private boolean withDependencyChecker = true;
    private BaseGradleExecutor.ConfigurationCaching withConfigurationCaching =
            BaseGradleExecutor.ConfigurationCaching.ON;
    // Indicates if we need to create a project without setting cmake.dir in local.properties.
    private boolean withCmakeDirInLocalProp = false;
    // NDK symlink path is relative to the test's BUILD_DIR. A full path example of
    // this is like this on bazel:
    // /private/var/tmp/_bazel/cd7382de6c57d974eabcf5c1270266ca/sandbox
    //   /darwin-sandbox/9/execroot/__main__/_tmp/85abb3fc831caa67a0715d2cf3ce5967/
    // The resulting symlink path is always in the form */ndk/
    private String ndkSymlinkPath = ".";
    @Nullable String cmakeVersion;
    @Nullable private List<Path> repoDirectories;
    @Nullable private File androidSdkDir;
    @Nullable private File androidNdkDir;
    @Nullable private String sideBySideNdkVersion = null;
    @Nullable private File gradleDistributionDirectory;
    @Nullable private File gradleBuildCacheDirectory;
    @Nullable private String kotlinVersion;
    @Nullable private String buildFileName = null;

    private Boolean withDeviceProvider = null;
    private boolean withSdk = true;
    private boolean withAndroidGradlePlugin = true;
    private boolean withKotlinGradlePlugin = false;
    private boolean withPluginManagementBlock = false;
    // list of included builds, relative to the main projectDir
    private List<String> withIncludedBuilds = Lists.newArrayList();

    /** Whether or not to output the log of the last build result when a test fails */
    private boolean outputLogOnFailure = true;
    private MavenRepoGenerator additionalMavenRepo;

    /** Create a GradleTestProject. */
    @NonNull
    public GradleTestProject create() {
        if (targetGradleVersion == null) {
            targetGradleVersion = GradleTestProject.GRADLE_TEST_VERSION;
        }

        if (androidSdkDir == null && withSdk) {
            androidSdkDir = SdkHelper.findSdkDir();
        }

        if (androidNdkDir == null) {
            String envCustomAndroidNdkHome =
                    Strings.emptyToNull(System.getenv().get("CUSTOM_ANDROID_NDK_ROOT"));
            if (envCustomAndroidNdkHome != null) {
                androidNdkDir = new File(envCustomAndroidNdkHome);
                Preconditions.checkState(
                        androidNdkDir.isDirectory(),
                        "CUSTOM_ANDROID_NDK_ROOT must point to a directory, "
                                + androidNdkDir.getAbsolutePath()
                                + " is not a directory");
            } else {
                if (sideBySideNdkVersion != null) {
                    androidNdkDir =
                            TestUtils.runningFromBazel()
                                    ? new File(
                                            BazelIntegrationTestsSuite.NDK_SIDE_BY_SIDE_ROOT
                                                    .toFile(),
                                            sideBySideNdkVersion)
                                    : new File(
                                            new File(
                                                    androidSdkDir,
                                                    SdkConstants.FD_NDK_SIDE_BY_SIDE),
                                            sideBySideNdkVersion);
                } else {
                    androidNdkDir =
                            TestUtils.runningFromBazel()
                                    ? BazelIntegrationTestsSuite.NDK_IN_TMP.toFile()
                                    : new File(androidSdkDir, SdkConstants.FD_NDK);
                }
            }
        }

        if (gradleDistributionDirectory == null) {
            gradleDistributionDirectory =
                    TestUtils.resolveWorkspacePath("tools/external/gradle").toFile();
        }

        if (kotlinVersion == null) {
            kotlinVersion = TestUtils.KOTLIN_VERSION_FOR_TESTS;
        }

        if (withDeviceProvider == null) {
            withDeviceProvider = GradleTestProject.APPLY_DEVICEPOOL_PLUGIN;
        }

        MemoryRequirement memoryRequirement = MemoryRequirement.use(heapSize, metaspace);

        return new GradleTestProject(
                (name != null ? name : DEFAULT_TEST_PROJECT_NAME),
                (buildFileName != null) ? buildFileName : "build.gradle",
                testProject,
                (targetGradleVersion != null ? targetGradleVersion : GRADLE_TEST_VERSION),
                withDependencyChecker,
                withConfigurationCaching,
                gradleProperties,
                memoryRequirement,
                (compileSdkVersion != null ? compileSdkVersion : DEFAULT_COMPILE_SDK_VERSION),
                profileDirectory,
                cmakeVersion,
                withCmakeDirInLocalProp,
                ndkSymlinkPath,
                withDeviceProvider,
                withSdk,
                withAndroidGradlePlugin,
                withKotlinGradlePlugin,
                withPluginManagementBlock,
                withIncludedBuilds,
                null,
                repoDirectories,
                additionalMavenRepo,
                androidSdkDir,
                androidNdkDir,
                gradleDistributionDirectory,
                gradleBuildCacheDirectory,
                kotlinVersion,
                outputLogOnFailure);
    }

    public GradleTestProjectBuilder withAdditionalMavenRepo(
            @Nullable MavenRepoGenerator mavenRepo) {
        additionalMavenRepo = mavenRepo;
        return this;
    }

    /** Policy for setting Heap Size for Gradle process */
    public static class MemoryRequirement {

        private static final String DEFAULT_HEAP = "1G";
        private static final String DEFAULT_METASPACE = "1G";

        /** use default heap size for gradle. */
        public static MemoryRequirement useDefault() {
            return use(null, null);
        }

        /**
         * Use a provided heap size for Gradle
         *
         * @param heap the desired heap size
         * @param metaspace the desired metaspace size
         */
        public static MemoryRequirement use(@Nullable String heap, @Nullable String metaspace) {
            return new MemoryRequirement(
                    heap != null ? heap : DEFAULT_HEAP,
                    metaspace != null ? metaspace : DEFAULT_METASPACE);
        }

        @NonNull private final String heap;
        @NonNull private final String metaspace;

        private MemoryRequirement(@NonNull String heap, @NonNull String metaspace) {
            this.heap = heap;
            this.metaspace = metaspace;
        }

        @NonNull
        public List<String> getJvmArgs() {
            return ImmutableList.of("-XX:MaxMetaspaceSize=" + metaspace, "-Xmx" + heap);
        }


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

    public GradleTestProjectBuilder withAndroidSdkDir(File androidSdkDir) {
        this.androidSdkDir = androidSdkDir;
        return this;
    }

    public GradleTestProjectBuilder withGradleDistributionDirectory(
            File gradleDistributionDirectory) {
        this.gradleDistributionDirectory = gradleDistributionDirectory;
        return this;
    }

    /**
     * Sets a custom directory for the Gradle build cache (not the Android Gradle build cache). The
     * path can be absolute or relative to projectDir.
     */
    public GradleTestProjectBuilder withGradleBuildCacheDirectory(
            @NonNull File gradleBuildCacheDirectory) {
        this.gradleBuildCacheDirectory = gradleBuildCacheDirectory;
        return this;
    }

    public GradleTestProjectBuilder setTargetGradleVersion(@Nullable String targetGradleVersion) {
        this.targetGradleVersion = targetGradleVersion;
        return this;
    }

    public GradleTestProjectBuilder withKotlinVersion(String kotlinVersion) {
        this.kotlinVersion = kotlinVersion;
        return this;
    }

    public GradleTestProjectBuilder withPluginManagementBlock(boolean withPluginManagementBlock) {
        this.withPluginManagementBlock = withPluginManagementBlock;
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

    public GradleTestProjectBuilder withKotlinGradlePlugin(boolean withKotlinGradlePlugin) {
        this.withKotlinGradlePlugin = withKotlinGradlePlugin;
        return this;
    }

    public GradleTestProjectBuilder withIncludedBuilds(String relativePath) {
        withIncludedBuilds.add(relativePath);
        return this;
    }

    public GradleTestProjectBuilder withConfigurationCaching(
            BaseGradleExecutor.ConfigurationCaching configurationCaching) {
        this.withConfigurationCaching = configurationCaching;
        return this;
    }

    public GradleTestProjectBuilder withConfigurationCacheMaxProblems(int maxProblems) {
        Preconditions.checkArgument(maxProblems > 0, "No need for this method if maxProblems is 0");
        this.withConfigurationCaching = BaseGradleExecutor.ConfigurationCaching.WARN;
        this.addGradleProperties(
                "org.gradle.unsafe.configuration-cache.max-problems=" + maxProblems);
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
        GradleProject app = new EmptyTestApp();
        if (name == null) {
            name = project;
        }

        File projectDir = TestProjectPaths.getTestProjectDir(project);
        addAllFiles(app, projectDir);
        return fromTestApp(app);
    }

    public GradleTestProjectBuilder fromDir(@NonNull File dir) {
        Preconditions.checkArgument(
                dir.isDirectory(), dir.getAbsolutePath() + " is not a directory");
        GradleProject app = new EmptyTestApp();
        addAllFiles(app, dir);
        return fromTestApp(app);
    }

    /** Create GradleTestProject from a data binding integration test. */
    public GradleTestProjectBuilder fromDataBindingIntegrationTest(
            @NonNull String project, boolean useAndroidX) {
        GradleProject app = new EmptyTestApp();
        name = project;
        // compute the root folder of the checkout, based on test-projects.
        String suffix = useAndroidX ? "" : "-support";
        File parentDir =
                TestUtils.resolveWorkspacePath("tools/data-binding/integration-tests" + suffix)
                        .toFile();

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
        if (!(this.testProject instanceof GradleProject)) {
            throw new IllegalStateException("addFile is only for GradleProject");
        }
        GradleProject app = (GradleProject) this.testProject;
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

    public GradleTestProjectBuilder setApkCreatorType(@NonNull ApkCreatorType apkCreatorType) {
        switch (apkCreatorType) {
            case APK_Z_FILE_CREATOR:
                gradleProperties.add(
                        BooleanOption.USE_NEW_APK_CREATOR.getPropertyName() + "=false");
                return this;
            case APK_FLINGER:
                gradleProperties.add(BooleanOption.USE_NEW_APK_CREATOR.getPropertyName() + "=true");
                return this;
        }
        throw new IllegalStateException();
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

    /**
     * Sets the test metaspace size requirement. Example values : 128m, 1024m...
     *
     * @param metaspaceSize the metaspacesize in a format understood by the -Xmx JVM parameter
     * @return itself.
     */
    public GradleTestProjectBuilder withMetaspace(String metaspaceSize) {
        this.metaspace = metaspaceSize;
        return this;
    }

    public GradleTestProjectBuilder withDependencyChecker(
            boolean dependencyChecker) {
        this.withDependencyChecker = dependencyChecker;
        return this;
    }

    public GradleTestProjectBuilder withCompileSdkVersion(@Nullable String compileSdkVersion) {
        this.compileSdkVersion = compileSdkVersion;
        return this;
    }

    public GradleTestProjectBuilder dontOutputLogOnFailure() {
        this.outputLogOnFailure = false;
        return this;
    }

    public GradleTestProjectBuilder setSideBySideNdkVersion(String sideBySideNdkVersion) {
        this.sideBySideNdkVersion = sideBySideNdkVersion;
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

    /** Enables setting cmake.dir in local.properties */
    public GradleTestProjectBuilder setWithCmakeDirInLocalProp(boolean withCmakeDirInLocalProp) {
        this.withCmakeDirInLocalProp = withCmakeDirInLocalProp;
        return this;
    }

    /** Enables setting ndk.symlinkdir in local.properties */
    public GradleTestProjectBuilder setWithNdkSymlinkDirInLocalProp(String ndkSymlinkPath) {
        this.ndkSymlinkPath = ndkSymlinkPath;
        return this;
    }

    /** Sets the cmake version to use */
    public GradleTestProjectBuilder setCmakeVersion(@NonNull String cmakeVersion) {
        this.cmakeVersion = cmakeVersion;
        return this;
    }

    public GradleTestProjectBuilder withBuildFileName(@Nullable String buildFileName) {
        this.buildFileName = buildFileName;
        return this;
    }

    private static class EmptyTestApp extends GradleProject {
        @Override
        public boolean containsFullBuildScript() {
            return true;
        }
    }

    /** Add all files in a directory to an GradleProject. */
    private static void addAllFiles(GradleProject app, File projectDir) {
        try {
            for (String filePath : TestFileUtils.listFiles(projectDir.toPath())) {
                app.addFile(
                        new TestSourceFile(
                                filePath, Files.toByteArray(new File(projectDir, filePath))));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


}

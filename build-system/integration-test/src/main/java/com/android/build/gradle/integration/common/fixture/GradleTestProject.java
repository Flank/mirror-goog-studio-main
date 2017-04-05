/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.file.Files.createTempDirectory;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.BasePlugin;
import com.android.build.gradle.integration.BazelIntegrationTestsSuite;
import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer;
import com.android.build.gradle.integration.performance.BenchmarkRecorder;
import com.android.build.gradle.model.Version;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.AndroidProject;
import com.android.io.StreamException;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.project.ProjectPropertiesWorkingCopy;
import com.android.testutils.OsType;
import com.android.testutils.TestUtils;
import com.android.testutils.apk.Aar;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.AtomBundle;
import com.android.testutils.apk.Zip;
import com.android.utils.FileUtils;
import com.android.utils.Pair;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.util.GradleVersion;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * JUnit4 test rule for integration test.
 *
 * <p>This rule create a gradle project in a temporary directory. It can be use with the @Rule
 * or @ClassRule annotations. Using this class with @Rule will create a gradle project in separate
 * directories for each unit test, whereas using it with @ClassRule creates a single gradle project.
 *
 * <p>The test directory is always deleted if it already exists at the start of the test to ensure a
 * clean environment.
 */
public final class GradleTestProject implements TestRule {
    public static final String ENV_CUSTOM_REPO = "CUSTOM_REPO";

    public static final File TEST_PROJECT_DIR;

    public static final String DEFAULT_COMPILE_SDK_VERSION;
    public static final int LATEST_NDK_PLATFORM_VERSION = 21;
    /** Latest published Google APIs version. Update this once new version is out. */
    public static final int LATEST_GOOGLE_APIS_VERSION = 24;

    public static final String DEFAULT_BUILD_TOOL_VERSION;
    public static final String UPCOMING_BUILD_TOOL_VERSION = "25.0.0";
    public static final String REMOTE_TEST_PROVIDER = System.getenv().get("REMOTE_TEST_PROVIDER");

    public static final String DEFAULT_KOTLIN_PLUGIN_VERSION = "1.0.5";

    public static final String DEVICE_PROVIDER_NAME =
            REMOTE_TEST_PROVIDER != null ? REMOTE_TEST_PROVIDER : BuilderConstants.CONNECTED;

    public static final String GRADLE_TEST_VERSION;
    public static final String GRADLE_EXP_TEST_VERSION;

    public static final String ANDROID_GRADLE_PLUGIN_VERSION;

    @NonNull public static final File ANDROID_HOME;
    @NonNull public static final File ANDROID_NDK_HOME;

    public static final boolean USE_JACK;
    public static final boolean IMPROVED_DEPENDENCY_RESOLUTION;

    public static final String DEVICE_TEST_TASK = "deviceCheck";

    private static final int MAX_TEST_NAME_DIR_WINDOWS = 100;

    public static final File BUILD_DIR;
    public static final File OUT_DIR;
    public static final File GRADLE_USER_HOME;
    public static final File ANDROID_SDK_HOME;

    /**
     * List of Apk file reference that should be closed and deleted once the TestRule is done.
     * This is useful on Windows when Apk will lock the underlying file and most test code
     * do not use try-with-resources nor explicitly call close().
     *
     */
    private static final List<Apk> tmpApkFiles = new ArrayList<>();

    static {
        try {
            TEST_PROJECT_DIR =
                    TestUtils.getWorkspaceFile(
                            "tools/base/build-system/integration-test/test-projects");
            assertThat(TEST_PROJECT_DIR).isDirectory();

            String buildDirPath = System.getenv("TEST_TMPDIR");
            assertNotNull("$TEST_TEMPDIR not set", buildDirPath);
            BUILD_DIR = new File(buildDirPath);
            OUT_DIR = new File(BUILD_DIR, "tests");
            ANDROID_SDK_HOME = new File(BUILD_DIR, "ANDROID_SDK_HOME");

            // Use a temporary directory, so that shards don't share daemons. Gradle builds are not
            // hermetic anyway and Gradle does not clean up test runfiles, so use the same home
            // across invocations to save disk space.
            GRADLE_USER_HOME =
                    TestUtils.runningFromBazel()
                            ? createTempDirectory(BUILD_DIR.toPath(), "GRADLE_USER_HOME").toFile()
                            : new File(BUILD_DIR, "GRADLE_USER_HOME");

            boolean useNightly =
                    Boolean.parseBoolean(
                            System.getenv().getOrDefault("USE_GRADLE_NIGHTLY", "false"));

            String nightlyVersion = getLatestGradleCheckedIn();
            if (useNightly) {
                assertNotNull("Failed to find latest nightly version.", nightlyVersion);
            }

            GRADLE_TEST_VERSION =
                    useNightly ? nightlyVersion : BasePlugin.GRADLE_MIN_VERSION.toString();

            // For now, the two are in sync.
            GRADLE_EXP_TEST_VERSION = GRADLE_TEST_VERSION;

            // These are some properties that we use in the integration test projects, when generating
            // build.gradle files. In case you would like to change any of the parameters, for instance
            // when testing cross product of versions of buildtools, compile sdks, plugin versions,
            // there are corresponding system environment variable that you are able to set.
            String envBuildToolVersion = Strings.emptyToNull(System.getenv("CUSTOM_BUILDTOOLS"));
            DEFAULT_BUILD_TOOL_VERSION =
                    MoreObjects.firstNonNull(
                            envBuildToolVersion, AndroidBuilder.MIN_BUILD_TOOLS_REV.toString());

            String envVersion = Strings.emptyToNull(System.getenv().get("CUSTOM_PLUGIN_VERSION"));
            ANDROID_GRADLE_PLUGIN_VERSION =
                    MoreObjects.firstNonNull(envVersion, Version.ANDROID_GRADLE_PLUGIN_VERSION);

            String envJack = System.getenv().get("CUSTOM_JACK");
            USE_JACK = !Strings.isNullOrEmpty(envJack);

            IMPROVED_DEPENDENCY_RESOLUTION =
                    Strings.isNullOrEmpty(
                            System.getenv().get("CUSTOM_DISABLE_IMPROVED_DEPENDENCY_RESOLUTION"));

            String envCustomCompileSdk =
                    Strings.emptyToNull(System.getenv().get("CUSTOM_COMPILE_SDK"));
            DEFAULT_COMPILE_SDK_VERSION = MoreObjects.firstNonNull(envCustomCompileSdk, "24");

            String envCustomAndroidHome =
                    Strings.emptyToNull(System.getenv().get("CUSTOM_ANDROID_HOME"));
            if (envCustomAndroidHome != null) {
                ANDROID_HOME = new File(envCustomAndroidHome);
                assertThat(ANDROID_HOME).named("$CUSTOM_ANDROID_HOME").isDirectory();
            } else {
                ANDROID_HOME = TestUtils.getSdk();
            }

            String envCustomAndroidNdkHome =
                    Strings.emptyToNull(System.getenv().get("CUSTOM_ANDROID_NDK_HOME"));
            if (envCustomAndroidNdkHome != null) {
                ANDROID_NDK_HOME = new File(envCustomAndroidNdkHome);
                assertThat(ANDROID_NDK_HOME).named("$CUSTOM_ANDROID_NDK_HOME").isDirectory();
            } else {
                ANDROID_NDK_HOME =
                        TestUtils.runningFromBazel()
                                ? BazelIntegrationTestsSuite.NDK_IN_TMP.toFile()
                                : new File(ANDROID_HOME, SdkConstants.FD_NDK);
            }
        } catch (Throwable t) {
            // Print something to stdout, to give us a chance to debug initialization problems.
            System.out.println(Throwables.getStackTraceAsString(t));
            throw Throwables.propagate(t);
        }
    }

    public static final String PLAY_SERVICES_VERSION = "9.6.1";
    public static final String SUPPORT_LIB_VERSION = "25.2.0";
    public static final String TEST_SUPPORT_LIB_VERSION = "0.5";
    public static final int SUPPORT_LIB_MIN_SDK = 9;

    private static final String COMMON_HEADER = "commonHeader.gradle";
    private static final String COMMON_LOCAL_REPO = "commonLocalRepo.gradle";
    private static final String COMMON_BUILD_SCRIPT = "commonBuildScript.gradle";
    private static final String COMMON_VERSIONS = "commonVersions.gradle";
    private static final String DEFAULT_TEST_PROJECT_NAME = "project";

    private final String name;
    @Nullable private File testDir;
    private File sourceDir;
    private File buildFile;
    private File localProp;
    private final boolean withoutNdk;
    private final boolean withDependencyChecker;

    private final Collection<String> gradleProperties;

    @Nullable private final TestProject testProject;

    private final String targetGradleVersion;

    private final boolean useJack;
    private final boolean improvedDependencyEnabled;
    @Nullable private final String buildToolsVersion;

    @Nullable private final BenchmarkRecorder benchmarkRecorder;
    @NonNull private final Path relativeProfileDirectory;

    @Nullable private String heapSize;

    private GradleBuildResult lastBuildResult;
    private ProjectConnection projectConnection;
    private final GradleTestProject rootProject;
    private final List<ProjectConnection> openConnections;

    GradleTestProject(
            @Nullable String name,
            @Nullable TestProject testProject,
            boolean useJack,
            boolean improvedDependencyEnabled,
            @Nullable String targetGradleVersion,
            boolean withoutNdk,
            boolean withDependencyChecker,
            @NonNull Collection<String> gradleProperties,
            @Nullable String heapSize,
            @Nullable String buildToolsVersion,
            @Nullable BenchmarkRecorder benchmarkRecorder,
            @NonNull Path relativeProfileDirectory) {
        this.testDir = null;
        this.buildFile = sourceDir = null;
        this.name = (name == null) ? DEFAULT_TEST_PROJECT_NAME : name;
        this.improvedDependencyEnabled = improvedDependencyEnabled;
        this.useJack = useJack;
        this.targetGradleVersion = targetGradleVersion;
        this.testProject = testProject;
        this.withoutNdk = withoutNdk;
        this.withDependencyChecker = withDependencyChecker;
        this.heapSize = heapSize;
        this.gradleProperties = gradleProperties;
        this.buildToolsVersion = buildToolsVersion;
        this.benchmarkRecorder = benchmarkRecorder;
        this.openConnections = Lists.newArrayList();
        this.rootProject = this;
        this.relativeProfileDirectory = relativeProfileDirectory;
    }

    /**
     * Create a GradleTestProject representing a subProject of another GradleTestProject.
     *
     * @param subProject name of the subProject.
     * @param rootProject root GradleTestProject.
     */
    private GradleTestProject(@NonNull String subProject, @NonNull GradleTestProject rootProject) {
        name = subProject;

        testDir = new File(rootProject.getTestDir(), subProject);
        assertTrue("No subproject dir at " + getTestDir().toString(), getTestDir().isDirectory());

        buildFile = new File(getTestDir(), "build.gradle");
        sourceDir = new File(getTestDir(), "src");
        withoutNdk = rootProject.withoutNdk;
        withDependencyChecker = rootProject.withDependencyChecker;
        gradleProperties = ImmutableList.of();
        testProject = null;
        targetGradleVersion = rootProject.getTargetGradleVersion();
        useJack = false;
        improvedDependencyEnabled = rootProject.isImprovedDependencyEnabled();
        openConnections = null;
        buildToolsVersion = null;
        benchmarkRecorder = rootProject.benchmarkRecorder;
        this.rootProject = rootProject;
        this.relativeProfileDirectory = rootProject.relativeProfileDirectory;
    }

    private String getTargetGradleVersion() {
        return targetGradleVersion;
    }

    public static GradleTestProjectBuilder builder() {
        return new GradleTestProjectBuilder();
    }

    /** Crawls the tools/external/gradle dir, and gets the latest gradle binary. */
    @Nullable
    public static String getLatestGradleCheckedIn() {
        File gradleDir = TestUtils.getWorkspaceFile("tools/external/gradle");

        // should match gradle-3.4-201612071523+0000-bin.zip, and gradle-3.2-bin.zip
        Pattern gradleVersion = Pattern.compile("^gradle-(\\d+.\\d+)(-\\d+\\+\\d+)?-bin\\.zip$");

        Comparator<Pair<String, String>> revisionsCmp =
                Comparator.nullsFirst(
                        Comparator.comparing(
                                (Pair<String, String> versionTimestamp) ->
                                        GradleVersion.version(versionTimestamp.getFirst()))
                                .thenComparing(Pair::getSecond));

        Pair<String, String> highestRevision = null;
        //noinspection ConstantConditions
        for (File f : gradleDir.listFiles()) {
            Matcher matcher = gradleVersion.matcher(f.getName());
            if (matcher.matches()) {
                Pair<String, String> current =
                        Pair.of(matcher.group(1), Strings.nullToEmpty(matcher.group(2)));

                if (revisionsCmp.compare(highestRevision, current) < 0) {
                    highestRevision = current;
                }
            }
        }

        if (highestRevision == null) {
            return null;
        } else {
            return highestRevision.getFirst() + highestRevision.getSecond();
        }
    }

    /**
     * Recursively delete directory or file.
     *
     * @param root directory to delete
     */
    private static void deleteRecursive(File root) {
        if (root.exists()) {
            if (root.isDirectory()) {
                File files[] = root.listFiles();
                if (files != null) {
                    for (File file : files) {
                        deleteRecursive(file);
                    }
                }
            }
            assertTrue("Failed to delete " + root.getAbsolutePath(), root.delete());
        }
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                createTestDirectory(description.getTestClass(), description.getMethodName());
                boolean testFailed = false;
                try {
                    base.evaluate();
                } catch (Throwable e) {
                    testFailed = true;
                    throw e;
                } finally {
                    for (Apk tmpApkFile : tmpApkFiles) {
                        try {
                            tmpApkFile.close();
                        } catch(Exception e) {
                            System.err.println("Error while closing APK file : " + e.getMessage());
                        }
                        File tmpFile = tmpApkFile.getFile().toFile();
                        if (tmpFile.exists() && !tmpFile.delete()) {
                            System.err.println("Cannot delete temporary file "
                                    + tmpApkFile.getFile());
                        }
                    }
                    openConnections.forEach(ProjectConnection::close);
                    if (benchmarkRecorder != null) {
                        benchmarkRecorder.doUploads();
                    }
                    if (testFailed && lastBuildResult != null) {
                        System.err.println("==============================================");
                        System.err.println("= Test " + description + " failed. Last build:");
                        System.err.println("==============================================");
                        System.err.println("=================== Stderr ===================");
                        System.err.print(lastBuildResult.getStderr());
                        System.err.println("=================== Stdout ===================");
                        System.err.print(lastBuildResult.getStdout());
                        System.err.println("==============================================");
                        System.err.println("=============== End last build ===============");
                        System.err.println("==============================================");
                    }
                }
            }
        };
    }

    private void createTestDirectory(Class<?> testClass, String methodName)
            throws IOException, StreamException {
        // On windows, move the temporary copy as close to root to avoid running into path too
        // long exceptions.
        testDir =
                SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS
                        ? new File(new File(System.getProperty("user.home")), "android-tests")
                        : OUT_DIR;

        String classDir = testClass.getSimpleName();
        String methodDir = null;

        // Create separate directory based on test method name if @Rule is used.
        // getMethodName() is null if this rule is used as a @ClassRule.
        if (methodName != null) {
            methodDir = methodName.replaceAll("[^a-zA-Z0-9_]", "_");
        }

        // In Windows, make sure we do not exceed the limit for test class / name size.
        if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
            int totalLen = classDir.length();
            if (methodDir != null) {
                totalLen += methodDir.length();
            }

            if (totalLen > MAX_TEST_NAME_DIR_WINDOWS) {
                String hash =
                        Hashing.sha1()
                                .hashString(classDir + methodDir, Charsets.US_ASCII)
                                .toString();
                classDir = hash.substring(0, Math.min(hash.length(), MAX_TEST_NAME_DIR_WINDOWS));
                methodDir = null;
            }
        }

        testDir = new File(testDir, classDir);
        if (methodDir != null) {
            testDir = new File(testDir, methodDir);
        }

        testDir = new File(testDir, name);

        buildFile = new File(testDir, "build.gradle");
        sourceDir = new File(testDir, "src");

        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        FileUtils.mkdirs(testDir);
        FileUtils.mkdirs(sourceDir);

        Files.write(
                generateVersions(),
                new File(testDir.getParent(), COMMON_VERSIONS),
                StandardCharsets.UTF_8);
        Files.write(
                generateLocalRepoScript(),
                new File(testDir.getParent(), COMMON_LOCAL_REPO),
                StandardCharsets.UTF_8);
        Files.write(
                generateCommonHeader(),
                new File(testDir.getParent(), COMMON_HEADER),
                StandardCharsets.UTF_8);
        Files.copy(
                new File(TEST_PROJECT_DIR, COMMON_BUILD_SCRIPT),
                new File(testDir.getParent(), COMMON_BUILD_SCRIPT));

        if (testProject != null) {
            testProject.write(
                    testDir, testProject.containsFullBuildScript() ? "" : getGradleBuildscript());
        } else {
            Files.write(getGradleBuildscript(), buildFile, Charsets.UTF_8);
        }

        localProp = createLocalProp();
        createGradleProp();
    }

    @NonNull
    private String generateCommonHeader() {
        String result =
                String.format(
                        "ext {\n"
                                + "    buildToolsVersion = '%1$s'\n"
                                + "    latestCompileSdk = %2$s\n"
                                + "    kotlinVersion = '%4$s'\n"
                                + "\n"
                                + "    plugins.withId('com.android.application') {\n"
                                + "        apply plugin: 'devicepool'\n"
                                + "    }\n"
                                + "    plugins.withId('com.android.library') {\n"
                                + "        apply plugin: 'devicepool'\n"
                                + "    }\n"
                                + "    plugins.withId('com.android.model.application') {\n"
                                + "        apply plugin: 'devicepool'\n"
                                + "    }\n"
                                + "    plugins.withId('com.android.model.library') {\n"
                                + "        apply plugin: 'devicepool'\n"
                                + "    }\n"
                                + "}\n"
                                + "\n"
                                + "",
                        DEFAULT_BUILD_TOOL_VERSION,
                        DEFAULT_COMPILE_SDK_VERSION,
                        useJack,
                        DEFAULT_KOTLIN_PLUGIN_VERSION);
        if (withDependencyChecker) {
            result = result
                    + "// Check to ensure dependencies are not resolved during configuration.\n"
                    + "//\n"
                    + "// If it is intentional, create GradleTestProject without dependency checker"
                    + "// {@see GradleTestProjectBuilder#withDependencyChecker} or remove the"
                    + "// checker with:\n"
                    + "//     gradle.removeListener(rootProject.ext.dependencyResolutionChecker)\n"
                    + "//\n"
                    + "// Tips: If you need to trace down where the Configuration is resolved, it \n"
                    + "// may be helpful to call setCanBeResolved(false) on the Configuration of \n"
                    + "// interest to get a stacktrace.\n"
                    + "Boolean isTaskGraphReady = false\n"
                    + "gradle.taskGraph.whenReady { isTaskGraphReady = true }\n"
                    + "\n"
                    + "ext.dependencyResolutionChecker = new DependencyResolutionListener() {\n"
                    + "    @Override\n"
                    + "    void beforeResolve(ResolvableDependencies resolvableDependencies) {\n"
                    + "        if (!isTaskGraphReady\n"
                    + "                && !resolvableDependencies.getName().equals('classpath')\n"  // classpath is resolved to find the plugin.
                    + "                && !resolvableDependencies.getName().startsWith('testTarget')\n"  // TODO: Fix for test plugin.
                    + "                && project.findProperty(\"" + AndroidProject.PROPERTY_BUILD_MODEL_ONLY + "\")?.toBoolean() != true) {\n"
                    + "            throw new RuntimeException(\n"
                    + "                    \"Dependency '$resolvableDependencies.name' was resolved during configuration\")\n"
                    + "        }\n"
                    + "    }\n"
                    + "\n"
                    + "    @Override\n"
                    + "    void afterResolve(ResolvableDependencies resolvableDependencies) {}\n"
                    + "}\n"
                    + "\n"
                    + "gradle.addListener(dependencyResolutionChecker)\n";
        }
        return result;
    }

    @NonNull
    private static String generateLocalRepoScript() {
        StringBuilder script = new StringBuilder();
        script.append("repositories {\n");
        for (Path repo : getLocalRepositories()) {
            script.append(mavenSnippet(repo));
        }
        script.append("}\n");
        return script.toString();
    }

    @NonNull
    public static String mavenSnippet(@NonNull Path repo) {
        return String.format(
                "maven { url '%s' }\n", repo.toAbsolutePath().toString().replace("\\", "\\\\"));
    }

    @NonNull
    public static List<Path> getLocalRepositories() {
        List<Path> repos = new ArrayList<>();

        String customRepo = System.getenv(ENV_CUSTOM_REPO);
        if (customRepo != null) {
            // We're running under Gradle.
            // TODO: support USE_EXTERNAL_REPO
            repos.add(Paths.get(customRepo));
        } else {
            // We're running under Bazel. Make sure the setup is there.
            assertThat(BazelIntegrationTestsSuite.OFFLINE_REPO)
                    .named("Offline repo unzipped by BazelIntegrationTestsSuite.")
                    .isDirectory();

            repos.add(BazelIntegrationTestsSuite.OFFLINE_REPO);
            repos.add(BazelIntegrationTestsSuite.PREBUILTS_REPO);
        }
        return repos;
    }

    @NonNull
    private static String generateVersions() {
        return String.format(
                "// Generated by GradleTestProject::generateVersions%n"
                        + "buildVersion = '%s'%n"
                        + "experimentalVersion = '%s'%n"
                        + "baseVersion = '%s'%n"
                        + "experimentalGradleVersion = '%s'%n"
                        + "supportLibVersion = '%s'%n"
                        + "testSupportLibVersion = '%s'%n"
                        + "playServicesVersion = '%s'%n"
                        + "supportLibMinSdk = %d%n"
                        + "constraintLayoutVersion = '%s'%n",
                Version.ANDROID_GRADLE_PLUGIN_VERSION,
                Version.ANDROID_GRADLE_COMPONENT_PLUGIN_VERSION,
                Version.ANDROID_TOOLS_BASE_VERSION,
                GRADLE_EXP_TEST_VERSION,
                SUPPORT_LIB_VERSION,
                TEST_SUPPORT_LIB_VERSION,
                PLAY_SERVICES_VERSION,
                SUPPORT_LIB_MIN_SDK,
                SdkConstants.LATEST_CONSTRAINT_LAYOUT_VERSION);
    }

    /** Create a GradleTestProject representing a subproject. */
    public GradleTestProject getSubproject(String name) {
        if (name.startsWith(":")) {
            name = name.substring(1);
        }
        return new GradleTestProject(name, rootProject);
    }

    /** Return the name of the test project. */
    public String getName() {
        return name;
    }

    /** Return the directory containing the test project. */
    @NonNull
    public File getTestDir() {
        Preconditions.checkState(
                testDir != null, "getTestDir called before the project was properly initialized.");
        return testDir;
    }

    /** Return the path to the default Java main source dir. */
    public File getMainSrcDir() {
        return FileUtils.join(getTestDir(), "src", "main", "java");
    }

    /** Return the build.gradle of the test project. */
    public File getSettingsFile() {
        return new File(getTestDir(), "settings.gradle");
    }

    /** Return the gradle.properties file of the test project. */
    @NonNull
    public File getGradlePropertiesFile() {
        return new File(getTestDir(), "gradle.properties");
    }

    /** Return the build.gradle of the test project. */
    public File getBuildFile() {
        return buildFile;
    }

    /** Change the build file used for execute. Should be run after @Before/@BeforeClass. */
    public void setBuildFile(@Nullable String buildFileName) {
        checkNotNull(buildFile, "Cannot call selectBuildFile before test directory is created.");
        if (buildFileName == null) {
            buildFileName = "build.gradle";
        }
        buildFile = new File(getTestDir(), buildFileName);
        assertThat(buildFile).exists();
    }

    /** Return the output directory from Android plugins. */
    public File getOutputDir() {
        return new File(
                getTestDir(), Joiner.on(File.separator).join("build", AndroidProject.FD_OUTPUTS));
    }

    /** Return the output directory from Android plugins. */
    public File getIntermediatesDir() {
        return new File(
                getTestDir(),
                Joiner.on(File.separator).join("build", AndroidProject.FD_INTERMEDIATES));
    }

    /** Return a File under the output directory from Android plugins. */
    public File getOutputFile(String path) {
        return new File(getOutputDir(), path);
    }

    /** Return a File under the intermediates directory from Android plugins. */
    public File getIntermediateFile(String path) {
        return new File(getIntermediatesDir(), path);
    }

    /** Returns the directory to look for profiles in. Defaults to build/profile/ */
    @NonNull
    public Path getProfileDirectory() {
        return rootProject.getTestDir().toPath().resolve(relativeProfileDirectory);
    }

    /**
     * Return the output apk File from the application plugin for the given dimension.
     *
     * <p>Expected dimensions orders are: - product flavors - build type - other modifiers (e.g.
     * "unsigned", "aligned")
     */
    @NonNull
    public Apk getApk(String... dimensions) throws IOException {
        List<String> dimensionList = Lists.newArrayListWithExpectedSize(1 + dimensions.length);
        dimensionList.add(getName());
        dimensionList.addAll(Arrays.asList(dimensions));
        File apkFile =
                getOutputFile("apk/" + Joiner.on("-").join(dimensionList)
                        + SdkConstants.DOT_ANDROID_PACKAGE);
        Apk apk;
        if (OsType.getHostOs() == OsType.WINDOWS && apkFile.exists()) {
            File copy = File.createTempFile("tmp", ".apk");
            FileUtils.copyFile(apkFile, copy);
            apk = new Apk(copy) {
                @NonNull
                @Override
                public Path getFile() {
                    return apkFile.toPath();
                }
            };
            tmpApkFiles.add(apk);
        } else {
            apk = new Apk(apkFile);
        }
        return apk;
    }

    @NonNull
    public Apk getTestApk(String... dimensions) throws IOException {
        List<String> dimensionList = Lists.newArrayList(dimensions);
        dimensionList.add("androidTest");
        return getApk(Iterables.toArray(dimensionList, String.class));
    }

    /**
     * Return the output aar File from the library plugin for the given dimension.
     *
     * <p>Expected dimensions orders are: - product flavors - build type - other modifiers (e.g.
     * "unsigned", "aligned")
     */
    public Aar getAar(String... dimensions) throws IOException {
        List<String> dimensionList = Lists.newArrayListWithExpectedSize(1 + dimensions.length);
        dimensionList.add(getName());
        dimensionList.addAll(Arrays.asList(dimensions));
            return new Aar(getOutputFile(
                    "aar/" + Joiner.on("-").join(dimensionList) + SdkConstants.DOT_AAR));
    }

    /**
     * Return the output atombundle file from the atom plugin for the given dimension.
     *
     * <p>Expected dimensions orders are: - product flavors - build type - other modifiers (e.g.
     * "unsigned", "aligned")
     */
    public AtomBundle getAtomBundle(String... dimensions) {
        List<String> dimensionList = Lists.newArrayListWithExpectedSize(1 + dimensions.length);
        dimensionList.add(getName());
        dimensionList.addAll(Arrays.asList(dimensions));
        try {
            return new AtomBundle(getOutputFile(
                    FileUtils.join(
                            "atombundle",
                            Joiner.on("-").join(dimensionList) + SdkConstants.DOT_ATOMBUNDLE)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Return the output atom file from the instantApp plugin for the given atom name and dimension.
     *
     * <p>Expected dimensions orders are: - product flavors - build type - other modifiers (e.g.
     * "unsigned", "aligned")
     */
    public Apk getAtom(String atomName, String... dimensions) throws IOException {
            return new Apk(
                    getIntermediateFile(
                            FileUtils.join(
                                    "atoms",
                                    Joiner.on("-").join(dimensions),
                                    atomName + SdkConstants.DOT_ANDROID_PACKAGE)));
    }

    /**
     * Returns the output instantApp bundle file from the instantApp plugin for the given dimension.
     *
     * <p>Expected dimensions orders are: - product flavors - build type - other modifiers (e.g.
     * "unsigned", "aligned")
     */
    public Zip getInstantAppBundle(String... dimensions) {
        List<String> dimensionList = Lists.newArrayListWithExpectedSize(1 + dimensions.length);
        dimensionList.add(getName());
        dimensionList.addAll(Arrays.asList(dimensions));
        File zipFile =
                getOutputFile(
                        FileUtils.join(
                                "apk", Joiner.on("-").join(dimensionList) + SdkConstants.DOT_ZIP));
        try {
            return new Zip(zipFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns a string that contains the gradle buildscript content */
    public static String getGradleBuildscript() {
        return "apply from: \"../commonHeader.gradle\"\n"
                + "buildscript { apply from: \"../commonBuildScript.gradle\" }\n"
                + "\n"
                + "apply from: \"../commonLocalRepo.gradle\"\n";
    }

    @Nullable
    public BenchmarkRecorder getBenchmarkRecorder() {
        return benchmarkRecorder;
    }

    /** Fluent method to run a build. */
    public RunGradleTasks executor() {
        return new RunGradleTasks(this, getProjectConnection());
    }

    /** Fluent method to get the model. */
    @NonNull
    public BuildModel model() {
        return new BuildModel(this, getProjectConnection());
    }

    /**
     * Runs gradle on the project. Throws exception on failure.
     *
     * @param tasks Variadic list of tasks to execute.
     */
    public void execute(@NonNull String... tasks) throws IOException, InterruptedException {
        lastBuildResult = executor().run(tasks);
    }

    public void execute(@NonNull List<String> arguments, @NonNull String... tasks)
            throws IOException, InterruptedException {
        lastBuildResult = executor().withArguments(arguments).run(tasks);
    }

    public GradleConnectionException executeExpectingFailure(@NonNull String... tasks)
            throws IOException, InterruptedException {
        lastBuildResult = executor().expectFailure().run(tasks);
        return lastBuildResult.getException();
    }

    public void executeConnectedCheck() throws IOException, InterruptedException {
        lastBuildResult = executor().executeConnectedCheck();
    }

    public void executeConnectedCheck(@NonNull List<String> arguments)
            throws IOException, InterruptedException {
        lastBuildResult = executor().withArguments(arguments).executeConnectedCheck();
    }

    /**
     * Runs gradle on the project, and returns the project model. Throws exception on failure.
     *
     * @param tasks Variadic list of tasks to execute.
     * @return the AndroidProject model for the project.
     */
    @NonNull
    public ModelContainer<AndroidProject> executeAndReturnModel(@NonNull String... tasks)
            throws IOException, InterruptedException {
        lastBuildResult = executor().run(tasks);
        return model().getSingle();
    }

    /**
     * Runs gradle on the project, and returns the model of the specified type. Throws exception on
     * failure.
     *
     * @param modelClass Class of the model to return
     * @param tasks Variadic list of tasks to execute.
     * @return the model for the project with the specified type.
     */
    @NonNull
    public <T> T executeAndReturnModel(Class<T> modelClass, String... tasks)
            throws IOException, InterruptedException {
        lastBuildResult = executor().run(tasks);
        return model().getSingle(modelClass);
    }

    /**
     * Runs gradle on the project, and returns the project model. Throws exception on failure.
     *
     * @param modelLevel whether to emulate an older IDE (studio 1.0) querying the model.
     * @param tasks Variadic list of tasks to execute.
     * @return the AndroidProject model for the project.
     */
    @NonNull
    public ModelContainer<AndroidProject> executeAndReturnModel(int modelLevel, String... tasks)
            throws IOException, InterruptedException {
        lastBuildResult = executor().run(tasks);
        return model().level(modelLevel).getSingle();
    }

    /**
     * Runs gradle on the project, and returns the project model. Throws exception on failure.
     *
     * @param modelClass Class of the model to return
     * @param modelLevel whether to emulate an older IDE (studio 1.0) querying the model.
     * @param tasks Variadic list of tasks to execute.
     * @return the AndroidProject model for the project.
     */
    @NonNull
    public <T> T executeAndReturnModel(Class<T> modelClass, int modelLevel, String... tasks)
            throws IOException, InterruptedException {
        lastBuildResult = executor().run(tasks);
        return model().level(modelLevel).getSingle(modelClass);
    }

    /**
     * Runs gradle on the project, and returns a project model for each sub-project. Throws
     * exception on failure.
     *
     * @param tasks Variadic list of tasks to execute.
     * @return the AndroidProject model for the project.
     */
    @NonNull
    public ModelContainer<AndroidProject> executeAndReturnMultiModel(String... tasks)
            throws IOException, InterruptedException {
        lastBuildResult = executor().run(tasks);
        return model().getMulti();
    }

    /** Returns the latest build result. */
    public GradleBuildResult getBuildResult() {
        return lastBuildResult;
    }

    public void setLastBuildResult(GradleBuildResult lastBuildResult) {
        this.lastBuildResult = lastBuildResult;
    }

    /**
     * Create a File object. getTestDir will be the base directory if a relative path is supplied.
     *
     * @param path Full path of the file. May be a relative path.
     */
    public File file(String path) {
        File result = new File(FileUtils.toSystemDependentPath(path));
        if (result.isAbsolute()) {
            return result;
        } else {
            return new File(getTestDir(), path);
        }
    }

    /** Returns a Gradle project Connection */
    @NonNull
    private ProjectConnection getProjectConnection() {
        if (projectConnection != null) {
            return projectConnection;
        }
        GradleConnector connector = GradleConnector.newConnector();

        // Limit daemon idle time for tests. 10 seconds is enough for another test
        // to start and reuse the daemon.
        ((DefaultGradleConnector) connector).daemonMaxIdleTime(10, TimeUnit.SECONDS);

        File distributionDirectory = TestUtils.getWorkspaceFile("tools/external/gradle");
        String distributionName = String.format("gradle-%s-bin.zip", targetGradleVersion);
        File distributionZip = new File(distributionDirectory, distributionName);

        projectConnection =
                connector
                        .useDistribution(distributionZip.toURI())
                        .useGradleUserHomeDir(GRADLE_USER_HOME)
                        .forProjectDirectory(getTestDir())
                        .connect();

        rootProject.openConnections.add(projectConnection);

        return projectConnection;
    }

    private File createLocalProp() throws IOException, StreamException {
        checkNotNull(testDir, "project");

        ProjectPropertiesWorkingCopy localProp =
                ProjectProperties.create(
                        testDir.getAbsolutePath(), ProjectProperties.PropertyType.LOCAL);

        localProp.setProperty(ProjectProperties.PROPERTY_SDK, ANDROID_HOME.getAbsolutePath());
        if (!withoutNdk) {
            localProp.setProperty(
                    ProjectProperties.PROPERTY_NDK, ANDROID_NDK_HOME.getAbsolutePath());
        }

        localProp.save();
        return (File) localProp.getFile();
    }

    private void createGradleProp() throws IOException {
        if (gradleProperties.isEmpty()) {
            return;
        }
        Files.write(
                Joiner.on('\n').join(gradleProperties),
                getGradlePropertiesFile(),
                Charset.defaultCharset());
    }

    @Nullable
    String getHeapSize() {
        return heapSize;
    }

    boolean isUseJack() {
        return useJack;
    }

    public boolean isImprovedDependencyEnabled() {
        return improvedDependencyEnabled;
    }

    public File getLocalProp() {
        return localProp;
    }

    @Nullable
    public String getBuildToolsVersion() {
        return buildToolsVersion;
    }
}

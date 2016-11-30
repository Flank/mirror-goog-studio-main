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
import static org.junit.Assert.assertTrue;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.BasePlugin;
import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer;
import com.android.build.gradle.integration.performance.BenchmarkRecorder;
import com.android.build.gradle.model.Version;
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.AndroidProject;
import com.android.io.StreamException;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.project.ProjectPropertiesWorkingCopy;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * JUnit4 test rule for integration test.
 *
 * This rule create a gradle project in a temporary directory.
 * It can be use with the @Rule or @ClassRule annotations.  Using this class with @Rule will create
 * a gradle project in separate directories for each unit test, whereas using it with @ClassRule
 * creates a single gradle project.
 *
 * The test directory is always deleted if it already exists at the start of the test to ensure a
 * clean environment.
 */
public final class GradleTestProject implements TestRule {

    public static final File TEST_RES_DIR = new File("src/test/resources");
    public static final File TEST_PROJECT_DIR = new File("test-projects");

    public static final String DEFAULT_COMPILE_SDK_VERSION;
    public static final int LATEST_NDK_PLATFORM_VERSION = 21;
    /** Latest published Google APIs version. Update this once new version is out. */
    public static final int LATEST_GOOGLE_APIS_VERSION = 24;

    public static final String DEFAULT_BUILD_TOOL_VERSION;
    public static final String UPCOMING_BUILD_TOOL_VERSION = "25.0.0";
    public static final String REMOTE_TEST_PROVIDER = System.getenv().get("REMOTE_TEST_PROVIDER");

    public static final String DEVICE_PROVIDER_NAME =
            REMOTE_TEST_PROVIDER != null ? REMOTE_TEST_PROVIDER : BuilderConstants.CONNECTED;

    public static final String GRADLE_TEST_VERSION;
    public static final String GRADLE_EXP_TEST_VERSION;
    public static final String GRADLE_NIGHTLY_VERSION = "3.3-20161111000054+0000";

    public static final String ANDROID_GRADLE_PLUGIN_VERSION;

    public static final boolean USE_JACK;
    public static final boolean IMPROVED_DEPENDENCY_RESOLUTION;

    public static final String DEVICE_TEST_TASK = "deviceCheck";

    private static final int MAX_TEST_NAME_DIR_WINDOWS = 100;

    static {
        boolean useNightly =
                Boolean.parseBoolean(System.getenv().getOrDefault("USE_GRADLE_NIGHTLY", "false"));
        GRADLE_TEST_VERSION =
                useNightly ? GRADLE_NIGHTLY_VERSION : BasePlugin.GRADLE_MIN_VERSION.toString();

        // For now, the two are in sync.
        GRADLE_EXP_TEST_VERSION = GRADLE_TEST_VERSION;

        // These are some properties that we use in the integration test projects, when generating
        // build.gradle files. In case you would like to change any of the parameters, for instance
        // when testing cross product of versions of buildtools, compile sdks, plugin versions,
        // there are corresponding system environment variable that you are able to set.
        String envBuildToolVersion = Strings.emptyToNull(System.getenv("CUSTOM_BUILDTOOLS"));
        DEFAULT_BUILD_TOOL_VERSION = MoreObjects.firstNonNull(envBuildToolVersion, "25.0.0");

        String envVersion = Strings.emptyToNull(System.getenv().get("CUSTOM_PLUGIN_VERSION"));
        ANDROID_GRADLE_PLUGIN_VERSION =
                MoreObjects.firstNonNull(envVersion, Version.ANDROID_GRADLE_PLUGIN_VERSION);

        String envJack = System.getenv().get("CUSTOM_JACK");
        USE_JACK = !Strings.isNullOrEmpty(envJack);

        IMPROVED_DEPENDENCY_RESOLUTION = !Strings.isNullOrEmpty(
                System.getenv().get("IMPROVED_DEPENDENCY_RESOLUTION"));

        String envCustomCompileSdk = Strings.emptyToNull(System.getenv().get("CUSTOM_COMPILE_SDK"));
        DEFAULT_COMPILE_SDK_VERSION = MoreObjects.firstNonNull(envCustomCompileSdk, "24");
    }

    public static final String PLAY_SERVICES_VERSION = "9.6.1";
    public static final String SUPPORT_LIB_VERSION = "25.0.0";
    public static final String TEST_SUPPORT_LIB_VERSION = "0.5";
    public static final int SUPPORT_LIB_MIN_SDK = 9;

    private static final String COMMON_HEADER = "commonHeader.gradle";
    private static final String COMMON_LOCAL_REPO = "commonLocalRepo.gradle";
    private static final String COMMON_BUILD_SCRIPT = "commonBuildScript.gradle";
    private static final String COMMON_VERSIONS = "commonVersions.gradle";
    private static final String DEFAULT_TEST_PROJECT_NAME = "project";

    private final String name;
    private final File outDir;
    @Nullable
    private File testDir;
    private File sourceDir;
    private File buildFile;
    private File localProp;
    private final File ndkDir;
    private final File sdkDir;

    private final Collection<String> gradleProperties;

    @Nullable
    private final TestProject testProject;

    private final String targetGradleVersion;

    private final boolean useJack;
    private final boolean minifyEnabled;
    private final boolean improvedDependencyEnabled;
    @Nullable
    private final String buildToolsVersion;

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
            boolean minifyEnabled,
            boolean useJack,
            boolean improvedDependencyEnabled,
            String targetGradleVersion,
            @Nullable File sdkDir,
            @Nullable File ndkDir,
            @NonNull Collection<String> gradleProperties,
            @Nullable String heapSize,
            @Nullable String buildToolsVersion,
            @Nullable BenchmarkRecorder benchmarkRecorder,
            @NonNull Path relativeProfileDirectory) {
        String buildDir = System.getenv("PROJECT_BUILD_DIR");
        this.outDir = (buildDir == null) ? new File("build/tests") : new File(buildDir, "tests");
        this.testDir = null;
        this.buildFile = sourceDir = null;
        this.name = (name == null) ? DEFAULT_TEST_PROJECT_NAME : name;
        this.minifyEnabled = minifyEnabled;
        this.improvedDependencyEnabled = improvedDependencyEnabled;
        this.useJack = useJack;
        this.targetGradleVersion = targetGradleVersion;
        this.testProject = testProject;
        this.sdkDir = sdkDir;
        this.ndkDir = ndkDir;
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
     * @param subProject name of the subProject.
     * @param rootProject root GradleTestProject.
     */
    private GradleTestProject(
            @NonNull String subProject,
            @NonNull GradleTestProject rootProject) {
        name = subProject;
        outDir = rootProject.outDir;

        testDir = new File(rootProject.testDir, subProject);
        assertTrue("No subproject dir at " + testDir.toString(), testDir.isDirectory());

        buildFile = new File(testDir, "build.gradle");
        sourceDir = new File(testDir, "src");
        ndkDir = rootProject.ndkDir;
        sdkDir = rootProject.sdkDir;
        gradleProperties = ImmutableList.of();
        testProject = null;
        targetGradleVersion = rootProject.getTargetGradleVersion();
        minifyEnabled = false;
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
                try {
                    base.evaluate();
                } finally {
                    openConnections.forEach(ProjectConnection::close);
                    if (benchmarkRecorder != null) {
                        benchmarkRecorder.doUploads();
                    }
                }
            }
        };
    }

    private void createTestDirectory(Class<?> testClass, String methodName)
            throws IOException, StreamException {
        // On windows, move the temporary copy as close to root to avoid running into path too
        // long exceptions.
        testDir = SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS
                ? new File(new File(System.getProperty("user.home")), "android-tests")
                : outDir;

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
                        Hashing.sha1().hashString(classDir + methodDir, Charsets.US_ASCII)
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
        Files.copy(
                new File(TEST_PROJECT_DIR, COMMON_HEADER),
                new File(testDir.getParent(), COMMON_HEADER));
        Files.copy(
                new File(TEST_PROJECT_DIR, COMMON_LOCAL_REPO),
                new File(testDir.getParent(), COMMON_LOCAL_REPO));
        Files.copy(
                new File(TEST_PROJECT_DIR, COMMON_BUILD_SCRIPT),
                new File(testDir.getParent(), COMMON_BUILD_SCRIPT));

        if (testProject != null) {
            testProject.write(
                    testDir,
                    testProject.containsFullBuildScript() ? "" :getGradleBuildscript());
        } else {
            Files.write(
                    getGradleBuildscript(),
                    buildFile,
                    Charsets.UTF_8);
        }

        localProp = createLocalProp(testDir, sdkDir, ndkDir);
        createGradleProp();
    }

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
                        + "supportLibMinSdk = %d%n",
                Version.ANDROID_GRADLE_PLUGIN_VERSION,
                Version.ANDROID_GRADLE_COMPONENT_PLUGIN_VERSION,
                Version.ANDROID_TOOLS_BASE_VERSION,
                GRADLE_EXP_TEST_VERSION,
                SUPPORT_LIB_VERSION,
                TEST_SUPPORT_LIB_VERSION,
                PLAY_SERVICES_VERSION,
                SUPPORT_LIB_MIN_SDK);
    }

    /**
     * Create a GradleTestProject representing a subproject.
     */
    public GradleTestProject getSubproject(String name) {
        if (name.startsWith(":")) {
            name = name.substring(1);
        }
        return new GradleTestProject(name, rootProject);
    }

    /**
     * Return the name of the test project.
     */
    public String getName() {
        return name;
    }

    /**
     * Return the directory containing the test project.
     */
    public File getTestDir() {
        return testDir;
    }

    /**
     * Return the path to the default Java main source dir.
     */
    public File getMainSrcDir() {
        assertThat(testDir).isNotNull();
        return FileUtils.join(testDir, "src", "main", "java");
    }

    /**
     * Return the build.gradle of the test project.
     */
    public File getSettingsFile() {
        return new File(testDir, "settings.gradle");
    }

    /**
     * Return the build.gradle of the test project.
     */
    public File getBuildFile() {
        return buildFile;
    }

    /**
     * Change the build file used for execute.  Should be run after @Before/@BeforeClass.
     */
    public void setBuildFile(@Nullable String buildFileName) {
        Preconditions.checkNotNull(
                buildFile,
                "Cannot call selectBuildFile before test directory is created.");
        if (buildFileName == null) {
            buildFileName = "build.gradle";
        }
        buildFile = new File(testDir, buildFileName);
        assertThat(buildFile).exists();
    }


    /**
     * Return the output directory from Android plugins.
     */
    public File getOutputDir() {
        return new File(testDir,
                Joiner.on(File.separator).join("build", AndroidProject.FD_OUTPUTS));
    }

    /**
     * Return the output directory from Android plugins.
     */
    public File getIntermediatesDir() {
        return new File(testDir,
                Joiner.on(File.separator).join("build", AndroidProject.FD_INTERMEDIATES));
    }

    /**
     * Return a File under the output directory from Android plugins.
     */
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
        return rootProject.testDir.toPath().resolve(relativeProfileDirectory);
    }

    /**
     * Return the output apk File from the application plugin for the given dimension.
     *
     * Expected dimensions orders are:
     *   - product flavors
     *   - build type
     *   - other modifiers (e.g. "unsigned", "aligned")
     */
    public File getApk(String... dimensions) {
        List<String> dimensionList = Lists.newArrayListWithExpectedSize(1 + dimensions.length);
        dimensionList.add(getName());
        dimensionList.addAll(Arrays.asList(dimensions));
        return getOutputFile(
                "apk/" + Joiner.on("-").join(dimensionList) + SdkConstants.DOT_ANDROID_PACKAGE);
    }

    public File getTestApk(String... dimensions) {
        List<String> dimensionList = Lists.newArrayList(dimensions);
        dimensionList.add("androidTest");
        return getApk(Iterables.toArray(dimensionList, String.class));
    }

    /**
     * Return the output aar File from the library plugin for the given dimension.
     *
     * Expected dimensions orders are:
     *   - product flavors
     *   - build type
     *   - other modifiers (e.g. "unsigned", "aligned")
     */
    public File getAar(String... dimensions) {
        List<String> dimensionList = Lists.newArrayListWithExpectedSize(1 + dimensions.length);
        dimensionList.add(getName());
        dimensionList.addAll(Arrays.asList(dimensions));
        return getOutputFile("aar/" + Joiner.on("-").join(dimensionList) + SdkConstants.DOT_AAR);
    }

    /**
     * Return the output atombundle file from the atom plugin for the given dimension.
     *
     * <p>Expected dimensions orders are: - product flavors - build type - other modifiers (e.g.
     * "unsigned", "aligned")
     */
    public File getAtomBundle(String... dimensions) {
        List<String> dimensionList = Lists.newArrayListWithExpectedSize(1 + dimensions.length);
        dimensionList.add(getName());
        dimensionList.addAll(Arrays.asList(dimensions));
        return getOutputFile(
                FileUtils.join(
                        "atombundle",
                        Joiner.on("-").join(dimensionList) + SdkConstants.DOT_ATOMBUNDLE));
    }

    /**
     * Return the output atom File from the instantApp plugin for the given atom name and dimension.
     *
     * <p>Expected dimensions orders are: - product flavors - build type - other modifiers (e.g.
     * "unsigned", "aligned")
     */
    public File getAtom(String atomName, String... dimensions) {
        return getIntermediateFile(
                FileUtils.join(
                        "assets",
                        Joiner.on("-").join(dimensions),
                        atomName + SdkConstants.DOT_ATOM));
    }

    /** Returns the SDK dir */
    public File getSdkDir() {
        return sdkDir;
    }

    /**
     * Returns the NDK dir
     */
    public File getNdkDir() {
        return ndkDir;
    }

    /**
     * Returns a string that contains the gradle buildscript content
     */
    public static String getGradleBuildscript() {
        return "apply from: \"../commonHeader.gradle\"\n" +
               "buildscript { apply from: \"../commonBuildScript.gradle\" }\n" +
               "\n" +
               "apply from: \"../commonLocalRepo.gradle\"\n";
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
     * Runs gradle on the project.  Throws exception on failure.
     *
     * @param tasks Variadic list of tasks to execute.
     */
    public void execute(@NonNull String... tasks) {
        lastBuildResult = executor().run(tasks);
    }


    public void execute(@NonNull List<String> arguments, @NonNull String... tasks) {
        lastBuildResult = executor().withArguments(arguments).run(tasks);
    }

    public GradleConnectionException executeExpectingFailure(@NonNull String... tasks) {
        lastBuildResult =  executor().expectFailure().run(tasks);
        return lastBuildResult.getException();
    }

    public void executeConnectedCheck() {
        lastBuildResult = executor().executeConnectedCheck();
    }

    public void executeConnectedCheck(@NonNull List<String> arguments) {
        lastBuildResult = executor().withArguments(arguments).executeConnectedCheck();
    }

    /**
     * Runs gradle on the project, and returns the project model.  Throws exception on failure.
     *
     * @param tasks Variadic list of tasks to execute.
     *
     * @return the AndroidProject model for the project.
     */
    @NonNull
    public ModelContainer<AndroidProject> executeAndReturnModel(@NonNull String... tasks) {
        lastBuildResult = executor().run(tasks);
        return model().getSingle();
    }

    /**
     * Runs gradle on the project, and returns the model of the specified type.
     * Throws exception on failure.
     *
     * @param modelClass Class of the model to return
     * @param tasks Variadic list of tasks to execute.
     *
     * @return the model for the project with the specified type.
     */
    @NonNull
    public <T> T executeAndReturnModel(Class<T> modelClass, String... tasks) {
        lastBuildResult = executor().run(tasks);
        return model().getSingle(modelClass);
    }

    /**
     * Runs gradle on the project, and returns the project model.  Throws exception on failure.
     *
     * @param modelLevel whether to emulate an older IDE (studio 1.0) querying the model.
     * @param tasks Variadic list of tasks to execute.
     *
     * @return the AndroidProject model for the project.
     */
    @NonNull
    public ModelContainer<AndroidProject> executeAndReturnModel(int modelLevel, String... tasks) {
        lastBuildResult = executor().run(tasks);
        return model().level(modelLevel).getSingle();
    }

    /**
     * Runs gradle on the project, and returns the project model.  Throws exception on failure.
     *
     * @param modelClass Class of the model to return
     * @param modelLevel whether to emulate an older IDE (studio 1.0) querying the model.
     * @param tasks Variadic list of tasks to execute.
     *
     * @return the AndroidProject model for the project.
     */
    @NonNull
    public <T> T executeAndReturnModel(
            Class<T> modelClass,
            int modelLevel,
            String... tasks) {
        lastBuildResult = executor().run(tasks);
        return model().level(modelLevel).getSingle(modelClass);
    }

    /**
     * Runs gradle on the project, and returns a project model for each sub-project.
     * Throws exception on failure.
     *
     * @param tasks Variadic list of tasks to execute.
     *
     * @return the AndroidProject model for the project.
     */
    @NonNull
    public ModelContainer<AndroidProject> executeAndReturnMultiModel(String... tasks) {
        lastBuildResult = executor().run(tasks);
        return model().getMulti();
    }

    /**
     * Returns the latest build result.
     */
    public GradleBuildResult getBuildResult() {
        return lastBuildResult;

    }

    /**
     * Create a File object.  getTestDir will be the base directory if a relative path is supplied.
     *
     * @param path Full path of the file.  May be a relative path.
     */
    public File file(String path) {
        File result = new File(FileUtils.toSystemDependentPath(path));
        if (result.isAbsolute()) {
            return result;
        } else {
            return new File(testDir, path);
        }
    }

    /**
     * Returns a Gradle project Connection
     */
    @NonNull
    private ProjectConnection getProjectConnection() {
        if (projectConnection != null) {
            return projectConnection;
        }
        GradleConnector connector = GradleConnector.newConnector();

        // Limit daemon idle time for tests. 10 seconds is enough for another test
        // to start and reuse the daemon.
        ((DefaultGradleConnector) connector).daemonMaxIdleTime(10, TimeUnit.SECONDS);

        projectConnection = connector
                .useGradleVersion(targetGradleVersion)
                .forProjectDirectory(testDir)
                .connect();

        rootProject.openConnections.add(projectConnection);

        return projectConnection;
    }

    private static File createLocalProp(
            @NonNull File project,
            @NonNull File sdkDir,
            @Nullable File ndkDir) throws IOException, StreamException {
        ProjectPropertiesWorkingCopy localProp = ProjectProperties.create(
                project.getAbsolutePath(), ProjectProperties.PropertyType.LOCAL);
        localProp.setProperty(ProjectProperties.PROPERTY_SDK, sdkDir.getAbsolutePath());
        if (ndkDir != null) {
            localProp.setProperty(ProjectProperties.PROPERTY_NDK, ndkDir.getAbsolutePath());
        }
        localProp.save();

        return (File) localProp.getFile();
    }

    private void createGradleProp() throws IOException {
        if (gradleProperties.isEmpty()) {
            return;
        }
        File propertyFile = file("gradle.properties");
        Files.write(Joiner.on('\n').join(gradleProperties), propertyFile, Charset.defaultCharset());
    }

    @Nullable
    String getHeapSize() {
        return heapSize;
    }

    boolean isUseJack() {
        return useJack;
    }

    boolean isMinifyEnabled() {
        return minifyEnabled;
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

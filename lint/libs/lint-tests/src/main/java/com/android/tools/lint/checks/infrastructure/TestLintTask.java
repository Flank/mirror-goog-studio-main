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

package com.android.tools.lint.checks.infrastructure;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.DOT_GRADLE;
import static com.android.SdkConstants.DOT_KT;
import static com.android.SdkConstants.DOT_KTS;
import static com.android.tools.lint.client.api.LintClient.CLIENT_UNIT_TESTS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.tools.lint.LintCliFlags;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.checks.infrastructure.TestFile.GradleTestFile;
import com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.ConfigurationHierarchy;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.JarFileIssueRegistry;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintListener;
import com.android.tools.lint.client.api.LintXmlConfiguration;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Desugaring;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.LintModelModuleProject;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Platform;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.utils.NullLogger;
import com.android.utils.Pair;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.ObjectArrays;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckReturnValue;

@SuppressWarnings("SameParameterValue")
public class TestLintTask {
    /** Map from project directory to corresponding Gradle model mocker */
    final Map<File, GradleModelMocker> projectMocks = Maps.newHashMap();
    /** Map from project directory to corresponding Gradle model mocker */
    final Map<File, ProjectDescription> dirToProjectDescription = Maps.newHashMap();
    /** Cache for {@link #getCheckedIssues()} */
    private List<Issue> checkedIssues;
    /** Whether the {@link #run} method has already been invoked */
    private boolean alreadyRun;

    // Configuration options

    protected ProjectDescription[] projects;
    private boolean impliedProject;
    boolean allowCompilationErrors;
    boolean allowObsoleteLintChecks = true;
    boolean allowSystemErrors = true;
    String incrementalFileName;
    Issue[] issues;
    String[] issueIds;
    boolean allowDelayedIssueRegistration;
    public File sdkHome;
    LintListener listener;
    GradleMockModifier mockModifier;
    LintDriverConfigurator driverConfigurator;
    OptionSetter optionSetter;
    ErrorMessageChecker messageChecker;
    ProjectInspector projectInspector;
    String variantName;
    EnumSet<Scope> customScope;
    public boolean forceSymbolResolutionErrors;
    ClientFactory clientFactory;
    Detector detector;
    File[] customRules;
    boolean ignoreUnknownGradleConstructs;
    Boolean supportResourceRepository;
    boolean allowMissingSdk;
    boolean requireCompileSdk;
    boolean vital;
    Map<String, byte[]> mockNetworkData;
    boolean allowNetworkAccess;
    boolean allowDuplicates;
    File rootDirectory;
    File tempDir;
    private TestFile baseline;
    File baselineFile;
    TestFile overrideConfig;
    File overrideConfigFile;
    Set<Desugaring> desugaring;
    EnumSet<Platform> platforms;
    boolean checkUInjectionHost = true;
    boolean useTestProject;
    boolean allowExceptions;
    public String testName = null;
    boolean useTestConfiguration = true;
    boolean stripRoot = true;

    /** Creates a new lint test task */
    public TestLintTask() {
        LintClient.setClientName(CLIENT_UNIT_TESTS);
        BuiltinIssueRegistry.reset();
        tempDir = createTempDirectory();
    }

    @NonNull
    private static File createTempDirectory() {
        try {
            return Files.createTempDirectory("").toFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Creates a new lint test task */
    @CheckReturnValue
    @NonNull
    public static TestLintTask lint() {
        return new TestLintTask();
    }

    /** Creates a new lint test task */
    public TestLintTask(@NonNull ProjectDescription[] projects) {
        this.projects = projects;
    }

    /**
     * Configures the test task to check the given test projects
     *
     * @return this, for constructor chaining
     */
    public TestLintTask projects(@NonNull ProjectDescription... projects) {
        ensurePreRun();
        this.projects = projects;
        return this;
    }

    /**
     * Configures the test task to check the given test files
     *
     * @return this, for constructor chaining
     */
    public TestLintTask files(@NonNull TestFile... files) {
        ensurePreRun();
        this.projects = new ProjectDescription[] {new ProjectDescription(files)};
        this.impliedProject = true;
        return this;
    }

    /**
     * Configures the test task to allow compilation errors in the test files (normally not allowed)
     *
     * @return this, for constructor chaining
     */
    public TestLintTask allowCompilationErrors() {
        ensurePreRun();
        this.allowCompilationErrors = true;
        return this;
    }

    /**
     * Sets whether the test task should allow compilation errors in the test files
     *
     * @return this, for constructor chaining
     */
    public TestLintTask allowCompilationErrors(boolean allow) {
        ensurePreRun();
        this.allowCompilationErrors = allow;
        return this;
    }

    /**
     * Sets whether the test task should allow lint custom checks; if not, these will be flagged
     * with an extra warning ({@link IssueRegistry#OBSOLETE_LINT_CHECK}).
     *
     * @return this, for constructor chaining
     */
    public TestLintTask allowObsoleteLintChecks(boolean allow) {
        ensurePreRun();
        this.allowObsoleteLintChecks = allow;
        return this;
    }

    /**
     * Configures the test task to allow the SDK to be missing. To set a specific SDK home, use
     * {@link #sdkHome(File)}.
     *
     * @return this, for constructor chaining
     */
    public TestLintTask allowMissingSdk() {
        return allowMissingSdk(true);
    }

    /**
     * Sets whether the test task should allow the SDK to be missing. Normally false. To set a
     * specific SDK home, use {@link #sdkHome(File)}.
     *
     * @param allowMissingSdk whether the SDK should be allowed to be missing
     * @return this, for constructor chaining
     */
    public TestLintTask allowMissingSdk(boolean allowMissingSdk) {
        ensurePreRun();
        this.allowMissingSdk = allowMissingSdk;
        return this;
    }

    /**
     * Configures the test task to require that the compileSdkVersion (specified in the project
     * description) must be installed.
     *
     * @return this, for constructor chaining
     */
    public TestLintTask requireCompileSdk() {
        return requireCompileSdk(true);
    }

    /**
     * Sets whether the test requires that the compileSdkVersion (specified in the project
     * description) must be installed.
     *
     * @param requireCompileSdk true to require the compileSdkVersion SDK to be installed
     * @return this, for constructor chaining
     */
    public TestLintTask requireCompileSdk(boolean requireCompileSdk) {
        ensurePreRun();
        this.requireCompileSdk = requireCompileSdk;
        return this;
    }

    /**
     * Sets whether the test task should silently ignore lint infrastructure errors (such as missing
     * .class files etc). This does not include exceptions from lint detectors; for that, see {@link
     * #allowExceptions}.
     *
     * @return this, for constructor chaining
     */
    public TestLintTask allowSystemErrors(boolean allow) {
        ensurePreRun();
        this.allowSystemErrors = allow;
        return this;
    }

    /**
     * Configures the test task to run incrementally, with the given file as the current file
     *
     * @param currentFileName the relative path to the current file
     * @return this, for constructor chaining
     */
    public TestLintTask incremental(@NonNull String currentFileName) {
        ensurePreRun();
        this.incrementalFileName = currentFileName;
        return this;
    }

    /**
     * Configures the test task to use the given detector when determining which issues to run. If
     * you're calling {@link #issues(Issue...)} you do not need to call this method, but for
     * detectors that report a lot of issues, this is more convenient. (This requires the set of
     * issues produced by a detector to be static fields in the detector class.)
     *
     * @param detector the detector to use to discover the set of issues
     * @return this, for constructor chaining
     */
    public TestLintTask detector(@NonNull Detector detector) {
        this.detector = detector;
        checkedIssues = null; // force recompute
        return this;
    }

    /**
     * Configures the test task to look for the given set of issues.
     *
     * @param issues the set of issues to check
     * @return this, for constructor chaining
     */
    public TestLintTask issues(@NonNull Issue... issues) {
        ensurePreRun();
        this.issues = issues;
        for (Issue issue : issues) {
            if (issue == IssueRegistry.LINT_ERROR) {
                allowSystemErrors = true;
                break;
            }
        }
        checkedIssues = null; // force recompute
        return this;
    }

    /**
     * Configures the test task to run with the given baseline
     *
     * @param baseline the baseline XML contents
     * @return this, for constructor chaining
     */
    public TestLintTask baseline(@NonNull TestFile baseline) {
        ensurePreRun();
        this.baseline = baseline;
        return this;
    }

    /**
     * Configures the test task to run with the given override configuration.
     *
     * @param overrideConfig the override config XML contents
     * @return this, for constructor chaining
     */
    public TestLintTask overrideConfig(@NonNull TestFile overrideConfig) {
        ensurePreRun();
        this.overrideConfig = overrideConfig;
        return this;
    }

    /**
     * Whether the lint test infrastructure should insert a special test configuration which exactly
     * enables the specific issues analyzed by this test task ({@see #issues}), and disables all
     * others. This makes it easier to write tests: you don't start picking up warnings from
     * unrelated other checks, and if it's a check that's disabled by default, you don't have to
     * provide a test configuration to override it.
     *
     * <p>However, lint itself need to test the behavior of lint.xml inheritance, and the test
     * configurations interfere with this. This flag (which is on by default) can be turned off to
     * use only the configurations present in the project.
     *
     * @param useTestConfiguration the override config XML contents
     * @return this, for constructor chaining
     */
    public TestLintTask useTestConfiguration(boolean useTestConfiguration) {
        ensurePreRun();
        this.useTestConfiguration = useTestConfiguration;
        return this;
    }

    /**
     * Configures the test task to look for the given set of issue ids.
     *
     * @param ids the set of issues to check
     * @return this, for constructor chaining
     */
    public TestLintTask issueIds(@NonNull String... ids) {
        ensurePreRun();
        this.issueIds = ids;
        for (String id : ids) {
            if (IssueRegistry.LINT_ERROR.getId().equals(id)) {
                allowSystemErrors = true;
                break;
            }
        }
        checkedIssues = null; // force recompute
        return this;
    }

    /**
     * Normally you're forced to pick issue id's to register up front. However, for custom views you
     * may not want those issues to be discovered until the project has been initialized and the
     * custom views read from lint.jar files provided by the project dependencies. In that case, you
     * can disable the check which enforces that at least one issue is registered (which in normal
     * scenarios helps catch incorrect lint test setups.)
     *
     * @param allowDelayedIssueRegistration if true, allow delayed issue registration
     * @return this, for constructor chaining
     */
    public TestLintTask allowDelayedIssueRegistration(boolean allowDelayedIssueRegistration) {
        this.allowDelayedIssueRegistration = allowDelayedIssueRegistration;
        checkedIssues = null; // force recompute
        return this;
    }

    /**
     * Normally you're forced to pick issue id's to register up front. However, for custom views you
     * may not want those issues to be discovered until the project has been initialized and the
     * custom views read from lint.jar files provided by the project dependencies. In that case, you
     * can disable the check which enforces that at least one issue is registered (which in normal
     * scenarios helps catch incorrect lint test setups.)
     *
     * @return this, for constructor chaining
     */
    public TestLintTask allowDelayedIssueRegistration() {
        return allowDelayedIssueRegistration(true);
    }

    /**
     * Configures the test task to look for issues in the given set of custom rule jars
     *
     * @param customRuleJars the jar files to look for issues in
     * @return this, for constructor chaining
     */
    @NonNull
    public TestLintTask customRules(@NonNull File... customRuleJars) {
        this.customRules = customRuleJars;
        checkedIssues = null; // force recompute
        return this;
    }

    /**
     * Configures the test task to run incrementally. This method can be called if the project only
     * contains a single file, which will be considered the current file. If there are multiple
     * files in the project you must call {@link #incremental(String)} instead.
     *
     * @return this, for constructor chaining
     */
    public TestLintTask incremental() {
        ensurePreRun();
        if (projects != null
                && projects.length == 1
                && projects[0].getFiles() != null
                && projects[0].getFiles().length == 1) {
            this.incrementalFileName = projects[0].getFiles()[0].getTargetPath();
        } else if (projects == null || projects.length == 0) {
            fail("Can't use incremental mode without any projects!");
        } else {
            StringBuilder sb = new StringBuilder();
            for (ProjectDescription project : projects) {
                for (TestFile file : project.getFiles()) {
                    sb.append("\n");
                    if (!project.getName().isEmpty()) {
                        sb.append(project.getName()).append("/");
                    }
                    sb.append(file.getTargetPath());
                }
            }
            fail(
                    "Can only use implicit incremental mode when there is a single "
                            + "source file; use incremental(relativePath) instead. Perhaps you "
                            + "meant one of the following: "
                            + sb.toString());
        }
        return this;
    }

    /**
     * Configures the lint task to notify the given {@link LintListener during
     * execution.
     *
     * @param listener the listener to register
     * @return this, for constructor chaining
     */
    public TestLintTask listener(@NonNull LintListener listener) {
        ensurePreRun();
        this.listener = listener;
        return this;
    }

    /**
     * Configures the lint task with a given SDK home to use instead of the default one.
     *
     * @param sdkHomeOverride the root directory of a custom SDK to use
     * @return this, for constructor chaining
     */
    public TestLintTask sdkHome(File sdkHomeOverride) {
        ensurePreRun();
        this.sdkHome = sdkHomeOverride;
        return this;
    }

    /**
     * Sets the test name (optional). This will place the tests in a temp directory including the
     * test name which is sometimes helpful for debugging.
     */
    public TestLintTask testName(String testName) {
        this.testName = testName;
        return this;
    }

    /**
     * This method allows you to add a hook which you can run on a mock builder model to tweak it,
     * such as changing or augmenting the builder model classes
     */
    public TestLintTask modifyGradleMocks(@NonNull GradleMockModifier mockModifier) {
        ensurePreRun();
        this.mockModifier = mockModifier;
        return this;
    }

    /**
     * Lint will try to use real production implementations of the lint infrastructure, such as
     * {@link LintModelModuleProject}. However, in a few (narrow) cases, we don't want to do this
     * because we want to simulate certain failure scenario. This flag gives tests a chance to opt
     * back to the previous test-specific project implementation.
     *
     * @param useTestProject whether to use the older test implementation for projects
     * @return this, for constructor chaining
     */
    public TestLintTask useTestProjectImplementation(boolean useTestProject) {
        ensurePreRun();
        this.useTestProject = useTestProject;
        return this;
    }

    /**
     * Registers a hook to initialize the lint driver during test execution
     *
     * @param configurator the callback to configure the lint driver
     * @return this, for constructor chaining
     */
    public TestLintTask configureDriver(@NonNull LintDriverConfigurator configurator) {
        ensurePreRun();
        driverConfigurator = configurator;
        return this;
    }

    /**
     * Registers a hook to initialize the options/flags for lint during test execution
     *
     * @param setter the callback to configure the options
     * @return this, for constructor chaining
     */
    public TestLintTask configureOptions(@NonNull OptionSetter setter) {
        ensurePreRun();
        optionSetter = setter;
        return this;
    }

    /**
     * Configures a custom scope to use when lint is run instead of the default one
     *
     * @param customScope the scope to configure lint with
     * @return this, for constructor chaining
     */
    public TestLintTask customScope(@Nullable EnumSet<Scope> customScope) {
        ensurePreRun();
        this.customScope = customScope;
        return this;
    }

    /**
     * Configures a custom error message checker to invoke on each reported error. Typically used to
     * make sure that code which parses error messages (such as quick fix handlers) are staying up
     * to date with the messages generated by the lint check.
     *
     * @param checker the checker to invoke
     * @return this, for constructor chaining
     */
    public TestLintTask checkMessage(@NonNull ErrorMessageChecker checker) {
        ensurePreRun();
        this.messageChecker = checker;
        return this;
    }

    /**
     * Configures a custom callback after lint has run but before the run context has been disposed,
     * which lets you analyze the client and project state.
     *
     * @param inspector the inspector to invoke
     * @return this, for constructor chaining
     */
    public TestLintTask checkProjects(@NonNull ProjectInspector inspector) {
        ensurePreRun();
        this.projectInspector = inspector;
        return this;
    }
    /**
     * Configures lint to run with a custom lint client instead of the default one.
     *
     * @param client the custom client to use
     * @return this, for constructor chaining
     * @deprecated Use {@link #clientFactory} instead
     */
    @Deprecated
    public TestLintTask client(@NonNull TestLintClient client) {
        ensurePreRun();
        assertNull("Only specify clientFactory", clientFactory);
        this.clientFactory =
                new ClientFactory() {
                    int count = 0;

                    @NonNull
                    @Override
                    public TestLintClient create() {
                        count++;
                        if (count > 1) {
                            client.log(
                                    null,
                                    ""
                                            + "Warning: Using the same client more than once; make sure you call\n"
                                            + "clientFactory() instead of the deprecated client() from your lint()\n"
                                            + "task. This normally just means passing in your creation code as a\n"
                                            + "lambda; e.g. client(object : TestLintClient()...) should be converted\n"
                                            + "to clientFactory({object: TestLintClient()...}).\n");
                        }
                        return client;
                    }
                };
        return this;
    }

    /**
     * Configures lint to run with a custom lint client instead of the default one. This is a
     * factory method instead of a method which takes an instance because during testing, lint may
     * need to run multiple times, and we want to make sure there's no leaking of instance state
     * between these separate runs.
     *
     * @param factory the factor which creates custom clients to use
     * @return this, for constructor chaining
     */
    public TestLintTask clientFactory(@Nullable ClientFactory factory) {
        ensurePreRun();
        this.clientFactory = factory;
        return this;
    }

    /**
     * Tells lint to select a particular Gradle variant. This only applies when using Gradle mocks.
     *
     * @param variantName the name of the variant to use
     * @return this, for constructor chaining
     */
    public TestLintTask variant(String variantName) {
        ensurePreRun();
        this.variantName = variantName;
        return this;
    }

    /**
     * Tells lint whether it's running in "vital" (fatal-severity-only) mode
     *
     * @param vital whether we're checking vital only issues
     * @return this, for constructor chaining
     */
    public TestLintTask vital(boolean vital) {
        ensurePreRun();
        this.vital = vital;
        return this;
    }

    /**
     * Tells the lint infrastructure to silently ignore any unknown Gradle constructs it encounters
     * when processing a Gradle file and attempting to build up mocks for the Gradle builder model
     *
     * @return this, for constructor chaining
     */
    public TestLintTask ignoreUnknownGradleConstructs() {
        ensurePreRun();
        ignoreUnknownGradleConstructs = true;
        return this;
    }

    /**
     * Tells the lint infrastructure to simulate symbol resolution errors. This is used in some rare
     * occurrences where you have a lint check which AST results and falls back to bytecode analysis
     * if symbol resolution fails; this lets you test both behaviors on all the same test files
     * without having to insert actual errors in the files.
     *
     * @return this, for constructor chaining
     */
    public TestLintTask forceSymbolResolutionErrors() {
        ensurePreRun();
        this.forceSymbolResolutionErrors = true;
        return this;
    }

    /**
     * Normally the lint test infrastructure ensures that all reported errors are unique (which
     * means that no error has the exact same message for the exact same error range in the source
     * file). That's normally a sign of a bug in the detector. If you're trying to report multiple
     * issues that happen to overlap the same region for the same issue id, make sure that the error
     * message is unique; the common way to do that is to include parameters in the error message,
     * such as the name of the variable or expression in question, and so on.
     *
     * <p>There <b>are</b> some cases where it's very difficult to avoid reporting the same error
     * message twice. For example, the ResourceTypeDetector, when it discovers that an expression
     * has a certain resource type (e.g. "R.drawable.foo" is a @DrawableRes, as is
     * "getResources().getDrawable(x)") it sees if this expression is used in a surrounding binary
     * expression for comparison purposes and warns if you're performing some suspicious comparisons
     * (for resource types only equals/not-equals is expected; making number-range comparisons is
     * usually a bug.) If you report this bug on the binary expression, you could end up reporting
     * it twice if we reach the expression both from the left operand and from the right operand.
     * But you don't want to limit reporting the error to just one of those branches since it needs
     * to work for both the case when both sides have a known resource type as when either only the
     * left or only the right have a known resource type. In these scenarios you can turn off the
     * duplicate check.
     *
     * @return this, for constructor chaining
     */
    public TestLintTask allowDuplicates() {
        this.allowDuplicates = true;
        return this;
    }

    /**
     * Sets the set of desugaring operations in effect
     *
     * @return this for constructor chaining
     */
    public TestLintTask desugaring(@NonNull Set<Desugaring> desugaring) {
        this.desugaring = desugaring;
        return this;
    }

    /**
     * Whether lint should check that the lint checks correctly handles the UInjectionHost string
     * literals. This will run lint checks that contain Kotlin files twice and diff the results.
     */
    public TestLintTask checkUInjectionHost(boolean check) {
        this.checkUInjectionHost = check;
        return this;
    }

    /**
     * Normally lint unit tests will abort if an exception is found. You can allow exceptions (which
     * will then be routed through lint's normal error trapping mechanism). This is primarily
     * intended to test lint itself..
     */
    public TestLintTask allowExceptions(boolean allowExceptions) {
        this.allowExceptions = allowExceptions;
        return this;
    }

    /**
     * Normally resource repositories are only provided in incremental/single-file lint runs. This
     * method allows you to add support for this in the test.
     *
     * @param supportResourceRepository if true, provide a resource repository to detectors that ask
     *     for it.
     * @return this, for constructor chaining
     */
    public TestLintTask supportResourceRepository(boolean supportResourceRepository) {
        ensurePreRun();
        this.supportResourceRepository = supportResourceRepository;
        return this;
    }

    /**
     * Normally, lint reports will show absolute paths in output by removing the local details of
     * the paths down to the project directories and showing this as a "/TESTROOT/" prefix.
     *
     * <p>By default it will also strip off the /TESTROOT/ prefix to show everything as relative,
     * but with this method you can turn this off.
     */
    public TestLintTask stripRoot(boolean stripRoot) {
        this.stripRoot = stripRoot;
        return this;
    }

    private void ensureConfigured() {
        getCheckedIssues(); // ensures that you've used one of the many DSL options to set issues

        if (projects == null) {
            throw new RuntimeException(
                    "No test files to check lint in: call files() or projects()");
        }
    }

    private void ensurePreRun() {
        if (alreadyRun) {
            throw new RuntimeException("This method should only be called before run()");
        }
    }

    /**
     * Given a result string possibly containing absolute paths to the given directory, replaces the
     * directory prefixes with {@code TESTROOT}, and optionally (if configured via {@link
     * #stripRoot}) makes the path relative to the test root.
     */
    public String stripRoot(File rootDir, String s) {
        String path = rootDir.getPath();
        if (s.contains(path)) {
            s = s.replace(path, "TESTROOT");
        }
        path = path.replace(File.separatorChar, '/');
        if (s.contains(path)) {
            s = s.replace(path, "/TESTROOT");
        }
        if (stripRoot && s.contains("TESTROOT")) {
            s = s.replace("/TESTROOT/", "").replace("/TESTROOT\\", "").replace("\nTESTROOT/", "\n");
            if (s.startsWith("TESTROOT/")) {
                s = s.substring("TESTROOT/".length());
            }
        }
        return s;
    }

    private static void addProjects(
            @NonNull List<ProjectDescription> target, @NonNull ProjectDescription... projects) {
        for (ProjectDescription project : projects) {
            if (!target.contains(project)) {
                target.add(project);
            }

            for (ProjectDescription dependency : project.getDependsOn()) {
                addProjects(target, dependency);
            }
        }
    }

    /** Constructs the actual lint projects on disk */
    @NonNull
    public List<File> createProjects(File rootDir) {
        List<ProjectDescription> allProjects = Lists.newArrayListWithCapacity(2 * projects.length);
        addProjects(allProjects, projects);

        // Assign names if necessary
        for (int i = 0; i < allProjects.size(); i++) {
            ProjectDescription project = allProjects.get(i);
            if (project.getName().isEmpty()) {
                project.setName("project" + i);
            }
        }

        // First create project directories (before populating them) since
        // the directories need to exist to make relative path computations
        // between the projects (used for example for dependencies) work
        // properly.
        List<File> projectDirs = Lists.newArrayList();
        for (ProjectDescription project : allProjects) {
            try {
                project.ensureUnique();
                File projectDir = getProjectDirectory(project, rootDir);
                dirToProjectDescription.put(projectDir, project);
                if (!projectDir.isDirectory()) {
                    boolean ok = projectDir.mkdirs();
                    assertTrue("Couldn't create projectDir " + projectDir, ok);
                }
                projectDirs.add(projectDir);

            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        // Populate project directories with source files
        for (ProjectDescription project : allProjects) {
            try {
                TestFile[] files = project.getFiles();
                File projectDir = getProjectDirectory(project, rootDir);

                // Also create dependency files
                if (!project.getDependsOn().isEmpty()) {
                    TestFile.PropertyTestFile propertyFile = null;
                    for (TestFile file : files) {
                        if (file instanceof TestFile.PropertyTestFile) {
                            propertyFile = (TestFile.PropertyTestFile) file;
                            break;
                        }
                    }
                    if (propertyFile == null) {
                        propertyFile = TestFiles.projectProperties();
                        files = ObjectArrays.concat(files, propertyFile);
                    }

                    int index = 1;
                    for (ProjectDescription dependency : project.getDependsOn()) {
                        File dependencyDir = getProjectDirectory(dependency, rootDir);
                        TestLintClient client = new TestLintClient(LintClient.clientName);
                        String relative = client.getRelativePath(projectDir, dependencyDir);
                        String referenceKey = "android.library.reference." + (index++);
                        propertyFile.property(referenceKey, relative);
                    }
                }

                populateProjectDirectory(project, projectDir, files);

                if (baseline != null) {
                    baselineFile = baseline.createFile(projectDir);
                }
                if (overrideConfig != null) {
                    overrideConfigFile = overrideConfig.createFile(projectDir);
                }
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        return projectDirs;
    }

    @NonNull
    private static File getProjectDirectory(
            @NonNull ProjectDescription project, @NonNull File rootDir) {
        if (project.getUnder() == null) {
            return new File(rootDir, project.getName());
        }
        List<String> segments = new ArrayList<>();
        do {
            segments.add(project.getName());
            project = project.getUnder();
        } while (project != null);
        Collections.reverse(segments);
        String relativePath = Joiner.on(File.separator).join(segments);
        return new File(rootDir, relativePath);
    }

    private boolean haveKotlinTestFiles() {
        for (ProjectDescription project : projects) {
            for (TestFile file : project.getFiles()) {
                if (file.targetRelativePath.endsWith(DOT_KT)
                        || file.targetRelativePath.endsWith(DOT_KTS)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Performs the lint check, returning the results of the lint check.
     *
     * @return the result
     */
    @CheckReturnValue
    @NonNull
    public TestLintResult run() {
        alreadyRun = true;
        ensureConfigured();

        File rootDir;
        if (rootDirectory != null) {
            rootDir = rootDirectory;
        } else if (testName != null) {
            rootDir = new File(tempDir, testName);
        } else {
            rootDir = tempDir;
        }

        try {
            // Use canonical path to make sure we don't end up failing
            // to chop off the prefix from Project#getDisplayPath
            rootDir = rootDir.getCanonicalFile();
        } catch (IOException ignore) {
        }

        // Make sure tests don't pick up random things outside the test directory.
        // I had accidentally placed a file named lint.xml in /tmp, and this caused
        // a number of confusing failures!
        ConfigurationHierarchy.Companion.setDefaultRootDir(tempDir);

        if (platforms == null) {
            platforms = computePlatforms(getCheckedIssues());
        }

        if (impliedProject
                && platforms.contains(Platform.JDK)
                && !platforms.contains(Platform.ANDROID)) {
            for (ProjectDescription project : projects) {
                project.setType(ProjectDescription.Type.JAVA);
            }
        }

        List<File> projectDirs = createProjects(rootDir);
        try {
            if (checkUInjectionHost) {
                setForceUiInjection(false);
            }

            TestLintClient lintClient = createClient();
            Pair<String, List<Incident>> result = checkLint(lintClient, rootDir, projectDirs);
            String output = result.getFirst();
            List<Incident> incidents = result.getSecond();

            if (projectInspector != null) {
                Collection<Project> knownProjects = lintClient.getKnownProjects();
                List<Project> projects = new ArrayList<>(knownProjects);
                LintDriver driver = lintClient.getDriver();
                projects.sort(Comparator.comparing(Project::getName));
                projectInspector.inspect(driver, projects);
            }

            // Test both with and without UInjectionHost
            if (checkUInjectionHost && haveKotlinTestFiles()) {
                setForceUiInjection(true);
                Pair<String, List<Incident>> result2 =
                        checkLint(createClient(), rootDir, projectDirs);
                String output2 = result2.getFirst();
                setForceUiInjection(false);

                assertEquals(
                        "The unit test results differ based on whether\n"
                                + "`kotlin.uast.force.uinjectionhost` is on or off. Make sure your\n"
                                + "detector correctly handles strings in Kotlin; soon all String\n"
                                + "`ULiteralExpression` elements will be wrapped in a `UPolyadicExpression`.\n"
                                + "Lint now runs the tests twice, in both modes, and checks that\n"
                                + "the results are identical.",
                        output,
                        output2);
            }

            return new TestLintResult(this, rootDir, output, lintClient.firstThrowable, incidents);
        } catch (Throwable e) {
            return new TestLintResult(this, rootDir, null, e, Collections.emptyList());
        } finally {
            deleteFilesRecursively(tempDir.toPath());
        }
    }

    /** Deletes all files in a directory tree but preserves all directories. */
    public static void deleteFilesRecursively(@NonNull Path dir) {
        try {
            Files.walkFileTree(
                    dir,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                                throws IOException {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setForceUiInjection(boolean on) {
        //noinspection KotlinInternalInJava
        org.jetbrains.uast.kotlin.KotlinConverter.INSTANCE.setForceUInjectionHost(on);
    }

    /** Returns all the platforms encountered by the given issues */
    private static EnumSet<Platform> computePlatforms(List<Issue> issues) {
        EnumSet<Platform> platforms = EnumSet.noneOf(Platform.class);
        for (Issue issue : issues) {
            platforms.addAll(issue.getPlatforms());
        }
        return platforms;
    }

    /**
     * Creates lint test projects according to the configured project descriptions. Note that these
     * are not the same projects that will be used if the {@link #run()} method is called. This
     * method is intended mainly for testing the lint infrastructure itself. Most detector tests
     * will just want to use {@link #run()}.
     *
     * @param keepFiles if true, don't delete the generated temporary project source files
     */
    @NonNull
    public List<Project> createProjects(boolean keepFiles) {
        File rootDir = createTempDirectory();
        try {
            // Use canonical path to make sure we don't end up failing
            // to chop off the prefix from Project#getDisplayPath
            rootDir = rootDir.getCanonicalFile();
        } catch (IOException ignore) {
        }

        List<File> projectDirs = createProjects(rootDir);

        TestLintClient lintClient = createClient();
        lintClient.setLintTask(this);
        try {
            List<Project> projects = Lists.newArrayList();
            for (File dir : projectDirs) {
                projects.add(lintClient.getProject(dir, rootDir));
            }
            return projects;
        } finally {
            lintClient.setLintTask(null);

            if (!keepFiles) {
                deleteFilesRecursively(rootDir.toPath());
            }
        }
    }

    @NonNull
    private Pair<String, List<Incident>> checkLint(
            @NonNull TestLintClient lintClient, @NonNull File rootDir, @NonNull List<File> files)
            throws Exception {
        lintClient.addCleanupDir(rootDir);
        lintClient.setLintTask(this);
        try {
            if (optionSetter != null) {
                optionSetter.set(lintClient.getFlags());
            }

            return lintClient.checkLint(files, getCheckedIssues());
        } finally {
            lintClient.setLintTask(null);
        }
    }

    @NonNull
    TestLintClient createClient() {
        TestLintClient client;
        if (clientFactory != null) {
            client = clientFactory.create();
        } else {
            LintClient.Companion.ensureClientNameInitialized();
            String clientName = LintClient.getClientName();
            try {
                client = new TestLintClient();
            } finally {
                LintClient.setClientName(clientName);
            }
        }

        if (!useTestConfiguration && overrideConfigFile != null) {
            ConfigurationHierarchy configurations = client.getConfigurations();
            if (configurations.getOverrides() == null) {
                Configuration config =
                        LintXmlConfiguration.create(configurations, overrideConfigFile);
                configurations.addGlobalConfigurations(null, config);
            }
        }

        client.task = this;
        return client;
    }

    public void populateProjectDirectory(
            @NonNull ProjectDescription project,
            @NonNull File projectDir,
            @NonNull TestFile... testFiles)
            throws IOException {
        if (!projectDir.exists()) {
            boolean ok = projectDir.mkdirs();
            if (!ok) {
                throw new RuntimeException("Couldn't create " + projectDir);
            }
        }

        boolean haveGradle = false;
        for (TestFile fp : testFiles) {
            if (fp instanceof GradleTestFile || fp.targetRelativePath.endsWith(DOT_GRADLE)) {
                haveGradle = true;
                break;
            }
        }

        List<String> jars = new ArrayList<>();

        for (TestFile fp : testFiles) {
            if (haveGradle) {
                if (ANDROID_MANIFEST_XML.equals(fp.targetRelativePath)) {
                    // The default should be src/main/AndroidManifest.xml, not just
                    // AndroidManifest.xml
                    // fp.to("src/main/AndroidManifest.xml");
                    fp.within("src/main");
                } else if (fp instanceof JavaTestFile
                        && fp.targetRootFolder != null
                        && fp.targetRootFolder.equals("src")) {
                    fp.within("src/main/java");
                } else if (fp instanceof TestFile.KotlinTestFile
                        && fp.targetRootFolder != null
                        && fp.targetRootFolder.equals("src")) {
                    fp.within("src/main/kotlin");
                }
            }

            if (fp instanceof TestFiles.LibraryReferenceTestFile) {
                jars.add(((TestFiles.LibraryReferenceTestFile) fp).file.getPath());
                continue;
            }

            fp.createFile(projectDir);

            // Note -- lint-override.xml is only a convention in the test suite; it's
            // not something lint automatically picks up!
            if ("lint-override.xml".equals(fp.targetRelativePath)) {
                overrideConfig = fp;
                continue;
            }

            if (fp instanceof GradleTestFile) {
                // Record mocking relationship used by createProject lint callback
                GradleModelMocker mocker = ((GradleTestFile) fp).getMocker(projectDir);
                if (ignoreUnknownGradleConstructs) {
                    mocker = mocker.withLogger(new NullLogger());
                }
                if (project.getDependencyGraph() != null) {
                    mocker = mocker.withDependencyGraph(project.getDependencyGraph());
                }
                projectMocks.put(projectDir, mocker);

                try {
                    projectMocks.put(projectDir.getCanonicalFile(), mocker);
                } catch (IOException ignore) {
                }
            }
        }

        if (!jars.isEmpty()) {
            // TODO: Make sure there's no existing class path file!
            TestFile classpath = TestFiles.classpath(jars.toArray(new String[0]));
            classpath.createFile(projectDir);
        }

        File manifest;
        if (haveGradle) {
            manifest = new File(projectDir, "src/main/AndroidManifest.xml");
        } else {
            manifest = new File(projectDir, ANDROID_MANIFEST_XML);
        }

        if (project.getType() != ProjectDescription.Type.JAVA) {
            addManifestFileIfNecessary(manifest);
        }
    }

    /**
     * All Android projects must have a manifest file; this one creates it if the test file didn't
     * add an explicit one.
     */
    private static void addManifestFileIfNecessary(@NonNull File manifest) throws IOException {
        // Ensure that there is at least a manifest file there to make it a valid project
        // as far as Lint is concerned:
        if (!manifest.exists()) {
            File parentFile = manifest.getParentFile();
            if (parentFile != null && !parentFile.isDirectory()) {
                boolean ok = parentFile.mkdirs();
                assertTrue("Couldn't create directory " + parentFile, ok);
            }
            try (FileWriter fw = new FileWriter(manifest)) {
                fw.write(
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                + "    package=\"lint.test.pkg\"\n"
                                + "    android:versionCode=\"1\"\n"
                                + "    android:versionName=\"1.0\" >\n"
                                + "</manifest>\n");
            }
        }
    }

    /**
     * Returns the list of issues to be checked by this run. If {@link #issues(Issue...)} is called
     * to register issues, this will be the exact set of issues used, otherwise the issues found as
     * fields in the detector passed to {@link #detector(Detector)} is used.
     */
    @NonNull
    public List<Issue> getCheckedIssues() {
        if (checkedIssues == null) {
            // Ensure custom rules don't linger from test to test
            JarFileIssueRegistry.Factory.clearCache();

            if (issues != null) {
                return checkedIssues = Arrays.asList(this.issues);
            }

            if (customRules != null) {
                TestLintClient client = createClient();
                client.task = this;
                List<JarFileIssueRegistry> registries =
                        JarFileIssueRegistry.Factory.get(client, Arrays.asList(customRules), null);
                IssueRegistry[] array = registries.toArray(new IssueRegistry[0]);
                IssueRegistry all = JarFileIssueRegistry.Factory.join(array);
                return checkedIssues = all.getIssues();
            }

            if (detector != null) {
                checkedIssues = Lists.newArrayList();
                // Find issues defined in the class
                Class<? extends Detector> detectorClass = detector.getClass();
                addIssuesFromClass(checkedIssues, detectorClass);
                if (checkedIssues.isEmpty()) {
                    // Look in file
                    try {
                        Class<?> fileClass = Class.forName(detectorClass.getName() + "Kt");
                        addIssuesFromClass(checkedIssues, fileClass);
                    } catch (ClassNotFoundException ignore) {
                    }
                }
                if (checkedIssues.isEmpty()) {
                    throw new RuntimeException(
                            "Could not find any Issue field instances in "
                                    + "detector "
                                    + detector.getClass().getSimpleName()
                                    + ": call "
                                    + "issues() to configure exact issues to check instead");
                }
                return checkedIssues;
            }

            if (issueIds != null && issueIds.length > 0) {
                checkedIssues = Lists.newArrayList();
                TestIssueRegistry registry = new TestIssueRegistry();
                for (String id : issueIds) {
                    Issue issue = registry.getIssue(id);
                    if (issue != null) {
                        checkedIssues.add(issue);
                    } // else: could be loaded by custom rule
                }

                return checkedIssues;
            }

            if (allowDelayedIssueRegistration) {
                return checkedIssues = Collections.emptyList();
            }

            throw new RuntimeException(
                    "No issues configured; you must call either issues(), "
                            + "detector() or customRules() to tell the lint infrastructure which checks "
                            + "should be performed");
        }

        return checkedIssues;
    }

    /** Adds issue fields found in the given class */
    private static void addIssuesFromClass(
            @NonNull List<Issue> checkedIssues, @NonNull Class<?> detectorClass) {
        addIssuesFromFields(checkedIssues, detectorClass.getFields());

        // Use getDeclaredFields to also pick up private fields (e.g. backing fields
        // for Kotlin properties); we can't *only* use getDeclaredFields since we
        // also want to pick up inherited fields (for example used in the GradleDetector
        // subclasses.)
        addIssuesFromFields(checkedIssues, detectorClass.getDeclaredFields());

        for (Method method : detectorClass.getDeclaredMethods()) {
            if ((method.getModifiers() & Modifier.STATIC) != 0
                    && method.getReturnType() == Issue.class
                    && method.getName().startsWith("get")
                    && method.getParameterCount() == 0) {
                try {
                    method.setAccessible(true);
                    Issue issue = (Issue) method.invoke(null);
                    if (!checkedIssues.contains(issue)) {
                        checkedIssues.add(issue);
                    }
                } catch (IllegalAccessException | InvocationTargetException ignore) {
                }
            }
        }
    }

    private static void addIssuesFromFields(@NonNull List<Issue> checkedIssues, Field[] fields) {
        for (Field field : fields) {
            if ((field.getModifiers() & Modifier.STATIC) != 0
                    && !field.getName().startsWith("_")
                    && field.getType() == Issue.class) {
                try {
                    field.setAccessible(true);
                    Issue issue = (Issue) field.get(null);
                    if (!checkedIssues.contains(issue)) {
                        checkedIssues.add(issue);
                    }
                } catch (IllegalAccessException ignore) {
                }
            }
        }
    }

    /**
     * Provides mock data to feed back to the URL connection if a detector calls {@link
     * LintClient#openConnection(URL)} and then attempts to read data from that connection
     */
    @NonNull
    public TestLintTask networkData(@NonNull String url, @NonNull byte[] data) {
        if (mockNetworkData == null) {
            mockNetworkData = Maps.newHashMap();
        }
        mockNetworkData.put(url, data);
        return this;
    }

    /**
     * Provides mock data to feed back to the URL connection if a detector calls {@link
     * LintClient#openConnection(URL, int)} and then attempts to read data from that connection.
     *
     * @return this, for constructor chaining
     */
    @NonNull
    public TestLintTask networkData(@NonNull String url, @NonNull String data) {
        return networkData(url, data.getBytes(Charsets.UTF_8));
    }

    /**
     * Normally lint will refuse to access the network (via the {@link
     * LintClient#openConnection(URL, int)} API; it cannot prevent detectors from directly access
     * networking libraries on its own). This is because from tests you normally want to provide
     * mock data instead. If you deliberately want to access the network (perhaps because you have
     * your own deeper mocking framework) you can turn this on.
     *
     * @param allowNetworkAccess whether network access should be allowed (default is false)
     * @return this, for constructor chaining
     */
    public TestLintTask allowNetworkAccess(boolean allowNetworkAccess) {
        this.allowNetworkAccess = allowNetworkAccess;
        return this;
    }

    /**
     * Sets the platforms that the test should run with
     *
     * @param platforms the platforms to use
     * @return this, for constructor chaining
     */
    public TestLintTask platforms(EnumSet<Platform> platforms) {
        this.platforms = platforms;
        return this;
    }

    /**
     * Configures the root directory to create projects in when running lint. Normally lint creates
     * it in the temporary folder, but this allows you to specify a specific location. When this is
     * set, lint will not delete the contents after running.
     *
     * @param rootDirectory the root directory to create lint files under
     * @return this, for constructor chaining
     */
    public TestLintTask rootDirectory(@Nullable File rootDirectory) {
        this.rootDirectory = rootDirectory;
        return this;
    }

    /**
     * Interface to implement to modify the Gradle builder model that is mocked from a {@link
     * TestFiles#gradle(String)} test file.
     *
     * <p>Register this modifier via {@link #modifyGradleMocks(GradleMockModifier)}.
     *
     * @deprecated Builder-model is going away
     */
    @Deprecated
    public interface GradleMockModifier {
        void modify(@NonNull IdeAndroidProject project, @NonNull IdeVariant variant);
    }

    /**
     * Interface to implement to configure the lint driver before lint starts running.
     *
     * <p>Register this configurator via {@link #configureDriver)}.
     */
    @FunctionalInterface
    public interface LintDriverConfigurator {
        void configure(@NonNull LintDriver driver);
    }

    /** Interface to implement a lint test task which customizes the command line flags */
    @FunctionalInterface
    public interface OptionSetter {
        void set(@NonNull LintCliFlags flags);
    }

    /**
     * Interface to implement to configure the lint driver to check all reported error messages.
     *
     * <p>Register this checker via {@link #checkMessage(ErrorMessageChecker)})}.
     */
    @FunctionalInterface
    public interface ErrorMessageChecker {
        void checkReportedError(
                @NonNull Context context,
                @NonNull Issue issue,
                @NonNull Severity severity,
                @NonNull Location location,
                @NonNull String message,
                @Nullable LintFix fixData);
    }

    /**
     * Interface to implement to have the test use a custom {@link TestLintClient}, typically
     * because you want to override one or more methods in the client.
     */
    @FunctionalInterface
    public interface ClientFactory {
        @NonNull
        TestLintClient create();
    }

    /**
     * Interface to implement a lint test task which inspects the known projects after lint has run.
     *
     * <p>Register this checker via {@link #checkProjects(ProjectInspector)}.
     */
    @FunctionalInterface
    public interface ProjectInspector {
        void inspect(@NonNull LintDriver driver, @NonNull List<Project> knownProjects);
    }
}

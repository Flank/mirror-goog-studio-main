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
import static com.android.SdkConstants.DOT_JAR;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.testutils.TestUtils;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.checks.infrastructure.TestFile.GradleTestFile;
import com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile;
import com.android.tools.lint.client.api.JarFileIssueRegistry;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintListener;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.utils.NullLogger;
import com.android.utils.SdkUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.ObjectArrays;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

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
    boolean allowCompilationErrors;
    boolean allowSystemErrors = true;
    String incrementalFileName;
    Issue[] issues;
    String[] issueIds;
    public File sdkHome;
    LintListener listener;
    GradleMockModifier mockModifier;
    LintDriverConfigurator driverConfigurator;
    ErrorMessageChecker messageChecker;
    String variantName;
    EnumSet<Scope> customScope;
    public boolean forceSymbolResolutionErrors;
    TestLintClient client;
    boolean skipExtraTokenChecks;
    Detector detector;
    File[] customRules;
    boolean ignoreUnknownGradleConstructs;
    Boolean supportResourceRepository;

    /** Creates a new lint test task */
    public TestLintTask() {
    }

    /** Creates a new lint test task */
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
        this.projects = new ProjectDescription[]{ new ProjectDescription(files) };
        return this;
    }

    /**
     * Configures the test task to allow compilation errors in the test files (normally not
     * allowed)
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
     * Sets whether the test task should silently ignore lint infrastructure errors
     * (such as missing .class files etc)
     *
     * @return this, for constructor chaining
     */
    public TestLintTask allowSystemErrors(boolean allow) {
        ensurePreRun();
        this.allowSystemErrors = allow;
        return this;
    }

    /**
     * Configures the test task to run incrementally, with the given file as
     * the current file
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
     * Configures the test task to use the given detector when
     * determining which issues to run. If you're calling
     * {@link #issues(Issue...)} you do not need to call this method,
     * but for detectors that report a lot of issues, this is more
     * convenient. (This requires the set of issues produced by a detector
     * to be static fields in the detector class.)
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
        checkedIssues = null; // force recompute
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
        checkedIssues = null; // force recompute
        return this;
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
     * Configures the test task to run incrementally. This method can be
     * called if the project only contains a single file, which will be
     * considered the current file. If there are multiple files in the
     * project you must call {@link #incremental(String)} instead.
     *
     * @return this, for constructor chaining
     */
    public TestLintTask incremental() {
        ensurePreRun();
        if (projects != null && projects.length == 1 &&
                projects[0].files != null &&
                projects[0].files.length == 1) {
            this.incrementalFileName = projects[0].files[0].getTargetPath();
        } else {
            assert false : "Can only use implicit incremental mode when there is a single "
                    + "source file; use incremental(relativePath) instead";
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
     * Configures the lint task with a given SDK home to use instead of the
     * default one.
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
     * This method allows you to add a hook which you can run on a mock
     * builder model to tweak it, such as changing or augmenting the builder model
     * classes
     */
    public TestLintTask modifyGradleMocks(@NonNull GradleMockModifier mockModifier) {
        ensurePreRun();
        this.mockModifier = mockModifier;
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
     * Configures a custom error message checker to invoke on each reported error.
     * Typically used to make sure that code which parses error messages (such as
     * quick fix handlers) are staying up to date with the messages generated
     * by the lint check.
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
     * Configures lint to run with a custom lint client instead of the
     * default one.
     *
     * @param client the custom client to use
     * @return this, for constructor chaining
     */
    public TestLintTask client(@Nullable TestLintClient client) {
        ensurePreRun();
        this.client = client;
        return this;
    }

    /**
     * Tells lint to select a particular Gradle variant. This only applies
     * when using Gradle mocks.
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
     * Normally lint will run your detectors <b>twice</b>, first on the
     * plain source code, and then a second time where it has inserted whitespace
     * and parentheses pretty much everywhere, to help catch bugs where your detector
     * is only checking direct parents or siblings rather than properly allowing for
     * whitespace and parenthesis nodes which can be present for example when using
     * PSI inside the IDE. You can skip these extra checks by calling this method.
     */
    public TestLintTask skipExtraTokenChecks() {
        ensurePreRun();
        skipExtraTokenChecks = true;
        return this;
    }

    /**
     * Tells the lint infrastructure to silently ignore any unknown Gradle constructs
     * it encounters when processing a Gradle file and attempting to build up mocks
     * for the Gradle builder model
     *
     * @return this, for constructor chaining
     */
    public TestLintTask ignoreUnknownGradleConstructs() {
        ensurePreRun();
        ignoreUnknownGradleConstructs = true;
        return this;
    }

    /**
     * Tells the lint infrastructure to simulate symbol resolution errors.
     * This is used in some rare occurrences where you have a lint check
     * which AST results and falls back to bytecode analysis if symbol
     * resolution fails; this lets you test both behaviors on all the same
     * test files without having to insert actual errors in the files.
     *
     * @return this, for constructor chaining
     */
    public TestLintTask forceSymbolResolutionErrors() {
        ensurePreRun();
        this.forceSymbolResolutionErrors = true;
        return this;
    }

    /**
     * Normally resource repositories are only provided in incremental/single-file
     * lint runs. This method allows you to add support for this in the test.
     *
     * @param supportResourceRepository if true, provide a resource repository to detectors that ask
     *                                  for it.
     * @return this, for constructor chaining
     */
    public TestLintTask supportResourceRepository(boolean supportResourceRepository) {
        ensurePreRun();
        this.supportResourceRepository = supportResourceRepository;
        return this;
    }

    private void ensureConfigured() {
        getCheckedIssues(); // ensures that you've used one of the many DSL options to set issues

        if (projects == null) {
            throw new RuntimeException("No test files to check lint in: call "
                    + "files() or projects()");
        }
    }

    private void ensurePreRun() {
        if (alreadyRun) {
            throw new RuntimeException("This method should only be called before run()");
        }
    }

    private static void addProjects(
            @NonNull List<ProjectDescription> target,
            @NonNull ProjectDescription... projects) {
        for (ProjectDescription project : projects) {
            if (!target.contains(project)) {
                target.add(project);
            }

            for (ProjectDescription dependency : project.dependsOn) {
                addProjects(target, dependency);
            }
        }
    }

    /** Constructs the actual lint projects on disk */
    @NonNull
    private List<File> createProjects(File rootDir) {
        List<ProjectDescription> allProjects = Lists.newArrayListWithCapacity(2 * projects.length);
        addProjects(allProjects, projects);

        // Assign names if necessary
        for (int i = 0; i < allProjects.size(); i++) {
            ProjectDescription project = allProjects.get(i);
            if (project.name == null) {
                project.name = "project" + Integer.toString(i);
            }
        }

        List<File> projectDirs = Lists.newArrayList();
        for (ProjectDescription project : allProjects) {
            try {
                TestFile[] files = project.files;

                // Also create dependency files
                if (!project.dependsOn.isEmpty()) {
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
                    for (ProjectDescription dependency : project.dependsOn) {
                        propertyFile.property("android.library.reference." + (index++),
                                "../" + dependency.name);
                    }
                }

                File projectDir = new File(rootDir, project.name);
                dirToProjectDescription.put(projectDir, project);
                populateProjectDirectory(project, projectDir, files);
                projectDirs.add(projectDir);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        return projectDirs;
    }

    /**
     * Performs the lint check, returning the results of the lint check.
     *
     * @return the result
     */
    @NonNull
    public TestLintResult run() {
        alreadyRun = true;
        ensureConfigured();

        File rootDir = Files.createTempDir();
        try {
            // Use canonical path to make sure we don't end up failing
            // to chop off the prefix from Project#getDisplayPath
            rootDir = rootDir.getCanonicalFile();
        } catch (IOException ignore) {
        }

        List<File> projectDirs = createProjects(rootDir);
        try {
            String output = checkLint(projectDirs);
            return new TestLintResult(output);
        } catch (Exception e) {
            return new TestLintResult(e);
        } finally {
            TestUtils.deleteFile(rootDir);
        }
    }

    /**
     * Creates lint test projects according to the configured project descriptions.
     * Note that these are not the same projects that will be used if the
     * {@link #run()} method is called. This method is intended mainly for testing
     * the lint infrastructure itself. Most detector tests will just want to
     * use {@link #run()}.
     *
     * @param keepFiles if true, don't delete the generated temporary project source files
     */
    @NonNull
    public List<Project> createProjects(boolean keepFiles) {
        File rootDir = Files.createTempDir();
        try {
            // Use canonical path to make sure we don't end up failing
            // to chop off the prefix from Project#getDisplayPath
            rootDir = rootDir.getCanonicalFile();
        } catch (IOException ignore) {
        }

        List<File> projectDirs = createProjects(rootDir);

        TestLintClient lintClient = createClient();
        lintClient.task = this;
        try {
            List<Project> projects = Lists.newArrayList();
            for (File dir : projectDirs) {
                projects.add(lintClient.getProject(dir, rootDir));
            }
            return projects;
        } finally {
            // Client should not be used outside of the check process
            //noinspection ConstantConditions
            lintClient.task = null;

            if (!keepFiles) {
                TestUtils.deleteFile(rootDir);
            }
        }
    }

    @NonNull
    private String checkLint(@NonNull List<File> files) throws Exception {
        TestLintClient lintClient = createClient();
        lintClient.task = this;
        try {
            return lintClient.checkLint(files, getCheckedIssues());
        } finally {
            // Client should not be used outside of the check process
            //noinspection ConstantConditions
            lintClient.task = null;
        }
    }

    @NonNull
    private TestLintClient createClient() {
        TestLintClient lintClient = client;
        if (lintClient == null) {
            lintClient = new TestLintClient();
        }
        return lintClient;
    }

    public void populateProjectDirectory(
            @NonNull ProjectDescription project,
            @NonNull File projectDir,
            @NonNull TestFile... testFiles) throws IOException {
        if (!projectDir.exists()) {
            boolean ok = projectDir.mkdirs();
            if (!ok) {
                throw new RuntimeException("Couldn't create " + projectDir);
            }
        }

        boolean haveGradle = false;
        for (TestFile fp : testFiles) {
            if (fp instanceof GradleTestFile) {
                haveGradle = true;
            }
        }

        for (TestFile fp : testFiles) {
            if (haveGradle) {
                if (ANDROID_MANIFEST_XML.equals(fp.targetRelativePath)) {
                    // The default should be src/main/AndroidManifest.xml, not just AndroidManifest.xml
                    //fp.to("src/main/AndroidManifest.xml");
                    fp.within("src/main");
                } else if (fp instanceof JavaTestFile && fp.targetRootFolder != null
                        && fp.targetRootFolder.equals("src")) {
                    fp.within("src/main/java");
                }
            }

            fp.createFile(projectDir);

            if (fp instanceof GradleTestFile) {
                // Record mocking relationship used by createProject lint callback
                GradleModelMocker mocker = ((GradleTestFile) fp).getMocker(projectDir);
                if (ignoreUnknownGradleConstructs) {
                    mocker = mocker.withLogger(new NullLogger());
                }
                projectMocks.put(projectDir, mocker);

                try {
                    projectMocks.put(projectDir.getCanonicalFile(), mocker);
                } catch (IOException ignore) {
                }
            }
        }

        File manifest;
        if (haveGradle) {
            manifest = new File(projectDir, "src/main/AndroidManifest.xml");
        } else {
            manifest = new File(projectDir, ANDROID_MANIFEST_XML);
        }

        if (project.type != ProjectDescription.Type.JAVA) {
            addManifestFileIfNecessary(manifest);
        }
    }

    /**
     * All Android projects must have a manifest file; this one creates it if the test
     * file didn't add an explicit one.
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
                fw.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "    package=\"foo.bar2\"\n" +
                        "    android:versionCode=\"1\"\n" +
                        "    android:versionName=\"1.0\" >\n" +
                        "</manifest>\n");
            }
        }
    }

    /**
     * Returns the list of issues to be checked by this run.
     * If {@link #issues(Issue...)} is called to register issues, this will be the
     * exact set of issues used, otherwise the issues found as fields in the
     * detector passed to {@link #detector(Detector)} is used.
     */
    @NonNull
    public List<Issue> getCheckedIssues() {
        if (checkedIssues == null) {
            if (issues != null) {
                return checkedIssues = Arrays.asList(this.issues);
            }

            if (customRules != null) {
                TestLintClient client = createClient();
                for (File jar : customRules) {
                    if (jar.isFile() && SdkUtils.endsWithIgnoreCase(jar.getPath(), DOT_JAR)) {
                        try {
                            JarFileIssueRegistry registry = JarFileIssueRegistry.get(client, jar);
                            return checkedIssues = registry.getIssues();
                        } catch (Throwable t) {
                            client.log(t, null);
                        }
                    }
                }
            }

            if (detector != null){
                checkedIssues = Lists.newArrayList();
                // Find issues defined in the class
                Class<? extends Detector> detectorClass = detector.getClass();
                for (Field field : detectorClass.getFields()) {
                    if ((field.getModifiers() & Modifier.STATIC) != 0
                            && field.getType() == Issue.class) {
                        try {
                            checkedIssues.add((Issue) field.get(null));
                        } catch (IllegalAccessException ignore) {
                        }
                    }
                }
                if (checkedIssues.isEmpty()) {
                    throw new RuntimeException("Could not find any Issue field instances in "
                            + "detector " + detector.getClass().getSimpleName() + ": call "
                            + "issues() to configure exact issues to check instead");
                }
                return checkedIssues;
            }

            if (issueIds != null && issueIds.length > 0) {
                checkedIssues = Lists.newArrayList();
                for (String id : issueIds) {
                    Issue issue = new BuiltinIssueRegistry().getIssue(id);
                    if (issue != null) {
                        checkedIssues.add(issue);
                    } // else: could be loaded by custom rule
                }

                return checkedIssues;
            }

            throw new RuntimeException("No issues configured; you must call either issues(), "
                    + "detector() or customRules() to tell the lint infrastructure which checks "
                    + "should be performed");
        }

        return checkedIssues;
    }

    /**
     * Interface to implement to modify the Gradle builder model that is mocked
     * from a {@link TestFiles#gradle(String)} test file.
     * <p>
     * Register this modifier via {@link #modifyGradleMocks(GradleMockModifier)}.
     */
    public interface GradleMockModifier {
        void modify(@NonNull AndroidProject project, @NonNull Variant variant);
    }

    /**
     * Interface to implement to configure the lint driver before lint starts
     * running.
     * <p>
     * Register this configurator via {@link #driverConfigurator)}.
     */
    public interface LintDriverConfigurator {
        void configure(@NonNull LintDriver driver);
    }

    /**
     * Interface to implement to configure the lint driver to check all reported error
     * messages.
     * <p>
     * Register this checker via {@link #checkMessage(ErrorMessageChecker)})}.
     */
    public interface ErrorMessageChecker {
        void checkReportedError(
                @NonNull Context context,
                @NonNull Issue issue,
                @NonNull Severity severity,
                @NonNull Location location,
                @NonNull String message);
    }
}
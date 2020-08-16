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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.DOT_KT;
import static com.android.SdkConstants.FD_GEN_SOURCES;
import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.FN_ANNOTATIONS_JAR;
import static com.android.SdkConstants.FN_ANNOTATIONS_ZIP;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.android.tools.lint.checks.infrastructure.KotlinClasspathKt.findKotlinStdlibPath;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.MergingException;
import com.android.ide.common.resources.ResourceFile;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceMerger;
import com.android.ide.common.resources.ResourceMergerItem;
import com.android.ide.common.resources.ResourceRepositories;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceSet;
import com.android.ide.common.resources.TestResourceRepository;
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceType;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.support.AndroidxNameUtils;
import com.android.testutils.TestUtils;
import com.android.tools.lint.Incident;
import com.android.tools.lint.LintCliClient;
import com.android.tools.lint.LintCliFlags;
import com.android.tools.lint.LintCliXmlParser;
import com.android.tools.lint.LintExternalAnnotationsManager;
import com.android.tools.lint.LintStats;
import com.android.tools.lint.Reporter;
import com.android.tools.lint.TextReporter;
import com.android.tools.lint.checks.ApiLookup;
import com.android.tools.lint.client.api.CircularDependencyException;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.GradleVisitor;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.client.api.UastParser;
import com.android.tools.lint.client.api.XmlParser;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Desugaring;
import com.android.tools.lint.detector.api.GradleContext;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.LintModelModuleProject;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.tools.lint.model.LintModelExternalLibrary;
import com.android.tools.lint.model.LintModelFactory;
import com.android.tools.lint.model.LintModelLibrary;
import com.android.tools.lint.model.LintModelMavenName;
import com.android.tools.lint.model.LintModelModule;
import com.android.tools.lint.model.LintModelSourceProvider;
import com.android.tools.lint.model.LintModelVariant;
import com.android.utils.ILogger;
import com.android.utils.Pair;
import com.android.utils.PositionXmlParser;
import com.android.utils.StdLogger;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.intellij.openapi.util.Computable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.util.PsiTreeUtil;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.config.LanguageVersionSettings;
import org.jetbrains.uast.UFile;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A {@link LintClient} class for use in lint unit tests.
 *
 * <p><b>NOTE: This is not a public or final API; if you rely on this be prepared to adjust your
 * code for the next tools release.</b>
 */
public class TestLintClient extends LintCliClient {
    protected final StringWriter writer = new StringWriter();
    protected File incrementalCheck;
    /** Managed by the {@link TestLintTask} */
    @SuppressWarnings({"NotNullFieldNotInitialized"})
    @NonNull
    TestLintTask task;

    /** Used to test PSI read lock issues. */
    private boolean insideReadAction = false;

    /** Records the first throwable reported as an error during this test */
    @Nullable Throwable firstThrowable;

    public TestLintClient() {
        this(CLIENT_UNIT_TESTS);
    }

    public TestLintClient(String clientName) {
        this(new LintCliFlags(), clientName);
    }

    public TestLintClient(LintCliFlags flags) {
        this(flags, CLIENT_UNIT_TESTS);
    }

    public TestLintClient(LintCliFlags flags, String clientName) {
        super(flags, clientName);
        TextReporter reporter = new TextReporter(this, flags, writer, false);
        reporter.setForwardSlashPaths(true); // stable tests
        flags.getReporters().add(reporter);
    }

    protected void setLintTask(@Nullable TestLintTask task) {
        if (task != null && task.optionSetter != null) {
            task.optionSetter.set(getFlags());
        }

        // Client should not be used outside of the check process
        //noinspection ConstantConditions
        this.task = task;

        //noinspection VariableNotUsedInsideIf
        if (task != null && !task.allowMissingSdk) {
            ensureSdkExists(this);
        }
    }

    static void ensureSdkExists(@NonNull LintClient client) {
        File sdkHome = client.getSdkHome();
        String message;
        if (sdkHome == null) {
            message = "No SDK configured. ";
        } else if (!sdkHome.isDirectory()) {
            message = sdkHome + " is not a directory. ";
        } else {
            return;
        }

        message =
                "This test requires an Android SDK: "
                        + message
                        + "\n"
                        + "If this test does not really need an SDK, set "
                        + "TestLintTask#allowMissingSdk(). Otherwise, make sure an SDK is "
                        + "available either by specifically pointing to one via "
                        + "TestLintTask#sdkHome(File), or configure $ANDROID_HOME in the "
                        + "environment";
        fail(message);
    }

    /**
     * Normally having $ANDROID_BUILD_TOP set when running lint is a bad idea (because it enables
     * some special support in lint for checking code in AOSP itself.) However, some lint tests
     * (particularly custom lint checks) may not care about this.
     */
    @SuppressWarnings("MethodMayBeStatic")
    protected boolean allowAndroidBuildEnvironment() {
        return true;
    }

    @Nullable
    private File findIncrementalProject(@NonNull List<File> files) {
        // Multiple projects: assume the project names were included in the incremental
        // task names
        if (task.incrementalFileName == null) {
            if (files.size() == 1) {
                assert false : "Need to specify incremental file name if more than one project";
            } else {
                return files.get(0);
            }
        }
        if (files.size() > 1) {
            for (File dir : files) {
                File root = dir.getParentFile(); // Allow the project name to be part of the name
                File current =
                        new File(root, task.incrementalFileName.replace('/', File.separatorChar));
                if (current.exists()) {
                    return dir;
                }
            }
        }

        for (File dir : files) {
            File current = new File(dir, task.incrementalFileName.replace('/', File.separatorChar));
            if (current.exists()) {
                return dir;
            }
        }

        // Just using basename? Search among all files
        for (File root : files) {
            for (File relative : getFilesRecursively(root)) {
                if (relative.getName().equals(task.incrementalFileName)) {
                    // Turn the basename into a full relative name
                    task.incrementalFileName = relative.getPath();
                    return root;
                }
            }
        }

        return null;
    }

    protected Pair<String, List<Incident>> checkLint(List<File> files, List<Issue> issues)
            throws Exception {
        if (task.incrementalFileName != null) {
            boolean found = false;

            File dir = findIncrementalProject(files);
            if (dir != null) {
                File current =
                        new File(dir, task.incrementalFileName.replace('/', File.separatorChar));
                if (!current.exists()) {
                    // Specified the project name as part of the name to disambiguate
                    current =
                            new File(
                                    dir.getParentFile(),
                                    task.incrementalFileName.replace('/', File.separatorChar));
                }
                if (current.exists()) {
                    setIncremental(current);
                    found = true;
                }
            }
            if (!found) {
                List<File> allFiles = new ArrayList<>();
                for (File file : files) {
                    allFiles.addAll(getFilesRecursively(file));
                }

                String all = allFiles.toString();
                fail(
                        "Could not find incremental file "
                                + task.incrementalFileName
                                + " in the test project folders; did you mean one of "
                                + all);
            }
        }

        if (!allowAndroidBuildEnvironment() && System.getenv("ANDROID_BUILD_TOP") != null) {
            fail(
                    "Don't run the lint tests with $ANDROID_BUILD_TOP set; that enables lint's "
                            + "special support for detecting AOSP projects (looking for .class "
                            + "files in $ANDROID_HOST_OUT etc), and this confuses lint.");
        }

        // Reset state here in case a client is reused for multiple runs
        output = new StringBuilder();
        writer.getBuffer().setLength(0);
        List<Incident> incidents = getIncidents();
        incidents.clear();
        setErrorCount(0);
        setWarningCount(0);

        String result = analyze(files, issues);

        return Pair.of(result, incidents);
    }

    private static List<File> getFilesRecursively(File root) {
        List<File> result = new ArrayList<>();
        addFilesUnder(result, root, root.getPath());
        return result;
    }

    private static void addFilesUnder(List<File> result, File file, String skipPrefix) {
        if (file.isFile()) {
            String path = file.getPath();
            if (path.startsWith(skipPrefix)) {
                int length = skipPrefix.length();
                if (path.length() > length && path.charAt(length) == File.separatorChar) {
                    length++;
                }
                path = path.substring(length);
            }
            result.add(new File(path));
        } else if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                Arrays.sort(files, (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));
                for (File child : files) {
                    addFilesUnder(result, child, skipPrefix);
                }
            }
        }
    }

    @Override
    public void reset() {
        super.reset();
        writer.getBuffer().setLength(0);
    }

    @Nullable
    @Override
    public File getSdkHome() {
        if (task.sdkHome != null) {
            return task.sdkHome;
        }

        return super.getSdkHome();
    }

    @NonNull
    @Override
    protected Project createProject(@NonNull File dir, @NonNull File referenceDir) {
        if (getProjectDirs().contains(dir)) {
            throw new CircularDependencyException(
                    "Circular library dependencies; check your project.properties files carefully");
        }
        getProjectDirs().add(dir);

        ProjectDescription description;
        try {
            description = task.dirToProjectDescription.get(dir.getCanonicalFile());
        } catch (IOException ignore) {
            description = task.dirToProjectDescription.get(dir);
        }

        GradleModelMocker mocker;
        try {
            mocker = task.projectMocks.get(dir.getCanonicalFile());
        } catch (IOException ignore) {
            mocker = task.projectMocks.get(dir);
        }
        LintCliFlags flags = getFlags();
        if (mocker != null && mocker.getProject() != null) {
            mocker.syncFlagsTo(flags);
            flags.setFatalOnly(task.vital);
            if (task.variantName != null) {
                mocker.setVariantName(task.variantName);
            }
            if (task.mockModifier != null) {
                task.mockModifier.modify(mocker.getProject(), mocker.getVariant());
            }
        }
        if (task.baselineFile != null) {
            flags.setBaselineFile(task.baselineFile);
        }
        if (mocker != null && (mocker.hasJavaPlugin() || mocker.hasJavaLibraryPlugin())) {
            description.type(ProjectDescription.Type.JAVA);
        }

        Project project = new TestProject(this, dir, referenceDir, description, mocker);

        // Try to use the real lint model project instead, if possible
        LintModelVariant buildVariant = project.getBuildVariant();
        if (buildVariant != null && !task.useTestProject) {
            // Make sure the test folders exist; this prevents the common mistake where you
            // add a gradle() test model but leave the source files in the old src/ and res/
            // folders instead of the required src/main/java, src/main/res, src/test/java, etc
            // locations
            if (hasOldDirectoryLayout(dir)) {
                fail(
                        "Warning: This test uses a gradle model mocker but doesn't have a main "
                                + "source set (src/main/java); that's suspicious; check that the test "
                                + "file is in (for example) src/main/res/ rather than res/.\n"
                                + "Alternatively, set useTestProjectImplementation(true) on the "
                                + "lint task.");
            }

            Project oldProject = project;
            project = new LintModelModuleProject(this, dir, referenceDir, buildVariant, null);
            project.setDirectLibraries(oldProject.getDirectLibraries());
        }

        registerProject(dir, project);
        return project;
    }

    private static boolean hasOldDirectoryLayout(File dir) {
        if (new File(dir, "res").exists()) {
            // Should probably be src/main/res/
            return true;
        }

        if (new File(dir, "src").exists()
                && !(new File(dir, "src/main").exists())
                && !(new File(dir, "src/test").exists())) {
            return true;
        }

        File[] srcs = new File(dir, "src/main").listFiles();
        if (srcs != null) {
            for (File child : srcs) {
                String name = child.getName();
                if (name.startsWith("lint-")) {
                    continue;
                }
                switch (name) {
                    case FN_ANDROID_MANIFEST_XML:
                    case "res":
                    case "java":
                    case "kotlin":
                    case "lint.xml":
                        break;
                    default:
                        return true;
                }
            }
        }

        return false;
    }

    @Nullable
    @Override
    public File getCacheDir(@Nullable String name, boolean create) {
        File cacheDir = super.getCacheDir(name, create);
        // Separate test caches from user's normal caches
        cacheDir = new File(cacheDir, "unit-tests");
        if (create) {
            //noinspection ResultOfMethodCallIgnored
            cacheDir.mkdirs();
        }
        return cacheDir;
    }

    @NonNull
    @Override
    public String getDisplayPath(
            @NonNull File file, @Nullable Project project, @NonNull TextFormat format) {
        String path = super.getDisplayPath(file, project, format);
        return path.replace(File.separatorChar, '/'); // stable tests
    }

    @Override
    public String getClientRevision() {
        return "unittest"; // Hardcode version to keep unit test output stable
    }

    @SuppressWarnings("StringBufferField")
    private StringBuilder output = null;

    public String analyze(List<File> files, List<Issue> issues) throws Exception {
        // We'll sync lint options to flags later when the project is created, but try
        // to do it early before the driver is initialized
        if (!files.isEmpty()) {
            GradleModelMocker mocker = task.projectMocks.get(files.get(0));
            if (mocker != null) {
                mocker.syncFlagsTo(getFlags());
            }
        }

        LintRequest request = createLintRequest(files);
        if (task.customScope != null) {
            request = request.setScope(task.customScope);
        }
        if (task.platforms != null) {
            request.setPlatform(task.platforms);
        }

        if (incrementalCheck != null) {
            File projectDir = findIncrementalProject(files);
            assert projectDir != null;
            assertTrue(isProjectDirectory(projectDir));
            Project project = createProject(projectDir, projectDir);
            project.addFile(incrementalCheck);
            List<Project> projects = singletonList(project);
            request.setProjects(projects);
        }

        driver = createDriver(new TestIssueRegistry(issues), request);

        if (task.driverConfigurator != null) {
            task.driverConfigurator.configure(driver);
        }

        if (task.listener != null) {
            driver.addLintListener(task.listener);
        }

        validateIssueIds();

        driver.analyze();

        // Check compare contract
        Incident prev = null;
        List<Incident> incidents = getIncidents();
        for (Incident incident : incidents) {
            if (prev != null) {
                boolean equals = incident.equals(prev);
                assertEquals(equals, prev.equals(incident));
                int compare = incident.compareTo(prev);
                assertEquals(equals, compare == 0);
                assertEquals(-compare, prev.compareTo(incident));
            }
            prev = incident;
        }

        Collections.sort(incidents);

        // Check compare contract and transitivity
        Incident prev2 = prev;
        prev = null;
        for (Incident incident : incidents) {
            if (prev != null && prev2 != null) {
                assertTrue(incident.compareTo(prev) >= 0);
                assertTrue(prev.compareTo(prev2) >= 0);
                assertTrue(incident.compareTo(prev2) >= 0);

                assertTrue(prev.compareTo(incident) <= 0);
                assertTrue(prev2.compareTo(prev) <= 0);
                assertTrue(prev2.compareTo(incident) <= 0);
            }
            prev2 = prev;
            prev = incident;
        }

        LintStats stats = LintStats.Companion.create(getErrorCount(), getWarningCount());
        for (Reporter reporter : getFlags().getReporters()) {
            reporter.write(stats, incidents);
        }

        output.append(writer.toString());

        if (output.length() == 0) {
            output.append("No warnings.");
        }

        String result = output.toString();
        if (result.equals("No issues found.\n")) {
            result = "No warnings.";
        }

        result = cleanup(result);

        if (task.listener != null) {
            driver.removeLintListener(task.listener);
        }

        return result;
    }

    @NonNull
    @Override
    protected LintDriver createDriver(
            @NonNull IssueRegistry registry, @NonNull LintRequest request) {
        LintDriver driver = super.createDriver(registry, request);
        driver.setFatalOnlyMode(task.vital);
        return driver;
    }

    @Override
    public boolean isProjectDirectory(@NotNull File dir) {
        if (task.dirToProjectDescription.containsKey(dir)) {
            return true;
        }
        return super.isProjectDirectory(dir);
    }

    protected void addCleanupDir(@NonNull File dir) {
        cleanupDirs.add(dir);
        try {
            cleanupDirs.add(dir.getCanonicalFile());
        } catch (IOException e) {
            fail(e.getLocalizedMessage());
        }
        cleanupDirs.add(dir.getAbsoluteFile());
    }

    protected final Set<File> cleanupDirs = Sets.newHashSet();

    protected String cleanup(String result) {
        List<File> sorted = new ArrayList<>(cleanupDirs);
        // Process dirs in order such that we match longest substrings first
        sorted.sort(
                (file1, file2) -> {
                    String path1 = file1.getPath();
                    String path2 = file2.getPath();
                    int delta = path2.length() - path1.length();
                    if (delta != 0) {
                        return delta;
                    } else {
                        return path1.compareTo(path2);
                    }
                });

        for (File dir : sorted) {
            String path = dir.getPath();
            if (result.contains(path)) {
                result = result.replace(path, "/TESTROOT");
            }
            path = path.replace(File.separatorChar, '/');
            if (result.contains(path)) {
                result = result.replace(path, "/TESTROOT");
            }
        }

        return result;
    }

    public String getErrors() {
        return writer.toString();
    }

    @NonNull
    @Override
    public XmlParser getXmlParser() {
        //noinspection ConstantConditions
        if (task != null && !task.allowCompilationErrors) {
            return new LintCliXmlParser(this) {
                @Override
                public Document parseXml(@NonNull CharSequence xml, @Nullable File file) {
                    try {
                        return PositionXmlParser.parse(xml.toString());
                    } catch (Exception e) {
                        String message =
                                e.getCause() != null
                                        ? e.getCause().getLocalizedMessage()
                                        : e.getLocalizedMessage();
                        fail(
                                message
                                        + " : Failure processing source "
                                        + file
                                        + ".\n"
                                        + "If you want your test to work with broken XML sources, add "
                                        + "`allowCompilationErrors()` on the TestLintTask.\n");
                        return null;
                    }
                }
            };
        } else {
            return super.getXmlParser();
        }
    }

    @NonNull
    @Override
    public UastParser getUastParser(@Nullable Project project) {
        return new LintCliUastParser(project) {
            @Override
            public boolean prepare(
                    @NonNull List<? extends JavaContext> contexts,
                    @Nullable LanguageLevel javaLanguageLevel,
                    @Nullable LanguageVersionSettings kotlinLanguageLevel) {
                boolean ok = super.prepare(contexts, javaLanguageLevel, kotlinLanguageLevel);
                if (task.forceSymbolResolutionErrors) {
                    ok = false;
                }
                return ok;
            }

            @Nullable
            @Override
            public UFile parse(@NonNull JavaContext context) {
                UFile file = super.parse(context);

                if (!task.allowCompilationErrors) {
                    if (file != null) {
                        PsiErrorElement error =
                                PsiTreeUtil.findChildOfType(file.getPsi(), PsiErrorElement.class);
                        if (error != null) {
                            fail(
                                    "Found error element "
                                            + error
                                            + " in "
                                            + context.file.getName()
                                            + " with text \""
                                            + error.getText()
                                            + "\" inside \""
                                            + error.getParent().getText()
                                            + "\"");
                        }
                    } else {
                        fail(
                                "Failure processing source "
                                        + context.getProject().getRelativePath(context.file)
                                        + ": No UAST AST created");
                    }
                }

                return file;
            }
        };
    }

    @NonNull
    @Override
    public GradleVisitor getGradleVisitor() {
        return new GroovyGradleVisitor();
    }

    @Override
    public void runReadAction(@NonNull Runnable runnable) {
        boolean prev = insideReadAction;
        insideReadAction = true;
        try {
            super.runReadAction(runnable);
        } finally {
            insideReadAction = prev;
        }
    }

    @Override
    public <T> T runReadAction(@NonNull Computable<T> computable) {
        boolean prev = insideReadAction;
        insideReadAction = true;
        try {
            return super.runReadAction(computable);
        } finally {
            insideReadAction = prev;
        }
    }

    @Override
    public void report(
            @NonNull Context context,
            @NonNull Issue issue,
            @NonNull Severity severity,
            @NonNull Location location,
            @NonNull String message,
            @NonNull TextFormat format,
            @Nullable LintFix fix) {
        assertNotNull(location);

        if (fix != null && !task.allowExceptions) {
            Throwable throwable = LintFix.getData(fix, Throwable.class);
            if (throwable != null && this.firstThrowable == null) {
                this.firstThrowable = throwable;
            }
        }

        // Ensure that we're inside a read action if we might need access to PSI.
        // This is one heuristic; we could add more assertions elsewhere as needed.
        if (context instanceof JavaContext
                || context instanceof GradleContext
                || location.getSource() instanceof PsiElement) {
            assertTrue(
                    "LintClient.report accessing a PSI element should "
                            + "always be called inside a runReadAction",
                    insideReadAction);
        }

        if (issue == IssueRegistry.LINT_ERROR) {
            if (!task.allowSystemErrors) {
                return;
            }

            // We don't care about this error message from lint tests; we don't compile
            // test project files
            if (message.startsWith("No `.class` files were found in project")) {
                return;
            }
        }

        if (task.messageChecker != null) {
            task.messageChecker.checkReportedError(
                    context,
                    issue,
                    severity,
                    location,
                    format.convertTo(message, TextFormat.TEXT),
                    fix);
        }

        if (severity == Severity.FATAL) {
            // Treat fatal errors like errors in the golden files.
            severity = Severity.ERROR;
        }

        // For messages into all secondary locations to ensure they get
        // specifically included in the text report
        if (location.getSecondary() != null) {
            Location l = location.getSecondary();
            if (l == location) {
                fail("Location link cycle");
            }
            while (l != null) {
                if (l.getMessage() == null) {
                    l.setMessage("<No location-specific message");
                }
                if (l == l.getSecondary()) {
                    fail("Location link cycle");
                }
                l = l.getSecondary();
            }
        }

        super.report(context, issue, severity, location, message, format, fix);

        // Make sure errors are unique! See documentation for #allowDuplicates.
        if (!task.allowDuplicates) {
            Incident prev = null;
            for (Incident incident : getIncidents()) {
                assertNotSame(incident, prev);
                assertNotEquals(
                        ""
                                + "Warning (message, location) reported more than once; this "
                                + "typically means that your detector is incorrectly reaching "
                                + "the same element twice (for example, visiting each call of a method "
                                + "and reporting the error on the method itself), or that you should "
                                + "incorporate more details in your error message such as specific names "
                                + "of methods or variables to make each message unique if overlapping "
                                + "errors are expected. Identical error encountered at the same location "
                                + "more  than once: "
                                + incident,
                        incident,
                        prev);
                prev = incident;
            }
        }

        if (fix instanceof LintFix.ReplaceString) {
            LintFix.ReplaceString replaceFix = (LintFix.ReplaceString) fix;
            String oldPattern = replaceFix.oldPattern;
            String oldString = replaceFix.oldString;
            Location rangeLocation = replaceFix.range != null ? replaceFix.range : location;
            String contents = readFile(rangeLocation.getFile()).toString();
            Position start = rangeLocation.getStart();
            Position end = rangeLocation.getEnd();
            assert start != null;
            assert end != null;
            String locationRange = contents.substring(start.getOffset(), end.getOffset());

            if (oldString != null) {
                int startIndex = contents.indexOf(oldString, start.getOffset());
                if (startIndex == -1 || startIndex > end.getOffset()) {
                    if (!(oldString.equals(LintFix.ReplaceString.INSERT_BEGINNING)
                            || oldString.equals(LintFix.ReplaceString.INSERT_END))) {
                        fail(
                                "Did not find \""
                                        + oldString
                                        + "\" in \""
                                        + locationRange
                                        + "\" as suggested in the quickfix for issue "
                                        + issue
                                        + " (text in range was \""
                                        + locationRange
                                        + "\")");
                    }
                }
            } else if (oldPattern != null) {
                Pattern pattern = Pattern.compile(oldPattern);
                if (!pattern.matcher(locationRange).find()) {
                    fail(
                            "Did not match pattern \""
                                    + oldPattern
                                    + "\" in \""
                                    + locationRange
                                    + "\" as suggested in the quickfix for issue "
                                    + issue);
                }
            }
        }
    }

    @Override
    public void log(Throwable exception, String format, Object... args) {
        if (exception != null) {
            if (firstThrowable == null) {
                firstThrowable = exception;
            }
            exception.printStackTrace();
        }
        StringBuilder sb = new StringBuilder();
        if (format != null) {
            sb.append(String.format(format, args));
        }
        if (exception != null) {
            sb.append(exception.toString());
        }
        System.err.println(sb);

        if (exception != null) {
            // Ensure that we get the full cause
            // fail(exception.toString());
            throw new RuntimeException(exception);
        }
    }

    @NonNull
    @Override
    public Configuration getConfiguration(@NonNull Project project, @Nullable LintDriver driver) {
        return getConfigurations()
                .getConfigurationForProject(
                        project,
                        (client, file) ->
                                (project.getBuildModule() != null)
                                        ? new TestLintOptionsConfiguration(
                                                task,
                                                client,
                                                project.getBuildModule()
                                                        .getLintOptions()
                                                        .getLintConfig(),
                                                project.getDir(),
                                                project.getBuildModule().getLintOptions(),
                                                false)
                                        : new TestConfiguration(task, client, project));
    }

    @Override
    protected boolean addBootClassPath(
            @NonNull Collection<? extends Project> knownProjects, Set<File> files) {
        boolean ok = super.addBootClassPath(knownProjects, files);

        // Also add in the kotlin standard libraries if applicable
        if (hasKotlin(knownProjects)) {
            for (String path : findKotlinStdlibPath()) {
                files.add(new File(path));
            }
        }

        return ok;
    }

    private static boolean hasKotlin(Collection<? extends Project> projects) {
        for (Project project : projects) {
            for (File dir : project.getJavaSourceFolders()) {
                if (hasKotlin(dir)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasKotlin(File dir) {
        if (dir.getPath().endsWith(DOT_KT)) {
            return true;
        } else if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File sub : files) {
                    if (hasKotlin(sub)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Override
    public File findResource(@NonNull String relativePath) {
        if (relativePath.equals(LintExternalAnnotationsManager.SDK_ANNOTATIONS_PATH)) {
            try {
                File rootDir = TestUtils.getWorkspaceRoot();
                File file = new File(rootDir, "tools/adt/idea/android/annotations");
                if (!file.exists()) {
                    throw new RuntimeException("File " + file + " not found");
                }
                return file;
            } catch (Throwable ignore) {
                // Lint checks not running inside a tools build -- typically
                // a third party lint check.
                return super.findResource(relativePath);
            }
        } else if (relativePath.startsWith("tools/support/")) {
            try {
                File rootDir = TestUtils.getWorkspaceRoot();
                String base = relativePath.substring("tools/support/".length());
                File file = new File(rootDir, "tools/base/files/typos/" + base);
                if (!file.exists()) {
                    return null;
                }
                return file;
            } catch (Throwable ignore) {
                // Lint checks not running inside a tools build -- typically
                // a third party lint check.
                return super.findResource(relativePath);
            }
        } else if (relativePath.equals(ApiLookup.XML_FILE_PATH)) {
            File file = super.findResource(relativePath);
            if (file == null || !file.exists()) {
                throw new RuntimeException(
                        "File " + (file == null ? relativePath : file.getPath()) + " not found");
            }
            return file;
        }
        throw new RuntimeException("Resource " + relativePath + " not found.");
    }

    @NonNull
    @Override
    public List<File> findGlobalRuleJars() {
        // Don't pick up random custom rules in ~/.android/lint when running unit tests
        return Collections.emptyList();
    }

    public void setIncremental(File currentFile) {
        incrementalCheck = currentFile;
    }

    @Override
    public boolean supportsProjectResources() {
        if (task.supportResourceRepository != null) {
            return task.supportResourceRepository;
        }
        return incrementalCheck != null;
    }

    @SuppressWarnings("MethodMayBeStatic")
    @Nullable
    protected String getProjectResourceLibraryName() {
        return null;
    }

    @Nullable
    @Override
    public ResourceRepository getResourceRepository(
            Project project, boolean includeDependencies, boolean includeLibraries) {
        if (!supportsProjectResources()) {
            return null;
        }

        TestResourceRepository repository = new TestResourceRepository(RES_AUTO);
        ILogger logger = new StdLogger(StdLogger.Level.INFO);
        ResourceMerger merger = new ResourceMerger(0);

        ResourceNamespace namespace = project.getResourceNamespace();
        ResourceSet resourceSet =
                new ResourceSet(
                        project.getName(), namespace, getProjectResourceLibraryName(), true, null) {
                    @Override
                    protected void checkItems() {
                        // No checking in ProjectResources; duplicates can happen, but
                        // the project resources shouldn't abort initialization
                    }
                };
        // Only support 1 resource folder in test setup right now
        int size = project.getResourceFolders().size();
        assertTrue("Found " + size + " test resources folders", size <= 1);
        if (size == 1) {
            resourceSet.addSource(project.getResourceFolders().get(0));
        }

        try {
            resourceSet.loadFromFiles(logger);
            merger.addDataSet(resourceSet);
            repository.update(merger);

            // Make tests stable: sort the item lists!
            for (ListMultimap<String, ResourceItem> multimap :
                    repository.getResourceTable().values()) {
                ResourceRepositories.sortItemLists(multimap);
            }

            // Workaround: The repository does not insert ids from layouts! We need
            // to do that here.
            // TODO: namespaces
            Map<ResourceType, ListMultimap<String, ResourceItem>> items =
                    repository.getResourceTable().row(ResourceNamespace.TODO());
            ListMultimap<String, ResourceItem> layouts = items.get(ResourceType.LAYOUT);
            if (layouts != null) {
                for (ResourceItem item : layouts.values()) {
                    PathString source = item.getSource();
                    if (source == null) {
                        continue;
                    }
                    File file = source.toFile();
                    if (file == null) {
                        continue;
                    }
                    try {
                        String xml = Files.toString(file, Charsets.UTF_8);
                        Document document = XmlUtils.parseDocumentSilently(xml, true);
                        assertNotNull(document);
                        Set<String> ids = Sets.newHashSet();
                        addIds(ids, document); // TODO: pull parser
                        if (!ids.isEmpty()) {
                            ListMultimap<String, ResourceItem> idMap =
                                    items.computeIfAbsent(
                                            ResourceType.ID, k -> ArrayListMultimap.create());
                            for (String id : ids) {
                                ResourceMergerItem idItem =
                                        new ResourceMergerItem(
                                                id,
                                                ResourceNamespace.TODO(),
                                                ResourceType.ID,
                                                null,
                                                null,
                                                null);
                                String qualifiers = source.getParentFileName();
                                if (qualifiers.startsWith("layout-")) {
                                    qualifiers = qualifiers.substring("layout-".length());
                                } else if (qualifiers.equals("layout")) {
                                    qualifiers = "";
                                }

                                // Creating the resource file will set the source of
                                // idItem.
                                //noinspection ResultOfObjectAllocationIgnored
                                ResourceFile.createSingle(file, idItem, qualifiers);
                                idMap.put(id, idItem);
                            }
                        }
                    } catch (IOException e) {
                        fail(e.toString());
                    }
                }
            }
        } catch (MergingException e) {
            fail(e.getMessage());
        }

        return repository;
    }

    private static void addIds(Set<String> ids, Node node) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
            if (id != null && !id.isEmpty()) {
                ids.add(Lint.stripIdPrefix(id));
            }

            NamedNodeMap attributes = element.getAttributes();
            for (int i = 0, n = attributes.getLength(); i < n; i++) {
                Attr attribute = (Attr) attributes.item(i);
                String value = attribute.getValue();
                if (value.startsWith(NEW_ID_PREFIX)) {
                    ids.add(value.substring(NEW_ID_PREFIX.length()));
                }
            }
        }

        NodeList children = node.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            addIds(ids, child);
        }
    }

    @Nullable
    @Override
    public IAndroidTarget getCompileTarget(@NonNull Project project) {
        IAndroidTarget compileTarget = super.getCompileTarget(project);
        if (compileTarget == null) {
            if (task.requireCompileSdk && project.getBuildTargetHash() != null) {
                fail(
                        "Could not find SDK to compile with ("
                                + project.getBuildTargetHash()
                                + "). "
                                + "Either allow the test to use any installed SDK (it defaults to the "
                                + "highest version) via TestLintTask#requireCompileSdk(false), or make "
                                + "sure the SDK being used is the right  one via "
                                + "TestLintTask#sdkHome(File) or $ANDROID_HOME and that the actual SDK "
                                + "platform (platforms/"
                                + project.getBuildTargetHash()
                                + " is installed "
                                + "there");
            }

            IAndroidTarget[] targets = getTargets();
            for (int i = targets.length - 1; i >= 0; i--) {
                IAndroidTarget target = targets[i];
                if (target.isPlatform()) {
                    return target;
                }
            }
        }

        return compileTarget;
    }

    @NonNull
    @Override
    public Set<Desugaring> getDesugaring(@NonNull Project project) {
        if (task.desugaring != null) {
            return task.desugaring;
        }
        return super.getDesugaring(project);
    }

    @NonNull
    @Override
    public List<File> getTestSourceFolders(@NonNull Project project) {
        List<File> testSourceFolders = super.getTestSourceFolders(project);

        File tests = new File(project.getDir(), "test");
        if (tests.exists()) {
            List<File> all = Lists.newArrayList(testSourceFolders);
            all.add(tests);
            testSourceFolders = all;
        }

        return testSourceFolders;
    }

    @NotNull
    @Override
    public List<File> getExternalAnnotations(@NotNull Collection<? extends Project> projects) {
        List<File> externalAnnotations = Lists.newArrayList(super.getExternalAnnotations(projects));

        for (Project project : projects) {
            File annotationsZip = new File(project.getDir(), FN_ANNOTATIONS_ZIP);
            if (annotationsZip.isFile()) {
                externalAnnotations.add(annotationsZip);
            }
            File annotationsJar = new File(project.getDir(), FN_ANNOTATIONS_JAR);
            if (annotationsJar.isFile()) {
                externalAnnotations.add(annotationsJar);
            }
        }

        return externalAnnotations;
    }

    @Nullable
    @Override
    public URLConnection openConnection(@NonNull URL url, int timeout) throws IOException {
        if (task.mockNetworkData != null) {
            String query = url.toExternalForm();
            byte[] bytes = task.mockNetworkData.get(query);
            if (bytes != null) {
                return new URLConnection(url) {
                    @Override
                    public void connect() {}

                    @Override
                    public InputStream getInputStream() {
                        return new ByteArrayInputStream(bytes);
                    }
                };
            }
        }

        if (!task.allowNetworkAccess) {
            fail(
                    "Lint detector test attempted to read from the network. Normally this means "
                            + "that you have forgotten to set up mock data (calling networkData() on the "
                            + "lint task) or the URL no longer matches. The URL encountered was "
                            + url);
        }

        return super.openConnection(url, timeout);
    }

    public static class TestProject extends Project {
        @Nullable public final GradleModelMocker mocker;
        private final ProjectDescription projectDescription;

        public TestProject(
                @NonNull LintClient client,
                @NonNull File dir,
                @NonNull File referenceDir,
                @Nullable ProjectDescription projectDescription,
                @Nullable GradleModelMocker mocker) {
            super(client, dir, referenceDir);
            this.projectDescription = projectDescription;
            this.mocker = mocker;
            // In the old days merging was opt in, but we're almost exclusively using/supporting
            // Gradle projects now, so make this the default behavior for test projects too, even
            // if they don't explicitly opt into Gradle features like mocking during the test.
            // E.g. a simple project like
            //     ManifestDetectorTest#testUniquePermissionsPrunedViaManifestRemove
            // which simply registers library and app manifests (no build files) should exhibit
            // manifest merging.
            this.mergeManifests = true;
        }

        @Override
        public boolean isGradleProject() {
            return mocker != null || super.isGradleProject();
        }

        @Override
        public boolean isLibrary() {
            if (mocker != null && mocker.isLibrary()) {
                return true;
            }

            return super.isLibrary()
                    || projectDescription != null
                            && projectDescription.getType() == ProjectDescription.Type.LIBRARY;
        }

        @Override
        public boolean isAndroidProject() {
            if (mocker != null && (mocker.hasJavaPlugin() || mocker.hasJavaLibraryPlugin())) {
                return false;
            }

            return projectDescription == null
                    || projectDescription.getType() != ProjectDescription.Type.JAVA;
        }

        @Nullable
        @Override
        public Boolean dependsOn(@NonNull String artifact) {
            artifact = AndroidxNameUtils.getCoordinateMapping(artifact);

            LintModelVariant variant = getBuildVariant();
            if (mocker != null && variant != null) {
                // Simulate what the Gradle integration does
                // TODO: Do this more effectively; we have direct library lookup
                for (LintModelLibrary lib : variant.getMainArtifact().getDependencies().getAll()) {
                    if (libraryMatches(artifact, lib)) {
                        return true;
                    }
                }
            }

            return super.dependsOn(artifact);
        }

        private static boolean libraryMatches(@NonNull String artifact, LintModelLibrary lib) {
            if (!(lib instanceof LintModelExternalLibrary)) {
                return false;
            }
            LintModelMavenName coordinates =
                    ((LintModelExternalLibrary) lib).getResolvedCoordinates();
            String c = coordinates.getGroupId() + ':' + coordinates.getArtifactId();
            c = AndroidxNameUtils.getCoordinateMapping(c);
            return artifact.equals(c);
        }

        @Override
        public int getBuildSdk() {
            if (mocker != null) {
                String compileTarget = mocker.getProject().getCompileTarget();
                //noinspection ConstantConditions
                if (compileTarget != null && !compileTarget.isEmpty()) {
                    AndroidVersion version = AndroidTargetHash.getPlatformVersion(compileTarget);
                    if (version != null) {
                        return version.getApiLevel();
                    }
                }
            }

            return super.getBuildSdk();
        }

        @Nullable
        @Override
        public String getBuildTargetHash() {
            if (mocker != null) {
                String compileTarget = mocker.getProject().getCompileTarget();
                //noinspection ConstantConditions
                if (compileTarget != null && !compileTarget.isEmpty()) {
                    return compileTarget;
                }
            }

            return super.getBuildTargetHash();
        }

        @Override
        public boolean getReportIssues() {
            if (projectDescription != null && !projectDescription.getReport()) {
                return false;
            }
            return super.getReportIssues();
        }

        @Nullable
        @Override
        public LintModelVariant getBuildVariant() {
            if (cachedLintVariant != null) {
                return cachedLintVariant;
            }
            if (mocker != null) {
                LintModelModule module =
                        new LintModelFactory()
                                .create(
                                        mocker.getProject(),
                                        mocker.getVariants(),
                                        mocker.getProjectDir(),
                                        true);
                cachedLintVariant = null;
                for (LintModelVariant variant : module.getVariants()) {
                    if (variant.getOldVariant() == mocker.getVariant()) {
                        cachedLintVariant = variant;
                        break;
                    }
                }
                if (cachedLintVariant == null) {
                    cachedLintVariant = module.findVariant(mocker.getVariant().getName());
                }
            }

            return cachedLintVariant;
        }

        @Nullable private LintModelVariant cachedLintVariant = null;

        private List<LintModelSourceProvider> mProviders;

        private List<LintModelSourceProvider> getSourceProviders() {
            if (mProviders == null) {
                LintModelVariant variant = getBuildVariant();
                if (variant == null) {
                    mProviders = Collections.emptyList();
                } else {
                    mProviders = variant.getSourceProviders();
                }
            }

            return mProviders;
        }

        @NonNull
        @Override
        public List<File> getManifestFiles() {
            if (manifestFiles == null) {
                //noinspection VariableNotUsedInsideIf
                if (mocker != null) {
                    for (LintModelSourceProvider provider : getSourceProviders()) {
                        File manifestFile = provider.getManifestFile();
                        if (manifestFile.exists()) { // model returns path whether or not it exists
                            if (manifestFiles == null) {
                                manifestFiles = Lists.newArrayList();
                            }
                            manifestFiles.add(manifestFile);
                        }
                    }
                }

                if (manifestFiles == null) {
                    manifestFiles = super.getManifestFiles();
                }
            }

            return manifestFiles;
        }

        @NonNull
        @Override
        public List<File> getResourceFolders() {
            if (resourceFolders == null) {
                //noinspection VariableNotUsedInsideIf
                if (mocker != null) {
                    for (LintModelSourceProvider provider : getSourceProviders()) {
                        Collection<File> list = provider.getResDirectories();
                        for (File file : list) {
                            if (file.exists()) { // model returns path whether or not it exists
                                if (resourceFolders == null) {
                                    resourceFolders = Lists.newArrayList();
                                }
                                resourceFolders.add(file);
                            }
                        }
                    }
                }

                if (resourceFolders == null) {
                    resourceFolders = super.getResourceFolders();
                }
            }
            return resourceFolders;
        }

        @NonNull
        @Override
        public List<File> getJavaSourceFolders() {
            if (javaSourceFolders == null) {
                //noinspection VariableNotUsedInsideIf
                if (mocker != null) {
                    List<File> list = Lists.newArrayList();
                    for (LintModelSourceProvider provider : getSourceProviders()) {
                        Collection<File> srcDirs = provider.getJavaDirectories();
                        // model returns path whether or not it exists
                        for (File srcDir : srcDirs) {
                            if (srcDir.exists()) {
                                list.add(srcDir);
                            }
                        }
                    }
                    javaSourceFolders = list;
                }
                if (javaSourceFolders == null || javaSourceFolders.isEmpty()) {
                    javaSourceFolders = super.getJavaSourceFolders();
                }
            }

            return javaSourceFolders;
        }

        @NonNull
        @Override
        public List<File> getGeneratedSourceFolders() {
            // In the tests the only way to mark something as generated is "gen" or "generated"
            if (generatedSourceFolders == null) {
                //noinspection VariableNotUsedInsideIf
                if (mocker != null) {
                    generatedSourceFolders =
                            mocker.getVariant().getMainArtifact().getGeneratedSourceFolders()
                                    .stream()
                                    .filter(File::exists)
                                    .collect(Collectors.toList());
                }
                if (generatedSourceFolders == null || generatedSourceFolders.isEmpty()) {
                    generatedSourceFolders = super.getGeneratedSourceFolders();
                    if (generatedSourceFolders.isEmpty()) {
                        File generated = new File(dir, "generated");
                        if (generated.isDirectory()) {
                            generatedSourceFolders = singletonList(generated);
                        } else {
                            generated = new File(dir, FD_GEN_SOURCES);
                            if (generated.isDirectory()) {
                                generatedSourceFolders = singletonList(generated);
                            }
                        }
                    }

                    return generatedSourceFolders;
                }
            }

            return generatedSourceFolders;
        }

        @NonNull
        @Override
        public List<File> getGeneratedResourceFolders() {
            // In the tests the only way to mark something as generated is "gen" or "generated"
            if (generatedResourceFolders == null) {
                //noinspection VariableNotUsedInsideIf
                if (mocker != null) {
                    generatedResourceFolders =
                            mocker.getVariant().getMainArtifact().getGeneratedResourceFolders()
                                    .stream()
                                    .filter(File::exists)
                                    .collect(Collectors.toList());
                }
                if (generatedResourceFolders == null || generatedResourceFolders.isEmpty()) {
                    generatedResourceFolders = super.getGeneratedResourceFolders();
                }
            }

            return generatedResourceFolders;
        }

        @NonNull
        @Override
        public List<File> getTestSourceFolders() {
            if (testSourceFolders == null) {
                LintModelVariant variant = getBuildVariant();
                if (mocker != null && variant != null) {
                    testSourceFolders = Lists.newArrayList();

                    for (LintModelSourceProvider provider : variant.getTestSourceProviders()) {
                        Collection<File> srcDirs = provider.getJavaDirectories();
                        // model returns path whether or not it exists
                        for (File srcDir : srcDirs) {
                            if (srcDir.exists()) {
                                testSourceFolders.add(srcDir);
                            }
                        }
                    }
                }
                if (testSourceFolders == null || testSourceFolders.isEmpty()) {
                    testSourceFolders = super.getTestSourceFolders();
                }
            }

            return testSourceFolders;
        }
    }
}

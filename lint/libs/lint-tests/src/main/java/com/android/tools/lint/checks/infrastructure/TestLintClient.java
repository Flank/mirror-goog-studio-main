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
import static com.android.tools.lint.checks.infrastructure.LintTestUtils.checkTransitiveComparator;
import static java.io.File.separatorChar;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
import com.android.sdklib.IAndroidTarget;
import com.android.support.AndroidxNameUtils;
import com.android.tools.lint.LintCliClient;
import com.android.tools.lint.LintCliFlags;
import com.android.tools.lint.LintCliXmlParser;
import com.android.tools.lint.LintFixPerformer;
import com.android.tools.lint.LintResourceRepository;
import com.android.tools.lint.LintStats;
import com.android.tools.lint.Reporter;
import com.android.tools.lint.TextReporter;
import com.android.tools.lint.XmlFileType;
import com.android.tools.lint.XmlReader;
import com.android.tools.lint.XmlWriter;
import com.android.tools.lint.client.api.CircularDependencyException;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.ConfigurationHierarchy;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintListener;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.client.api.LintXmlConfiguration;
import com.android.tools.lint.client.api.ResourceRepositoryScope;
import com.android.tools.lint.client.api.UastParser;
import com.android.tools.lint.client.api.XmlParser;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Desugaring;
import com.android.tools.lint.detector.api.GradleContext;
import com.android.tools.lint.detector.api.Incident;
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
import com.android.tools.lint.model.LintModelLibrary;
import com.android.tools.lint.model.LintModelLintOptions;
import com.android.tools.lint.model.LintModelMavenName;
import com.android.tools.lint.model.LintModelModule;
import com.android.tools.lint.model.LintModelModuleType;
import com.android.tools.lint.model.LintModelSourceProvider;
import com.android.tools.lint.model.LintModelVariant;
import com.android.utils.ILogger;
import com.android.utils.NullLogger;
import com.android.utils.PositionXmlParser;
import com.android.utils.SdkUtils;
import com.android.utils.StdLogger;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.Computable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import kotlin.Pair;
import kotlin.io.FilesKt;
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

    private TextReporter reporter;

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
        reporter = new TextReporter(this, flags, writer, false);
        reporter.setForwardSlashPaths(true); // stable tests
        flags.getReporters().add(reporter);
    }

    @Override
    public String getClientDisplayName() {
        return "Lint Unit Tests";
    }

    public void setLintTask(@Nullable TestLintTask task) {
        if (task != null && task.optionSetter != null) {
            task.optionSetter.set(getFlags());
        }

        if (task != null) {
            reporter.setFormat(task.textFormat);
            reporter.setIncludeSecondaryLineContent(task.showSecondaryLintContent);
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
        String incrementalFileName = task.incrementalFileName;
        if (incrementalFileName == null) {
            if (files.size() == 1) {
                assert false : "Need to specify incremental file name if more than one project";
            } else {
                return files.get(0);
            }
        }
        if (files.size() > 1) {
            for (File dir : files) {
                File root = dir.getParentFile(); // Allow the project name to be part of the name
                File current = new File(root, incrementalFileName.replace('/', separatorChar));
                if (current.exists()) {
                    // Try to match project directory exactly
                    int index = incrementalFileName.indexOf('/');
                    if (index != -1) {
                        File path = new File(root, task.incrementalFileName.substring(0, index));
                        if (path.exists()) {
                            return path;
                        }
                    }
                    return dir;
                }
            }
        }

        for (File dir : files) {
            File current = new File(dir, incrementalFileName.replace('/', separatorChar));
            if (current.exists()) {
                return dir;
            }
        }

        for (File dir : files) {
            File current = new File(dir, "../" + incrementalFileName.replace('/', separatorChar));
            if (current.exists()) {
                return dir;
            }
        }

        // Just using basename? Search among all files
        for (File root : files) {
            for (File relative : getFilesRecursively(root)) {
                if (relative.getName().equals(incrementalFileName)) {
                    // Turn the basename into a full relative name
                    task.incrementalFileName = relative.getPath();
                    return root;
                }
            }
        }

        return null;
    }

    protected TestResultState checkLint(
            @NonNull File rootDir,
            @NonNull List<File> files,
            @NonNull List<Issue> issues,
            @NonNull TestMode mode)
            throws Exception {
        String incrementalFileName = task.incrementalFileName;
        if (incrementalFileName != null) {
            boolean found = false;

            File dir = findIncrementalProject(files);
            if (dir != null) {
                // may be reassigned by findIncrementalProject if using just a basename
                incrementalFileName = task.incrementalFileName;
                File current = new File(dir, incrementalFileName.replace('/', separatorChar));
                if (!current.exists()) {
                    // Specified the project name as part of the name to disambiguate
                    current =
                            new File(
                                    dir.getParentFile(),
                                    incrementalFileName.replace('/', separatorChar));
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
                                + incrementalFileName
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

        String result =
                mode == TestMode.PARTIAL
                        ? analyzeAndReportProvisionally(rootDir, files, issues)
                        : analyze(rootDir, files, issues);

        Throwable firstThrowable = task.runner.getFirstThrowable();
        return new TestResultState(this, rootDir, result, getDefiniteIncidents(), firstThrowable);
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
                if (path.length() > length && path.charAt(length) == separatorChar) {
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
        if (mocker != null) {
            if (mocker.getPrimary()) {
                mocker.syncFlagsTo(flags);
                flags.setFatalOnly(task.vital);

                String variantName = null;
                if (description != null && description.getVariantName() != null) {
                    variantName = description.getVariantName();
                } else if (task.variantName != null) {
                    variantName = task.variantName;
                }
                if (variantName != null) {
                    mocker.setVariantName(variantName);
                }
            } else if (description != null && description.getVariantName() != null) {
                mocker.setVariantName(description.getVariantName());
            }
        }
        if (task.baselineFile != null) {
            flags.setBaselineFile(task.baselineFile);
        }
        if (task.overrideConfigFile != null) {
            flags.setOverrideLintConfig(task.overrideConfigFile);
        }
        if (mocker != null && description != null && (mocker.hasJavaOrJavaLibraryPlugin())) {
            description.type(ProjectDescription.Type.JAVA);
        }

        if (description != null
                && task.reportFrom != null
                && !(task.reportFrom.isUnder(description)
                        || description.isUnder(task.reportFrom))) {
            if (task.reportFrom != description) {
                referenceDir =
                        ProjectDescription.Companion.getProjectDirectory(
                                task.reportFrom, referenceDir);
            } else {
                referenceDir = dir;
            }
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

    @Override
    public void storeState(@NonNull Project project) {
        super.storeState(project);

        // Make sure we don't have any absolute paths in the serialization files, if any,
        // as well as checking that all XML files are well-formed while we're at it.
        String absProjectPath = project.getDir().getAbsolutePath();
        String absTestRootPath = task.tempDir.getPath();
        String absHomePrefix = System.getProperty("user.home");
        String absProjectCanonicalPath = absProjectPath;
        String absTestRootCanonicalPath = absTestRootPath;
        try {
            absProjectCanonicalPath = project.getDir().getCanonicalPath();
            absTestRootCanonicalPath = task.tempDir.getCanonicalPath();
        } catch (IOException ignore) {
        }
        for (XmlFileType type : XmlFileType.values()) {
            File serializationFile = getSerializationFile(project, type);
            if (serializationFile.exists()) {
                String xml = FilesKt.readText(serializationFile, Charsets.UTF_8);
                if (type != XmlFileType.RESOURCE_REPOSITORY) {
                    // Skipping RESOURCE_REPOSITORY because these are not real
                    // XML files, but we're going to replace these soon so not
                    // worth going to the trouble of changing the file format
                    Document document = XmlUtils.parseDocumentSilently(xml, false);
                    assertNotNull("Not valid XML", document);
                }

                // Make sure we don't have any absolute paths to the test root or project
                // directories or home directories in the XML
                if (type.isPersistenceFile() || !getFlags().isFullPath()) {
                    assertFalse(xml, xml.contains(absProjectPath));
                    assertFalse(xml, xml.contains(absProjectCanonicalPath));
                    assertFalse(xml, xml.contains(absTestRootPath));
                    assertFalse(xml, xml.contains(absTestRootCanonicalPath));
                    assertFalse(xml, xml.contains(absHomePrefix));
                }
            }
        }
    }

    @Nullable
    @Override
    public File getCacheDir(@Nullable String name, boolean create) {
        File cacheDir;

        //noinspection ConstantConditions
        if (task != null && task.tempDir != null) {
            cacheDir = new File(task.tempDir, "lint-cache/" + name);
        } else {
            cacheDir = super.getCacheDir(name, create);
            // Separate test caches from user's normal caches
            cacheDir = new File(cacheDir, "unit-tests/" + name);
        }

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
        return path.replace(separatorChar, '/'); // stable tests
    }

    @Override
    public String getClientRevision() {
        return "unittest"; // Hardcode version to keep unit test output stable
    }

    @SuppressWarnings("StringBufferField")
    private final StringBuilder output = new StringBuilder();

    private interface LintTestAnalysis {
        void analyze(LintDriver driver);
    }

    public String analyze(File rootDir, List<File> files, List<Issue> issues) throws Exception {
        return analyze(rootDir, files, issues, LintDriver::analyze);
    }

    public String analyzeAndReportProvisionally(
            @NonNull File rootDir, @NonNull List<File> files, @NonNull List<Issue> issues)
            throws Exception {
        for (File dir : files) {
            // We don't want to share client instances between the analysis of each module
            // and the final report generation.
            TestLintClient client = task.runner.createClient();
            client.incrementalCheck = incrementalCheck;
            client.analyze(
                    rootDir, Collections.singletonList(dir), issues, LintDriver::analyzeOnly);
        }

        // This isn't expressing it right; I want to be able to say "load the graph from all of
        // these" but focus the report on one in particular
        return analyze(rootDir, files, issues, LintDriver::mergeOnly);
    }

    public String analyze(
            @NonNull File rootDir,
            @NonNull List<File> files,
            @NonNull List<Issue> issues,
            @NonNull LintTestAnalysis work)
            throws Exception {
        // We'll sync lint options to flags later when the project is created, but try
        // to do it early before the driver is initialized
        if (!files.isEmpty()) {
            GradleModelMocker mocker = task.projectMocks.get(files.get(0));
            if (mocker != null && mocker.getPrimary()) {
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
        if (files.size() > 1) {
            request.setSrcRoot(rootDir);
        }

        if (incrementalCheck != null) {
            File projectDir = findIncrementalProject(files);
            assert projectDir != null;
            assertTrue(isProjectDirectory(projectDir));
            Project project = createProject(projectDir, projectDir);
            project.addFile(incrementalCheck);
            List<Project> projects = singletonList(project);
            request.setProjects(projects);
            project.setReportIssues(true);
        }

        driver = createDriver(new TestIssueRegistry(issues), request);

        if (task.driverConfigurator != null) {
            task.driverConfigurator.configure(driver);
        }

        for (LintListener listener : task.listeners) {
            driver.addLintListener(listener);
        }

        validateIssueIds();

        work.analyze(driver);

        // Check compare contract
        Incident prev = null;
        List<Incident> incidents = getDefiniteIncidents();
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
        if (task.reportFrom != null) {
            useProjectRelativePaths(task.reportFrom);
        } else {
            useRootRelativePaths();
        }

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

        String result = writeOutput(incidents);

        for (LintListener listener : task.listeners) {
            driver.removeLintListener(listener);
        }

        return result;
    }

    @NonNull
    @Override
    protected LintRequest createLintRequest(@NonNull List<? extends File> files) {
        TestLintRequest request = new TestLintRequest(this, files);
        configureLintRequest(request);
        return request;
    }

    public static class TestLintRequest extends LintRequest {
        Project mainProject = null;

        public TestLintRequest(@NonNull LintClient client, @NonNull List<? extends File> files) {
            super(client, files);
        }

        @NonNull
        @Override
        public Project getMainProject(@NonNull Project project) {
            if (mainProject != null) {
                return mainProject;
            }
            for (Project main : project.getClient().getKnownProjects()) {
                if (main.getType() == LintModelModuleType.APP
                        && project.getAllLibraries().contains(project)) {
                    return main;
                }
            }
            return super.getMainProject(project);
        }
    }

    public String writeOutput(List<Incident> incidents) throws IOException {
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
    public boolean isProjectDirectory(@NonNull File dir) {
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
            result = task.stripRoot(dir, result);
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
                    ResolveCheckerKt.checkFile(context, file, task);
                }

                return file;
            }
        };
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
    public boolean supportsPartialAnalysis() {
        return driver.getMode() != LintDriver.DriverMode.GLOBAL;
    }

    @Override
    protected void mergeIncidents(
            @NonNull Project library,
            @NonNull Project main,
            @NonNull Context mainContext,
            @NonNull Map<Project, List<Incident>> definiteMap,
            @NonNull Map<Project, List<Incident>> provisionalMap) {
        // Some basic validity checks to make sure test projects aren't set up correctly;
        // normally AGP manifest merging enforces this
        // We have to initialize the manifest because these projects haven't been loaded
        // and processed
        if (library.getMinSdk() <= 1 && !library.getManifestFiles().isEmpty()) {
            String xml = FilesKt.readText(library.getManifestFiles().get(0), Charsets.UTF_8);
            Document document = XmlUtils.parseDocumentSilently(xml, true);
            if (document != null) {
                library.readManifest(document);
            }
        }
        if (main.getMinSdk() <= 1 && !main.getManifestFiles().isEmpty()) {
            String xml = FilesKt.readText(main.getManifestFiles().get(0), Charsets.UTF_8);
            Document document = XmlUtils.parseDocumentSilently(xml, true);
            if (document != null) {
                main.readManifest(document);
            }
        }
        int libraryMinSdk = library.getMinSdk() == -1 ? 1 : library.getMinSdk();
        int mainMinSdk = main.getMinSdk() == -1 ? 1 : main.getMinSdk();
        if (libraryMinSdk > mainMinSdk) {
            fail(
                    "Library project minSdkVersion ("
                            + libraryMinSdk
                            + ") cannot be higher than consuming project's minSdkVersion ("
                            + mainMinSdk
                            + ")");
        }

        ProjectDescription reportFrom = task.reportFrom;
        if (reportFrom != null) {
            setIncidentProjects(reportFrom, definiteMap);
            setIncidentProjects(reportFrom, provisionalMap);
        }

        super.mergeIncidents(library, main, mainContext, definiteMap, provisionalMap);
    }

    private void setIncidentProjects(ProjectDescription project, Map<Project, List<Incident>> map) {
        for (List<Incident> incidents : map.values()) {
            useProjectRelativePaths(project, incidents);
        }
    }

    @Override
    public void report(
            @NonNull Context context, @NonNull Incident incident, @NonNull TextFormat format) {
        Location location = incident.getLocation();
        LintFix fix = incident.getFix();
        Issue issue = incident.getIssue();
        assertNotNull(location);

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
            if (incident.getMessage().startsWith("No `.class` files were found in project")) {
                return;
            }
        }

        if (task.messageChecker != null
                // Don't run this if there's an internal failure instead; this is just going
                // to be confusing and we want to reach the final report comparison instead which
                // will show the lint error
                && !(!task.allowExceptions
                        && issue == IssueRegistry.LINT_ERROR
                        && fix instanceof LintFix.DataMap
                        && ((LintFix.DataMap) fix).getThrowable(LintDriver.KEY_THROWABLE)
                                != null)) {
            task.messageChecker.checkReportedError(
                    context,
                    incident.getIssue(),
                    incident.getSeverity(),
                    incident.getLocation(),
                    format.convertTo(incident.getMessage(), TextFormat.TEXT),
                    fix);
        }

        if (incident.getSeverity() == Severity.FATAL) {
            // Treat fatal errors like errors in the golden files.
            incident.setSeverity(Severity.ERROR);
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
                    l.setMessage("<No location-specific message>");
                }
                if (l == l.getSecondary()) {
                    fail("Location link cycle");
                }
                l = l.getSecondary();
            }
        }

        if (!task.allowExceptions) {
            Throwable throwable = LintFix.getThrowable(fix, LintDriver.KEY_THROWABLE);
            if (throwable != null && task.runner.getFirstThrowable() == null) {
                task.runner.setFirstThrowable(throwable);
            }
        }

        incident = checkIncidentSerialization(incident);

        if (fix != null) {
            checkFix(fix, incident);
        }

        super.report(context, incident, format);

        // Make sure errors are unique! See documentation for #allowDuplicates.
        List<Incident> incidents = getDefiniteIncidents();
        int incidentCount = incidents.size();
        if (incidentCount > 1) {
            Incident prev = incidents.get(incidentCount - 2);
            if (incident.equals(prev)) {
                if (task.allowDuplicates) {
                    // If we allow duplicates, don't list them multiple times.
                    incidents.remove(incidentCount - 1);
                    if (incident.getSeverity().isError()) {
                        setErrorCount(getErrorCount() - 1);
                    } else if (incident.getSeverity() == Severity.WARNING) {
                        // Don't count informational as a warning
                        setWarningCount(getWarningCount() - 1);
                    }
                } else {
                    TestMode mode = task.runner.getCurrentTestMode();
                    String field = mode.getFieldName();
                    String prologue =
                            ""
                                    + "java.lang.AssertionError: Incident (message, location) reported more\n"
                                    + "than once";

                    if (mode != TestMode.DEFAULT) {
                        prologue += "in test mode " + field;
                    }

                    prologue +=
                            "; this typically "
                                    + "means that your detector is incorrectly reaching the same element "
                                    + "twice (for example, visiting each call of a method and reporting the "
                                    + "error on the method itself), or that you should incorporate more "
                                    + "details in your error message such as specific names of methods or "
                                    + "variables to make each message unique if overlapping errors are "
                                    + "expected.\n"
                                    + "\n";

                    if (mode != TestMode.DEFAULT) {
                        prologue +=
                                ""
                                        + "To debug the unit test in this test mode, add the following to the"
                                        + "lint() test task: testModes("
                                        + field
                                        + ")\n"
                                        + "\n";
                    }

                    prologue = SdkUtils.wrap(prologue.substring(prologue.indexOf(':') + 2), 72, "");

                    String interlogue = "";
                    if (driver.getMode() == LintDriver.DriverMode.MERGE) {
                        interlogue =
                                ""
                                        + "This error happened while merging in provisional results; a common\n"
                                        + "cause for this is that your detector is\tusing the merged manifest from\n"
                                        + "each project to report problems. This means that these same errors are\n"
                                        + "reported repeatedly, from each sub project,\tinstead\tof only\tbeing\n"
                                        + "reported on the\tmain/app project. To fix this, consider\tadding a check\n"
                                        + "for (context.project == context.mainProject).\n"
                                        + "\n";
                    }
                    String epilogue =
                            ""
                                    + "If you *really* want to allow this, add .allowDuplicates() to the test\n"
                                    + "task.\n"
                                    + "\n"
                                    + "Identical incident encountered at the same location more than once:\n"
                                    + incident;

                    fail(prologue + interlogue + epilogue);
                }
            }
        }
    }

    private Incident checkIncidentSerialization(@NonNull Incident incident) {
        // Check persistence: serialize and deserialize the issue metadata and continue using the
        // deserialized version. It also catches cases where a detector modifies the incident
        // after reporting it.
        try {
            File xmlFile = File.createTempFile("incident", ".xml");
            XmlWriter xmlWriter =
                    new XmlWriter(this, XmlFileType.INCIDENTS, new FileWriter(xmlFile));
            xmlWriter.writeIncidents(Collections.singletonList(incident), emptyList());

            // Read it back
            List<Issue> issueList = singletonList(incident.getIssue());
            //noinspection MissingVendor
            IssueRegistry registry =
                    new IssueRegistry() {
                        @NonNull
                        @Override
                        public List<Issue> getIssues() {
                            return issueList;
                        }
                    };
            XmlReader xmlReader = new XmlReader(this, registry, incident.getProject(), xmlFile);
            incident = xmlReader.getIncidents().get(0);
            //noinspection ResultOfMethodCallIgnored
            xmlFile.delete();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return incident;
    }

    /** Validity checks for the quickfix associated with the given incident */
    private void checkFix(@NonNull LintFix fix, @NonNull Incident incident) {
        if (fix instanceof LintFix.ReplaceString) {
            LintFix.ReplaceString replaceFix = (LintFix.ReplaceString) fix;
            String oldPattern = replaceFix.getOldPattern();
            Location range = replaceFix.getRange();
            String oldString = replaceFix.getOldString();
            Location rangeLocation = range != null ? range : incident.getLocation();
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
                                        + incident.getIssue()
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
                                    + incident.getIssue());
                }
            }

            File file = range != null ? range.getFile() : incident.getLocation().getFile();
            if (!file.isFile()) {
                fail(file + " is not a file. Use fix().createFile instead of fix().replace.");
            }
        } else if (fix instanceof LintFix.CreateFileFix) {
            LintFix.CreateFileFix createFix = (LintFix.CreateFileFix) fix;
            File file = createFix.getFile();
            if (createFix.getDelete()) {
                assertTrue(file + " cannot be deleted; does not exist", file.exists());
            } else if (file.exists()) {
                fail("Cannot create file " + file + ": already exists");
            }
        }

        // Make sure any source files referenced by the quick fixes are loaded
        // for subsequent quickfix verifications (since those run after the
        // test projects have been deleted)
        readFixFiles(incident, fix);
    }

    private void readFixFiles(Incident incident, LintFix fix) {
        if (fix instanceof LintFix.LintFixGroup) {
            for (LintFix f : ((LintFix.LintFixGroup) fix).getFixes()) {
                readFixFiles(incident, f);
            }
        } else {
            Location location = LintFixPerformer.Companion.getLocation(incident, fix);
            File file = location.getFile();
            if (file.isFile()) {
                String displayName = fix.getDisplayName();
                assertTrue(
                        "Quickfix file paths must be absolute: "
                                + file
                                + " from "
                                + (displayName != null
                                        ? displayName
                                        : fix.getClass().getSimpleName()),
                        file.isAbsolute());
                getSourceText(file);
            }
        }
    }

    @Override
    protected void sortResults() {
        super.sortResults();
        // Make sure Incident comparator is correct. (See also IncidentTest#testComparator)
        checkTransitiveComparator(getDefiniteIncidents());
    }

    /**
     * If the test results are spread across more than one project, use root relative rather than
     * project relative paths
     */
    private void useRootRelativePaths() {
        boolean multipleProjects = false;
        Iterator<Incident> iterator = getDefiniteIncidents().iterator();
        if (iterator.hasNext()) {
            Incident prev = iterator.next();
            Project firstProject = prev.getProject();
            while (iterator.hasNext()) {
                Incident incident = iterator.next();
                if (firstProject != incident.getProject()) {
                    multipleProjects = true;
                    break;
                }
            }
        }
        if (multipleProjects) {
            // Null out projects on incidents such that it won't print project
            // local paths
            for (Incident incident : getDefiniteIncidents()) {
                incident.setProject(null);
            }
        }
    }

    /**
     * If the tests are configured to provide reporting from a specific project, set things up such
     * that all the paths are relative to that project's directory.
     */
    private void useProjectRelativePaths(ProjectDescription from) {
        useProjectRelativePaths(from, getDefiniteIncidents());
    }

    private void useProjectRelativePaths(ProjectDescription from, List<Incident> incidents) {
        if (incidents.isEmpty()) {
            return;
        }

        Project project = findProject(from);
        for (Incident incident : incidents) {
            Project incidentProject = incident.getProject();
            if (incidentProject != null && incidentProject != project) {
                File dir = incidentProject.getDir();
                Location location = ensureAbsolutePaths(dir, incident.getLocation());
                incident.setLocation(location);
                if (project != null) {
                    incident.setProject(project);
                }
                // TODO: Handle secondary
            }
        }
    }

    /** Returns the project instance for the given project description */
    @Nullable
    private Project findProject(ProjectDescription description) {
        for (Map.Entry<File, ProjectDescription> entry : task.dirToProjectDescription.entrySet()) {
            if (entry.getValue() == description) {
                return getDirToProject().get(entry.getKey());
            }
        }

        return null;
    }

    private static Location ensureAbsolutePaths(@NonNull File base, @NonNull Location location) {
        // Recurse to normalize all paths in the secondary linked list too
        Location secondary =
                location.getSecondary() != null
                        ? ensureAbsolutePaths(base, location.getSecondary())
                        : null;

        File file = location.getFile();
        if (!file.isAbsolute() || file.getPath().startsWith("../")) {
            String relative = file.getPath();
            File absolute = new File(base, relative);
            Position start = location.getStart();
            Position end = location.getEnd();
            location =
                    start != null && end != null
                            ? Location.create(absolute, start, end)
                            : Location.create(absolute);
        }
        if (secondary != null) {
            location.setSecondary(secondary);
        }

        return location;
    }

    @Override
    public void log(Throwable exception, String format, @NonNull Object... args) {
        if (exception != null) {
            if (task.runner.getFirstThrowable() == null) {
                task.runner.setFirstThrowable(exception);
            }
            exception.printStackTrace();
        }
        StringBuilder sb = new StringBuilder();
        if (format != null) {
            sb.append(String.format(format, args));
        }
        if (exception != null) {
            sb.append(exception);
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
    public Configuration getConfiguration(
            @NonNull com.android.tools.lint.detector.api.Project project,
            @Nullable final LintDriver driver) {
        if (!task.useTestConfiguration) {
            return super.getConfiguration(project, driver);
        }

        return getConfigurations()
                .getConfigurationForProject(
                        project,
                        (file, defaultConfiguration) ->
                                createConfiguration(project, defaultConfiguration));
    }

    private Configuration createConfiguration(
            @NonNull com.android.tools.lint.detector.api.Project project,
            @NonNull Configuration defaultConfiguration) {
        // Ensure that we have a fallback configuration which disables everything
        // except the relevant issues

        if (task.overrideConfigFile != null) {
            ConfigurationHierarchy configurations = getConfigurations();
            if (configurations.getOverrides() == null) {
                Configuration config =
                        LintXmlConfiguration.create(configurations, task.overrideConfigFile);
                configurations.addGlobalConfigurations(null, config);
            }
        }

        LintModelModule model = project.getBuildModule();
        final ConfigurationHierarchy configurations = getConfigurations();
        if (model != null) {
            LintModelLintOptions options = model.getLintOptions();
            return configurations.createLintOptionsConfiguration(
                    project,
                    options,
                    false,
                    defaultConfiguration,
                    () ->
                            new TestLintOptionsConfiguration(
                                    task, project, configurations, options, false));
        } else {
            return configurations.createChainedConfigurations(
                    project,
                    null,
                    () -> new TestConfiguration(task, configurations),
                    () -> {
                        File lintConfigXml =
                                ConfigurationHierarchy.Companion.getLintXmlFile(project.getDir());
                        if (lintConfigXml.isFile()) {
                            LintXmlConfiguration configuration =
                                    LintXmlConfiguration.create(configurations, lintConfigXml);
                            configuration.setFileLevel(false);
                            return configuration;
                        } else {
                            return null;
                        }
                    });
        }
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

    @NonNull
    @Override
    public List<File> findGlobalRuleJars() {
        // Don't pick up random custom rules in ~/.android/lint when running unit tests
        return Collections.emptyList();
    }

    public void setIncremental(File currentFile) {
        incrementalCheck = currentFile;
    }

    @NonNull
    @Override
    public ResourceRepository getResources(
            @NonNull Project project, @NonNull ResourceRepositoryScope scope) {
        task.requestedResourceRepository = true;

        // In special test mode requesting to check resource repositories
        // (default vs ~AGP one) ?
        if (!task.forceAgpResourceRepository) {
            // Default lint resource repository, used in production.
            // Instead of just returning the resource repository computed by super,
            // we'll compute it, then clear the in-memory caches, then call it again;
            // this will test the persistence mechanism as well which helps verify
            // correct serialization
            super.getResources(project, scope);
            LintResourceRepository.Companion.clearCaches(this, project);
            return super.getResources(project, scope);
        }

        ResourceNamespace namespace = project.getResourceNamespace();
        List<Project> projects = new ArrayList<>();
        projects.add(project);
        if (scope.includesDependencies()) {
            for (Project dep : project.getAllLibraries()) {
                if (!dep.isExternalLibrary()) {
                    projects.add(dep);
                }
            }
        }
        List<Pair<String, List<File>>> resourceSets = new ArrayList<>();
        for (Project p : projects) {
            List<File> resFolders = p.getResourceFolders();
            resourceSets.add(new Pair<>(project.getName(), resFolders));
        }

        // TODO: Allow TestLintTask pass libraryName (such as getProjectResourceLibraryName())
        return getResources(namespace, null, resourceSets, true);
    }

    /**
     * Create a test resource repository given a list of resource sets, where each resource set is a
     * resource set name and a list of resource folders
     */
    public static ResourceRepository getResources(
            @NonNull ResourceNamespace namespace,
            @Nullable String libraryName,
            @NonNull List<Pair<String, List<File>>> resourceSets,
            boolean reportErrors) {
        TestResourceRepository repository = new TestResourceRepository(RES_AUTO);
        ILogger logger = reportErrors ? new StdLogger(StdLogger.Level.INFO) : new NullLogger();
        ResourceMerger merger = new ResourceMerger(0);

        try {
            for (Pair<String, List<File>> pair : resourceSets) {
                String projectName = pair.getFirst();
                List<File> resFolders = pair.getSecond();
                ResourceSet resourceSet =
                        new ResourceSet(projectName, namespace, libraryName, true, null) {
                            @Override
                            protected void checkItems() {
                                // No checking in ProjectResources; duplicates can happen, but
                                // the project resources shouldn't abort initialization
                            }
                        };
                for (File res : resFolders) {
                    resourceSet.addSource(res);
                }
                resourceSet.loadFromFiles(logger);
                merger.addDataSet(resourceSet);
            }
            repository.update(merger);

            // Make tests stable: sort the item lists!
            for (ListMultimap<String, ResourceItem> map : repository.getResourceTable().values()) {
                ResourceRepositories.sortItemLists(map);
            }

            // Workaround: The repository does not insert ids from layouts! We need
            // to do that here.
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
                    String xml = FilesKt.readText(file, Charsets.UTF_8);
                    Document document = XmlUtils.parseDocumentSilently(xml, true);
                    assertNotNull(document);
                    Set<String> ids = Sets.newHashSet();
                    addIds(ids, document);
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
                            if (qualifiers == null) {
                                qualifiers = "";
                            } else if (qualifiers.startsWith("layout-")) {
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
                }
            }
        } catch (MergingException e) {
            if (reportErrors) {
                throw new RuntimeException(e);
            }
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

    @NonNull
    @Override
    public List<File> getExternalAnnotations(@NonNull Collection<? extends Project> projects) {
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
            if (mocker != null && (mocker.hasJavaOrJavaLibraryPlugin())) {
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
                Integer buildSdk = mocker.getBuildSdk();
                if (buildSdk != null) {
                    return buildSdk;
                }
            }

            return super.getBuildSdk();
        }

        @Nullable
        @Override
        public String getBuildTargetHash() {
            if (mocker != null) {
                String buildTargetHash = mocker.getBuildTargetHash();
                if (buildTargetHash != null) {
                    return buildTargetHash;
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
                cachedLintVariant = mocker.getLintVariant();
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
                            mocker.getGeneratedSourceFolders().stream()
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
                            mocker.getGeneratedSourceFolders().stream()
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

        public void setReferenceDir(File dir) {
            this.referenceDir = dir;
        }
    }
}

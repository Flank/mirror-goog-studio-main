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
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.Variant;
import com.android.ide.common.res2.AbstractResourceRepository;
import com.android.ide.common.res2.DuplicateDataException;
import com.android.ide.common.res2.MergingException;
import com.android.ide.common.res2.ResourceFile;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.res2.ResourceMerger;
import com.android.ide.common.res2.ResourceRepository;
import com.android.ide.common.res2.ResourceSet;
import com.android.resources.ResourceType;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.testutils.TestUtils;
import com.android.tools.lint.EcjParser;
import com.android.tools.lint.ExternalAnnotationRepository;
import com.android.tools.lint.LintCliClient;
import com.android.tools.lint.LintCliFlags;
import com.android.tools.lint.Reporter;
import com.android.tools.lint.TextReporter;
import com.android.tools.lint.Warning;
import com.android.tools.lint.client.api.CircularDependencyException;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintListener;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.tools.lint.psi.EcjPsiBuilder;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A {@link LintClient} class for use in lint unit tests.
 *
 * <p><b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
public class TestLintClient extends LintCliClient {
    protected final StringWriter writer = new StringWriter();
    protected File incrementalCheck;
    /** Managed by the {@link TestLintTask} */
    @SuppressWarnings("NullableProblems") 
    @NonNull TestLintTask task;

    public TestLintClient() {
        super(new LintCliFlags(), "test");
        flags.getReporters().add(new TextReporter(this, flags, writer, false));
    }

    /**
     * Normally having $ANDROID_BUILD_TOP set when running lint is a bad idea
     * (because it enables some special support in lint for checking code in AOSP
     * itself.) However, some lint tests (particularly custom lint checks) may not care
     * about this.
     */
    @SuppressWarnings("MethodMayBeStatic")
    protected boolean allowAndroidBuildEnvironment() {
        return true;
    }

    protected String checkLint(List<File> files, List<Issue> issues) throws Exception {
        if (task.incrementalFileName != null) {
            boolean found = false;
            for (File dir : files) {
                File current = new File(dir,
                        task.incrementalFileName.replace('/', File.separatorChar));
                if (current.exists()) {
                    setIncremental(current);
                    found = true;
                    break;
                }
            }
            if (!found) {
                fail("Could not find incremental file " + task.incrementalFileName
                        + " in the project folders " + files);
            }
        }

        if (!allowAndroidBuildEnvironment() && System.getenv("ANDROID_BUILD_TOP") != null) {
            fail("Don't run the lint tests with $ANDROID_BUILD_TOP set; that enables lint's "
                    + "special support for detecting AOSP projects (looking for .class "
                    + "files in $ANDROID_HOST_OUT etc), and this confuses lint.");
        }

        // Reset state here in case a client is reused for multiple runs
        output = new StringBuilder();
        writer.getBuffer().setLength(0);
        warnings.clear();
        errorCount = 0;
        warningCount = 0;

        String result = analyze(files, issues);

        if (runExtraTokenChecks()) {
            output.setLength(0);
            reset();
            try {
                //lintClient.warnings.clear();
                Field field = LintCliClient.class.getDeclaredField("warnings");
                field.setAccessible(true);
                List list = (List)field.get(this);
                list.clear();
            } catch (Throwable t) {
                fail(t.toString());
            }

            String secondResult;
            try {
                EcjPsiBuilder.setDebugOptions(true, true);
                secondResult = analyze(files, issues);
            } finally {
                EcjPsiBuilder.setDebugOptions(false, false);
            }

            assertEquals("The lint check produced different results when run on the "
                            + "normal test files and a version where parentheses and whitespace tokens "
                            + "have been inserted everywhere. The lint check should be resilient towards "
                            + "these kinds of differences (since in the IDE, PSI will include both "
                            + "types of nodes. Your detector should call LintUtils.skipParenthes(parent) "
                            + "to jump across parentheses nodes when checking parents, and there are "
                            + "similar methods in LintUtils to skip across whitespace siblings.\n",
                    result, secondResult);
        }

        // The output typically contains a few directory/filenames.
        // On Windows we need to change the separators to the unix-style
        // forward slash to make the test as OS-agnostic as possible.
        if (File.separatorChar != '/') {
            result = result.replace(File.separatorChar, '/');
        }

        for (File f : files) {
            TestUtils.deleteFile(f);
        }

        return result;
    }

    private boolean runExtraTokenChecks() {
        if (task.skipExtraTokenChecks) {
            return false;
        }
        for (Issue issue : task.getCheckedIssues()) {
            if (issue.getImplementation().getScope().contains(Scope.JAVA_FILE)) {
                Class<? extends Detector> detectorClass = issue.getImplementation()
                        .getDetectorClass();
                if (Detector.JavaPsiScanner.class.isAssignableFrom(detectorClass)) {
                    return true;
                }
            }
        }
        return false;
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
        if (projectDirs.contains(dir)) {
            throw new CircularDependencyException(
                    "Circular library dependencies; check your project.properties files carefully");
        }
        projectDirs.add(dir);

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
        if (task.variantName != null) {
            mocker.setVariantName(task.variantName);
        }

        return new TestProject(this, dir, referenceDir, description, mocker);
    }

    @Override
    public String getClientRevision() {
        return "unittest"; // Hardcode version to keep unit test output stable
    }

    public static class CustomIssueRegistry extends IssueRegistry {
        private final List<Issue> issues;

        public CustomIssueRegistry(List<Issue> issues) {
            this.issues = issues;
        }

        @NonNull
        @Override
        public List<Issue> getIssues() {
            return issues;
        }
    }

    @SuppressWarnings("StringBufferField")
    private StringBuilder output = null;

    public String analyze(List<File> files, List<Issue> issues) throws Exception {
        driver = new LintDriver(new CustomIssueRegistry(issues), this);

        if (task.driverConfigurator != null) {
            task.driverConfigurator.configure(driver);
        }

        if (task.listener != null) {
            driver.addLintListener(task.listener);
        }

        if (task.mockModifier != null) {
            driver.addLintListener((driver, type, context) -> {
                if (type == LintListener.EventType.SCANNING_PROJECT && context != null) {
                    Project project = context.getProject();
                    AndroidProject model = project.getGradleProjectModel();
                    Variant variant = project.getCurrentVariant();
                    if (model != null && variant != null) {
                        task.mockModifier.modify(model, variant);
                    }
                }
            });
        }

        LintRequest request = new LintRequest(this, files);
        if (incrementalCheck != null) {
            assertEquals(1, files.size());
            File projectDir = files.get(0);
            assertTrue(isProjectDirectory(projectDir));
            Project project = createProject(projectDir, projectDir);
            project.addFile(incrementalCheck);
            List<Project> projects = Collections.singletonList(project);
            request.setProjects(projects);
        }

        if (task.customScope != null) {
            request = request.setScope(task.customScope);
        }

        driver.analyze(request);

        // Check compare contract
        Warning prev = null;
        for (Warning warning : warnings) {
            if (prev != null) {
                boolean equals = warning.equals(prev);
                assertEquals(equals, prev.equals(warning));
                int compare = warning.compareTo(prev);
                assertEquals(equals, compare == 0);
                assertEquals(-compare, prev.compareTo(warning));
            }
            prev = warning;
        }

        Collections.sort(warnings);

        // Check compare contract and transitivity
        Warning prev2 = prev;
        prev = null;
        for (Warning warning : warnings) {
            if (prev != null && prev2 != null) {
                assertTrue(warning.compareTo(prev) >= 0);
                assertTrue(prev.compareTo(prev2) >= 0);
                assertTrue(warning.compareTo(prev2) >= 0);

                assertTrue(prev.compareTo(warning) <= 0);
                assertTrue(prev2.compareTo(prev) <= 0);
                assertTrue(prev2.compareTo(warning) <= 0);
            }
            prev2 = prev;
            prev = warning;
        }

        Reporter.Stats stats = new Reporter.Stats(errorCount, warningCount);
        for (Reporter reporter : flags.getReporters()) {
            reporter.write(stats, warnings);
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

    protected static String cleanup(String result) {
        // The output typically contains a few directory/filenames.
        // On Windows we need to change the separators to the unix-style
        // forward slash to make the test as OS-agnostic as possible.
        if (File.separatorChar != '/') {
            result = result.replace(File.separatorChar, '/');
        }

        return result;
    }

    public String getErrors() {
        return writer.toString();
    }

    @Override
    public JavaParser getJavaParser(@Nullable Project project) {
        return new EcjParser(this, project) {
            @Override
            public boolean prepareJavaParse(@NonNull List<JavaContext> contexts) {
                boolean success = super.prepareJavaParse(contexts);
                if (task.forceSymbolResolutionErrors) {
                    success = false;
                }
                boolean allowCompilationErrors = task.allowCompilationErrors;
                if (!allowCompilationErrors && ecjResult != null) {
                    StringBuilder sb = new StringBuilder();
                    for (CompilationUnitDeclaration unit : ecjResult.getCompilationUnits()) {
                        // so maybe I don't need my map!!
                        CategorizedProblem[] problems = unit.compilationResult()
                                .getAllProblems();
                        if (problems != null) {
                            for (IProblem problem : problems) {
                                if (problem == null || !problem.isError()) {
                                    continue;
                                }
                                String filename = new File(new String(
                                        problem.getOriginatingFileName())).getName();
                                sb.append(filename)
                                        .append(":")
                                        .append(problem.isError() ? "Error" : "Warning")
                                        .append(": ").append(problem.getSourceLineNumber())
                                        .append(": ").append(problem.getMessage())
                                        .append('\n');
                            }
                        }
                    }
                    if (sb.length() > 0) {
                        fail("Found compilation problems in lint test not overriding "
                                + "allowCompilationErrors():\n" + sb);
                    }

                }

                return success;
            }
        };
    }

    @Override
    public void report(
            @NonNull Context context,
            @NonNull Issue issue,
            @NonNull Severity severity,
            @NonNull Location location,
            @NonNull String message,
            @NonNull TextFormat format) {
        assertNotNull(location);

        if (task.allowSystemErrors && issue == IssueRegistry.LINT_ERROR) {
            return;
        }

        // Use plain ascii in the test golden files for now. (This also ensures
        // that the markup is well-formed, e.g. if we have a ` without a matching
        // closing `, the ` would show up in the plain text.)
        message = format.convertTo(message, TextFormat.TEXT);

        if (task.messageChecker != null) {
            task.messageChecker.checkReportedError(context, issue, severity,
                    location, message);
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

        super.report(context, issue, severity, location, message, format);

        // Make sure errors are unique!
        Warning prev = null;
        for (Warning warning : warnings) {
            assertNotSame(warning, prev);
            assert prev == null || !warning.equals(prev) : warning;
            prev = warning;
        }
    }

    @Override
    public void log(Throwable exception, String format, Object... args) {
        if (exception != null) {
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
            //fail(exception.toString());
            throw new RuntimeException(exception);
        }
    }

    @NonNull
    @Override
    public Configuration getConfiguration(@NonNull Project project,
            @Nullable LintDriver driver) {
        return new TestConfiguration(task, this, project, null);
    }

    @Override
    public File findResource(@NonNull String relativePath) {
        if (relativePath.equals(ExternalAnnotationRepository.SDK_ANNOTATIONS_PATH)) {
            File rootDir = TestUtils.getWorkspaceRoot();
            File file = new File(rootDir, "tools/adt/idea/android/annotations");
            if (!file.exists()) {
                throw new RuntimeException("File " + file + " not found");
            }
            return file;
        } else if (relativePath.startsWith("tools/support/")) {
            String base = relativePath.substring("tools/support/".length());
            File rootDir = TestUtils.getWorkspaceRoot();
            File file = new File(rootDir, "tools/base/files/typos/" + base);
            if (!file.exists()) {
                return null;
            }
            return file;
        } else if (relativePath.equals("platform-tools/api/api-versions.xml")) {
            File file = new File(getSdkHome(), relativePath);
            if (!file.exists()) {
                throw new RuntimeException("File " + file + " not found");
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
        return incrementalCheck != null;
    }

    @SuppressWarnings("MethodMayBeStatic")
    @Nullable
    protected String getProjectResourceLibraryName() {
        return null;
    }

    @Nullable
    @Override
    public AbstractResourceRepository getResourceRepository(Project project,
            boolean includeDependencies, boolean includeLibraries) {
        if (incrementalCheck == null) {
            return null;
        }

        ResourceRepository repository = new ResourceRepository(false);
        ILogger logger = new StdLogger(StdLogger.Level.INFO);
        ResourceMerger merger = new ResourceMerger(0);

        ResourceSet resourceSet = new ResourceSet(project.getName(),
                getProjectResourceLibraryName()) {
            @Override
            protected void checkItems() throws DuplicateDataException {
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
            merger.mergeData(repository.createMergeConsumer(), true);

            // Make tests stable: sort the item lists!
            Map<ResourceType, ListMultimap<String, ResourceItem>> map = repository.getItems();
            for (Map.Entry<ResourceType, ListMultimap<String, ResourceItem>> entry : map.entrySet()) {
                Map<String, List<ResourceItem>> m = Maps.newHashMap();
                ListMultimap<String, ResourceItem> value = entry.getValue();
                List<List<ResourceItem>> lists = Lists.newArrayList();
                for (Map.Entry<String, ResourceItem> e : value.entries()) {
                    String key = e.getKey();
                    ResourceItem item = e.getValue();

                    List<ResourceItem> list = m.get(key);
                    if (list == null) {
                        list = Lists.newArrayList();
                        lists.add(list);
                        m.put(key, list);
                    }
                    list.add(item);
                }

                for (List<ResourceItem> list : lists) {
                    list.sort((o1, o2) -> o1.getKey().compareTo(o2.getKey()));
                }

                // Store back in list multi map in new sorted order
                value.clear();
                for (Map.Entry<String, List<ResourceItem>> e : m.entrySet()) {
                    String key = e.getKey();
                    List<ResourceItem> list = e.getValue();
                    for (ResourceItem item : list) {
                        value.put(key, item);
                    }
                }
            }

            // Workaround: The repository does not insert ids from layouts! We need
            // to do that here.
            Map<ResourceType,ListMultimap<String,ResourceItem>> items = repository.getItems();
            ListMultimap<String, ResourceItem> layouts = items
                    .get(ResourceType.LAYOUT);
            if (layouts != null) {
                for (ResourceItem item : layouts.values()) {
                    ResourceFile source = item.getSource();
                    if (source == null) {
                        continue;
                    }
                    File file = source.getFile();
                    try {
                        String xml = Files.toString(file, Charsets.UTF_8);
                        Document document = XmlUtils.parseDocumentSilently(xml, true);
                        assertNotNull(document);
                        Set<String> ids = Sets.newHashSet();
                        addIds(ids, document); // TODO: pull parser
                        if (!ids.isEmpty()) {
                            ListMultimap<String, ResourceItem> idMap =
                                    items.computeIfAbsent(ResourceType.ID,
                                            k -> ArrayListMultimap.create());
                            for (String id : ids) {
                                ResourceItem idItem = new ResourceItem(id, ResourceType.ID,
                                        null, null);
                                String qualifiers = file.getParentFile().getName();
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
        }
        catch (MergingException e) {
            fail(e.getMessage());
        }

        return repository;
    }

    private static void addIds(Set<String> ids, Node node) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
            if (id != null && !id.isEmpty()) {
                ids.add(LintUtils.stripIdPrefix(id));
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

    public static class TestProject extends Project {
        @Nullable
        public final GradleModelMocker mocker;
        private final ProjectDescription projectDescription;

        public TestProject(@NonNull LintClient client, @NonNull File dir,
                @NonNull File referenceDir, @Nullable ProjectDescription projectDescription,
                @Nullable GradleModelMocker mocker) {
            super(client, dir, referenceDir);
            this.projectDescription = projectDescription;
            this.mocker = mocker;
        }

        @Override
        public boolean isGradleProject() {
            return mocker != null || super.isGradleProject();
        }

        @Nullable
        @Override
        public Variant getCurrentVariant() {
            return mocker != null ? mocker.getVariant() : null;
        }

        @Override
        public boolean isLibrary() {
            return super.isLibrary()  || projectDescription != null
                    && projectDescription.type == ProjectDescription.Type.LIBRARY;
        }

        @Override
        public boolean isAndroidProject() {
            return projectDescription == null ||
                    projectDescription.type != ProjectDescription.Type.JAVA;
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
        public AndroidProject getGradleProjectModel() {
            return mocker != null ? mocker.getProject() : null;
        }

        private List<SourceProvider> mProviders;

        private List<SourceProvider> getSourceProviders() {
            if (mProviders == null) {
                AndroidProject project = getGradleProjectModel();
                Variant variant = getCurrentVariant();
                if (project == null || variant == null) {
                    return Collections.emptyList();
                }

                List<SourceProvider> providers = Lists.newArrayList();
                AndroidArtifact mainArtifact = variant.getMainArtifact();

                providers.add(project.getDefaultConfig().getSourceProvider());

                for (String flavorName : variant.getProductFlavors()) {
                    for (ProductFlavorContainer flavor : project.getProductFlavors()) {
                        if (flavorName.equals(flavor.getProductFlavor().getName())) {
                            providers.add(flavor.getSourceProvider());
                            break;
                        }
                    }
                }

                SourceProvider multiProvider = mainArtifact.getMultiFlavorSourceProvider();
                if (multiProvider != null) {
                    providers.add(multiProvider);
                }

                String buildTypeName = variant.getBuildType();
                for (BuildTypeContainer buildType : project.getBuildTypes()) {
                    if (buildTypeName.equals(buildType.getBuildType().getName())) {
                        providers.add(buildType.getSourceProvider());
                        break;
                    }
                }

                SourceProvider variantProvider = mainArtifact.getVariantSourceProvider();
                if (variantProvider != null) {
                    providers.add(variantProvider);
                }

                mProviders = providers;
            }

            return mProviders;
        }

        @NonNull
        @Override
        public List<File> getManifestFiles() {
            if (manifestFiles == null) {
                //noinspection VariableNotUsedInsideIf
                if (mocker != null) {
                    for (SourceProvider provider : getSourceProviders()) {
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
                    for (SourceProvider provider : getSourceProviders()) {
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
                    javaSourceFolders = Lists.newArrayList();
                    for (SourceProvider provider : getSourceProviders()) {
                        Collection<File> srcDirs = provider.getJavaDirectories();
                        // model returns path whether or not it exists
                        javaSourceFolders.addAll(srcDirs.stream()
                                .filter(File::exists)
                                .collect(Collectors.toList()));
                    }
                }
                if (javaSourceFolders == null || javaSourceFolders.isEmpty()) {
                    javaSourceFolders = super.getJavaSourceFolders();
                }
            }

            return javaSourceFolders;
        }
    }
}
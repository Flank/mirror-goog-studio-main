/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint.client.api;

import static com.android.SdkConstants.ATTR_IGNORE;
import static com.android.SdkConstants.CLASS_CONSTRUCTOR;
import static com.android.SdkConstants.CONSTRUCTOR_NAME;
import static com.android.SdkConstants.DOT_CLASS;
import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.DOT_JAVA;
import static com.android.SdkConstants.DOT_KT;
import static com.android.SdkConstants.FD_GRADLE_WRAPPER;
import static com.android.SdkConstants.FN_GRADLE_WRAPPER_PROPERTIES;
import static com.android.SdkConstants.FN_LOCAL_PROPERTIES;
import static com.android.SdkConstants.FQCN_SUPPRESS_LINT;
import static com.android.SdkConstants.RES_FOLDER;
import static com.android.SdkConstants.SUPPRESS_ALL;
import static com.android.SdkConstants.SUPPRESS_LINT;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.ide.common.resources.configuration.FolderConfiguration.QUALIFIER_SPLITTER;
import static com.android.tools.lint.detector.api.LintUtils.isAnonymousClass;
import static java.io.File.separator;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.repository.ResourceVisibilityLookup;
import com.android.ide.common.res2.AbstractResourceRepository;
import com.android.ide.common.res2.ResourceItem;
import com.android.repository.api.ProgressIndicator;
import com.android.resources.ResourceFolderType;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.lint.client.api.LintListener.EventType;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.JavaPsiScanner;
import com.android.tools.lint.detector.api.Detector.JavaScanner;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.Pair;
import com.google.common.annotations.Beta;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.ast.Annotation;
import lombok.ast.AnnotationElement;
import lombok.ast.AnnotationMethodDeclaration;
import lombok.ast.AnnotationValue;
import lombok.ast.ArrayInitializer;
import lombok.ast.ConstructorDeclaration;
import lombok.ast.Expression;
import lombok.ast.MethodDeclaration;
import lombok.ast.Modifiers;
import lombok.ast.Node;
import lombok.ast.StrictListAccessor;
import lombok.ast.StringLiteral;
import lombok.ast.TypeDeclaration;
import lombok.ast.TypeReference;
import lombok.ast.VariableDefinition;
import org.jetbrains.uast.UElement;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Analyzes Android projects and files
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public class LintDriver {
    /**
     * Max number of passes to run through the lint runner if requested by
     * {@link #requestRepeat}
     */
    private static final int MAX_PHASES = 3;
    private static final String SUPPRESS_LINT_VMSIG = '/' + SUPPRESS_LINT + ';';
    /** Prefix used by the comment suppress mechanism in Studio/IntelliJ */
    private static final String STUDIO_ID_PREFIX = "AndroidLint";

    private final LintClient client;
    private LintRequest request;
    private IssueRegistry registry;
    private volatile boolean canceled;
    private EnumSet<Scope> scope;
    private List<? extends Detector> applicableDetectors;
    private Map<Scope, List<Detector>> scopeDetectors;
    private List<LintListener> listeners;
    private int phase;
    private List<Detector> repeatingDetectors;
    private EnumSet<Scope> repeatScope;
    private Project[] currentProjects;
    private Project currentProject;
    private boolean abbreviating = true;
    private boolean parserErrors;
    private Map<Object,Object> properties;
    /** Whether we need to look for legacy (old Lombok-based Java API) detectors */
    private boolean runLombokCompatChecks = false;
    /** Whether we need to look for legacy (old PSI) detectors */
    private boolean runPsiCompatChecks = false;
    /** Whether we should run all normal checks on test sources */
    private boolean checkTestSources;
    /** Whether we should include generated sources in the analysis */
    private boolean checkGeneratedSources;
    /** Whether we're only analyzing fatal-severity issues */
    private boolean fatalOnlyMode;
    private LintBaseline baseline;

    /**
     * Creates a new {@link LintDriver}
     *
     * @param registry The registry containing issues to be checked
     * @param client the tool wrapping the analyzer, such as an IDE or a CLI
     */
    public LintDriver(@NonNull IssueRegistry registry, @NonNull LintClient client) {
        this.registry = registry;
        this.client = new LintClientWrapper(client);
    }

    /**
     * Number of fatal exceptions (internal errors, usually from ECJ) we've
     * encountered; we don't log each and every one to avoid massive log spam
     * in code which triggers this condition
     */
    private static int exceptionCount;

    /** Max number of logs to include */
    private static final int MAX_REPORTED_CRASHES = 20;

    /** Logs the given error produced by the various lint detectors */
    public static void handleDetectorError(@NonNull Context context, @NonNull RuntimeException e) {
        String simpleClassName = e.getClass().getSimpleName();
        if (simpleClassName.equals("IndexNotReadyException")) {
            // Attempting to access PSI during startup before indices are ready; ignore these.
            // See http://b.android.com/176644 for an example.
            return;
        } else if (e instanceof ProcessCanceledException ||
                simpleClassName.equals("ProcessCanceledException")) {
            // Cancelling inspections in the IDE
            context.getDriver().cancel();
            return;
        }

        if (exceptionCount++ > MAX_REPORTED_CRASHES) {
            // No need to keep spamming the user that a lot of the files
            // are tripping up ECJ, they get the picture.
            return;
        }

        StringBuilder sb = new StringBuilder(100);
        sb.append("Unexpected failure during lint analysis of ");
        sb.append(context.file.getName());
        sb.append(" (this is a bug in lint or one of the libraries it depends on)\n");

        sb.append(simpleClassName);
        sb.append(':');
        StackTraceElement[] stackTrace = e.getStackTrace();
        int count = 0;
        for (StackTraceElement frame : stackTrace) {
            if (count > 0) {
                sb.append("<-");
            }

            String className = frame.getClassName();
            sb.append(className.substring(className.lastIndexOf('.') + 1));
            sb.append('.').append(frame.getMethodName());
            sb.append('(');
            sb.append(frame.getFileName()).append(':').append(frame.getLineNumber());
            sb.append(')');
            count++;
            // Only print the top N frames such that we can identify the bug
            if (count == 8) {
                break;
            }
        }
        Throwable throwable = null; // NOT e: this makes for very noisy logs
        //noinspection ConstantConditions
        context.log(throwable, sb.toString());

        if (VALUE_TRUE.equals(System.getenv("LINT_PRINT_STACKTRACE"))) {
            e.printStackTrace();
        }
    }

    /**
     * For testing only: returns the number of exceptions thrown during Java AST analysis
     *
     * @return the number of internal errors found
     */
    @VisibleForTesting
    public static int getCrashCount() {
        return exceptionCount;
    }

    /**
     * For testing only: clears the crash counter
     */
    @VisibleForTesting
    public static void clearCrashCount() {
        exceptionCount = 0;
    }

    /** Cancels the current lint run as soon as possible */
    public void cancel() {
        canceled = true;
    }

    /**
     * Returns the scope for the lint job
     *
     * @return the scope, never null
     */
    @NonNull
    public EnumSet<Scope> getScope() {
        return scope;
    }

    /**
     * Sets the scope for the lint job
     *
     * @param scope the scope to use
     */
    public void setScope(@NonNull EnumSet<Scope> scope) {
        this.scope = scope;
    }

    /**
     * Returns the lint client requesting the lint check. This may not be the same
     * instance as the one passed in to this driver; lint uses a wrapper which performs
     * additional validation to ensure that for example badly behaved detectors which report
     * issues that have been disabled will get muted without the real lint client getting
     * notified. Thus, this {@link LintClient} is suitable for use by detectors to look
     * up a client to for example get location handles from, but tool handling code should
     * never try to cast this client back to their original lint client. For the original
     * lint client, use {@link LintRequest} instead.
     *
     * @return the client, never null
     */
    @NonNull
    public LintClient getClient() {
        return client;
    }

    /**
     * Returns the current request, which points to the original files to be checked,
     * the original scope, the original {@link LintClient}, as well as the release mode.
     *
     * @return the request
     */
    @NonNull
    public LintRequest getRequest() {
        return request;
    }

    /**
     * Records a property for later retrieval by {@link #getProperty(Object)}
     *
     * @param key the key to associate the value with
     * @param value the value, or null to remove a previous binding
     */
    public void putProperty(@NonNull Object key, @Nullable Object value) {
        if (properties == null) {
            properties = Maps.newHashMap();
        }
        if (value == null) {
            properties.remove(key);
        } else {
            properties.put(key, value);
        }
    }

    /**
     * Returns the property previously stored with the given key, or null
     *
     * @param key the key
     * @return the value or null if not found
     */
    @Nullable
    public Object getProperty(@NonNull Object key) {
        if (properties != null) {
            return properties.get(key);
        }

        return null;
    }

    /** Whether we're only analyzing fatal-severity issues */
    public boolean isFatalOnlyMode() {
        return fatalOnlyMode;
    }

    /** Sets whether we're only analyzing fatal-severity issues */
    public void setFatalOnlyMode(boolean fatalOnlyMode) {
        this.fatalOnlyMode = fatalOnlyMode;
    }

    @Nullable
    public LintBaseline getBaseline() {
        return baseline;
    }

    public void setBaseline(@Nullable LintBaseline baseline) {
        this.baseline = baseline;
    }

    /**
     * Returns the current phase number. The first pass is numbered 1. Only one pass
     * will be performed, unless a {@link Detector} calls {@link #requestRepeat}.
     *
     * @return the current phase, usually 1
     */
    public int getPhase() {
        return phase;
    }

    /**
     * Returns the current {@link IssueRegistry}.
     *
     * @return the current {@link IssueRegistry}
     */
    @NonNull
    public IssueRegistry getRegistry() {
        return registry;
    }

    /**
     * Returns the project containing a given file, or null if not found. This searches
     * only among the currently checked project and its library projects, not among all
     * possible projects being scanned sequentially.
     *
     * @param file the file to be checked
     * @return the corresponding project, or null if not found
     */
    @Nullable
    public Project findProjectFor(@NonNull File file) {
        if (currentProjects != null) {
            if (currentProjects.length == 1) {
                return currentProjects[0];
            }
            String path = file.getPath();
            for (Project project : currentProjects) {
                if (path.startsWith(project.getDir().getPath())) {
                    return project;
                }
            }
        }

        return null;
    }

    /**
     * Sets whether lint should abbreviate output when appropriate.
     *
     * @param abbreviating true to abbreviate output, false to include everything
     */
    public void setAbbreviating(boolean abbreviating) {
        this.abbreviating = abbreviating;
    }

    /**
     * Sets whether lint should run all the normal checks on the test sources
     * (instead of just the checks that have opted into considering tests).
     *
     * @param checkTestSources true to run all the checks on all test sources
     */
    public void setCheckTestSources(boolean checkTestSources) {
        this.checkTestSources = checkTestSources;
    }

    /**
     * Returns whether lint will run all the normal checks on the test sources
     * (instead of just the checks that have opted into considering tests).
     *
     * @return true iff lint will run all the checks on test sources
     */
    public boolean isCheckTestSources() {
        return checkTestSources;
    }


    public void setCheckGeneratedSources(boolean checkGeneratedSources) {
        this.checkGeneratedSources = checkGeneratedSources;
    }

    public boolean isCheckGeneratedSources() {
        return checkGeneratedSources;
    }

    /**
     * Returns whether lint should abbreviate output when appropriate.
     *
     * @return true if lint should abbreviate output, false when including everything
     */
    public boolean isAbbreviating() {
        return abbreviating;
    }

    /**
     * Returns whether lint has encountered any files with fatal parser errors
     * (e.g. broken source code, or even broken parsers)
     * <p>
     * This is useful for checks that need to make sure they've seen all data in
     * order to be conclusive (such as an unused resource check).
     *
     * @return true if any files were not properly processed because they
     *         contained parser errors
     */
    public boolean hasParserErrors() {
        return parserErrors;
    }

    /**
     * Sets whether lint has encountered files with fatal parser errors.
     *
     * @see #hasParserErrors()
     * @param hasErrors whether parser errors have been encountered
     */
    public void setHasParserErrors(boolean hasErrors) {
        parserErrors = hasErrors;
    }

    /**
     * Returns the projects being analyzed
     *
     * @return the projects being analyzed
     */
    @NonNull
    public List<Project> getProjects() {
        if (currentProjects != null) {
            return Arrays.asList(currentProjects);
        }
        return Collections.emptyList();
    }

    /**
     * Analyze the given file (which can point to an Android project). Issues found
     * are reported to the associated {@link LintClient}.
     *
     * @param files the files and directories to be analyzed
     * @param scope the scope of the analysis; detectors with a wider scope will
     *            not be run. If null, the scope will be inferred from the files.
     * @deprecated use {@link #analyze(LintRequest) instead}
     */
    @Deprecated
    public void analyze(@NonNull List<File> files, @Nullable EnumSet<Scope> scope) {
        analyze(new LintRequest(client, files).setScope(scope));
    }

    /**
     * Analyze the given files (which can point to Android projects or directories
     * containing Android projects). Issues found are reported to the associated
     * {@link LintClient}.
     * <p>
     * Note that the {@link LintDriver} is not multi thread safe or re-entrant;
     * if you want to run potentially overlapping lint jobs, create a separate driver
     * for each job.
     *
     * @param request the files and directories to be analyzed
     */
    public void analyze(@NonNull LintRequest request) {
        try {
            this.request = request;
            analyze();
        } finally {
            this.request = null;
        }
    }

    /** Runs the driver to analyze the requested files */
    private void analyze() {
        canceled = false;
        scope = request.getScope();
        assert scope == null || !scope.contains(Scope.ALL_RESOURCE_FILES) ||
                scope.contains(Scope.RESOURCE_FILE);

        Collection<Project> projects;
        try {
            projects = request.getProjects();
            if (projects == null) {
                projects = computeProjects(request.getFiles());
            }
            client.initializeProjects(projects);
        } catch (CircularDependencyException e) {
            currentProject = e.getProject();
            if (currentProject != null) {
                Location location = e.getLocation();
                File file = location != null ? location.getFile() : currentProject.getDir();
                Context context = new Context(this, currentProject, null, file);
                context.report(IssueRegistry.LINT_ERROR, e.getLocation(), e.getMessage());
                currentProject = null;
            }
            return;
        }
        if (projects.isEmpty()) {
            client.log(null, "No projects found for %1$s", request.getFiles().toString());
            return;
        }
        if (canceled) {
            client.disposeProjects(projects);
            return;
        }

        registerCustomDetectors(projects);

        if (scope == null) {
            scope = Scope.infer(projects);
        }

        // See if the lint.xml file specifies a baseline and we're not in incremental mode
        if (baseline == null && scope.size() > 2) {
            Project lastProject = Iterables.getLast(projects);
            Configuration mainConfiguration = client.getConfiguration(lastProject, this);
            File baselineFile = mainConfiguration.getBaselineFile();
            if (baselineFile != null) {
                baseline = new LintBaseline(client, baselineFile);
            }
        }

        fireEvent(EventType.STARTING, null);

        for (Project project : projects) {
            phase = 1;

            Project main = request.getMainProject(project);

            // The set of available detectors varies between projects
            computeDetectors(project);

            if (applicableDetectors.isEmpty()) {
                // No detectors enabled in this project: skip it
                continue;
            }

            checkProject(project, main);
            if (canceled) {
                break;
            }

            runExtraPhases(project, main);
        }

        if (baseline != null) {
            Project lastProject = Iterables.getLast(projects);
            Project main = request.getMainProject(lastProject);
            baseline.reportBaselineIssues(this, main);
        }

        fireEvent(canceled ? EventType.CANCELED : EventType.COMPLETED, null);
        client.disposeProjects(projects);
    }

    private void registerCustomDetectors(Collection<Project> projects) {
        // Look at the various projects, and if any of them provide a custom
        // lint jar, "add" them (this will replace the issue registry with
        // a CompositeIssueRegistry containing the original issue registry
        // plus JarFileIssueRegistry instances for each lint jar
        Set<File> jarFiles = Sets.newHashSet();
        for (Project project : projects) {
            jarFiles.addAll(client.findRuleJars(project));
            for (Project library : project.getAllLibraries()) {
                jarFiles.addAll(client.findRuleJars(library));
            }
        }

        jarFiles.addAll(client.findGlobalRuleJars());

        if (!jarFiles.isEmpty()) {
            List<IssueRegistry> registries = Lists.newArrayListWithExpectedSize(jarFiles.size());
            registries.add(registry);
            for (File jarFile : jarFiles) {
                try {
                    JarFileIssueRegistry registry = JarFileIssueRegistry.get(client, jarFile);
                    if (registry.hasLombokLegacyDetectors()) {
                        runLombokCompatChecks = true;
                        runPsiCompatChecks = true;
                    } else if (registry.hasPsiLegacyDetectors()) {
                        runPsiCompatChecks = true;
                    }
                    registries.add(registry);
                } catch (Throwable e) {
                    client.log(e, "Could not load custom rule jar file %1$s", jarFile);
                }
            }
            if (registries.size() > 1) { // the first item is registry itself
                registry = new CompositeIssueRegistry(registries);
            }
        }
    }

    /**
     * Sets whether the lint driver should look for compatibility checks for Lombok and
     * PSI (the older {@link JavaScanner} and {@link JavaPsiScanner} APIs.)
     * <p>
     * Lint normally figures this out on its own by inspecting JAR file registries
     * etc. This is intended for test infrastructure usage.
     *
     * @param lombok whether to run Lombok compat checks
     * @param psi    whether to run PSI compat checks
     */
    public void setRunCompatChecks(boolean lombok, boolean psi) {
        runLombokCompatChecks = lombok;
        runPsiCompatChecks = psi;
    }

    private void runExtraPhases(@NonNull Project project, @NonNull Project main) {
        // Did any detectors request another phase?
        if (repeatingDetectors != null) {
            // Yes. Iterate up to MAX_PHASES times.

            // During the extra phases, we might be narrowing the scope, and setting it in the
            // scope field such that detectors asking about the available scope will get the
            // correct result. However, we need to restore it to the original scope when this
            // is done in case there are other projects that will be checked after this, since
            // the repeated phases is done *per project*, not after all projects have been
            // processed.
            EnumSet<Scope> oldScope = scope;

            do {
                phase++;
                fireEvent(EventType.NEW_PHASE,
                        new Context(this, project, null, project.getDir()));

                // Narrow the scope down to the set of scopes requested by
                // the rules.
                if (repeatScope == null) {
                    repeatScope = Scope.ALL;
                }
                scope = Scope.intersect(scope, repeatScope);
                if (scope.isEmpty()) {
                    break;
                }

                // Compute the detectors to use for this pass.
                // Unlike the normal computeDetectors(project) call,
                // this is going to use the existing instances, and include
                // those that apply for the configuration.
                computeRepeatingDetectors(repeatingDetectors, project);

                if (applicableDetectors.isEmpty()) {
                    // No detectors enabled in this project: skip it
                    continue;
                }

                checkProject(project, main);
                if (canceled) {
                    break;
                }
            } while (phase < MAX_PHASES && repeatingDetectors != null);

            scope = oldScope;
        }
    }

    private void computeRepeatingDetectors(List<Detector> detectors, Project project) {
        // Ensure that the current visitor is recomputed
        currentFolderType = null;
        currentVisitor = null;
        currentXmlDetectors = null;
        currentBinaryDetectors = null;

        // Create map from detector class to issue such that we can
        // compute applicable issues for each detector in the list of detectors
        // to be repeated
        List<Issue> issues = registry.getIssues();
        Multimap<Class<? extends Detector>, Issue> issueMap =
                ArrayListMultimap.create(issues.size(), 3);
        for (Issue issue : issues) {
            issueMap.put(issue.getImplementation().getDetectorClass(), issue);
        }

        Map<Class<? extends Detector>, EnumSet<Scope>> detectorToScope =
                new HashMap<>();
        Map<Scope, List<Detector>> scopeToDetectors =
                new EnumMap<>(Scope.class);

        List<Detector> detectorList = new ArrayList<>();
        // Compute the list of detectors (narrowed down from repeatingDetectors),
        // and simultaneously build up the detectorToScope map which tracks
        // the scopes each detector is affected by (this is used to populate
        // the scopeDetectors map which is used during iteration).
        Configuration configuration = project.getConfiguration(this);
        for (Detector detector : detectors) {
            Class<? extends Detector> detectorClass = detector.getClass();
            Collection<Issue> detectorIssues = issueMap.get(detectorClass);
            if (detectorIssues != null) {
                boolean add = false;
                for (Issue issue : detectorIssues) {
                    // The reason we have to check whether the detector is enabled
                    // is that this is a per-project property, so when running lint in multiple
                    // projects, a detector enabled only in a different project could have
                    // requested another phase, and we end up in this project checking whether
                    // the detector is enabled here.
                    if (!configuration.isEnabled(issue)) {
                        continue;
                    }

                    add = true; // Include detector if any of its issues are enabled

                    EnumSet<Scope> s = detectorToScope.get(detectorClass);
                    EnumSet<Scope> issueScope = issue.getImplementation().getScope();
                    if (s == null) {
                        detectorToScope.put(detectorClass, issueScope);
                    } else if (!s.containsAll(issueScope)) {
                        EnumSet<Scope> union = EnumSet.copyOf(s);
                        union.addAll(issueScope);
                        detectorToScope.put(detectorClass, union);
                    }
                }

                if (add) {
                    detectorList.add(detector);
                    EnumSet<Scope> union = detectorToScope.get(detector.getClass());
                    for (Scope s : union) {
                        List<Detector> list = scopeToDetectors.get(s);
                        if (list == null) {
                            list = new ArrayList<>();
                            scopeToDetectors.put(s, list);
                        }
                        list.add(detector);
                    }
                }
            }
        }

        applicableDetectors = detectorList;
        scopeDetectors = scopeToDetectors;
        repeatingDetectors = null;
        repeatScope = null;

        validateScopeList();
    }

    private void computeDetectors(@NonNull Project project) {
        // Ensure that the current visitor is recomputed
        currentFolderType = null;
        currentVisitor = null;

        Configuration configuration = project.getConfiguration(this);
        scopeDetectors = new EnumMap<>(Scope.class);
        applicableDetectors = registry.createDetectors(client, configuration,
                scope, scopeDetectors);

        validateScopeList();
    }

    /** Development diagnostics only, run with assertions on */
    @SuppressWarnings("all") // Turn off warnings for the intentional assertion side effect below
    private void validateScopeList() {
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true; // Intentional side-effect
        if (assertionsEnabled) {
            List<Detector> resourceFileDetectors = scopeDetectors.get(Scope.RESOURCE_FILE);
            if (resourceFileDetectors != null) {
                for (Detector detector : resourceFileDetectors) {
                    // This is wrong; it should allow XmlScanner instead of ResourceXmlScanner!
                    assert detector instanceof ResourceXmlDetector : detector;
                }
            }

            List<Detector> manifestDetectors = scopeDetectors.get(Scope.MANIFEST);
            if (manifestDetectors != null) {
                for (Detector detector : manifestDetectors) {
                    assert detector instanceof Detector.XmlScanner : detector;
                }
            }
            List<Detector> javaCodeDetectors = scopeDetectors.get(Scope.ALL_JAVA_FILES);
            if (javaCodeDetectors != null) {
                for (Detector detector : javaCodeDetectors) {
                    assert detector instanceof JavaScanner ||
                            detector instanceof Detector.UastScanner ||
                            detector instanceof JavaPsiScanner : detector;
                }
            }
            List<Detector> javaFileDetectors = scopeDetectors.get(Scope.JAVA_FILE);
            if (javaFileDetectors != null) {
                for (Detector detector : javaFileDetectors) {
                    assert detector instanceof JavaScanner ||
                            detector instanceof Detector.UastScanner ||
                            detector instanceof JavaPsiScanner : detector;
                }
            }

            List<Detector> classDetectors = scopeDetectors.get(Scope.CLASS_FILE);
            if (classDetectors != null) {
                for (Detector detector : classDetectors) {
                    assert detector instanceof Detector.ClassScanner : detector;
                }
            }

            List<Detector> classCodeDetectors = scopeDetectors.get(Scope.ALL_CLASS_FILES);
            if (classCodeDetectors != null) {
                for (Detector detector : classCodeDetectors) {
                    assert detector instanceof Detector.ClassScanner : detector;
                }
            }

            List<Detector> gradleDetectors = scopeDetectors.get(Scope.GRADLE_FILE);
            if (gradleDetectors != null) {
                for (Detector detector : gradleDetectors) {
                    assert detector instanceof Detector.GradleScanner : detector;
                }
            }

            List<Detector> otherDetectors = scopeDetectors.get(Scope.OTHER);
            if (otherDetectors != null) {
                for (Detector detector : otherDetectors) {
                    assert detector instanceof Detector.OtherFileScanner : detector;
                }
            }

            List<Detector> dirDetectors = scopeDetectors.get(Scope.RESOURCE_FOLDER);
            if (dirDetectors != null) {
                for (Detector detector : dirDetectors) {
                    assert detector instanceof Detector.ResourceFolderScanner : detector;
                }
            }

            List<Detector> binaryDetectors = scopeDetectors.get(Scope.BINARY_RESOURCE_FILE);
            if (binaryDetectors != null) {
                for (Detector detector : binaryDetectors) {
                    assert detector instanceof Detector.BinaryResourceScanner : detector;
                }
            }
        }
    }

    private void registerProjectFile(
            @NonNull Map<File, Project> fileToProject,
            @NonNull File file,
            @NonNull File projectDir,
            @NonNull File rootDir) {
        fileToProject.put(file, client.getProject(projectDir, rootDir));
    }

    private Collection<Project> computeProjects(@NonNull List<File> files) {
        // Compute list of projects
        Map<File, Project> fileToProject = new LinkedHashMap<>();

        File sharedRoot = null;

        // Ensure that we have absolute paths such that if you lint
        //  "foo bar" in "baz" we can show baz/ as the root
        List<File> absolute = new ArrayList<>(files.size());
        for (File file : files) {
            absolute.add(file.getAbsoluteFile());
        }
        // Always use absoluteFiles so that we can check the file's getParentFile()
        // which is null if the file is not absolute.
        files = absolute;

        if (files.size() > 1) {
            sharedRoot = LintUtils.getCommonParent(files);
            if (sharedRoot != null && sharedRoot.getParentFile() == null) { // "/" ?
                sharedRoot = null;
            }
        }

        for (File file : files) {
            if (file.isDirectory()) {
                File rootDir = sharedRoot;
                if (rootDir == null) {
                    rootDir = file;
                    if (files.size() > 1) {
                        rootDir = file.getParentFile();
                        if (rootDir == null) {
                            rootDir = file;
                        }
                    }
                }

                // Figure out what to do with a directory. Note that the meaning of the
                // directory can be ambiguous:
                // If you pass a directory which is unknown, we don't know if we should
                // search upwards (in case you're pointing at a deep java package folder
                // within the project), or if you're pointing at some top level directory
                // containing lots of projects you want to scan. We attempt to do the
                // right thing, which is to see if you're pointing right at a project or
                // right within it (say at the src/ or res/) folder, and if not, you're
                // hopefully pointing at a project tree that you want to scan recursively.
                if (client.isProjectDirectory(file)) {
                    registerProjectFile(fileToProject, file, file, rootDir);
                    continue;
                } else {
                    File parent = file.getParentFile();
                    if (parent != null) {
                        if (client.isProjectDirectory(parent)) {
                            registerProjectFile(fileToProject, file, parent, parent);
                            continue;
                        } else {
                            parent = parent.getParentFile();
                            if (parent != null && client.isProjectDirectory(parent)) {
                                registerProjectFile(fileToProject, file, parent, parent);
                                continue;
                            }
                        }
                    }

                    // Search downwards for nested projects
                    addProjects(file, fileToProject, rootDir);
                }
            } else {
                // Pointed at a file: Search upwards for the containing project
                File parent = file.getParentFile();
                while (parent != null) {
                    if (client.isProjectDirectory(parent)) {
                        registerProjectFile(fileToProject, file, parent, parent);
                        break;
                    }
                    parent = parent.getParentFile();
                }
            }

            if (canceled) {
                return Collections.emptySet();
            }
        }

        for (Map.Entry<File, Project> entry : fileToProject.entrySet()) {
            File file = entry.getKey();
            Project project = entry.getValue();
            if (!file.equals(project.getDir())) {
                if (file.isDirectory()) {
                    try {
                        File dir = file.getCanonicalFile();
                        if (dir.equals(project.getDir())) {
                            continue;
                        }
                    } catch (IOException ioe) {
                        // pass
                    }
                }

                project.addFile(file);
            }
        }

        // Partition the projects up such that we only return projects that aren't
        // included by other projects (e.g. because they are library projects)

        Collection<Project> allProjects = fileToProject.values();
        Set<Project> roots = new HashSet<>(allProjects);
        for (Project project : allProjects) {
            roots.removeAll(project.getAllLibraries());
        }

        // Report issues for all projects that are explicitly referenced. We need to
        // do this here, since the project initialization will mark all library
        // projects as no-report projects by default.
        for (Project project : allProjects) {
            // Report issues for all projects explicitly listed or found via a directory
            // traversal -- including library projects.
            project.setReportIssues(true);
        }

        if (LintUtils.assertionsEnabled()) {
            // Make sure that all the project directories are unique. This ensures
            // that we didn't accidentally end up with different project instances
            // for a library project discovered as a directory as well as one
            // initialized from the library project dependency list
            IdentityHashMap<Project, Project> projects =
                    new IdentityHashMap<>();
            for (Project project : roots) {
                projects.put(project, project);
                for (Project library : project.getAllLibraries()) {
                    projects.put(library, library);
                }
            }
            Set<File> dirs = new HashSet<>();
            for (Project project : projects.keySet()) {
                assert !dirs.contains(project.getDir());
                dirs.add(project.getDir());
            }
        }

        return roots;
    }

    private void addProjects(
            @NonNull File dir,
            @NonNull Map<File, Project> fileToProject,
            @NonNull File rootDir) {
        if (canceled) {
            return;
        }

        if (client.isProjectDirectory(dir)) {
            registerProjectFile(fileToProject, dir, dir, rootDir);
        } else {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        addProjects(file, fileToProject, rootDir);
                    }
                }
            }
        }
    }

    private void checkProject(@NonNull Project project, @NonNull Project main) {
        File projectDir = project.getDir();

        Context projectContext = new Context(this, project, null, projectDir);
        fireEvent(EventType.SCANNING_PROJECT, projectContext);

        List<Project> allLibraries = project.getAllLibraries();
        Set<Project> allProjects = new HashSet<>(allLibraries.size() + 1);
        allProjects.add(project);
        allProjects.addAll(allLibraries);
        currentProjects = allProjects.toArray(new Project[allProjects.size()]);

        currentProject = project;

        for (Detector check : applicableDetectors) {
            check.beforeCheckProject(projectContext);
            if (canceled) {
                return;
            }
        }

        assert currentProject == project;
        runFileDetectors(project, main);

        if (!Scope.checkSingleFile(scope)) {
            List<Project> libraries = project.getAllLibraries();
            for (Project library : libraries) {
                Context libraryContext = new Context(this, library, project, projectDir);
                fireEvent(EventType.SCANNING_LIBRARY_PROJECT, libraryContext);
                currentProject = library;

                for (Detector check : applicableDetectors) {
                    check.beforeCheckLibraryProject(libraryContext);
                    if (canceled) {
                        return;
                    }
                }
                assert currentProject == library;

                runFileDetectors(library, main);
                if (canceled) {
                    return;
                }

                assert currentProject == library;

                for (Detector check : applicableDetectors) {
                    check.afterCheckLibraryProject(libraryContext);
                    if (canceled) {
                        return;
                    }
                }
            }
        }

        currentProject = project;

        for (Detector check : applicableDetectors) {
            check.afterCheckProject(projectContext);
            if (canceled) {
                return;
            }
        }

        if (canceled) {
            client.report(
                projectContext,
                    // Must provide an issue since API guarantees that the issue parameter
                IssueRegistry.CANCELLED,
                Severity.INFORMATIONAL,
                Location.create(project.getDir()),
                "Lint canceled by user", TextFormat.RAW, null);
        }

        currentProjects = null;
    }

    private void runFileDetectors(@NonNull Project project, @Nullable Project main) {
        // Look up manifest information (but not for library projects)
        if (project.isAndroidProject()) {
            for (File manifestFile : project.getManifestFiles()) {
                XmlParser parser = client.getXmlParser();
                XmlContext context = new XmlContext(this, project, main, manifestFile, null,
                        parser);
                context.document = parser.parseXml(context);
                if (context.document != null) {
                    try {
                        project.readManifest(context.document);

                        if ((!project.isLibrary() || (main != null
                                && main.isMergingManifests()))
                                && scope.contains(Scope.MANIFEST)) {
                            List<Detector> detectors = scopeDetectors.get(Scope.MANIFEST);
                            if (detectors != null) {
                                ResourceVisitor v = new ResourceVisitor(parser, detectors,
                                        null);
                                fireEvent(EventType.SCANNING_FILE, context);
                                v.visitFile(context, manifestFile);
                            }
                        }
                    } finally {
                      if (context.document != null) { // else: freed by XmlVisitor above
                          parser.dispose(context, context.document);
                      }
                    }
                }
            }

            // Process both Scope.RESOURCE_FILE and Scope.ALL_RESOURCE_FILES detectors together
            // in a single pass through the resource directories.
            if (scope.contains(Scope.ALL_RESOURCE_FILES)
                    || scope.contains(Scope.RESOURCE_FILE)
                    || scope.contains(Scope.RESOURCE_FOLDER)
                    || scope.contains(Scope.BINARY_RESOURCE_FILE)) {
                List<Detector> dirChecks = scopeDetectors.get(Scope.RESOURCE_FOLDER);
                List<Detector> binaryChecks = scopeDetectors.get(Scope.BINARY_RESOURCE_FILE);
                List<Detector> checks = union(scopeDetectors.get(Scope.RESOURCE_FILE),
                        scopeDetectors.get(Scope.ALL_RESOURCE_FILES));
                boolean haveXmlChecks = checks != null && !checks.isEmpty();
                List<ResourceXmlDetector> xmlDetectors;
                if (haveXmlChecks) {
                    xmlDetectors = new ArrayList<>(checks.size());
                    for (Detector detector : checks) {
                        if (detector instanceof ResourceXmlDetector) {
                            xmlDetectors.add((ResourceXmlDetector) detector);
                        }
                    }
                    haveXmlChecks = !xmlDetectors.isEmpty();
                } else {
                    xmlDetectors = Collections.emptyList();
                }
                if (haveXmlChecks
                        || dirChecks != null && !dirChecks.isEmpty()
                        || binaryChecks != null && !binaryChecks.isEmpty()) {
                    List<File> files = project.getSubset();
                    if (files != null) {
                        checkIndividualResources(project, main, xmlDetectors, dirChecks,
                                binaryChecks, files);
                    } else {
                        List<File> resourceFolders = project.getResourceFolders();
                        if (!resourceFolders.isEmpty()) {
                            for (File res : resourceFolders) {
                                checkResFolder(project, main, res, xmlDetectors, dirChecks,
                                        binaryChecks);
                            }
                        }
                    }
                }
            }

            if (canceled) {
                return;
            }
        }

        if (scope.contains(Scope.JAVA_FILE) || scope.contains(Scope.ALL_JAVA_FILES)) {
            List<Detector> checks = union(scopeDetectors.get(Scope.JAVA_FILE),
                    scopeDetectors.get(Scope.ALL_JAVA_FILES));
            if (checks != null && !checks.isEmpty()) {
                List<File> files = project.getSubset();
                if (files != null) {
                    checkIndividualJavaFiles(project, main, checks, files);
                } else {
                    List<File> sourceFolders = project.getJavaSourceFolders();
                    List<File> testFolders = scope.contains(Scope.TEST_SOURCES)
                            ? project.getTestSourceFolders() : Collections.emptyList();
                    List<File> generatedFolders = checkGeneratedSources
                            ? project.getGeneratedSourceFolders() : Collections.emptyList();
                    checkJava(project, main, sourceFolders, testFolders, generatedFolders, checks);
                }
            }
        }

        if (canceled) {
            return;
        }

        if (scope.contains(Scope.CLASS_FILE)
                || scope.contains(Scope.ALL_CLASS_FILES)
                || scope.contains(Scope.JAVA_LIBRARIES)) {
            checkClasses(project, main);
        }

        if (canceled) {
            return;
        }

        if (scope.contains(Scope.GRADLE_FILE)) {
            checkBuildScripts(project, main);
        }

        if (canceled) {
            return;
        }

        if (scope.contains(Scope.OTHER)) {
            List<Detector> checks = scopeDetectors.get(Scope.OTHER);
            if (checks != null) {
                OtherFileVisitor visitor = new OtherFileVisitor(checks);
                visitor.scan(this, project, main);
            }
        }

        if (canceled) {
            return;
        }

        if (project == main && scope.contains(Scope.PROGUARD_FILE) &&
                project.isAndroidProject()) {
            checkProGuard(project, main);
        }

        if (project == main && scope.contains(Scope.PROPERTY_FILE)) {
            checkProperties(project, main);
        }
    }

    private void checkBuildScripts(Project project, Project main) {
        List<Detector> detectors = scopeDetectors.get(Scope.GRADLE_FILE);
        if (detectors != null) {
            List<File> files = project.getSubset();
            if (files == null) {
                files = project.getGradleBuildScripts();
            }
            for (File file : files) {
                Context context = new Context(this, project, main, file);
                fireEvent(EventType.SCANNING_FILE, context);
                for (Detector detector : detectors) {
                    detector.beforeCheckFile(context);
                    detector.visitBuildScript(context, Maps.newHashMap());
                    detector.afterCheckFile(context);
                }
            }
        }
    }

    private void checkProGuard(Project project, Project main) {
        List<Detector> detectors = scopeDetectors.get(Scope.PROGUARD_FILE);
        if (detectors != null) {
            List<File> files = project.getProguardFiles();
            for (File file : files) {
                Context context = new Context(this, project, main, file);
                fireEvent(EventType.SCANNING_FILE, context);
                for (Detector detector : detectors) {
                    detector.beforeCheckFile(context);
                    detector.run(context);
                    detector.afterCheckFile(context);
                }
            }
        }
    }

    private void checkProperties(Project project, Project main) {
        List<Detector> detectors = scopeDetectors.get(Scope.PROPERTY_FILE);
        if (detectors != null) {
            checkPropertyFile(project, main, detectors, FN_LOCAL_PROPERTIES);
            checkPropertyFile(project, main, detectors, FD_GRADLE_WRAPPER + separator +
                    FN_GRADLE_WRAPPER_PROPERTIES);
        }
    }

    private void checkPropertyFile(Project project, Project main, List<Detector> detectors,
            String relativePath) {
        File file = new File(project.getDir(), relativePath);
        if (file.exists()) {
            Context context = new Context(this, project, main, file);
            fireEvent(EventType.SCANNING_FILE, context);
            for (Detector detector : detectors) {
                detector.beforeCheckFile(context);
                detector.run(context);
                detector.afterCheckFile(context);
            }
        }
    }

    /** True if execution has been canceled */
    boolean isCanceled() {
        return canceled;
    }

    /**
     * Returns the super class for the given class name,
     * which should be in VM format (e.g. java/lang/Integer, not java.lang.Integer).
     * If the super class is not known, returns null. This can happen if
     * the given class is not a known class according to the project or its
     * libraries, for example because it refers to one of the core libraries which
     * are not analyzed by lint.
     *
     * @param name the fully qualified class name
     * @return the corresponding super class name (in VM format), or null if not known
     */
    @Nullable
    public String getSuperClass(@NonNull String name) {
        return client.getSuperClass(currentProject, name);
    }

    /**
     * Returns true if the given class is a subclass of the given super class.
     *
     * @param classNode the class to check whether it is a subclass of the given
     *            super class name
     * @param superClassName the fully qualified super class name (in VM format,
     *            e.g. java/lang/Integer, not java.lang.Integer.
     * @return true if the given class is a subclass of the given super class
     */
    public boolean isSubclassOf(@NonNull ClassNode classNode, @NonNull String superClassName) {
        if (superClassName.equals(classNode.superName)) {
            return true;
        }

        if (currentProject != null) {
            Boolean isSub = client.isSubclassOf(currentProject, classNode.name, superClassName);
            if (isSub != null) {
                return isSub;
            }
        }

        String className = classNode.name;
        while (className != null) {
            if (className.equals(superClassName)) {
                return true;
            }
            className = getSuperClass(className);
        }

        return false;
    }
    @Nullable
    private static List<Detector> union(
            @Nullable List<Detector> list1,
            @Nullable List<Detector> list2) {
        if (list1 == null) {
            return list2;
        } else if (list2 == null) {
            return list1;
        } else {
            // Use set to pick out unique detectors, since it's possible for there to be overlap,
            // e.g. the DuplicateIdDetector registers both a cross-resource issue and a
            // single-file issue, so it shows up on both scope lists:
            Set<Detector> set = new HashSet<>(list1.size() + list2.size());
            set.addAll(list1);
            set.addAll(list2);

            return new ArrayList<>(set);
        }
    }

    /** Check the classes in this project (and if applicable, in any library projects */
    private void checkClasses(Project project, Project main) {
        List<File> files = project.getSubset();
        if (files != null) {
            checkIndividualClassFiles(project, main, files);
            return;
        }

        // We need to read in all the classes up front such that we can initialize
        // the parent chains (such that for example for a virtual dispatch, we can
        // also check the super classes).

        List<File> libraries = project.getJavaLibraries(false);
        List<ClassEntry> libraryEntries = ClassEntry.fromClassPath(client, libraries, true);

        List<File> classFolders = project.getJavaClassFolders();
        List<ClassEntry> classEntries;
        if (classFolders.isEmpty()) {
            String message = String.format("No `.class` files were found in project \"%1$s\", "
                    + "so none of the classfile based checks could be run. "
                    + "Does the project need to be built first?", project.getName());
            Location location = Location.create(project.getDir());
            client.report(new Context(this, project, main, project.getDir()),
                    IssueRegistry.LINT_ERROR,
                    project.getConfiguration(this).getSeverity(IssueRegistry.LINT_ERROR),
                    location, message, TextFormat.RAW, null);
            classEntries = Collections.emptyList();
        } else {
            classEntries = ClassEntry.fromClassPath(client, classFolders, true);
        }

        // Actually run the detectors. Libraries should be called before the
        // main classes.
        runClassDetectors(Scope.JAVA_LIBRARIES, libraryEntries, project, main);

        if (canceled) {
            return;
        }

        runClassDetectors(Scope.CLASS_FILE, classEntries, project, main);
        runClassDetectors(Scope.ALL_CLASS_FILES, classEntries, project, main);
    }

    private void checkIndividualClassFiles(
            @NonNull Project project,
            @Nullable Project main,
            @NonNull List<File> files) {
        List<File> classFiles = Lists.newArrayListWithExpectedSize(files.size());
        List<File> classFolders = project.getJavaClassFolders();
        if (!classFolders.isEmpty()) {
            for (File file : files) {
                String path = file.getPath();
                if (file.isFile() && path.endsWith(DOT_CLASS)) {
                    classFiles.add(file);
                }
            }
        }

        List<ClassEntry> entries = ClassEntry.fromClassFiles(client, classFiles, classFolders,
                true);
        if (!entries.isEmpty()) {
            Collections.sort(entries);
            runClassDetectors(Scope.CLASS_FILE, entries, project, main);
        }
    }

    /**
     * Stack of {@link ClassNode} nodes for outer classes of the currently
     * processed class, including that class itself. Populated by
     * {@link #runClassDetectors(Scope, List, Project, Project)} and used by
     * {@link #getOuterClassNode(ClassNode)}
     */
    private Deque<ClassNode> mOuterClasses;

    private void runClassDetectors(Scope scope, List<ClassEntry> entries,
            Project project, Project main) {
        if (this.scope.contains(scope)) {
            List<Detector> classDetectors = scopeDetectors.get(scope);
            if (classDetectors != null && !classDetectors.isEmpty() && !entries.isEmpty()) {
                AsmVisitor visitor = new AsmVisitor(client, classDetectors);

                CharSequence sourceContents = null;
                String sourceName = "";
                mOuterClasses = new ArrayDeque<>();
                ClassEntry prev = null;
                for (ClassEntry entry : entries) {
                    if (prev != null && prev.compareTo(entry) == 0) {
                        // Duplicate entries for some reason: ignore
                        continue;
                    }
                    prev = entry;

                    ClassReader reader;
                    ClassNode classNode;
                    try {
                        reader = new ClassReader(entry.bytes);
                        classNode = new ClassNode();
                        reader.accept(classNode, 0 /* flags */);
                    } catch (Throwable t) {
                        client.log(null, "Error processing %1$s: broken class file?",
                                entry.path());
                        continue;
                    }

                    ClassNode peek;
                    while ((peek = mOuterClasses.peek()) != null) {
                        if (classNode.name.startsWith(peek.name)) {
                            break;
                        } else {
                            mOuterClasses.pop();
                        }
                    }
                    mOuterClasses.push(classNode);

                    if (isSuppressed(null, classNode)) {
                        // Class was annotated with suppress all -- no need to look any further
                        continue;
                    }

                    if (sourceContents != null) {
                        // Attempt to reuse the source buffer if initialized
                        // This means making sure that the source files
                        //    foo/bar/MyClass and foo/bar/MyClass$Bar
                        //    and foo/bar/MyClass$3 and foo/bar/MyClass$3$1 have the same prefix.
                        String newName = classNode.name;
                        int newRootLength = newName.indexOf('$');
                        if (newRootLength == -1) {
                            newRootLength = newName.length();
                        }
                        int oldRootLength = sourceName.indexOf('$');
                        if (oldRootLength == -1) {
                            oldRootLength = sourceName.length();
                        }
                        if (newRootLength != oldRootLength ||
                                !sourceName.regionMatches(0, newName, 0, newRootLength)) {
                            sourceContents = null;
                        }
                    }

                    ClassContext context = new ClassContext(this, project, main,
                            entry.file, entry.jarFile, entry.binDir, entry.bytes,
                            classNode, scope == Scope.JAVA_LIBRARIES /*fromLibrary*/,
                            sourceContents);

                    try {
                        visitor.runClassDetectors(context);
                    } catch (Exception e) {
                        client.log(e, null);
                    }

                    if (canceled) {
                        return;
                    }

                    sourceContents = context.getSourceContents(false/*read*/);
                    sourceName = classNode.name;
                }

                mOuterClasses = null;
            }
        }
    }

    /** Returns the outer class node of the given class node
     * @param classNode the inner class node
     * @return the outer class node */
    public ClassNode getOuterClassNode(@NonNull ClassNode classNode) {
        String outerName = classNode.outerClass;

        Iterator<ClassNode> iterator = mOuterClasses.iterator();
        while (iterator.hasNext()) {
            ClassNode node = iterator.next();
            if (outerName != null) {
                if (node.name.equals(outerName)) {
                    return node;
                }
            } else if (node == classNode) {
                return iterator.hasNext() ? iterator.next() : null;
            }
        }

        return null;
    }

    /**
     * Returns the {@link ClassNode} corresponding to the given type, if possible, or null
     *
     * @param type the fully qualified type, using JVM signatures (/ and $, not . as path
     *             separators)
     * @param flags the ASM flags to pass to the {@link ClassReader}, normally 0 but can
     *              for example be {@link ClassReader#SKIP_CODE} and/oor
     *              {@link ClassReader#SKIP_DEBUG}
     * @return the class node for the type, or null
     */
    @Nullable
    public ClassNode findClass(@NonNull ClassContext context, @NonNull String type, int flags) {
        String relative = type.replace('/', File.separatorChar) + DOT_CLASS;
        File classFile = findClassFile(context.getProject(), relative);
        if (classFile != null) {
            if (classFile.getPath().endsWith(DOT_JAR)) {
                // TODO: Handle .jar files
                return null;
            }

            try {
                byte[] bytes = client.readBytes(classFile);
                ClassReader reader = new ClassReader(bytes);
                ClassNode classNode = new ClassNode();
                reader.accept(classNode, flags);

                return classNode;
            } catch (Throwable t) {
                client.log(null, "Error processing %1$s: broken class file?",
                        classFile.getPath());
            }
        }

        return null;
    }

    @Nullable
    private File findClassFile(@NonNull Project project, String relativePath) {
        for (File root : client.getJavaClassFolders(project)) {
            File path = new File(root, relativePath);
            if (path.exists()) {
                return path;
            }
        }
        // Search in the libraries
        for (File root : client.getJavaLibraries(project, true)) {
            // TODO: Handle .jar files!
            //if (root.getPath().endsWith(DOT_JAR)) {
            //}

            File path = new File(root, relativePath);
            if (path.exists()) {
                return path;
            }
        }

        // Search dependent projects
        for (Project library : project.getDirectLibraries()) {
            File path = findClassFile(library, relativePath);
            if (path != null) {
                return path;
            }
        }

        return null;
    }

    private void checkJava(
            @NonNull Project project,
            @Nullable Project main,
            @NonNull List<File> sourceFolders,
            @NonNull List<File> testSourceFolders,
            @NonNull List<File> generatedSources,
            @NonNull List<Detector> checks) {
        assert !checks.isEmpty();

        // Gather all Java source files in a single pass; more efficient.
        List<File> sources = new ArrayList<>(100);
        for (File folder : sourceFolders) {
            gatherJavaFiles(folder, sources);
        }
        for (File folder : generatedSources) {
            gatherJavaFiles(folder, sources);
        }

        List<JavaContext> contexts = Lists.newArrayListWithExpectedSize(2 * sources.size());
        for (File file : sources) {
            JavaContext context = new JavaContext(this, project, main, file);
            contexts.add(context);
        }

        // Test sources
        sources.clear();
        for (File folder : testSourceFolders) {
            gatherJavaFiles(folder, sources);
        }
        List<JavaContext> testContexts = Lists.newArrayListWithExpectedSize(sources.size());
        for (File file : sources) {
            JavaContext context = new JavaContext(this, project, main, file);
            context.setTestSource(true);
            testContexts.add(context);
        }

        // Visit all contexts
        if (!contexts.isEmpty() || !testContexts.isEmpty()) {
            visitJavaFiles(checks, project, contexts, testContexts);
        }
    }

    private void visitJavaFiles(@NonNull List<Detector> checks, @NonNull Project project,
            @NonNull List<JavaContext> contexts, @NonNull List<JavaContext> testContexts) {
        // Temporary: we still have some builtin checks that aren't migrated to
        // PSI. Until that's complete, remove them from the list here
        //List<Detector> scanners = checks;
        List<Detector> scanners = Lists.newArrayListWithCapacity(checks.size());
        List<Detector> uastScanners = Lists.newArrayListWithCapacity(checks.size());
        for (Detector detector : checks) {
            if (detector instanceof JavaPsiScanner) {
                scanners.add(detector);
            } else if (detector instanceof Detector.UastScanner) {
                uastScanners.add(detector);
            }
        }

        List<JavaContext> allContexts;
        if (testContexts.isEmpty()) {
            allContexts = contexts;
        } else {
            allContexts = Lists.newArrayListWithExpectedSize(
                    contexts.size() + testContexts.size());
            allContexts.addAll(contexts);
            allContexts.addAll(testContexts);
        }

        // Force all test sources into the normal source check (where all checks apply) ?
        if (checkTestSources) {
            contexts = allContexts;
            testContexts = Collections.emptyList();
        }

        if (!uastScanners.isEmpty()) {
            UastParser parser = client.getUastParser(project);
            if (parser == null) {
                client.log(null, "No java parser provided to lint: not running Java checks");
                return;
            }

            UastParser uastParser = client.getUastParser(currentProject);
            for (JavaContext context : allContexts) {
                context.setUastParser(uastParser);
            }
            final UElementVisitor uElementVisitor = new UElementVisitor(parser, uastScanners);

            parserErrors = !uElementVisitor.prepare(contexts);

            for (final JavaContext context : contexts) {
                fireEvent(EventType.SCANNING_FILE, context);
                // TODO: Don't hold read lock around the entire process?
                client.runReadAction(() -> uElementVisitor.visitFile(context));
                if (canceled) {
                    return;
                }
            }

            uElementVisitor.dispose();

            if (!testContexts.isEmpty()) {
                List<Detector> testScanners = filterTestScanners(uastScanners);
                if (!testScanners.isEmpty()) {
                    UElementVisitor uTestVisitor = new UElementVisitor(parser, testScanners);

                    for (JavaContext context : testContexts) {
                        fireEvent(EventType.SCANNING_FILE, context);
                        // TODO: Don't hold read lock around the entire process?
                        client.runReadAction(() -> uTestVisitor.visitFile(context));
                        if (canceled) {
                            return;
                        }
                    }

                    uTestVisitor.dispose();
                }
            }
        }

        if (runPsiCompatChecks) {
            JavaParser parser = client.getJavaParser(project);
            if (parser == null) {
                client.log(null, "No java parser provided to lint: not running Java checks");
                return;
            }

            for (JavaContext context : allContexts) {
                context.setParser(parser);
            }

            JavaPsiVisitor visitor = new JavaPsiVisitor(parser, scanners);
            if (runLombokCompatChecks) {
                visitor.setDisposeUnitsAfterUse(false);
            }

            parserErrors = !visitor.prepare(allContexts);

            for (JavaContext context : contexts) {
                fireEvent(EventType.SCANNING_FILE, context);
                visitor.visitFile(context);
                if (canceled) {
                    return;
                }
            }

            // Run tests separately: most checks aren't going to apply for tests
            JavaPsiVisitor testVisitor = null;
            if (!testContexts.isEmpty()) {
                List<Detector> testScanners = filterTestScanners(scanners);
                if (!testScanners.isEmpty()) {
                    testVisitor = new JavaPsiVisitor(parser, testScanners);
                    if (runLombokCompatChecks) {
                        testVisitor.setDisposeUnitsAfterUse(false);
                    }

                    for (JavaContext context : testContexts) {
                        fireEvent(EventType.SCANNING_FILE, context);
                        testVisitor.visitFile(context);
                        if (canceled) {
                            return;
                        }
                    }
                }
            }

            // Only if the user is using some custom lint rules that haven't been updated
            // yet
            //noinspection ConstantConditions
            if (runLombokCompatChecks) {
                try {
                    // Call EcjParser#disposePsi (if running from Gradle) to clear up PSI
                    // caches that are full from the above JavaPsiVisitor call. We do this
                    // instead of calling visitor.dispose because we want to *keep* the
                    // ECJ parse around for use by the Lombok bridge.
                    parser.getClass().getMethod("disposePsi").invoke(parser);
                } catch (Throwable ignore) {
                }

                // Filter the checks to only those that implement JavaScanner
                List<Detector> filtered = Lists.newArrayListWithCapacity(checks.size());
                for (Detector detector : checks) {
                    if (detector instanceof JavaScanner) {
                        assert !(detector instanceof JavaPsiScanner); // Shouldn't be both
                        filtered.add(detector);
                    }
                }

                if (!filtered.isEmpty()) {
                    /* Let's not complain quite yet
                    List<String> detectorNames = Lists.newArrayListWithCapacity(filtered.size());
                    for (Detector detector : filtered) {
                        detectorNames.add(detector.getClass().getName());
                    }
                    Collections.sort(detectorNames);

                    String message = String.format("Lint found one or more custom checks using its "
                            + "older Java API; these checks are still run in compatibility mode, "
                            + "but this causes duplicated parsing, and in the next version lint "
                            + "will no longer include this legacy mode. Make sure the following "
                            + "lint detectors are upgraded to the new API: %1$s",
                            Joiner.on(", ").join(detectorNames));
                    JavaContext first = contexts.get(0);
                    Project project = first.getProject();
                    Location location = Location.create(project.getDir());
                    client.report(first,
                            IssueRegistry.LINT_ERROR,
                            project.getConfiguration(this).getSeverity(IssueRegistry.LINT_ERROR),
                            location, message, TextFormat.RAW);
                    */

                    JavaVisitor oldVisitor = new JavaVisitor(parser, filtered);

                    // NOTE: We do NOT call oldVisitor.prepare and dispose here since this
                    // visitor is wrapping the same java parser as the one we used for PSI,
                    // so calling prepare again would wipe out the results we're trying to reuse.
                    for (JavaContext context : contexts) {
                        fireEvent(EventType.SCANNING_FILE, context);
                        oldVisitor.visitFile(context);
                        if (canceled) {
                            return;
                        }
                    }

                    if (!testContexts.isEmpty()) {
                        List<Detector> testScanners = filterTestScanners(filtered);
                        if (!testScanners.isEmpty()) {
                            JavaVisitor oldTestVisitor = new JavaVisitor(parser, testScanners);
                            for (JavaContext context : testContexts) {
                                fireEvent(EventType.SCANNING_FILE, context);
                                oldTestVisitor.visitFile(context);
                                if (canceled) {
                                    return;
                                }
                            }
                        }
                    }
                }
            }

            visitor.dispose();
            if (testVisitor != null) {
                testVisitor.dispose();
            }
            for (JavaContext context : allContexts) {
                context.setParser(null);
            }
        } else {
            assert !runLombokCompatChecks; // the lombok compat support requires the psi compat checks too
        }
    }

    @NonNull
    private List<Detector> filterTestScanners(@NonNull List<Detector> scanners) {
        List<Detector> testScanners = Lists.newArrayListWithExpectedSize(scanners.size());
        // Compute intersection of Java and test scanners
        Collection<Detector> sourceScanners = scopeDetectors.get(Scope.TEST_SOURCES);
        if (sourceScanners == null) {
            return Collections.emptyList();
        }
        if (sourceScanners.size() > 15 && scanners.size() > 15) {
            sourceScanners = Sets.newHashSet(sourceScanners);
        }
        for (Detector check : scanners) {
            if (sourceScanners.contains(check)) {
                testScanners.add(check);
            }
        }
        return testScanners;
    }

    private void checkIndividualJavaFiles(
            @NonNull Project project,
            @Nullable Project main,
            @NonNull List<Detector> checks,
            @NonNull List<File> files) {

        List<JavaContext> contexts = Lists.newArrayListWithExpectedSize(files.size());
        List<File> testFolders = project.getTestSourceFolders();
        for (File file : files) {
            if (file.isFile()) {
                String path = file.getPath();
                if (path.endsWith(DOT_JAVA) || path.endsWith(DOT_KT)) {
                    JavaContext context = new JavaContext(this, project, main, file);

                    // Figure out if this file is a test context
                    for (File testFolder : testFolders) {
                        if (FileUtil.isAncestor(testFolder, file, false)) {
                            context.setTestSource(true);
                            break;
                        }
                    }

                    contexts.add(context);
                }
            }
        }

        if (contexts.isEmpty()) {
            return;
        }

        // We're not sure if these individual files are tests or non-tests; treat them
        // as non-tests now. This gives you warnings if you're editing an individual
        // test file for example.

        visitJavaFiles(checks, project, contexts, Collections.emptyList());
    }

    private static void gatherJavaFiles(@NonNull File dir, @NonNull List<File> result) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    String path = file.getPath();
                    if (path.endsWith(DOT_JAVA) || path.endsWith(DOT_KT)) {
                        result.add(file);
                    }
                } else if (file.isDirectory()) {
                    gatherJavaFiles(file, result);
                }
            }
        }
    }

    private ResourceFolderType currentFolderType;
    private List<ResourceXmlDetector> currentXmlDetectors;
    private List<Detector> currentBinaryDetectors;
    private ResourceVisitor currentVisitor;

    @Nullable
    private ResourceVisitor getVisitor(
            @NonNull ResourceFolderType type,
            @NonNull List<ResourceXmlDetector> checks,
            @Nullable List<Detector> binaryChecks) {
        if (type != currentFolderType) {
            currentFolderType = type;

            // Determine which XML resource detectors apply to the given folder type
            List<ResourceXmlDetector> applicableXmlChecks =
                    new ArrayList<>(checks.size());
            for (ResourceXmlDetector check : checks) {
                if (check.appliesTo(type)) {
                    applicableXmlChecks.add(check);
                }
            }
            List<Detector> applicableBinaryChecks = null;
            if (binaryChecks != null) {
                applicableBinaryChecks = new ArrayList<>(binaryChecks.size());
                for (Detector check : binaryChecks) {
                    if (check.appliesTo(type)) {
                        applicableBinaryChecks.add(check);
                    }
                }
            }

            // If the list of detectors hasn't changed, then just use the current visitor!
            if (currentXmlDetectors != null && currentXmlDetectors.equals(applicableXmlChecks)
                    && Objects.equal(currentBinaryDetectors, applicableBinaryChecks)) {
                return currentVisitor;
            }

            currentXmlDetectors = applicableXmlChecks;
            currentBinaryDetectors = applicableBinaryChecks;

            if (applicableXmlChecks.isEmpty()
                    && (applicableBinaryChecks == null || applicableBinaryChecks.isEmpty())) {
                currentVisitor = null;
                return null;
            }

            XmlParser parser = client.getXmlParser();
            currentVisitor = new ResourceVisitor(parser, applicableXmlChecks,
                    applicableBinaryChecks);
        }

        return currentVisitor;
    }

    private void checkResFolder(
            @NonNull Project project,
            @Nullable Project main,
            @NonNull File res,
            @NonNull List<ResourceXmlDetector> xmlChecks,
            @Nullable List<Detector> dirChecks,
            @Nullable List<Detector> binaryChecks) {
        File[] resourceDirs = res.listFiles();
        if (resourceDirs == null) {
            return;
        }

        // Sort alphabetically such that we can process related folder types at the
        // same time, and to have a defined behavior such that detectors can rely on
        // predictable ordering, e.g. layouts are seen before menus are seen before
        // values, etc (l < m < v).

        Arrays.sort(resourceDirs);
        for (File dir : resourceDirs) {
            ResourceFolderType type = ResourceFolderType.getFolderType(dir.getName());
            if (type != null) {
                checkResourceFolder(project, main, dir, type, xmlChecks, dirChecks, binaryChecks);
            }

            if (canceled) {
                return;
            }
        }
    }

    private void checkResourceFolder(
            @NonNull Project project,
            @Nullable Project main,
            @NonNull File dir,
            @NonNull ResourceFolderType type,
            @NonNull List<ResourceXmlDetector> xmlChecks,
            @Nullable List<Detector> dirChecks,
            @Nullable List<Detector> binaryChecks) {

        // Process the resource folder

        if (dirChecks != null && !dirChecks.isEmpty()) {
            ResourceContext context = new ResourceContext(this, project, main, dir, type);
            String folderName = dir.getName();
            fireEvent(EventType.SCANNING_FILE, context);
            for (Detector check : dirChecks) {
                if (check.appliesTo(type)) {
                    check.beforeCheckFile(context);
                    check.checkFolder(context, folderName);
                    check.afterCheckFile(context);
                }
            }
            if (binaryChecks == null && xmlChecks.isEmpty()) {
                return;
            }
        }

        File[] files = dir.listFiles();
        if (files == null || files.length <= 0) {
            return;
        }

        ResourceVisitor visitor = getVisitor(type, xmlChecks, binaryChecks);
        if (visitor != null) { // if not, there are no applicable rules in this folder
            // Process files in alphabetical order, to ensure stable output
            // (for example for the duplicate resource detector)
            Arrays.sort(files);
            for (File file : files) {
                if (LintUtils.isXmlFile(file)) {
                    XmlContext context = new XmlContext(this, project, main, file, type,
                            visitor.getParser());
                    fireEvent(EventType.SCANNING_FILE, context);
                    visitor.visitFile(context, file);
                } else if (binaryChecks != null && (LintUtils.isBitmapFile(file) ||
                            type == ResourceFolderType.RAW)) {
                    ResourceContext context = new ResourceContext(this, project, main, file,
                            type);
                    fireEvent(EventType.SCANNING_FILE, context);
                    visitor.visitBinaryResource(context);
                }
                if (canceled) {
                    return;
                }
            }
        }
    }

    /** Checks individual resources */
    private void checkIndividualResources(
            @NonNull Project project,
            @Nullable Project main,
            @NonNull List<ResourceXmlDetector> xmlDetectors,
            @Nullable List<Detector> dirChecks,
            @Nullable List<Detector> binaryChecks,
            @NonNull List<File> files) {
        for (File file : files) {
            if (file.isDirectory()) {
                // Is it a resource folder?
                ResourceFolderType type = ResourceFolderType.getFolderType(file.getName());
                if (type != null && new File(file.getParentFile(), RES_FOLDER).exists()) {
                    // Yes.
                    checkResourceFolder(project, main, file, type, xmlDetectors, dirChecks,
                            binaryChecks);
                } else if (file.getName().equals(RES_FOLDER)) { // Is it the res folder?
                    // Yes
                    checkResFolder(project, main, file, xmlDetectors, dirChecks, binaryChecks);
                } else {
                    client.log(null, "Unexpected folder %1$s; should be project, " +
                            "\"res\" folder or resource folder", file.getPath());
                }
            } else if (file.isFile() && LintUtils.isXmlFile(file)) {
                // Yes, find out its resource type
                String folderName = file.getParentFile().getName();
                ResourceFolderType type = ResourceFolderType.getFolderType(folderName);
                if (type != null) {
                    ResourceVisitor visitor = getVisitor(type, xmlDetectors, binaryChecks);
                    if (visitor != null) {
                        XmlContext context = new XmlContext(this, project, main, file, type,
                                visitor.getParser());
                        fireEvent(EventType.SCANNING_FILE, context);
                        visitor.visitFile(context, file);
                    }
                }
            } else if (binaryChecks != null && file.isFile() && LintUtils.isBitmapFile(file)) {
                // Yes, find out its resource type
                String folderName = file.getParentFile().getName();
                ResourceFolderType type = ResourceFolderType.getFolderType(folderName);
                if (type != null) {
                    ResourceVisitor visitor = getVisitor(type, xmlDetectors, binaryChecks);
                    if (visitor != null) {
                        ResourceContext context = new ResourceContext(this, project, main,
                                file, type);
                        fireEvent(EventType.SCANNING_FILE, context);
                        visitor.visitBinaryResource(context);
                        if (canceled) {
                            return;
                        }
                    }
                }
            }
        }
    }

    /**
     * Adds a listener to be notified of lint progress
     *
     * @param listener the listener to be added
     */
    public void addLintListener(@NonNull LintListener listener) {
        if (listeners == null) {
            listeners = new ArrayList<>(1);
        }
        listeners.add(listener);
    }

    /**
     * Removes a listener such that it is no longer notified of progress
     *
     * @param listener the listener to be removed
     */
    public void removeLintListener(@NonNull LintListener listener) {
        listeners.remove(listener);
        if (listeners.isEmpty()) {
            listeners = null;
        }
    }

    /** Notifies listeners, if any, that the given event has occurred */
    private void fireEvent(@NonNull LintListener.EventType type, @Nullable Context context) {
        if (listeners != null) {
            for (LintListener listener : listeners) {
                listener.update(this, type, context);
            }
        }
    }

    /**
     * Wrapper around the lint client. This sits in the middle between a
     * detector calling for example {@link LintClient#report} and
     * the actual embedding tool, and performs filtering etc such that detectors
     * and lint clients don't have to make sure they check for ignored issues or
     * filtered out warnings.
     */
    private class LintClientWrapper extends LintClient {
        @NonNull
        private final LintClient mDelegate;

        public LintClientWrapper(@NonNull LintClient delegate) {
            super(getClientName());
            mDelegate = delegate;
        }

        @Nullable
        @Override
        public Document getMergedManifest(@NonNull Project project) {
            return mDelegate.getMergedManifest(project);
        }

        @Override
        public void resolveMergeManifestSources(@NonNull Document mergedManifest,
                @NonNull Object reportFile) {
            mDelegate.resolveMergeManifestSources(mergedManifest, reportFile);
        }

        @Override
        public boolean isMergeManifestNode(@NonNull org.w3c.dom.Node node) {
            return mDelegate.isMergeManifestNode(node);
        }

        @Nullable
        @Override
        public Pair<File,org.w3c.dom.Node> findManifestSourceNode(
                @NonNull org.w3c.dom.Node mergedNode) {
            return mDelegate.findManifestSourceNode(mergedNode);
        }

        @Nullable
        @Override
        public Location findManifestSourceLocation(@NonNull org.w3c.dom.Node mergedNode) {
            return mDelegate.findManifestSourceLocation(mergedNode);
        }

        @Deprecated
        public void report(@NonNull Context context, @NonNull Issue issue,
                @NonNull Severity severity,
                @NonNull Location location, @NonNull String message, @NonNull TextFormat format) {
            report(context, issue, severity, location, message, format, null);
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
            //noinspection ConstantConditions
            if (location == null) {
                // Misbehaving third-party lint detectors
                assert false : issue;
                return;
            }

            assert currentProject != null;
            if (!currentProject.getReportIssues()) {
                return;
            }

            Configuration configuration = context.getConfiguration();
            if (!configuration.isEnabled(issue)) {
                if (issue != IssueRegistry.PARSER_ERROR && issue != IssueRegistry.LINT_ERROR &&
                        issue != IssueRegistry.BASELINE) {
                    mDelegate.log(null, "Incorrect detector reported disabled issue %1$s",
                            issue.toString());
                }
                return;
            }

            if (configuration.isIgnored(context, issue, location, message)) {
                return;
            }

            if (severity == Severity.IGNORE) {
                return;
            }

            if (baseline != null) {
                boolean filtered = baseline.findAndMark(issue, location, message, severity,
                        context.getProject());
                if (filtered) {
                    return;
                }
            }

            mDelegate.report(context, issue, severity, location, message, format, fix);
        }

        // Everything else just delegates to the embedding lint client

        @Override
        @NonNull
        public Configuration getConfiguration(@NonNull Project project,
          @Nullable LintDriver driver) {
            return mDelegate.getConfiguration(project, driver);
        }

        @NonNull
        @Override
        public String getDisplayPath(File file) {
            return mDelegate.getDisplayPath(file);
        }

        @Override
        public void log(@NonNull Severity severity, @Nullable Throwable exception,
                @Nullable String format, @Nullable Object... args) {
            mDelegate.log(exception, format, args);
        }

        @NonNull
        @Override
        public List<File> getTestLibraries(@NonNull Project project) {
            return mDelegate.getTestLibraries(project);
        }

        @Nullable
        @Override
        public String getClientRevision() {
            return mDelegate.getClientRevision();
        }

        @Override
        public void runReadAction(@NonNull Runnable runnable) {
            mDelegate.runReadAction(runnable);
        }

        @Override
        @NonNull
        public CharSequence readFile(@NonNull File file) {
            return mDelegate.readFile(file);
        }

        @Override
        @NonNull
        public byte[] readBytes(@NonNull File file) throws IOException {
            return mDelegate.readBytes(file);
        }

        @Override
        @NonNull
        public List<File> getJavaSourceFolders(@NonNull Project project) {
            return mDelegate.getJavaSourceFolders(project);
        }

        @NonNull
        @Override
        public List<File> getGeneratedSourceFolders(@NonNull Project project) {
            return mDelegate.getGeneratedSourceFolders(project);
        }

        @Override
        @NonNull
        public List<File> getJavaClassFolders(@NonNull Project project) {
            return mDelegate.getJavaClassFolders(project);
        }

        @NonNull
        @Override
        public List<File> getJavaLibraries(@NonNull Project project, boolean includeProvided) {
            return mDelegate.getJavaLibraries(project, includeProvided);
        }

        @NonNull
        @Override
        public List<File> getTestSourceFolders(@NonNull Project project) {
            return mDelegate.getTestSourceFolders(project);
        }

        @Override
        public Collection<Project> getKnownProjects() {
            return mDelegate.getKnownProjects();
        }

        @Nullable
        @Override
        public BuildToolInfo getBuildTools(@NonNull Project project) {
            return mDelegate.getBuildTools(project);
        }

        @NonNull
        @Override
        public Map<String, String> createSuperClassMap(@NonNull Project project) {
            return mDelegate.createSuperClassMap(project);
        }

        @NonNull
        @Override
        public ResourceVisibilityLookup.Provider getResourceVisibilityProvider() {
            return mDelegate.getResourceVisibilityProvider();
        }

        @Override
        @NonNull
        public List<File> getResourceFolders(@NonNull Project project) {
            return mDelegate.getResourceFolders(project);
        }

        @Override
        @NonNull
        public XmlParser getXmlParser() {
            return mDelegate.getXmlParser();
        }

        @Override
        @NonNull
        public Class<? extends Detector> replaceDetector(
                @NonNull Class<? extends Detector> detectorClass) {
            return mDelegate.replaceDetector(detectorClass);
        }

        @Override
        @NonNull
        public SdkInfo getSdkInfo(@NonNull Project project) {
            return mDelegate.getSdkInfo(project);
        }

        @Override
        @NonNull
        public Project getProject(@NonNull File dir, @NonNull File referenceDir) {
            return mDelegate.getProject(dir, referenceDir);
        }

        @Nullable
        @Override
        public JavaParser getJavaParser(@Nullable Project project) {
            return mDelegate.getJavaParser(project);
        }

        @Nullable
        @Override
        public UastParser getUastParser(@Nullable Project project) {
            return mDelegate.getUastParser(project);
        }

        @Override
        public File findResource(@NonNull String relativePath) {
            return mDelegate.findResource(relativePath);
        }

        @SuppressWarnings("deprecation") // forwarding required API
        @Override
        @Nullable
        public File getCacheDir(boolean create) {
            return mDelegate.getCacheDir(create);
        }

        @Nullable
        @Override
        public File getCacheDir(@Nullable String name, boolean create) {
            return mDelegate.getCacheDir(name, create);
        }

        @Override
        @NonNull
        protected ClassPathInfo getClassPath(@NonNull Project project) {
            return mDelegate.getClassPath(project);
        }

        @Override
        public void log(@Nullable Throwable exception, @Nullable String format,
                @Nullable Object... args) {
            mDelegate.log(exception, format, args);
        }

        @Override
        protected void initializeProjects(@NonNull Collection<Project> knownProjects) {
            mDelegate.initializeProjects(knownProjects);
        }

        @Override
        protected void disposeProjects(@NonNull Collection<Project> knownProjects) {
            mDelegate.disposeProjects(knownProjects);
        }

        @Override
        @Nullable
        public File getSdkHome() {
            return mDelegate.getSdkHome();
        }

        @Override
        @NonNull
        public IAndroidTarget[] getTargets() {
            return mDelegate.getTargets();
        }

        @Nullable
        @Override
        public AndroidSdkHandler getSdk() {
            return mDelegate.getSdk();
        }

        @Nullable
        @Override
        public IAndroidTarget getCompileTarget(@NonNull Project project) {
            return mDelegate.getCompileTarget(project);
        }

        @Override
        public int getHighestKnownApiLevel() {
            return mDelegate.getHighestKnownApiLevel();
        }

        @Override
        @Nullable
        public String getSuperClass(@NonNull Project project, @NonNull String name) {
            return mDelegate.getSuperClass(project, name);
        }

        @Override
        @Nullable
        public Boolean isSubclassOf(@NonNull Project project, @NonNull String name,
                @NonNull String superClassName) {
            return mDelegate.isSubclassOf(project, name, superClassName);
        }

        @Override
        @NonNull
        public String getProjectName(@NonNull Project project) {
            return mDelegate.getProjectName(project);
        }

        @Override
        public boolean isGradleProject(Project project) {
            return mDelegate.isGradleProject(project);
        }

        @NonNull
        @Override
        protected Project createProject(@NonNull File dir, @NonNull File referenceDir) {
            return mDelegate.createProject(dir, referenceDir);
        }

        @NonNull
        @Override
        public List<File> findGlobalRuleJars() {
            return mDelegate.findGlobalRuleJars();
        }

        @NonNull
        @Override
        public List<File> findRuleJars(@NonNull Project project) {
            return mDelegate.findRuleJars(project);
        }

        @Override
        public boolean isProjectDirectory(@NonNull File dir) {
            return mDelegate.isProjectDirectory(dir);
        }

        @Override
        public void registerProject(@NonNull File dir, @NonNull Project project) {
            log(Severity.WARNING, null, "Too late to register projects");
            mDelegate.registerProject(dir, project);
        }

        @Override
        public IssueRegistry addCustomLintRules(@NonNull IssueRegistry registry) {
            return mDelegate.addCustomLintRules(registry);
        }

        @NonNull
        @Override
        public List<File> getAssetFolders(@NonNull Project project) {
            return mDelegate.getAssetFolders(project);
        }

        @Override
        public ClassLoader createUrlClassLoader(@NonNull URL[] urls, @NonNull ClassLoader parent) {
            return mDelegate.createUrlClassLoader(urls, parent);
        }

        @Override
        public boolean checkForSuppressComments() {
            return mDelegate.checkForSuppressComments();
        }

        @Override
        public boolean supportsProjectResources() {
            return mDelegate.supportsProjectResources();
        }

        @SuppressWarnings("deprecation") // forwarding required API
        @Nullable
        @Override
        public AbstractResourceRepository getProjectResources(Project project,
                boolean includeDependencies) {
            return mDelegate.getProjectResources(project, includeDependencies);
        }

        @Nullable
        @Override
        public AbstractResourceRepository getResourceRepository(Project project,
                boolean includeModuleDependencies, boolean includeLibraries) {
            return mDelegate.getResourceRepository(project, includeModuleDependencies,
                    includeLibraries);
        }

        @NonNull
        @Override
        public ProgressIndicator getRepositoryLogger() {
            return mDelegate.getRepositoryLogger();
        }

        @NonNull
        @Override
        public Location.Handle createResourceItemHandle(@NonNull ResourceItem item) {
            return mDelegate.createResourceItemHandle(item);
        }

        @Nullable
        @Override
        public URLConnection openConnection(@NonNull URL url) throws IOException {
            return mDelegate.openConnection(url);
        }

        @Nullable
        @Override
        public URLConnection openConnection(@NonNull URL url, int timeout) throws IOException {
            return mDelegate.openConnection(url, timeout);
        }

        @Override
        public void closeConnection(@NonNull URLConnection connection) throws IOException {
            mDelegate.closeConnection(connection);
        }
    }

    /**
     * Requests another pass through the data for the given detector. This is
     * typically done when a detector needs to do more expensive computation,
     * but it only wants to do this once it <b>knows</b> that an error is
     * present, or once it knows more specifically what to check for.
     *
     * @param detector the detector that should be included in the next pass.
     *            Note that the lint runner may refuse to run more than a couple
     *            of runs.
     * @param scope the scope to be revisited. This must be a subset of the
     *       current scope ({@link #getScope()}, and it is just a performance hint;
     *       in particular, the detector should be prepared to be called on other
     *       scopes as well (since they may have been requested by other detectors).
     *       You can pall null to indicate "all".
     */
    public void requestRepeat(@NonNull Detector detector, @Nullable EnumSet<Scope> scope) {
        if (repeatingDetectors == null) {
            repeatingDetectors = new ArrayList<>();
        }
        repeatingDetectors.add(detector);

        if (scope != null) {
            if (repeatScope == null) {
                repeatScope = scope;
            } else {
                repeatScope = EnumSet.copyOf(repeatScope);
                repeatScope.addAll(scope);
            }
        } else {
            repeatScope = Scope.ALL;
        }
    }

    // Unfortunately, ASMs nodes do not extend a common DOM node type with parent
    // pointers, so we have to have multiple methods which pass in each type
    // of node (class, method, field) to be checked.

    /**
     * Returns whether the given issue is suppressed in the given method.
     *
     * @param issue the issue to be checked, or null to just check for "all"
     * @param classNode the class containing the issue
     * @param method the method containing the issue
     * @param instruction the instruction within the method, if any
     * @return true if there is a suppress annotation covering the specific
     *         issue on this method
     */
    public boolean isSuppressed(
            @Nullable Issue issue,
            @NonNull ClassNode classNode,
            @NonNull MethodNode method,
            @Nullable AbstractInsnNode instruction) {
        if (method.invisibleAnnotations != null) {
            @SuppressWarnings("unchecked")
            List<AnnotationNode> annotations = method.invisibleAnnotations;
            return isSuppressed(issue, annotations);
        }

        // Initializations of fields end up placed in generated methods (<init>
        // for members and <clinit> for static fields).
        if (instruction != null && method.name.charAt(0) == '<') {
            AbstractInsnNode next = LintUtils.getNextInstruction(instruction);
            if (next != null && next.getType() == AbstractInsnNode.FIELD_INSN) {
                FieldInsnNode fieldRef = (FieldInsnNode) next;
                FieldNode field = findField(classNode, fieldRef.owner, fieldRef.name);
                if (field != null && isSuppressed(issue, field)) {
                    return true;
                }
            } else if (classNode.outerClass != null && classNode.outerMethod == null
                        && isAnonymousClass(classNode)) {
                if (isSuppressed(issue, classNode)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Nullable
    private static MethodInsnNode findConstructorInvocation(
            @NonNull MethodNode method,
            @NonNull String className) {
        InsnList nodes = method.instructions;
        for (int i = 0, n = nodes.size(); i < n; i++) {
            AbstractInsnNode instruction = nodes.get(i);
            if (instruction.getOpcode() == Opcodes.INVOKESPECIAL) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (className.equals(call.owner)) {
                    return call;
                }
            }
        }

        return null;
    }

    @Nullable
    private FieldNode findField(
            @NonNull ClassNode classNode,
            @NonNull String owner,
            @NonNull String name) {
        ClassNode current = classNode;
        while (current != null) {
            if (owner.equals(current.name)) {
                @SuppressWarnings("rawtypes") // ASM API
                List fieldList = current.fields;
                for (Object f : fieldList) {
                    FieldNode field = (FieldNode) f;
                    if (field.name.equals(name)) {
                        return field;
                    }
                }
                return null;
            }
            current = getOuterClassNode(current);
        }
        return null;
    }

    @Nullable
    private MethodNode findMethod(
            @NonNull ClassNode classNode,
            @NonNull String name,
            boolean includeInherited) {
        ClassNode current = classNode;
        while (current != null) {
            @SuppressWarnings("rawtypes") // ASM API
            List methodList = current.methods;
            for (Object f : methodList) {
                MethodNode method = (MethodNode) f;
                if (method.name.equals(name)) {
                    return method;
                }
            }

            if (includeInherited) {
                current = getOuterClassNode(current);
            } else {
                break;
            }
        }
        return null;
    }

    /**
     * Returns whether the given issue is suppressed for the given field.
     *
     * @param issue the issue to be checked, or null to just check for "all"
     * @param field the field potentially annotated with a suppress annotation
     * @return true if there is a suppress annotation covering the specific
     *         issue on this field
     */
    @SuppressWarnings("MethodMayBeStatic") // API; reserve need to require driver state later
    public boolean isSuppressed(@Nullable Issue issue, @NonNull FieldNode field) {
        if (field.invisibleAnnotations != null) {
            @SuppressWarnings("unchecked")
            List<AnnotationNode> annotations = field.invisibleAnnotations;
            return isSuppressed(issue, annotations);
        }

        return false;
    }

    /**
     * Returns whether the given issue is suppressed in the given class.
     *
     * @param issue the issue to be checked, or null to just check for "all"
     * @param classNode the class containing the issue
     * @return true if there is a suppress annotation covering the specific
     *         issue in this class
     */
    public boolean isSuppressed(@Nullable Issue issue, @NonNull ClassNode classNode) {
        if (classNode.invisibleAnnotations != null) {
            @SuppressWarnings("unchecked")
            List<AnnotationNode> annotations = classNode.invisibleAnnotations;
            return isSuppressed(issue, annotations);
        }

        if (classNode.outerClass != null && classNode.outerMethod == null
                && isAnonymousClass(classNode)) {
            ClassNode outer = getOuterClassNode(classNode);
            if (outer != null) {
                MethodNode m = findMethod(outer, CONSTRUCTOR_NAME, false);
                if (m != null) {
                    MethodInsnNode call = findConstructorInvocation(m, classNode.name);
                    if (call != null) {
                        if (isSuppressed(issue, outer, m, call)) {
                            return true;
                        }
                    }
                }
                m = findMethod(outer, CLASS_CONSTRUCTOR, false);
                if (m != null) {
                    MethodInsnNode call = findConstructorInvocation(m, classNode.name);
                    if (call != null) {
                        if (isSuppressed(issue, outer, m, call)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private static boolean isSuppressed(@Nullable Issue issue, List<AnnotationNode> annotations) {
        for (AnnotationNode annotation : annotations) {
            String desc = annotation.desc;

            // We could obey @SuppressWarnings("all") too, but no need to look for it
            // because that annotation only has source retention.

            if (desc.endsWith(SUPPRESS_LINT_VMSIG)) {
                if (annotation.values != null) {
                    for (int i = 0, n = annotation.values.size(); i < n; i += 2) {
                        String key = (String) annotation.values.get(i);
                        if (key.equals("value")) {
                            Object value = annotation.values.get(i + 1);
                            if (value instanceof String) {
                                String id = (String) value;
                                if (matches(issue, id)) {
                                    return true;
                                }
                            } else if (value instanceof List) {
                                @SuppressWarnings("rawtypes")
                                List list = (List) value;
                                for (Object v : list) {
                                    if (v instanceof String) {
                                        String id = (String) v;
                                        if (matches(issue, id)) {
                                            return true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    private static boolean matches(@Nullable Issue issue, @NonNull String id) {
        if (id.equalsIgnoreCase(SUPPRESS_ALL)) {
            return true;
        }

        if (issue != null) {
            String issueId = issue.getId();
            if (id.equalsIgnoreCase(issueId)) {
                return true;
            }
            if (id.startsWith(STUDIO_ID_PREFIX)
                && id.regionMatches(true, STUDIO_ID_PREFIX.length(), issueId, 0, issueId.length())
                && id.substring(STUDIO_ID_PREFIX.length()).equalsIgnoreCase(issueId)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true if the given issue is suppressed by the given suppress string; this
     * is typically the same as the issue id, but is allowed to not match case sensitively,
     * and is allowed to be a comma separated list, and can be the string "all"
     *
     * @param issue  the issue id to match
     * @param string the suppress string -- typically the id, or "all", or a comma separated list
     *               of ids
     * @return true if the issue is suppressed by the given string
     */
    private static boolean isSuppressed(@NonNull Issue issue, @NonNull String string) {
        if (string.isEmpty()) {
            return false;
        }

        if (string.indexOf(',') == -1) {
            if (matches(issue, string)) {
                return true;
            }
        } else {
            for (String id : Splitter.on(',').trimResults().split(string)) {
                if (matches(issue, id)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Returns whether the given issue is suppressed in the given parse tree node.
     *
     * @param context the context for the source being scanned
     * @param issue the issue to be checked, or null to just check for "all"
     * @param scope the AST node containing the issue
     * @return true if there is a suppress annotation covering the specific
     *         issue in this class
     */
    public boolean isSuppressed(@Nullable JavaContext context, @NonNull Issue issue,
            @Nullable Node scope) {
        boolean checkComments = client.checkForSuppressComments() &&
                context != null && context.containsCommentSuppress();
        while (scope != null) {
            Class<? extends Node> type = scope.getClass();
            // The Lombok AST uses a flat hierarchy of node type implementation classes
            // so no need to do instanceof stuff here.
            if (type == VariableDefinition.class) {
                // Variable
                VariableDefinition declaration = (VariableDefinition) scope;
                if (isSuppressed(issue, declaration.astModifiers())) {
                    return true;
                }
            } else if (type == MethodDeclaration.class) {
                // Method
                // Look for annotations on the method
                MethodDeclaration declaration = (MethodDeclaration) scope;
                if (isSuppressed(issue, declaration.astModifiers())) {
                    return true;
                }
            } else if (type == ConstructorDeclaration.class) {
                // Constructor
                // Look for annotations on the method
                ConstructorDeclaration declaration = (ConstructorDeclaration) scope;
                if (isSuppressed(issue, declaration.astModifiers())) {
                    return true;
                }
            } else if (TypeDeclaration.class.isAssignableFrom(type)) {
                // Class, annotation, enum, interface
                TypeDeclaration declaration = (TypeDeclaration) scope;
                if (isSuppressed(issue, declaration.astModifiers())) {
                    return true;
                }
            } else if (type == AnnotationMethodDeclaration.class) {
                // Look for annotations on the method
                AnnotationMethodDeclaration declaration = (AnnotationMethodDeclaration) scope;
                if (isSuppressed(issue, declaration.astModifiers())) {
                    return true;
                }
            }

            if (checkComments && context.isSuppressedWithComment(scope, issue)) {
                return true;
            }

            scope = scope.getParent();
        }

        return false;
    }

    public boolean isSuppressed(@Nullable JavaContext context, @NonNull Issue issue,
            @Nullable UElement scope) {
        boolean checkComments = client.checkForSuppressComments() &&
                context != null && context.containsCommentSuppress();
        while (scope != null) {
            if (scope instanceof PsiModifierListOwner) {
                PsiModifierListOwner owner = (PsiModifierListOwner) scope;
                if (isSuppressed(issue, owner.getModifierList())) {
                    return true;
                }
            }

            if (checkComments && context.isSuppressedWithComment(scope, issue)) {
                return true;
            }

            scope = scope.getUastParent();
            if (scope instanceof PsiFile) {
                return false;
            }
        }

        return false;
    }

    public boolean isSuppressed(@Nullable JavaContext context, @NonNull Issue issue,
            @Nullable PsiElement scope) {
        boolean checkComments = client.checkForSuppressComments() &&
                context != null && context.containsCommentSuppress();
        while (scope != null) {
            if (scope instanceof PsiModifierListOwner) {
                PsiModifierListOwner owner = (PsiModifierListOwner) scope;
                if (isSuppressed(issue, owner.getModifierList())) {
                    return true;
                }
            }

            if (checkComments && context.isSuppressedWithComment(scope, issue)) {
                return true;
            }

            scope = scope.getParent();
            if (scope instanceof PsiFile) {
                return false;
            }
        }

        return false;
    }

    /**
     * Returns true if the given AST modifier has a suppress annotation for the
     * given issue (which can be null to check for the "all" annotation)
     *
     * @param issue the issue to be checked
     * @param modifiers the modifier to check
     * @return true if the issue or all issues should be suppressed for this
     *         modifier
     */
    private static boolean isSuppressed(@Nullable Issue issue, @Nullable Modifiers modifiers) {
        if (modifiers == null) {
            return false;
        }
        StrictListAccessor<Annotation, Modifiers> annotations = modifiers.astAnnotations();
        if (annotations == null) {
            return false;
        }

        for (Annotation annotation : annotations) {
            TypeReference t = annotation.astAnnotationTypeReference();
            String typeName = t.getTypeName();
            if (typeName.endsWith(SUPPRESS_LINT)
                    || typeName.endsWith("SuppressWarnings")) {
                StrictListAccessor<AnnotationElement, Annotation> values =
                        annotation.astElements();
                if (values != null) {
                    for (AnnotationElement element : values) {
                        AnnotationValue valueNode = element.astValue();
                        if (valueNode == null) {
                            continue;
                        }
                        if (valueNode instanceof StringLiteral) {
                            StringLiteral literal = (StringLiteral) valueNode;
                            String value = literal.astValue();
                            if (matches(issue, value)) {
                                return true;
                            }
                        } else if (valueNode instanceof ArrayInitializer) {
                            ArrayInitializer array = (ArrayInitializer) valueNode;
                            StrictListAccessor<Expression, ArrayInitializer> expressions =
                                    array.astExpressions();
                            if (expressions == null) {
                                continue;
                            }
                            for (Expression arrayElement : expressions) {
                                if (arrayElement instanceof StringLiteral) {
                                    String value = ((StringLiteral) arrayElement).astValue();
                                    if (matches(issue, value)) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    public static final String SUPPRESS_WARNINGS_FQCN = "java.lang.SuppressWarnings";


    /**
     * Returns true if the given AST modifier has a suppress annotation for the
     * given issue (which can be null to check for the "all" annotation)
     *
     * @param issue the issue to be checked
     * @param modifierList the modifier to check
     * @return true if the issue or all issues should be suppressed for this
     *         modifier
     */
    public static boolean isSuppressed(@NonNull Issue issue,
            @Nullable PsiModifierList modifierList) {
        if (modifierList == null) {
            return false;
        }

        for (PsiAnnotation annotation : modifierList.getAnnotations()) {
            String fqcn = annotation.getQualifiedName();
            if (fqcn != null && (fqcn.equals(FQCN_SUPPRESS_LINT)
                    || fqcn.equals(SUPPRESS_WARNINGS_FQCN)
                    || fqcn.equals(SUPPRESS_LINT))) { // when missing imports
                PsiAnnotationParameterList parameterList = annotation.getParameterList();
                for (PsiNameValuePair pair : parameterList.getAttributes()) {
                    if (isSuppressed(issue, pair.getValue())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Returns true if the annotation member value, assumed to be specified on a a SuppressWarnings
     * or SuppressLint annotation, specifies the given id (or "all").
     *
     * @param issue the issue to be checked
     * @param value     the member value to check
     * @return true if the issue or all issues should be suppressed for this modifier
     */
    public static boolean isSuppressed(@NonNull Issue issue,
            @Nullable PsiAnnotationMemberValue value) {
        if (value instanceof PsiLiteral) {
            PsiLiteral literal = (PsiLiteral)value;
            Object literalValue = literal.getValue();
            if (literalValue instanceof String) {
                if (isSuppressed(issue, (String) literalValue)) {
                    return true;
                }
            }
        } else if (value instanceof PsiArrayInitializerMemberValue) {
            PsiArrayInitializerMemberValue mv = (PsiArrayInitializerMemberValue)value;
            for (PsiAnnotationMemberValue mmv : mv.getInitializers()) {
                if (isSuppressed(issue, mmv)) {
                    return true;
                }
            }
        } else if (value instanceof PsiArrayInitializerExpression) {
            PsiArrayInitializerExpression expression = (PsiArrayInitializerExpression) value;
            PsiExpression[] initializers = expression.getInitializers();
            for (PsiExpression e : initializers) {
                if (isSuppressed(issue, e)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Returns whether the given issue is suppressed in the given XML DOM node.
     *
     * @param issue the issue to be checked, or null to just check for "all"
     * @param node the DOM node containing the issue
     * @return true if there is a suppress annotation covering the specific
     *         issue in this class
     */
    public boolean isSuppressed(@Nullable XmlContext context, @NonNull Issue issue,
            @Nullable org.w3c.dom.Node node) {
        if (node instanceof Attr) {
            node = ((Attr) node).getOwnerElement();
        }
        boolean checkComments = client.checkForSuppressComments()
                && context != null && context.containsCommentSuppress();
        while (node != null) {
            if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element element = (Element) node;
                if (element.hasAttributeNS(TOOLS_URI, ATTR_IGNORE)) {
                    String ignore = element.getAttributeNS(TOOLS_URI, ATTR_IGNORE);
                    if (isSuppressed(issue, ignore)) {
                        return true;
                    }
                } else if (checkComments && context.isSuppressedWithComment(node, issue)) {
                    return true;
                }
            }

            node = node.getParentNode();
        }

        return false;
    }

    private File cachedFolder = null;
    private int cachedFolderVersion = -1;
    /** Pattern for version qualifiers */
    private static final Pattern VERSION_PATTERN = Pattern.compile("^v(\\d+)$");

    /**
     * Returns the folder version of the given file. For example, for the file values-v14/foo.xml,
     * it returns 14.
     *
     * @param resourceFile the file to be checked
     * @return the folder version, or -1 if no specific version was specified
     */
    public int getResourceFolderVersion(@NonNull File resourceFile) {
        File parent = resourceFile.getParentFile();
        if (parent == null) {
            return -1;
        }
        if (parent.equals(cachedFolder)) {
            return cachedFolderVersion;
        }

        cachedFolder = parent;
        cachedFolderVersion = -1;

        for (String qualifier : QUALIFIER_SPLITTER.split(parent.getName())) {
            Matcher matcher = VERSION_PATTERN.matcher(qualifier);
            if (matcher.matches()) {
                String group = matcher.group(1);
                assert group != null;
                cachedFolderVersion = Integer.parseInt(group);
                break;
            }
        }

        return cachedFolderVersion;
    }

}

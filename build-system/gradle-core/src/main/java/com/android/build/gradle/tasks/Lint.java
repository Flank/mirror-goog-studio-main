/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.tasks;

import static com.android.SdkConstants.VALUE_FALSE;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.LintGradleClient;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.dsl.LintOptions;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.tools.lint.LintCliFlags;
import com.android.tools.lint.Reporter;
import com.android.tools.lint.Reporter.Stats;
import com.android.tools.lint.Warning;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.checks.GradleDetector;
import com.android.tools.lint.checks.UnusedResourceDetector;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.LintBaseline;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Severity;
import com.android.utils.FileUtils;
import com.android.utils.Pair;
import com.android.utils.StringHelper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

@ParallelizableTask
public class Lint extends BaseTask {
    /** Name of property used to enable {@link #MODEL_LIBRARIES} */
    public static final String MODEL_LIBRARIES_PROPERTY = "lint.new-lib-model"; // for test access
    /**
     * Whether lint should attempt to do deep analysis of libraries. E.g. when
     * building up the project graph, when it encounters an AndroidLibrary or JavaLibrary
     * dependency, it should check if it's a local project, and if so recursively initialize
     * the project with the local source paths etc of the library (in the past, this was not
     * the case: it would naively just point to the library's resources and class files,
     * which were the compiled outputs.
     * <p>
     * The new behavior is clearly the correct behavior (see issue #194092), but since this
     * is a risky fix, we're putting it behind a flag now and as soon as we get some real
     * user testing, we should enable this by default and remove the old code.
     */
    public static final boolean MODEL_LIBRARIES =
            !VALUE_FALSE.equals(System.getProperty(MODEL_LIBRARIES_PROPERTY));

    private static final Logger LOG = Logging.getLogger(Lint.class);

    @Nullable private LintOptions lintOptions;
    @Nullable private File sdkHome;
    private boolean fatalOnly;
    private ToolingModelBuilderRegistry toolingRegistry;
    @Nullable private File reportsDir;
    private File manifestReportFile;
    private File outputsDir;

    private FileCollection manifestsForVariant;

    @InputFiles
    @Optional
    public FileCollection getManifestsForVariant() {
        return manifestsForVariant;
    }

    private VariantScope variantScope;

    public void setLintOptions(@NonNull LintOptions lintOptions) {
        this.lintOptions = lintOptions;
    }

    public void setSdkHome(@NonNull File sdkHome) {
        this.sdkHome = sdkHome;
    }

    public void setToolingRegistry(ToolingModelBuilderRegistry toolingRegistry) {
        this.toolingRegistry = toolingRegistry;
    }

    public void setReportsDir(@Nullable File reportDir) {
        this.reportsDir = reportDir;
    }

    public void setFatalOnly(boolean fatalOnly) {
        this.fatalOnly = fatalOnly;
    }

    @TaskAction
    public void lint() throws IOException {
        // we run by default in headless mode, so the JVM doesn't steal focus.
        System.setProperty("java.awt.headless", "true");

        AndroidProject modelProject = createAndroidProject(getProject());
        if (getVariantName() != null && !getVariantName().isEmpty()) {
            for (Variant variant : modelProject.getVariants()) {
                if (variant.getName().equals(getVariantName())) {
                    lintSingleVariant(modelProject, variant);
                }
            }
        } else {
            lintAllVariants(modelProject);
        }
    }

    /**
     * Runs lint individually on all the variants, and then compares the results
     * across variants and reports these
     */
    public void lintAllVariants(@NonNull AndroidProject modelProject) throws IOException {
        // In the Gradle integration we iterate over each variant, and
        // attribute unused resources to each variant, so don't make
        // each variant run go and inspect the inactive variant sources
        UnusedResourceDetector.sIncludeInactiveReferences = false;

        Map<Variant,List<Warning>> warningMap = Maps.newHashMap();
        List<LintBaseline> baselines = Lists.newArrayList();
        for (Variant variant : modelProject.getVariants()) {
            Pair<List<Warning>,LintBaseline> pair = runLint(modelProject, variant, false);
            List<Warning> warnings = pair.getFirst();
            warningMap.put(variant, warnings);
            LintBaseline baseline = pair.getSecond();
            if (baseline != null) {
                baselines.add(baseline);
            }
        }

        // Compute error matrix
        boolean quiet = false;
        if (lintOptions != null) {
            quiet = lintOptions.isQuiet();
        }

        for (Map.Entry<Variant,List<Warning>> entry : warningMap.entrySet()) {
            Variant variant = entry.getKey();
            List<Warning> warnings = entry.getValue();
            if (!fatalOnly && !quiet) {
                LOG.warn("Ran lint on variant {}: {} issues found",
                        variant.getName(), warnings.size());
            }
        }

        List<Warning> mergedWarnings = LintGradleClient.merge(warningMap, modelProject);
        int errorCount = 0;
        int warningCount = 0;
        for (Warning warning : mergedWarnings) {
            if (warning.severity == Severity.ERROR || warning.severity == Severity.FATAL) {
                errorCount++;
            } else if (warning.severity == Severity.WARNING) {
                warningCount++;
            }
        }

        // We pick the first variant to generate the full report and don't generate if we don't
        // have any variants.
        if (!modelProject.getVariants().isEmpty()) {
            Set<Variant> allVariants = Sets.newTreeSet(
                    (v1, v2) -> v1.getName().compareTo(v2.getName()));

            allVariants.addAll(modelProject.getVariants());
            Variant variant = allVariants.iterator().next();

            IssueRegistry registry = new BuiltinIssueRegistry();
            LintCliFlags flags = new LintCliFlags();
            LintGradleClient client = new LintGradleClient(
                    registry, flags, getProject(), modelProject,
                    sdkHome, variant, getBuildTools(), getManifestReportFile(variant));
            syncOptions(lintOptions, client, flags, null, getProject(), reportsDir,
                    true, fatalOnly);

            // Compute baseline counts. This is tricky because an error could appear in
            // multiple variants, and in that case it should only be counted as filtered
            // from the baseline once, but if there are errors that appear only in individual
            // variants, then they shouldn't count as one. To correctly account for this we
            // need to ask the baselines themselves to merge their results. Right now they
            // only contain the remaining (fixed) issues; to address this we'd need to move
            // found issues to a different map such that at the end we can successively
            // merge the baseline instances together to a final one which has the full set
            // of filtered and remaining counts.
            int baselineErrorCount = 0;
            int baselineWarningCount = 0;
            int fixedCount = 0;
            if (!baselines.isEmpty()) {
                // Figure out the actual overlap; later I could stash these into temporary
                // objects to compare
                // For now just combine them in a dumb way
                for (LintBaseline baseline : baselines) {
                    baselineErrorCount = Math.max(baselineErrorCount,
                            baseline.getFoundErrorCount());
                    baselineWarningCount = Math.max(baselineWarningCount,
                            baseline.getFoundWarningCount());
                    fixedCount = Math.max(fixedCount, baseline.getFixedCount());
                }
            }

            Stats stats = new Stats(errorCount, warningCount, baselineErrorCount,
                    baselineWarningCount, fixedCount);

            for (Reporter reporter : flags.getReporters()) {
                reporter.write(stats, mergedWarnings);
            }

            File baselineFile = flags.getBaselineFile();
            if (baselineFile != null && !baselineFile.exists()) {
                File dir = baselineFile.getParentFile();
                boolean ok = true;
                if (!dir.isDirectory()) {
                    ok = dir.mkdirs();
                }
                if (!ok) {
                    System.err.println("Couldn't create baseline folder " + dir);
                } else {
                    Reporter reporter = Reporter.createXmlReporter(client, baselineFile, true);
                    reporter.write(stats, mergedWarnings);
                    System.err.println("Created baseline file " + baselineFile);
                    if (VALUE_TRUE.equals(System.getProperty("lint.baselines.continue"))) {
                        return;
                    }
                    System.err.println("(Also breaking build in case this was not intentional.)");
                    String message = ""
                            + "Created baseline file " + baselineFile + "\n"
                            + "\n"
                            + "Also breaking the build in case this was not intentional. If you\n"
                            + "deliberately created the baseline file, re-run the build and this\n"
                            + "time it should succeed without warnings.\n"
                            + "\n"
                            + "If not, investigate the baseline path in the lintOptions config\n"
                            + "or verify that the baseline file has been checked into version\n"
                            + "control.\n"
                            + "\n"
                            + "You can set the system property lint.baselines.continue=true\n"
                            + "if you want to create many missing baselines in one go.";
                    throw new GradleException(message);
                }
            }

            if (baselineErrorCount > 0 || baselineWarningCount > 0) {
                System.out.println(String.format("%1$s were filtered out because "
                                + "they were listed in the baseline file, %2$s\n",
                        LintUtils.describeCounts(baselineErrorCount, baselineWarningCount, false,
                                true),
                        baselineFile));
            }
            if (fixedCount > 0) {
                System.out.println(String.format("%1$d errors/warnings were listed in the "
                        + "baseline file (%2$s) but not found in the project; perhaps they have "
                        + "been fixed?\n", fixedCount, baselineFile));
            }

            if (flags.isSetExitCode() && errorCount > 0) {
                abort();
            }
        }
    }

    private void abort() {
        String message;
        if (fatalOnly) {
            message = "" +
                    "Lint found fatal errors while assembling a release target.\n" +
                    "\n" +
                    "To proceed, either fix the issues identified by lint, or modify your build script as follows:\n" +
                    "...\n" +
                    "android {\n" +
                    "    lintOptions {\n" +
                    "        checkReleaseBuilds false\n" +
                    "        // Or, if you prefer, you can continue to check for errors in release builds,\n" +
                    "        // but continue the build even when errors are found:\n" +
                    "        abortOnError false\n" +
                    "    }\n" +
                    "}\n" +
                    "...";
        } else {
            message = "" +
                    "Lint found errors in the project; aborting build.\n" +
                    "\n" +
                    "Fix the issues identified by lint, or add the following to your build script to proceed with errors:\n" +
                    "...\n" +
                    "android {\n" +
                    "    lintOptions {\n" +
                    "        abortOnError false\n" +
                    "    }\n" +
                    "}\n" +
                    "...";
        }
        throw new GradleException(message);
    }

    /**
     * Runs lint on a single specified variant
     */
    public void lintSingleVariant(@NonNull AndroidProject modelProject, @NonNull Variant variant) {
        runLint(modelProject, variant, true);
    }

    /** Runs lint on the given variant and returns the set of warnings */
    private Pair<List<Warning>,LintBaseline> runLint(
            /*
             * Note that as soon as we disable {@link #MODEL_LIBRARIES} this is
             * unused and we can delete it and all the callers passing it recursively
             */
            @NonNull AndroidProject modelProject,
            @NonNull Variant variant,
            boolean report) {
        IssueRegistry registry = createIssueRegistry();
        LintCliFlags flags = new LintCliFlags();
        LintGradleClient client = new LintGradleClient(registry, flags, getProject(), modelProject,
                sdkHome, variant, getBuildTools(), getManifestReportFile(variant));
        if (fatalOnly) {
            flags.setFatalOnly(true);
        }
        if (lintOptions != null) {
            syncOptions(lintOptions, client, flags, variant, getProject(), reportsDir, report,
                    fatalOnly);
        }
        if (!report || fatalOnly) {
            flags.setQuiet(true);
        }
        flags.setWriteBaselineIfMissing(report && !fatalOnly);

        Pair<List<Warning>,LintBaseline> warnings;
        try {
            warnings = client.run(registry);
        } catch (IOException e) {
            throw new GradleException("Invalid arguments.", e);
        }

        if (report && client.haveErrors() && flags.isSetExitCode()) {
            abort();
        }

        return warnings;
    }

    private static void syncOptions(
            @NonNull LintOptions options,
            @NonNull LintGradleClient client,
            @NonNull LintCliFlags flags,
            @Nullable Variant variant,
            @NonNull Project project,
            @Nullable File reportsDir,
            boolean report,
            boolean fatalOnly) {
        options.syncTo(client, flags, variant != null ? variant.getName() : null, project,
                reportsDir, report);

        boolean displayEmpty = !(fatalOnly || flags.isQuiet());
        for (Reporter reporter : flags.getReporters()) {
            reporter.setDisplayEmpty(displayEmpty);
        }
    }

    private AndroidProject createAndroidProject(@NonNull Project gradleProject) {
        String modelName = AndroidProject.class.getName();
        ToolingModelBuilder modelBuilder = toolingRegistry.getBuilder(modelName);
        assert modelBuilder != null;

        // setup the level 3 sync.
        final ExtraPropertiesExtension ext = gradleProject.getExtensions().getExtraProperties();
        ext.set(
                AndroidProject.PROPERTY_BUILD_MODEL_ONLY_VERSIONED,
                Integer.toString(AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD));

        try {
            return (AndroidProject) modelBuilder.buildAll(modelName, gradleProject);
        } finally {
            ext.set(AndroidProject.PROPERTY_BUILD_MODEL_ONLY_VERSIONED, null);
        }
    }

    private static BuiltinIssueRegistry createIssueRegistry() {
        return new LintGradleIssueRegistry();
    }

    public void setManifestReportFile(@Nullable File manifestReportFile) {
        this.manifestReportFile = manifestReportFile;
    }

    @Nullable
    public File getManifestReportFile(@Nullable Variant variant) {
        if (manifestReportFile == null && outputsDir != null && variant != null) {
            // When running the lint-all (all variants task) we don't
            // have a report file since it varies by variant; this
            // duplicates the logic in VariantScopeImpl#getManifestReportFile
            manifestReportFile = FileUtils.join(outputsDir,
                    "logs", "manifest-merger-" + variant.getDisplayName()
                            + "-report.txt");
            // variant.getDisplayName corresponds to variantData.getVariantConfiguration().getBaseName()
        }
        return manifestReportFile;
    }

    public void setOutputsDir(@Nullable File outputsDir) {
        this.outputsDir = outputsDir;
    }

    @Nullable
    public File getOutputsDir() {
        return outputsDir;
    }

    // Issue registry when Lint is run inside Gradle: we replace the Gradle
    // detector with a local implementation which directly references Groovy
    // for parsing. In Studio on the other hand, the implementation is replaced
    // by a PSI-based check. (This is necessary for now since we don't have a
    // tool-agnostic API for the Groovy AST and we don't want to add a 6.3MB dependency
    // on Groovy itself quite yet.
    public static class LintGradleIssueRegistry extends BuiltinIssueRegistry {
        private boolean mInitialized;

        public LintGradleIssueRegistry() {
        }

        @NonNull
        @Override
        public List<Issue> getIssues() {
            List<Issue> issues = super.getIssues();
            if (!mInitialized) {
                mInitialized = true;
                for (Issue issue : issues) {
                    if (issue.getImplementation().getDetectorClass() == GradleDetector.class) {
                        issue.setImplementation(GroovyGradleDetector.IMPLEMENTATION);
                    }
                }
            }

            return issues;
        }
    }

    public static class ConfigAction implements TaskConfigAction<Lint> {

        private final VariantScope scope;

        public ConfigAction(@NonNull VariantScope scope) {
            this.scope = scope;
        }

        @Override
        @NonNull
        public String getName() {
            return scope.getTaskName("lint");
        }

        @Override
        @NonNull
        public Class<Lint> getType() {
            return Lint.class;
        }

        @Override
        public void execute(@NonNull Lint lint) {
            GlobalScope globalScope = scope.getGlobalScope();
            lint.setLintOptions(globalScope.getExtension().getLintOptions());
            File sdkFolder = globalScope.getSdkHandler().getSdkFolder();
            if (sdkFolder != null) {
                lint.setSdkHome(sdkFolder);
            }
            lint.setAndroidBuilder(globalScope.getAndroidBuilder());
            lint.setVariantName(scope.getVariantConfiguration().getFullName());
            lint.manifestsForVariant =
                    scope.getOutput(TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS);
            lint.setToolingRegistry(globalScope.getToolingRegistry());
            lint.setReportsDir(globalScope.getReportsDir());
            lint.setOutputsDir(scope.getGlobalScope().getOutputsDir());
            lint.setManifestReportFile(scope.getManifestReportFile());
            lint.setDescription("Runs lint on the " + StringHelper
                    .capitalize(scope.getVariantConfiguration().getFullName()) + " build.");
            lint.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        }
    }

    public static class VitalConfigAction implements TaskConfigAction<Lint> {

        private final VariantScope scope;

        public VitalConfigAction(@NonNull VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("lintVital");
        }

        @NonNull
        @Override
        public Class<Lint> getType() {
            return Lint.class;
        }

        @Override
        public void execute(@NonNull Lint task) {
            String variantName = scope.getVariantData().getVariantConfiguration().getFullName();
            GlobalScope globalScope = scope.getGlobalScope();
            task.setAndroidBuilder(globalScope.getAndroidBuilder());
            // TODO: Make this task depend on lintCompile too (resolve initialization order first)
            task.setLintOptions(globalScope.getExtension().getLintOptions());
            task.setSdkHome(checkNotNull(
                    globalScope.getSdkHandler().getSdkFolder(), "SDK not set up."));
            task.setVariantName(variantName);
            task.manifestsForVariant =
                    scope.getOutput(TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS);
            task.setToolingRegistry(globalScope.getToolingRegistry());
            task.setReportsDir(globalScope.getReportsDir());
            task.setOutputsDir(scope.getGlobalScope().getOutputsDir());
            task.setManifestReportFile(scope.getManifestReportFile());
            task.setFatalOnly(true);
            task.setDescription(
                    "Runs lint on just the fatal issues in the " + variantName + " build.");
        }
    }

    public static class GlobalConfigAction implements TaskConfigAction<Lint> {

        private final GlobalScope globalScope;

        public GlobalConfigAction(GlobalScope globalScope) {
            this.globalScope = globalScope;
        }

        @NonNull
        @Override
        public String getName() {
            return TaskManager.LINT;
        }

        @NonNull
        @Override
        public Class<Lint> getType() {
            return Lint.class;
        }

        @Override
        public void execute(@NonNull Lint lintTask) {
            lintTask.setDescription("Runs lint on all variants.");
            lintTask.setVariantName("");
            lintTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
            lintTask.setLintOptions(globalScope.getExtension().getLintOptions());
            File sdkFolder = globalScope.getSdkHandler().getSdkFolder();
            if (sdkFolder != null) {
                lintTask.setSdkHome(sdkFolder);
            }
            lintTask.setToolingRegistry(globalScope.getToolingRegistry());
            lintTask.setReportsDir(globalScope.getReportsDir());
            lintTask.setOutputsDir(globalScope.getOutputsDir());
            lintTask.setAndroidBuilder(globalScope.getAndroidBuilder());
        }
    }
}

/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.scope;

import static com.android.builder.core.BuilderConstants.FD_REPORTS;
import static com.android.builder.model.AndroidProject.FD_GENERATED;
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;
import static com.android.builder.model.AndroidProject.FD_OUTPUTS;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.BuildCacheUtils;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.OptionalCompilationStep;
import com.android.builder.utils.FileCache;
import com.android.utils.FileUtils;
import com.google.common.base.CharMatcher;
import com.google.common.base.MoreObjects;
import java.io.File;
import java.util.Set;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/**
 * A scope containing data for the Android plugin.
 */
public class GlobalScope implements TransformGlobalScope {

    @NonNull
    private Project project;

    @NonNull
    private AndroidBuilder androidBuilder;

    @NonNull
    private AndroidConfig extension;

    @NonNull
    private SdkHandler sdkHandler;

    @NonNull
    private NdkHandler ndkHandler;

    @NonNull
    private ToolingModelBuilderRegistry toolingRegistry;

    @Nullable
    private File mockableAndroidJarFile;

    @NonNull private final Set<OptionalCompilationStep> optionalCompilationSteps;

    @NonNull private final ProjectOptions projectOptions;

    @Nullable
    private final FileCache buildCache;

    @Nullable private FileCache projectLevelCache = null;

    @Nullable private ConfigurableFileCollection java8LangSupportJar = null;

    public GlobalScope(
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull AndroidConfig extension,
            @NonNull SdkHandler sdkHandler,
            @NonNull NdkHandler ndkHandler,
            @NonNull ToolingModelBuilderRegistry toolingRegistry) {
        // Attention: remember that this code runs early in the build lifecycle, project may not
        // have been fully configured yet (e.g. buildDir can still change).
        this.project = project;
        this.androidBuilder = androidBuilder;
        this.extension = extension;
        this.sdkHandler = sdkHandler;
        this.ndkHandler = ndkHandler;
        this.toolingRegistry = toolingRegistry;
        this.optionalCompilationSteps = projectOptions.getOptionalCompilationSteps();
        this.projectOptions = projectOptions;
        this.buildCache =
                BuildCacheUtils.createBuildCacheIfEnabled(
                        project.getRootProject()::file, projectOptions);
    }

    @NonNull
    @Override
    public Project getProject() {
        return project;
    }

    @NonNull
    public AndroidConfig getExtension() {
        return extension;
    }

    @NonNull
    public AndroidBuilder getAndroidBuilder() {
        return androidBuilder;
    }

    @NonNull
    public String getProjectBaseName() {
        return (String) project.property("archivesBaseName");
    }

    @NonNull
    public SdkHandler getSdkHandler() {
        return sdkHandler;
    }

    @NonNull
    public NdkHandler getNdkHandler() {
        return ndkHandler;
    }

    @NonNull
    public ToolingModelBuilderRegistry getToolingRegistry() {
        return toolingRegistry;
    }

    @NonNull
    @Override
    public File getBuildDir() {
        return project.getBuildDir();
    }

    @NonNull
    public File getIntermediatesDir() {
        return new File(getBuildDir(), FD_INTERMEDIATES);
    }

    @NonNull
    public File getGeneratedDir() {
        return new File(getBuildDir(), FD_GENERATED);
    }

    @NonNull
    public File getReportsDir() {
        return new File(getBuildDir(), FD_REPORTS);
    }

    public File getTestResultsFolder() {
        return new File(getBuildDir(), "test-results");
    }

    public File getTestReportFolder() {
        return new File(getBuildDir(), "reports/tests");
    }

    @NonNull
    public File getMockableAndroidJarFile() {

        if (mockableAndroidJarFile == null) {
            // Since the file ends up in $rootProject.buildDir, it will survive clean
            // operations - projects generated by AS don't have a top-level clean task that
            // would delete the top-level build directory. This means that the name has to
            // encode all the necessary information, otherwise the task will be UP-TO-DATE
            // even if the file should be regenerated. That's why we put the SDK version and
            // "default-values" in there, so if one project uses the returnDefaultValues flag,
            // it will just generate a new file and not change the semantics for other
            // sub-projects. There's an implicit "v1" there as well, if we ever change the
            // generator logic, the names will have to be changed.
            String fileExt;
            if (getExtension().getTestOptions().getUnitTests().isReturnDefaultValues()) {
                fileExt = ".default-values.jar";
            } else {
                fileExt = ".jar";
            }
            File outDir = new File(
                    getProject().getRootProject().getBuildDir(),
                    AndroidProject.FD_GENERATED);

            CharMatcher safeCharacters =
                    CharMatcher.JAVA_LETTER_OR_DIGIT.or(CharMatcher.anyOf("-."));
            String sdkName =
                    safeCharacters.negate().replaceFrom(
                            getExtension().getCompileSdkVersion(), '-');
            mockableAndroidJarFile = new File(outDir, "mockable-" + sdkName + fileExt);
        }
        return mockableAndroidJarFile;
    }

    @NonNull
    public File getOutputsDir() {
        return new File(getBuildDir(), FD_OUTPUTS);
    }

    /**
     * Obtains the default location for APKs.
     *
     * @return the default location for APKs
     */
    @NonNull
    private File getDefaultApkLocation() {
        return FileUtils.join(getBuildDir(), FD_OUTPUTS, "apk");
    }

    /**
     * Obtains the location where APKs should be placed.
     *
     * @return the location for APKs
     */
    @NonNull
    public File getApkLocation() {
        return MoreObjects.firstNonNull(
                AndroidGradleOptions.getApkLocation(project),
                getDefaultApkLocation());
    }

    @Override
    public boolean isActive(OptionalCompilationStep step) {
        return optionalCompilationSteps.contains(step);
    }

    @NonNull
    public String getArchivesBaseName() {
        return (String)getProject().getProperties().get("archivesBaseName");
    }

    @NonNull
    public File getJacocoAgentOutputDirectory() {
        return new File(getIntermediatesDir(), "jacoco");
    }

    @NonNull
    public File getJacocoAgent() {
        return new File(getJacocoAgentOutputDirectory(), "jacocoagent.jar");
    }

    @NonNull
    @Override
    public ProjectOptions getProjectOptions() {
        return projectOptions;
    }

    @Nullable
    @Override
    public FileCache getBuildCache() {
        return buildCache;
    }

    @NonNull
    public synchronized FileCache getProjectLevelCache() {
        if (projectLevelCache == null) {
            projectLevelCache =
                    FileCache.getInstanceWithSingleProcessLocking(
                            FileUtils.join(
                                    project.getRootProject().getBuildDir(),
                                    FD_INTERMEDIATES,
                                    "project-cache"));
        }

        return projectLevelCache;
    }

    /** Validate flag options. */
    public static void validateAndroidGradleOptions(
            @NonNull ProjectOptions projectOptions, @Nullable FileCache buildCache) {
        if (projectOptions.get(BooleanOption.ENABLE_IMPROVED_DEPENDENCY_RESOLUTION)
                && buildCache == null) {
            throw new InvalidUserDataException("Build cache must be enabled to use improved "
                    + "dependency resolution.  Set -Pandroid.enableBuildCache=true to continue.  "
                    + "If enabling build cache is not possible, improved dependency resolution can "
                    + "be disabled by setting "
                    + "-Pandroid.enableImprovedDependenciesResolution=false.");
        }
    }

    @NonNull
    public ConfigurableFileCollection getJava8LangSupportJar() {
        if (java8LangSupportJar == null) {
            java8LangSupportJar =
                    getProject()
                            .files(
                                    FileUtils.join(
                                            getIntermediatesDir(),
                                            "processing-tools",
                                            "java8-lang-support",
                                            "desugar.jar"));
        }
        return java8LangSupportJar;
    }
}

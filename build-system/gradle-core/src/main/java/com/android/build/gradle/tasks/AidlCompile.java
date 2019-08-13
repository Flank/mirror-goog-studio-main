/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.AIDL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.tasks.TaskInputHelper.memoizeToProvider;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.core.VariantConfiguration;
import com.android.build.gradle.internal.process.GradleProcessExecutor;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.NonIncrementalTask;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.builder.compiling.DependencyFileProcessor;
import com.android.builder.internal.compiler.AidlProcessor;
import com.android.builder.internal.compiler.DirectoryWalker;
import com.android.builder.internal.incremental.DependencyData;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.repository.Revision;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.util.PatternSet;

/**
 * Task to compile aidl files. Supports incremental update.
 *
 * <p>TODO(b/124424292)
 *
 * <p>We can not use gradle worker in this task as we use {@link GradleProcessExecutor} for
 * compiling aidl files, which should not be serialized.
 */
@CacheableTask
public abstract class AidlCompile extends NonIncrementalTask {

    private static final PatternSet PATTERN_SET = new PatternSet().include("**/*.aidl");

    @Nullable private Collection<String> packageWhitelist;

    @Internal
    public abstract ListProperty<File> getSourceDirs();

    private FileCollection importDirs;

    @Internal
    public abstract Property<File> getAidlExecutableProvider();

    @Internal
    public abstract Property<Revision> getBuildToolsRevisionProvider();

    // Given the same version, the path or contents of the AIDL tool may change across platforms,
    // but it would still produce the same output (given the same inputs)---see bug 138920846.
    // Therefore, the path or contents of the tool should not be an input. Instead, we set the
    // tool's version as input.
    @Input
    public String getAidlVersion() {
        Revision buildToolsRevision = getBuildToolsRevisionProvider().getOrNull();
        Preconditions.checkState(buildToolsRevision != null, "Build Tools not present");

        File aidlExecutable = getAidlExecutableProvider().getOrNull();
        Preconditions.checkState(
                aidlExecutable != null,
                "AIDL executable not present in Build Tools " + buildToolsRevision.toString());
        Preconditions.checkState(
                aidlExecutable.exists(),
                "AIDL executable does not exist: " + aidlExecutable.getPath());

        return buildToolsRevision.toString();
    }

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract Property<File> getAidlFrameworkProvider();

    @InputFiles
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract Property<FileTree> getSourceFiles();

    private static class DepFileProcessor implements DependencyFileProcessor {
        @Override
        public DependencyData processFile(@NonNull File dependencyFile) throws IOException {
            return DependencyData.parseDependencyFile(dependencyFile);
        }
    }

    @Override
    protected void doTaskAction() throws IOException {
        // this is full run, clean the previous output
        File destinationDir = getSourceOutputDir().get().getAsFile();
        Directory parcelableDir = getPackagedDir().getOrNull();
        FileUtils.cleanOutputDir(destinationDir);
        if (parcelableDir != null) {
            FileUtils.cleanOutputDir(parcelableDir.getAsFile());
        }

        try (WorkerExecutorFacade workers = getWorkerFacadeWithThreads(false)) {
            Collection<File> sourceFolders = getSourceDirs().get();
            Set<File> importFolders = getImportDirs().getFiles();

            List<File> fullImportList =
                    Lists.newArrayListWithCapacity(sourceFolders.size() + importFolders.size());
            fullImportList.addAll(sourceFolders);
            fullImportList.addAll(importFolders);

            AidlProcessor processor =
                    new AidlProcessor(
                            getAidlExecutableProvider().get().getAbsolutePath(),
                            getAidlFrameworkProvider().get().getAbsolutePath(),
                            fullImportList,
                            destinationDir,
                            parcelableDir != null ? parcelableDir.getAsFile() : null,
                            packageWhitelist,
                            new DepFileProcessor(),
                            new GradleProcessExecutor(getProject()),
                            new LoggedProcessOutputHandler(new LoggerWrapper(getLogger())));

            for (File dir : sourceFolders) {
                workers.submit(AidlCompileRunnable.class, new AidlCompileParams(dir, processor));
            }
        }
    }

    @OutputDirectory
    @NonNull
    public abstract DirectoryProperty getSourceOutputDir();

    @OutputDirectory
    @Optional
    @NonNull
    public abstract DirectoryProperty getPackagedDir();

    @Input
    @Optional
    @Nullable
    public Collection<String> getPackageWhitelist() {
        return packageWhitelist;
    }

    public void setPackageWhitelist(@Nullable Collection<String> packageWhitelist) {
        this.packageWhitelist = packageWhitelist;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getImportDirs() {
        return importDirs;
    }

    public static class CreationAction extends VariantTaskCreationAction<AidlCompile> {

        public CreationAction(@NonNull VariantScope scope) {
            super(scope);
        }

        @Override
        @NonNull
        public String getName() {
            return getVariantScope().getTaskName("compile", "Aidl");
        }

        @Override
        @NonNull
        public Class<AidlCompile> getType() {
            return AidlCompile.class;
        }

        @Override
        public void handleProvider(@NonNull TaskProvider<? extends AidlCompile> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope().getTaskContainer().setAidlCompileTask(taskProvider);
            getVariantScope()
                    .getArtifacts()
                    .producesDir(
                            InternalArtifactType.AIDL_SOURCE_OUTPUT_DIR.INSTANCE,
                            BuildArtifactsHolder.OperationType.INITIAL,
                            taskProvider,
                            AidlCompile::getSourceOutputDir,
                            "out");

            if (getVariantScope().getVariantConfiguration().getType().isAar()) {
                getVariantScope()
                        .getArtifacts()
                        .producesDir(
                                InternalArtifactType.AIDL_PARCELABLE.INSTANCE,
                                BuildArtifactsHolder.OperationType.INITIAL,
                                taskProvider,
                                AidlCompile::getPackagedDir,
                                "out");
            }
        }

        @Override
        public void configure(@NonNull AidlCompile compileTask) {
            super.configure(compileTask);
            VariantScope scope = getVariantScope();

            final VariantConfiguration<?, ?, ?> variantConfiguration =
                    scope.getVariantConfiguration();

            compileTask
                    .getAidlExecutableProvider()
                    .set(scope.getGlobalScope().getSdkComponents().getAidlExecutableProvider());
            compileTask
                    .getBuildToolsRevisionProvider()
                    .set(scope.getGlobalScope().getSdkComponents().getBuildToolsRevisionProvider());
            compileTask
                    .getAidlFrameworkProvider()
                    .set(scope.getGlobalScope().getSdkComponents().getAidlFrameworkProvider());

            compileTask
                    .getSourceDirs()
                    .set(
                            memoizeToProvider(
                                    scope.getGlobalScope().getProject(),
                                    variantConfiguration::getAidlSourceList));

            // This is because aidl may be in the same folder as Java and we want to restrict to
            // .aidl files and not java files.
            compileTask
                    .getSourceFiles()
                    .set(
                            memoizeToProvider(
                                    scope.getGlobalScope().getProject(),
                                    () ->
                                            scope.getGlobalScope()
                                                    .getProject()
                                                    .getLayout()
                                                    .files(compileTask.getSourceDirs())
                                                    .getAsFileTree()
                                                    .matching(PATTERN_SET)));

            compileTask.importDirs = scope.getArtifactFileCollection(COMPILE_CLASSPATH, ALL, AIDL);

            if (variantConfiguration.getType().isAar()) {
                compileTask.setPackageWhitelist(
                        scope.getGlobalScope().getExtension().getAidlPackageWhiteList());
            }
        }
    }

    static class AidlCompileRunnable implements Runnable {
        private final AidlCompileParams params;

        @Inject
        AidlCompileRunnable(AidlCompileParams params) {
            this.params = params;
        }

        @Override
        public void run() {
            try {
                DirectoryWalker.builder()
                        .root(params.dir.toPath())
                        .extensions("aidl")
                        .action(params.processor)
                        .build()
                        .walk();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class AidlCompileParams implements Serializable {
        private final File dir;
        private final AidlProcessor processor;

        AidlCompileParams(File dir, AidlProcessor processor) {
            this.dir = dir;
            this.processor = processor;
        }
    }
}

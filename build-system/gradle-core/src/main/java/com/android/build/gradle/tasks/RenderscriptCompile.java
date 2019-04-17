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
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.RENDERSCRIPT;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.scope.InternalArtifactType.RENDERSCRIPT_LIB;
import static com.android.build.gradle.internal.scope.InternalArtifactType.RENDERSCRIPT_SOURCE_OUTPUT_DIR;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.process.GradleProcessExecutor;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.NdkTask;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.internal.compiler.DirectoryWalker;
import com.android.builder.internal.compiler.RenderScriptProcessor;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.FileUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskProvider;

/** Task to compile Renderscript files. Supports incremental update. */
@CacheableTask
public class RenderscriptCompile extends NdkTask {

    // ----- PUBLIC TASK API -----

    private File sourceOutputDir;

    private File resOutputDir;

    private File objOutputDir;

    private Provider<Directory> libOutputDir;


    // ----- PRIVATE TASK API -----

    private FileCollection sourceDirs;

    private FileCollection importDirs;

    private Supplier<Integer> targetApi;

    private boolean supportMode;

    private boolean useAndroidX;

    private int optimLevel;

    private boolean debugBuild;

    private boolean ndkMode;

    private Provider<BuildToolInfo> buildToolInfoProvider;

    @Input
    public String getBuildToolsVersion() {
        return buildToolInfoProvider.get().getRevision().toString();
    }

    @OutputDirectory
    public File getSourceOutputDir() {
        return sourceOutputDir;
    }

    public void setSourceOutputDir(File sourceOutputDir) {
        this.sourceOutputDir = sourceOutputDir;
    }

    @OutputDirectory
    public File getResOutputDir() {
        return resOutputDir;
    }

    public void setResOutputDir(File resOutputDir) {
        this.resOutputDir = resOutputDir;
    }

    @OutputDirectory
    public File getObjOutputDir() {
        return objOutputDir;
    }

    public void setObjOutputDir(File objOutputDir) {
        this.objOutputDir = objOutputDir;
    }

    @OutputDirectory
    public Provider<Directory> getLibOutputDir() {
        return libOutputDir;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @SkipWhenEmpty
    public FileCollection getSourceDirs() {
        return sourceDirs.getAsFileTree();
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getImportDirs() {
        return importDirs;
    }

    public void setImportDirs(FileCollection importDirs) {
        this.importDirs = importDirs;
    }

    @Input
    public Integer getTargetApi() {
        return targetApi.get();
    }

    @Input
    public boolean isSupportMode() {
        return supportMode;
    }

    public void setSupportMode(boolean supportMode) {
        this.supportMode = supportMode;
    }

    @Input
    public boolean useAndroidX() {
        return useAndroidX;
    }

    @Input
    public int getOptimLevel() {
        return optimLevel;
    }

    public void setOptimLevel(int optimLevel) {
        this.optimLevel = optimLevel;
    }

    @Input
    public boolean isDebugBuild() {
        return debugBuild;
    }

    public void setDebugBuild(boolean debugBuild) {
        this.debugBuild = debugBuild;
    }

    @Input
    public boolean isNdkMode() {
        return ndkMode;
    }

    public void setNdkMode(boolean ndkMode) {
        this.ndkMode = ndkMode;
    }

    @Override
    protected void doTaskAction() throws IOException, InterruptedException, ProcessException {
        // this is full run (always), clean the previous outputs
        File sourceDestDir = getSourceOutputDir();
        FileUtils.cleanOutputDir(sourceDestDir);

        File resDestDir = getResOutputDir();
        FileUtils.cleanOutputDir(resDestDir);

        File objDestDir = getObjOutputDir();
        FileUtils.cleanOutputDir(objDestDir);

        File libDestDir = libOutputDir.get().getAsFile();
        FileUtils.cleanOutputDir(libDestDir);

        Set<File> sourceDirectories = sourceDirs.getFiles();

        compileAllRenderscriptFiles(
                sourceDirectories,
                getImportFolders(),
                sourceDestDir,
                resDestDir,
                objDestDir,
                libDestDir,
                getTargetApi(),
                isDebugBuild(),
                getOptimLevel(),
                isNdkMode(),
                isSupportMode(),
                useAndroidX(),
                getNdkConfig() == null ? null : getNdkConfig().getAbiFilters(),
                new LoggedProcessOutputHandler(new LoggerWrapper(getLogger())),
                buildToolInfoProvider.get());
    }

    /**
     * Compiles all the renderscript files found in the given source folders.
     *
     * <p>Right now this is the only way to compile them as the renderscript compiler requires all
     * renderscript files to be passed for all compilation.
     *
     * <p>Therefore whenever a renderscript file or header changes, all must be recompiled.
     *
     * @param sourceFolders all the source folders to find files to compile
     * @param importFolders all the import folders.
     * @param sourceOutputDir the output dir in which to generate the source code
     * @param resOutputDir the output dir in which to generate the bitcode file
     * @param targetApi the target api
     * @param debugBuild whether the build is debug
     * @param optimLevel the optimization level
     * @param ndkMode whether the renderscript code should be compiled to generate C/C++ bindings
     * @param supportMode support mode flag to generate .so files.
     * @param useAndroidX whether to use AndroidX dependencies
     * @param abiFilters ABI filters in case of support mode
     * @throws IOException failed
     * @throws InterruptedException failed
     */
    public void compileAllRenderscriptFiles(
            @NonNull Collection<File> sourceFolders,
            @NonNull Collection<File> importFolders,
            @NonNull File sourceOutputDir,
            @NonNull File resOutputDir,
            @NonNull File objOutputDir,
            @NonNull File libOutputDir,
            int targetApi,
            boolean debugBuild,
            int optimLevel,
            boolean ndkMode,
            boolean supportMode,
            boolean useAndroidX,
            @Nullable Set<String> abiFilters,
            @NonNull ProcessOutputHandler processOutputHandler,
            @NonNull BuildToolInfo buildToolInfo)
            throws InterruptedException, ProcessException, IOException {
        checkNotNull(sourceFolders, "sourceFolders cannot be null.");
        checkNotNull(importFolders, "importFolders cannot be null.");
        checkNotNull(sourceOutputDir, "sourceOutputDir cannot be null.");
        checkNotNull(resOutputDir, "resOutputDir cannot be null.");

        String renderscript = buildToolInfo.getPath(BuildToolInfo.PathId.LLVM_RS_CC);
        if (renderscript == null || !new File(renderscript).isFile()) {
            throw new IllegalStateException("llvm-rs-cc is missing");
        }

        RenderScriptProcessor processor =
                new RenderScriptProcessor(
                        sourceFolders,
                        importFolders,
                        sourceOutputDir,
                        resOutputDir,
                        objOutputDir,
                        libOutputDir,
                        buildToolInfo,
                        targetApi,
                        debugBuild,
                        optimLevel,
                        ndkMode,
                        supportMode,
                        useAndroidX,
                        abiFilters,
                        new LoggerWrapper(getLogger()));
        processor.build(new GradleProcessExecutor(getProject()), processOutputHandler);
    }


    // get the import folders. If the .rsh files are not directly under the import folders,
    // we need to get the leaf folders, as this is what llvm-rs-cc expects.
    @NonNull
    private Collection<File> getImportFolders() throws IOException {
        Set<File> results = Sets.newHashSet();

        Collection<File> dirs = Lists.newArrayList();
        dirs.addAll(getImportDirs().getFiles());
        dirs.addAll(sourceDirs.getFiles());

        for (File dir : dirs) {
            // TODO(samwho): should "rsh" be a constant somewhere?
            DirectoryWalker.builder()
                    .root(dir.toPath())
                    .extensions("rsh")
                    .action((start, path) -> results.add(path.getParent().toFile()))
                    .build()
                    .walk();
        }

        return results;
    }

    // ----- CreationAction -----

    public static class CreationAction extends VariantTaskCreationAction<RenderscriptCompile> {

        private File sourceOutputDir;
        private Provider<Directory> libOutputDir;

        public CreationAction(@NonNull VariantScope scope) {
            super(scope);
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("compile", "Renderscript");
        }

        @NonNull
        @Override
        public Class<RenderscriptCompile> getType() {
            return RenderscriptCompile.class;
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);

            sourceOutputDir =
                    getVariantScope()
                            .getArtifacts()
                            .appendArtifact(RENDERSCRIPT_SOURCE_OUTPUT_DIR, taskName, "out");
            libOutputDir =
                    getVariantScope()
                            .getArtifacts()
                            .createDirectory(RENDERSCRIPT_LIB, taskName, "lib");
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends RenderscriptCompile> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope().getTaskContainer().setRenderscriptCompileTask(taskProvider);
        }

        @Override
        public void configure(@NonNull RenderscriptCompile renderscriptTask) {
            super.configure(renderscriptTask);
            VariantScope scope = getVariantScope();

            BaseVariantData variantData = scope.getVariantData();
            final GradleVariantConfiguration config = variantData.getVariantConfiguration();

            boolean ndkMode = config.getRenderscriptNdkModeEnabled();

            renderscriptTask.targetApi = TaskInputHelper.memoize(config::getRenderscriptTarget);

            renderscriptTask.supportMode = config.getRenderscriptSupportModeEnabled();
            renderscriptTask.useAndroidX =
                    scope.getGlobalScope().getProjectOptions().get(BooleanOption.USE_ANDROID_X);
            renderscriptTask.ndkMode = ndkMode;
            renderscriptTask.debugBuild = config.getBuildType().isRenderscriptDebuggable();
            renderscriptTask.optimLevel = config.getBuildType().getRenderscriptOptimLevel();

            renderscriptTask.sourceDirs =
                    scope.getGlobalScope()
                            .getProject()
                            .files((Callable<Collection<File>>) config::getRenderscriptSourceList);
            renderscriptTask.importDirs = scope.getArtifactFileCollection(
                    COMPILE_CLASSPATH, ALL, RENDERSCRIPT);

            renderscriptTask.setSourceOutputDir(sourceOutputDir);
            renderscriptTask.setResOutputDir(scope.getRenderscriptResOutputDir());
            renderscriptTask.setObjOutputDir(scope.getRenderscriptObjOutputDir());
            renderscriptTask.libOutputDir = libOutputDir;

            renderscriptTask.setNdkConfig(config.getNdkConfig());

            renderscriptTask.buildToolInfoProvider =
                    scope.getGlobalScope().getSdkComponents().getBuildToolInfoProvider();

            if (config.getType().isTestComponent()) {
                renderscriptTask.dependsOn(scope.getTaskContainer().getProcessManifestTask());
            }
        }
    }
}

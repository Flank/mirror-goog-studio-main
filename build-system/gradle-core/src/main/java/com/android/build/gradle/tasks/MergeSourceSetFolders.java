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

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.core.VariantType;
import com.android.ide.common.res2.AssetMerger;
import com.android.ide.common.res2.AssetSet;
import com.android.ide.common.res2.FileStatus;
import com.android.ide.common.res2.FileValidity;
import com.android.ide.common.res2.MergedAssetWriter;
import com.android.ide.common.res2.MergingException;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.ParallelizableTask;

@ParallelizableTask
public class MergeSourceSetFolders extends IncrementalTask {

    // ----- PUBLIC TASK API -----

    private File outputDir;

    @OutputDirectory
    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }

    // ----- PRIVATE TASK API -----

    private InputSupplier<List<AssetSet>> assetSetSupplier;
    // for the dependencies
    private InputSupplier<List<AssetSet>> dependencySetSupplier;

    private FileCollection libraries = null;
    private FileCollection shadersOutputDir = null;
    private FileCollection copyApk = null;
    private String ignoreAssets = null;

    private final FileValidity<AssetSet> fileValidity = new FileValidity<>();

    @Override
    protected boolean isIncremental() {
        return true;
    }

    @Override
    protected void doFullTaskAction() throws IOException {
        // this is full run, clean the previous output
        File destinationDir = getOutputDir();
        FileUtils.cleanOutputDir(destinationDir);

        List<AssetSet> assetSets = computeAssetSetList();

        // create a new merger and populate it with the sets.
        AssetMerger merger = new AssetMerger();

        try {
            for (AssetSet assetSet : assetSets) {
                // set needs to be loaded.
                assetSet.loadFromFiles(getILogger());
                merger.addDataSet(assetSet);
            }

            // get the merged set and write it down.
            MergedAssetWriter writer = new MergedAssetWriter(destinationDir);

            merger.mergeData(writer, false /*doCleanUp*/);

            // No exception? Write the known state.
            merger.writeBlobTo(getIncrementalFolder(), writer, false);
        } catch (MergingException e) {
            getLogger().error("Could not merge source set folders: ", e);
            merger.cleanBlob(getIncrementalFolder());
            throw new ResourceException(e.getMessage(), e);
        }
    }

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) throws IOException {
        // create a merger and load the known state.
        AssetMerger merger = new AssetMerger();
        try {
            if (!merger.loadFromBlob(getIncrementalFolder(), true /*incrementalState*/)) {
                doFullTaskAction();
                return;
            }

            // compare the known state to the current sets to detect incompatibility.
            // This is in case there's a change that's too hard to do incrementally. In this case
            // we'll simply revert to full build.
            List<AssetSet> assetSets = computeAssetSetList();

            if (!merger.checkValidUpdate(assetSets)) {
                getLogger().info("Changed Asset sets: full task run!");
                doFullTaskAction();
                return;

            }

            // The incremental process is the following:
            // Loop on all the changed files, find which ResourceSet it belongs to, then ask
            // the resource set to update itself with the new file.
            for (Map.Entry<File, FileStatus> entry : changedInputs.entrySet()) {
                File changedFile = entry.getKey();

                // Ignore directories.
                if (changedFile.isDirectory()) {
                    continue;
                }

                merger.findDataSetContaining(changedFile, fileValidity);
                if (fileValidity.getStatus() == FileValidity.FileStatus.UNKNOWN_FILE) {
                    doFullTaskAction();
                    return;

                } else if (fileValidity.getStatus() == FileValidity.FileStatus.VALID_FILE) {
                    if (!fileValidity.getDataSet().updateWith(
                            fileValidity.getSourceFile(),
                            changedFile,
                            entry.getValue(),
                            getILogger())) {
                        getLogger().info(
                                "Failed to process {} event! Full task run", entry.getValue());
                        doFullTaskAction();
                        return;
                    }
                }
            }

            MergedAssetWriter writer = new MergedAssetWriter(getOutputDir());

            merger.mergeData(writer, false /*doCleanUp*/);

            // No exception? Write the known state.
            merger.writeBlobTo(getIncrementalFolder(), writer, false);
        } catch (MergingException e) {
            getLogger().error("Could not merge source set folders: ", e);
            merger.cleanBlob(getIncrementalFolder());
            throw new ResourceException(e.getMessage(), e);
        } finally {
            // some clean up after the task to help multi variant/module builds.
            fileValidity.clear();
        }
    }

    @SuppressWarnings("unused")
    @InputFiles
    @Optional
    public FileCollection getLibraries() {
        return libraries;
    }

    @SuppressWarnings("unused")
    @InputFiles
    @Optional
    public FileCollection getShadersOutputDir() {
        return shadersOutputDir;
    }

    @VisibleForTesting
    void setShadersOutputDir(FileCollection shadersOutputDir) {
        this.shadersOutputDir = shadersOutputDir;
    }

    @SuppressWarnings("unused")
    @InputFiles
    @Optional
    public FileCollection getCopyApk() {
        return copyApk;
    }

    @VisibleForTesting
    void setCopyApk(FileCollection copyApk) {
        this.copyApk = copyApk;
    }

    @SuppressWarnings("unused")
    @Input
    @Optional
    public String getIgnoreAssets() {
        return ignoreAssets;
    }

    @VisibleForTesting
    void setIgnoreAssets(String ignoreAssets) {
        this.ignoreAssets = ignoreAssets;
    }

    @VisibleForTesting
    void setAssetSetSupplier(InputSupplier<List<AssetSet>> assetSetSupplier) {
        this.assetSetSupplier = assetSetSupplier;
    }

    @VisibleForTesting
    void setDependencySetSupplier(InputSupplier<List<AssetSet>> dependencySetSupplier) {
        this.dependencySetSupplier = dependencySetSupplier;
    }

    // input list for the source folder based asset folders.
    @SuppressWarnings("unused")
    @InputFiles
    public Set<File> getSourceFolderInputs() {
        List<AssetSet> sets = assetSetSupplier.get();
        // collect the files.
        Set<File> assetSetFolders = Sets.newHashSetWithExpectedSize(sets.size());
        for (AssetSet assetSet : sets) {
            assetSetFolders.addAll(assetSet.getSourceFiles());
        }

        return assetSetFolders;
    }

    @SuppressWarnings("unused")
    @InputFiles
    @Optional
    public Set<File> getDependencyInputs() {
        if (dependencySetSupplier != null) {
            List<AssetSet> list = dependencySetSupplier.get();
            Set<File> files = Sets.newHashSetWithExpectedSize(list.size());
            for (AssetSet assetSet : list) {
                files.addAll(assetSet.getSourceFiles());
            }
            return files;
        }

        return ImmutableSet.of();
    }

    /**
     * Compute the list of Asset set to be used during execution based all the inputs.
     */
    @VisibleForTesting
    List<AssetSet> computeAssetSetList() {
        List<AssetSet> assetSets = assetSetSupplier.getLastValue();

        if (copyApk == null
                && shadersOutputDir == null
                && ignoreAssets == null
                && dependencySetSupplier == null) {
            return assetSets;
        }

        List<AssetSet> sets = Lists.newArrayList();

        // get the dependency base assets sets.
        if (dependencySetSupplier != null) {
            // get the dependencies in first.
            sets.addAll(dependencySetSupplier.getLastValue());
        }

        // add the generated folders to the first set of the folder-based sets.
        List<File> generatedAssetFolders = Lists.newArrayList();

        if (shadersOutputDir != null) {
            generatedAssetFolders.addAll(shadersOutputDir.getFiles());
        }

        if (copyApk != null) {
            generatedAssetFolders.addAll(copyApk.getFiles());
        }

        // add the generated files to the main set.
        final AssetSet mainAssetSet = assetSets.get(0);
        assert mainAssetSet.getConfigName().equals(BuilderConstants.MAIN);
        mainAssetSet.addSources(generatedAssetFolders);

        sets.addAll(assetSets);

        if (ignoreAssets != null) {
            for (AssetSet set : sets) {
                set.setIgnoredPatterns(ignoreAssets);
            }
        }

        return sets;
    }


    protected abstract static class ConfigAction implements TaskConfigAction<MergeSourceSetFolders> {
        @NonNull
        protected final VariantScope scope;

        protected ConfigAction(@NonNull VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public Class<MergeSourceSetFolders> getType() {
            return MergeSourceSetFolders.class;
        }

        @Override
        public void execute(@NonNull MergeSourceSetFolders mergeAssetsTask) {
            BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
            VariantConfiguration variantConfig = variantData.getVariantConfiguration();

            mergeAssetsTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            mergeAssetsTask.setVariantName(variantConfig.getFullName());
            mergeAssetsTask.setIncrementalFolder(scope.getIncrementalDir(getName()));
        }
    }

    public static class MergeAssetConfigAction extends ConfigAction {

        public MergeAssetConfigAction(@NonNull VariantScope scope) {
            super(scope);
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("merge", "Assets");
        }

        @Override
        public void execute(@NonNull MergeSourceSetFolders mergeAssetsTask) {
            super.execute(mergeAssetsTask);
            final BaseVariantData<? extends BaseVariantOutputData> variantData =
                    scope.getVariantData();
            final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
            final Project project = scope.getGlobalScope().getProject();

            variantData.mergeAssetsTask = mergeAssetsTask;

            mergeAssetsTask.assetSetSupplier = InputSupplier.from(variantConfig::getAssetSets);

            mergeAssetsTask.shadersOutputDir = project.files(scope.getShadersOutputDir());
            if (variantData.copyApkTask != null) {
                mergeAssetsTask.copyApk = project.files(variantData.copyApkTask.getDestinationDir());
            }

            AaptOptions options = scope.getGlobalScope().getExtension().getAaptOptions();
            if (options != null) {
                mergeAssetsTask.ignoreAssets = options.getIgnoreAssets();
            }

            if (!variantConfig.getType().equals(VariantType.LIBRARY)) {
                mergeAssetsTask.dependencySetSupplier = InputSupplier.from(
                        variantConfig::getAssetSetsForDependencies);
            }

            mergeAssetsTask.setOutputDir(scope.getMergeAssetsOutputDir());
        }
    }

    public static class MergeJniLibFoldersConfigAction extends ConfigAction {

        public MergeJniLibFoldersConfigAction(@NonNull VariantScope scope) {
            super(scope);
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("merge", "JniLibFolders");
        }

        @Override
        public void execute(@NonNull MergeSourceSetFolders mergeAssetsTask) {
            super.execute(mergeAssetsTask);
            BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
            final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();

            mergeAssetsTask.assetSetSupplier = InputSupplier.from(variantConfig::getJniLibsSets);
            mergeAssetsTask.setOutputDir(scope.getMergeNativeLibsOutputDir());
        }
    }

    public static class MergeShaderSourceFoldersConfigAction extends ConfigAction {

        public MergeShaderSourceFoldersConfigAction(@NonNull VariantScope scope) {
            super(scope);
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("merge", "Shaders");
        }

        @Override
        public void execute(@NonNull MergeSourceSetFolders mergeAssetsTask) {
            super.execute(mergeAssetsTask);
            BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
            final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();

            mergeAssetsTask.assetSetSupplier = InputSupplier.from(variantConfig::getShaderSets);
            mergeAssetsTask.setOutputDir(scope.getMergeShadersOutputDir());
        }
    }
}

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
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.aapt.AaptGradleFactory;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.VectorDrawablesOptions;
import com.android.builder.png.VectorDrawableRenderer;
import com.android.ide.common.res2.FileStatus;
import com.android.ide.common.res2.FileValidity;
import com.android.ide.common.res2.GeneratedResourceSet;
import com.android.ide.common.res2.MergedResourceWriter;
import com.android.ide.common.res2.MergingException;
import com.android.ide.common.res2.NoOpResourcePreprocessor;
import com.android.ide.common.res2.QueueableResourceCompiler;
import com.android.ide.common.res2.ResourceMerger;
import com.android.ide.common.res2.ResourcePreprocessor;
import com.android.ide.common.res2.ResourceSet;
import com.android.ide.common.vectordrawable.ResourcesNotSupportedException;
import com.android.resources.Density;
import com.android.utils.FileUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.ParallelizableTask;

@ParallelizableTask
public class MergeResources extends IncrementalTask {

    // ----- PUBLIC TASK API -----

    /**
     * Directory to write the merged resources to
     */
    private File outputDir;

    private File generatedPngsOutputDir;

    // ----- PRIVATE TASK API -----

    /**
     * Optional file to write any publicly imported resource types and names to
     */
    private File publicFile;

    private boolean processResources;

    private boolean crunchPng;

    private boolean validateEnabled;

    private File blameLogFolder;

    private Supplier<List<ResourceSet>> dependencySetSupplier;
    private Supplier<Set<File>> dependencyFileSupplier;
    // actual inputs
    private InputSupplier<List<ResourceSet>> sourceFolderInputs;
    private FileCollection renderscriptResOutputDir;
    private FileCollection generatedResOutputDir;
    private FileCollection microApkResDirectory;
    private Supplier<FileCollection> extraGeneratedResFolders;

    private final FileValidity<ResourceSet> fileValidity = new FileValidity<>();

    private boolean disableVectorDrawables;

    private Collection<String> generatedDensities;

    private int minSdk;

    private VariantScope variantScope;

    @Input
    public String getBuildToolsVersion() {
        return getBuildTools().getRevision().toString();
    }

    @Override
    protected boolean isIncremental() {
        return true;
    }

    @Override
    protected void doFullTaskAction() throws IOException {
        ResourcePreprocessor preprocessor = getPreprocessor();

        // this is full run, clean the previous output
        File destinationDir = getOutputDir();
        FileUtils.cleanOutputDir(destinationDir);

        List<ResourceSet> resourceSets = getConfiguredResourceSets(preprocessor);

        // create a new merger and populate it with the sets.
        ResourceMerger merger = new ResourceMerger(minSdk);

        try {
            for (ResourceSet resourceSet : resourceSets) {
                resourceSet.loadFromFiles(getILogger());
                merger.addDataSet(resourceSet);
            }

            // get the merged set and write it down.
            QueueableResourceCompiler resourceCompiler;
            if (getProcessResources()) {
                resourceCompiler = AaptGradleFactory.make(
                        getBuilder(),
                        getCrunchPng(),
                        variantScope,
                        getAaptTempDir());
            } else {
                resourceCompiler = QueueableResourceCompiler.NONE;
            }
            MergedResourceWriter writer = new MergedResourceWriter(
                    destinationDir,
                    getPublicFile(),
                    getBlameLogFolder(),
                    preprocessor,
                    resourceCompiler,
                    getIncrementalFolder());

            merger.mergeData(writer, false /*doCleanUp*/);

            // No exception? Write the known state.
            merger.writeBlobTo(getIncrementalFolder(), writer, false);
        } catch (MergingException e) {
            System.out.println(e.getMessage());
            merger.cleanBlob(getIncrementalFolder());
            throw new ResourceException(e.getMessage(), e);
        }
    }

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) throws IOException {
        ResourcePreprocessor preprocessor = getPreprocessor();

        // create a merger and load the known state.
        ResourceMerger merger = new ResourceMerger(minSdk);
        try {
            if (!merger.loadFromBlob(getIncrementalFolder(), true /*incrementalState*/)) {
                doFullTaskAction();
                return;
            }

            for (ResourceSet resourceSet : merger.getDataSets()) {
                resourceSet.setPreprocessor(preprocessor);
            }

            List<ResourceSet> resourceSets = getConfiguredResourceSets(preprocessor);

            // compare the known state to the current sets to detect incompatibility.
            // This is in case there's a change that's too hard to do incrementally. In this case
            // we'll simply revert to full build.
            if (!merger.checkValidUpdate(resourceSets)) {
                getLogger().info("Changed Resource sets: full task run!");
                doFullTaskAction();
                return;
            }

            // The incremental process is the following:
            // Loop on all the changed files, find which ResourceSet it belongs to, then ask
            // the resource set to update itself with the new file.
            for (Map.Entry<File, FileStatus> entry : changedInputs.entrySet()) {
                File changedFile = entry.getKey();

                merger.findDataSetContaining(changedFile, fileValidity);
                if (fileValidity.getStatus() == FileValidity.FileStatus.UNKNOWN_FILE) {
                    doFullTaskAction();
                    return;
                } else if (fileValidity.getStatus() == FileValidity.FileStatus.VALID_FILE) {
                    if (!fileValidity.getDataSet().updateWith(
                            fileValidity.getSourceFile(), changedFile, entry.getValue(),
                            getILogger())) {
                        getLogger().info(
                                String.format("Failed to process %s event! Full task run",
                                        entry.getValue()));
                        doFullTaskAction();
                        return;
                    }
                }
            }


            QueueableResourceCompiler resourceCompiler;
            if (getProcessResources()) {
                resourceCompiler = AaptGradleFactory.make(
                        getBuilder(),
                        getCrunchPng(),
                        variantScope,
                        getAaptTempDir());
            } else {
                resourceCompiler = QueueableResourceCompiler.NONE;
            }

            MergedResourceWriter writer = new MergedResourceWriter(
                    getOutputDir(),
                    getPublicFile(),
                    getBlameLogFolder(),
                    preprocessor,
                    resourceCompiler,
                    getIncrementalFolder());
            merger.mergeData(writer, false /*doCleanUp*/);
            // No exception? Write the known state.
            merger.writeBlobTo(getIncrementalFolder(), writer, false);
        } catch (MergingException e) {
            merger.cleanBlob(getIncrementalFolder());
            throw new ResourceException(e.getMessage(), e);
        } finally {
            // some clean up after the task to help multi variant/module builds.
            fileValidity.clear();
        }
    }

    @NonNull
    private ResourcePreprocessor getPreprocessor() {
        // Only one pre-processor for now. The code will need slight changes when we add more.

        if (isDisableVectorDrawables()) {
            // If the user doesn't want any PNGs, leave the XML file alone as well.
            return NoOpResourcePreprocessor.INSTANCE;
        }

        Collection<Density> densities =
                getGeneratedDensities().stream().map(Density::getEnum).collect(Collectors.toList());

        return new VectorDrawableRenderer(
                getMinSdk(), getGeneratedPngsOutputDir(), densities, getILogger()) {
            @Override
            public void generateFile(File toBeGenerated, File original) throws IOException {
                try {
                    super.generateFile(toBeGenerated, original);
                } catch (ResourcesNotSupportedException e) {
                    // Add gradle-specific error message.
                    throw new GradleException(
                            String.format(
                                    "Can't process attribute %1$s=\"%2$s\": "
                                            + "references to other resources are not supported by "
                                            + "build-time PNG generation. "
                                            + "See http://developer.android.com/tools/help/vector-asset-studio.html "
                                            + "for details.",
                                    e.getName(), e.getValue()));
                }
            }
        };
    }

    @NonNull
    private List<ResourceSet> getConfiguredResourceSets(ResourcePreprocessor preprocessor) {
        List<ResourceSet> resourceSets = computeResourceSetList();
        List<ResourceSet> generatedSets = Lists.newArrayListWithCapacity(resourceSets.size());

        for (ResourceSet resourceSet : resourceSets) {
            resourceSet.setPreprocessor(preprocessor);
            ResourceSet generatedSet = new GeneratedResourceSet(resourceSet);
            resourceSet.setGeneratedSet(generatedSet);
            generatedSets.add(generatedSet);
        }

        // Put all generated sets at the start of the list.
        resourceSets.addAll(0, generatedSets);
        return resourceSets;
    }

    @InputFiles
    public FileCollection getRenderscriptResOutputDir() {
        return renderscriptResOutputDir;
    }

    @VisibleForTesting
    void setRenderscriptResOutputDir(FileCollection renderscriptResOutputDir) {
        this.renderscriptResOutputDir = renderscriptResOutputDir;
    }

    @InputFiles
    public FileCollection getGeneratedResOutputDir() {
        return generatedResOutputDir;
    }

    @VisibleForTesting
    void setGeneratedResOutputDir(FileCollection generatedResOutputDir) {
        this.generatedResOutputDir = generatedResOutputDir;
    }

    @InputFiles
    @Optional
    public FileCollection getMicroApkResDirectory() {
        return microApkResDirectory;
    }

    @VisibleForTesting
    void setMicroApkResDirectory(FileCollection microApkResDirectory) {
        this.microApkResDirectory = microApkResDirectory;
    }

    @InputFiles
    @Optional
    public FileCollection getExtraGeneratedResFolders() {
        return extraGeneratedResFolders != null ? extraGeneratedResFolders.get() : null;
    }

    @VisibleForTesting
    void setExtraGeneratedResFolders(Supplier<FileCollection> extraGeneratedResFolders) {
        this.extraGeneratedResFolders = extraGeneratedResFolders;
    }

    @InputFiles
    public Set<File> getSourceFolderInputs() {
        List<ResourceSet> inputs = sourceFolderInputs.get();
        Set<File> files = Sets.newHashSetWithExpectedSize(inputs.size());

        for (ResourceSet resourceSet : inputs) {
            files.addAll(resourceSet.getSourceFiles());
        }

        return files;
    }

    @VisibleForTesting
    void setSourceFolderInputs(
            InputSupplier<List<ResourceSet>> sourceFolderInputs) {
        this.sourceFolderInputs = sourceFolderInputs;
    }

    @InputFiles
    @Optional
    public Set<File> getDependencyInputs() {
        if (dependencyFileSupplier != null) {
            return dependencyFileSupplier.get();
        }
        return null;
    }

    @OutputDirectory
    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }

    public boolean getCrunchPng() {
        return crunchPng;
    }

    public void setCrunchPng(boolean crunchPng) {
        this.crunchPng = crunchPng;
    }

    public boolean getProcessResources() {
        return processResources;
    }

    public void setProcessResources(boolean processResources) {
        this.processResources = processResources;
    }

    @Optional
    @OutputFile
    public File getPublicFile() {
        return publicFile;
    }

    public void setPublicFile(File publicFile) {
        this.publicFile = publicFile;
    }

    // Synthetic input: the validation flag is set on the resource sets in ConfigAction.execute.
    @Input
    public boolean isValidateEnabled() {
        return validateEnabled;
    }

    public void setValidateEnabled(boolean validateEnabled) {
        this.validateEnabled = validateEnabled;
    }

    @OutputDirectory
    @Optional
    public File getBlameLogFolder() {
        return blameLogFolder;
    }

    public void setBlameLogFolder(File blameLogFolder) {
        this.blameLogFolder = blameLogFolder;
    }

    public File getGeneratedPngsOutputDir() {
        return generatedPngsOutputDir;
    }

    public void setGeneratedPngsOutputDir(File generatedPngsOutputDir) {
        this.generatedPngsOutputDir = generatedPngsOutputDir;
    }

    @Input
    public Collection<String> getGeneratedDensities() {
        return generatedDensities;
    }

    @Input
    public int getMinSdk() {
        return minSdk;
    }

    public void setMinSdk(int minSdk) {
        this.minSdk = minSdk;
    }

    public void setGeneratedDensities(Collection<String> generatedDensities) {
        this.generatedDensities = generatedDensities;
    }

    @Input
    public boolean isDisableVectorDrawables() {
        return disableVectorDrawables;
    }

    public void setDisableVectorDrawables(boolean disableVectorDrawables) {
        this.disableVectorDrawables = disableVectorDrawables;
    }

    @VisibleForTesting
    void setDependencySetSupplier(Supplier<List<ResourceSet>> dependencySetSupplier) {
        this.dependencySetSupplier = dependencySetSupplier;
    }

    @VisibleForTesting
    void setDependencyFileSupplier(Supplier<Set<File>> dependencyFileSupplier) {
        this.dependencyFileSupplier = dependencyFileSupplier;
    }

    /**
     * Compute the list of resource set to be used during execution based all the inputs.
     */
    @VisibleForTesting
    @NonNull
    List<ResourceSet> computeResourceSetList() {
        List<ResourceSet> resourceSets = Lists.newArrayList();
        // get the dependencies first
        if (dependencySetSupplier != null) {
            resourceSets.addAll(dependencySetSupplier.get());
        }

        // add the folder based next
        List<ResourceSet> sourceFolderSets = sourceFolderInputs.get();
        resourceSets.addAll(sourceFolderSets);

        // We add the generated folders to the main set
        List<File> generatedResFolders = Lists.newArrayList();

        generatedResFolders.addAll(renderscriptResOutputDir.getFiles());
        generatedResFolders.addAll(generatedResOutputDir.getFiles());

        FileCollection extraFolders = getExtraGeneratedResFolders();
        if (extraFolders != null) {
            generatedResFolders.addAll(extraFolders.getFiles());
        }
        if (microApkResDirectory != null) {
            generatedResFolders.addAll(microApkResDirectory.getFiles());
        }

        // add the generated files to the main set.
        final ResourceSet mainResourceSet = sourceFolderSets.get(0);
        assert mainResourceSet.getConfigName().equals(BuilderConstants.MAIN);
        mainResourceSet.addSources(generatedResFolders);

        return resourceSets;
    }

    /**
     * Obtains the temporary directory for {@code aapt} to use.
     *
     * @return the temporary directory
     */
    @NonNull
    private File getAaptTempDir() {
        return FileUtils.mkdirs(new File(getIncrementalFolder(), "aapt-temp"));
    }

    public static class ConfigAction implements TaskConfigAction<MergeResources> {

        @NonNull
        private final VariantScope scope;

        @NonNull
        private final String taskNamePrefix;

        @Nullable
        private final File outputLocation;

        private final boolean includeDependencies;

        private final boolean processResources;

        public ConfigAction(
                @NonNull VariantScope scope,
                @NonNull String taskNamePrefix,
                @Nullable File outputLocation,
                boolean includeDependencies,
                boolean processResources) {
            this.scope = scope;
            this.taskNamePrefix = taskNamePrefix;
            this.outputLocation = outputLocation;
            this.includeDependencies = includeDependencies;
            this.processResources = processResources;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName(taskNamePrefix, "Resources");
        }

        @NonNull
        @Override
        public Class<MergeResources> getType() {
            return MergeResources.class;
        }

        @Override
        public void execute(@NonNull MergeResources mergeResourcesTask) {
            final BaseVariantData<? extends BaseVariantOutputData> variantData =
                    scope.getVariantData();
            final AndroidConfig extension = scope.getGlobalScope().getExtension();
            final Project project = scope.getGlobalScope().getProject();

            mergeResourcesTask.setMinSdk(
                    variantData
                            .getVariantConfiguration()
                            .getResourcesMinSdkVersion()
                            .getApiLevel());

            mergeResourcesTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            mergeResourcesTask.setVariantName(scope.getVariantConfiguration().getFullName());
            mergeResourcesTask.setIncrementalFolder(scope.getIncrementalDir(getName()));
            mergeResourcesTask.variantScope = scope;

            // Libraries use this task twice, once for compilation (with dependencies),
            // where blame is useful, and once for packaging where it is not.
            if (includeDependencies) {
                mergeResourcesTask.setBlameLogFolder(scope.getResourceBlameLogDir());
            }
            mergeResourcesTask.setProcessResources(processResources);
            mergeResourcesTask.setCrunchPng(extension.getAaptOptions().getCruncherEnabled());

            VectorDrawablesOptions vectorDrawablesOptions = variantData
                    .getVariantConfiguration()
                    .getMergedFlavor()
                    .getVectorDrawables();

            Set<String> generatedDensities = vectorDrawablesOptions.getGeneratedDensities();

            mergeResourcesTask.setGeneratedDensities(
                    Objects.firstNonNull(generatedDensities, Collections.<String>emptySet()));

            mergeResourcesTask.setDisableVectorDrawables(
                    vectorDrawablesOptions.getUseSupportLibrary()
                            || mergeResourcesTask.getGeneratedDensities().isEmpty());

            final boolean validateEnabled = AndroidGradleOptions.isResourceValidationEnabled(
                    project);

            mergeResourcesTask.setValidateEnabled(validateEnabled);

            if (includeDependencies) {
                mergeResourcesTask.dependencySetSupplier = InputSupplier.from(() -> variantData
                        .getVariantConfiguration().getResourceSetsForDependencies(validateEnabled));
                mergeResourcesTask.dependencyFileSupplier = InputSupplier.from(() -> variantData
                        .getVariantConfiguration().getResourceFoldersForDependencies());

            }
            mergeResourcesTask.sourceFolderInputs = InputSupplier.from(
                    () -> variantData.getVariantConfiguration().getResourceSets(validateEnabled));
            mergeResourcesTask.extraGeneratedResFolders = InputSupplier.from(
                    variantData::getExtraGeneratedResFolders);
            mergeResourcesTask.renderscriptResOutputDir = project.files(scope.getRenderscriptResOutputDir());
            mergeResourcesTask.generatedResOutputDir = project.files(scope.getGeneratedResOutputDir());
            if (scope.getMicroApkTask() != null &&
                    variantData.getVariantConfiguration().getBuildType().isEmbedMicroApp()) {
                mergeResourcesTask.microApkResDirectory = project.files(scope.getMicroApkResDirectory());
            }

            mergeResourcesTask.setOutputDir(
                    outputLocation != null
                            ? outputLocation
                            : scope.getDefaultMergeResourcesOutputDir());

            mergeResourcesTask.setGeneratedPngsOutputDir(scope.getGeneratedPngsOutputDir());

            variantData.mergeResourcesTask = mergeResourcesTask;
        }
    }
}

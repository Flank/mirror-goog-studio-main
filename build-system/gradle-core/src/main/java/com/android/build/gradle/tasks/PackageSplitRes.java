/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.core.VariantConfiguration;
import com.android.build.gradle.internal.packaging.IncrementalPackagerBuilder;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidBuilderTask;
import com.android.build.gradle.internal.tasks.SigningConfigMetadata;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.files.IncrementalRelativeFileSets;
import com.android.builder.internal.packaging.IncrementalPackager;
import com.android.ide.common.build.ApkInfo;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;

/** Package each split resources into a specific signed apk file. */
public class PackageSplitRes extends AndroidBuilderTask {

    private FileCollection signingConfig;
    private File incrementalDir;
    public BuildableArtifact processedResources;
    public File splitResApkOutputDirectory;

    @InputFiles
    public BuildableArtifact getProcessedResources() {
        return processedResources;
    }

    @OutputDirectory
    public File getSplitResApkOutputDirectory() {
        return splitResApkOutputDirectory;
    }

    @InputFiles
    public FileCollection getSigningConfig() {
        return signingConfig;
    }

    @TaskAction
    protected void doFullTaskAction() {

        ExistingBuildElements.from(
                        InternalArtifactType.DENSITY_OR_LANGUAGE_SPLIT_PROCESSED_RES,
                        processedResources)
                .transform(
                        (split, output) -> {
                            if (output == null) {
                                throw new RuntimeException(
                                        "Cannot find processed resources for " + split);
                            }
                            File outFile =
                                    new File(
                                            splitResApkOutputDirectory,
                                            PackageSplitRes.this.getOutputFileNameForSplit(
                                                    split, signingConfig != null));
                            File intDir =
                                    new File(
                                            incrementalDir,
                                            FileUtils.join(split.getFilterName(), "tmp"));
                            try {
                                FileUtils.cleanOutputDir(intDir);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }

                            try (IncrementalPackager pkg =
                                    new IncrementalPackagerBuilder(
                                                    IncrementalPackagerBuilder.ApkFormat.FILE)
                                            .withSigning(
                                                    SigningConfigMetadata.Companion.load(
                                                            signingConfig))
                                            .withOutputFile(outFile)
                                            .withProject(PackageSplitRes.this.getProject())
                                            .withIntermediateDir(intDir)
                                            .build()) {
                                pkg.updateAndroidResources(
                                        IncrementalRelativeFileSets.fromZip(output));
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                            return outFile;
                        })
                .into(
                        InternalArtifactType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT,
                        splitResApkOutputDirectory);
    }

    public String getOutputFileNameForSplit(final ApkInfo apkData, boolean isSigned) {
        String archivesBaseName = (String) getProject().getProperties().get("archivesBaseName");
        String apkName = archivesBaseName + "-" + apkData.getBaseName();
        return apkName + (isSigned ? "" : "-unsigned") + SdkConstants.DOT_ANDROID_PACKAGE;
    }

    // ----- CreationAction -----

    public static class CreationAction extends VariantTaskCreationAction<PackageSplitRes> {

        private File splitResApkOutputDirectory;

        public CreationAction(VariantScope scope) {
            super(scope);
        }

        @Override
        @NonNull
        public String getName() {
            return getVariantScope().getTaskName("package", "SplitResources");
        }

        @Override
        @NonNull
        public Class<PackageSplitRes> getType() {
            return PackageSplitRes.class;
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);
            splitResApkOutputDirectory =
                    getVariantScope()
                            .getArtifacts()
                            .appendArtifact(
                                    InternalArtifactType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT,
                                    taskName,
                                    "out");
        }

        @Override
        public void handleProvider(@NonNull TaskProvider<? extends PackageSplitRes> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope().getTaskContainer().setPackageSplitResourcesTask(taskProvider);
        }

        @Override
        public void configure(@NonNull PackageSplitRes task) {
            super.configure(task);
            VariantScope scope = getVariantScope();

            BaseVariantData variantData = scope.getVariantData();
            final VariantConfiguration config = variantData.getVariantConfiguration();

            task.processedResources =
                    scope.getArtifacts().getFinalArtifactFiles(InternalArtifactType.PROCESSED_RES);
            task.signingConfig = scope.getSigningConfigFileCollection();
            task.splitResApkOutputDirectory = splitResApkOutputDirectory;
            task.incrementalDir = scope.getIncrementalDir(getName());
        }
    }
}

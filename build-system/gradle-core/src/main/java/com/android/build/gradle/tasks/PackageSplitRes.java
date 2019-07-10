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
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.packaging.IncrementalPackagerBuilder;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.BuildElementsTransformParams;
import com.android.build.gradle.internal.scope.BuildElementsTransformRunnable;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.NonIncrementalTask;
import com.android.build.gradle.internal.tasks.SigningConfigUtils;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.files.IncrementalRelativeFileSets;
import com.android.builder.internal.packaging.ApkCreatorType;
import com.android.builder.internal.packaging.IncrementalPackager;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.inject.Inject;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskProvider;

/** Package each split resources into a specific signed apk file. */
public abstract class PackageSplitRes extends NonIncrementalTask {

    private FileCollection signingConfig;
    private File incrementalDir;
    private boolean keepTimestampsInApk;

    @InputFiles
    public abstract DirectoryProperty getProcessedResources();

    @OutputDirectory
    public abstract DirectoryProperty getSplitResApkOutputDirectory();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getSigningConfig() {
        return signingConfig;
    }

    @Input
    public boolean getKeepTimestampsInApk() {
        return keepTimestampsInApk;
    }

    @Override
    protected void doTaskAction() {
        ExistingBuildElements.from(
                        InternalArtifactType.DENSITY_OR_LANGUAGE_SPLIT_PROCESSED_RES,
                        getProcessedResources())
                .transform(
                        getWorkerFacadeWithWorkers(),
                        PackageSplitResTransformRunnable.class,
                        ((apkInfo, file) ->
                                new PackageSplitResTransformParams(apkInfo, file, this)))
                .into(
                        InternalArtifactType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT,
                        getSplitResApkOutputDirectory().get().getAsFile());
    }

    private static class PackageSplitResTransformRunnable extends BuildElementsTransformRunnable {

        @Inject
        public PackageSplitResTransformRunnable(@NonNull PackageSplitResTransformParams params) {
            super(params);
        }

        @Override
        public void run() {
            PackageSplitResTransformParams params = (PackageSplitResTransformParams) getParams();
            File intDir =
                    new File(
                            params.incrementalDir,
                            FileUtils.join(params.apkInfo.getFilterName(), "tmp"));
            try {
                FileUtils.cleanOutputDir(intDir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            try (IncrementalPackager pkg =
                    new IncrementalPackagerBuilder(IncrementalPackagerBuilder.ApkFormat.FILE)
                            .withSigning(
                                    SigningConfigUtils.Companion.load(params.signingConfigFile))
                            .withOutputFile(params.output)
                            .withKeepTimestampsInApk(params.keepTimestampsInApk)
                            .withIntermediateDir(intDir)
                            .withApkCreatorType(ApkCreatorType.APK_Z_FILE_CREATOR)
                            .withChangedAndroidResources(
                                    IncrementalRelativeFileSets.fromZip(params.input))
                            .build()) {
                pkg.updateFiles();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static class PackageSplitResTransformParams extends BuildElementsTransformParams {
        private final ApkData apkInfo;
        private final File input;
        private final File output;
        private final File incrementalDir;
        private final File signingConfigFile;
        private final boolean keepTimestampsInApk;

        PackageSplitResTransformParams(ApkData apkInfo, File input, PackageSplitRes task) {
            if (input == null) {
                throw new RuntimeException("Cannot find processed resources for " + apkInfo);
            }
            this.apkInfo = apkInfo;
            this.input = input;
            output =
                    new File(
                            task.getSplitResApkOutputDirectory().get().getAsFile(),
                            getOutputFileNameForSplit(
                                    apkInfo,
                                    (String)
                                            task.getProject()
                                                    .getProperties()
                                                    .get("archivesBaseName"),
                                    task.signingConfig != null));
            incrementalDir = task.incrementalDir;
            signingConfigFile = SigningConfigUtils.Companion.getOutputFile(task.getSigningConfig());
            keepTimestampsInApk = task.getKeepTimestampsInApk();
        }

        @Nullable
        @Override
        public File getOutput() {
            return output;
        }
    }

    public static String getOutputFileNameForSplit(
            final ApkData apkData, String archivesBaseName, boolean isSigned) {
        String apkName = archivesBaseName + "-" + apkData.getBaseName();
        return apkName + (isSigned ? "" : "-unsigned") + SdkConstants.DOT_ANDROID_PACKAGE;
    }

    // ----- CreationAction -----

    public static class CreationAction extends VariantTaskCreationAction<PackageSplitRes> {

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
        public void handleProvider(@NonNull TaskProvider<? extends PackageSplitRes> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope().getTaskContainer().setPackageSplitResourcesTask(taskProvider);
            getVariantScope()
                    .getArtifacts()
                    .producesDir(
                            InternalArtifactType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT,
                            BuildArtifactsHolder.OperationType.INITIAL,
                            taskProvider,
                            PackageSplitRes::getSplitResApkOutputDirectory,
                            "out");
        }

        @Override
        public void configure(@NonNull PackageSplitRes task) {
            super.configure(task);
            VariantScope scope = getVariantScope();

            scope.getArtifacts()
                    .setTaskInputToFinalProduct(
                            InternalArtifactType.PROCESSED_RES, task.getProcessedResources());
            task.signingConfig = scope.getSigningConfigFileCollection();
            task.incrementalDir = scope.getIncrementalDir(getName());
            task.keepTimestampsInApk =
                    scope.getGlobalScope()
                            .getProjectOptions()
                            .get(BooleanOption.KEEP_TIMESTAMPS_IN_APK);
        }
    }
}

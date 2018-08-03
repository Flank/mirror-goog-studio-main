/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.annotations.VisibleForTesting;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.packaging.ApkCreatorFactories;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidBuilderTask;
import com.android.build.gradle.internal.tasks.factory.EagerTaskCreationAction;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.packaging.PackagerException;
import com.android.ide.common.build.ApkInfo;
import com.android.ide.common.signing.KeytoolException;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.tooling.BuildException;

/**
 * Task for create a split apk per packaged resources.
 *
 * <p>Right now, there is only one packaged resources file in InstantRun mode, but we could decide
 * to slice the resources in the future.
 */
public class InstantRunResourcesApkBuilder extends AndroidBuilderTask {

    @VisibleForTesting public static final String APK_FILE_NAME = "resources";

    private AndroidBuilder androidBuilder;
    private InstantRunBuildContext instantRunBuildContext;
    private File outputDirectory;
    private CoreSigningConfig signingConf;
    private File supportDirectory;

    private BuildableArtifact resources;

    private InternalArtifactType resInputType;

    @Nested
    @Optional
    public CoreSigningConfig getSigningConf() {
        return signingConf;
    }

    @Input
    public String getResInputType() {
        return resInputType.name();
    }

    @Input
    public String getPatchingPolicy() {
        return instantRunBuildContext.getPatchingPolicy().name();
    }

    @InputFiles
    public BuildableArtifact getResourcesFile() {
        return resources;
    }

    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    @TaskAction
    protected void doFullTaskAction() {

        if (instantRunBuildContext.getPatchingPolicy()
                != InstantRunPatchingPolicy.MULTI_APK_SEPARATE_RESOURCES) {
            // when not packaging resources in a separate APK, delete the output APK file so
            // that if we switch back to this mode later on, we ensure that the APK is rebuilt
            // and re-added to the build context and therefore the build-info.xml
            getResInputBuildArtifacts()
                    .forEach(
                            buildOutput -> {
                                ApkInfo apkInfo = buildOutput.getApkInfo();
                                final File outputFile =
                                        new File(
                                                outputDirectory,
                                                mangleApkName(apkInfo)
                                                        + SdkConstants.DOT_ANDROID_PACKAGE);
                                try {
                                    FileUtils.deleteIfExists(outputFile);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
            return;
        }
        getResInputBuildArtifacts()
                .transform(
                        (apkData, input) -> {
                            try {
                                if (input == null) {
                                    return null;
                                }
                                final File outputFile =
                                        new File(
                                                outputDirectory,
                                                mangleApkName(apkData)
                                                        + SdkConstants.DOT_ANDROID_PACKAGE);
                                FileUtils.deleteIfExists(outputFile);
                                Files.createParentDirs(outputFile);

                                // packageCodeSplitApk uses a temporary directory for incremental runs.
                                // Since we don't
                                // do incremental builds here, make sure it gets an empty directory.
                                File tempDir =
                                        new File(
                                                supportDirectory,
                                                "package_" + mangleApkName(apkData));

                                FileUtils.cleanOutputDir(tempDir);

                                androidBuilder.packageCodeSplitApk(
                                        input,
                                        ImmutableSet.of(),
                                        signingConf,
                                        outputFile,
                                        tempDir,
                                        ApkCreatorFactories.fromProjectProperties(
                                                getProject(), true));
                                instantRunBuildContext.addChangedFile(FileType.SPLIT, outputFile);
                                return outputFile;

                            } catch (IOException | KeytoolException | PackagerException e) {
                                throw new BuildException(
                                        "Exception while creating resources split APK", e);
                            }
                        })
                .into(InternalArtifactType.INSTANT_RUN_PACKAGED_RESOURCES, outputDirectory);
    }

    @VisibleForTesting
    protected BuildElements getResInputBuildArtifacts() {
        return ExistingBuildElements.from(resInputType, resources);
    }

    static String mangleApkName(ApkInfo apkData) {
        return APK_FILE_NAME + "-" + apkData.getBaseName();
    }

    public static class CreationAction
            extends EagerTaskCreationAction<InstantRunResourcesApkBuilder> {

        protected final VariantScope variantScope;
        private final InternalArtifactType resInputType;

        public CreationAction(
                @NonNull InternalArtifactType resInputType, @NonNull VariantScope scope) {
            this.resInputType = resInputType;
            this.variantScope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("processInstantRun", "ResourcesApk");
        }

        @NonNull
        @Override
        public Class<InstantRunResourcesApkBuilder> getType() {
            return InstantRunResourcesApkBuilder.class;
        }

        @Override
        public void execute(@NonNull InstantRunResourcesApkBuilder resourcesApkBuilder) {
            resourcesApkBuilder.setVariantName(variantScope.getFullVariantName());
            resourcesApkBuilder.resInputType = resInputType;
            resourcesApkBuilder.supportDirectory = variantScope.getIncrementalDir(getName());
            resourcesApkBuilder.androidBuilder = variantScope.getGlobalScope().getAndroidBuilder();
            resourcesApkBuilder.signingConf =
                    variantScope.getVariantConfiguration().getSigningConfig();
            resourcesApkBuilder.instantRunBuildContext = variantScope.getInstantRunBuildContext();
            resourcesApkBuilder.resources =
                    variantScope.getArtifacts().getFinalArtifactFiles(resInputType);
            resourcesApkBuilder.outputDirectory = variantScope.getInstantRunResourceApkFolder();
        }
    }
}

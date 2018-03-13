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

package com.android.build.gradle.internal.transforms;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.internal.aapt.AaptOptions;
import com.android.ide.common.build.ApkInfo;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;

/**
 * Tasks to generate M+ style pure splits APKs with dex files.
 */
public class InstantRunSliceSplitApkBuilder extends InstantRunSplitApkBuilder {

    private final WaitableExecutor executor = WaitableExecutor.useGlobalSharedThreadPool();
    private final boolean runSerially;

    public InstantRunSliceSplitApkBuilder(
            @NonNull Logger logger,
            @NonNull Project project,
            @NonNull InstantRunBuildContext buildContext,
            @NonNull AndroidBuilder androidBuilder,
            @Nullable FileCollection aapt2FromMaven,
            @NonNull String applicationId,
            @Nullable CoreSigningConfig signingConf,
            @NonNull AaptGeneration aaptGeneration,
            @NonNull AaptOptions aaptOptions,
            @NonNull File outputDirectory,
            @NonNull File supportDirectory,
            @Nullable Boolean runAapt2Serially,
            @NonNull FileCollection resources,
            @NonNull FileCollection resourcesWithMainManifest,
            @NonNull FileCollection apkList,
            @NonNull ApkInfo mainApk) {
        super(
                logger,
                project,
                buildContext,
                androidBuilder,
                aapt2FromMaven,
                applicationId,
                signingConf,
                aaptGeneration,
                aaptOptions,
                outputDirectory,
                supportDirectory,
                resources,
                resourcesWithMainManifest,
                apkList,
                mainApk);
        runSerially = runAapt2Serially == null
                ? SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS
                : runAapt2Serially;

    }

    @NonNull
    @Override
    public String getName() {
        return "instantRunSlicesApk";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return ImmutableSet.of(ExtendedContentType.DEX);
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return Sets.immutableEnumSet(QualifiedContent.Scope.PROJECT,
                QualifiedContent.Scope.SUB_PROJECTS);
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws TransformException, InterruptedException, IOException {

        // this will hold the list of split APKs to build.
        List<DexFiles> splitsToBuild = new ArrayList<>();
        if (transformInvocation.isIncremental()) {
            for (TransformInput transformInput : transformInvocation.getInputs()) {
                for (JarInput jarInput : transformInput.getJarInputs()) {
                    logger.error("InstantRunDependenciesApkBuilder received a jar file "
                            + jarInput.getFile().getAbsolutePath());
                }

                for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
                    Map<File, Set<Status>> sliceStatuses =
                            directoryInput
                                    .getChangedFiles()
                                    .entrySet()
                                    .stream()
                                    .collect(
                                            Collectors.groupingBy(
                                                    fileStatus ->
                                                            fileStatus.getKey().getParentFile(),
                                                    Collectors.mapping(
                                                            Map.Entry::getValue,
                                                            Collectors.toSet())));

                    for (Map.Entry<File, Set<Status>> slices : sliceStatuses.entrySet()) {
                        if (slices.getValue().equals(EnumSet.of(Status.REMOVED))) {
                            DexFiles dexFile =
                                    new DexFiles(ImmutableSet.of(), slices.getKey().getName());

                            String outputFileName = dexFile.encodeName() + "_unaligned.apk";
                            FileUtils.deleteIfExists(new File(outputDirectory, outputFileName));
                            outputFileName = dexFile.encodeName() + ".apk";
                            FileUtils.deleteIfExists(new File(outputDirectory, outputFileName));
                            break;
                        } else if (!slices.getValue().equals(EnumSet.of(Status.NOTCHANGED))) {
                            File[] dexFiles = slices.getKey().listFiles();
                            if (dexFiles != null) {
                                try {
                                    splitsToBuild.add(
                                            new DexFiles(dexFiles, directoryInput.getName()));
                                } catch (Exception e) {
                                    throw new TransformException(e);
                                }
                            }
                            break;
                        }
                    }
                }
            }
        } else {
            FileUtils.cleanOutputDir(outputDirectory);
            for (TransformInput transformInput : transformInvocation.getInputs()) {
                for (JarInput jarInput : transformInput.getJarInputs()) {
                    logger.error("InstantRunDependenciesApkBuilder received a jar file "
                            + jarInput.getFile().getAbsolutePath());
                }
                for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
                    File[] files = directoryInput.getFile().listFiles();
                    if (files == null) {
                        continue;
                    }
                    try {
                        splitsToBuild.add(
                                new DexFiles(ImmutableSet.copyOf(files), directoryInput.getName()));
                    } catch (Exception e) {
                        throw new TransformException(e);
                    }
                }
            }
        }

        logger.debug("Invoking aapt2 serially : {} ", runSerially);

        // now build the APKs in parallel
        splitsToBuild.forEach(
                split -> {
                    try {
                        if (runSerially) {
                            generateSplitApk(mainApk, split);
                        } else {
                            executor.execute(() -> generateSplitApk(mainApk, split));
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        if (!runSerially) {
            executor.waitForTasksWithQuickFail(true /* cancelRemaining */);
        }
    }
}

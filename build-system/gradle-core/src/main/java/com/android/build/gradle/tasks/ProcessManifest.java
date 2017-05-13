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
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.scope.SplitScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.variant.TaskContainer;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.ProductFlavor;
import com.android.manifmerger.ManifestMerger2;
import com.android.manifmerger.MergingReport;
import com.android.manifmerger.XmlDocument;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.apache.tools.ant.BuildException;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

/** a Task that only merge a single manifest with its overlays. */
@ParallelizableTask
@CacheableTask
public class ProcessManifest extends ManifestProcessorTask {

    private Supplier<String> minSdkVersion;
    private Supplier<String> targetSdkVersion;
    private Supplier<Integer> maxSdkVersion;

    private VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor>
            variantConfiguration;
    private SplitScope splitScope;

    private File reportFile;

    @Override
    protected void doFullTaskAction() {
        File outputManifestFile = getManifestOutputFile();
        File aaptFriendlyManifestOutputFile = getAaptFriendlyManifestOutputFile();
        MergingReport mergingReport =
                getBuilder()
                        .mergeManifestsForApplication(
                                getMainManifest(),
                                getManifestOverlays(),
                                Collections.emptyList(),
                                getPackageOverride(),
                                getVersionCode(),
                                getVersionName(),
                                getMinSdkVersion(),
                                getTargetSdkVersion(),
                                getMaxSdkVersion(),
                                outputManifestFile.getAbsolutePath(),
                                aaptFriendlyManifestOutputFile.getAbsolutePath(),
                                null /* outInstantRunManifestLocation */,
                                ManifestMerger2.MergeType.LIBRARY,
                                variantConfiguration.getManifestPlaceholders(),
                                Collections.emptyList(),
                                getReportFile());

        XmlDocument mergedXmlDocument =
                mergingReport.getMergedXmlDocument(MergingReport.MergedManifestKind.MERGED);

        ImmutableMap<String, String> properties =
                mergedXmlDocument != null
                        ? ImmutableMap.of(
                                "packageId", mergedXmlDocument.getPackageName(),
                                "split", mergedXmlDocument.getSplitName())
                        : ImmutableMap.of();

        splitScope.addOutputForSplit(
                TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS,
                splitScope.getMainSplit(),
                outputManifestFile,
                properties);
        splitScope.addOutputForSplit(
                TaskOutputHolder.TaskOutputType.AAPT_FRIENDLY_MERGED_MANIFESTS,
                splitScope.getMainSplit(),
                aaptFriendlyManifestOutputFile,
                properties);
        try {
            splitScope.save(
                    TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS, getManifestOutputDirectory());
            splitScope.save(
                    TaskOutputHolder.TaskOutputType.AAPT_FRIENDLY_MERGED_MANIFESTS,
                    getAaptFriendlyManifestOutputDirectory());
        } catch (IOException e) {
            throw new BuildException("Exception while saving build metadata : ", e);
        }
    }

    @NonNull
    @Override
    @Internal
    public File getManifestOutputFile() {
        Preconditions.checkNotNull(splitScope.getMainSplit());
        return FileUtils.join(
                getManifestOutputDirectory(),
                splitScope.getMainSplit().getDirName(),
                SdkConstants.ANDROID_MANIFEST_XML);
    }

    @Nullable
    @Override
    @Internal
    public File getInstantRunManifestOutputFile() {
        return null;
    }

    @Nullable
    @Override
    @Internal
    public File getAaptFriendlyManifestOutputFile() {
        Preconditions.checkNotNull(splitScope.getMainSplit());
        return FileUtils.join(
                getAaptFriendlyManifestOutputDirectory(),
                splitScope.getMainSplit().getDirName(),
                SdkConstants.ANDROID_MANIFEST_XML);
    }

    @Input
    @Optional
    public String getMinSdkVersion() {
        return minSdkVersion.get();
    }

    @Input
    @Optional
    public String getTargetSdkVersion() {
        return targetSdkVersion.get();
    }

    @Input
    @Optional
    public Integer getMaxSdkVersion() {
        return maxSdkVersion.get();
    }

    @Internal
    public VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor>
            getVariantConfiguration() {
        return variantConfiguration;
    }

    public void setVariantConfiguration(
            VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor> variantConfiguration) {
        this.variantConfiguration = variantConfiguration;
    }

    @Input
    @Optional
    public File getReportFile() {
        return reportFile;
    }

    public void setReportFile(File reportFile) {
        this.reportFile = reportFile;
    }

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public File getMainManifest() {
        return variantConfiguration.getMainManifest();
    }

    @Input
    @Optional
    public String getPackageOverride() {
        return variantConfiguration.getApplicationId();
    }

    @Input
    public int getVersionCode() {
        return variantConfiguration.getVersionCode();
    }

    @Input
    @Optional
    public String getVersionName() {
        return variantConfiguration.getVersionName();
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public List<File> getManifestOverlays() {
        return variantConfiguration.getManifestOverlays();
    }

    /**
     * Returns a serialized version of our map of key value pairs for placeholder substitution.
     *
     * This serialized form is only used by gradle to compare past and present tasks to determine
     * whether a task need to be re-run or not.
     */
    @Input
    @Optional
    public String getManifestPlaceholders() {
        return serializeMap(variantConfiguration.getManifestPlaceholders());
    }

    public static class ConfigAction implements TaskConfigAction<ProcessManifest> {

        private VariantScope scope;

        public ConfigAction(VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("process", "Manifest");
        }

        @NonNull
        @Override
        public Class<ProcessManifest> getType() {
            return ProcessManifest.class;
        }

        @Override
        public void execute(@NonNull ProcessManifest processManifest) {
            VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor> config =
                    scope.getVariantConfiguration();
            final AndroidBuilder androidBuilder = scope.getGlobalScope().getAndroidBuilder();

            processManifest.setAndroidBuilder(androidBuilder);
            processManifest.setVariantName(config.getFullName());

            processManifest.variantConfiguration = config;

            final ProductFlavor mergedFlavor = config.getMergedFlavor();

            processManifest.minSdkVersion =
                    TaskInputHelper.memoize(
                            () -> {
                                ApiVersion minSdkVersion1 = mergedFlavor.getMinSdkVersion();
                                if (minSdkVersion1 == null) {
                                    return null;
                                }
                                return minSdkVersion1.getApiString();
                            });

            processManifest.targetSdkVersion =
                    TaskInputHelper.memoize(
                            () -> {
                                ApiVersion targetSdkVersion = mergedFlavor.getTargetSdkVersion();
                                if (targetSdkVersion == null) {
                                    return null;
                                }
                                return targetSdkVersion.getApiString();
                            });

            processManifest.maxSdkVersion = TaskInputHelper.memoize(mergedFlavor::getMaxSdkVersion);

            processManifest.setManifestOutputDirectory(scope.getManifestOutputDirectory());

            processManifest.setAaptFriendlyManifestOutputDirectory(
                    scope.getAaptFriendlyManifestOutputDirectory());
            processManifest.splitScope = scope.getSplitScope();

            scope.getVariantData()
                    .addTask(TaskContainer.TaskKind.PROCESS_MANIFEST, processManifest);
        }
    }
}

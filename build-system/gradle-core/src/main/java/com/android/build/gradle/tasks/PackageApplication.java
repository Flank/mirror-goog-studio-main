/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.profile.ProcessProfileWriter;
import com.android.builder.utils.FileCache;
import com.google.wireless.android.sdk.stats.GradleBuildProjectMetrics;
import java.io.File;
import java.io.IOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Internal;

/** Task to package an Android application (APK). */
public class PackageApplication extends PackageAndroidArtifact {

    InternalArtifactType expectedOutputType;

    @Override
    @Internal
    protected InternalArtifactType getInternalArtifactType() {
        return expectedOutputType;
    }

    @Override
    @Internal
    protected boolean isIncremental() {
        return true;
    }

    @Override
    void recordMetrics(File apkOutputFile, File resourcesApFile) {
        long metricsStartTime = System.nanoTime();
        GradleBuildProjectMetrics.Builder metrics = GradleBuildProjectMetrics.newBuilder();

        Long apkSize = getSize(apkOutputFile);
        if (apkSize != null) {
            metrics.setApkSize(apkSize);
        }

        Long resourcesApSize = getSize(resourcesApFile);
        if (resourcesApSize != null) {
            metrics.setResourcesApSize(resourcesApSize);
        }

        metrics.setMetricsTimeNs(System.nanoTime() - metricsStartTime);

        ProcessProfileWriter.getProject(getProject().getPath()).setMetrics(metrics);
    }

    @Nullable
    @Internal
    private static Long getSize(@Nullable File file) {
        if (file == null) {
            return null;
        }
        try {
            return java.nio.file.Files.size(file.toPath());
        } catch (IOException e) {
            return null;
        }
    }

    // ----- CreationAction -----

    /**
     * Configures the task to perform the "standard" packaging, including all files that should end
     * up in the APK.
     */
    public static class StandardCreationAction extends CreationAction<PackageApplication> {

        private final InternalArtifactType expectedOutputType;

        public StandardCreationAction(
                @NonNull VariantScope packagingScope,
                @NonNull File outputDirectory,
                @NonNull InternalArtifactType inputResourceFilesType,
                @NonNull BuildableArtifact manifests,
                @NonNull InternalArtifactType manifestType,
                @NonNull OutputScope outputScope,
                @Nullable FileCache fileCache,
                @NonNull InternalArtifactType expectedOutputType) {
            super(
                    packagingScope,
                    outputDirectory,
                    inputResourceFilesType,
                    manifests,
                    manifestType,
                    fileCache,
                    outputScope);
            this.expectedOutputType = expectedOutputType;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("package");
        }

        @NonNull
        @Override
        public Class<PackageApplication> getType() {
            return PackageApplication.class;
        }

        @Override
        protected void configure(PackageApplication task) {
            super.configure(task);
            task.expectedOutputType = expectedOutputType;
        }
    }

    /** Configures the task to only package resources and assets. */
    public static class InstantRunResourcesCreationAction
            extends CreationAction<PackageApplication> {

        @NonNull
        private final File mOutputFile;

        public InstantRunResourcesCreationAction(
                @NonNull File outputFile,
                @NonNull VariantScope scope,
                @NonNull InternalArtifactType inputResourceFilesType,
                @NonNull BuildableArtifact manifests,
                @NonNull InternalArtifactType manifestType,
                @Nullable FileCache fileCache,
                @NonNull OutputScope outputScope) {
            super(
                    scope,
                    outputFile.getParentFile(),
                    inputResourceFilesType,
                    manifests,
                    manifestType,
                    fileCache,
                    outputScope);
            mOutputFile = outputFile;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("packageInstantRunResources");
        }

        @NonNull
        @Override
        public Class<PackageApplication> getType() {
            return PackageApplication.class;
        }

        @Override
        protected void configure(@NonNull PackageApplication packageApplication) {
            packageApplication.expectedOutputType =
                    InternalArtifactType.INSTANT_RUN_PACKAGED_RESOURCES;
            packageApplication.instantRunFileType = FileType.RESOURCES;

            // Skip files which are not needed for hot/cold swap.
            FileCollection emptyCollection = variantScope.getGlobalScope().getProject().files();

            packageApplication.dexFolders = emptyCollection;
            packageApplication.jniFolders = emptyCollection;
            packageApplication.javaResourceFiles = emptyCollection;
            packageApplication.apkList =
                    variantScope
                            .getArtifacts()
                            .getFinalArtifactFiles(InternalArtifactType.APK_LIST);

            // Don't sign.
            packageApplication.setSigningConfig(null);
            packageApplication.outputFileProvider = (split) -> mOutputFile;
        }
    }
}

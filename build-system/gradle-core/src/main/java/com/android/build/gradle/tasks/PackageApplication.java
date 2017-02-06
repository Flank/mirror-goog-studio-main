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
import com.android.build.gradle.internal.incremental.DexPackagingPolicy;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.builder.profile.ProcessProfileWriter;
import com.android.ide.common.res2.FileStatus;
import com.google.wireless.android.sdk.stats.GradleBuildProjectMetrics;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.ParallelizableTask;

/**
 * Task to package an Android application (APK).
 */
@ParallelizableTask
public class PackageApplication extends PackageAndroidArtifact {

    @Override
    protected boolean isIncremental() {
        return true;
    }

    @Override
    protected void doFullTaskAction() throws IOException {
        super.doFullTaskAction();
        recordMetrics();
    }

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) throws IOException {
        super.doIncrementalTaskAction(changedInputs);
        recordMetrics();
    }

    private void recordMetrics() {
        long metricsStartTime = System.nanoTime();
        GradleBuildProjectMetrics.Builder metrics = GradleBuildProjectMetrics.newBuilder();

        Long apkSize = getSize(getOutputFile());
        if (apkSize != null) {
            metrics.setApkSize(apkSize);
        }

        Long resourcesApSize = getSize(getResourceFile());
        if (resourcesApSize != null) {
            metrics.setResourcesApSize(resourcesApSize);
        }

        metrics.setMetricsTimeNs(System.nanoTime() - metricsStartTime);

        ProcessProfileWriter.getProject(getProject().getPath()).setMetrics(metrics);
    }

    @Nullable
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

    // ----- ConfigAction -----

    /**
     * Configures the task to perform the "standard" packaging, including all
     * files that should end up in the APK.
     */
    public static class StandardConfigAction
            extends PackageAndroidArtifact.ConfigAction<PackageApplication> {

        public StandardConfigAction(
                @NonNull PackagingScope scope,
                @Nullable InstantRunPatchingPolicy patchingPolicy) {
            super(scope, patchingPolicy);
        }

        @NonNull
        @Override
        public String getName() {
            return packagingScope.getTaskName("package");
        }

        @NonNull
        @Override
        public Class<PackageApplication> getType() {
            return PackageApplication.class;
        }

        @Override
        public void execute(@NonNull final PackageApplication packageApplication) {
            ConventionMappingHelper.map(
                    packageApplication,
                    "outputFile",
                    packagingScope::getOutputPackage);
            super.execute(packageApplication);
        }
    }

    /**
     * Configures the task to only package resources and assets.
     */
    public static class InstantRunResourcesConfigAction
            extends PackageAndroidArtifact.ConfigAction<PackageApplication> {

        @NonNull
        private final File mOutputFile;

        public InstantRunResourcesConfigAction(
                @NonNull File outputFile,
                @NonNull PackagingScope scope,
                @Nullable InstantRunPatchingPolicy patchingPolicy) {
            super(scope, patchingPolicy);
            mOutputFile = outputFile;
        }

        @NonNull
        @Override
        public String getName() {
            return packagingScope.getTaskName("packageInstantRunResources");
        }

        @NonNull
        @Override
        public Class<PackageApplication> getType() {
            return PackageApplication.class;
        }

        @Override
        public void execute(@NonNull final PackageApplication packageApplication) {
            packageApplication.setOutputFile(mOutputFile);

            super.execute(packageApplication);
            packageApplication.instantRunFileType = FileType.RESOURCES;

            // Don't try to add any special dex files to this zip.
            packageApplication.dexPackagingPolicy = DexPackagingPolicy.STANDARD;

            // Skip files which are not needed for hot/cold swap.
            FileCollection emptyCollection = packagingScope.getProject().files();

            packageApplication.dexFolders = emptyCollection;
            packageApplication.jniFolders = emptyCollection;
            packageApplication.javaResourceFiles = emptyCollection;

            // Don't sign.
            packageApplication.setSigningConfig(null);
        }
    }
}

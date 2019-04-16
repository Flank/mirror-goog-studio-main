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
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.profile.ProcessProfileWriter;
import com.android.builder.utils.FileCache;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.GradleBuildProjectMetrics;
import java.io.File;
import java.io.IOException;
import javax.inject.Inject;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.workers.WorkerExecutor;

/** Task to package an Android application (APK). */
public abstract class PackageApplication extends PackageAndroidArtifact {

    InternalArtifactType expectedOutputType;

    @Inject
    public PackageApplication(WorkerExecutor workerExecutor) {
        super(workerExecutor);
    }

    @Override
    @Internal
    protected InternalArtifactType getInternalArtifactType() {
        return expectedOutputType;
    }

    public static void recordMetrics(String projectPath, File apkOutputFile, File resourcesApFile) {
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

        ProcessProfileWriter.getProject(projectPath).setMetrics(metrics);
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
    public static class CreationAction
            extends PackageAndroidArtifact.CreationAction<PackageApplication> {

        private final InternalArtifactType expectedOutputType;

        public CreationAction(
                @NonNull VariantScope packagingScope,
                @NonNull File outputDirectory,
                @NonNull InternalArtifactType inputResourceFilesType,
                @NonNull Provider<Directory> manifests,
                @NonNull InternalArtifactType manifestType,
                @NonNull OutputScope outputScope,
                @Nullable FileCache fileCache,
                @NonNull InternalArtifactType expectedOutputType,
                boolean packageCustomClassDependencies) {
            super(
                    packagingScope,
                    outputDirectory,
                    inputResourceFilesType,
                    manifests,
                    manifestType,
                    fileCache,
                    outputScope,
                    packageCustomClassDependencies);
            this.expectedOutputType = expectedOutputType;
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("package");
        }

        @NonNull
        @Override
        public Class<PackageApplication> getType() {
            return PackageApplication.class;
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);
            getVariantScope()
                    .getArtifacts()
                    .appendArtifact(
                            expectedOutputType, ImmutableList.of(outputDirectory), taskName);
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends PackageApplication> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope().getTaskContainer().setPackageAndroidTask(taskProvider);
        }

        @Override
        protected void finalConfigure(PackageApplication task) {
            super.finalConfigure(task);
            task.expectedOutputType = expectedOutputType;
        }
    }
}

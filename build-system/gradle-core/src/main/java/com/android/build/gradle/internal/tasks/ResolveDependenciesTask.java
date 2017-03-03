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

package com.android.build.gradle.internal.tasks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.TestAndroidConfig;
import com.android.build.gradle.internal.DependencyManager;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.dependency.level2.AndroidDependency;
import com.android.builder.utils.FileCache;
import com.android.ide.common.internal.WaitableExecutor;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.function.Consumer;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

/**
 * Task for resolving dependencies and exploding .aar.
 *
 * Replaces all PrepareLibTask when AndroidGradleOptions.isImprovedDependencyResolutionEnabled() is
 * true.
 */
public class ResolveDependenciesTask extends BaseTask {
    private BaseVariantData<? extends BaseVariantOutputData> variantData;
    private DependencyManager dependencyManager;
    private String testedProjectPath;
    @Nullable private FileCache buildCache;
    private FileCache projectLevelCache;

    @TaskAction
    public void resolveDependencies() throws InterruptedException, IOException {
        // Resolve variant dependencies.
        dependencyManager.resolveDependencies(
                variantData.getVariantDependency(),
                testedProjectPath,
                variantData.getScope().getGlobalScope().getBuildCache(),
                variantData.getScope().getGlobalScope().getProjectLevelCache());

        variantData
                .getVariantConfiguration()
                .setResolvedDependencies(
                        variantData.getVariantDependency().getCompileDependencies(),
                        variantData.getVariantDependency().getPackageDependencies());

        extractAarInParallel(
                getProject(), variantData.getVariantConfiguration(), buildCache, projectLevelCache);
    }

    public static void extractAarInParallel(
            @NonNull Project project,
            @NonNull GradleVariantConfiguration config,
            @Nullable FileCache buildCache,
            @NonNull FileCache projectLevelCache)
            throws InterruptedException, IOException {
        WaitableExecutor<Void> executor = WaitableExecutor.useGlobalSharedThreadPool();

        Set<AndroidDependency> dependencies =
                Sets.newHashSet(config.getFlatCompileAndroidLibraries());
        dependencies.addAll(config.getFlatPackageAndroidLibraries());

        for (AndroidDependency androidDependency :dependencies) {
            if (androidDependency.getProjectPath() != null) {
                // Don't need to explode sub-module library.
                continue;
            }
            File input = androidDependency.getArtifactFile();
            File output = androidDependency.getExtractedFolder();
            // For snapshots, we use a project local file cache.
            boolean useBuildCache =
                    PrepareLibraryTask.shouldUseBuildCache(
                            buildCache != null, androidDependency.getCoordinates());
            PrepareLibraryTask.prepareLibrary(
                    input,
                    output,
                    useBuildCache ? buildCache : projectLevelCache,
                    createAction(project, executor, input),
                    project.getLogger(),
                    false /* includeTroubleshootingMessage */);
        }
        executor.waitForTasksWithQuickFail(false);
    }

    private static Consumer<File> createAction(
            @NonNull Project project,
            @NonNull WaitableExecutor<Void> executor,
            @NonNull File input) {
        return (outputDir) -> executor.execute(() -> {
            PrepareLibraryTask.extract(
                    input,
                    outputDir,
                    project);
            return null;
        });
    }

    public static class ConfigAction implements TaskConfigAction<ResolveDependenciesTask> {
        @NonNull
        private final VariantScope scope;
        @NonNull
        private final DependencyManager dependencyManager;

        public ConfigAction(
                @NonNull VariantScope scope,
                @NonNull DependencyManager dependencyManager) {
            this.scope = scope;
            this.dependencyManager = dependencyManager;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("resolve", "Dependencies");
        }

        @NonNull
        @Override
        public Class<ResolveDependenciesTask> getType() {
            return ResolveDependenciesTask.class;
        }

        @Override
        public void execute(@NonNull ResolveDependenciesTask task) {
            task.variantData = scope.getVariantData();
            task.dependencyManager = dependencyManager;
            task.setVariantName(scope.getFullVariantName());

            task.testedProjectPath =
                    scope.getGlobalScope().getExtension() instanceof TestAndroidConfig
                            ? ((TestAndroidConfig) scope.getGlobalScope().getExtension()).getTargetProjectPath()
                            : null;

            task.buildCache = scope.getGlobalScope().getBuildCache();
            task.projectLevelCache = scope.getGlobalScope().getProjectLevelCache();
        }
    }
}

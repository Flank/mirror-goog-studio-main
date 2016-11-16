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
import com.android.build.gradle.TestAndroidConfig;
import com.android.build.gradle.internal.DependencyManager;
import com.android.build.gradle.internal.LibraryCache;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.internal.variant.TestedVariantData;
import com.android.builder.core.VariantType;

import com.android.builder.dependency.level2.AndroidDependency;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
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

    @TaskAction
    public void resolveDependencies() {
        // Resolve variant dependencies.
        GradleVariantConfiguration config = variantData.getVariantConfiguration();
        VariantDependencies testedVariantDeps = null;
        if (variantData instanceof TestVariantData
                && config.getTestedConfig() != null
                && config.getTestedConfig().getType() == VariantType.LIBRARY) {
            TestedVariantData testedVariantData = ((TestVariantData) variantData).getTestedVariantData();
            if (testedVariantData instanceof BaseVariantData) {
                testedVariantDeps = ((BaseVariantData) testedVariantData).getVariantDependency();
            }
        }

        dependencyManager.resolveDependencies(
                variantData.getVariantDependency(),
                testedProjectPath);

        variantData.getVariantConfiguration().setResolvedDependencies(
                variantData.getVariantDependency().getCompileDependencies(),
                variantData.getVariantDependency().getPackageDependencies());

        // FIXME: Refactor to DependencyManager.  Move to separate task and make it multithreaded.
        // Explode aar.
        for (AndroidDependency androidDependency : Iterables.concat(
                config.getFlatCompileAndroidLibraries(), config.getFlatPackageAndroidLibraries())) {
            if (androidDependency.getProjectPath() != null) {
                // Don't need to explode sub-module library.
                continue;
            }
            extract(androidDependency.getArtifactFile(), androidDependency.getExtractedFolder());
        }
    }

    private void extract(File bundle, File outputDir) {
        LibraryCache.unzipAar(bundle, outputDir, getProject());
        // verify the we have a classes.jar, if we don't just create an empty one.
        File classesJar = new File(new File(outputDir, "jars"), "classes.jar");
        if (classesJar.exists()) {
            return;
        }
        try {
            Files.createParentDirs(classesJar);
            JarOutputStream jarOutputStream = new JarOutputStream(
                    new BufferedOutputStream(new FileOutputStream(classesJar)), new Manifest());
            jarOutputStream.close();
        } catch (IOException e) {
            throw new RuntimeException("Cannot create missing classes.jar", e);
        }

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
        }
    }
}

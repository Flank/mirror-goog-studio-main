/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.build.gradle.internal.dependency.DependencyChecker;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.model.SyncIssue;
import com.android.utils.StringHelper;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;

@ParallelizableTask
public class PrepareDependenciesTask extends BaseTask {

    private BaseVariantData variant;
    private final List<DependencyChecker> checkers = Lists.newArrayList();

    @TaskAction
    protected void prepare() {
        int minSdk = variant.getVariantConfiguration().getMinSdkVersionValue();

        boolean foundError = false;

        for (DependencyChecker checker : checkers) {
            for (Map.Entry<ModuleVersionIdentifier, Integer> entry :
                    checker.getLegacyApiLevels().entrySet()) {
                ModuleVersionIdentifier mavenVersion = entry.getKey();
                int api = entry.getValue();
                if (api > minSdk) {
                    foundError = true;
                    String variantName = checker.getVariantName();
                    getLogger().error(String.format(
                            "Variant %s has a dependency on version %s of the legacy %s Maven " +
                                    "artifact, which corresponds to API level %s. This is not " +
                                    "compatible with min SDK of this module, which is %s. " +
                                    "Please use the 'gradle dependencies' task to debug your " +
                                    "dependencies graph.",
                            StringHelper.capitalize(variantName),
                            mavenVersion.getVersion(),
                            mavenVersion.getGroup(),
                            api,
                            minSdk));
                }
            }

            for (SyncIssue syncIssue : checker.getSyncIssues()) {
                if (syncIssue.getSeverity() == SyncIssue.SEVERITY_ERROR) {
                    foundError = true;
                    getLogger().error(syncIssue.getMessage());
                }
            }
        }

        if (foundError) {
            throw new GradleException("Dependency Error. See console for details.");
        }

    }

    public void addChecker(DependencyChecker checker) {
        checkers.add(checker);
    }

    public BaseVariantData getVariant() {
        return variant;
    }

    public void setVariant(BaseVariantData variant) {
        this.variant = variant;
    }


    public static final class ConfigAction implements TaskConfigAction<PrepareDependenciesTask> {

        private final VariantScope scope;

        private final VariantDependencies configurationDependencies;

        public ConfigAction(
                @NonNull VariantScope scope,
                @NonNull VariantDependencies configurationDependencies) {
            this.scope = scope;
            this.configurationDependencies = configurationDependencies;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("prepare", "Dependencies");
        }

        @NonNull
        @Override
        public Class<PrepareDependenciesTask> getType() {
            return PrepareDependenciesTask.class;
        }

        @Override
        public void execute(@NonNull PrepareDependenciesTask prepareDependenciesTask) {
            scope.getVariantData().prepareDependenciesTask = prepareDependenciesTask;
            prepareDependenciesTask.dependsOn(scope.getPreBuildTask().getName());

            prepareDependenciesTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            prepareDependenciesTask.setVariantName(scope.getVariantConfiguration().getFullName());
            prepareDependenciesTask.setVariant(scope.getVariantData());


            prepareDependenciesTask.addChecker(configurationDependencies.getChecker());
        }
    }
}

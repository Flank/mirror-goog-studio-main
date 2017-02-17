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

package com.android.build.gradle.internal.tasks;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.google.common.collect.Maps;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.tasks.TaskAction;

/** Pre build task that does some checks for application variants */
public class TestPreBuildTask extends DefaultAndroidTask {

    // list of Android only compile and runtime classpath.
    private ArtifactCollection testedRuntimeClasspath;
    private ArtifactCollection testRuntimeClasspath;
    private VariantScope variantScope;

    @TaskAction
    void run() {
        testedRuntimeClasspath =
                variantScope
                        .getTestedVariantData()
                        .getScope()
                        .getArtifactCollection(RUNTIME_CLASSPATH, ALL, CLASSES);
        testRuntimeClasspath = variantScope.getArtifactCollection(RUNTIME_CLASSPATH, ALL, CLASSES);

        checkTestAndTestedDependencies();
    }

    private void checkTestAndTestedDependencies() {
        Set<ResolvedArtifactResult> testedArtifacts = testedRuntimeClasspath.getArtifacts();
        Set<ResolvedArtifactResult> testArtifacts = testRuntimeClasspath.getArtifacts();

        // Store a map of groupId -> (artifactId -> versions)
        Map<String, Map<String, String>> testedIds =
                Maps.newHashMapWithExpectedSize(testedArtifacts.size());

        // build a list of the runtime artifacts
        for (ResolvedArtifactResult artifact : testedArtifacts) {
            // only care about external dependencies to compare versions.
            final ComponentIdentifier componentIdentifier =
                    artifact.getId().getComponentIdentifier();
            if (componentIdentifier instanceof ModuleComponentIdentifier) {
                ModuleComponentIdentifier moduleId =
                        (ModuleComponentIdentifier) componentIdentifier;

                // get the sub-map, creating it if needed.
                Map<String, String> subMap =
                        testedIds.computeIfAbsent(moduleId.getGroup(), s -> new HashMap<>());

                subMap.put(moduleId.getModule(), moduleId.getVersion());
            }
        }

        // run through the compile ones to check for provided only.
        for (ResolvedArtifactResult artifact : testArtifacts) {
            // only care about external dependencies to compare versions.
            final ComponentIdentifier componentIdentifier =
                    artifact.getId().getComponentIdentifier();
            if (componentIdentifier instanceof ModuleComponentIdentifier) {
                ModuleComponentIdentifier moduleId =
                        (ModuleComponentIdentifier) componentIdentifier;

                Map<String, String> subMap = testedIds.get(moduleId.getGroup());
                if (subMap != null) {
                    String testedVersion = subMap.get(moduleId.getModule());
                    if (testedVersion != null) {
                        if (!testedVersion.equals(moduleId.getVersion())) {
                            throw new GradleException(
                                    String.format(
                                            "Conflict with dependency '%s:%s' in project '%s'. Resolved versions for"
                                                    + " app (%s) and test app (%s) differ. See"
                                                    + " http://g.co/androidstudio/app-test-app-conflict"
                                                    + " for details.",
                                            moduleId.getGroup(),
                                            moduleId.getModule(),
                                            getProject().getPath(),
                                            testedVersion,
                                            moduleId.getVersion()));
                        }
                    }
                }
            }
        }
    }

    public static class ConfigAction implements TaskConfigAction<TestPreBuildTask> {

        @NonNull private final VariantScope variantScope;

        public ConfigAction(@NonNull VariantScope variantScope) {
            this.variantScope = variantScope;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("pre", "Build");
        }

        @NonNull
        @Override
        public Class<TestPreBuildTask> getType() {
            return TestPreBuildTask.class;
        }

        @Override
        public void execute(@NonNull TestPreBuildTask task) {
            task.setVariantName(variantScope.getFullVariantName());

            task.variantScope = variantScope;
            variantScope.getVariantData().preBuildTask = task;
        }
    }
}

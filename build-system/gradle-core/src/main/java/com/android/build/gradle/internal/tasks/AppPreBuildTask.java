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
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.MANIFEST;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.google.common.collect.Sets;
import java.util.Set;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.tasks.TaskAction;

/** Pre build task that does some checks for application variants */
public class AppPreBuildTask extends DefaultAndroidTask {

    // list of Android only compile and runtime classpath.
    private ArtifactCollection compileClasspath;
    private ArtifactCollection runtimeClasspath;
    private VariantScope variantScope;

    @TaskAction
    void run() {
        compileClasspath = variantScope.getArtifactCollection(COMPILE_CLASSPATH, ALL, MANIFEST);
        runtimeClasspath = variantScope.getArtifactCollection(RUNTIME_CLASSPATH, ALL, MANIFEST);

        checkAppWithAndroidLibAsCompileOnly();
    }

    private void checkAppWithAndroidLibAsCompileOnly() {
        Set<ResolvedArtifactResult> compileArtifacts = compileClasspath.getArtifacts();
        Set<ResolvedArtifactResult> runtimeArtifacts = runtimeClasspath.getArtifacts();

        Set<ComponentIdentifier> runtimeIds =
                Sets.newHashSetWithExpectedSize(runtimeArtifacts.size());

        // build a list of the runtime artifacts
        for (ResolvedArtifactResult artifact : runtimeArtifacts) {
            runtimeIds.add(artifact.getId().getComponentIdentifier());
        }

        // run through the compile ones to check for provided only.
        for (ResolvedArtifactResult artifact : compileArtifacts) {
            if (!runtimeIds.contains(artifact.getId().getComponentIdentifier())) {
                String display = artifact.getId().getComponentIdentifier().getDisplayName();
                throw new RuntimeException(
                        "Android dependency '"
                                + display
                                + "' is set to compileOnly/provided which is not supported");
            }
        }
    }

    public static class ConfigAction implements TaskConfigAction<AppPreBuildTask> {

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
        public Class<AppPreBuildTask> getType() {
            return AppPreBuildTask.class;
        }

        @Override
        public void execute(@NonNull AppPreBuildTask task) {
            task.setVariantName(variantScope.getFullVariantName());

            task.variantScope = variantScope;
            variantScope.getVariantData().preBuildTask = task;
        }
    }
}

/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency

import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.VariantScope
import org.gradle.api.Action
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler

/**
 * An Action to synchronize a dependency with a runtimeClasspath.
 *
 * This is meant to be passed to [ResolvableDependencies.beforeResolve]
 */
class ConstraintHandler(
    private val variantScope: VariantScope,
    private val constraints: DependencyConstraintHandler,
    private val runtimeConfigName: String
) : Action<ResolvableDependencies> {
    override fun execute(compile: ResolvableDependencies) {
        // Resolve the runtime dependencies to get the full resolved artifacts.
        // Only cares about EXTERNAL scope as it's the only one that has versioned dependencies.
        // Also, querying for MODULE would try to run ArtifactTransform on the sub-modules which
        // would fail since they have not been built yet.
        // Query for CLASSES so that we get Android and non-Android dependencies.
        val runtimeArtifacts = variantScope.getArtifactCollection(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.EXTERNAL,
            AndroidArtifacts.ArtifactType.CLASSES
        )

        val compileConfigName = compile.name

        // loop on all the artifacts and set constraints for the compile classpath.
        runtimeArtifacts.artifacts.forEach { artifact ->
            val id = artifact.id.componentIdentifier
            if (id is ModuleComponentIdentifier) {
                // using a repository with a flatDir to stock local AARs will result in an
                // external module dependency with no version.
                if (!id.version.isNullOrEmpty()) {
                    constraints.add(
                        compileConfigName,
                        "${id.group}:${id.module}:${id.version}"
                    ) { constraint ->
                        constraint.because("$runtimeConfigName uses version ${id.version}")
                        constraint.version { versionConstraint ->
                            versionConstraint.strictly(id.version)
                        }
                    }
                }
            }
        }
    }
}
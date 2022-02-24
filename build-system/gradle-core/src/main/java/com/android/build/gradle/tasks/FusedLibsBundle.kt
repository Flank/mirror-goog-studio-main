/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.fusedlibs.FusedLibsInternalArtifactType
import com.android.build.gradle.internal.fusedlibs.FusedLibsVariantScope
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Task does not calculate anything, only creates a jar.")
abstract class FusedLibsBundle: Jar() {

    // We have to explicitly repeat the output file as the artifacts API expects a
    // RegularFileProperty annotated with OutputFile so proper dependency can be expressed.
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    abstract class CreationAction<T: FusedLibsBundle>(
            val creationConfig: FusedLibsVariantScope,
            val artifactType: FusedLibsInternalArtifactType<RegularFile>
    ) : TaskCreationAction<T>() {

        override fun handleProvider(taskProvider: TaskProvider<T>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    FusedLibsBundle::outputFile
            ).on(artifactType)
        }

        override fun configure(task: T) {

            // manually resets the output value to the one this task will use.
            task.outputFile.set(task.archiveFile)
            task.destinationDirectory.set(creationConfig.layout.buildDirectory.dir(task.name))
        }
    }
}

@DisableCachingByDefault(because = "Task does not calculate anything, only creates a jar.")
abstract class FusedLibsBundleAar: FusedLibsBundle() {

    class CreationAction(
            creationConfig: FusedLibsVariantScope,
    ) : FusedLibsBundle.CreationAction<FusedLibsBundleAar>(
            creationConfig,
            FusedLibsInternalArtifactType.JAR_CLASSES,
    ) {

        override val name: String
            get() = "bundle"
        override val type: Class<FusedLibsBundleAar>
            get() = FusedLibsBundleAar::class.java

        override fun configure(task: FusedLibsBundleAar) {
            super.configure(task)
            task.archiveFileName.set("bundle.aar")
            task.from(creationConfig.artifacts.get(FusedLibsInternalArtifactType.MERGED_CLASSES))
        }
    }
}

@DisableCachingByDefault(because = "Task does not calculate anything, only creates a jar.")
abstract class FusedLibsBundleClasses: FusedLibsBundle() {

    class CreationAction(
            creationConfig: FusedLibsVariantScope,
    ): FusedLibsBundle.CreationAction<FusedLibsBundleClasses>(
            creationConfig,
            FusedLibsInternalArtifactType.BUNDLED_LIBS
    ) {
        override val name: String
            get() = "packageJar"
        override val type: Class<FusedLibsBundleClasses>
            get() = FusedLibsBundleClasses::class.java

        override fun configure(task: FusedLibsBundleClasses) {
            super.configure(task)
            task.archiveFileName.set("classes.jar")
            task.from(creationConfig.artifacts.get(FusedLibsInternalArtifactType.JAR_CLASSES))
        }
    }
}

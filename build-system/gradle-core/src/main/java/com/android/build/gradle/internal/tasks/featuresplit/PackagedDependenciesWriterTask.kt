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

package com.android.build.gradle.internal.tasks.featuresplit

import org.gradle.api.Action
import com.android.build.api.attributes.VariantAttr
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ARTIFACT_TYPE
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.utils.FileUtils
import com.google.common.base.Joiner
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider

private val jarTypeOnly = Action { container: AttributeContainer ->
    container.attribute(ARTIFACT_TYPE, AndroidArtifacts.ArtifactType.JAR.type)
}

/** Task to write the list of transitive dependencies.  */
@CacheableTask
abstract class PackagedDependenciesWriterTask : NonIncrementalTask() {

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    val content: Set<String> by lazy {
        // incoming.ResolutionResult will not return direct file dependencies for jars, and so
        // jars would not be properly marked as packed in an apk. Instead query via artifactView,
        // in addition filtering only for file based dependencies. This is checked by a
        // DynamicApp integration test (`test bundleDebug task`), which checks jar deps are properly
        // packaged.
        runtimeClasspath.incoming.resolutionResult.allComponents
            .asSequence()
            .map { it.toIdString() }
            .plus(
                runtimeClasspath.incoming.artifactView { config ->
                        config.attributes(jarTypeOnly)
                        config.componentFilter { id ->
                            !(id is ProjectComponentIdentifier || id is ModuleComponentIdentifier) }}
                    .artifacts
                    .asSequence()
                    .map{ it.toIdString()})
            .toSortedSet()
    }

    // the list of packaged dependencies by transitive dependencies.
    private lateinit var transitivePackagedDeps : ArtifactCollection

    private lateinit var runtimeClasspath: Configuration

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    val transitivePackagedDepsFC : FileCollection
        get() = transitivePackagedDeps.artifactFiles

    private lateinit var projectPath: String

    override fun doTaskAction() {
        val apkFilters = mutableSetOf<String>()
        val contentFilters = mutableSetOf<String>()
        // load the transitive information and remove from the full content.
        // We know this is correct because this information is also used in
        // FilteredArtifactCollection to remove this content from runtime-based ArtifactCollection.
        // However since we directly use the Configuration here, we have to manually remove it.
        for (transitiveDep in transitivePackagedDeps) {
            // register the APK that generated this list to remove it from our list
            apkFilters.add(transitiveDep.toIdString())
            // read its packaged content to also remove it.
            val lines = transitiveDep.file.readLines()
            contentFilters.addAll(lines)
        }

        // compute the overall content
        val filteredContent = content.asSequence()
            .filter { !apkFilters.contains(it) && !contentFilters.contains(it) && it != projectPath }
            .toSortedSet()

        val asFile = outputFile.get().asFile
        FileUtils.mkdirs(asFile.parentFile)
        asFile.writeText(Joiner.on(System.lineSeparator()).join(filteredContent))
    }

    /**
     * Action to create the task that generates the transitive dependency list to be consumed by
     * other modules.
     *
     * This cannot depend on preBuild as it would introduce a dependency cycle.
     */
    class CreationAction(private val variantScope: VariantScope) :
        TaskCreationAction<PackagedDependenciesWriterTask>() {

        override val name: String
            get() = variantScope.getTaskName("generate", "FeatureTransitiveDeps")
        override val type: Class<PackagedDependenciesWriterTask>
            get() = PackagedDependenciesWriterTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out PackagedDependenciesWriterTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesFile(
                InternalArtifactType.PACKAGED_DEPENDENCIES,
                BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider,
                PackagedDependenciesWriterTask::outputFile,
                "deps.txt"
            )
        }

        override fun configure(task: PackagedDependenciesWriterTask) {
            task.variantName = variantScope.fullVariantName
            task.projectPath = variantScope.globalScope.project.path
            task.runtimeClasspath = variantScope.variantDependencies.runtimeClasspath

            task.transitivePackagedDeps =
                variantScope.getArtifactCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    AndroidArtifacts.ArtifactType.PACKAGED_DEPENDENCIES)
        }
    }
}

private fun ResolvedComponentResult.toIdString() : String {
    return id.toIdString {
        getAndroidVariant()
    }
}

fun ResolvedArtifactResult.toIdString(): String {
    return id.componentIdentifier.toIdString {
        variant.attributes.getAttribute(VariantAttr.ATTRIBUTE)?.name
    }
}

private inline fun ComponentIdentifier.toIdString(variantProvider: () -> String?) : String {
    return when (this) {
        is ProjectComponentIdentifier -> {
            val variant = variantProvider()
            if (variant == null) {
                projectPath
            } else {
                "$projectPath::$variant"
            }
        }
        is ModuleComponentIdentifier -> "$group:$module"
        else -> toString()
    }
}

private fun ResolvedComponentResult.getAndroidVariant(): String? = variants
    .asSequence()
    .map { result ->
        // what we have access here are the attributes of the variant that was selected
        // rather than the one setup on the resolved variant (if one were to access this via
        // ArtifactCollection).
        // In order to handle cross project boundaries (in the case of composite projects where
        // both side have different classloader for instance), the attributes are desugared into
        // Strings.
        // So in this case all the attributes are Attribute<String> with the value being the
        // original generic type.
        val key = result.attributes.keySet().firstOrNull {
            it.name == VariantAttr::class.java.name
        }

        if (key != null) {
            result.attributes.getAttribute(key) as String?
        } else null
    }
    .filter { it != null }
    .firstOrNull()


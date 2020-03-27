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
package com.android.build.gradle.tasks

import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.BaseCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.compiling.BuildConfigGenerator
import com.android.builder.model.ClassField
import com.android.utils.FileUtils
import com.google.common.collect.Lists
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider

@CacheableTask
abstract class GenerateBuildConfig : NonIncrementalTask() {

    // ----- PUBLIC TASK API -----

    @get:OutputDirectory
    abstract val sourceOutputDir: DirectoryProperty

    // ----- PRIVATE TASK API -----

    @get:Input
    @get:Optional
    abstract val buildTypeName: Property<String>

    @get:Input
    var isLibrary: Boolean = false
        private set

    @get:Input
    abstract val buildConfigPackageName: Property<String>

    @get:Input
    @get:Optional
    abstract val appPackageName: Property<String>

    @get:Input
    abstract val debuggable: Property<Boolean>

    @get:Input
    abstract val flavorName: Property<String>

    @get:Input
    abstract val flavorNamesWithDimensionNames: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val versionName: Property<String?>

    @get:Input
    @get:Optional
    abstract val versionCode: Property<Int?>

    // just to know whether to set versionCode/Name in the build config field.
    // For apps we want to put it whether the info is there or not because it might be set
    // in release but not in debug but you need the code to compile in both variants.
    // And we cannot rely on Provider.isPresent as it does not disambiguate between missing value
    // and null value.
    @get:Internal
    abstract val hasVersionInfo: Property<Boolean>

    val itemValues: List<String>
        @Input
        get() {
            val resolvedItems = items.get()
            val list = Lists.newArrayListWithCapacity<String>(resolvedItems.size * 3)

            for (item in resolvedItems) {
                when (item) {
                    is String -> list.add(item)
                    is ClassField -> list.apply {
                        add(item.type)
                        add(item.name)
                        add(item.value)
                    }
                }
            }
            return list
        }

    @get:Internal // handled by getItemValues()
    abstract val items: ListProperty<Any>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedManifests: DirectoryProperty

    override fun doTaskAction() {
        // must clear the folder in case the packagename changed, otherwise,
        // there'll be two classes.
        val destinationDir = sourceOutputDir.get().asFile
        FileUtils.cleanOutputDir(destinationDir)

        val generator = BuildConfigGenerator(
            sourceOutputDir.get().asFile,
            buildConfigPackageName.get()
        )

        // Hack (see IDEA-100046): We want to avoid reporting "condition is always true"
        // from the data flow inspection, so use a non-constant value. However, that defeats
        // the purpose of this flag (when not in debug mode, if (BuildConfig.DEBUG && ...) will
        // be completely removed by the compiler), so as a hack we do it only for the case
        // where debug is true, which is the most likely scenario while the user is looking
        // at source code.
        // map.put(PH_DEBUG, Boolean.toString(mDebug));

        generator.addField(
            "boolean", "DEBUG", if (debuggable.get()) "Boolean.parseBoolean(\"true\")" else "false"
        )

        if (isLibrary) {
            generator
                .addField(
                    "String",
                    "LIBRARY_PACKAGE_NAME",
                    """"${buildConfigPackageName.get()}""""
                )
        } else {
            generator.addField(
                "String",
                "APPLICATION_ID",
                """"${appPackageName.get()}""""
            )
        }

        buildTypeName.orNull?.let {
            generator.addField("String", "BUILD_TYPE", """"$it"""")
        }

        flavorName.get().let {
            if (it.isNotEmpty()) {
                generator.addField("String", "FLAVOR", """"$it"""")
            }
        }

        val flavors = flavorNamesWithDimensionNames.get()
        val count = flavors.size
        if (count > 1) {
            var i = 0
            while (i < count) {
                generator.addField(
                    "String",
                    """FLAVOR_${flavors[i + 1]}""",
                    """"${flavors[i]}""""
                )
                i += 2
            }
        }

        if (hasVersionInfo.get()) {
            generator.addField("int", "VERSION_CODE", versionCode.getOrElse(1).toString())
            generator
                .addField(
                    "String",
                    "VERSION_NAME",
                    """"${versionName.getOrElse("")}""""
                )
        }

        // user generated items
        generator.addItems(items.get())

        generator.generate()
    }

    // ----- Config Action -----

    internal class CreationAction(creationConfig: BaseCreationConfig) :
        VariantTaskCreationAction<GenerateBuildConfig, BaseCreationConfig>(
            creationConfig
        ) {

        override val name: String = computeTaskName("generate", "BuildConfig")

        override val type: Class<GenerateBuildConfig> = GenerateBuildConfig::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<out GenerateBuildConfig>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.taskContainer.generateBuildConfigTask = taskProvider
            creationConfig.operations.setInitialProvider(
                taskProvider,
                GenerateBuildConfig::sourceOutputDir
            ).atLocation(creationConfig.paths.buildConfigSourceOutputDir.canonicalPath)
                .on(InternalArtifactType.GENERATED_BUILD_CONFIG_JAVA)
        }

        override fun configure(
            task: GenerateBuildConfig
        ) {
            super.configure(task)

            val variantDslInfo = creationConfig.variantDslInfo

            val project = creationConfig.globalScope.project
            task.buildConfigPackageName.setDisallowChanges(project.provider {
                variantDslInfo.originalApplicationId
            })

            if (creationConfig is ApkCreationConfig) {
                task.appPackageName.setDisallowChanges(creationConfig.applicationId)
            }

            if (creationConfig is ApplicationCreationConfig) {
                val mainSplit = creationConfig.outputs.getMainSplit()
                // check the variant API property first (if there is one) in case the variant
                // output version has been overridden, otherwise use the variant configuration
                task.versionCode.setDisallowChanges(mainSplit.versionCode)
                task.versionName.setDisallowChanges(mainSplit.versionName)
                task.hasVersionInfo.setDisallowChanges(true)
            } else {
                task.hasVersionInfo.setDisallowChanges(false)
            }

            task.debuggable.setDisallowChanges(creationConfig.variantDslInfo.isDebuggable)

            task.buildTypeName.setDisallowChanges(variantDslInfo.componentIdentity.buildType)

            // no need to memoize, variant configuration does that already.
            task.flavorName.setDisallowChanges(
                    project.provider { variantDslInfo.componentIdentity.flavorName })

            task.flavorNamesWithDimensionNames.setDisallowChanges(project.provider {
                variantDslInfo.flavorNamesWithDimensionNames
            })

            task.items.setDisallowChanges(project.provider { variantDslInfo.buildConfigItems })

            if (creationConfig.variantType.isTestComponent) {
                creationConfig.operations.setTaskInputToFinalProduct(
                    InternalArtifactType.PACKAGED_MANIFESTS, task.mergedManifests
                )
            }
            task.isLibrary = creationConfig.variantType.isAar
        }
    }
}

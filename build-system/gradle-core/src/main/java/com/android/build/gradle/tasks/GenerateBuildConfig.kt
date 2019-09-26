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

import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.TaskInputHelper
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.compiling.BuildConfigGenerator
import com.android.builder.model.ClassField
import com.android.utils.FileUtils
import com.google.common.collect.Lists
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import java.io.File
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
    lateinit var sourceOutputDir: File

    // ----- PRIVATE TASK API -----

    @get:Input
    lateinit var buildTypeName: String

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
    abstract val versionName: Property<String>

    @get:Input
    abstract val versionCode: Property<Int>

    val itemValues: List<String>
        @Input
        get() {
            val resolvedItems = items.get()
            val list = Lists.newArrayListWithCapacity<String>(resolvedItems.size * 3)

            for (item in resolvedItems) {
                if (item is String) {
                    list.add(item)
                } else if (item is ClassField) {
                    list.add(item.type)
                    list.add(item.name)
                    list.add(item.value)
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
        val destinationDir = sourceOutputDir
        FileUtils.cleanOutputDir(destinationDir)

        val generator = BuildConfigGenerator(
            sourceOutputDir,
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
                    '"'.toString() + buildConfigPackageName.get() + '"'.toString()
                )
                .addDeprecatedField(
                    "String",
                    "APPLICATION_ID",
                    '"'.toString() + buildConfigPackageName.get() + '"'.toString(),
                    "@deprecated APPLICATION_ID is misleading in libraries. For the library package name use LIBRARY_PACKAGE_NAME"
                )
        } else {
            generator.addField(
                "String",
                "APPLICATION_ID",
                '"'.toString() + appPackageName.get() + '"'.toString()
            )
        }

        generator
            .addField("String", "BUILD_TYPE", '"'.toString() + buildTypeName + '"'.toString())
            .addField("String", "FLAVOR", '"'.toString() + flavorName.get() + '"'.toString())
            .addField("int", "VERSION_CODE", Integer.toString(versionCode.get()))
            .addField(
                "String",
                "VERSION_NAME",
                '"'.toString() + versionName.getOrElse("") + '"'.toString()
            )
            .addItems(items.get())

        val flavors = flavorNamesWithDimensionNames.get()
        val count = flavors.size
        if (count > 1) {
            var i = 0
            while (i < count) {
                generator.addField(
                    "String",
                    "FLAVOR_" + flavors[i + 1],
                    '"'.toString() + flavors[i] + '"'.toString()
                )
                i += 2
            }
        }

        generator.generate()
    }

    // ----- Config Action -----

    class CreationAction(scope: VariantScope) :
        VariantTaskCreationAction<GenerateBuildConfig>(scope) {

        override val name: String = scope.getTaskName("generate", "BuildConfig")

        override val type: Class<GenerateBuildConfig> = GenerateBuildConfig::class.java

        override fun handleProvider(taskProvider: TaskProvider<out GenerateBuildConfig>) {
            super.handleProvider(taskProvider)
            variantScope.taskContainer.generateBuildConfigTask = taskProvider
        }

        override fun configure(task: GenerateBuildConfig) {
            super.configure(task)

            val variantData = variantScope.variantData

            val variantConfiguration = variantData.variantConfiguration

            val project = variantScope.globalScope.project
            task.buildConfigPackageName.set(
                TaskInputHelper.memoizeToProvider(project) {
                    variantConfiguration.originalApplicationId
                }
            )

            task.appPackageName.set(
                TaskInputHelper.memoizeToProvider(project) {
                    variantConfiguration.applicationId.takeUnless {
                        variantConfiguration.type.isAar
                    }
                }
            )

            task.versionName.set(
                TaskInputHelper.memoizeToProvider(project) { variantConfiguration.versionName })
            task.versionCode.set(
                TaskInputHelper.memoizeToProvider(project) { variantConfiguration.versionCode })

            task.debuggable.set(
                TaskInputHelper.memoizeToProvider(project) {
                    variantConfiguration.buildType.isDebuggable
                }
            )

            task.buildTypeName = variantConfiguration.buildType.name

            // no need to memoize, variant configuration does that already.
            task.flavorName.set(project.provider { variantConfiguration.flavorName })

            task.flavorNamesWithDimensionNames.set(
                TaskInputHelper.memoizeToProvider(project) {
                    variantConfiguration.flavorNamesWithDimensionNames
                }
            )

            task.items.set(
                TaskInputHelper.memoizeToProvider(project) {
                    variantConfiguration.buildConfigItems
                }
            )

            task.sourceOutputDir = variantScope.buildConfigSourceOutputDir

            if (variantScope.variantConfiguration.type.isTestComponent) {
                variantScope.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.MERGED_MANIFESTS, task.mergedManifests
                )
            }

            task.isLibrary = variantConfiguration.type.isAar
        }
    }
}

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

package com.android.build.gradle.internal.dsl

import com.android.build.api.attributes.VariantAttr
import com.android.build.api.dsl.extension.BundleExtension
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.tasks.ZipModuleTask
import com.google.common.collect.ImmutableMap
import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.file.FileCollection

open class BundleExtensionImpl(private val project: Project): BundleExtension {

    private val _modules: MutableMap<String, FileCollection> = mutableMapOf()

    val modules: Map<String, FileCollection>
        get() = ImmutableMap.copyOf(_modules)

    override fun bundle(moduleName: String, projectPath: String, variantName: String) {
        if (_modules.containsKey(moduleName)) {
            throw RuntimeException("Module name '$moduleName' already used")
        }

        /*
        for each project, create a specific config, add the project as a dependency,
        and set an attribute to target exactly the requested variantName.
        This allows us to request different variants for different projects.
         */

        // create the config for this project
        val configName = "bundle${projectPath.replace(':','_')}"
        val config = project.configurations.create(configName)

        // add the project as a dependency
        val handler = project.dependencies
        val projectNotation = ImmutableMap.of<String, String>("path", projectPath)
        handler.add(configName, handler.project(projectNotation))

        // set the attributes on the configuration to target the bundleElements
        val factory = project.objects
        val attributes = config.attributes

        attributes.attribute(
                Usage.USAGE_ATTRIBUTE, factory.named(Usage::class.java,
                VariantDependencies.USAGE_BUNDLE))
        attributes.attribute(
                VariantAttr.ATTRIBUTE, factory.named(VariantAttr::class.java, variantName))

        // create the task that is going to zip this module's content in a zip file
        val moduleOutput = project.layout.buildDirectory.file("intermediates/$moduleName.zip")
        val task = project.tasks.create("zip$moduleName", ZipModuleTask::class.java, ZipModuleTask.ConfigAction(moduleOutput, config))

        // record this for the bundle task
        _modules[moduleName] = project.files(moduleOutput).builtBy(task)
    }
}



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

package com.android.build.gradle.internal.tasks.structureplugin

import com.android.build.gradle.BasePlugin
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskAction

open class GatherModuleInfoTask : DefaultTask() {
    private lateinit var sourceProjectName : String
    lateinit var outputProvider : Provider<RegularFile>
        private set
    private var moduleDataHolder = ModuleInfo()

    @TaskAction
    fun action() {
        moduleDataHolder.path = sourceProjectName
        moduleDataHolder.saveAsJsonTo(outputProvider.get().asFile)
    }

    class ConfigAction(private val project: Project) : Action<GatherModuleInfoTask> {
        override fun execute(task: GatherModuleInfoTask) {
            task.sourceProjectName = project.name
            task.outputProvider = project.layout.buildDirectory.file("local-module-info.json")
        }
    }
}

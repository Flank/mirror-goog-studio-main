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

package com.android.build.gradle.internal.plugins

import com.android.build.gradle.BasePlugin
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.tasks.CombineInfo
import com.android.build.gradle.internal.tasks.GatherAndroidModuleInfo
import com.android.build.gradle.internal.tasks.GatherJavaModuleInfo
import com.android.build.gradle.internal.tasks.GatherModuleInfo
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationVariant
import org.gradle.api.attributes.Usage
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin

const val USAGE_STRUCTURE = "android-debug-structure"
const val ARTIFACT_TYPE_MODULE_INFO = "android-debug-module-info"
/**
 * Plugin to gather the project structure of modules
 */
class StructurePlugin: Plugin<Project> {

    var combineTask: CombineInfo? = null
    var gatherTask: GatherModuleInfo? = null

    override fun apply(project: Project) {
        if (project == project.rootProject) {
            // create a configuration to consume the files
            val structureConfig = project.configurations.create("projectStructure").apply {
                isCanBeConsumed = false
                attributes.attribute(
                        Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, USAGE_STRUCTURE))
            }

            // create the task
            combineTask = project.tasks.create("TBD", CombineInfo::class.java).apply {
                outputProvider = project.layout.buildDirectory.file("project-structure.json")

                subModules = structureConfig
                        .incoming
                        .artifactView(
                                { config ->
                                    config.attributes({ container ->
                                        container.attribute<String>(
                                                AndroidArtifacts.ARTIFACT_TYPE,
                                                ARTIFACT_TYPE_MODULE_INFO)
                                    })
                                })
                        .artifacts
                        .artifactFiles

            }
        }

        project.plugins.withType(BasePlugin::class.java) { plugin ->
            createTask(project, GatherAndroidModuleInfo::class.java) {
                extension = plugin.extension
            }
        }

        project.plugins.withType(JavaPlugin::class.java) {
            createTask(project, GatherJavaModuleInfo::class.java)
        }

        project.plugins.withType(JavaLibraryPlugin::class.java) {
            createTask(project, GatherJavaModuleInfo::class.java)
        }
    }

    private fun <T: GatherModuleInfo> createTask(
            project: Project,
            taskClass: Class<T>,
            taskConfig: (T.() -> Unit)? = null) {

        // create a configuration to publish the file
        val structureConfig = project.configurations.create("projectStructure").apply {
            isCanBeResolved = false
            attributes.attribute(
                    Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, USAGE_STRUCTURE))
        }

        val task = project.tasks.create("gatherModuleInfo", taskClass).apply {
            // use layout to get a Provider<RegularFile> to ensure we use the correct buildDir
            outputProvider = project.layout.buildDirectory.file("local-module-info.json")
            gatherTask = this
            taskConfig?.invoke(this)
        }


        // publish the json file as an artifact
        structureConfig.outgoing.variants(
                { variants: NamedDomainObjectContainer<ConfigurationVariant> ->
                    variants.create(ARTIFACT_TYPE_MODULE_INFO) { variant ->
                        variant.artifact(task.outputProvider) { artifact ->
                            artifact.type = ARTIFACT_TYPE_MODULE_INFO
                            artifact.builtBy(task)
                        }
                    }
                })

        // if this the root project, register this output as an input to the combine task
        combineTask?.let {
            it.localModuleInfo = task.outputProvider
            it.dependsOn(task)
        }
    }
}


/*
 * Copyright (C) 2019 The Android Open Source Project
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


package com.example.apiuser

import com.android.build.gradle.BaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * An example of legacy DSL & API use that we want to ensure binary compabibility for.
 *
 * This isn't necessarily an example to copy for new API users, for that
 * see the API recipes on github.
 */
class ExamplePlugin: Plugin<Project> {
    override fun apply(project: Project) {
        var foundAndroidPlugin = false
        project.getPluginManager().withPlugin("com.android.application") {
            foundAndroidPlugin = true
            registerTask(project)
        }
        project.getPluginManager().withPlugin("com.android.library") {
            foundAndroidPlugin = true
            registerTask(project)
        }

        project.afterEvaluate {
            if (!foundAndroidPlugin) {
                throw IllegalStateException(
                        "To use com.example.apiuser.example-plugin " +
                                "you also need to apply one of the following:\n" +
                                " * com.android.application or\n" +
                                " * com.android.library" +
                                "");
            }
        }
    }

    private fun registerTask(project: Project) {
        val baseExtension: BaseExtension = project.extensions.getByType(BaseExtension::class.java)

        project.tasks.register("examplePluginTask", ExampleTask::class.java) {
            it.configure(baseExtension)
        }

        baseExtension.buildTypes.all { buildType ->
            println("Build type ${buildType.name} had signing config ${buildType.signingConfig?.name}")
            buildType.signingConfig = baseExtension.signingConfigs.getByName("debug")
        }
        baseExtension.productFlavors.all { flavor ->
            println("Product flavor ${flavor.name} had signing config ${flavor.signingConfig?.name}")
            flavor.signingConfig = baseExtension.signingConfigs.getByName("debug")
        }
    }
}

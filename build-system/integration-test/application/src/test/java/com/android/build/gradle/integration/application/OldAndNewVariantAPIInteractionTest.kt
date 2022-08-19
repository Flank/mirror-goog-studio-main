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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import org.junit.Rule
import org.junit.Test

class OldAndNewVariantAPIInteractionTest {
    @JvmField
    @Rule
    val project = GradleTestProject.builder().fromTestApp(MinimalSubProject.app("com.example.app"))
        .withPluginManagementBlock(true)
        .create()

    @Test
    fun testOldAndNewJavaOptions() {
        project.buildFile.delete()

        project.file("build.gradle.kts").writeText("""
            apply(from = "../commonHeader.gradle")
            apply(from = "../commonLocalRepo.gradle")
            plugins {
                id("com.android.application")
            }

            android {
                namespace = "com.example.app"
                compileSdkVersion(${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION})
            }

            class ArgumentProvider(val name: String, val value: String): CommandLineArgumentProvider {
                override fun asArguments() = listOf("-A{'${'$'}'}name={'${'$'}'}{value}")
            }

            abstract class ReadVariantObjectsTask : DefaultTask() {

                @get:Input
                abstract val newApiClassNames: ListProperty<String>

                @get:Input
                abstract val newApiArguments: MapProperty<String, String>

                @get:Input
                abstract val newApiArgumentProviders: ListProperty<CommandLineArgumentProvider>

                @get:Input
                abstract val oldApiClassNames: ListProperty<String>

                @get:Input
                abstract val oldApiArguments: MapProperty<String, String>

                @get:Input
                abstract val oldApiArgumentProviders: ListProperty<CommandLineArgumentProvider>

                @TaskAction
                fun execute() {
                        println("Read NEW variant object:")
                        println("\tclassNames = " + newApiClassNames.get())
                        println("\targuments = " + newApiArguments.get().keys.joinToString(", ", "{", "}"))
                        println("\targumentProviders = " + newApiArgumentProviders.get().joinToString(", ", "{", "}") { arg -> (arg as ArgumentProvider).name })
                        println("Read OLD variant object:")
                        println("\tclassNames = " + oldApiClassNames.get())
                        println("\targuments = " + oldApiArguments.get().keys.joinToString(", ", "{", "}"))
                        println("\targumentProviders = " + oldApiArgumentProviders.get().joinToString(", ", "{", "}") { arg -> (arg as ArgumentProvider).name })
                }
            }

            val taskProvider = tasks.register<ReadVariantObjectsTask>("readVariantObjects")

            androidComponents {
                onVariants(selector().withName("debug")) {

                    println("Write variant object with New API")
                    it.javaCompilation.annotationProcessor.apply {
                        classNames.add("className-SetByNewApi")
                        arguments.put("argument-SetByNewApi", "abc")
                        argumentProviders.add(ArgumentProvider("argumentProvider-SetByNewApi", "abc"))
                    }

                    taskProvider.configure {
                        newApiClassNames.set(it.javaCompilation.annotationProcessor.classNames)
                        newApiArguments.set(it.javaCompilation.annotationProcessor.arguments)
                        newApiArgumentProviders.set(it.javaCompilation.annotationProcessor.argumentProviders)
                    }
                }
            }

            android.applicationVariants.all {
                if (this.name != "debug") return@all

                println("Write variant object with Old API")
                javaCompileOptions.annotationProcessorOptions.let {
                    it.classNames.add("className-SetByOldApi")
                    it.arguments["argument-SetByOldApi"] = "abc"
                    it.compilerArgumentProviders.add(ArgumentProvider("argumentProvider-SetByOldApi", "abc"))
                }

                taskProvider.configure {
                    oldApiClassNames.set(javaCompileOptions.annotationProcessorOptions.classNames)
                    oldApiArguments.set(javaCompileOptions.annotationProcessorOptions.arguments)
                    oldApiArgumentProviders.set(javaCompileOptions.annotationProcessorOptions.compilerArgumentProviders)
                }
            }
        """.trimIndent())

        project.executor().run("readVariantObjects").stdout.use {
            assertThat(it).contains("""Read NEW variant object:
classNames = [className-SetByNewApi, className-SetByOldApi]
arguments = {argument-SetByNewApi, argument-SetByOldApi}
argumentProviders = {argumentProvider-SetByNewApi, argumentProvider-SetByOldApi}
Read OLD variant object:
classNames = [className-SetByNewApi, className-SetByOldApi]
arguments = {argument-SetByNewApi, argument-SetByOldApi}
argumentProviders = {argumentProvider-SetByNewApi, argumentProvider-SetByOldApi}""")
        }
    }
}

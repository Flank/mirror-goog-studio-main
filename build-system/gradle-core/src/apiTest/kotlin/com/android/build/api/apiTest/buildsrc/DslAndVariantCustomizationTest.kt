/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.api.apiTest.buildsrc

import com.google.common.truth.Truth
import org.junit.Test
import kotlin.test.assertNotNull

class DslAndVariantCustomizationTest : BuildSrcScriptApiTest() {

    @Test
    fun customizeAgpDsl() {
        given {
            tasksToInvoke.add("debugExample")
            addBuildSrc {
                addSource(
                    "src/main/kotlin/ExampleTask.kt",
                    // language=kotlin
                    """
                    import org.gradle.api.DefaultTask
                    import org.gradle.api.provider.Property
                    import org.gradle.api.tasks.Input
                    import org.gradle.api.tasks.TaskAction

                    abstract class ExampleTask: DefaultTask() {

                        @get:Input
                        abstract val parameters: Property<String>

                        @ExperimentalStdlibApi
                        @TaskAction
                        fun taskAction() {
                            System.out.println("Task executed with : \"${'$'}{parameters.get()}\"")
                        }
                    }
                    """.trimIndent())
                addSource(
                    "src/main/kotlin/BuildTypeExtension.kt",
                    // language=kotlin
                    """
                    /**
                    * Simple DSL extension interface that will be attached to the android build type
                    * DSL element.
                    */
                    interface BuildTypeExtension {
                        var invocationParameters: String?
                    }
                    """.trimIndent()
                )
                addSource(
                    "src/main/kotlin/ExamplePlugin.kt",
                    // language=kotlin
                    """
                    import org.gradle.api.Plugin
                    import org.gradle.api.Project
                    import org.gradle.api.plugins.ExtensionAware
                    import java.io.File
                    import com.android.build.api.dsl.ApplicationExtension
                    import com.android.build.api.variant.AndroidComponentsExtension
                    import com.android.build.api.artifact.SingleArtifact

                    abstract class ExamplePlugin: Plugin<Project> {

                        override fun apply(project: Project) {
                            // attach the BuildTypeExtension to each elements returned by the
                            // android buildTypes API.
                            val android = project.extensions.getByType(ApplicationExtension::class.java)
                            android.buildTypes.forEach {
                                (it as ExtensionAware).extensions.add(
                                    "exampleDsl",
                                    BuildTypeExtension::class.java)
                            }

                            val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
                            // hook up task configuration on the variant API.
                            androidComponents.onVariants { variant ->
                                // get the associated DSL BuildType element from the variant name
                                val buildTypeDsl = android.buildTypes.getByName(variant.name)
                                // find the extension on that DSL element.
                                val buildTypeExtension = (buildTypeDsl as ExtensionAware).extensions.findByName("exampleDsl")
                                    as BuildTypeExtension
                                // create and configure the Task using the extension DSL values.
                                project.tasks.register(variant.name + "Example", ExampleTask::class.java) { task ->
                                    task.parameters.set(buildTypeExtension.invocationParameters ?: "")
                                }
                            }
                        }
                    }
                    """.trimIndent()
                )
            }
            addModule(":app") {
                addCommonBuildFile(this,
                """
                buildTypes {
                    debug {
                        the<BuildTypeExtension>().invocationParameters = "-debug -log"
                    }
                }
                """.trimIndent())
                testingElements.addManifest(this)
                testingElements.addMainActivity(this)
            }
        }
        withDocs {
            this.index =
                """
                This recipe shows how a third party plugin can add DSL elements basically anywhere
                in the android DSL tree. This is particularly useful when such a plugin would like
                to configure behaviors specific to a build type or a product flavor by having such
                configuration directly attached to the buildTypes or Flavor declaration in the
                android dsl block.

                In this example, the BuildTypeExtension type is a DSL interface declaration which
                is attached to the Android Gradle Plugin build-type dsl element using "exampleDsl"
                namespace.

                Any DSL element definition that extend ExtensionAware can have third party
                extensions attached to it, see Android Gradle Plugin DSL javadocs.

                See full documentation on Gradle's web site at :
                https://docs.gradle.org/current/userguide/custom_plugins.html#sec:getting_input_from_the_build

                In groovy, the end-users will be able to use the extension as follow :
                android {
                    buildTypes {
                        debug {
                            exampleDsl {
                                invocationParameters = "-debug -log"
                            }
                        }
                    }
                }

                while in Kotlin, because the script are compiled, you need to use the following
                syntax :
                android {
                    buildTypes {
                        debug {
                            the<BuildTypeExtension>().invocationParameters = "-debug -log"
                        }
                    }
                }

                To be able to use the extension DSL element to configure tasks, the easiest is to
                hook up the Task creation in the onVariants API callback and look up the element
                from the android block build-type element.
                """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("Task executed with : \"-debug -log\"")
        }
    }

    @Test
    fun customizeAgpDslAndVariant() {
        given {
            tasksToInvoke.add("debugExample")
            addBuildSrc {
                addSource(
                    "src/main/kotlin/ExampleTask.kt",
                    // language=kotlin
                    """
                    import org.gradle.api.DefaultTask
                    import org.gradle.api.provider.Property
                    import org.gradle.api.tasks.Input
                    import org.gradle.api.tasks.TaskAction

                    abstract class ExampleTask: DefaultTask() {

                        @get:Input
                        abstract val parameters: Property<String>

                        @ExperimentalStdlibApi
                        @TaskAction
                        fun taskAction() {
                            System.out.println("Task executed with : \"${'$'}{parameters.get()}\"")
                        }
                    }
                    """.trimIndent())
                addSource(
                    "src/main/kotlin/VariantExtension.kt",
                    // language=kotlin
                    """
                    /**
                    * Simple Variant scoped extension interface that will be attached to the AGP
                    * variant object.
                    */
                    import org.gradle.api.provider.Property

                    interface VariantExtension {
                        /**
                         * the parameters is declared a Property<> so other plugins can declare a
                         * task providing this value that will then be determined at execution time.
                         */
                        val parameters: Property<String>
                    }
                    """.trimIndent()
                )
                addSource(
                    "src/main/kotlin/BuildTypeExtension.kt",
                    // language=kotlin
                    """
                    /**
                    * Simple DSL extension interface that will be attached to the android build type
                    * DSL element.
                    */
                    interface BuildTypeExtension {
                        var invocationParameters: String?
                    }
                    """.trimIndent()
                )
                addSource(
                    "src/main/kotlin/ProviderPlugin.kt",
                    // language=kotlin
                    """
                    import org.gradle.api.Plugin
                    import org.gradle.api.Project
                    import java.io.File
                    import com.android.build.api.dsl.ApplicationExtension
                    import com.android.build.api.variant.AndroidComponentsExtension

                    abstract class ProviderPlugin: Plugin<Project> {

                        override fun apply(project: Project) {
                            val objects = project.getObjects();

                            val android = project.extensions.getByType(ApplicationExtension::class.java)
                            android.buildTypes.forEach {
                                it.extensions.add(
                                    "exampleDsl",
                                    BuildTypeExtension::class.java)
                            }


                            val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
                            androidComponents.beforeVariants { variantBuilder ->
                                val variantExtension = objects.newInstance(VariantExtension::class.java)

                                val debug = android.buildTypes.getByName(variantBuilder.name)
                                val buildTypeExtension = debug.extensions.findByName("exampleDsl")
                                    as BuildTypeExtension
                                variantExtension.parameters.set(
                                    buildTypeExtension.invocationParameters ?: ""
                                )

                                variantBuilder.registerExtension(
                                    VariantExtension::class.java,
                                    variantExtension
                                )
                            }
                        }
                    }
                    """.trimIndent()
                )
                addSource(
                    "src/main/kotlin/ConsumerPlugin.kt",
                    // language=kotlin
                    """
                    import org.gradle.api.Plugin
                    import org.gradle.api.Project
                    import org.gradle.api.plugins.ExtensionAware
                    import java.io.File
                    import com.android.build.api.dsl.ApplicationExtension
                    import com.android.build.api.variant.AndroidComponentsExtension
                    import com.android.build.api.artifact.SingleArtifact

                    abstract class ConsumerPlugin: Plugin<Project> {

                        override fun apply(project: Project) {
                            val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
                            androidComponents.onVariants { variant ->
                                project.tasks.register(variant.name + "Example", ExampleTask::class.java) { task ->
                                    task.parameters.set(
                                        variant.getExtension(VariantExtension::class.java)?.parameters
                                    )
                                }
                            }
                        }
                    }
                    """.trimIndent()
                )
                addSource(
                    "src/main/kotlin/ExampleTask.kt",
                    // language=kotlin
                    """
                    import org.gradle.api.DefaultTask
                    import org.gradle.api.provider.Property
                    import org.gradle.api.tasks.Input
                    import org.gradle.api.tasks.TaskAction

                    abstract class ExampleTask: DefaultTask() {

                        @get:Input
                        abstract val parameters: Property<String>

                        @ExperimentalStdlibApi
                        @TaskAction
                        fun taskAction() {
                            System.out.println("Task executed with : \"${'$'}{parameters.get()}\"")
                        }
                    }
                    """.trimIndent())
            }
            addModule(":app") {
                buildFile =
                """
                plugins {
                        id("com.android.application")
                        kotlin("android")
                }

                apply<ProviderPlugin>()
                apply<ConsumerPlugin>()

                android { ${testingElements.addCommonAndroidBuildLogic()}
                    buildTypes {
                        debug {
                            the<BuildTypeExtension>().invocationParameters = "-debug -log"
                        }
                    }
                }
                """.trimIndent()
                testingElements.addManifest(this)
                testingElements.addMainActivity(this)
            }
        }
        withDocs {
            index =
                """
                Building up on the 'customizeAgpDsl' recipe, this recipe will also add an extension
                to the Android Gradle Plugin [Variant] interfaces. This is particularly useful when
                such a plugin would like to offer a Variant scoped object that can be looked up
                by other third party plugins.

                In this example, the BuildTypeExtension type is a DSL interface declaration which
                is attached to the Android Gradle Plugin build-type dsl element using "exampleDsl"
                namespace. The DSL extension is then used in the beforeVariants API to create a
                variant scoped object and register it.

                A second plugin called ConsumerPlugin (also applied on the same project) will look
                up the Variant scoped object to configure the ExampleTask. This demonstrate how
                two plugins can share variant scoped objects without making explicit direct
                connections.

                Because [VariantExtension.parameters] is declared as property, you could extend the
                example further by having a Task providing the value.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("Task executed with : \"-debug -log\"")
        }
    }

    @Test
    fun customizeAgpDslAndVariantWithConvenientAPI() {
        given {
            tasksToInvoke.add("debugExample")
            addBuildSrc {
                addSource(
                    "src/main/kotlin/ExampleTask.kt",
                    // language=kotlin
                    """
                    import org.gradle.api.DefaultTask
                    import org.gradle.api.provider.Property
                    import org.gradle.api.tasks.Input
                    import org.gradle.api.tasks.TaskAction

                    abstract class ExampleTask: DefaultTask() {

                        @get:Input
                        abstract val parameters: Property<String>

                        @ExperimentalStdlibApi
                        @TaskAction
                        fun taskAction() {
                            System.out.println("Task executed with : \"${'$'}{parameters.get()}\"")
                        }
                    }
                    """.trimIndent())
                addSource(
                    "src/main/kotlin/ExampleVariantExtension.kt",
                    // language=kotlin
                    """
                    /**
                    * Simple Variant scoped extension interface that will be attached to the AGP
                    * variant object.
                    */
                    import org.gradle.api.provider.Property
                    import com.android.build.api.variant.VariantExtension

                    interface ExampleVariantExtension: VariantExtension {
                        /**
                         * the parameters is declared a Property<> so other plugins can declare a
                         * task providing this value that will then be determined at execution time.
                         */
                        val parameters: Property<String>
                    }
                    """.trimIndent()
                )
                addSource(
                    "src/main/kotlin/BuildTypeExtension.kt",
                    // language=kotlin
                    """
                    /**
                    * Simple DSL extension interface that will be attached to the android build type
                    * DSL element.
                    */
                    interface BuildTypeExtension {
                        var invocationParameters: String?
                    }
                    """.trimIndent()
                )
                addSource(
                    "src/main/kotlin/ProviderPlugin.kt",
                    // language=kotlin
                    """
                    import org.gradle.api.Plugin
                    import org.gradle.api.Project
                    import java.io.File
                    import com.android.build.api.variant.AndroidComponentsExtension
                    import com.android.build.api.variant.DslExtension

                    abstract class ProviderPlugin: Plugin<Project> {

                        override fun apply(project: Project) {
                            project.extensions.getByType(AndroidComponentsExtension::class.java)
                                .registerExtension(
                                    DslExtension.Builder("exampleDsl")
                                        .extendBuildTypeWith(BuildTypeExtension::class.java)
                                        .build()
                                    ) { variantExtensionConfig ->
                                        project.objects.newInstance(ExampleVariantExtension::class.java).also {
                                            it.parameters.set(
                                                variantExtensionConfig.buildTypeExtension(BuildTypeExtension::class.java)
                                                    .invocationParameters
                                            )
                                        }
                                    }
                        }
                    }
                    """.trimIndent()
                )
                addSource(
                    "src/main/kotlin/ConsumerPlugin.kt",
                    // language=kotlin
                    """
                    import org.gradle.api.Plugin
                    import org.gradle.api.Project
                    import java.io.File
                    import com.android.build.api.variant.AndroidComponentsExtension

                    abstract class ConsumerPlugin: Plugin<Project> {

                        override fun apply(project: Project) {
                            project.extensions.getByType(AndroidComponentsExtension::class.java)
                                .onVariants { variant ->
                                    project.tasks.register(variant.name + "Example", ExampleTask::class.java) { task ->
                                        task.parameters.set(
                                            variant.getExtension(ExampleVariantExtension::class.java)?.parameters
                                        )
                                    }
                            }
                        }
                    }
                    """.trimIndent()
                )
                addSource(
                    "src/main/kotlin/ExampleTask.kt",
                    // language=kotlin
                    """
                    import org.gradle.api.DefaultTask
                    import org.gradle.api.provider.Property
                    import org.gradle.api.tasks.Input
                    import org.gradle.api.tasks.TaskAction

                    abstract class ExampleTask: DefaultTask() {

                        @get:Input
                        abstract val parameters: Property<String>

                        @ExperimentalStdlibApi
                        @TaskAction
                        fun taskAction() {
                            System.out.println("Task executed with : \"${'$'}{parameters.get()}\"")
                        }
                    }
                    """.trimIndent())
            }
            addModule(":app") {
                buildFile =
                    """
                plugins {
                        id("com.android.application")
                        kotlin("android")
                }

                apply<ProviderPlugin>()
                apply<ConsumerPlugin>()

                android { ${testingElements.addCommonAndroidBuildLogic()}
                    buildTypes {
                        debug {
                            the<BuildTypeExtension>().invocationParameters = "-debug -log"
                        }
                    }
                }
                """.trimIndent()
                testingElements.addManifest(this)
                testingElements.addMainActivity(this)
            }
        }
        withDocs {
            index =
                """
                Building up on the 'customizeAgpDsl' recipe, this recipe will also add an extension
                to the Android Gradle Plugin [Variant] interfaces. This is particularly useful when
                such a plugin would like to offer a Variant scoped object that can be looked up
                by other third party plugins.

                In this example, the BuildTypeExtension type is a DSL interface declaration which
                is attached to the Android Gradle Plugin build-type dsl element using "exampleDsl"
                namespace. The DSL extension is then used in the beforeVariants API to create a
                variant scoped object and register it.

                A second plugin called ConsumerPlugin (also applied on the same project) will look
                up the Variant scoped object to configure the ExampleTask. This demonstrate how
                two plugins can share variant scoped objects without making explicit direct
                connections.

                Because [VariantExtension.parameters] is declared as property, you could extend the
                example further by having a Task providing the value.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("Task executed with : \"-debug -log\"")
        }
    }
}

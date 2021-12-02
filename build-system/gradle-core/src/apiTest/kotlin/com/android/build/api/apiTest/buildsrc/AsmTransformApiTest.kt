/*
 * Copyright (C) 2020 The Android Open Source Project
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
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertNotNull

class AsmTransformApiTest: BuildSrcScriptApiTest() {

    @Test
    fun testAsmTransformApi() {
        given {
            addBuildSrc {
                addSource(
                    "src/main/kotlin/ExamplePlugin.kt",
                    // language=kotlin
                    """
                import com.android.build.api.variant.AndroidComponentsExtension
                import com.android.build.api.instrumentation.AsmClassVisitorFactory
                import com.android.build.api.instrumentation.ClassContext
                import com.android.build.api.instrumentation.ClassData
                import com.android.build.api.instrumentation.FramesComputationMode
                import com.android.build.api.instrumentation.InstrumentationParameters
                import com.android.build.api.instrumentation.InstrumentationScope
                import org.gradle.api.Plugin
                import org.gradle.api.Project
                import org.gradle.api.provider.Property
                import org.gradle.api.tasks.Input
                import org.objectweb.asm.ClassVisitor
                import org.objectweb.asm.util.TraceClassVisitor
                import java.io.File
                import java.io.PrintWriter

                abstract class ExamplePlugin : Plugin<Project> {

                    override fun apply(project: Project) {

                        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)

                        androidComponents.onVariants { variant ->
                            variant.instrumentation.transformClassesWith(ExampleClassVisitorFactory::class.java,
                                                                         InstrumentationScope.ALL) {
                                it.writeToStdout.set(true)
                            }
                            variant.instrumentation.setAsmFramesComputationMode(FramesComputationMode.COPY_FRAMES)
                        }
                    }

                    interface ExampleParams : InstrumentationParameters {
                        @get:Input
                        val writeToStdout: Property<Boolean>
                    }

                    abstract class ExampleClassVisitorFactory :
                        AsmClassVisitorFactory<ExampleParams> {

                        override fun createClassVisitor(
                            classContext: ClassContext,
                            nextClassVisitor: ClassVisitor
                        ): ClassVisitor {
                            return if (parameters.get().writeToStdout.get()) {
                                TraceClassVisitor(nextClassVisitor, PrintWriter(System.out))
                            } else {
                                TraceClassVisitor(nextClassVisitor, PrintWriter(File("trace_out")))
                            }
                        }

                        override fun isInstrumentable(classData: ClassData): Boolean {
                            return classData.className.startsWith("com.example")
                        }
                    }
                }
                """.trimIndent()
                )

                buildFile = """
                    dependencies {
                        implementation("org.ow2.asm:asm-util:7.0")
                    }
                """.trimIndent()
            }
            addModule(":app") {
                addSource(
                    "src/main/kotlin/com/example/ExampleClass.kt",
                    // language=kotlin
                    """
                        package com.example

                        class ExampleClass
                """.trimIndent()
                )
                addCommonBuildFile(this)
                testingElements.addManifest(this)
                testingElements.addMainActivity(this)
            }
        }
        withDocs {
            index =
                    // language=markdown
                """
# Transforming classes with ASM

This sample shows how to transform classes with ASM class visitors. The example transformer prints a bytecode trace
generated by TraceClassVisitor to the standard output for all classes in the package `com.example`.

## To Run
./gradlew transformDebugClassesWithAsm
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            Truth.assertThat(output).contains(
                """ |// access flags 0x31
                    |public final class com/example/ExampleClass {
                    """.trimMargin()
            )
            val transformTask = task(":app:transformDebugClassesWithAsm")
            assertNotNull(transformTask)
            Truth.assertThat(transformTask.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }
}

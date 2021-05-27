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

package com.android.build.gradle.integration.instrumentation

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests frames will be fixed when setting the frames computation mode to something other than to
 * copy frames.
 */
@RunWith(FilterableParameterized::class)
class AsmTransformApiFixFramesTest(val packageName: String) {

    companion object {

        @Parameterized.Parameters(name = "variant_api_package_{0}")
        @JvmStatic
        fun parameters() = listOf("com.android.build.api.variant", "com.android.build.api.extension")
    }

    @get:Rule
    val project = GradleTestProject.builder().fromTestProject("asmTransformApi")
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF).create()

    @Before
    fun setUp() {
        project.getSubproject(":buildSrc")
                .file("src/main/java/com/example/buildsrc/instrumentation/FramesBreakingClassVisitor.kt")
                .writeText(
                        // language=kotlin
                        """
                    package com.example.buildsrc.instrumentation

                    import org.objectweb.asm.ClassVisitor
                    import org.objectweb.asm.Label
                    import org.objectweb.asm.Opcodes.*


                    class FramesBreakingClassVisitor(
                            apiVersion: Int,
                            cv: ClassVisitor
                    ) : ClassVisitor(apiVersion, cv) {

                        override fun visit(
                                version: Int,
                                access: Int,
                                name: String?,
                                signature: String?,
                                superName: String?,
                                interfaces: Array<out String>?
                        ) {
                            super.visit(version, access, name, signature, superName, interfaces)

                            injectCode()
                        }

                        private fun injectCode() {
                            // Code below is (with manually broken stack map and maxs):
                            // public void foo() {
                            //     Object i = null;
                            //     if (i == null) {
                            //         i = new String();
                            //     } else {
                            //         i = new StringBuilder();
                            //     }
                            // }

                            val mv = visitMethod(ACC_PUBLIC, "foo", "()V", null, null)
                            mv.visitCode()
                            mv.visitInsn(ACONST_NULL)
                            mv.visitVarInsn(ASTORE, 1)
                            mv.visitVarInsn(ALOAD, 1)
                            val l0 = Label()
                            mv.visitJumpInsn(IFNONNULL, l0)
                            mv.visitTypeInsn(NEW, "java/lang/StringBuilder")
                            mv.visitInsn(DUP)
                            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false)
                            mv.visitVarInsn(ASTORE, 1)
                            val l1 = Label()
                            mv.visitJumpInsn(GOTO, l1)
                            mv.visitLabel(l0)
                            mv.visitFrame(F_APPEND, 1, arrayOf<Any>(INTEGER), 0, null)
                            mv.visitTypeInsn(NEW, "java/lang/String")
                            mv.visitInsn(DUP)
                            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/String", "<init>", "()V", false)
                            mv.visitVarInsn(ASTORE, 1)
                            mv.visitLabel(l1)
                            mv.visitFrame(F_SAME, 0, null, 0, null)
                            mv.visitInsn(RETURN)
                            mv.visitMaxs(0, 0)
                            mv.visitEnd()
                        }
                    }
                        """.trimIndent()
                )

        project.getSubproject(":buildSrc")
                .file("src/main/java/com/example/buildsrc/instrumentation/FramesBreakingClassVisitorFactory.kt")
                .writeText(
                        // language=kotlin
                        """
                    package com.example.buildsrc.instrumentation

                    import com.android.build.api.instrumentation.AsmClassVisitorFactory
                    import com.android.build.api.instrumentation.ClassContext
                    import com.android.build.api.instrumentation.ClassData
                    import com.android.build.api.instrumentation.InstrumentationParameters
                    import org.objectweb.asm.ClassVisitor

                    abstract class FramesBreakingClassVisitorFactory:
                            AsmClassVisitorFactory<InstrumentationParameters.None> {
                        override fun createClassVisitor(
                                classContext: ClassContext,
                                nextClassVisitor: ClassVisitor
                        ): ClassVisitor {
                            return FramesBreakingClassVisitor(
                                    instrumentationContext.apiVersion.get(),
                                    nextClassVisitor
                            )
                        }

                        override fun isInstrumentable(classData: ClassData): Boolean {
                            return true
                        }
                    }
                        """.trimIndent()
                )

        project.getSubproject(":app")
                .file("src/test/java/com/example/unittest/UnitTestBrokenFramesTest.kt").apply {
                    parentFile.mkdirs()
                    writeText(
                            // language=kotlin
                            """
                        package com.example.unittest

                        import org.junit.Test

                        class UnitTestBrokenFramesTest {

                            @Test
                            fun invokeInjectedMethod() {
                                this::class.java.getMethod("foo").invoke(this)
                            }
                        }
                        """.trimIndent()
                    )
                }
    }

    private fun configureVisitor(framesMode: String) {
        if (packageName != "com.android.build.api.extension") {
            TestFileUtils.searchAndReplace(
                project.getSubproject(":buildSrc")
                    .file("src/main/java/com/example/buildsrc/plugin/InstrumentationPlugin.kt"),

                "import com.android.build.api.extension.AndroidComponentsExtension",
                "import $packageName.AndroidComponentsExtension"
            )
        }

        TestFileUtils.searchAndReplace(
                project.getSubproject(":buildSrc")
                        .file("src/main/java/com/example/buildsrc/plugin/InstrumentationPlugin.kt"),
                "val androidComponentsExt = project.extensions.getByType(AndroidComponentsExtension::class.java)",
                // language=kotlin
                """
            val androidComponentsExt = project.extensions.getByType(AndroidComponentsExtension::class.java)
            androidComponentsExt.onVariants {
                    it.unitTest?.transformClassesWith(
                            FramesBreakingClassVisitorFactory::class.java,
                            InstrumentationScope.PROJECT
                    ) {}
                    it.unitTest?.setAsmFramesComputationMode($framesMode)
                }
                """.trimIndent()

        )
    }

    @Test
    fun framesShouldBeBrokenWithCopyFramesMode() {
        configureVisitor("FramesComputationMode.COPY_FRAMES")
        val result = project.executor().expectFailure().run(":app:testDebugUnitTest")
        assertThat(result.failedTasks).containsExactly(":app:testDebugUnitTest")
    }

    @Test
    fun framesShouldBeFixedWithComputeFramesForInstrumentedMethodsMode() {
        configureVisitor("FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS")
        project.executor().run(":app:testDebugUnitTest")
    }

    @Test
    fun framesShouldBeFixedWithComputeFramesForInstrumentedClassesMode() {
        configureVisitor("FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_CLASSES")
        project.executor().run(":app:testDebugUnitTest")
    }

    @Test
    fun framesShouldBeFixedWithComputeFramesForAllClassesMode() {
        configureVisitor("FramesComputationMode.COMPUTE_FRAMES_FOR_ALL_CLASSES")
        project.executor().run(":app:testDebugUnitTest")
    }
}

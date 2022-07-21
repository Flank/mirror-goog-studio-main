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

package com.android.build.gradle.integration.instrumentation

import com.android.SdkConstants.DOT_CLASS
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.AsmApiApiTestUtils
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests integration between the asm transform pipeline and the old transform.
 */
@RunWith(Parameterized::class)
class AsmTransformOldTransformIntegrationTest(private val withCoverage: Boolean) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "withCoverage_{0}")
        fun parameters() = listOf(true, false)
    }

    @get:Rule
    val project = GradleTestProject.builder().fromTestProject("asmTransformApi").create()

    /** regression test for b/232438924 */
    @Test
    fun oldTransformApiRunsAfterAsmTransform() {
        val removedClassName = "ClassExtendsAClassThatExtendsAnotherClassAndImplementsTwoInterfaces"

        FileUtils.writeToFile(
            project.getSubproject(":buildSrc").file("src/main/java/com/example/buildsrc/instrumentation/OldTransform.kt"),
            """
                package com.example.buildsrc.instrumentation

                import com.android.build.api.transform.*
                import java.nio.file.Files
                import java.nio.file.Path
                import java.util.function.Consumer

                class OldTransform : Transform() {

                    override fun getName(): String {
                        return "OldTransform"
                    }

                    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> {
                        return mutableSetOf(QualifiedContent.DefaultContentType.CLASSES)
                    }

                    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
                        return mutableSetOf(
                            QualifiedContent.Scope.PROJECT,
                            QualifiedContent.Scope.EXTERNAL_LIBRARIES,
                            QualifiedContent.Scope.SUB_PROJECTS
                        )
                    }

                    override fun getReferencedScopes(): MutableSet<in QualifiedContent.Scope>? {
                        return mutableSetOf()
                    }

                    override fun isIncremental(): Boolean {
                        return false
                    }

                    override fun transform(transformInvocation: TransformInvocation) {
                        transformInvocation.outputProvider.deleteAll()

                        transformInvocation.inputs.forEach(Consumer { input: TransformInput ->
                            input.directoryInputs.forEach(Consumer { directory: DirectoryInput ->

                                val outputFile: Path = transformInvocation.outputProvider.getContentLocation(
                                    directory.name,
                                    directory.contentTypes,
                                    directory.scopes,
                                    Format.DIRECTORY
                                ).toPath()

                                Files.walk(directory.file.toPath()).use { paths ->
                                    paths.filter { file -> !Files.isDirectory(file) }
                                        .forEach { file ->
                                            val relativeFile: Path = directory.file.toPath().relativize(file)

                                            if (!relativeFile.endsWith("${removedClassName + DOT_CLASS}")) {
                                                val output = outputFile.resolve(relativeFile)
                                                Files.createDirectories(output.parent)
                                                Files.copy(file, output)
                                            }
                                        }
                                }
                            })
                            input.jarInputs.forEach(Consumer { jarInput: JarInput ->
                                val jarFile: Path = jarInput.file.toPath()
                                val output: Path = transformInvocation.outputProvider
                                    .getContentLocation(jarInput.name, jarInput.contentTypes, jarInput.scopes, Format.JAR)
                                    .toPath().toAbsolutePath()
                                Files.createDirectories(output.parent)
                                Files.copy(jarFile, output)
                            })

                        })
                    }
                }
            """.trimIndent()
        )

        TestFileUtils.searchAndReplace(
            project.getSubproject(":buildSrc")
                .file("src/main/java/com/example/buildsrc/plugin/InstrumentationPlugin.kt"),
            "val androidComponentsExt = project.extensions.getByType(AndroidComponentsExtension::class.java)",
            // language=kotlin
            """
                project.pluginManager.withPlugin("com.android.application") {
                    val baseExtension = project.extensions.getByType(com.android.build.api.dsl.ApplicationExtension::class.java) as com.android.build.gradle.BaseExtension
                    baseExtension.registerTransform(com.example.buildsrc.instrumentation.OldTransform())
                }
                val androidComponentsExt = project.extensions.getByType(AndroidComponentsExtension::class.java)
                """.trimIndent()

        )

        if (!withCoverage) {
            TestFileUtils.searchAndReplace(
                project.getSubproject(":app").buildFile,
                "enableAndroidTestCoverage true",
                "enableAndroidTestCoverage false"
            )
            TestFileUtils.searchAndReplace(
                project.getSubproject(":app").buildFile,
                "enableUnitTestCoverage true",
                "enableUnitTestCoverage false"
            )
        }

        AsmApiApiTestUtils.configureExtensionForAnnotationAddingVisitor(project)
        AsmApiApiTestUtils.configureExtensionForInterfaceAddingVisitor(project)

        val result =
            project.executor()
                .with(BooleanOption.LEGACY_TRANSFORM_TASK_FORCE_NON_INCREMENTAL, true)
                .run(":app:assembleDebug")

        assertThat(result.didWorkTasks).containsAtLeastElementsIn(
            listOf(
                ":app:transformClassesWithOldTransformForDebug",
                ":app:transformDebugClassesWithAsm"
            )
        )

        val apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG)

        // app classes
        AsmApiApiTestUtils.checkClassesAreInstrumented(
            apk = apk,
            classesDescriptorPackagePrefix = AsmApiApiTestUtils.appClassesDescriptorPrefix,
            expectedClasses = AsmApiApiTestUtils.projectClasses.minus(removedClassName),
            expectedAnnotatedMethods = mapOf(
                "ClassImplementsI" to listOf("f1"),
                "ClassExtendsOneClassAndImplementsTwoInterfaces" to listOf("f3"),
            ),
            expectedInstrumentedClasses = listOf(
                "ClassWithNoInterfacesOrSuperclasses",
                "ClassExtendsOneClassAndImplementsTwoInterfaces"
            )
        )
    }
}

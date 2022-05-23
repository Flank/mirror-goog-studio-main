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

package com.android.build.gradle.integration.api

import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

/**
 * Test adding a new class to the component using the PROJECT scoped class access.
 */
class LibraryAllClassesAccessTest {

    @get: Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.library"))
            .create()

    @Test
    fun `ensure tasks can be transformed with PROJECT scope`() {
        project.buildFile.appendText(
            """
        buildscript {
            dependencies {
                classpath("org.javassist:javassist:3.26.0-GA")
            }
        }
        import com.android.build.api.artifact.ScopedArtifact
        import com.android.build.api.artifact.SingleArtifact
        import com.android.build.api.variant.ScopedArtifacts.Scope

        ${addCustomizationTask()}

        androidComponents {
            onVariants(selector().all(), { variant ->
                ${registerCustomizationTask(ScopedArtifacts.Scope.PROJECT)}
            })
        }
        """.trimIndent())

        val result = project.executor().run("assembleDebug")
        Truth.assertThat(result.didWorkTasks).contains(":debugModifyPROJECTClasses")
        // check resulting APK that new classes is present in the dex.

        val apk = project.getAar("debug") {
            // check that both interfaces and original code is present in the APK.
            TruthHelper.assertThatAar(it)
                .containsClass("Lcom/example/helloworld/HelloWorld;");
            TruthHelper.assertThatAar(it)
                .containsClass("Lcom/android/api/tests/PROJECTInterface;");
        }
    }

    private fun registerCustomizationTask(
        forScope: ScopedArtifacts.Scope,
        classNameToGenerate: String = "${forScope.name}Interface"
    ) =
        """
        TaskProvider ${forScope.name.lowercase()}ScopedTask = project.tasks.register(variant.getName() + "Modify${forScope.name}Classes", ModifyClassesTask.class) { task ->
            task.getClassNameToGenerate().set("$classNameToGenerate")
        }

        variant
            .artifacts
            .forScope(Scope.${forScope.name})
            .use(${forScope.name.lowercase()}ScopedTask)
            .toTransform(
                ScopedArtifact.CLASSES.INSTANCE,
                ModifyClassesTask::getInputJars,
                ModifyClassesTask::getInputDirectories,
                ModifyClassesTask::getOutputClasses
            )
        """

    private fun addCustomizationTask() =
        """
        import javassist.ClassPool
        import javassist.CtClass
        import java.util.jar.*
        import java.nio.file.Files
        import java.nio.file.Paths

        abstract class ModifyClassesTask extends DefaultTask {
            @OutputFile
            abstract RegularFileProperty getOutputClasses()

            @InputFiles
            abstract ListProperty<RegularFile> getInputJars()

            @InputFiles
            abstract ListProperty<Directory> getInputDirectories()

            @Input
            abstract Property<String> getClassNameToGenerate()

            @TaskAction
            void taskAction() {

                ClassPool pool = new ClassPool(ClassPool.getDefault())
                JarOutputStream jarOutput = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(
                    getOutputClasses().get().getAsFile()
                )));
                getInputJars().get().each { regularFile ->
                    System.out.println("In jar handling " + regularFile)
                    new JarFile(regularFile.getAsFile()).withCloseable { jarFile ->
                        Enumeration<JarEntry> jarEntries = jarFile.entries();
                        while(jarEntries.hasMoreElements()) {
                            JarEntry jarEntry = jarEntries.nextElement();
                            if (!jarEntry.getName().startsWith("META-INF/MANIFEST")  && !jarEntry.isDirectory()) {\
                                jarOutput.putNextEntry(new JarEntry(jarEntry.getName()))
                                jarFile.getInputStream(jarEntry).withCloseable { is ->
                                    is.transferTo(jarOutput)
                                }
                                jarOutput.closeEntry()
                            }
                        }
                    }
                }
                getInputDirectories().get().each { directory ->
                    Files.walk(directory.getAsFile().toPath()).each { source ->
                        if (source.toFile().isFile()) {
                            String fileName = directory.getAsFile().toPath().relativize(source)
                                .toString()
                                .replace(File.separatorChar, '/' as char)
                            println("Handling " + fileName)
                            jarOutput.putNextEntry(new JarEntry(fileName))
                            (new FileInputStream(source.toFile())).withCloseable { is ->
                                is.transferTo(jarOutput)
                            }
                            jarOutput.closeEntry()
                        }
                    }
                }
                CtClass interfaceClass = pool.makeInterface("com.android.api.tests." + getClassNameToGenerate().get());
                println("Adding ${'$'}interfaceClass")
                jarOutput.putNextEntry(new JarEntry("com/android/api/tests/" + getClassNameToGenerate().get() + ".class"))
                jarOutput.write(interfaceClass.toBytecode())
                jarOutput.closeEntry()

                jarOutput.close()
            }
        }
        """.trimIndent()
}

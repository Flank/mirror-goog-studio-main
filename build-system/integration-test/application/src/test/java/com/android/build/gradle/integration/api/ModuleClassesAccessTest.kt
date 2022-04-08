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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import  com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class ModuleClassesAccessTest {

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.application"))
            .create()


    @Test
    fun `ensure merging task not invoked when moduleClasses are not requested`() {
        val result = project.executor().run("assembleDebug")
        Truth.assertThat(result.didWorkTasks).doesNotContain(":mergeDebugProjectClasses")
    }

    @Test
    fun `ensure merging task is invoked when moduleClasses are transformed`() {
        project.buildFile.appendText("""
buildscript {
    dependencies {
        classpath("org.javassist:javassist:3.26.0-GA")
    }
}

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts.Scope;
import javassist.ClassPool
import javassist.CtClass
import java.util.jar.*;

abstract class ModifyClassesTask extends DefaultTask {
    @OutputFile
    abstract RegularFileProperty getOutputClasses()

    @InputFiles
    abstract ListProperty<RegularFile> getInputJars()

    @InputFiles
    abstract ListProperty<Directory> getInputDirectories()

    @TaskAction
    void taskAction() {

        ClassPool pool = new ClassPool(ClassPool.getDefault())

        JarOutputStream jarOutput = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(
            getOutputClasses().get().getAsFile()
        )));
        getInputJars().get().forEach { regularFile ->

            JarFile jarFile = new JarFile(regularFile.getAsFile());
            Enumeration<JarEntry> jarEntries = jarFile.entries();
            while(jarEntries.hasMoreElements()) {
                JarEntry jarEntry = jarEntries.nextElement();
                if (!jarEntry.isDirectory()) {
                    System.out.println("Putting from jar " + jarEntry.getName())
                    jarOutput.putNextEntry(new JarEntry(jarEntry.getName()))
                    jarFile.getInputStream(jarEntry).withCloseable { is ->
                        is.transferTo(jarOutput)
                    }
                    jarOutput.closeEntry()
                }
            }
            jarFile.close()

        }

        getInputDirectories().get().forEach { directory ->
            directory.getAsFile().eachFileRecurse (groovy.io.FileType.FILES) { file ->
                String relativePath = directory.getAsFile().toURI().relativize(file.toURI()).getPath();
                System.out.println("Putting from file " + relativePath.replace(File.separatorChar, '/' as char))
                jarOutput.putNextEntry(new JarEntry(relativePath.replace(File.separatorChar, '/' as char)))
                    new FileInputStream(file).withCloseable { is ->
                        is.transferTo(jarOutput)
                    }
                    jarOutput.closeEntry()
            }
        }
        CtClass interfaceClass = pool.makeInterface("com.android.api.tests.SomeInterface");
        println("Adding ${'$'}interfaceClass")
        jarOutput.putNextEntry(new JarEntry("com/android/api/tests/SomeInterface.class"))
        jarOutput.write(interfaceClass.toBytecode())
        jarOutput.closeEntry()

        jarOutput.close()
    }
}

androidComponents {
    onVariants(selector().all(), { variant ->
        TaskProvider<?> taskProvider = project.tasks.register(variant.getName() + "ModifyClasses", ModifyClassesTask.class)
        variant.artifacts.forScope(Scope.PROJECT).use(taskProvider)
            .toTransform(
                ScopedArtifact.CLASSES.INSTANCE,
                ModifyClassesTask::getInputJars,
                ModifyClassesTask::getInputDirectories,
                ModifyClassesTask::getOutputClasses
            )
    })
}
        """.trimIndent())

        val result = project.executor().run("assembleDebug")
        Truth.assertThat(result.didWorkTasks).contains(":debugModifyClasses")
        // check resulting APK that new classes is present in the dex.

        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        TruthHelper.assertThatApk(apk)
            .containsClass("Lcom/android/api/tests/SomeInterface;");
        TruthHelper.assertThatApk(apk)
            .containsClass("Lcom/example/helloworld/HelloWorld;");
    }

    @Test
    fun `ensure deprecated artifact types are still functional`() {
        project.file("src/main/java/com/android/api/tests/SomeSource.java").also {
            it.parentFile.mkdirs()
            it.writeText(
                """
            package com.android.api.tests;

            class SomeSource {
                public String toString() {
                    return "Something !";
                }
            }
            """.trimIndent()
            )
        }
        project.buildFile.appendText("""
buildscript {
    dependencies {
        classpath("org.javassist:javassist:3.26.0-GA")
    }
}

import javassist.ClassPool
import javassist.CtClass
import java.util.jar.*;
import com.android.build.api.artifact.MultipleArtifact;

import org.gradle.api.DefaultTask;
 import org.gradle.api.tasks.TaskAction;
import javassist.ClassPool;
import javassist.CtClass;

abstract class AddClassesTask extends DefaultTask {

    @OutputFiles
    abstract DirectoryProperty getOutput();

    @TaskAction
    void taskAction() {

        ClassPool pool = new ClassPool(ClassPool.getDefault());
        CtClass interfaceClass = pool.makeInterface("com.android.api.tests.SomeInterface");
        System.out.println("Adding ${'$'}interfaceClass");
        interfaceClass.writeFile(output.get().asFile.absolutePath);
    }
}

androidComponents {
    onVariants(selector().all(), { variant ->
        TaskProvider<AddClassesTask> taskProvider = project.tasks.register(variant.getName() + "AddAllClasses", AddClassesTask.class)
        variant.artifacts.use(taskProvider)
            .wiredWith( { it.getOutput() })
            .toAppendTo(MultipleArtifact.ALL_CLASSES_DIRS.INSTANCE)
    })
}
""".trimIndent())

        val result = project.executor().run("assembleDebug")
        Truth.assertThat(result.didWorkTasks).contains(":debugAddAllClasses")
        // check resulting APK that new classes is present in the dex.

        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        TruthHelper.assertThatApk(apk)
            .containsClass("Lcom/android/api/tests/SomeInterface;");
        TruthHelper.assertThatApk(apk)
            .containsClass("Lcom/android/api/tests/SomeSource;");
    }
}

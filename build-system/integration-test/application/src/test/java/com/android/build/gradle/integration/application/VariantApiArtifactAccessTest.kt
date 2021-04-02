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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertNotNull

class VariantApiArtifactAccessTest {

    @JvmField
    @Rule
    val tmp = TemporaryFolder()

    val app = MinimalSubProject.app("com.example.test")
        .appendToBuild("""
import com.android.build.api.artifact.SingleArtifact

abstract class CustomTask extends DefaultTask {

    @OutputFile
    abstract RegularFileProperty getOutputFile()

    @InputFiles
    abstract DirectoryProperty getApkLocation()

    @TaskAction
    public void provideVersionCode() {
      System.out.println("Custom Task invoked, writing at " + getOutputFile().getAsFile().get().getAbsolutePath())
      FileWriter fw = new FileWriter(getOutputFile().getAsFile().get())
      apkLocation.asFile.get().listFiles().each { file ->
        if (file.getName().endsWith(".apk")) {
          fw.write(file.getAbsolutePath())
        }
      }
      fw.write()
      fw.close()
    }
}


androidComponents {
    onVariants(selector().all(), {
        TaskProvider customTaskProvider = tasks.register(name + "CustomTask", CustomTask.class)
        customTaskProvider.configure {
            task ->
                task.getApkLocation().set(artifacts.get(SingleArtifact.APK.INSTANCE))
                Provider<Directory> outputDir = getProject().getLayout().getBuildDirectory()
                task.getOutputFile().set(outputDir.file(name + "/out.txt"))
        }
    })
}
    """.trimIndent())

    @JvmField
    @Rule
    val project = GradleTestProject.builder()
        .fromTestApp(
            MultiModuleTestProject.builder()
                .subproject(":app", app)
                .build()
        ).create()

    /**
     * Test that exercise the [artifacts.get()] API to wire a [Task] that will consume the
     * ArtifactType.APK and list the files into an output text file.
     */
    @Test
    fun listApk() {

        assertNotNull(project)
        project.execute("clean", "debugCustomTask")
        val outFile = File(project.projectDir, "app/build/debugCustomTask/out.txt")
        println("out is ${outFile.absolutePath}")
        assertThat(outFile).exists()
        Truth.assertThat(outFile.readText()).contains("app-debug.apk")
    }
}

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
import com.android.testutils.truth.FileSubject
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertNotNull

class VariantApiCustomizationTest {

    @JvmField
    @Rule
    val tmp = TemporaryFolder()

    val app = MinimalSubProject.app("com.example.test")
        .appendToBuild("""
abstract class CustomTask extends DefaultTask {

    String versionCode

    @Input
    String getVersionCode() { return versionCode }
    void setVersionCode(String value) { versionCode = value }

    @OutputFile
    abstract RegularFileProperty getOutputFile()

    @TaskAction
    public void provideVersionCode() {
      System.out.println("Custom Task invoked, writing at " + getOutputFile().getAsFile().get().getAbsolutePath())
      FileWriter fw = new FileWriter(getOutputFile().getAsFile().get())
      fw.write(versionCode)
      fw.close()
    }
}


android {
    onVariants { variant ->
        TaskProvider customTaskProvider = tasks.register(variant.name + "CustomTask", CustomTask.class) {
            task ->
                task.setVersionCode("123")
                task.getOutputFile().set(new File("/tmp/versionCode.txt"))
        }
        variant.outputs[0].getVersionCode().set(customTaskProvider.map {
          task ->
            FileReader fr = new FileReader(task.getOutputFile().getAsFile().get())
            String value = fr.readLine()
            fr.close()
            return Integer.parseInt(value);
        })
    }
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

    @Test
    fun setCustomVersionCode() {

        assertNotNull(project)
        project.execute("clean", "assembleDebug")
        val appProject = project.getSubproject(":app")
        // now check that resulting merged manifest has the right 123 version.
        val manifestFile = File(appProject.buildDir, "intermediates/merged_manifests/debug/AndroidManifest.xml")
        FileSubject.assertThat(manifestFile).exists()
        assertThat(manifestFile.readText()).contains("android:versionCode=\"123\"")
    }
}
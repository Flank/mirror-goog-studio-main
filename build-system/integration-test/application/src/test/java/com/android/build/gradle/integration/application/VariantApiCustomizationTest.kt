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
    String versionName

    @Input
    String getVersionCode() { return versionCode }
    void setVersionCode(String value) { versionCode = value }

    @Input
    String getVersionName() { return versionName }
    void setVersionName(String value) { versionName = value }

    @OutputFile
    abstract RegularFileProperty getVersionCodeOutputFile()

    @OutputFile
    abstract RegularFileProperty getVersionNameOutputFile()

    @TaskAction
    public void run() {
        FileWriter versionCodeWriter = new FileWriter(getVersionCodeOutputFile().getAsFile().get())
        versionCodeWriter.write(versionCode)
        versionCodeWriter.close()

        FileWriter versionNameWriter = new FileWriter(getVersionNameOutputFile().getAsFile().get())
        versionNameWriter.write(versionName)
        versionNameWriter.close()
    }
}


androidComponents {
    onVariants(selector().all(), {
        TaskProvider customTaskProvider = tasks.register(name + "CustomTask", CustomTask.class) {
            task ->
                task.setVersionCode("123")
                task.setVersionName("foo")
                task.getVersionCodeOutputFile().set(new File("/tmp/versionCode.txt"))
                task.getVersionNameOutputFile().set(new File("/tmp/versionName.txt"))
        }
        outputs[0].getVersionCode().set(customTaskProvider.flatMap { it.getVersionCodeOutputFile() }.map {
            FileReader fr = new FileReader(it.getAsFile())
            String value = fr.readLine()
            fr.close()
            return Integer.parseInt(value);
        })
        outputs[0].getVersionName().set(customTaskProvider.flatMap { it.getVersionNameOutputFile() }.map {
            FileReader fr = new FileReader(it.getAsFile())
            String value = fr.readLine()
            fr.close()
            return value;
        })
    })
}""")

    @JvmField
    @Rule
    val project = GradleTestProject.builder()
        .fromTestApp(
            MultiModuleTestProject.builder()
                .subproject(":app", app)
                .build()
        ).create()

    @Test
    fun setValuesViaVariantApi() {
        assertNotNull(project)
        project.execute("clean", "assembleDebug")
        val appProject = project.getSubproject(":app")
        // now check that resulting merged manifest has the right 123 version.
        val manifestFile = File(appProject.buildDir, "intermediates/packaged_manifests/debug/AndroidManifest.xml")
        assertThat(manifestFile).exists()
        assertThat(manifestFile.readText()).contains("android:versionCode=\"123\"")
        assertThat(manifestFile.readText()).contains("android:versionName=\"foo\"")
    }
}

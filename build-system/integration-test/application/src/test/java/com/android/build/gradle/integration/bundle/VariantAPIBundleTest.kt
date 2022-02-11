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

package com.android.build.gradle.integration.bundle

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class VariantAPIBundleTest {
    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Test
    fun testMetadataAddedFromVariantAPI() {
        project.buildFile.appendText("""
            abstract class AddMetadataInBundleTask extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getMetadataFile()

                @TaskAction
                void taskAction() {
                    getMetadataFile().get().getAsFile().write("some metadata", true)
                }
            }

            androidComponents {

                onVariants(selector().all(), { variant ->
                    Provider<Task> metadataTask = project.tasks.register(
                        variant.name + "AddMetadata",
                         AddMetadataInBundleTask.class
                    ) { task ->
                        task.getMetadataFile().set(new File(project.buildDir, "metadata.pb"))
                    }
                    variant.bundleConfig.addMetadataFile(
                        "com.foo",
                        metadataTask.flatMap { it.getMetadataFile() }
                    )
                })
            }
        """.trimIndent()
        )

        project.execute("bundleDebug")

        ZipFile(getResultBundle()).use { aab ->
            Truth.assertThat(aab.entries().toList().map { it.name })
                .contains("BUNDLE-METADATA/com.foo/metadata.pb")
        }
    }

    private fun getResultBundle(): File {
        return FileUtils.join(project.buildDir, "outputs", "bundle", "debug", "project-debug.aab")
    }
}

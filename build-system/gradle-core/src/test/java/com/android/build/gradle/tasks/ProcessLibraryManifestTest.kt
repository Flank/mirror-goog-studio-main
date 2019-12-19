/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.build.VariantOutput
import com.android.build.gradle.internal.scope.ApkData
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/**
 * Tests for {@link ProcessManifest}
 */
class ProcessLibraryManifestTest {

    @Rule @JvmField var temporaryFolder = TemporaryFolder()

    internal lateinit var task: ProcessLibraryManifest

    @Before
    @Throws(IOException::class)
    fun setUp() {

        val project: Project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        val taskProvider = project.tasks.register("fooRelease", ProcessLibraryManifest::class.java)
        task = taskProvider.get()

        task.minSdkVersion.set("1")
        task.maxSdkVersion.set(1)
        task.targetSdkVersion.set("1")
        task.versionCode.set(1)
        task.versionName.set("versionName")
        task.packageOverride.set("packageOverride")
        task.manifestPlaceholders.set(mapOf())
        task.mainSplit.set(
            ApkData.of(
                VariantOutput.OutputType.MAIN,
                emptyList(),
                1,
                "",
                "",
                "",
                "fooRelease",
                "",
                ""
            )
        )
    }

    @Test
    fun testInputsAreAnnotatedCorrectly() {
        assertThat(task.inputs.properties).containsKey("maxSdkVersion")
        assertThat(task.inputs.properties).containsKey("minSdkVersion")
        assertThat(task.inputs.properties).containsKey("targetSdkVersion")
        assertThat(task.inputs.properties).containsKey("versionCode")
        assertThat(task.inputs.properties).containsKey("versionName")
        assertThat(task.inputs.properties).containsKey("manifestPlaceholders")
        assertThat(task.inputs.properties).containsKey("packageOverride")
        assertThat(task.inputs.properties).containsEntry("mainSplitFullName", "fooRelease")
    }
}
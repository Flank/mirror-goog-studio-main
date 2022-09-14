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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.fixtures.FakeGradleDirectory
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class MergeGeneratedProguardFilesCreationActionTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun testGetSubFolder_does_not_exist() {
        val topFolder1 = FakeGradleDirectory(temporaryFolder.newFolder())
        Truth.assertThat(
            MergeGeneratedProguardFilesCreationAction.getSubFolder(topFolder1, "meta-info", "proguard")).isNull()

    }

    @Test
    fun testGetSubFolder_exists() {
        temporaryFolder.newFolder().also {
            File(File(it, "meta-inf"), "proguard").also { directory ->
                directory.mkdirs()

                val subFolder = MergeGeneratedProguardFilesCreationAction.getSubFolder(
                    FakeGradleDirectory(it),
                "meta-inf",
                    "proguard"
                )

                Truth.assertThat(subFolder).isNotNull()
                Truth.assertThat(subFolder?.asFile).isEqualTo(directory)
            }
        }
    }

    @Test
    fun testGetSubFolder_exists_case_incorrect_1() {
        temporaryFolder.newFolder().also {
            File(File(it, "META-INF"), "proguard").also { directory ->
                directory.mkdirs()

                val subFolder = MergeGeneratedProguardFilesCreationAction.getSubFolder(
                    FakeGradleDirectory(it),
                    "meta-inf",
                    "proguard"
                )

                Truth.assertThat(subFolder).isNotNull()
            }
        }
    }

    @Test
    fun testGetSubFolder_exists_case_incorrect_2() {
        temporaryFolder.newFolder().also {
            File(File(it, "meta-inf"), "proguard").also { directory ->
                directory.mkdirs()

                val subFolder = MergeGeneratedProguardFilesCreationAction.getSubFolder(
                    FakeGradleDirectory(it),
                    "META-INF",
                    "proguard"
                )

                Truth.assertThat(subFolder).isNotNull()
            }
        }
    }

    @Test
    fun testGetSubFolder_partially_exists() {
        temporaryFolder.newFolder().also {
            File(it, "META-INF").also { directory ->
                directory.mkdirs()

                val subFolder = MergeGeneratedProguardFilesCreationAction.getSubFolder(
                    FakeGradleDirectory(it),
                    "meta-inf",
                    "proguard"
                )

                Truth.assertThat(subFolder).isNull()
            }
        }
    }
}

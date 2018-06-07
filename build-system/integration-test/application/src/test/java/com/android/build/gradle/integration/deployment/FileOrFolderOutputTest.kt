/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.deployment

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class FileOrFolderOutputTest {

    @JvmField @Rule
    var project: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("basic")
        .withoutNdk()
        .create()

    @Test
    fun build() {

        project.executor()
            .run("clean", "assembleDebug")

        val apkContent: Map<String, Long> =
            project.getApk(GradleTestProject.ApkType.DEBUG).use {
                PathSubject.assertThat(it.file).isFile()
                getZipContent(it.file.toFile())
            }

        assertThat(apkContent).isNotNull()

        project.executor()
            .with(BooleanOption.DEPLOYMENT_USES_DIRECTORY, true)
            .run("assembleDebug")

        project.getApk(GradleTestProject.ApkType.DEBUG).use {
            PathSubject.assertThat(it.file).isDirectory()

            val apkContentCopy = mutableMapOf<String, Long>()
            apkContentCopy.putAll(apkContent)
            compareContent(apkContentCopy, it.file.toFile(), it.file.toFile())
            assertThat(apkContentCopy.isEmpty())
        }

        // now rebuild incrementally looking for the APK rather than folder.
        project.executor()
            .run("assembleDebug")

        project.getApk(GradleTestProject.ApkType.DEBUG).use {
            PathSubject.assertThat(it.file).isFile()

            assertThat(apkContent).containsExactlyEntriesIn(
                getZipContent(it.file.toFile()))
        }
    }

    private fun getZipContent(zip: File) : Map<String, Long> {
        val apkContent = mutableMapOf<String, Long>()
        val zipFile = ZipFile(zip)
        for (entry in zipFile.entries()) {
            if (!entry.isDirectory) {
                apkContent[entry.name] = entry.size
            }
        }
        return apkContent
    }

    /**
     * Compare recursively the directory structure starting a 'folder' with the passed map
     * of relative file path and file length.
     */
    private fun compareContent(content: MutableMap<String, Long>, folder: File, base: File) {

        folder.listFiles().forEach { file ->
            if (file.isDirectory) {
                compareContent(content, file, base)
            } else {
                val entryName = file.toRelativeString(base)
                assertThat(content.containsKey(entryName)).isTrue()
                content.remove(entryName)
            }
        }
    }
}
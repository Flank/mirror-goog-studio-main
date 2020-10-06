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

package com.android.build.gradle.internal.tasks

import com.google.common.truth.Truth.assertThat

import com.android.build.gradle.internal.signing.SigningConfigVersions
import java.io.File
import java.io.IOException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Tests for the [SigningConfigVersionsWriterTask]  */
class SigningConfigVersionsWriterTaskTest {
    @Rule
    @JvmField
    var temporaryFolder = TemporaryFolder()

    internal lateinit var project: Project
    internal lateinit var task: SigningConfigVersionsWriterTask
    lateinit var outputFile : File

    @Before
    @Throws(IOException::class)
    fun setUp() {
        val testDir = temporaryFolder.newFolder()
        outputFile = temporaryFolder.newFile()
        project = ProjectBuilder.builder().withProjectDir(testDir).build()

        task = project.tasks.create("test", SigningConfigVersionsWriterTask::class.java)
        task.outputFile.set(outputFile)
    }

    @Test
    @Throws(IOException::class)
    fun testWithOverrides() {
        task.enableV1Signing.set(false)
        task.enableV2Signing.set(false)
        task.enableV3Signing.set(true)
        task.enableV4Signing.set(true)
        task.overrideEnableV1Signing.set(true)
        task.overrideEnableV2Signing.set(true)

        task.doTaskAction()

        val loadedSigningConfigVersions = SigningConfigUtils.loadSigningConfigVersions(outputFile)
        assertThat(loadedSigningConfigVersions).isEqualTo(
            SigningConfigVersions(
                enableV1Signing = true,
                enableV2Signing = true,
                enableV3Signing = true,
                enableV4Signing = true
            )
        )
    }

    @Test
    @Throws(IOException::class)
    fun testWithoutOverrides() {
        task.enableV1Signing.set(false)
        task.enableV2Signing.set(false)
        task.enableV3Signing.set(true)
        task.enableV4Signing.set(true)

        task.doTaskAction()

        val loadedSigningConfigVersions = SigningConfigUtils.loadSigningConfigVersions(outputFile)
        assertThat(loadedSigningConfigVersions).isEqualTo(
            SigningConfigVersions(
                enableV1Signing = false,
                enableV2Signing = false,
                enableV3Signing = true,
                enableV4Signing = true
            )
        )
    }
}

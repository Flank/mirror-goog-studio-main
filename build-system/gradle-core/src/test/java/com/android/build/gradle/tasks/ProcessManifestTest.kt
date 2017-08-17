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

import com.google.common.truth.Truth
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.MockitoAnnotations
import java.io.IOException

/**
 * Tests for {@link ProcessManifest}
 */
class ProcessManifestTest {

    @Rule @JvmField var temporaryFolder = TemporaryFolder()

    internal lateinit var task: ProcessManifest

    @Before
    @Throws(IOException::class)
    fun setUp() {

        MockitoAnnotations.initMocks(this)
        val project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()

        task = project!!.tasks.create("processManifest", ProcessManifest::class.java)
    }

    @Test()
    fun testBackwardCompatibility() {
        try {
            @Suppress("DEPRECATION")
            task.manifestOutputFile
            Assert.fail("failed to raise backward incompatible exception")
        } catch(e: RuntimeException) {
            Truth.assertThat(e.message).contains("gradle-plugin-3-0-0-migration.html");
        }
    }
}
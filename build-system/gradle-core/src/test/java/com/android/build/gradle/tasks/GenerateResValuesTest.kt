/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.build.api.variant.ResValue
import com.android.build.api.variant.impl.ResValueKeyImpl
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.generators.ResValueGenerator
import com.android.testutils.truth.PathSubject.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit test for GenerateResValues
 */
class GenerateResValuesTest {

    @get:Rule
    var temporaryFolder = TemporaryFolder()

    @Test
    fun test() {
        val testDir = temporaryFolder.newFolder()
        // To make sure we clean the output directory.
        val trashFile = File(testDir, "dummy.txt").also { it.createNewFile()}

        val project = ProjectBuilder.builder().withProjectDir(testDir).build()

        val task = project.tasks.create("test", GenerateResValues::class.java)
        task.items.put(
            ResValueKeyImpl("string", "VALUE_DEFAULT"), ResValue("1")
        )
        task.resOutputDir = testDir
        task.analyticsService.set(FakeNoOpAnalyticsService())

        task.taskAction()

        val output = File(testDir, "values/" + ResValueGenerator.RES_VALUE_FILENAME_XML)
        assertThat(output).contentWithUnixLineSeparatorsIsExactly(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>

                    <!-- Automatically generated file. DO NOT MODIFY -->

                    <!-- Added from the variant API -->
                    <string name="VALUE_DEFAULT" translatable="false">1</string>

                </resources>""".trimIndent()
        )
        assertThat(trashFile).doesNotExist()
    }
}

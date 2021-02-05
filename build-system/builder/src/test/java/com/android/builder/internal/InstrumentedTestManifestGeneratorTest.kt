/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.builder.internal

import com.android.testutils.truth.PathSubject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.Exception

class InstrumentedTestManifestGeneratorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /**
     * Tests the common case, making sure the template resource is packaged correctly.
     */
    @Test
    @kotlin.jvm.Throws(Exception::class)
    fun generate() {
        val destination = temporaryFolder.newFile()
        val generator = InstrumentedTestManifestGenerator(
            outputFile = destination,
            packageName = "com.example.test",
            minSdkVersion = "19",
            targetSdkVersion = "24",
            testedPackageName = "com.example",
            testRunnerName = "android.support.test.runner.AndroidJUnitRunner",
            handleProfiling = false,
            functionalTest = false
        )
        generator.generate()
        PathSubject.assertThat(destination).isFile()
    }
}

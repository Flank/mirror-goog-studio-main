/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency

import com.android.build.gradle.internal.coverage.JacocoOptions
import com.android.build.gradle.internal.transforms.testdata.SomeClass
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestResources
import com.android.testutils.TestUtils
import com.google.common.annotations.VisibleForTesting
import com.google.common.truth.Truth.assertThat
import org.gradle.api.services.BuildServiceParameters
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class JacocoInstrumentationServiceTest {

    @get:Rule
    val temporaryDirectory = TemporaryFolder()

    private val mockJacocoInstrumentationService = MockJacocoInstrumentationService()

    @Test
    fun didInstrumentUsingBuildService() {
        val classesDir = temporaryDirectory.newFolder("classes")
        TestInputsGenerator.pathWithClasses(classesDir.toPath(), listOf(SomeClass::class.java))
        val someClassClassFile = classesDir.resolve(
            SomeClass::class.java.canonicalName
                .replace(".", File.separator) + ".class"
        )
        val jacocoVersion = JacocoOptions.DEFAULT_VERSION
        val jacocoJars = listOf(
            "org/jacoco/org.jacoco.core/$jacocoVersion/org.jacoco.core-$jacocoVersion.jar",
            "org/ow2/asm/asm/9.1/asm-9.1.jar",
            "org/ow2/asm/asm-commons/9.1/asm-commons-9.1.jar",
            "org/ow2/asm/asm-tree/9.1/asm-tree-9.1.jar"
        ).map(this::getTestJar)

        val instrumented = mockJacocoInstrumentationService.instrument(
            someClassClassFile.inputStream(),
            someClassClassFile.name,
            jacocoJars,
            JacocoOptions.DEFAULT_VERSION
        )

        // Verify the instrumented class is larger than the original.
        assertThat(instrumented.size).isGreaterThan(someClassClassFile.readBytes().size)
        // Check caches are created
        assertThat(mockJacocoInstrumentationService.instrumenterCache.size()).isEqualTo(1)

        // Check caches are used; shouldn't fail when passing not passing jar dependencies, since
        // the Jacoco version is only used to retrieve the Instrumenter from cache.
        mockJacocoInstrumentationService.instrument(
            someClassClassFile.inputStream(),
            someClassClassFile.name,
            emptyList(),
            JacocoOptions.DEFAULT_VERSION
        )
    }

    @After
    fun cacheShouldBeEmptyAfterClose() {
        mockJacocoInstrumentationService.close()
        assertThat(mockJacocoInstrumentationService.instrumenterCache.size()).isEqualTo(0)
    }

    private fun getTestJar(path: String) : File {
        return TestUtils.getLocalMavenRepoFile(path).toFile()
    }

    class MockJacocoInstrumentationService : JacocoInstrumentationService() {

        @VisibleForTesting
        public override val instrumenterCache = super.instrumenterCache

        override fun getParameters(): BuildServiceParameters.None {
            throw UnsupportedOperationException()
        }
    }
}

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

package com.android.build.gradle.integration.instrumentation

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.AsmApiApiTestUtils
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

/**
 * Tests integration between the asm transform pipeline and jacoco.
 */
class AsmTransformTestCoverageIntegrationTest {

    @get:Rule
    val project = GradleTestProject.builder().fromTestProject("asmTransformApi")
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF).create()

    @Test
    fun runUnitTestsWithCoverage() {
        AsmApiApiTestUtils.configureExtensionForAnnotationAddingVisitor(project)
        AsmApiApiTestUtils.configureExtensionForInterfaceAddingVisitor(project)

        project.executor().run(":app:createDebugUnitTestCoverageReport")

        val generatedCoverageReport = FileUtils.join(
            project.getSubproject(":app").buildDir,
            "reports",
            "coverage",
            "test",
            "debug",
            "index.html"
        )
        Truth.assertThat(generatedCoverageReport.exists()).isTrue()
    }

    /** regression test for b/197065758 */
    @Test
    fun testKotlinInlineFunction() {
        AsmApiApiTestUtils.configureExtensionForAnnotationAddingVisitor(project)
        AsmApiApiTestUtils.configureExtensionForInterfaceAddingVisitor(project)

        TestFileUtils.searchAndReplace(
            project.getSubproject(":app")
                .file("src/main/java/com/example/myapplication/ClassImplementsI.kt"),
            "fun f2() {}",
            """
                fun f2() {}
                inline fun inlineMethod(crossinline predicate: (String) -> Boolean): Boolean {
                    return predicate.invoke("test")
                }
            """.trimIndent()
        )

        TestFileUtils.searchAndReplace(
            project.getSubproject(":app")
                .file("src/test/java/com/example/test/UnitTest.kt"),
            "obj.f2()",
            "assertTrue(obj.inlineMethod { it == \"test\" })"
        )

        project.executor().run(":app:testDebugUnitTest")
    }
}


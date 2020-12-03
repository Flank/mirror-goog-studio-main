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

package com.android.build.gradle.integration.dexing

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.TestInputsGenerator
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files

class DuplicateClassesTest {

    val app = MinimalSubProject.app("com.example")
        .withFile("src/main/java/com/example/A.java",
            "package com.example; public class A {}")
        .withFile("src/main/java/com/example/C.java",
            "package com.example; public class C {}")
    val javaLib = MinimalSubProject.javaLibrary()
        .withFile("src/main/java/com/example/A.java",
            "package com.example; public class A {}")
        .withFile("src/main/java/com/example/B.java",
            "package com.example; public class B {}")

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(
            MultiModuleTestProject.builder()
                .subproject(":app", app)
                .subproject(":lib", javaLib)
                .dependency("implementation", app, javaLib)
                .build()
        ).create()

    private val lineSeparator: String = System.lineSeparator()

    @Test
    fun testExternalLibrariesWithDuplicateClasses() {
        val jar1 = project.getSubproject(":app").projectDir.toPath().resolve("libs/jar1.jar")
        Files.createDirectories(jar1.parent)
        TestInputsGenerator.jarWithEmptyClasses(jar1, listOf("com/example/A"))

        val jar2 = project.getSubproject(":app").projectDir.toPath().resolve("libs/jar2.jar")
        TestInputsGenerator.jarWithEmptyClasses(jar2, listOf("com/example/A", "com/example/G"))

        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile,
            """dependencies {
                        |api files('libs/jar1.jar', 'libs/jar2.jar')
                    |}""".trimMargin())
        val result = project.executor().expectFailure().run("clean", ":app:checkDebugDuplicateClasses")

        assertThat(result.failureMessage).contains(
            "Duplicate class com.example.A found in modules jar1 (jar1.jar) and jar2 (jar2.jar)$lineSeparator${lineSeparator}Go to the documentation to learn how to <a href=\"d.android.com/r/tools/classpath-sync-errors\">Fix dependency resolution errors</a>.")
    }
}

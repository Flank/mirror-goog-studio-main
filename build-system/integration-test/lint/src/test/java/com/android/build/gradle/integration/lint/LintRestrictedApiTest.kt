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

package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(FilterableParameterized::class)
class LintRestrictedApiTest(private val checkDependencies: Boolean) {

    companion object {
        @Parameterized.Parameters(name = "checkDependencies_{0}")
        @JvmStatic
        fun params() = listOf(true, false)
    }

    private val lib1 =
        MinimalSubProject.lib("com.example.one")
            .appendToBuild(
                """
                    group = 'test.group.one'

                    android {
                        lintOptions {
                            abortOnError false
                            textOutput file("lint-results.txt")
                        }
                    }
                """.trimIndent()
            ).withFile(
                "src/main/java/com/example/one/Foo.java",
                """
                    package com.example.one;

                    import com.example.two.Bar;
                    import com.example.three.Baz;

                    public class Foo {
                        public static void method() {
                            Bar.method1(); // not allowed
                            Baz.method1(); // not allowed
                            Bar.method2(); // allowed
                            Baz.method2(); // allowed
                        }
                    }
                """.trimIndent()
            )

    private val lib2 =
        MinimalSubProject.lib("com.example.two")
            .appendToBuild(
                """
                    group = 'test.group.two'

                    dependencies {
                        api 'androidx.annotation:annotation:1.1.0'
                    }
                """.trimIndent()
            ).withFile(
                "src/main/java/com/example/two/Bar.java",
                """
                    package com.example.two;

                    import androidx.annotation.RestrictTo;

                    public class Bar {
                        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
                        public static void method1() {}

                        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
                        public static void method2() {}
                    }
                """.trimIndent()
            )

    private val javaLib =
        MinimalSubProject.javaLibrary()
            .appendToBuild(
                """
                    apply plugin: 'com.android.lint'

                    group = 'test.group.three'

                    dependencies {
                        api 'androidx.annotation:annotation:1.1.0'
                    }
                """.trimIndent()
            ).withFile(
                "src/main/java/com/example/three/Baz.java",
                """
                    package com.example.three;

                    import androidx.annotation.RestrictTo;

                    public class Baz {
                        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
                        public static void method1() {}

                        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
                        public static void method2() {}
                    }
                """.trimIndent()
            )

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject(":lib1", lib1)
                    .subproject(":lib2", lib2)
                    .subproject(":java-lib", javaLib)
                    .dependency(lib1, lib2)
                    .dependency(lib1, javaLib)
                    .build()
            )
            .create()

    // Regression test for b/180408990
    @Test
    fun testRestrictedApisFromModuleDependency() {
        if (checkDependencies) {
            TestFileUtils.appendToFile(
                project.getSubproject(":lib1").buildFile,
                """
                    android {
                        lintOptions {
                            checkDependencies true
                        }
                    }
                """.trimIndent()
            )
        }
        getExecutor().run("clean", ":lib1:lintDebug")
        Truth.assertThat(project.buildResult.failedTasks).isEmpty()
        val reportFile = File(project.getSubproject(":lib1").projectDir, "lint-results.txt")
        PathSubject.assertThat(reportFile).exists()
        PathSubject.assertThat(reportFile).contains(
            "Foo.java:8: Error: Bar.method1 can only be called from within the same library group (referenced groupId=test.group.two from groupId=test.group.one) [RestrictedApi]"
        )
        PathSubject.assertThat(reportFile).contains(
            "Foo.java:9: Error: Baz.method1 can only be called from within the same library group (referenced groupId=test.group.three from groupId=test.group.one) [RestrictedApi]"
        )
        PathSubject.assertThat(reportFile).doesNotContain(
            "library group prefix"
        )
    }

    private fun getExecutor(): GradleTaskExecutor =
        project.executor().with(BooleanOption.USE_ANDROID_X, true)
}

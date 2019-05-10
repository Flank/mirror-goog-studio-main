/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.integration.desugar

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.build.gradle.integration.desugar.resources.InterfaceWithDefaultMethod
import com.android.testutils.TestInputsGenerator
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/** Checks that we are able to build library project instrumentation tests using Java 8 language features.  */
class DesugarLibraryAndroidTestTest {

    val lib = MinimalSubProject.lib("com.example.lib").apply {
        appendToBuild(
            """
            android.compileOptions.sourceCompatibility 1.8
            android.compileOptions.targetCompatibility 1.8
            dependencies {
                implementation files('libs/interface.jar')
            }
            """.trimIndent()
        )
        addFile(
            TestSourceFile(
                "src/main/java/com/example/lib/Java8Use.java", """
                    package com.example.lib;

                    import com.android.build.gradle.integration.desugar.resources.InterfaceWithDefaultMethod;

                    interface Java8Use extends InterfaceWithDefaultMethod {
                        public static void doLambda() {
                            Runnable r = () -> { };
                        }
                        default void defaultMethod() {
                            myDefaultMethod();
                        }
                    }
                    """.trimIndent()
            )
        )
        addFile(
            TestSourceFile(
                "src/androidTest/java/com/example/lib/test/Java8UseTest.java", """
                    package com.example.lib.test;

                    interface Java8UseTest {
                        public static void doLambda() {
                            Runnable r = () -> { };
                        }
                        default void defaultMethod() {}
                    }
                    """.trimIndent()
            )
        )
        addFile(
            TestSourceFile(
                "libs/interface.jar",
                TestInputsGenerator.jarWithClasses(listOf(InterfaceWithDefaultMethod::class.java))
            )
        )
    }

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(lib)
        .create()

    @Test
    fun testUsingJava8() {
        project.executor().run("assembleDebugAndroidTest")

        project.getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG).use { apk ->
            val classes = apk.mainDexFile.get().classes.keys
            assertThat(classes.filter { it.startsWith("Lcom/example/lib/Java8Use") })
                .hasSize(2)
            assertThat(classes.filter { it.startsWith("Lcom/example/lib/test/Java8UseTest") })
                .hasSize(2)
            assertThat(classes.filter { it.startsWith("Lcom/android/build/gradle/integration/desugar/resources/InterfaceWithDefaultMethod") })
                .hasSize(2)
        }

    }
}

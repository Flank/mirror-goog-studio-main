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
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import com.android.build.gradle.integration.desugar.resources.MarkerA
import com.android.build.gradle.integration.desugar.resources.MarkerB
import com.android.build.gradle.integration.desugar.resources.MarkerC
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator.jarWithClasses
import org.junit.Rule
import org.junit.Test

/** Checks that we are able to build app project instrumentation tests using Java 8 language features.
 *
 * Specifically we set up the case that causes b/130917947
 * App main -> Lib C -> Lib A, Lib B  which means when desugaring C it has A,B
 * App test -> Lib B. which means when desugaring C in the android test classpath
 * it has B,A as dependencies (i.e. in different order)
 */
class DesugarAppAndroidTestTest {

    private val app = MinimalSubProject.app("com.example.app").apply {
        appendToBuild(
            """
                    android.compileOptions.sourceCompatibility 1.8
                    android.compileOptions.targetCompatibility 1.8

                    dependencies {
                        implementation 'com.example:libc:1'
                        androidTestImplementation 'com.example:libb:1'
                    }
                    """.trimIndent()
        )
        addFile(
            TestSourceFile(
                "src/main/java/${"com.example.app".replace('.','/')}/Java8Use.java", """
                    package ${"com.example.app"};
                    interface Marker {
                        public static void doLambda() {
                            Runnable r = () -> { };
                        }
                        default void defaultMethod() {}
                     }
                    """.trimIndent()
            )
        )
    }

    private val mavenRepo =  MavenRepoGenerator(listOf(
        MavenRepoGenerator.Library("com.example:liba:1", jarWithClasses(listOf(MarkerA::class.java))),
        MavenRepoGenerator.Library("com.example:libb:1", jarWithClasses(listOf(MarkerB::class.java))),
        MavenRepoGenerator.Library("com.example:libc:1", jarWithClasses(listOf(MarkerC::class.java)), "com.example:liba:1", "com.example:libb:1")
    ))

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(app)
        .withAdditionalMavenRepo(mavenRepo)
        .create()


    @Test
    fun testUsingJava8() {
        project.executor().run(":assembleDebug", ":assembleDebugAndroidTest")

        val markerA = "Lcom/android/build/gradle/integration/desugar/resources/MarkerA;"
        val markerB = "Lcom/android/build/gradle/integration/desugar/resources/MarkerB;"
        val markerC = "Lcom/android/build/gradle/integration/desugar/resources/MarkerC;"

        project.getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            assertThat(apk).hasClass(markerA)
            assertThat(apk).hasClass(markerB)
            assertThat(apk).hasClass(markerC)
        }

        project.getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG).use { apk ->
            assertThat(apk).doesNotContainClass(markerA)
            assertThat(apk).doesNotContainClass(markerB)
            assertThat(apk).doesNotContainClass(markerC) // With b/130917947 this is present.
        }
    }
}

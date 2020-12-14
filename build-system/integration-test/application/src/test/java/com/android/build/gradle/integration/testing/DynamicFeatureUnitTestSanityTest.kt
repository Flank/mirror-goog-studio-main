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

package com.android.build.gradle.integration.testing

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.usLocaleCapitalize
import org.junit.Rule
import org.junit.Test

/**
 * Sanity test for dynamic feature unit tests.
 *
 * Check classes from the app and the dynamic features and library dependencies of each
 * can be accessed in tests at both compile and runtime.
 *
 *
 * The project setup is as follows:
 * ```
 * DF2 unit tests ------> DF2 ------> DF1 ------> App
 *                         \-> Lib2    \-> Lib1     \-> Lib0
 * ```
 */
class DynamicFeatureUnitTestSanityTest {

    private fun MinimalSubProject.withMarkerJavaFile(
        name: String = packageName!!.substringAfterLast('.').usLocaleCapitalize(),
        expression: String = "\"$name\""
    ): MinimalSubProject =
        withFile(
            "src/main/java/${packageName!!.replace('.', '/')}/$name.java",
            """
                package $packageName;
                public class $name {
                    public static String getName() {
                        return $expression;
                    }
                }
            """.trimIndent()
        )

    private val lib0 = MinimalSubProject.lib("com.example.lib0").withMarkerJavaFile()

    private val app = MinimalSubProject.app("com.example.app")
        .appendToBuild("android.dynamicFeatures = [':dynamicFeature1', ':dynamicFeature2']")
        .withMarkerJavaFile(expression = """
            "App can call lib0:\n" +
            "    " + com.example.lib0.Lib0.getName()
            """.trimIndent())

    private val lib1 = MinimalSubProject.lib("com.example.lib1").withMarkerJavaFile()

    private val df1 = MinimalSubProject.dynamicFeature("com.example.df1")
        .withMarkerJavaFile(expression = """
            "DF1 can call app:\n" +
            "    " + com.example.app.App.getName().replace("\n", "\n    ") + "\n" +
            "DF1 can call lib1:\n" +
            "    " + com.example.lib1.Lib1.getName()
            """.trimIndent())

    private val lib2 = MinimalSubProject.lib("com.example.lib2").withMarkerJavaFile()

    private val df2 = MinimalSubProject.dynamicFeature("com.example.df2")
        .withMarkerJavaFile(expression = """
            "DF2 can call DF1:\n" +
            "    " + com.example.df1.Df1.getName().replace("\n", "\n    ") + "\n" +
            "DF2 can call lib2:\n" +
            "    " + com.example.lib2.Lib2.getName()
            """.trimIndent())
        .withFile(
            "src/test/java/com/example/df2/SanityTest.java",
            """
                    package com.example.df2;

                    import static org.junit.Assert.assertEquals;
                    import org.junit.Test;

                    /** Check that we compile and run against classes from all six locations */
                    public class SanityTest {
                        @Test
                        public void testClasspathComplete() {
                            assertEquals(
                                "DF2 can call DF1:\n" +
                                "    DF1 can call app:\n" +
                                "        App can call lib0:\n" +
                                "            Lib0\n" +
                                "    DF1 can call lib1:\n" +
                                "        Lib1\n" +
                                "DF2 can call lib2:\n" +
                                "    Lib2",
                                com.example.df2.Df2.getName());
                        }
                    }
            """.trimIndent()
        )

    private val gradleBuild = MultiModuleTestProject.builder()
        .subproject(":lib0", lib0)
        .subproject(":lib1", lib1)
        .subproject(":lib2", lib2)
        .subproject(":app", app)
        .subproject(":dynamicFeature1", df1)
        .subproject(":dynamicFeature2", df2)
        .dependency(app, lib0)
        .dependency(df1, app)
        .dependency(df1, lib1)
        .dependency(df2, app) // TODO(b/151407022): should this be necessary?
        .dependency(df2, df1)
        .dependency(df2, lib2)
        .unitTestDependency(df2, "junit:junit:4.12")
        .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(gradleBuild)
        .create()

    @Test
    fun sanityTest() {
        project.executor().run(":dynamicFeature2:testDebugUnitTest")
        val resultsXml = project.file("dynamicFeature2/build/test-results/testDebugUnitTest/TEST-com.example.df2.SanityTest.xml")
        assertThat(resultsXml).contains("testClasspathComplete")
    }
}

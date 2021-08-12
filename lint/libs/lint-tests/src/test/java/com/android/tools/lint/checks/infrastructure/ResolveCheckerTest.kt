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

package com.android.tools.lint.checks.infrastructure

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.AlwaysShowActionDetector
import com.android.tools.lint.checks.ToastDetector
import com.android.tools.lint.checks.infrastructure.LintDetectorTest.compiled
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("LintDocExample")
class ResolveCheckerTest {
    private fun lint(): TestLintTask {
        return TestLintTask.lint().sdkHome(TestUtils.getSdk().toFile())
    }

    @Test
    fun testInvalidImport() {
        try {
            lint().files(
                kotlin(
                    """
                    package test.pkg
                    import java.io.File // OK
                    import invalid.Cls // ERROR
                    class Test
                    """
                )
            )
                .testModes(TestMode.DEFAULT)
                .issues(AlwaysShowActionDetector.ISSUE)
                .run()
                .expectErrorCount(1)
        } catch (e: Throwable) {
            assertEquals(
                """
                app/src/test/pkg/Test.kt:4: Error:
                Couldn't resolve this import [LintError]
                                    import invalid.Cls // ERROR
                                           ~~~~~~~~~~~

                This usually means that the unit test needs to declare a stub file or
                placeholder with the expected signature such that type resolving works.

                If this import is immaterial to the test, either delete it, or mark
                this unit test as allowing resolution errors by setting
                `allowCompilationErrors()`.

                (This check only enforces import references, not all references, so if
                it doesn't matter to the detector, you can just remove the import but
                leave references to the class in the code.)

                For more information, see the "Library Dependencies and Stubs" section in
                https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-main:lint/docs/api-guide/unit-testing.md.html
                """.trimIndent(),
                e.message?.replace(" \n", "\n")?.trim()
            )
        }
    }

    @Test
    fun testInvalidReference() {
        try {
            lint().files(
                java(
                    """
                    package test.pkg;
                    public class Test {
                        public void test() {
                            Object o1 = MenuItem.UNRELATED_REFERENCE_NOT_A_PROBLEM; // OK
                            Object o2 = MenuItem.SHOW_AS_ACTION_ALWAYS; // ERROR
                        }
                    }
                    """
                )
            )
                .testModes(TestMode.DEFAULT)
                .issues(AlwaysShowActionDetector.ISSUE)
                .run()
                .expectErrorCount(1)
        } catch (e: Throwable) {
            assertEquals(
                """
                app/src/test/pkg/Test.java:6: Error:
                Couldn't resolve this reference [LintError]
                                            Object o2 = MenuItem.SHOW_AS_ACTION_ALWAYS; // ERROR
                                                                 ~~~~~~~~~~~~~~~~~~~~~

                The tested detector returns `SHOW_AS_ACTION_ALWAYS` from `getApplicableReferenceNames()`,
                which means this reference is probably relevant to the test, but when the
                reference cannot be resolved, lint won't invoke `visitReference` on it.
                This usually means that the unit test needs to declare a stub file or
                placeholder with the expected signature such that type resolving works.

                If this reference is immaterial to the test, either delete it, or mark
                this unit test as allowing resolution errors by setting
                `allowCompilationErrors()`.

                For more information, see the "Library Dependencies and Stubs" section in
                https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-main:lint/docs/api-guide/unit-testing.md.html
                """.trimIndent(),
                e.message?.replace(" \n", "\n")?.trim()
            )
        }
    }

    @Test
    fun testInvalidCall() {
        try {
            lint().files(
                kotlin(
                    """
                    package test.pkg
                    fun test() {
                        unrelatedCallsOk()
                        android.widget.Toast.makeText() // OK
                        invalid.makeText() // ERROR
                    }
                    """
                )
            )
                .testModes(TestMode.DEFAULT)
                .issues(ToastDetector.ISSUE)
                .run()
                .expectErrorCount(1)
        } catch (e: Throwable) {
            assertEquals(
                """
                app/src/test/pkg/test.kt:5: Error:
                Couldn't resolve this call [LintError]
                                        android.widget.Toast.makeText() // OK
                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

                The tested detector returns `makeText` from `getApplicableMethodNames()`,
                which means this reference is probably relevant to the test, but when the
                call cannot be resolved, lint won't invoke `visitMethodCall` on it.
                This usually means that the unit test needs to declare a stub file or
                placeholder with the expected signature such that type resolving works.

                If this call is immaterial to the test, either delete it, or mark
                this unit test as allowing resolution errors by setting
                `allowCompilationErrors()`.

                For more information, see the "Library Dependencies and Stubs" section in
                https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-main:lint/docs/api-guide/unit-testing.md.html
                """.trimIndent(),
                e.message?.replace(" \n", "\n")?.trim()
            )
        }
    }

    @Test
    fun testValidImports() {
        lint().files(
            java(
                """
                package test.api;
                public interface JavaApi {
                    final int MY_CONSTANT = 5;
                    default void test() { }

                }
                """
            ).indented(),
            kotlin(
                """
                package test.api;
                const val foo1 = 42
                val foo2 = 43
                """
            ),
            java(
                """
                package test.pkg;
                import static test.api.JavaApi.MY_CONSTANT;
                import static test.api.JavaApi.test;
                public class Test { }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg
                import test.api.foo1
                import test.api.foo2
                fun test() { }
                """
            ).indented()
        )
            .testModes(TestMode.DEFAULT)
            .issues(AlwaysShowActionDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun testResolveTopLevelFunctionImport() {
        lint().files(
            kotlin(
                """
                package test

                import androidx.compose.runtime.remember

                fun test() {
                    val foo = remember { true }
                }
            """
            ),
            compiled(
                "libs/remember.jar",
                kotlin(
                    "androidx/compose/runtime/Remember.kt",
                    """
                    package androidx.compose.runtime

                    inline fun <T> remember(calculation: () -> T): T = calculation()

                    inline fun <T, V1> remember(
                        v1: V1,
                        calculation: () -> T
                    ): T = calculation()

                    inline fun <T, V1, V2> remember(
                        v1: V1,
                        v2: V2,
                        calculation: () -> T
                    ): T = calculation()

                    inline fun <T, V1, V2, V3> remember(
                        v1: V1,
                        v2: V2,
                        v3: V3,
                        calculation: () -> T
                    ): T = calculation()

                    inline fun <V> remember(
                        vararg inputs: Any?,
                        calculation: () -> V
                    ): V = calculation()
                    """
                ).indented(),
                0x8c16884e,
                """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAAAGNgYGBmYGBgBGJWKM3ApcYlkZiXUpSfmVKhl5yfW5BfnKpX
                VJpXkpmbKsQVlJqbmpuUWuRdwsXHxVKSWlwixBYCJL1LlBi0GADwe0pKUAAA
                AA==
                """,
                """
                androidx/compose/runtime/RememberKt.class:
                H4sIAAAAAAAAAK1WW1PbVhD+juSLfMEWJlDippSAQ8BAZBuatjHQUmZoPCEk
                EzxqpzzJtkIFtpTRkT15ZPrQp/6BvvYX9DHtQ4ehb/1Rna4uYAOKCU089u5q
                tft9u2elc/zPv3/+BWAVWwyzmtmyLaP1WmlanVcW1xW7azpGR1de6B2909Dt
                J04cjEE+1Hqa0tbMA+VZ41BvkldkkOwgimF1fufIctqGqRz2OsrLrtl0DMvk
                ynZglaoLO5cxqgyba/VHV/0b14GtLdbr1Y3qAklqYseyD5RD3WnYmkFRmmla
                juZn7FrObrfdpqhUU2s3u23PLyHBMDVAYZiObptaW6mZjk0YRpPHkWIYb/6o
                N48CkOearXV0x+32/vzVogc8ey7IQXVBTWMEmSTSyF7kC2kpjlGGmGH2rCOd
                4dZ8yHKlMYZbKeQwzjBSMAovC/31ZzWG6etGwLAdVvj/GdwPoYNTy6HTrKvl
                61guTFTolRlyYbTfD1/49+mIv3tHauWtbdbVyg1brTAcvltXH6bPX96zT3Vl
                aPN1deWGC7DC8O38/gfqrr6mhpZ3c3yqUvWqVKvei/mq63AJcwxjIVgMo2dw
                T3VHa2mO5vbW6Ym01TJXROkdPXINgfyvDdcqkdUqM+acHE8kBUlMCpMC6XRS
                kEfpd3KcPDnO3yOdFx6zmYh0ciyzSloW8lIukiNXSXx8+rP09xt2cnz6W0yQ
                I/mVi8GkmBytxOQYOaPDUuP5zbBUkoIsnQPE5ARpaRhQMv/s7UAkRTl1BS4m
                p0mnhsGO5Nd92IwPm6lMyNl8OidJLBeZZKXRkjzj6QGQzGWQ3OlPQjwZlU5/
                rZSYu/b06rG6u9MEoxvcKQW17IqKK+gJZSqjQSJ1di4+OHIYIltWizbr7I5h
                6rtd113XGm3dRbTouFE123CvA2dizzgwNadrk1144Z+zNbNncINunx8um/3T
                iyG5Z3Xtpr5tuPm3gxzVzxgIRBkCInA/UdymXwwi1uhqi/wC6Wwxl3wDWVwr
                /oEJht/dxxDrJGPUk0SpG2RPkE9CHB9hkryUhASBAV950XF8TTrOvAR4LPmA
                5Snddp/n7KLPsr4YypLwWKYp9IzFKw138IlXus/HAr6PPb6EG9JnnAoYvwv6
                zS75jBtLoYxZj7FIoWeM4iXGabL6vQoB96ced0r0gvrsdwP2BoVEScvLPvvD
                yHIIfYIWdMP7oxUl26d3a5bP6eVzehmzZAme5RYiBoXMeIWM+KMdLKUQlLIX
                LP14MXefShk2gAwt6dkAMgMDGMcc5j348QsDuOdzCwGrLwVserKKb0i3yLtA
                1RT3IdawWMMSSSzX8ABKDSWU98E4KljZxxhHlGOVI8HxGUeM4yHHHY48x+cc
                0xxTHF9wzHLc5fiSY47jkfct/AfDBOqMsAoAAA==
                """
            )
        ).issues(AlwaysShowActionDetector.ISSUE).run().expectClean()
    }
}

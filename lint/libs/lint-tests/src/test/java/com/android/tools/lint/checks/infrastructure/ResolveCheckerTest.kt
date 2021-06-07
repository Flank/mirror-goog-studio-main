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
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import org.junit.Test
import org.junit.Assert.assertEquals

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
                https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-master-dev:lint/docs/api-guide/unit-testing.md.html
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
                https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-master-dev:lint/docs/api-guide/unit-testing.md.html
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
                https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-master-dev:lint/docs/api-guide/unit-testing.md.html
                """.trimIndent(),
                e.message?.replace(" \n", "\n")?.trim()
            )
        }
    }
}

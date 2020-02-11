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

package com.android.tools.lint.checks.studio

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import org.junit.Test

class SwingUtilitiesDetectorTest {

    @Test
    fun testProblems() {
        studioLint()
            .files(
                java(
                    """
                    package test.pkg;
                    import javax.swing.SwingUtilities;

                    public class Test {
                        public void test() {
                            Runnable runnable = new Runnable() {
                                public void run() {
                                }
                            };
                            SwingUtilities.invokeLater(runnable); // WARN
                            Test.invokeLater();  // OK
                        }

                        public static void invokeLater(Runnable run) { run.run(); }
                    }
                """
                ).indented(),
                java(
                    """
                    // Stub until test infrastructure passes the right class path for non-Android
                    // modules.
                    package javax.swing;
                    public class SwingUtilities {
                        public static void invokeLater(Runnable run) { run.run(); }
                    }
                """
                )
            )
            .issues(SwingUtilitiesDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/Test.java:10: Error: Do not use SwingUtilities.invokeLater; use Application.invokeLater instead. See go/do-not-freeze. [WrongInvokeLater]
                        SwingUtilities.invokeLater(runnable); // WARN
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
            )
    }
}

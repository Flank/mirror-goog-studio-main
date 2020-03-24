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

@file:Suppress("SpellCheckingInspection")

package com.android.tools.lint.checks.studio

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import org.junit.Test

class HdpiDetectorTest {

    @Test
    fun testProblems() {
        studioLint()
            .files(
                java(
                    """
                    package test.pkg;
                    import com.intellij.util.ui.JBUI;
                    import com.intellij.ui.scale.JBUIScale;

                    public class HdpiDetectorTest {
                        private static final int FIGURE_PADDING = JBUI.scale(3); // WARN
                        private int size;
                        private float width;
                        public void test(int foo, float bar) {
                            size = JBUI.scale(foo); // WARN
                            this.width = JBUIScale.scale(bar); // WARN
                            System.out.println(JBUI.scale(foo)); // OK
                        }
                    }
                """
                ).indented(),
                kotlin(
                    """
                    package test.pkg
                    import com.intellij.util.ui.JBUI
                    import com.intellij.ui.scale.JBUIScale

                    class HdpiDetectorTestKotlin {
                        val FIGURE_PADDING = JBUI.scale(3) // WARN
                        private var size: Int = 0
                        fun test(foo: Int, bar: Float) {
                            size = JBUI.scale(foo) // WARN
                            this.width = JBUIScale.scale(bar) // WARN
                            println(JBUI.scale(foo)) // OK
                        }
                    }
                """
                ).indented(),
                // Stubs
                java(
                    """
                    package com.intellij.util.ui;
                    @SuppressWarnings("all")
                    public class JBUI {
                        // Stubs
                        public static int scale(int i) { return i; }

                    }
                    """
                ),
                java(
                    """
                    package com.intellij.ui.scale;
                    @SuppressWarnings("all")
                    public class JBUIScale {
                        public static float scale(float f) { return f; }
                    }
                    """
                )
            )
            .issues(HdpiDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/HdpiDetectorTest.java:6: Error: Do not store JBUI.scale scaled results in fields; this will not work correctly on dynamic theme or font size changes [JbUiStored]
                    private static final int FIGURE_PADDING = JBUI.scale(3); // WARN
                                                                   ~~~~~
                src/test/pkg/HdpiDetectorTest.java:10: Error: Do not store JBUI.scale scaled results in fields; this will not work correctly on dynamic theme or font size changes [JbUiStored]
                        size = JBUI.scale(foo); // WARN
                                    ~~~~~
                src/test/pkg/HdpiDetectorTest.java:11: Error: Do not store JBUI.scale scaled results in fields; this will not work correctly on dynamic theme or font size changes [JbUiStored]
                        this.width = JBUIScale.scale(bar); // WARN
                                               ~~~~~
                src/test/pkg/HdpiDetectorTestKotlin.kt:6: Error: Do not store JBUI.scale scaled results in fields; this will not work correctly on dynamic theme or font size changes [JbUiStored]
                    val FIGURE_PADDING = JBUI.scale(3) // WARN
                                              ~~~~~
                src/test/pkg/HdpiDetectorTestKotlin.kt:9: Error: Do not store JBUI.scale scaled results in fields; this will not work correctly on dynamic theme or font size changes [JbUiStored]
                        size = JBUI.scale(foo) // WARN
                                    ~~~~~
                src/test/pkg/HdpiDetectorTestKotlin.kt:10: Error: Do not store JBUI.scale scaled results in fields; this will not work correctly on dynamic theme or font size changes [JbUiStored]
                        this.width = JBUIScale.scale(bar) // WARN
                                               ~~~~~
                6 errors, 0 warnings
                """
            )
    }
}

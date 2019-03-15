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

class SwingWorkerDetectorTest {

    @Test
    fun testProblems() {
        studioLint()
            .files(
                java(
                    """
                    package test.pkg;
                    import javax.swing.SwingWorker;
                    import java.util.List;

                    public class Test {
                        public void test() {
                            SwingWorker worker = new SwingWorker<Boolean, Integer>() {
                              @Override
                              protected Boolean doInBackground() throws Exception {
                                return true;
                              }

                              @Override
                              protected void process(List<Integer> chunks) {
                              }
                            };
                        }
                    }
                """
                ).indented(),
                java(
                    """
                    // Stub until test infrastructure passes the right class path for non-Android
                    // modules.
                    package javax.swing;
                    import java.util.List;
                    @SuppressWarnings("SwingWorker")
                    public abstract class SwingWorker<T, V> {
                        protected abstract T doInBackground() throws Exception;
                        protected void process(List<V> chunks) {
                        }
                    }
                """
                )
            )
            .issues(SwingWorkerDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/Test.java:7: Error: Do not use javax.swing.SwingWorker, use com.intellij.util.concurrency.SwingWorker instead. See go/do-not-freeze. [SwingWorker]
                        SwingWorker worker = new SwingWorker<Boolean, Integer>() {
                                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
            )
    }
}

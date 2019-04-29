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

class ImplicitExecutorDetectorTest {

    @Test
    fun testProblems() {
        studioLint()
            .files(
                java(
                    """
                    package com.google.common.util.concurrent;

                    import java.util.concurrent.Executor;
                    import com.google.common.util.concurrent.ListenableFuture;

                    public class Futures {
                        public static <V> void addCallback(ListenableFuture<V> future, Runnable runnable) {}
                        public static <V> void addCallback(ListenableFuture<V> future, Runnable runnable, Executor executor) {}
                    }
                """
                ).indented(),
                java(
                    """
                    package test.pkg;

                    import java.util.concurrent.Executor;
                    import java.util.concurrent.ForkJoinPool;
                    import java.util.concurrent.CompletableFuture;
                    import com.google.common.util.concurrent.Futures;
                    import com.google.common.util.concurrent.ListenableFuture;

                    public class Test {
                        public void test(CompletableFuture<Void> future, Executor executor) {
                            future.whenCompleteAsync(null); // WARN
                            future.whenCompleteAsync(null, executor); // OK
                        }

                        public void guavaTest(ListenableFuture<Void> future, Executor executor) {
                            Futures.addCallback(future, () -> {}); // WARN
                            Futures.addCallback(future, () -> {}, executor); // OK
                        }
                    }
                """
                ).indented()
            )
            .issues(ImplicitExecutorDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/Test.java:11: Error: Use whenCompleteAsync overload with an explicit Executor instead. See go/do-not-freeze. [ImplicitExecutor]
                        future.whenCompleteAsync(null); // WARN
                        ~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/Test.java:16: Error: Use addCallback overload with an explicit Executor instead. See go/do-not-freeze. [ImplicitExecutor]
                        Futures.addCallback(future, () -> {}); // WARN
                        ~~~~~~~~~~~~~~~~~~~
                2 errors, 0 warnings
                """.trimIndent()
            )
    }
}

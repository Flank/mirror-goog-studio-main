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

class ForkJoinPoolDetectorTest {

    @Test
    fun testProblems() {
        studioLint()
            .files(
                java(
                    """
                    package test.pkg;
                    import java.util.concurrent.Executor;
                    import java.util.concurrent.ForkJoinPool;
                    import java.util.concurrent.CompletableFuture;

                    public class Test {
                        public void test(CompletableFuture<Void> future, Executor executor) {
                            new ForkJoinPool(); // WARN
                            ForkJoinPool.commonPool(); // WARN
                            future.whenCompleteAsync(null); // WARN
                            future.whenCompleteAsync(null, executor); // OK
                        }
                    }
                """
                ).indented()
            )
            .issues(ForkJoinPoolDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/Test.java:8: Error: Avoid using ForkJoinPool, directly or indirectly (for example via CompletableFuture). It has a limited set of threads on some machines which leads to hangs. See go/do-not-freeze. [ForkJoinPool]
                        new ForkJoinPool(); // WARN
                        ~~~~~~~~~~~~~~~~~~
                src/test/pkg/Test.java:9: Error: Avoid using ForkJoinPool, directly or indirectly (for example via CompletableFuture). It has a limited set of threads on some machines which leads to hangs. See go/do-not-freeze. [ForkJoinPool]
                        ForkJoinPool.commonPool(); // WARN
                        ~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/Test.java:10: Error: Avoid using ForkJoinPool, directly or indirectly (for example via CompletableFuture). It has a limited set of threads on some machines which leads to hangs. See go/do-not-freeze. [ForkJoinPool]
                        future.whenCompleteAsync(null); // WARN
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                3 errors, 0 warnings
                """
            )
    }
}

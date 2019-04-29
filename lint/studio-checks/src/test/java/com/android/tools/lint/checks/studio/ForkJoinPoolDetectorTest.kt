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
            .issues(ForkJoinPoolDetector.COMMON_FJ_POOL, ForkJoinPoolDetector.NEW_FJ_POOL)
            .run()
            .expect(
                """
                src/test/pkg/Test.java:9: Error: Avoid using common ForkJoinPool, directly or indirectly (for example via CompletableFuture). It has a limited set of threads on some machines which leads to hangs. See go/do-not-freeze. [CommonForkJoinPool]
                        ForkJoinPool.commonPool(); // WARN
                                     ~~~~~~~~~~
                src/test/pkg/Test.java:8: Error: Avoid using new ForkJoinPool instances when possible. Prefer using the IntelliJ application pool via com.intellij.openapi.application.Application#executeOnPooledThread, or for the Android Gradle Plugin use com.android.build.gradle.internal.tasks.Workers. See go/do-not-freeze. [NewForkJoinPool]
                        new ForkJoinPool(); // WARN
                        ~~~~~~~~~~~~~~~~~~
                2 errors, 0 warnings
                """
            )
    }
}

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
import org.junit.Test

class ForbiddenStudioCallDetectorTest {

    @Test
    fun testStringIntern() {
        studioLint()
            .files(
                java(
                    """
                    package test.pkg;

                    @SuppressWarnings({
                        "MethodMayBeStatic",
                        "ClassNameDiffersFromFileName",
                        "StringOperationCanBeSimplified"
                    })
                    public class Test {
                        public String intern() { return ""; }

                        public void test(String s) {
                            String s1 = this.intern(); // OK
                            String s2 = "foo".intern(); // ERROR
                            String s3 = s.intern(); // ERROR
                            //noinspection NoInterning
                            String s4 = s.intern(); // OK
                        }

                        @SuppressWarnings("NoInterning")
                        public Test() {
                            System.out.println("foo".intern()); // OK
                        }
                    }
                   """
                ).indented()
            )
            .issues(ForbiddenStudioCallDetector.INTERN)
            .run()
            .expect(
                """
                src/test/pkg/Test.java:13: Error: Do not intern strings; if reusing strings is truly necessary build a local cache [NoInterning]
                        String s2 = "foo".intern(); // ERROR
                                          ~~~~~~~~
                src/test/pkg/Test.java:14: Error: Do not intern strings; if reusing strings is truly necessary build a local cache [NoInterning]
                        String s3 = s.intern(); // ERROR
                                      ~~~~~~~~
                2 errors, 0 warnings
                """
            )
    }

    @Test
    fun testFilesCopy() {
        studioLint()
            .files(
                java(
                    """
                    package java.nio.file;

                    // Actual test
                    public class Test {
                        public void test(Path p1, Path p2, InputStream in) {
                            Files.copy(path1, path2); // ERROR
                        }
                    }

                    // Stubs
                    public interface InputStream { }
                    public interface Path { }
                    public interface CopyOption { }
                    public class Files {
                        public static Path copy(Path source, Path target, CopyOption... options) { return null; }
                    }
                    """
                ).indented(),
            )
            .issues(ForbiddenStudioCallDetector.FILES_COPY)
            .run()
            .expect(
                """
                src/java/nio/file/Test.java:6: Error: Do not use java.nio.file.Files.copy(Path, Path). Instead, use FileUtils.copyFile(Path, Path) or Kotlin's File#copyTo(File) [NoNioFilesCopy]
                        Files.copy(path1, path2); // ERROR
                              ~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
            )
    }
}

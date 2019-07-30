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

class FileComparisonDetectorTest {

    @Test
    fun testProblems() {
        studioLint()
            .files(
                java(
                    """
                    package test.pkg;
                    import java.io.File;
                    import java.util.Objects;

                    public class Test {
                        public void test(File file1, File file2, Object object1, Object object2) {
                            boolean b1 = object1.equals(object2); // OK
                            boolean b2 = object1 == object2; // OK
                            boolean b3 = object1 != object2; // OK
                            boolean b4 = file1.equals(file2); // WARN
                            boolean b5 = file1 == file2; // WARN
                            boolean b6 = file1 != file2; // WARN
                            boolean b7 = Objects.equals(file1, file2); // WARN
                        }
                    }
                    """
                ).indented()
            )
            .issues(FileComparisonDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/Test.java:10: Error: Do not compare java.io.File with equals or ==: will not work correctly on case insensitive file systems! See go/files-howto. [FileComparisons]
                        boolean b4 = file1.equals(file2); // WARN
                                     ~~~~~~~~~~~~~~~~~~~
                src/test/pkg/Test.java:11: Error: Do not compare java.io.File with equals or ==: will not work correctly on case insensitive file systems! See go/files-howto. [FileComparisons]
                        boolean b5 = file1 == file2; // WARN
                                     ~~~~~~~~~~~~~~
                src/test/pkg/Test.java:12: Error: Do not compare java.io.File with equals or ==: will not work correctly on case insensitive file systems! See go/files-howto. [FileComparisons]
                        boolean b6 = file1 != file2; // WARN
                                     ~~~~~~~~~~~~~~
                src/test/pkg/Test.java:13: Error: Do not compare java.io.File with equals or ==: will not work correctly on case insensitive file systems! See go/files-howto. [FileComparisons]
                        boolean b7 = Objects.equals(file1, file2); // WARN
                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                4 errors, 0 warnings
                """
            )
    }
}

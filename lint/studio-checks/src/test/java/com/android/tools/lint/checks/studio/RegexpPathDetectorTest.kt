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

class RegexpPathDetectorTest {

    @Test
    fun testPaths() {
        studioLint()
            .files(
                java(
                    """
                    package test.pkg;
                    import java.io.File;

                    public class Test {
                        public void test(String s, File file, String myPath) {
                            s.split(myPath); // WARN: path likely based on name
                            s.split(file.getPath()); // WARN: path from file

                            String p1 = file.getPath();
                            String p2;
                            p2 = file.getPath();
                            s.split(p1); // WARN: path from variable
                            s.split(p2); // WARN: path from variable

                            s.split("literal"); // OK

                            Pattern.compile(myPath); // WARN: path likely based on name
                            s.replaceFirst(myPath, "foo"); // WARN: path likely based on name
                            s.replaceAll(myPath, "foo"); // WARN: path likely based on name
                        }
                    }
                    """
                ).indented()
            )
            .issues(RegexpPathDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/Test.java:6: Error: Passing a path to a parameter which expects a regular expression is dangerous; on Windows path separators will look like escapes. Wrap path with Pattern.quote. [RegexPath]
                        s.split(myPath); // WARN: path likely based on name
                        ~~~~~~~~~~~~~~~
                src/test/pkg/Test.java:7: Error: Passing a path to a parameter which expects a regular expression is dangerous; on Windows path separators will look like escapes. Wrap path with Pattern.quote. [RegexPath]
                        s.split(file.getPath()); // WARN: path from file
                        ~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/Test.java:12: Error: Passing a path to a parameter which expects a regular expression is dangerous; on Windows path separators will look like escapes. Wrap path with Pattern.quote. [RegexPath]
                        s.split(p1); // WARN: path from variable
                        ~~~~~~~~~~~
                src/test/pkg/Test.java:13: Error: Passing a path to a parameter which expects a regular expression is dangerous; on Windows path separators will look like escapes. Wrap path with Pattern.quote. [RegexPath]
                        s.split(p2); // WARN: path from variable
                        ~~~~~~~~~~~
                src/test/pkg/Test.java:18: Error: Passing a path to a parameter which expects a regular expression is dangerous; on Windows path separators will look like escapes. Wrap path with Pattern.quote. [RegexPath]
                        s.replaceFirst(myPath, "foo"); // WARN: path likely based on name
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/Test.java:19: Error: Passing a path to a parameter which expects a regular expression is dangerous; on Windows path separators will look like escapes. Wrap path with Pattern.quote. [RegexPath]
                        s.replaceAll(myPath, "foo"); // WARN: path likely based on name
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~
                6 errors, 0 warnings
                """
            )
    }
}

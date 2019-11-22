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

class GradleApiUsageDetectorTest {

    @Test
    fun testProblems() {
        studioLint()
            .files(
                java(
                    """
                    package test.pkg;

                    public class Test {
                        public void test(
                        org.gradle.api.Project project,
                        org.gradle.api.DefaultProject defaultProject,
                        org.gradle.process.ExecOperations execOperations) {
                            project.exec(); // WARN
                            defaultProject.exec(); // WARN
                            java.util.function.Function<?, ?> f = defaultProject::exec; // WARN
                            org.gradle.api.Project::exec; // WARN
                            execOperations.exec(); // OK
                            execOperations::exec; // OK
                        }
                    }
                """
                ).indented(),
                java(
                    """
                    package org.gradle.api;

                    interface Project {
                        void exec();
                    }
                """
                ).indented(),
                java(
                    """
                    package org.gradle.api;

                    public class DefaultProject implements Project{
                        @Override void exec() {}
                    }
                """
                ).indented(),
                java(
                    """
                    package org.gradle.process;

                    public interface ExecOperations {
                        void exec();
                    }
                """
                ).indented()
            )
            .issues(GradleApiUsageDetector.ISSUE)
            .run()
            .expect(
                """
                    src/test/pkg/Test.java:8: Error: Avoid using org.gradle.api.Project.exec as it is incompatible with Gradle instant execution. [ProjectExecOperations]
                            project.exec(); // WARN
                                    ~~~~
                    src/test/pkg/Test.java:9: Error: Avoid using org.gradle.api.Project.exec as it is incompatible with Gradle instant execution. [ProjectExecOperations]
                            defaultProject.exec(); // WARN
                                           ~~~~
                    src/test/pkg/Test.java:10: Error: Avoid using org.gradle.api.Project.exec as it is incompatible with Gradle instant execution. [ProjectExecOperations]
                            java.util.function.Function<?, ?> f = defaultProject::exec; // WARN
                                                                  ~~~~~~~~~~~~~~~~~~~~
                    src/test/pkg/Test.java:11: Error: Avoid using org.gradle.api.Project.exec as it is incompatible with Gradle instant execution. [ProjectExecOperations]
                            org.gradle.api.Project::exec; // WARN
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    4 errors, 0 warnings
                """
            )
    }
}

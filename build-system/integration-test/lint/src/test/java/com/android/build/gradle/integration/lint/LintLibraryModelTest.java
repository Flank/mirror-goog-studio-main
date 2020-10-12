/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.integration.lint;

import static com.android.testutils.truth.FileSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.utils.FileUtils;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Assemble tests for lintLibraryModel.
 *
 * <p>To run just this test: ./gradlew :base:build-system:integration-test:application:test
 * -D:base:build-system:integration-test:application:test.single=LintLibraryModelTest
 */
@RunWith(FilterableParameterized.class)
public class LintLibraryModelTest {
    @Parameterized.Parameters(name = "{0}")
    public static LintInvocationType[] getParams() {
        return LintInvocationType.values();
    }

    @Rule
    public final GradleTestProject project;

    public LintLibraryModelTest(LintInvocationType lintInvocationType) {
        this.project = lintInvocationType.testProjectBuilder()
                .fromTestProject("lintLibraryModel")
                .create();
    }

    @Test
    public void checkLintLibraryModel() throws Exception {
        // Run twice to catch issues with configuration caching
        project.execute("clean", ":app:lintDebug");
        project.execute("clean", ":app:lintDebug");
        String expected =
                ""
                        + FileUtils.join("src", "main", "java", "com", "android", "test", "lint", "javalib", "JavaLib.java") + ":4: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                        + "    public static final String SD_CARD = \"/sdcard/something\";\n"
                        + "                                         ~~~~~~~~~~~~~~~~~~~\n"
                        + FileUtils.join("src", "main", "java", "com", "android", "test", "lint", "lintmodel", "mylibrary", "MyLibrary.java") + ":9: Warning: DateFormat character 'Y' in YYYY is the week-era-year; did you mean 'y' ? [WeekBasedYear]\n"
                        + "         DateTimeFormatter.ofPattern(\"'profile-'YYYY-MM-dd-HH-mm-ss-SSS'.rawproto'\", Locale.US); // ERROR\n"
                        + "                                               ~~~~\n"
                        + "0 errors, 2 warnings";
        File file = new File(project.getSubproject("app").getProjectDir(), "lint-results.txt");
        assertThat(file).exists();
        assertThat(file).contentWithUnixLineSeparatorsIsExactly(expected);
    }
}

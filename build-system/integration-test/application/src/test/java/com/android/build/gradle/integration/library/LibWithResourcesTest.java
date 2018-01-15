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

package com.android.build.gradle.integration.library;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import org.junit.Rule;
import org.junit.Test;

/** Tests for libraries with resources. */
public class LibWithResourcesTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("libDependency").create();

    @Test
    public void checkInvalidResourcesWithAapt1() throws Exception {
        // Build should be successful for release mode without invalid resources.
        project.executor().withEnabledAapt2(false).run("clean", "lib:assembleRelease");

        TestFileUtils.searchAndReplace(
                project.file("lib/src/main/res/values/strings.xml"),
                "<string name=\"lib_string\">SUCCESS-LIB</string>",
                "<string name=\"lib_string\">SUCCESS-LIB</string>\n"
                        + "<string name=\"oops\">@string/invalid</string>");

        // Build should be successful for debug mode even if there are invalid references.
        project.executor().withEnabledAapt2(false).run("clean", "lib:assembleDebug");

        // Build should fail for release mode if there are invalid references.
        GradleBuildResult result =
                project.executor()
                        .withEnabledAapt2(false)
                        .expectFailure()
                        .run("clean", "lib:assembleRelease");

        assertThat(result.getStderr())
                .contains(
                        "AAPT: No resource found that matches the given name "
                                + "(at 'oops' with value '@string/invalid')");

        TestFileUtils.searchAndReplace(
                project.file("lib/src/main/res/values/strings.xml"),
                "<string name=\"oops\">@string/invalid</string>",
                "");

        // Again, build should be successful for release mode without invalid resources.
        project.executor().withEnabledAapt2(false).run("lib:assembleRelease");
    }

    @Test
    public void checkInvalidResourcesWithAapt2() throws Exception {
        // Build should be successful for release mode without invalid resources.
        project.executor().withEnabledAapt2(true).run("clean", "lib:assembleRelease");

        TestFileUtils.searchAndReplace(
                project.file("lib/src/main/res/values/strings.xml"),
                "<string name=\"lib_string\">SUCCESS-LIB</string>",
                "<string name=\"lib_string\">SUCCESS-LIB</string>\n"
                        + "<string name=\"oops\">@string/invalid</string>");

        // Build should be successful for debug mode even if there are invalid references.
        project.executor().withEnabledAapt2(true).run("clean", "lib:assembleDebug");

        // Build should fail for release mode if there are invalid references.
        GradleBuildResult result =
                project.executor()
                        .withEnabledAapt2(true)
                        .expectFailure()
                        .run("clean", "lib:assembleRelease");

        assertThat(result.getStderr())
                .contains(
                        "resource string/invalid "
                                + "(aka com.android.tests.libstest.lib:string/invalid) not found");

        TestFileUtils.searchAndReplace(
                project.file("lib/src/main/res/values/strings.xml"),
                "<string name=\"oops\">@string/invalid</string>",
                "");

        // Again, build should be successful for release mode without invalid resources.
        project.executor().withEnabledAapt2(true).run("lib:assembleRelease");
    }
}

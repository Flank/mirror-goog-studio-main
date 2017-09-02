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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test for a custom jar in a library model, used by a consuming app.
 *
 * <p>The custom lint rule comes from a 3rd java module.
 */
public class LintCustomRuleTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("lintCustomRules").create();

    @Test
    public void checkCustomLint() throws Exception {
        project.executor().expectFailure().run("clean", ":app:lintDebug");
        String expected =
                "src/main/AndroidManifest.xml:11: Error: Should not specify <activity>. [UnitTestLintCheck]\n"
                        + "        <activity android:name=\".MainActivity\">\n"
                        + "        ^\n"
                        + "\n"
                        + "   Explanation for issues of type \"UnitTestLintCheck\":\n"
                        + "   This app should not have any activities.\n"
                        + "\n"
                        + "1 errors, 0 warnings";
        File file = new File(project.getSubproject("app").getTestDir(), "lint-results.txt");
        assertThat(file).exists();
        assertThat(file).contentWithUnixLineSeparatorsIsExactly(expected);
    }
}

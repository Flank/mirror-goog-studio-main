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

import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Test for a custom jar in a library model, used by a consuming app.
 *
 * <p>The custom lint rule comes from a 3rd java module.
 */
@RunWith(FilterableParameterized.class)
public class LintCustomRuleTest {

    @Parameterized.Parameters(name = "usePartialAnalysis = {0}")
    public static Object[] getParameters() {
        return new Object[] {true, false};
    }

    @Parameterized.Parameter public boolean usePartialAnalysis;

    @Rule
    public final GradleTestProject project =
            GradleTestProject.builder().fromTestProject("lintCustomRules").create();

    @Test
    public void checkCustomLint() throws Exception {
        // Run twice to catch issues with configuration caching
        getExecutor().expectFailure().run(":app:clean", ":app:lintDebug");
        getExecutor().expectFailure().run(":app:clean", ":app:lintDebug");
        File file = new File(project.getSubproject("app").getProjectDir(), "lint-results.txt");
        assertThat(file).exists();
        assertThat(file).contentWithUnixLineSeparatorsIsExactly(expected);
    }

    @Test
    public void checkCustomLintFromCompileOnlyDependency() throws Exception {
        TestFileUtils.searchAndReplace(
                project.getSubproject(":app").getBuildFile(), "implementation", "compileOnly");
        getExecutor().expectFailure().run(":app:clean", ":app:lintDebug");
        File file = new File(project.getSubproject("app").getProjectDir(), "lint-results.txt");
        assertThat(file).exists();
        assertThat(file).contentWithUnixLineSeparatorsIsExactly(expected);
    }

    private String expected =
            "src"
                    + File.separator
                    + "main"
                    + File.separator
                    + "AndroidManifest.xml:11: Error: Should not specify <activity>. [UnitTestLintCheck from LintCustomRuleTest]\n"
                    + "        <activity android:name=\".MainActivity\">\n"
                    + "        ^\n"
                    + "\n"
                    + "   Explanation for issues of type \"UnitTestLintCheck\":\n"
                    + "   This app should not have any activities.\n"
                    + "\n"
                    + "   Vendor: Google\n"
                    + "   Identifier: LintCustomRuleTest\n"
                    + "\n"
                    + "1 errors, 0 warnings";

    private GradleTaskExecutor getExecutor() {
        return project.executor().with(BooleanOption.USE_LINT_PARTIAL_ANALYSIS, usePartialAnalysis);
    }
}

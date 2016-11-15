/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleBuildResult
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.AssumeUtil
import groovy.transform.CompileStatic
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

/**
 * Check that we recognize dependency cycles.
 */
@CompileStatic
class DependencyCyclesTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("applibtest")
            .create()

    @Test
    public void cycle() {
        AssumeUtil.assumeResolveDependencyOnConfiguration();

        project.file("lib/build.gradle") << """
dependencies {
    compile project(':app')
}
"""

        GradleBuildResult result =
                project.executor().expectFailure().run("clean", ":app:assemble");

        assertThat(result.getStderr()).contains(":app -> :lib -> :app");
    }
}

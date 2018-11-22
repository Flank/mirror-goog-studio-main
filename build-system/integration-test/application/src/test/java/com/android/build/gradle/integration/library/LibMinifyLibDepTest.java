/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static com.android.testutils.truth.FileSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.options.BooleanOption;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for libMinifyLibDep. */
public class LibMinifyLibDepTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("libMinifyLibDep").create();

    @Test
    public void lint() throws IOException, InterruptedException {
        project.execute("lint");
    }

    @Test
    public void checkProguard() throws Exception {
        project.executor().run("assembleDebug");
        File mapping = project.getSubproject("lib").file("build/outputs/mapping/debug/mapping.txt");
        // Check classes are obfuscated unless it is kept by the proguard configuration.
        assertThat(mapping)
                .containsAllOf(
                        "com.android.tests.basic.StringGetter -> com.android.tests.basic.StringGetter",
                        "com.android.tests.internal.StringGetterInternal -> com.android.tests.a.a");
    }

    @Test
    public void checkTestAssemblyWithR8() throws Exception {
        project.executor().with(BooleanOption.ENABLE_R8, true).run("assembleAndroidTest");
    }

    @Test
    public void checkTestAssemblyWithProguard() throws Exception {
        project.executor().with(BooleanOption.ENABLE_R8, false).run("assembleAndroidTest");
    }
}

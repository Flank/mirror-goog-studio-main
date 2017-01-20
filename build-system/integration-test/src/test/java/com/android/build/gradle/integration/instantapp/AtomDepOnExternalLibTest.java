/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.instantapp;

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.SUPPORT_LIB_VERSION;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Test atom project with a dependency on an external library. */
public class AtomDepOnExternalLibTest {
    @ClassRule
    public static GradleTestProject sProject =
            GradleTestProject.builder().fromTestProject("singleAtom").withoutNdk().create();

    @BeforeClass
    public static void setUp() throws Exception {
        // Add an external library dependency.
        TestFileUtils.appendToFile(
                sProject.getSubproject(":atom").getBuildFile(),
                "\n"
                        + "dependencies {\n"
                        + "    compile \"com.android.support:appcompat-v7:"
                        + SUPPORT_LIB_VERSION
                        + "\"\n"
                        + "}\n");
    }

    @AfterClass
    public static void cleanUp() {
        sProject = null;
    }

    @Test
    public void build() throws Exception {
        sProject.execute("clean", ":instantApp:assembleRelease");
    }
}

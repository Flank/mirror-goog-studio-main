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

package com.android.build.gradle.integration.dependencies;

import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.IOException;
import org.gradle.tooling.BuildException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/** test for failing compilation in case of missing matching build type. */
public class BuildTypeMismatchTest {

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("projectWithModules").create();

    @Rule public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setUp() throws Exception {
        Files.write("include 'app', 'library'", project.getSettingsFile(), Charsets.UTF_8);

        TestFileUtils.appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n"
                        + "dependencies {\n"
                        + "    compile project(':library')\n"
                        + "}\n"
                        + "android {\n"
                        + "    buildTypes {\n"
                        + "        foo.initWith(buildTypes.debug)\n"
                        + "    }\n"
                        + "}");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkCompilation() throws Exception {
        thrown.expect(BuildException.class);
        project.executor().run("clean", "app:assembleFoo");
    }

    @Test
    public void checkModel() throws IOException {
        GetAndroidModelAction.ModelContainer<AndroidProject> models = project.model().getMulti();
    }
}

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

package com.android.build.gradle.integration.application;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.PerformanceTestProjects;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import java.io.IOException;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class AntennaPodSmokeTest {

    @Rule
    public GradleTestProject mainProject =
            GradleTestProject.builder().fromExternalProject("AntennaPod").create();

    private GradleTestProject project;

    @Before
    public void setUp() throws IOException {
        project = mainProject.getSubproject("AntennaPod");
        PerformanceTestProjects.initializeAntennaPod(mainProject);
    }

    @Test
    public void buildAntennaPod() throws Exception {
        GetAndroidModelAction.ModelContainer<AndroidProject> modelContainer =
                project.model().ignoreSyncIssues().getMulti();
        Map<String, AndroidProject> models = modelContainer.getModelMap();

        assertThat(models.get(":app").getSyncIssues()).hasSize(1);
        TruthHelper.assertThat(models.get(":app"))
                .hasSingleIssue(
                        SyncIssue.SEVERITY_WARNING,
                        SyncIssue.TYPE_GENERIC,
                        null,
                        "One of the plugins you are using supports Java 8 "
                                + "language features. To try the support built into"
                                + " the Android plugin, remove the following from "
                                + "your build.gradle:\n"
                                + "    apply plugin: '<plugin_name>'\n"
                                + "or\n"
                                + "    plugin {\n"
                                + "        id '<plugin_name>' version '<version>'\n"
                                + "    }\n\n"
                                + "To learn more, go to https://d.android.com/r/"
                                + "tools/java-8-support-message.html\n");

        project.executor().run("clean");

        project.executor()
                .with(BooleanOption.IDE_GENERATE_SOURCES_ONLY, true)
                .run(ModelHelper.getDebugGenerateSourcesCommands(models));

        project.executor().run(":app:assembleDebug");

    }

}

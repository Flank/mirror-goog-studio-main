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

package com.android.build.gradle.integration.application;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for dependencyChecker. */
public class DependencyCheckerTest {

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @AfterClass
    public static void tearDown() {
        project = null;
    }

    @Test
    public void checkHttpComponentsisRemoved() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n" + "dependencies.compile 'org.apache.httpcomponents:httpclient:4.1.1'\n");

        GradleBuildResult result = project.executor().expectFailure().run("assembleDebug");

        // TODO: this error message seems broken as it doesn't include the reason if the rejection.
        assertThat(result.getFailureMessage())
                .isEqualTo(
                        "Could not resolve all dependencies for configuration ':debugCompileClasspath'.");

        // FIXME we should also check the model, but due to lack of leniency, it's not possible right now.
        //
        //ModelContainer<AndroidProject> modelContainer = project.model().getSingle();
        //
        //LibraryGraphHelper helper = new LibraryGraphHelper(modelContainer);
        //
        //Variant appDebug = ModelHelper.getVariant(modelContainer.getOnlyModel().getVariants(), "debug");
        //
        //DependencyGraphs compileGraph = appDebug.getMainArtifact().getDependencyGraphs();
        //
        //assertThat(helper.on(compileGraph).withType(JAVA).mapTo(COORDINATES)).isEmpty();
    }
}

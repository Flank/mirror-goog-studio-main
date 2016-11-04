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

package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.fixture.BuildModel.Feature.FULL_DEPENDENCIES;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.MODULE;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

import com.android.build.gradle.integration.common.fixture.BuildModel;
import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction;
import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.GraphItem;
import com.android.builder.model.level2.LibraryGraph;
import com.android.ide.common.process.ProcessException;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for provided java submodule in app
 */
public class AppWithProvidedProjectJarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();

    @BeforeClass
    public static void setUp() throws IOException {
        Files.write("include 'app', 'jar'", project.getSettingsFile(), Charsets.UTF_8);

        appendToFile(project.getSubproject("app").getBuildFile(),
                "\n" +
                "dependencies {\n" +
                "    provided project(\":jar\")\n" +
                "}\n");

        project.execute("clean", ":app:assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkProvidedJarIsNotPackaged() throws IOException, ProcessException {
        assertThatApk(project.getSubproject("app").getApk("debug"))
                .doesNotContainClass("Lcom/example/android/multiproject/person/People;");
    }

    @Test
    public void checkProvidedJarIsInTheMainArtifactDependency() {
        ModelContainer<AndroidProject> modelContainer = project.model()
                .withFeature(FULL_DEPENDENCIES).getMulti();

        LibraryGraphHelper helper = new LibraryGraphHelper(modelContainer);

        Variant variant = ModelHelper.getVariant(
                modelContainer.getModelMap().get(":app").getVariants(), "debug");

        LibraryGraph compileGraph = variant.getMainArtifact().getCompileGraph();

        // assert that there is one sub-module dependency
        assertThat(helper.on(compileGraph).withType(MODULE).asList())
                .named("Module dependencies")
                .hasSize(1);
        // and that it's provided
        GraphItem javaItem = helper.on(compileGraph).withType(MODULE).asSingleGraphItem();
        assertThat(compileGraph.getProvidedLibraries())
                .named("compile provided list")
                .containsExactly(javaItem.getArtifactAddress());

        // check that the package graph does not contain the item (or anything else)
        LibraryGraph packageGraph = variant.getMainArtifact().getPackageGraph();

        assertThat(packageGraph.getDependencies()).named("package dependencies").isEmpty();
    }
}

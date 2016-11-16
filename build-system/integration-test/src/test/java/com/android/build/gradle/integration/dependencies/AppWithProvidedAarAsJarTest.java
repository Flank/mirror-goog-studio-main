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

package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.fixture.BuildModel.Feature.FULL_DEPENDENCIES;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Filter.PROVIDED;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.GRADLE_PATH;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.VARIANT;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.MODULE;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.LibraryGraph;
import com.android.ide.common.process.ProcessException;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for provided jar in library where the jar comes from a library project.
 */
public class AppWithProvidedAarAsJarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();

    @BeforeClass
    public static void setUp() throws IOException {
        Files.write("include 'app', 'library'", project.getSettingsFile(), Charsets.UTF_8);

        appendToFile(project.getSubproject("app").getBuildFile(),
                "\n" +
                "\n" +
                "dependencies {\n" +
                "    provided project(path: \":library\", configuration: \"fakeJar\")\n" +
                "}\n");

        appendToFile(project.getSubproject("library").getBuildFile(),
                "\n" +
                "configurations {\n" +
                "    fakeJar\n" +
                "}\n" +
                "\n" +
                "task makeFakeJar(type: Jar) {\n" +
                "    from \"src/main/java\"\n" +
                "}\n" +
                "\n" +
                "artifacts {\n" +
                "    fakeJar makeFakeJar\n" +
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
                .doesNotContainClass("Lcom/example/android/multiproject/library/PersonView;");
    }

    @Test
    public void checkProvidedJarIsInTheMainArtifactDependency() {
        ModelContainer<AndroidProject> modelContainer = project.model().getMulti();
        LibraryGraphHelper helper = new LibraryGraphHelper(modelContainer);

        Variant variant = ModelHelper.getVariant(
                modelContainer.getModelMap().get(":app").getVariants(), "debug");

        LibraryGraph compileGraph = variant.getMainArtifact().getCompileGraph();

        assertThat(helper.on(compileGraph).withType(MODULE).mapTo(GRADLE_PATH))
                .named("app sub-module dependencies")
                .containsExactly(":library");
    }

    @Test
    public void checkProvidedJarIsDeclaredAsProvided() {
        ModelContainer<AndroidProject> modelContainer = project.model()
                .withFeature(FULL_DEPENDENCIES).getMulti();

        LibraryGraphHelper helper = new LibraryGraphHelper(modelContainer);

        Variant variant = ModelHelper.getVariant(
                modelContainer.getModelMap().get(":app").getVariants(), "debug");

        LibraryGraph compileGraph = variant.getMainArtifact().getCompileGraph();

        final LibraryGraphHelper.Items subModules = helper.on(compileGraph).withType(MODULE);

        assertThat(subModules.mapTo(GRADLE_PATH))
                .named("app sub-module dependencies")
                .containsExactly(":library");

        assertThat(subModules.filter(PROVIDED).mapTo(GRADLE_PATH))
                .named("app sub-module provided dependencies")
                .containsExactly(":library");
    }
}

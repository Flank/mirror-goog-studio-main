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
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAar;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Filter.PROVIDED;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.GRADLE_PATH;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.VARIANT;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.MODULE;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

import com.android.build.gradle.integration.common.fixture.BuildModel;
import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.LibraryGraph;
import com.android.ide.common.process.ProcessException;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import java.io.IOException;
import java.util.Collection;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for provided jar in library
 */
public class LibWithProvidedDirectJarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();
    static ModelContainer<AndroidProject> modelContainer;
    private static LibraryGraphHelper helper;

    @BeforeClass
    public static void setUp() throws IOException {
        Files.write("include 'app', 'library', 'jar'", project.getSettingsFile(), Charsets.UTF_8);

        appendToFile(project.getSubproject("app").getBuildFile(),
                "\n" +
                "dependencies {\n" +
                "    compile project(\":library\")\n" +
                "}\n");

        appendToFile(project.getSubproject("library").getBuildFile(),
                "\n" +
                "dependencies {\n" +
                "    provided project(\":jar\")\n" +
                "}\n");

        project.execute("clean", ":library:assembleDebug");
        modelContainer = project.model().withFeature(FULL_DEPENDENCIES).getMulti();
        helper = new LibraryGraphHelper(modelContainer);
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        modelContainer = null;
        helper = null;
    }

    @Test
    public void checkProvidedJarIsNotPackaged() throws IOException, ProcessException {
        assertThatAar(project.getSubproject("library").getAar("debug"))
                .doesNotContainClass("Lcom/example/android/multiproject/person/People;");
    }

    @Test
    public void checkProvidedJarIsIntheLibCompileDeps() {
        Variant variant = ModelHelper.getVariant(
                modelContainer.getModelMap().get(":library").getVariants(), "debug");

        LibraryGraph graph = variant.getMainArtifact().getCompileGraph();

        LibraryGraphHelper.Items moduleItems = helper.on(graph).withType(MODULE);

        assertThat(moduleItems.mapTo(GRADLE_PATH)).containsExactly(":jar");
        assertThat(moduleItems.filter(PROVIDED).mapTo(GRADLE_PATH)).containsExactly(":jar");

        assertThat(graph.getProvidedLibraries())
                .containsExactly(moduleItems.asSingleGraphItem().getArtifactAddress());
    }

    @Test
    public void checkProvidedJarIsNotIntheLibPackageDeps() {
        Variant variant = ModelHelper.getVariant(
                modelContainer.getModelMap().get(":library").getVariants(), "debug");

        LibraryGraph graph = variant.getMainArtifact().getPackageGraph();
        assertThat(graph.getDependencies()).isEmpty();
    }

    @Test
    public void checkProvidedJarIsNotInTheAppDeps() {
        Variant variant = ModelHelper.getVariant
                (modelContainer.getModelMap().get(":app").getVariants(), "debug");

        LibraryGraph graph = variant.getMainArtifact().getCompileGraph();

        // query directly the full transitive list and it should only contain :library
        assertThat(helper.on(graph).withTransitive().mapTo(GRADLE_PATH)).containsExactly(":library");
    }
}

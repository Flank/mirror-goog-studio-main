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
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Filter.PROVIDED;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;
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
import com.android.builder.model.level2.LibraryGraph;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.Collection;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for provided local jar in libs
 */
public class LibWithProvidedLocalJarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithLocalDeps")
            .create();
    static ModelContainer<AndroidProject> model;

    @BeforeClass
    public static void setUp() throws IOException {
        appendToFile(project.getBuildFile(),
                "\n" +
                "apply plugin: \"com.android.library\"\n" +
                "\n" +
                "android {\n" +
                "    compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n" +
                "    buildToolsVersion \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "\"\n" +
                "}\n" +
                "\n" +
                "dependencies {\n" +
                "    provided files(\"libs/util-1.0.jar\")\n" +
                "}\n");

        project.execute("clean", "assembleDebug");
        model = project.model().withFeature(FULL_DEPENDENCIES).getSingle();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void checkProvidedLocalJarIsNotPackaged() throws IOException {
        assertThatZip(project.getAar("debug")).doesNotContain("libs/util-1.0.jar");
    }

    @Test
    public void checkProvidedLocalJarIsInTheMainArtifactDependency() {
        LibraryGraphHelper helper = new LibraryGraphHelper(model);

        Variant variant = ModelHelper.getVariant(model.getOnlyModel().getVariants(), "debug");

        LibraryGraph compileGraph = variant.getMainArtifact().getCompileGraph();

        LibraryGraphHelper.Items javaDependencies = helper.on(compileGraph).withType(JAVA);
        assertThat(javaDependencies.asList()).hasSize(1);
        assertThat(compileGraph.getProvidedLibraries())
                .containsExactly(javaDependencies.asSingleGraphItem().getArtifactAddress());

        LibraryGraph packageGraph = variant.getMainArtifact().getPackageGraph();
        assertThat(packageGraph.getDependencies()).isEmpty();
    }
}

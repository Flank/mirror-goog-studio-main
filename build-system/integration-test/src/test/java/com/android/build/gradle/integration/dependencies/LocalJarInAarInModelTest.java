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

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_BUILD_TOOL_VERSION;
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_COMPILE_SDK_VERSION;
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.SUPPORT_LIB_VERSION;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.ANDROID;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static org.junit.Assert.fail;

import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.Library;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.gradle.tooling.BuildException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * test for the path of the local jars in aars before and after exploding them.
 */
public class LocalJarInAarInModelTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.noBuildFile())
            .create();

    @Before
    public void setUp() throws Exception {
        appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: \"com.android.application\"\n"
                        + "\n"
                        + "android {\n"
                        + "  compileSdkVersion " + DEFAULT_COMPILE_SDK_VERSION + "\n"
                        + "  buildToolsVersion \"" + DEFAULT_BUILD_TOOL_VERSION + "\"\n"
                        + "\n"
                        + "  defaultConfig {\n"
                        + "    minSdkVersion 4\n"
                        + "  }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "  compile \"com.android.support:support-v4:" + SUPPORT_LIB_VERSION
                        + "\"\n"
                        + "}\n");
    }

    @After
    public void cleanUp() {
        project = null;
    }

    @Test
    public void checkAarsExplodedAfterSync() throws Exception {
        ModelContainer<AndroidProject> model = project.model().getSingle();
        LibraryGraphHelper helper = new LibraryGraphHelper(model);

        Variant variant = ModelHelper.getVariant(model.getOnlyModel().getVariants(), "debug");

        DependencyGraphs graph = variant.getMainArtifact().getDependencyGraphs();
        LibraryGraphHelper.Items androidItems = helper.on(graph).withType(ANDROID);

        // check the model validity: making sure the folders are extracted and the local
        // jars are present.
        List<Library> libraries = androidItems.asLibraries();
        assertThat(libraries).hasSize(6);
        for (Library androidLibrary : libraries) {
            File rootFolder = androidLibrary.getFolder();
            assertThat(rootFolder).isDirectory();
            assertThat(new File(rootFolder, androidLibrary.getJarFile())).isFile();
            for (String localJar : androidLibrary.getLocalJars()) {
                assertThat(new File(rootFolder, localJar)).isFile();
            }
        }
    }
}

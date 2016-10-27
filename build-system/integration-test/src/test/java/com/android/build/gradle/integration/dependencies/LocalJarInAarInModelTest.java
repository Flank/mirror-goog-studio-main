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
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.Variant;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
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
    public void setUp() throws IOException {
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
    public void checkModelBeforeBuild() {
        //clean the project and get the model. The aar won"t be exploded for this sync event.
        AndroidProject model = project.executeAndReturnModel("clean");

        Variant variant = ModelHelper.getVariant(model.getVariants(), "debug");

        Dependencies dependencies = variant.getMainArtifact().getCompileDependencies();
        Collection<AndroidLibrary> libraries = dependencies.getLibraries();

        assertThat(libraries).hasSize(1);

        // now build the project.
        project.execute("prepareDebugDependencies");

        // now check the model validity
        AndroidLibrary lib = libraries.iterator().next();
        assertThat(lib.getJarFile()).isFile();
        for (File localJar : lib.getLocalJars()) {
            assertThat(localJar).isFile();
        }
    }

    @Test
    public void checkModelAfterBuild() {
        //build the project and get the model. The aar is exploded for this sync event.
        AndroidProject model = project.executeAndReturnModel("clean", "prepareDebugDependencies");

        Variant variant = ModelHelper.getVariant(model.getVariants(), "debug");

        Dependencies dependencies = variant.getMainArtifact().getCompileDependencies();
        Collection<AndroidLibrary> libraries = dependencies.getLibraries();

        assertThat(libraries).hasSize(1);

        // now check the model validity
        AndroidLibrary lib = libraries.iterator().next();
        assertThat(lib.getJarFile()).isFile();
        for (File localJar : lib.getLocalJars()) {
            assertThat(localJar).isFile();
        }
    }
}

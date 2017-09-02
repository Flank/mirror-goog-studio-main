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

package com.android.build.gradle.integration.library;

import static com.android.build.gradle.integration.common.fixture.BuildModel.Feature.FULL_DEPENDENCIES;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static com.android.build.gradle.internal.coverage.JacocoPlugin.DEFAULT_JACOCO_VERSION;
import static com.android.testutils.truth.MoreTruth.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Dex;
import com.android.testutils.truth.MoreTruth;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.IOException;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Test for jacoco agent runtime dependencies. */
public class JacocoDependenciesTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("projectWithModules").create();

    @Before
    public void setUp() throws Exception {
        Files.write("include 'app', 'library'", project.getSettingsFile(), Charsets.UTF_8);

        appendToFile(
                project.getBuildFile(),
                "\nsubprojects {\n"
                        + "    apply from: \"$rootDir/../commonLocalRepo.gradle\"\n"
                        + "}\n");

        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\ndependencies {\n" + "    compile project(':library')\n" + "}\n");

        appendToFile(
                project.getSubproject("library").getBuildFile(),
                "\nandroid.buildTypes.debug.testCoverageEnabled = true");
    }

    @Test
    public void checkJacocoInApp() throws IOException, InterruptedException {
        project.executor().run("clean", "app:assembleDebug");

        Apk apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG);
        MoreTruth.assertThat(apk.getFile()).isFile();

        Optional<Dex> dexOptional = apk.getMainDexFile();
        MoreTruth.assertThat(dexOptional).isPresent();

        // noinspection ConstantConditions
        assertThat(dexOptional.get()).containsClasses("Lorg/jacoco/agent/rt/IAgent;");
    }

    @Test
    public void checkAgentRuntimeVersion() throws IOException {
        TestFileUtils.searchAndReplace(
                project.getSubproject("library").getBuildFile(),
                "apply plugin: 'com.android.library'",
                "\n"
                        + "project.buildscript { buildscript -> \n"
                        + "  apply from: '../../commonLocalRepo.gradle', to:buildscript\n"
                        + "  dependencies {\n"
                        + "    classpath 'org.jacoco:org.jacoco.core:0.7.5.201505241946'\n"
                        + "  }\n"
                        + "}\n"
                        + "apply plugin: 'com.android.library'\n"
                        + "dependencies {\n"
                        + "  implementation 'org.jacoco:org.jacoco.agent:0.7.5.201505241946:runtime'\n"
                        + "}\n");
        assertAgentMavenCoordinates(
                "org.jacoco:org.jacoco.agent:" + DEFAULT_JACOCO_VERSION + ":runtime@jar");
    }

    @Test
    public void checkAgentRuntimeVersionWhenOverridden() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "buildscript { \n"
                        + "  dependencies {\n"
                        + "    classpath 'org.jacoco:org.jacoco.core:0.7.5.201505241946'\n"
                        + "  }\n"
                        + "}\n");
        assertAgentMavenCoordinates("org.jacoco:org.jacoco.agent:0.7.5.201505241946:runtime@jar");
    }

    private void assertAgentMavenCoordinates(@NonNull String expected) throws IOException {
        ModelContainer<AndroidProject> container =
                project.model()
                        .level(AndroidProject.MODEL_LEVEL_LATEST)
                        .withFeature(FULL_DEPENDENCIES)
                        .getMulti();
        LibraryGraphHelper helper = new LibraryGraphHelper(container);
        Variant appDebug =
                ModelHelper.getVariant(
                        container.getModelMap().get(":library").getVariants(), "debug");

        DependencyGraphs dependencyGraphs = appDebug.getMainArtifact().getDependencyGraphs();
        assertThat(
                        helper.on(dependencyGraphs)
                                .forPackage()
                                .withType(JAVA)
                                .mapTo(LibraryGraphHelper.Property.COORDINATES))
                .named("jacoco agent runtime jar")
                .containsExactly(expected);
    }
}

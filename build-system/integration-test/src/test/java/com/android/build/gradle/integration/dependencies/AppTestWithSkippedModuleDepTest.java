/*
 * Copyright (C) 2016 The Android Open Source Project
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
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.ANDROID;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.MODULE;

import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.LibraryGraph;
import com.android.ide.common.process.ProcessException;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for app with a sub-module as both a compile and androidTestCompile dependency.
 */
public class AppTestWithSkippedModuleDepTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();
    private static ModelContainer<AndroidProject> modelContainer;
    private static LibraryGraphHelper helper;

    @BeforeClass
    public static void setUp() throws IOException {
        Files.write("include 'app', 'jar'", project.getSettingsFile(), Charsets.UTF_8);

        TestFileUtils.appendToFile(project.getSubproject("app").getBuildFile(),
                "\ndependencies {\n"
                + "    compile project(':jar')\n"
                + "    androidTestCompile project(':jar')\n"
                + "}\n");

        project.execute("clean", ":app:assembleDebug", ":app:assembleAndroidTest");
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
    public void checkAppBuild() throws IOException, ProcessException {
        File appApk = project.getSubproject(":app").getApk("debug");
        assertThat(appApk).named("app debug apk").isFile();
        assertThatApk(appApk)
                .named("app debug apk")
                .containsClass("Lcom/example/android/multiproject/person/Person;");
    }

    @Test
    public void checkTestBuild() throws IOException, ProcessException {

        File testApk = project.getSubproject(":app").getApk("debug", "androidTest");
        assertThat(testApk).named("app test apk").isFile();
        assertThatApk(testApk)
                .named("app test apk")
                .doesNotContainClass("Lcom/example/android/multiproject/person/Person;");
    }

    @Test
    public void checkLevel2AppModel() {
        LibraryGraphHelper helper = new LibraryGraphHelper(modelContainer);

        Map<String, AndroidProject> models = modelContainer.getModelMap();

        Variant appDebug = ModelHelper.getVariant(models.get(":app").getVariants(), "debug");

        // --- Compile Graph

        LibraryGraph compileGraph = appDebug.getMainArtifact().getCompileGraph();

        // check direct dependencies
        assertThat(helper.on(compileGraph).withType(MODULE).mapTo(Property.GRADLE_PATH))
                .named("app direct compile module dependencies")
                .containsExactly(":jar");

        assertThat(helper.on(compileGraph).withType(JAVA).asList())
                .named("app direct compile java deps")
                .isEmpty();

        assertThat(helper.on(compileGraph).withType(ANDROID).asList())
                .named("app direct compile android deps")
                .isEmpty();

        // --- package Graph

        LibraryGraph packageGraph = appDebug.getMainArtifact().getPackageGraph();

        // check direct dependencies
        assertThat(helper.on(packageGraph).withType(MODULE).mapTo(Property.GRADLE_PATH))
                .named("app direct package module dependencies")
                .containsExactly(":jar");

        assertThat(helper.on(packageGraph).withType(JAVA).asList())
                .named("app direct package java deps")
                .isEmpty();

        assertThat(helper.on(packageGraph).withType(ANDROID).asList())
                .named("app direct package android deps")
                .isEmpty();

        // --- skipped/provided states
        assertThat(packageGraph.getSkippedLibraries())
                .named("package skipped libraries")
                .isEmpty();
        assertThat(compileGraph.getSkippedLibraries())
                .named("compile skipped libraries")
                .isEmpty();
    }

    @Test
    public void checkLevel2TestModel() {

        Map<String, AndroidProject> models = modelContainer.getModelMap();

        Variant appDebug = ModelHelper.getVariant(
                models.get(":app").getVariants(), "debug");

        AndroidArtifact testArtifact = ModelHelper.getAndroidArtifact(
                appDebug.getExtraAndroidArtifacts(), AndroidProject.ARTIFACT_ANDROID_TEST);

        // --- compile Graph

        LibraryGraph compileGraph = testArtifact.getCompileGraph();

        // check direct dependencies
        assertThat(helper.on(compileGraph).withType(MODULE).mapTo(Property.GRADLE_PATH))
                .named("app direct compile module dependencies")
                .containsExactly(":jar");

        assertThat(helper.on(compileGraph).withType(JAVA).asList())
                .named("app direct compile java deps")
                .isEmpty();

        assertThat(helper.on(compileGraph).withType(ANDROID).asList())
                .named("app direct compile android deps")
                .isEmpty();

        // --- package Graph

        LibraryGraph packageGraph = testArtifact.getPackageGraph();

        // check direct dependencies
        assertThat(helper.on(packageGraph).withType(MODULE).mapTo(Property.GRADLE_PATH))
                .named("app direct package module dependencies")
                .containsExactly(":jar");

        assertThat(helper.on(packageGraph).withType(JAVA).asList())
                .named("app direct package java deps")
                .isEmpty();

        assertThat(helper.on(packageGraph).withType(ANDROID).asList())
                .named("app direct package android deps")
                .isEmpty();

        // --- provided/skipppd state
        assertThat(packageGraph.getSkippedLibraries())
                .named("package skipped libraries")
                .containsExactly(":jar");
        assertThat(compileGraph.getSkippedLibraries())
                .named("compile skipped libraries")
                .isEmpty();
    }
}

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

package com.android.build.gradle.integration.testing

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Items
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.Variant
import com.android.builder.model.level2.DependencyGraphs
import com.android.testutils.apk.Apk
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.build.gradle.integration.common.fixture.BuildModel.Feature.FULL_DEPENDENCIES
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Filter.SKIPPED
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.COORDINATES
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.GRADLE_PATH
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.ANDROID
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.MODULE

/**
 * Test separate test module testing an app with aar dependencies.
 */
@CompileStatic
public class SeparateTestWithAarDependencyTest {

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("separateTestModule")
            .create()

    static ModelContainer<AndroidProject> models
    static LibraryGraphHelper helper

    @BeforeClass
    static void setup() {
        project.getSubproject("app").getBuildFile() << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion = '$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION'

    defaultConfig {
        minSdkVersion $GradleTestProject.SUPPORT_LIB_MIN_SDK
    }
}
dependencies {
    compile 'com.android.support:appcompat-v7:$GradleTestProject.SUPPORT_LIB_VERSION'
}
        """

        project.execute("clean", "assemble")
        models = project.model().withFeature(FULL_DEPENDENCIES).getMulti()
        helper = new LibraryGraphHelper(models)
    }

    @AfterClass
    static void cleanUp() {
        project = null
        models = null
        helper = null
    }

    @Test
    void "check app doesn't contain test app's code"() {
        Apk apk = project.getSubproject('test').getApk("debug")
        assertThatApk(apk).doesNotContainClass("Lcom/android/tests/basic/Main;")
    }

    @Test
    void "check app doesn't contain test app's layout"() {
        Apk apk = project.getSubproject('test').getApk("debug")
        assertThatApk(apk).doesNotContainResource("layout/main.xml")
    }

    @Test
    void "check app doesn't contain test app's dependency lib's code"() {
        Apk apk = project.getSubproject('test').getApk("debug")
        assertThatApk(apk).doesNotContainClass("Landroid/support/v7/app/ActionBar;")
    }

    @Test
    void "check app doesn't contain test app's dependency lib's resources"() {
        Apk apk = project.getSubproject('test').getApk("debug")
        assertThatApk(apk).doesNotContainResource("layout/abc_action_bar_title_item.xml")
    }

    @Test
    void "check test model's compile deps includes the tested app"() {
        Collection<Variant> variants = models.getModelMap().get(":test").getVariants()

        // get the main artifact of the debug artifact and its dependencies
        Variant variant = ModelHelper.getVariant(variants, "debug")
        AndroidArtifact artifact = variant.getMainArtifact();

        DependencyGraphs compileGraph = artifact.getDependencyGraphs();

        // check the app and its children dependencies show up flat in the main
        // dependency list.
        assertThat(helper.on(compileGraph).mapTo(COORDINATES))
                .containsAllOf(
                ":app::debug",
                "com.android.support:support-core-ui:" + GradleTestProject.SUPPORT_LIB_VERSION + "@aar",
                "com.android.support:support-core-utils:" + GradleTestProject.SUPPORT_LIB_VERSION + "@aar",
                "com.android.support:appcompat-v7:" + GradleTestProject.SUPPORT_LIB_VERSION + "@aar",
                "com.android.support:support-fragment:" + GradleTestProject.SUPPORT_LIB_VERSION + "@aar",
                "com.android.support:support-compat:" + GradleTestProject.SUPPORT_LIB_VERSION + "@aar",
                "com.android.support:support-v4:" + GradleTestProject.SUPPORT_LIB_VERSION + "@aar",
                "com.android.support:support-annotations:" + GradleTestProject.SUPPORT_LIB_VERSION + "@jar",
                "com.android.support:animated-vector-drawable:" + GradleTestProject.SUPPORT_LIB_VERSION + "@aar",
                "com.android.support:support-media-compat:" + GradleTestProject.SUPPORT_LIB_VERSION + "@aar",
                "com.android.support:support-vector-drawable:" + GradleTestProject.SUPPORT_LIB_VERSION + "@aar");
    }

    @Test
    void "check test model's package deps doesnt include the tested app"() {
        Collection<Variant> variants = models.getModelMap().get(":test").getVariants()

        // get the main artifact of the debug artifact and its dependencies
        Variant variant = ModelHelper.getVariant(variants, "debug")
        AndroidArtifact artifact = variant.getMainArtifact();

        // verify the same dependencies in package are skipped.
        DependencyGraphs dependencyGraph = artifact.getDependencyGraphs();

        Items packageItems = helper.on(dependencyGraph).forPackage();

        // check the app project shows up as a project dependency
        Items moduleItems = packageItems.withType(MODULE)

        // make sure the package does not contain the app or its dependencies
        assertThat(packageItems.mapTo(COORDINATES)).containsNoneOf(
                ":app::debug",
                "com.android.support:support-core-ui:" + GradleTestProject.SUPPORT_LIB_VERSION + "@aar",
                "com.android.support:support-core-utils:" + GradleTestProject.SUPPORT_LIB_VERSION + "@aar",
                "com.android.support:appcompat-v7:" + GradleTestProject.SUPPORT_LIB_VERSION + "@aar",
                "com.android.support:support-fragment:" + GradleTestProject.SUPPORT_LIB_VERSION + "@aar",
                "com.android.support:support-compat:" + GradleTestProject.SUPPORT_LIB_VERSION + "@aar",
                "com.android.support:support-v4:" + GradleTestProject.SUPPORT_LIB_VERSION + "@aar",
                "com.android.support:support-annotations:" + GradleTestProject.SUPPORT_LIB_VERSION + "@jar",
                "com.android.support:animated-vector-drawable:" + GradleTestProject.SUPPORT_LIB_VERSION + "@aar",
                "com.android.support:support-media-compat:" + GradleTestProject.SUPPORT_LIB_VERSION + "@aar",
                "com.android.support:support-vector-drawable:" + GradleTestProject.SUPPORT_LIB_VERSION + "@aar");

    }

    @Test
    @Category(DeviceTests)
    void "connected check"() {
        project.execute("clean",":test:deviceCheck");
    }
}

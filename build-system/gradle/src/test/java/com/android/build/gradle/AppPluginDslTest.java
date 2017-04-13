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

package com.android.build.gradle;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.PostprocessingOptions;
import com.android.build.gradle.internal.fixture.TestConstants;
import com.android.build.gradle.internal.fixture.TestProjects;
import com.android.build.gradle.internal.fixture.VariantChecker;
import com.android.build.gradle.internal.fixture.VariantCheckers;
import com.android.build.gradle.tasks.MergeResources;
import groovy.util.Eval;
import java.util.Arrays;
import org.gradle.api.Project;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for the public DSL of the App plugin ("android") */
public class AppPluginDslTest {

    @Rule public TemporaryFolder projectDirectory = new TemporaryFolder();

    protected AppPlugin plugin;
    protected AppExtension android;
    protected Project project;
    protected VariantChecker checker;

    @Before
    public void setUp() throws Exception {
        TestProjects.Plugin myPlugin = TestProjects.Plugin.APP;
        project =
                TestProjects.builder(projectDirectory.newFolder("project").toPath())
                        .withPlugin(myPlugin)
                        .build();

        android = (AppExtension) project.getExtensions().getByType(myPlugin.getExtensionClass());
        android.setCompileSdkVersion(TestConstants.COMPILE_SDK_VERSION);
        android.setBuildToolsVersion(TestConstants.BUILD_TOOL_VERSION);
        plugin = (AppPlugin) project.getPlugins().getPlugin(myPlugin.getPluginClass());
        checker = VariantCheckers.createAppChecker(android);
    }

    @Test
    public void testGeneratedDensities() throws Exception {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    flavorDimensions 'foo'\n"
                        + "    productFlavors {\n"
                        + "        f1 {\n"
                        + "        }\n"
                        + "\n"
                        + "        f2  {\n"
                        + "            vectorDrawables {\n"
                        + "                generatedDensities 'ldpi'\n"
                        + "                generatedDensities += ['mdpi']\n"
                        + "            }\n"
                        + "        }\n"
                        + "\n"
                        + "        f3 {\n"
                        + "            vectorDrawables {\n"
                        + "                generatedDensities = defaultConfig.generatedDensities - ['ldpi', 'mdpi']\n"
                        + "            }\n"
                        + "        }\n"
                        + "\n"
                        + "        f4.vectorDrawables.generatedDensities = []\n"
                        + "\n"
                        + "        oldSyntax {\n"
                        + "            generatedDensities = ['ldpi']\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        plugin.createAndroidTasks(false);

        checkGeneratedDensities(
                "mergeF1DebugResources", "ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi");
        checkGeneratedDensities("mergeF2DebugResources", "ldpi", "mdpi");
        checkGeneratedDensities("mergeF3DebugResources", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi");
        checkGeneratedDensities("mergeF4DebugResources");
        checkGeneratedDensities("mergeOldSyntaxDebugResources", "ldpi");
    }

    @Test
    public void testUseSupportLibrary_default() throws Exception {
        plugin.createAndroidTasks(false);

        assertThat(getTask("mergeDebugResources", MergeResources.class).isDisableVectorDrawables())
                .isFalse();
    }

    @Test
    public void testUseSupportLibrary_flavors() throws Exception {

        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "\n"
                        + "    flavorDimensions 'foo'\n"
                        + "    productFlavors {\n"
                        + "        f1 {\n"
                        + "        }\n"
                        + "\n"
                        + "        f2  {\n"
                        + "            vectorDrawables {\n"
                        + "                useSupportLibrary true\n"
                        + "            }\n"
                        + "        }\n"
                        + "\n"
                        + "        f3 {\n"
                        + "            vectorDrawables {\n"
                        + "                useSupportLibrary = false\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        plugin.createAndroidTasks(false);

        assertThat(
                        getTask("mergeF1DebugResources", MergeResources.class)
                                .isDisableVectorDrawables())
                .isFalse();
        assertThat(
                        getTask("mergeF2DebugResources", MergeResources.class)
                                .isDisableVectorDrawables())
                .isTrue();
        assertThat(
                        getTask("mergeF3DebugResources", MergeResources.class)
                                .isDisableVectorDrawables())
                .isFalse();
    }

    @Test
    public void testPostprocessingBlock_noActions() throws Exception {
        BuildType release = android.getBuildTypes().getByName("release");
        release.getPostprocessing().setCodeShrinker("proguard");

        plugin.createAndroidTasks(false);

        assertThat(project.getTasks().getNames())
                .doesNotContain("transformClassesAndResourcesWithProguardForRelease");
    }

    @Test
    public void testPostprocessingBlock_proguard() throws Exception {
        BuildType release = android.getBuildTypes().getByName("release");
        release.getPostprocessing().setRemoveUnusedCode(true);

        plugin.createAndroidTasks(false);

        assertThat(project.getTasks().getNames())
                .contains("transformClassesAndResourcesWithProguardForRelease");
    }

    @Test
    public void testPostprocessingBlock_justObfuscate() throws Exception {
        BuildType release = android.getBuildTypes().getByName("release");
        release.getPostprocessing().setObfuscate(true);

        plugin.createAndroidTasks(false);

        assertThat(project.getTasks().getNames())
                .contains("transformClassesAndResourcesWithProguardForRelease");
    }

    @Test
    public void testPostprocessingBlock_builtInShrinker() throws Exception {
        PostprocessingOptions postprocessing =
                android.getBuildTypes().getByName("release").getPostprocessing();
        postprocessing.setRemoveUnusedCode(true);
        postprocessing.setCodeShrinker("android_gradle");

        assertThat(postprocessing.getCodeShrinker()).isEqualTo("android_gradle");

        plugin.createAndroidTasks(false);

        assertThat(project.getTasks().getNames())
                .contains("transformClassesWithAndroidGradleClassShrinkerForRelease");
    }

    @Test
    public void testPostprocessingBlock_mixingDsls_newOld() throws Exception {
        BuildType release = android.getBuildTypes().getByName("release");
        release.getPostprocessing().setCodeShrinker("android_gradle");

        try {
            release.setMinifyEnabled(true);
            Assert.fail();
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("setMinifyEnabled");
        }
    }

    @Test
    public void testPostprocessingBlock_mixingDsls_oldNew() throws Exception {
        BuildType release = android.getBuildTypes().getByName("release");
        release.setMinifyEnabled(true);

        try {
            release.getPostprocessing().setCodeShrinker("android_gradle");
            Assert.fail();
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("setMinifyEnabled");
        }
    }

    @Test
    public void testPostprocessingBlock_validating() throws Exception {
        PostprocessingOptions postprocessing =
                android.getBuildTypes().getByName("release").getPostprocessing();
        postprocessing.setCodeShrinker("android_gradle");
        postprocessing.setRemoveUnusedCode(true);
        postprocessing.setObfuscate(true);

        try {
            plugin.createAndroidTasks(false);
            Assert.fail();
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("does not support obfuscating");
        }
    }

    @Test
    public void testPostprocessingBlock_resourceShrinker() throws Exception {
        PostprocessingOptions postprocessing =
                android.getBuildTypes().getByName("release").getPostprocessing();
        postprocessing.setCodeShrinker("android_gradle");
        postprocessing.setRemoveUnusedCode(true);
        postprocessing.setRemoveUnusedResources(true);

        plugin.createAndroidTasks(false);

        assertThat(project.getTasks().getNames())
                .containsAllOf(
                        "transformClassesWithAndroidGradleClassShrinkerForRelease",
                        "transformClassesWithShrinkResForRelease");
    }

    @Test
    public void testPostprocessingBlock_noCodeShrinking() throws Exception {
        PostprocessingOptions postprocessing =
                android.getBuildTypes().getByName("release").getPostprocessing();
        postprocessing.setCodeShrinker("android_gradle");
        postprocessing.setRemoveUnusedCode(false);
        postprocessing.setRemoveUnusedResources(true);

        try {
            plugin.createAndroidTasks(false);
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("requires unused code shrinking");
        }
    }

    @Test
    public void testPostprocessingBlock_noCodeShrinking_oldDsl() throws Exception {
        BuildType release = android.getBuildTypes().getByName("release");
        release.setShrinkResources(true);

        try {
            plugin.createAndroidTasks(false);
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("requires unused code shrinking");
        }
    }

    @Test
    public void testPostprocessingBlock_initWith() throws Exception {
        BuildType debug = android.getBuildTypes().getByName("debug");
        BuildType release = android.getBuildTypes().getByName("release");

        debug.setMinifyEnabled(true);
        release.getPostprocessing().setRemoveUnusedCode(true);

        BuildType debugCopy = android.getBuildTypes().create("debugCopy");
        debugCopy.initWith(debug);

        BuildType releaseCopy = android.getBuildTypes().create("releaseCopy");
        releaseCopy.initWith(release);
    }

    private void checkGeneratedDensities(String taskName, String... densities) {
        MergeResources mergeResources = getTask(taskName, MergeResources.class);
        assertThat(mergeResources.getGeneratedDensities())
                .containsExactlyElementsIn(Arrays.asList(densities));
    }

    protected <T> T getTask(String name, @SuppressWarnings("unused") Class<T> klass) {
        //noinspection unchecked
        return (T) project.getTasks().getByName(name);
    }
}

/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.plugins;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.PostProcessingBlock;
import com.android.build.gradle.internal.fixture.TestConstants;
import com.android.build.gradle.internal.fixture.TestProjects;
import com.android.build.gradle.internal.fixture.VariantChecker;
import com.android.build.gradle.internal.fixture.VariantCheckers;
import com.android.build.gradle.tasks.MergeResources;
import groovy.util.Eval;
import java.util.Arrays;
import org.gradle.api.Project;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for the public DSL of the App plugin ("com.android.application") */
public class AppPluginDslTest {
    public static final String PROGUARD_DEBUG = "minifyDebugWithProguard";
    public static final String R8_DEBUG = "minifyDebugWithR8";
    public static final String R8_RELEASE = "minifyReleaseWithR8";
    public static final String R8_DEBUG_ANDROID_TEST = "minifyDebugAndroidTestWithR8";

    private static final String DEFAULT_DEBUG;
    private static final String DEFAULT_DEBUG_ANDROID_TEST;
    private static final String DEFAULT_RELEASE;

    static {
        DEFAULT_DEBUG = R8_DEBUG;
        DEFAULT_DEBUG_ANDROID_TEST = R8_DEBUG_ANDROID_TEST;
        DEFAULT_RELEASE = R8_RELEASE;
    }

    @Rule public final TemporaryFolder projectDirectory = new TemporaryFolder();

    protected AppPlugin plugin;
    protected AppExtension android;
    protected Project project;
    protected VariantChecker checker;
    private TestProjects.Plugin pluginType = TestProjects.Plugin.APP;

    @Before
    public void setUp() throws Exception {
        project =
                TestProjects.builder(projectDirectory.newFolder("project").toPath())
                        .withPlugin(pluginType)
                        .build();

        initFieldsFromProject();
    }

    private void initFieldsFromProject() {
        android = (AppExtension) project.getExtensions().getByType(pluginType.getExtensionClass());
        android.setCompileSdkVersion(TestConstants.COMPILE_SDK_VERSION);
        android.setBuildToolsVersion(TestConstants.BUILD_TOOL_VERSION);
        plugin = (AppPlugin) project.getPlugins().getPlugin(pluginType.getPluginClass());
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
        plugin.createAndroidTasks();

        checkGeneratedDensities(
                "mergeF1DebugResources", "ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi");
        checkGeneratedDensities("mergeF2DebugResources", "ldpi", "mdpi");
        checkGeneratedDensities("mergeF3DebugResources", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi");
        checkGeneratedDensities("mergeF4DebugResources");
        checkGeneratedDensities("mergeOldSyntaxDebugResources", "ldpi");
    }

    @Test
    public void testUseSupportLibrary_default() throws Exception {
        plugin.createAndroidTasks();

        assertThat(getTask("mergeDebugResources", MergeResources.class)
                        .isVectorSupportLibraryUsed())
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
        plugin.createAndroidTasks();

        assertThat(
                        getTask("mergeF1DebugResources", MergeResources.class)
                                .isVectorSupportLibraryUsed())
                .isFalse();
        assertThat(
                        getTask("mergeF2DebugResources", MergeResources.class)
                                .isVectorSupportLibraryUsed())
                .isTrue();
        assertThat(
                        getTask("mergeF3DebugResources", MergeResources.class)
                                .isVectorSupportLibraryUsed())
                .isFalse();
    }

    @Test
    public void testPostprocessingBlock_default() throws Exception {
        BuildType release = android.getBuildTypes().getByName("release");
        release.getPostprocessing().setRemoveUnusedCode(true);

        plugin.createAndroidTasks();

        assertThat(project.getTasks().getNames()).contains(DEFAULT_RELEASE);
    }

    @Test
    public void testPostprocessingBlock_justObfuscate() throws Exception {
        BuildType release = android.getBuildTypes().getByName("release");
        release.getPostprocessing().setObfuscate(true);

        plugin.createAndroidTasks();

        assertThat(project.getTasks().getNames()).contains(DEFAULT_RELEASE);
    }

    @Test
    public void testPostprocessingBlock_r8_noFeatures() throws Exception {
        BuildType release = android.getBuildTypes().getByName("release");
        release.getPostprocessing().setCodeShrinker("r8");
        release.getPostprocessing().setRemoveUnusedCode(false);

        plugin.createAndroidTasks();

        assertThat(project.getTasks().getNames()).doesNotContain(R8_RELEASE);
    }

    @Test
    public void testPostprocessingBlock_r8() throws Exception {
        BuildType release = android.getBuildTypes().getByName("release");
        release.getPostprocessing().setCodeShrinker("r8");
        release.getPostprocessing().setRemoveUnusedCode(true);

        plugin.createAndroidTasks();

        assertThat(project.getTasks().getNames()).contains(R8_RELEASE);
    }

    @Test
    public void testPostprocessingBlock_mixingDsls_newOld() throws Exception {
        BuildType release = android.getBuildTypes().getByName("release");
        release.getPostprocessing().setCodeShrinker("r8");

        try {
            release.setMinifyEnabled(true);
            fail();
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
            fail();
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("setMinifyEnabled");
        }
    }

    @Test
    public void testPostprocessingBlock_noCodeShrinking_oldDsl() throws Exception {
        BuildType release = android.getBuildTypes().getByName("release");
        release.setShrinkResources(true);

        try {
            plugin.createAndroidTasks();
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

    @Test
    public void testShrinkerChoice_oldDsl_r8Flag() throws Exception {
        project =
                TestProjects.builder(projectDirectory.newFolder("oldDsl").toPath())
                        .withPlugin(pluginType)
                        .build();
        initFieldsFromProject();

        BuildType debug = android.getBuildTypes().getByName("debug");
        debug.setMinifyEnabled(true);

        plugin.createAndroidTasks();

        assertThat(project.getTasks().getNames()).doesNotContain(PROGUARD_DEBUG);
        assertThat(project.getTasks().getNames()).contains(R8_DEBUG);
    }

    @Test
    public void testShrinkerChoice_oldDsl_r8FlagWithoutMinification() throws Exception {
        project =
                TestProjects.builder(projectDirectory.newFolder("oldDsl").toPath())
                        .withPlugin(pluginType)
                        .build();
        initFieldsFromProject();

        BuildType debug = android.getBuildTypes().getByName("debug");
        debug.setMinifyEnabled(false);

        plugin.createAndroidTasks();

        assertThat(project.getTasks().getNames()).doesNotContain(PROGUARD_DEBUG);
        assertThat(project.getTasks().getNames()).doesNotContain(R8_DEBUG);
    }

    @Test
    public void testShrinkerChoice_newDsl_noInstantRun() throws Exception {
        android.getBuildTypes().getByName("debug").getPostprocessing().setRemoveUnusedCode(true);

        plugin.createAndroidTasks();

        assertThat(project.getTasks().getNames()).contains(DEFAULT_DEBUG);
    }

    @Test
    public void testApkShrinker_oldDsl() throws Exception {
        project =
                TestProjects.builder(projectDirectory.newFolder("oldDsl_builtInShrinker").toPath())
                        .withPlugin(pluginType)
                        .build();
        initFieldsFromProject();
        BuildType debug = android.getBuildTypes().getByName("debug");
        debug.setMinifyEnabled(true);

        plugin.createAndroidTasks();

        assertThat(project.getTasks().getNames()).contains(R8_DEBUG);
        assertThat(project.getTasks().getNames()).contains(R8_DEBUG_ANDROID_TEST);
    }

    @Test
    public void testApkShrinker_newDsl_noObfuscation() {
        PostProcessingBlock postprocessing =
                android.getBuildTypes().getByName("debug").getPostprocessing();
        postprocessing.setRemoveUnusedCode(true);

        plugin.createAndroidTasks();

        assertThat(project.getTasks().getNames()).contains(DEFAULT_DEBUG);
        assertThat(project.getTasks().getNames()).doesNotContain(DEFAULT_DEBUG_ANDROID_TEST);
    }

    @Test
    public void testApkShrinker_newDsl_obfuscation() throws Exception {
        PostProcessingBlock postprocessing =
                android.getBuildTypes().getByName("debug").getPostprocessing();
        postprocessing.setRemoveUnusedCode(true);
        postprocessing.setObfuscate(true);

        plugin.createAndroidTasks();

        assertThat(project.getTasks().getNames()).contains(DEFAULT_DEBUG);
        assertThat(project.getTasks().getNames()).contains(DEFAULT_DEBUG_ANDROID_TEST);
    }

    @Test
    public void testMinSdkVersionParsing() {
        android.getDefaultConfig().setMinSdkVersion("P");
        assertThat(android.getDefaultConfig().getMinSdkVersion().getApiLevel())
                .named("android.defaultConfig.minSdkVersion.apiLevel")
                .isEqualTo(27);
        assertThat(android.getDefaultConfig().getMinSdkVersion().getApiString())
                .named("android.defaultConfig.minSdkVersion.apiLevel")
                .isEqualTo("P");
    }

    @Test
    public void testEmulatorSnapshots() throws Exception {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    testOptions {\n"
                        + "        emulatorSnapshots {\n"
                        + "          enableForTestFailures true\n"
                        + "          retainAll()\n"
                        + "          maxSnapshotsForTestFailures 2\n"
                        + "          compressSnapshots true\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        plugin.createAndroidTasks();
    }

    @Test
    public void testResourceConfigurations() {
        Eval.me("project",
                project,
                "project.android {\n"
                        + "    flavorDimensions += ['fruit']\n"
                        + "    defaultConfig {\n"
                        + "        resourceConfigurations += ['en']\n"
                        + "    }\n"
                        + "    productFlavors {\n"
                        + "        orange {\n"
                        + "            resourceConfigurations += ['de']\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        plugin.createAndroidTasks();

        assertThat(android.getDefaultConfig().getResourceConfigurations()).containsExactly("en");

        assertThat(android.getProductFlavors().getByName("orange").getResourceConfigurations()).containsExactly("de");
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

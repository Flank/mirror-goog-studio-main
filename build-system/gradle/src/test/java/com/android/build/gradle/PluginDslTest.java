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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.android.SdkConstants;
import com.android.build.gradle.api.TestVariant;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.fixture.BaseTestedVariant;
import com.android.build.gradle.internal.fixture.TestConstants;
import com.android.build.gradle.internal.fixture.TestProjects;
import com.android.build.gradle.internal.fixture.VariantChecker;
import com.android.build.gradle.internal.fixture.VariantCheckers;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.MockableAndroidJarTask;
import com.android.build.gradle.tasks.factory.AndroidJavaCompile;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import groovy.util.Eval;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Tests for checking the "application" and "atom" DSLs. */
@RunWith(Parameterized.class)
public class PluginDslTest {

    @Rule public TemporaryFolder projectDirectory = new TemporaryFolder();
    private BasePlugin plugin;
    private BaseExtension android;
    private Project project;
    private VariantChecker checker;
    private final TestProjects.Plugin pluginType;

    @Parameterized.Parameters
    public static Collection<TestProjects.Plugin[]> generateStates() {
        return ImmutableList.of(new TestProjects.Plugin[] {TestProjects.Plugin.APP});
    }

    public PluginDslTest(TestProjects.Plugin pluginType) {
        this.pluginType = pluginType;
    }


    @Before
    public void setUp() throws Exception {
        project =
                TestProjects.builder(projectDirectory.newFolder("project").toPath())
                        .withPlugin(pluginType)
                        .build();

        android = project.getExtensions().getByType(pluginType.getExtensionClass());
        android.setCompileSdkVersion(TestConstants.COMPILE_SDK_VERSION);
        android.setBuildToolsVersion(TestConstants.BUILD_TOOL_VERSION);

        if (pluginType == TestProjects.Plugin.APP) {
            plugin = (AppPlugin) project.getPlugins().getPlugin(pluginType.getPluginClass());
            checker = VariantCheckers.createAppChecker((AppExtension) android);
        } else {
            throw new AssertionError("Unsupported plugin type");
        }
    }

    @Test
    public void testBasic() {
        plugin.createAndroidTasks(false);
        VariantCheckers.checkDefaultVariants(plugin.getVariantManager().getVariantScopes());

        // we can now call this since the variants/tasks have been created
        Set<BaseTestedVariant> variants = checker.getVariants();
        assertEquals(2, variants.size());

        Set<TestVariant> testVariants = checker.getTestVariants();
        assertEquals(1, testVariants.size());

        checker.checkTestedVariant("debug", "debugAndroidTest", variants, testVariants);
        checker.checkNonTestedVariant("release", variants);
    }

    /** Same as Basic but with a slightly different DSL. */
    @Test
    public void testBasic2() {
        plugin.createAndroidTasks(false);
        VariantCheckers.checkDefaultVariants(plugin.getVariantManager().getVariantScopes());

        // we can now call this since the variants/tasks have been created
        Set<BaseTestedVariant> variants = checker.getVariants();
        assertEquals(2, variants.size());

        Set<TestVariant> testVariants = checker.getTestVariants();
        assertEquals(1, testVariants.size());

        checker.checkTestedVariant("debug", "debugAndroidTest", variants, testVariants);
        checker.checkNonTestedVariant("release", variants);
    }

    @Test
    public void testBasicWithStringTarget() {
        Eval.me(
                "project",
                project,
                "\n        project.android {\n            compileSdkVersion 'android-"
                        + String.valueOf(TestConstants.COMPILE_SDK_VERSION)
                        + "'\n        }\n");

        plugin.createAndroidTasks(false);
        VariantCheckers.checkDefaultVariants(plugin.getVariantManager().getVariantScopes());

        // we can now call this since the variants/tasks have been created
        Set<BaseTestedVariant> variants = checker.getVariants();
        assertEquals(2, variants.size());

        Set<TestVariant> testVariants = checker.getTestVariants();
        assertEquals(1, testVariants.size());

        checker.checkTestedVariant("debug", "debugAndroidTest", variants, testVariants);
        checker.checkNonTestedVariant("release", variants);
    }

    @Test
    public void testMultiRes() {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "\n"
                        + "    sourceSets {\n"
                        + "        main {\n"
                        + "            res {\n"
                        + "                srcDirs 'src/main/res1', 'src/main/res2'\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        // nothing to be done here. If the DSL fails, it'll throw an exception
    }

    @Test
    public void testBuildTypes() {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    testBuildType 'staging'\n"
                        + "\n"
                        + "    buildTypes {\n"
                        + "        staging {\n"
                        + "            signingConfig signingConfigs.debug\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        plugin.createAndroidTasks(false);
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>(3);
        map.put("appVariants", 3);
        map.put("unitTest", 3);
        map.put("androidTests", 1);
        assertEquals(
                VariantCheckers.countVariants(map),
                plugin.getVariantManager().getVariantScopes().size());

        // we can now call this since the variants/tasks have been created

        // does not include tests
        Set<BaseTestedVariant> variants = checker.getVariants();
        assertEquals(3, variants.size());

        Set<TestVariant> testVariants = checker.getTestVariants();
        assertEquals(1, testVariants.size());

        checker.checkTestedVariant("staging", "stagingAndroidTest", variants, testVariants);

        checker.checkNonTestedVariant("debug", variants);
        checker.checkNonTestedVariant("release", variants);
    }

    @Test
    public void testFlavors() {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    flavorDimensions 'foo'\n"
                        + "    productFlavors {\n"
                        + "        flavor1 {\n"
                        + "\n"
                        + "        }\n"
                        + "        flavor2 {\n"
                        + "\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        plugin.createAndroidTasks(false);
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>(3);
        map.put("appVariants", 4);
        map.put("unitTest", 4);
        map.put("androidTests", 2);
        assertEquals(
                VariantCheckers.countVariants(map),
                plugin.getVariantManager().getVariantScopes().size());

        // we can now call this since the variants/tasks have been created

        // does not include tests
        Set<BaseTestedVariant> variants = checker.getVariants();
        assertEquals(4, variants.size());

        Set<TestVariant> testVariants = checker.getTestVariants();
        assertEquals(2, testVariants.size());

        checker.checkTestedVariant(
                "flavor1Debug", "flavor1DebugAndroidTest", variants, testVariants);
        checker.checkTestedVariant(
                "flavor2Debug", "flavor2DebugAndroidTest", variants, testVariants);

        checker.checkNonTestedVariant("flavor1Release", variants);
        checker.checkNonTestedVariant("flavor2Release", variants);
    }

    @Test
    public void testMultiFlavors() {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    flavorDimensions   'dimension1', 'dimension2'\n"
                        + "\n"
                        + "    productFlavors {\n"
                        + "        f1 {\n"
                        + "            dimension   'dimension1'\n"
                        + "            javaCompileOptions.annotationProcessorOptions.className 'f1'\n"
                        + "        }\n"
                        + "        f2 {\n"
                        + "            dimension   'dimension1'\n"
                        + "            javaCompileOptions.annotationProcessorOptions.className 'f2'\n"
                        + "        }\n"
                        + "\n"
                        + "        fa {\n"
                        + "            dimension   'dimension2'\n"
                        + "            javaCompileOptions.annotationProcessorOptions.className 'fa'\n"
                        + "        }\n"
                        + "        fb {\n"
                        + "            dimension   'dimension2'\n"
                        + "            javaCompileOptions.annotationProcessorOptions.className 'fb'\n"
                        + "        }\n"
                        + "        fc {\n"
                        + "            dimension   'dimension2'\n"
                        + "            javaCompileOptions.annotationProcessorOptions.className 'fc'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        plugin.createAndroidTasks(false);
        ImmutableMap<String, Integer> map =
                ImmutableMap.of("appVariants", 12, "unitTests", 12, "androidTests", 6);
        assertEquals(
                VariantCheckers.countVariants(map),
                plugin.getVariantManager().getVariantDataList().size());

        // we can now call this since the variants/tasks have been created

        // does not include tests
        Set<BaseTestedVariant> variants = checker.getVariants();
        assertEquals(12, variants.size());

        Set<TestVariant> testVariants = checker.getTestVariants();
        assertEquals(6, testVariants.size());

        checker.checkTestedVariant("f1FaDebug", "f1FaDebugAndroidTest", variants, testVariants);
        checker.checkTestedVariant("f1FbDebug", "f1FbDebugAndroidTest", variants, testVariants);
        checker.checkTestedVariant("f1FcDebug", "f1FcDebugAndroidTest", variants, testVariants);
        checker.checkTestedVariant("f2FaDebug", "f2FaDebugAndroidTest", variants, testVariants);
        checker.checkTestedVariant("f2FbDebug", "f2FbDebugAndroidTest", variants, testVariants);
        checker.checkTestedVariant("f2FcDebug", "f2FcDebugAndroidTest", variants, testVariants);

        Map<String, GradleVariantConfiguration> variantMap = getVariantMap();

        for (String dim1 : ImmutableList.of("f1", "f2")) {
            for (String dim2 : ImmutableList.of("fa", "fb", "fc")) {
                String variantName =
                        StringHelper.combineAsCamelCase(ImmutableList.of(dim1, dim2, "debug"));
                GradleVariantConfiguration variant = variantMap.get(variantName);
                assertThat(
                                variant.getJavaCompileOptions()
                                        .getAnnotationProcessorOptions()
                                        .getClassNames())
                        .containsExactly(dim2, dim1)
                        .inOrder();
            }
        }

        checker.checkNonTestedVariant("f1FaRelease", variants);
        checker.checkNonTestedVariant("f1FbRelease", variants);
        checker.checkNonTestedVariant("f1FcRelease", variants);
        checker.checkNonTestedVariant("f2FaRelease", variants);
        checker.checkNonTestedVariant("f2FbRelease", variants);
        checker.checkNonTestedVariant("f2FcRelease", variants);
    }

    @Test
    public void testSourceSetsApi() {
        // query the sourceSets, will throw if missing
        Eval.me(
                "project",
                project,
                "    println project.android.sourceSets.main.java.srcDirs\n"
                        + "println project.android.sourceSets.main.resources.srcDirs\n"
                        + "println project.android.sourceSets.main.manifest.srcFile\n"
                        + "println project.android.sourceSets.main.res.srcDirs\n"
                        + "println project.android.sourceSets.main.assets.srcDirs");
    }

    @Test
    public void testObfuscationMappingFile() {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    buildTypes {\n"
                        + "        release {\n"
                        + "            minifyEnabled true\n"
                        + "            proguardFile getDefaultProguardFile('proguard-android.txt')\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        plugin.createAndroidTasks(false);
        VariantCheckers.checkDefaultVariants(plugin.getVariantManager().getVariantScopes());

        // we can now call this since the variants/tasks have been created

        // does not include tests
        Set<BaseTestedVariant> variants = checker.getVariants();
        assertEquals(2, variants.size());

        for (BaseTestedVariant variant : variants) {
            if ("release".equals(variant.getBuildType().getName())) {
                assertNotNull(variant.getMappingFile());
            } else {
                Assert.assertNull(variant.getMappingFile());
            }
        }
    }

    @Test
    public void testProguardDsl() throws Exception {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    buildTypes {\n"
                        + "        release {\n"
                        + "            proguardFile 'file1.1'\n"
                        + "            proguardFiles 'file1.2', 'file1.3'\n"
                        + "        }\n"
                        + "\n"
                        + "        custom {\n"
                        + "            proguardFile 'file3.1'\n"
                        + "            proguardFiles 'file3.2', 'file3.3'\n"
                        + "            proguardFiles = ['file3.1']\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    flavorDimensions 'foo'\n"
                        + "    productFlavors {\n"
                        + "        f1 {\n"
                        + "            proguardFile 'file2.1'\n"
                        + "            proguardFiles 'file2.2', 'file2.3'\n"
                        + "        }\n"
                        + "\n"
                        + "        f2  {\n"
                        + "\n"
                        + "        }\n"
                        + "\n"
                        + "        f3 {\n"
                        + "            proguardFile 'file4.1'\n"
                        + "            proguardFiles 'file4.2', 'file4.3'\n"
                        + "            proguardFiles = ['file4.1']\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        plugin.createAndroidTasks(false);

        Map<String, List<String>> expected = new HashMap<>();
        expected.put(
                "f1Release",
                ImmutableList.of("file1.1", "file1.2", "file1.3", "file2.1", "file2.2", "file2.3"));
        expected.put("f1Debug", ImmutableList.of("file2.1", "file2.2", "file2.3"));
        expected.put("f2Release", ImmutableList.of("file1.1", "file1.2", "file1.3"));
        expected.put("f2Debug", Collections.emptyList());
        expected.put("f2Custom", ImmutableList.of("file3.1"));
        expected.put("f3Custom", ImmutableList.of("file3.1", "file4.1"));

        checkProguardFiles(expected);
    }

    @Test
    public void testSettingLanguageLevelFromCompileSdk_doNotOverride() {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    compileOptions {\n"
                        + "        sourceCompatibility '1.6'\n"
                        + "        targetCompatibility '1.6'\n"
                        + "    }\n"
                        + "}\n");
        plugin.createAndroidTasks(false);

        AndroidJavaCompile compileReleaseJavaWithJavac =
                (AndroidJavaCompile)
                        project.getTasks().getByName(checker.getReleaseJavacTaskName());
        assertEquals(
                JavaVersion.VERSION_1_6.toString(),
                compileReleaseJavaWithJavac.getTargetCompatibility());
        assertEquals(
                JavaVersion.VERSION_1_6.toString(),
                compileReleaseJavaWithJavac.getSourceCompatibility());
    }

    @Test
    public void testMockableJarName() {
        android.setCompileSdkVersion(
                "Google Inc.:Google APIs:" + TestConstants.COMPILE_SDK_VERSION);
        plugin.createAndroidTasks(false);

        MockableAndroidJarTask mockableAndroidJar =
                (MockableAndroidJarTask) project.getTasks().getByName("mockableAndroidJar");
        File mockableJarFile = mockableAndroidJar.getOutputFile();
        if (SdkConstants.CURRENT_PLATFORM != SdkConstants.PLATFORM_WINDOWS) {
            // Windows paths contain : to identify drives.
            assertFalse(mockableJarFile.getAbsolutePath().contains(":"));
        }

        assertEquals("mockable-Google-Inc.-Google-APIs-24.v2.jar", mockableJarFile.getName());
    }

    @Test
    public void testEncoding() {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "\n"
                        + "    compileOptions {\n"
                        + "       encoding 'foo'\n"
                        + "    }\n"
                        + "}\n");
        plugin.createAndroidTasks(false);

        AndroidJavaCompile compileReleaseJavaWithJavac =
                (AndroidJavaCompile)
                        project.getTasks().getByName(checker.getReleaseJavacTaskName());
        assertEquals("foo", compileReleaseJavaWithJavac.getOptions().getEncoding());
    }

    @Test
    public void testInstrumentationRunnerArguments_merging() throws Exception {

        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    defaultConfig {\n"
                        + "        testInstrumentationRunnerArguments(value: 'default', size: 'small')\n"
                        + "    }\n"
                        + "\n"
                        + "    flavorDimensions 'foo'\n"
                        + "    productFlavors {\n"
                        + "        f1 {\n"
                        + "        }\n"
                        + "\n"
                        + "        f2  {\n"
                        + "            testInstrumentationRunnerArgument 'value', 'f2'\n"
                        + "        }\n"
                        + "\n"
                        + "        f3  {\n"
                        + "            testInstrumentationRunnerArguments['otherValue'] = 'f3'\n"
                        + "        }\n"
                        + "\n"
                        + "        f4  {\n"
                        + "            testInstrumentationRunnerArguments(otherValue: 'f4.1')\n"
                        + "            testInstrumentationRunnerArguments = [otherValue: 'f4.2']\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        plugin.createAndroidTasks(false);

        Map<String, GradleVariantConfiguration> variantMap = getVariantMap();

        Map<String, Map<String, String>> expected =
                ImmutableMap.of(
                        "f1Debug",
                        ImmutableMap.of("value", "default", "size", "small"),
                        "f2Debug",
                        ImmutableMap.of("value", "f2", "size", "small"),
                        "f3Debug",
                        ImmutableMap.of("value", "default", "size", "small", "otherValue", "f3"),
                        "f4Debug",
                        ImmutableMap.of("value", "default", "size", "small", "otherValue", "f4.2"));

        expected.forEach(
                (variant, args) ->
                        assertThat(variantMap.get(variant).getInstrumentationRunnerArguments())
                                .containsExactlyEntriesIn(args));
    }

    /** Make sure DSL objects don't need "=" everywhere. */
    @Test
    public void testSetters() throws Exception {

        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "\n"
                        + "\n"
                        + "    buildTypes {\n"
                        + "        debug {\n"
                        + "            useProguard false\n"
                        + "            shrinkResources true\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n");
        BuildType debug = android.getBuildTypes().getByName("debug");
        assertThat(debug.isUseProguard()).isFalse();
        assertThat(debug.isShrinkResources()).isTrue();
        //noinspection deprecation
        assertThat(debug.getUseJack()).isNull();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testAdbExe() throws Exception {
        assertNotNull(android.getAdbExe());
        assertNotNull(android.getAdbExecutable());
        assertEquals(android.getAdbExe(), android.getAdbExecutable());
    }

    public void checkProguardFiles(Map<String, List<String>> expected) {
        Map<String, GradleVariantConfiguration> variantMap = getVariantMap();
        expected.forEach(
                (variantName, expectedFileNames) -> {
                    Set<File> proguardFiles =
                            variantMap
                                    .get(variantName)
                                    .getProguardFiles(Collections.emptyList());
                    Set<File> expectedFiles =
                            expectedFileNames
                                    .stream()
                                    .map(project::file)
                                    .collect(Collectors.toSet());
                    assertThat(proguardFiles)
                            .named("Proguard files for " + variantName)
                            .containsExactlyElementsIn(expectedFiles);
                });
    }

    public Map<String, GradleVariantConfiguration> getVariantMap() {
        Map<String, GradleVariantConfiguration> result = new HashMap<>();
        for (VariantScope variantScope : plugin.getVariantManager().getVariantScopes()) {
            result.put(variantScope.getFullVariantName(), variantScope.getVariantConfiguration());
        }
        return result;
    }
}

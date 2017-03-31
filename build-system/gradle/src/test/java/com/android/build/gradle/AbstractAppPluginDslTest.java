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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.TestVariant;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.BuildType;
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
import org.gradle.api.DomainObjectSet;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.junit.Assert;

/** Base class for checking the "application" and "atom" DSLs. */
public abstract class AbstractAppPluginDslTest<
                PluginT extends BasePlugin,
                ExtensionT extends BaseExtension,
                VariantT extends BaseVariant>
        extends BaseDslTest {
    protected PluginT plugin;
    protected ExtensionT android;

    protected static void checkTestTasks(@NonNull TestVariant variant) {
        assertNotNull(variant.getAidlCompile());
        assertNotNull(variant.getMergeResources());
        assertNotNull(variant.getMergeAssets());
        assertNotNull(variant.getGenerateBuildConfig());
        assertNotNull(variant.getJavaCompiler());
        assertNotNull(variant.getProcessJavaResources());
        assertNotNull(variant.getAssemble());
        assertNotNull(variant.getConnectedInstrumentTest());
        assertNotNull(variant.getTestedVariant());
        assertTrue(variant.getOutputs().isEmpty());

        // FIX ME !
        //for (BaseVariantOutput baseVariantOutput : variant.getOutputs()) {
        //    assertNotNull(baseVariantOutput.getProcessManifest());
        //    assertNotNull(baseVariantOutput.getProcessResources());
        //}
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        project.apply(ImmutableMap.of("plugin", getPluginName()));
        android = project.getExtensions().getByType(getExtensionClass());
        android.setCompileSdkVersion(COMPILE_SDK_VERSION);
        android.setBuildToolsVersion(BUILD_TOOL_VERSION);
        plugin = getPlugin();
    }

    @NonNull
    protected abstract String getPluginName();

    @NonNull
    protected abstract Class<PluginT> getPluginClass();

    @NonNull
    protected abstract Class<ExtensionT> getExtensionClass();

    @NonNull
    protected abstract DomainObjectSet<TestVariant> getTestVariants();

    @NonNull
    protected abstract DomainObjectSet<VariantT> getVariants();

    @NonNull
    protected abstract String getReleaseJavacTaskName();

    protected abstract void checkTestedVariant(
            @NonNull String variantName,
            @NonNull String testedVariantName,
            @NonNull Collection<VariantT> variants,
            @NonNull Set<TestVariant> testVariants);

    protected abstract void checkNonTestedVariant(
            @NonNull String variantName, @NonNull Set<VariantT> variants);

    public void testBasic() {
        plugin.createAndroidTasks(false);
        checkDefaultVariants(plugin.getVariantManager().getVariantScopes());

        // we can now call this since the variants/tasks have been created
        Set<VariantT> variants = getVariants();
        assertEquals(2, variants.size());

        Set<TestVariant> testVariants = getTestVariants();
        assertEquals(1, testVariants.size());

        checkTestedVariant("debug", "debugAndroidTest", variants, testVariants);
        checkNonTestedVariant("release", variants);
    }

    /** Same as Basic but with a slightly different DSL. */
    public void testBasic2() {
        plugin.createAndroidTasks(false);
        checkDefaultVariants(plugin.getVariantManager().getVariantScopes());

        // we can now call this since the variants/tasks have been created
        Set<VariantT> variants = getVariants();
        assertEquals(2, variants.size());

        Set<TestVariant> testVariants = getTestVariants();
        assertEquals(1, testVariants.size());

        checkTestedVariant("debug", "debugAndroidTest", variants, testVariants);
        checkNonTestedVariant("release", variants);
    }

    public void testBasicWithStringTarget() {
        Eval.me(
                "project",
                project,
                "\n        project.android {\n            compileSdkVersion 'android-"
                        + String.valueOf(COMPILE_SDK_VERSION)
                        + "'\n        }\n");

        plugin.createAndroidTasks(false);
        checkDefaultVariants(plugin.getVariantManager().getVariantScopes());

        // we can now call this since the variants/tasks have been created
        Set<VariantT> variants = getVariants();
        assertEquals(2, variants.size());

        Set<TestVariant> testVariants = getTestVariants();
        assertEquals(1, testVariants.size());

        checkTestedVariant("debug", "debugAndroidTest", variants, testVariants);
        checkNonTestedVariant("release", variants);
    }

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
        assertEquals(countVariants(map), plugin.getVariantManager().getVariantScopes().size());

        // we can now call this since the variants/tasks have been created

        // does not include tests
        Set<VariantT> variants = getVariants();
        assertEquals(3, variants.size());

        Set<TestVariant> testVariants = getTestVariants();
        assertEquals(1, testVariants.size());

        checkTestedVariant("staging", "stagingAndroidTest", variants, testVariants);

        checkNonTestedVariant("debug", variants);
        checkNonTestedVariant("release", variants);
    }

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
        assertEquals(countVariants(map), plugin.getVariantManager().getVariantScopes().size());

        // we can now call this since the variants/tasks have been created

        // does not include tests
        Set<VariantT> variants = getVariants();
        assertEquals(4, variants.size());

        Set<TestVariant> testVariants = getTestVariants();
        assertEquals(2, testVariants.size());

        checkTestedVariant("flavor1Debug", "flavor1DebugAndroidTest", variants, testVariants);
        checkTestedVariant("flavor2Debug", "flavor2DebugAndroidTest", variants, testVariants);

        checkNonTestedVariant("flavor1Release", variants);
        checkNonTestedVariant("flavor2Release", variants);
    }

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
        assertEquals(countVariants(map), plugin.getVariantManager().getVariantScopes().size());

        // we can now call this since the variants/tasks have been created

        // does not include tests
        Set<VariantT> variants = getVariants();
        assertEquals(12, variants.size());

        Set<TestVariant> testVariants = getTestVariants();
        assertEquals(6, testVariants.size());

        checkTestedVariant("f1FaDebug", "f1FaDebugAndroidTest", variants, testVariants);
        checkTestedVariant("f1FbDebug", "f1FbDebugAndroidTest", variants, testVariants);
        checkTestedVariant("f1FcDebug", "f1FcDebugAndroidTest", variants, testVariants);
        checkTestedVariant("f2FaDebug", "f2FaDebugAndroidTest", variants, testVariants);
        checkTestedVariant("f2FbDebug", "f2FbDebugAndroidTest", variants, testVariants);
        checkTestedVariant("f2FcDebug", "f2FcDebugAndroidTest", variants, testVariants);

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

        checkNonTestedVariant("f1FaRelease", variants);
        checkNonTestedVariant("f1FbRelease", variants);
        checkNonTestedVariant("f1FcRelease", variants);
        checkNonTestedVariant("f2FaRelease", variants);
        checkNonTestedVariant("f2FbRelease", variants);
        checkNonTestedVariant("f2FcRelease", variants);
    }

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
        checkDefaultVariants(plugin.getVariantManager().getVariantScopes());

        // we can now call this since the variants/tasks have been created

        // does not include tests
        Set<VariantT> variants = getVariants();
        assertEquals(2, variants.size());

        for (VariantT variant : variants) {
            if ("release".equals(variant.getBuildType().getName())) {
                assertNotNull(variant.getMappingFile());
            } else {
                Assert.assertNull(variant.getMappingFile());
            }
        }
    }

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

    public void testSettingLanguageLevelFromCompileSdk_dontOverride() {
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
                (AndroidJavaCompile) project.getTasks().getByName(getReleaseJavacTaskName());
        assertEquals(
                JavaVersion.VERSION_1_6.toString(),
                compileReleaseJavaWithJavac.getTargetCompatibility());
        assertEquals(
                JavaVersion.VERSION_1_6.toString(),
                compileReleaseJavaWithJavac.getSourceCompatibility());
    }

    public void testMockableJarName() {
        android.setCompileSdkVersion("Google Inc.:Google APIs:" + COMPILE_SDK_VERSION);
        plugin.createAndroidTasks(false);

        MockableAndroidJarTask mockableAndroidJar =
                (MockableAndroidJarTask) project.getTasks().getByName("mockableAndroidJar");
        File mockableJarFile = mockableAndroidJar.getOutputFile();
        if (SdkConstants.CURRENT_PLATFORM != SdkConstants.PLATFORM_WINDOWS) {
            // Windows paths contain : to identify drives.
            Assert.assertFalse(mockableJarFile.getAbsolutePath().contains(":"));
        }

        assertEquals("mockable-Google-Inc.-Google-APIs-24.v2.jar", mockableJarFile.getName());
    }

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
                (AndroidJavaCompile) project.getTasks().getByName(getReleaseJavacTaskName());
        assertEquals("foo", compileReleaseJavaWithJavac.getOptions().getEncoding());
    }

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
                        "f1Debug", ImmutableMap.of("value", "default", "size", "small"),
                        "f2Debug", ImmutableMap.of("value", "f2", "size", "small"),
                        "f3Debug",
                                ImmutableMap.of(
                                        "value", "default", "size", "small", "otherValue", "f3"),
                        "f4Debug",
                                ImmutableMap.of(
                                        "value", "default", "size", "small", "otherValue", "f4.2"));

        expected.forEach(
                (variant, args) ->
                        assertThat(variantMap.get(variant).getInstrumentationRunnerArguments())
                                .containsExactlyEntriesIn(args));
    }

    /** Make sure DSL objects don't need "=" everywhere. */
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

    protected <T> T getTask(String name, @SuppressWarnings("unused") Class<T> klass) {
        //noinspection unchecked
        return (T) project.getTasks().getByName(name);
    }

    public Map<String, GradleVariantConfiguration> getVariantMap() {
        Map<String, GradleVariantConfiguration> result = new HashMap<>();
        for (VariantScope variantScope : plugin.getVariantManager().getVariantScopes()) {
            result.put(variantScope.getFullVariantName(), variantScope.getVariantConfiguration());
        }
        return result;
    }

    @SuppressWarnings("unchecked") // For some reason type bounds don't "just compile".
    private PluginT getPlugin() {
        Class<? extends Plugin> pluginClass = (Class<? extends Plugin>) getPluginClass();
        Plugin plugin = project.getPlugins().getPlugin(pluginClass);
        return (PluginT) plugin;
    }
}

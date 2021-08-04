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
import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.build.api.component.impl.ComponentImpl;
import com.android.build.api.dsl.ApkSigningConfig;
import com.android.build.api.dsl.ApplicationExtension;
import com.android.build.api.variant.impl.ApplicationVariantBuilderImpl;
import com.android.build.api.variant.impl.ApplicationVariantImpl;
import com.android.build.api.variant.impl.SigningConfigImpl;
import com.android.build.api.variant.impl.VariantImpl;
import com.android.build.gradle.AppExtension;
import com.android.build.gradle.internal.BadPluginException;
import com.android.build.gradle.internal.VariantManager;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.fixture.TestConstants;
import com.android.build.gradle.internal.fixture.TestProjects;
import com.android.build.gradle.internal.fixture.VariantCheckers;
import com.android.build.gradle.internal.packaging.GradleKeystoreHelper;
import com.android.build.gradle.internal.variant.ComponentInfo;
import com.android.build.gradle.internal.variant.VariantInputModel;
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.SigningConfig;
import com.android.prefs.AbstractAndroidLocations;
import com.android.utils.EnvironmentProvider;
import com.android.utils.StdLogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import groovy.util.Eval;
import java.io.File;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import junit.framework.TestCase;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.project.ProjectStateInternal;
import org.gradle.api.tasks.compile.JavaCompile;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.Opcodes;

/** Tests for the internal workings of the app plugin ("android") */
public class AppPluginInternalTest {

    @Rule public TemporaryFolder projectDirectory = new TemporaryFolder();
    private Project project;

    @Before
    public void setUp() throws Exception {
        project =
                TestProjects.builder(projectDirectory.newFolder("project").toPath())
                        .withPlugin(TestProjects.Plugin.APP)
                        .build();
        AppExtension android = project.getExtensions().getByType(AppExtension.class);
        android.setCompileSdkVersion(TestConstants.COMPILE_SDK_VERSION);
        android.setBuildToolsVersion(TestConstants.BUILD_TOOL_VERSION);
    }

    @Test
    public void testBasic() {
        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        plugin.createAndroidTasks();

        final VariantInputModel variantInputModel = plugin.getVariantInputModel();
        TestCase.assertEquals(2, variantInputModel.getBuildTypes().size());
        TestCase.assertNotNull(variantInputModel.getBuildTypes().get(BuilderConstants.DEBUG));
        TestCase.assertNotNull(variantInputModel.getBuildTypes().get(BuilderConstants.RELEASE));
        TestCase.assertEquals(0, variantInputModel.getProductFlavors().size());

        List<ComponentImpl> components = getComponents(plugin.getVariantManager());

        VariantCheckers.checkDefaultVariants(components);

        VariantCheckers.findComponent(components, "debug");
        VariantCheckers.findComponent(components, "release");
        VariantCheckers.findComponent(components, "debugAndroidTest");
    }

    @Test
    public void testDefaultConfig() {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    signingConfigs {\n"
                        + "        fakeConfig {\n"
                        + "            storeFile project.file('aa')\n"
                        + "            storePassword 'bb'\n"
                        + "            keyAlias 'cc'\n"
                        + "            keyPassword 'dd'\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    defaultConfig {\n"
                        + "        versionCode 1\n"
                        + "        versionName '2.0'\n"
                        + "        minSdkVersion 2\n"
                        + "        targetSdkVersion 3\n"
                        + "\n"
                        + "        signingConfig signingConfigs.fakeConfig\n"
                        + "    }\n"
                        + "}\n");

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        plugin.createAndroidTasks();

        TestCase.assertEquals(
                Integer.valueOf(1), plugin.getExtension().getDefaultConfig().getVersionCode());
        TestCase.assertNotNull(plugin.getExtension().getDefaultConfig().getMinSdkVersion());
        TestCase.assertEquals(
                2, plugin.getExtension().getDefaultConfig().getMinSdkVersion().getApiLevel());
        TestCase.assertNotNull(plugin.getExtension().getDefaultConfig().getTargetSdkVersion());
        TestCase.assertEquals(
                3, plugin.getExtension().getDefaultConfig().getTargetSdkVersion().getApiLevel());
        TestCase.assertEquals("2.0", plugin.getExtension().getDefaultConfig().getVersionName());

        TestCase.assertEquals(
                new File(project.getProjectDir(), "aa"),
                plugin.getExtension().getDefaultConfig().getSigningConfig().getStoreFile());
        TestCase.assertEquals(
                "bb",
                plugin.getExtension().getDefaultConfig().getSigningConfig().getStorePassword());
        TestCase.assertEquals(
                "cc", plugin.getExtension().getDefaultConfig().getSigningConfig().getKeyAlias());
        TestCase.assertEquals(
                "dd", plugin.getExtension().getDefaultConfig().getSigningConfig().getKeyPassword());
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

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        plugin.createAndroidTasks();

        TestCase.assertEquals(3, plugin.getVariantInputModel().getBuildTypes().size());

        List<ComponentImpl> components = getComponents(plugin.getVariantManager());

        LinkedHashMap<String, Integer> map = new LinkedHashMap<>(3);
        map.put("appVariants", 3);
        map.put("unitTests", 3);
        map.put("androidTests", 1);
        TestCase.assertEquals(VariantCheckers.countVariants(map), components.size());

        String[] variantNames = new String[] {"debug", "release", "staging"};

        for (String variantName : variantNames) {
            VariantCheckers.findComponent(components, variantName);
        }

        ComponentImpl testVariant = VariantCheckers.findComponent(components, "stagingAndroidTest");
        TestCase.assertEquals("staging", testVariant.getBuildType());
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

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        plugin.createAndroidTasks();

        TestCase.assertEquals(2, plugin.getVariantInputModel().getProductFlavors().size());

        List<ComponentImpl> components = getComponents(plugin.getVariantManager());

        LinkedHashMap<String, Integer> map = new LinkedHashMap<>(3);
        map.put("appVariants", 4);
        map.put("unitTests", 4);
        map.put("androidTests", 2);
        TestCase.assertEquals(VariantCheckers.countVariants(map), components.size());

        String[] variantNames =
                new String[] {
                    "flavor1Debug",
                    "flavor1Release",
                    "flavor1DebugAndroidTest",
                    "flavor2Debug",
                    "flavor2Release",
                    "flavor2DebugAndroidTest"
                };

        for (String variantName : variantNames) {
            VariantCheckers.findComponent(components, variantName);
        }
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
                        + "            dimension 'dimension1'\n"
                        + "        }\n"
                        + "        f2 {\n"
                        + "            dimension 'dimension1'\n"
                        + "        }\n"
                        + "\n"
                        + "        fa {\n"
                        + "            dimension 'dimension2'\n"
                        + "        }\n"
                        + "        fb {\n"
                        + "            dimension 'dimension2'\n"
                        + "        }\n"
                        + "        fc {\n"
                        + "            dimension 'dimension2'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n");

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        plugin.createAndroidTasks();

        TestCase.assertEquals(5, plugin.getVariantInputModel().getProductFlavors().size());

        List<ComponentImpl> components = getComponents(plugin.getVariantManager());
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>(3);
        map.put("appVariants", 12);
        map.put("unitTests", 12);
        map.put("androidTests", 6);
        TestCase.assertEquals(VariantCheckers.countVariants(map), components.size());

        String[] variantNames =
                new String[] {
                    "f1FaDebug",
                    "f1FbDebug",
                    "f1FcDebug",
                    "f2FaDebug",
                    "f2FbDebug",
                    "f2FcDebug",
                    "f1FaRelease",
                    "f1FbRelease",
                    "f1FcRelease",
                    "f2FaRelease",
                    "f2FbRelease",
                    "f2FcRelease",
                    "f1FaDebugAndroidTest",
                    "f1FbDebugAndroidTest",
                    "f1FcDebugAndroidTest",
                    "f2FaDebugAndroidTest",
                    "f2FbDebugAndroidTest",
                    "f2FcDebugAndroidTest"
                };

        for (String variantName : variantNames) {
            VariantCheckers.findComponent(components, variantName);
        }
    }
    @Test
    public void testSigningConfigs() throws Exception {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "\n"
                        + "    signingConfigs {\n"
                        + "        one {\n"
                        + "            storeFile project.file('a1')\n"
                        + "            storePassword 'b1'\n"
                        + "            keyAlias 'c1'\n"
                        + "            keyPassword 'd1'\n"
                        + "        }\n"
                        + "        two {\n"
                        + "            storeFile project.file('a2')\n"
                        + "            storePassword 'b2'\n"
                        + "            keyAlias 'c2'\n"
                        + "            keyPassword 'd2'\n"
                        + "        }\n"
                        + "        three {\n"
                        + "            storeFile project.file('a3')\n"
                        + "            storePassword 'b3'\n"
                        + "            keyAlias 'c3'\n"
                        + "            keyPassword 'd3'\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    defaultConfig {\n"
                        + "        versionCode 1\n"
                        + "        versionName '2.0'\n"
                        + "        minSdkVersion 2\n"
                        + "        targetSdkVersion 3\n"
                        + "    }\n"
                        + "\n"
                        + "    buildTypes {\n"
                        + "        debug {\n"
                        + "        }\n"
                        + "        staging {\n"
                        + "        }\n"
                        + "        release {\n"
                        + "            signingConfig owner.signingConfigs.three\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    flavorDimensions 'foo'\n"
                        + "    productFlavors {\n"
                        + "        flavor1 {\n"
                        + "        }\n"
                        + "        flavor2 {\n"
                        + "            signingConfig owner.signingConfigs.one\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "}\n");

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        plugin.createAndroidTasks();

        List<ComponentImpl> components = getComponents(plugin.getVariantManager());
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>(3);
        map.put("appVariants", 6);
        map.put("unitTests", 6);
        map.put("androidTests", 2);
        TestCase.assertEquals(VariantCheckers.countVariants(map), components.size());

        ComponentImpl variant;
        SigningConfigImpl signingConfig;

        variant = VariantCheckers.findComponent(components, "flavor1Debug");
        signingConfig = ((ApplicationVariantImpl) variant).getSigningConfig();
        TestCase.assertNotNull(signingConfig);
        final File file = signingConfig.getStoreFile().get();
        assertNotNull(file);
        assertThat(file)
                .isEqualTo(
                        GradleKeystoreHelper.getDefaultDebugKeystoreLocation(
                                new AbstractAndroidLocations(
                                        EnvironmentProvider.DIRECT,
                                        new StdLogger(StdLogger.Level.VERBOSE),
                                        true) {}));

        variant = VariantCheckers.findComponent(components, "flavor1Staging");
        signingConfig = ((ApplicationVariantImpl) variant).getSigningConfig();
        TestCase.assertNull(signingConfig);

        variant = VariantCheckers.findComponent(components, "flavor1Release");
        signingConfig = ((ApplicationVariantImpl) variant).getSigningConfig();
        TestCase.assertNotNull(signingConfig);
        TestCase.assertEquals(
                new File(project.getProjectDir(), "a3"), signingConfig.getStoreFile().get());

        variant = VariantCheckers.findComponent(components, "flavor2Debug");
        signingConfig = ((ApplicationVariantImpl) variant).getSigningConfig();
        TestCase.assertNotNull(signingConfig);
        final File file1 = signingConfig.getStoreFile().get();
        assertNotNull(file1);
        assertThat(file1)
                .isEqualTo(
                        GradleKeystoreHelper.getDefaultDebugKeystoreLocation(
                                new AbstractAndroidLocations(
                                        EnvironmentProvider.DIRECT,
                                        new StdLogger(StdLogger.Level.VERBOSE),
                                        true) {}));

        variant = VariantCheckers.findComponent(components, "flavor2Staging");
        signingConfig = ((ApplicationVariantImpl) variant).getSigningConfig();
        TestCase.assertNotNull(signingConfig);
        TestCase.assertEquals(
                new File(project.getProjectDir(), "a1"), signingConfig.getStoreFile().get());

        variant = VariantCheckers.findComponent(components, "flavor2Release");
        signingConfig = ((ApplicationVariantImpl) variant).getSigningConfig();
        TestCase.assertNotNull(signingConfig);
        TestCase.assertEquals(
                new File(project.getProjectDir(), "a3"), signingConfig.getStoreFile().get());
    }

    /**
     * test that debug build type maps to the SigningConfig object as the signingConfig container
     */
    @Test
    public void testDebugSigningConfig() throws Exception {
        Eval.me(
                "project",
                project,
                "\n"
                        + "\n"
                        + "project.android {\n"
                        + "    signingConfigs {\n"
                        + "        debug {\n"
                        + "            storePassword = 'foo'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        plugin.createAndroidTasks();

        // check that the debug buildType has the updated debug signing config.
        BuildType buildType =
                plugin.getVariantInputModel()
                        .getBuildTypes()
                        .get(BuilderConstants.DEBUG)
                        .getBuildType();
        ApkSigningConfig signingConfig = buildType.getSigningConfig();
        TestCase.assertEquals(
                plugin.getVariantInputModel().getSigningConfigs().get(BuilderConstants.DEBUG),
                signingConfig);
        TestCase.assertEquals("foo", signingConfig.getStorePassword());
    }

    @Test
    public void testSigningConfigInitWith() throws Exception {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    signingConfigs {\n"
                        + "        foo.initWith(owner.signingConfigs.debug)\n"
                        + "    }\n"
                        + "}\n");

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);

        SigningConfig debugSC =
                plugin.getVariantInputModel().getSigningConfigs().get(BuilderConstants.DEBUG);
        SigningConfig fooSC = plugin.getVariantInputModel().getSigningConfigs().get("foo");

        TestCase.assertNotNull(fooSC);

        TestCase.assertEquals(debugSC.getStoreFile(), fooSC.getStoreFile());
        TestCase.assertEquals(debugSC.getStorePassword(), fooSC.getStorePassword());
        TestCase.assertEquals(debugSC.getKeyAlias(), fooSC.getKeyAlias());
        TestCase.assertEquals(debugSC.getKeyPassword(), fooSC.getKeyPassword());
    }

    @Test
    public void testPluginDetection() {
        project.apply(ImmutableMap.of("plugin", "java"));

        AppExtension android = project.getExtensions().getByType(AppExtension.class);
        android.setCompileSdkVersion(TestConstants.COMPILE_SDK_VERSION);
        android.setBuildToolsVersion(TestConstants.BUILD_TOOL_VERSION);

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        Exception recordedException = null;
        try {
            plugin.createAndroidTasks();
        } catch (Exception e) {
            recordedException = e;
        }

        TestCase.assertNotNull(recordedException);
        TestCase.assertSame(BadPluginException.class, recordedException.getClass());
    }

    @Test
    public void testJava8CompileBootclasspath() {
        AppExtension android = project.getExtensions().getByType(AppExtension.class);
        android.setCompileSdkVersion(TestConstants.COMPILE_SDK_VERSION);
        android.setBuildToolsVersion(TestConstants.BUILD_TOOL_VERSION);
        android.getCompileOptions().setSourceCompatibility(JavaVersion.VERSION_1_8);
        android.getCompileOptions().setTargetCompatibility(JavaVersion.VERSION_1_8);

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        plugin.createAndroidTasks();
        ((ProjectStateInternal) project.getState()).configured();

        JavaCompile compileDebugJavaWithJavac =
                (JavaCompile) project.getTasks().getByName("compileDebugJavaWithJavac");
        Set<File> bootclasspath =
                compileDebugJavaWithJavac.getOptions().getBootstrapClasspath().getFiles();
        assertThat(bootclasspath.stream().map(File::getName).collect(Collectors.toSet()))
                .containsExactly("android.jar", "core-lambda-stubs.jar");
        assertThat(bootclasspath)
                .containsExactlyElementsIn(
                        plugin.globalScope.getBootClasspath().get().stream()
                                .map(RegularFile::getAsFile)
                                .collect(Collectors.toList()));
    }

    @Test
    public void testAsmVersionIsTheLatest() throws IllegalAccessException {
        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        plugin.createAndroidTasks();
        List<ComponentImpl> components = getComponents(plugin.getVariantManager());

        VariantCheckers.checkDefaultVariants(components);

        int latestAsmVersion = 4;
        int latestAsmVersionEnum = Opcodes.ASM4;
        for (Field field : Opcodes.class.getDeclaredFields()) {
            if (field.getName().startsWith("ASM") && !field.getName().endsWith("EXPERIMENTAL")) {
                int asmVersion = Integer.parseInt(field.getName().substring(3));
                if (asmVersion > latestAsmVersion) {
                    latestAsmVersion = asmVersion;
                    latestAsmVersionEnum = field.getInt(null);
                }
            }
        }

        for (ComponentImpl component : components) {
            assertThat(component.getAsmApiVersion()).isEqualTo(latestAsmVersionEnum);
        }
    }

    public static List<ComponentImpl> getComponents(
            @NonNull
                    VariantManager<
                                    ApplicationExtension,
                                    ?,
                                    ApplicationVariantBuilderImpl,
                                    ApplicationVariantImpl>
                            variantManager) {

        List<VariantImpl> variants =
                variantManager.getMainComponents().stream()
                        .map(ComponentInfo::getVariant)
                        .collect(Collectors.toList());

        ImmutableList.Builder<ComponentImpl> builder =
                ImmutableList.<ComponentImpl>builder().addAll(variants);
        variants.forEach(
                it ->
                        it.getNestedComponents()
                                .forEach(component -> builder.add((ComponentImpl) component)));
        return builder.build();
    }
}

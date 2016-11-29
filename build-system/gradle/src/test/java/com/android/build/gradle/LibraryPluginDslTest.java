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

import com.android.annotations.NonNull;
import com.android.build.gradle.api.LibraryVariant;
import com.android.build.gradle.api.TestVariant;
import com.android.builder.model.SigningConfig;
import com.google.common.collect.ImmutableMap;
import java.util.Set;

/** Tests for the public DSL of the Lib plugin ('com.android.library') */
public class LibraryPluginDslTest extends BaseDslTest {
    private LibraryPlugin plugin;
    private LibraryExtension android;

    private static void checkTestedVariant(
            @NonNull String variantName,
            @NonNull String testedVariantName,
            @NonNull Set<LibraryVariant> variants,
            @NonNull Set<TestVariant> testVariants) {
        LibraryVariant variant = findVariant(variants, variantName);
        assertNotNull(variant);
        assertNotNull(variant.getTestVariant());
        assertEquals(testedVariantName, variant.getTestVariant().getName());
        assertEquals(variant.getTestVariant(), findVariant(testVariants, testedVariantName));
        checkLibraryTasks(variant);
        checkTestTasks(variant.getTestVariant());
    }

    private static void checkNonTestedVariant(
            @NonNull String variantName, @NonNull Set<LibraryVariant> variants) {
        LibraryVariant variant = findVariant(variants, variantName);
        assertNotNull(variant);
        assertNull(variant.getTestVariant());
        checkLibraryTasks(variant);
    }

    private static void checkTestTasks(@NonNull TestVariant variant) {
        assertNotNull(variant.getAidlCompile());
        assertNotNull(variant.getMergeResources());
        assertNotNull(variant.getMergeAssets());
        assertNotNull(variant.getMergeResources());
        assertNotNull(variant.getGenerateBuildConfig());
        assertNotNull(variant.getJavaCompile());
        assertNotNull(variant.getProcessJavaResources());

        assertNotNull(variant.getAssemble());
        assertNotNull(variant.getUninstall());

        if (variant.isSigningReady()) {
            assertNotNull(variant.getInstall());
        } else {
            assertNull(variant.getInstall());
        }

        assertNotNull(variant.getConnectedInstrumentTest());
    }

    private static void checkLibraryTasks(@NonNull LibraryVariant variant) {
        assertNotNull(variant.getCheckManifest());
        assertNotNull(variant.getAidlCompile());
        assertNotNull(variant.getMergeResources());
        assertNotNull(variant.getGenerateBuildConfig());
        assertNotNull(variant.getJavaCompile());
        assertNotNull(variant.getProcessJavaResources());
        assertNotNull(variant.getAssemble());
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        project.apply(ImmutableMap.of("plugin", "com.android.library"));
        android = project.getExtensions().getByType(LibraryExtension.class);
        android.setCompileSdkVersion(COMPILE_SDK_VERSION);
        android.setBuildToolsVersion(BUILD_TOOL_VERSION);
        plugin = project.getPlugins().getPlugin(LibraryPlugin.class);
    }

    public void testBasic() {
        plugin.createAndroidTasks(false);

        Set<LibraryVariant> variants = android.getLibraryVariants();
        assertThat(variants).hasSize(2);

        Set<TestVariant> testVariants = android.getTestVariants();
        assertThat(testVariants).hasSize(1);

        checkTestedVariant("debug", "debugAndroidTest", variants, testVariants);
        checkNonTestedVariant("release", variants);
    }

    public void testNewBuildType() {
        android.getBuildTypes().create("custom");
        plugin.createAndroidTasks(false);

        Set<LibraryVariant> variants = android.getLibraryVariants();
        assertThat(variants).hasSize(3);

        Set<TestVariant> testVariants = android.getTestVariants();
        assertThat(testVariants).hasSize(1);

        checkTestedVariant("debug", "debugAndroidTest", variants, testVariants);
        checkNonTestedVariant("release", variants);
        checkNonTestedVariant("custom", variants);
    }

    public void testNewBuildType_testBuildType() {
        android.getBuildTypes().create("custom");
        android.setTestBuildType("custom");
        plugin.createAndroidTasks(false);

        Set<LibraryVariant> variants = android.getLibraryVariants();
        assertThat(variants).hasSize(3);

        Set<TestVariant> testVariants = android.getTestVariants();
        assertThat(testVariants).hasSize(1);

        checkTestedVariant("custom", "customAndroidTest", variants, testVariants);
        checkNonTestedVariant("release", variants);
        checkNonTestedVariant("debug", variants);
    }

    /**
     * test that debug build type maps to the SigningConfig object as the signingConfig container
     */
    public void testDebugSigningConfig() throws Exception {
        android.getSigningConfigs().getByName("debug", debug -> debug.setStorePassword("foo"));

        SigningConfig signingConfig = android.getBuildTypes().getByName("debug").getSigningConfig();

        assertNotNull(signingConfig);
        assertEquals(android.getSigningConfigs().getByName("debug"), signingConfig);
        assertEquals("foo", signingConfig.getStorePassword());
    }
}

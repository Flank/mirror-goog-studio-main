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

package com.android.build.gradle

import com.android.annotations.NonNull
import com.android.build.gradle.api.AtomVariant
import com.android.build.gradle.api.AtomVariantOutput
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.api.TestVariant
import com.android.build.gradle.internal.SdkHandler
import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.test.BaseTest
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

import static com.android.build.gradle.DslTestUtil.DEFAULT_VARIANTS
import static com.android.build.gradle.DslTestUtil.countVariants

/**
 * Tests for the public DSL of the Atom plugin.
 */
class AtomPluginDslTest  extends BaseTest {

    @Override
    protected void setUp() throws Exception {
        SdkHandler.testSdkFolder = new File(System.getenv("ANDROID_HOME"))
    }

    public void testBasic() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_PROJECTS}/basic")).build()

        project.apply plugin: 'com.android.atom'

        project.android {
            compileSdkVersion COMPILE_SDK_VERSION
            buildToolsVersion '20.0.0'
            defaultConfig {
                versionName '1.0'
            }
        }

        AtomPlugin plugin = project.plugins.getPlugin(AtomPlugin)

        plugin.createAndroidTasks(false)
        assertEquals(DEFAULT_VARIANTS.size(), plugin.variantManager.variantDataList.size())

        // we can now call this since the variants/tasks have been created
        Set<AtomVariant> variants = project.android.atomVariants
        assertEquals(2, variants.size())

        Set<TestVariant> testVariants = project.android.testVariants
        assertEquals(1, testVariants.size())

        checkTestedVariant("debug", "debugAndroidTest", variants, testVariants)
        checkNonTestedVariant("release", variants)
    }

    public void testBasicWithStringTarget() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_PROJECTS}/basic")).build()

        project.apply plugin: 'com.android.atom'

        project.android {
            compileSdkVersion "android-" + COMPILE_SDK_VERSION
            buildToolsVersion '20.0.0'
            defaultConfig {
                versionName '1.0'
            }
        }

        AtomPlugin plugin = project.plugins.getPlugin(AtomPlugin)

        plugin.createAndroidTasks(false)
        assertEquals(DEFAULT_VARIANTS.size(), plugin.variantManager.variantDataList.size())

        // we can now call this since the variants/tasks have been created
        Set<AtomVariant> variants = project.android.atomVariants
        assertEquals(2, variants.size())

        Set<TestVariant> testVariants = project.android.testVariants
        assertEquals(1, testVariants.size())

        checkTestedVariant("debug", "debugAndroidTest", variants, testVariants)
        checkNonTestedVariant("release", variants)
    }

    public void testMultiRes() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_PROJECTS}/multires")).build()

        project.apply plugin: 'com.android.atom'

        project.android {
            compileSdkVersion COMPILE_SDK_VERSION
            buildToolsVersion '20.0.0'
            defaultConfig {
                versionName '1.0'
            }

            sourceSets {
                main {
                    res {
                        srcDirs 'src/main/res1', 'src/main/res2'
                    }
                }
            }
        }

        // nothing to be done here. If the DSL fails, it'll throw an exception
    }

    public void testBuildTypes() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_PROJECTS}/basic")).build()

        project.apply plugin: 'com.android.atom'

        project.android {
            compileSdkVersion COMPILE_SDK_VERSION
            buildToolsVersion '20.0.0'
            testBuildType "staging"
            defaultConfig {
                versionName '1.0'
            }

            buildTypes {
                staging {
                    signingConfig signingConfigs.debug
                }
            }
        }

        AtomPlugin plugin = project.plugins.getPlugin(AtomPlugin)

        plugin.createAndroidTasks(false)
        assertEquals(
                countVariants(atomVariants: 3, unitTest: 3, androidTests: 1),
                plugin.variantManager.variantDataList.size())

        // we can now call this since the variants/tasks have been created

        // does not include tests
        Set<AtomVariant> variants = project.android.atomVariants
        assertEquals(3, variants.size())

        Set<TestVariant> testVariants = project.android.testVariants
        assertEquals(1, testVariants.size())

        checkTestedVariant("staging", "stagingAndroidTest", variants, testVariants)

        checkNonTestedVariant("debug", variants)
        checkNonTestedVariant("release", variants)
    }

    public void testSourceSetsApi() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_PROJECTS}/basic")).build()

        project.apply plugin: 'com.android.atom'

        project.android {
            compileSdkVersion COMPILE_SDK_VERSION
            buildToolsVersion '20.0.0'
        }

        // query the sourceSets, will throw if missing
        println project.android.sourceSets.main.java.srcDirs
        println project.android.sourceSets.main.resources.srcDirs
        println project.android.sourceSets.main.manifest.srcFile
        println project.android.sourceSets.main.res.srcDirs
        println project.android.sourceSets.main.assets.srcDirs
    }

    public void testProguardDsl() throws Exception {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_PROJECTS}/basic")).build()

        project.apply plugin: 'com.android.application'

        project.android {
            compileSdkVersion COMPILE_SDK_VERSION
            buildToolsVersion '20.0.0'
            defaultConfig {
                versionName '1.0'
            }

            buildTypes {
                release {
                    proguardFile 'file1.1'
                    proguardFiles 'file1.2', 'file1.3'
                }

                custom {
                    proguardFile 'file3.1'
                    proguardFiles 'file3.2', 'file3.3'
                    proguardFiles = ['file3.1']
                }
            }

            productFlavors {
                f1 {
                    proguardFile 'file2.1'
                    proguardFiles 'file2.2', 'file2.3'
                }

                f2  {

                }

                f3 {
                    proguardFile 'file4.1'
                    proguardFiles 'file4.2', 'file4.3'
                    proguardFiles = ['file4.1']
                }
            }
        }

        AppPlugin plugin = project.plugins.getPlugin(AppPlugin)
        plugin.createAndroidTasks(false)

        def variantsData = plugin.variantManager.variantDataList
        Map<String, GradleVariantConfiguration> variantMap =
                variantsData.collectEntries {[it.name, it.variantConfiguration]}

        def expectedFiles = [
                f1Release: ["file1.1", "file1.2", "file1.3", "file2.1", "file2.2", "file2.3"],
                f1Debug: ["file2.1", "file2.2", "file2.3"],
                f2Release: ["file1.1", "file1.2", "file1.3"],
                f2Debug: [],
                f2Custom: ["file3.1"],
                f3Custom: ["file3.1", "file4.1"],
        ]

        expectedFiles.each { name, expected ->
            def actual = variantMap[name].getProguardFiles(false, [])
            assert (actual*.name as Set) == (expected as Set), name
        }
    }

    public void testProguardDsl_initWith() throws Exception {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_PROJECTS}/basic")).build()

        project.apply plugin: 'com.android.atom'

        project.android {
            compileSdkVersion COMPILE_SDK_VERSION
            buildToolsVersion '20.0.0'
            defaultConfig {
                versionName '1.0'
            }

            buildTypes {
                common {
                    testProguardFile 'file1.1'
                }

                custom1.initWith(buildTypes.common)
                custom2.initWith(buildTypes.common)

                custom1 {
                    testProguardFile 'file2.1'
                }
            }
        }

        AtomPlugin plugin = project.plugins.getPlugin(AtomPlugin)
        plugin.createAndroidTasks(false)

        def variantsData = plugin.variantManager.variantDataList
        Map<String, GradleVariantConfiguration> variantMap =
                variantsData.collectEntries {[it.name, it.variantConfiguration]}

        def expectedFiles = [
                common: ["file1.1"],
                custom1: ["file1.1", "file2.1"],
                custom2: ["file1.1"],
        ]

        expectedFiles.each { name, expected ->
            Set<File> actual = variantMap[name].testProguardFiles
            assert (actual*.name as Set) == (expected as Set), name
        }
    }

    public void testSettingLanguageLevelFromCompileSdk() {
        def testLanguageLevel = { version, expectedLanguageLevel, useJack ->
            Project project = ProjectBuilder.builder().withProjectDir(
                    new File(testDir, "${FOLDER_TEST_PROJECTS}/basic")).build()

            project.apply plugin: 'com.android.atom'
            project.android {
                compileSdkVersion version
                buildToolsVersion '20.0.0'
                defaultConfig {
                    versionName '1.0'
                }
            }

            AtomPlugin plugin = project.plugins.getPlugin(AtomPlugin)
            plugin.createAndroidTasks(false)

            assertEquals(
                    "target compatibility for ${version}",
                    expectedLanguageLevel.toString(),
                    project.compileReleaseAtomJavaWithJavac.targetCompatibility)
            assertEquals(
                    "source compatibility for ${version}",
                    expectedLanguageLevel.toString(),
                    project.compileReleaseAtomJavaWithJavac.sourceCompatibility)
        }

        for (useJack in [true, false]) {
            def propName = 'java.specification.version'
            String originalVersion = System.getProperty(propName)
            try{
                System.setProperty(propName, '1.7')
                testLanguageLevel('android-21', JavaVersion.VERSION_1_7, useJack)
                testLanguageLevel('android-21', JavaVersion.VERSION_1_7, useJack)
                testLanguageLevel('Google Inc.:Google APIs:22', JavaVersion.VERSION_1_7, useJack)

                System.setProperty(propName, '1.6')
                testLanguageLevel(21, JavaVersion.VERSION_1_6, useJack)
                testLanguageLevel('android-21', JavaVersion.VERSION_1_6, useJack)
                testLanguageLevel('Google Inc.:Google APIs:22', JavaVersion.VERSION_1_6, useJack)
            } finally {
                System.setProperty(propName, originalVersion)
            }
        }
    }

    public void testSettingLanguageLevelFromCompileSdk_dontOverride() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_PROJECTS}/basic")).build()

        project.apply plugin: 'com.android.atom'
        project.android {
            compileSdkVersion COMPILE_SDK_VERSION
            buildToolsVersion '20.0.0'
            defaultConfig {
                versionName '1.0'
            }

            compileOptions {
                sourceCompatibility JavaVersion.VERSION_1_6
                targetCompatibility JavaVersion.VERSION_1_6
            }
        }
        AtomPlugin plugin = project.plugins.getPlugin(AtomPlugin)
        plugin.createAndroidTasks(false)

        assertEquals(
                JavaVersion.VERSION_1_6.toString(),
                project.compileReleaseAtomJavaWithJavac.targetCompatibility)
        assertEquals(
                JavaVersion.VERSION_1_6.toString(),
                project.compileReleaseAtomJavaWithJavac.sourceCompatibility)
    }


    private static void checkTestedVariant(@NonNull String variantName,
            @NonNull String testedVariantName,
            @NonNull Set<AtomVariant> variants,
            @NonNull Set<TestVariant> testVariants) {
        AtomVariant variant = findNamedItem(variants, variantName, "variantData")
        assertNotNull(variant)
        assertNotNull(variant.testVariant)
        assertEquals(testedVariantName, variant.testVariant.name)
        assertEquals(variant.testVariant, findNamedItemMaybe(testVariants, testedVariantName))
        checkTasks(variant)
        assertTrue(variant.testVariant instanceof TestVariant)
        checkTestTasks(variant.testVariant)
    }

    private static void checkNonTestedVariant(@NonNull String variantName,
            @NonNull Set<AtomVariant> variants) {
        AtomVariant variant = findNamedItem(variants, variantName, "variantData")
        assertNotNull(variant)
        assertNull(variant.testVariant)
        checkTasks(variant)
    }

    private static void checkTasks(@NonNull AtomVariant variant) {
        assertNotNull(variant.aidlCompile)
        assertNotNull(variant.mergeResources)
        assertNotNull(variant.mergeAssets)
        assertNotNull(variant.generateBuildConfig)
        assertNotNull(variant.javaCompiler)
        assertNotNull(variant.processJavaResources)
        assertNotNull(variant.assemble)

        assertFalse(variant.outputs.isEmpty())

        for (BaseVariantOutput baseVariantOutput : variant.outputs) {
            assertTrue(baseVariantOutput instanceof AtomVariantOutput)
            AtomVariantOutput atomVariantOutput = (AtomVariantOutput) baseVariantOutput

            assertNotNull(atomVariantOutput.processManifest)
            assertNotNull(atomVariantOutput.processResources)
            assertNotNull(atomVariantOutput.bundleAtom)
        }
    }

    private static void checkTestTasks(@NonNull TestVariant variant) {
        assertNotNull(variant.aidlCompile)
        assertNotNull(variant.mergeResources)
        assertNotNull(variant.mergeAssets)
        assertNotNull(variant.generateBuildConfig)
        assertNotNull(variant.javaCompiler)
        assertNotNull(variant.processJavaResources)
        assertNotNull(variant.assemble)
        assertNotNull(variant.connectedInstrumentTest)
        assertNotNull(variant.testedVariant)
        assertFalse(variant.outputs.isEmpty())

        for (BaseVariantOutput baseVariantOutput : variant.outputs) {
            assertNotNull(baseVariantOutput.processManifest)
            assertNotNull(baseVariantOutput.processResources)
        }
    }
}

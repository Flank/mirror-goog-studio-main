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
package com.android.tools.lint.checks.infrastructure

import com.android.tools.lint.LintCliFlags
import com.android.tools.lint.model.LintModelAndroidLibrary
import com.android.tools.lint.model.LintModelJavaLibrary
import com.android.tools.lint.model.LintModelModuleType
import com.android.tools.lint.model.LintModelVariant
import com.android.utils.ILogger
import com.google.common.truth.Truth
import org.intellij.lang.annotations.Language
import org.junit.Assert
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class GradleModelMockerTest {
    @get:Rule
    var tempFolder = TemporaryFolder()
    private fun createMocker(@Language("Groovy") gradle: String): GradleModelMocker {
        return createMocker(gradle, tempFolder)!!
    }

    @Test
    fun testLibraries() {
        val mocker = createMocker(
            """
apply plugin: 'com.android.application'

dependencies {
    compile 'my.group.id:mylib:25.0.0-SNAPSHOT'
}"""
        )
        val module = mocker.getLintModule()
        val variant = mocker.getLintVariant()!!

        Truth.assertThat(module.type).isEqualTo(LintModelModuleType.APP)
        val libraries = variant.mainArtifact.dependencies.compileDependencies.roots
        Truth.assertThat(libraries).hasSize(1)
        val library = libraries.first()
        Truth.assertThat(library.artifactAddress).isEqualTo("my.group.id:mylib:25.0.0-SNAPSHOT")
    }

    @Test
    fun testLibrariesInExtraArtifacts() {
        val mocker = createMocker(
            """
apply plugin: 'com.android.application'

dependencies {
    testCompile 'my.group.id:mylib1:1.2.3-rc4'
    androidTestImplementation 'my.group.id:mylib2:4.5.6-SNAPSHOT'
}"""
        )
        val module = mocker.getLintModule()
        val variant = mocker.getLintVariant()!!

        Truth.assertThat(module.type).isEqualTo(LintModelModuleType.APP)

        val testLibraries = variant.testArtifact!!.dependencies.compileDependencies.roots
        Truth.assertThat(testLibraries).hasSize(1)
        val testLibrary = testLibraries.first()
        Truth.assertThat(testLibrary.artifactAddress).isEqualTo("my.group.id:mylib1:1.2.3-rc4")

        val androidTestLibraries = variant.androidTestArtifact!!.dependencies.compileDependencies.roots
        Truth.assertThat(androidTestLibraries).hasSize(1)
        val library = androidTestLibraries.first()
        Truth.assertThat(library.artifactAddress).isEqualTo("my.group.id:mylib2:4.5.6-SNAPSHOT")
    }

    @Test
    fun testKotlin() {
        val mocker = createMocker(
            """
apply plugin: 'kotlin-android'

dependencies {
    implementation "org.jetbrains.kotlin:kotlin-stdlib-jdk7:${"$"}kotlin_version"
}"""
        )
        val module = mocker.getLintModule()
        val variant = mocker.getLintVariant()!!

        Truth.assertThat(module.type).isEqualTo(LintModelModuleType.APP)

        val javaLibraries = variant.mainArtifact.dependencies.compileDependencies
            .getAllLibraries()
            .filterIsInstance<LintModelJavaLibrary>()
            .map { it.artifactAddress }
        Truth.assertThat(javaLibraries)
            .containsAtLeast(
                "org.jetbrains.kotlin:kotlin-stdlib-jdk7:\$kotlin_version",
                "org.jetbrains.kotlin:kotlin-stdlib:\$kotlin_version",
                "org.jetbrains.kotlin:kotlin-stdlib-common:\$kotlin_version"
            )
    }

    @Test
    fun testKotlinWithInterpolation() {
        val mocker = createMocker(
            """
apply plugin: 'kotlin-android'

ext {
    kotlin_version = "1.3.21"
}

dependencies {
    implementation "org.jetbrains.kotlin:kotlin-stdlib-jdk7:${"$"}kotlin_version"
}"""
        )
        val module = mocker.getLintModule()
        val variant = mocker.getLintVariant()!!

        Truth.assertThat(module.type).isEqualTo(LintModelModuleType.APP)

        val libraries = variant.mainArtifact.dependencies.compileDependencies
            .getAllLibraries()
            .filterIsInstance<LintModelJavaLibrary>()
            .map { it.artifactAddress }
        Truth.assertThat(libraries).hasSize(4)
        Truth.assertThat(libraries)
            .containsExactly(
                "org.jetbrains.kotlin:kotlin-stdlib-common:1.3.21",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.21",
                "org.jetbrains.kotlin:kotlin-stdlib:1.3.21",
                "org.jetbrains:annotations:13.0"
            )
    }

    @Test
    fun testMinSdkVersion() {
        val mocker = createMocker(
            """android {
    compileSdkVersion 25
    buildToolsVersion "25.0.0"
    defaultConfig {
        applicationId "com.android.tools.test"
        minSdkVersion 5
        targetSdkVersion 16
        versionCode 2
        versionName "MyName"
    }
}"""
        )
        val module = mocker.getLintModule()
        val variant = mocker.getLintVariant()!!

        Truth.assertThat(module.type).isEqualTo(LintModelModuleType.APP)

        Truth.assertThat(module.compileTarget).isEqualTo("android-25")
//        Truth.assertThat(module.buildToolsVersion).isEqualTo("25.0.0")
        Truth.assertThat(variant.minSdkVersion).isNotNull()
        Truth.assertThat(variant.minSdkVersion!!.apiLevel).isEqualTo(5)
        Truth.assertThat(variant.targetSdkVersion!!.apiLevel).isEqualTo(16)
//        Truth.assertThat(variant.versionCode).isEqualTo(2)
//        Truth.assertThat(variant.versionName).isEqualTo("MyName")
        Truth.assertThat(variant.mainArtifact.applicationId).isEqualTo("com.android.tools.test")
    }

    @Test
    fun testFlavors() {
        val mocker = createMocker(
            """
apply plugin: 'com.android.application'

android {
    defaultConfig {
        resConfigs "mdpi"
    }
    flavorDimensions  "pricing", "releaseType"
    productFlavors {
        beta {
            dimension "releaseType"
            resConfig "en"
            resConfigs "nodpi", "hdpi"
            versionNameSuffix "-beta"
            applicationIdSuffix '.beta'
        }
        normal { dimension "releaseType" }
        free { dimension "pricing" }
        paid { dimension "pricing" }
    }
}"""
        )
        val module = mocker.getLintModule()
        Truth.assertThat(module.type).isEqualTo(LintModelModuleType.APP)

        Truth.assertThat(module.variants.map { it.name })
            .containsExactly(
                "freeBetaDebug", "paidBetaDebug", "freeNormalDebug", "paidNormalDebug",
                "freeBetaRelease", "paidBetaRelease", "freeNormalRelease", "paidNormalRelease"
            )

        Truth.assertThat(module.findVariant("freeBetaDebug")!!.debuggable).isTrue()
        Truth.assertThat(module.findVariant("freeBetaRelease")!!.debuggable).isFalse()

        // ResConfigs
        Truth.assertThat(module.findVariant("freeNormalDebug")!!.resourceConfigurations).containsExactly("mdpi")
        Truth.assertThat(module.findVariant("paidNormalRelease")!!.resourceConfigurations).containsExactly("mdpi")
        Truth.assertThat(module.findVariant("freeBetaDebug")!!.resourceConfigurations)
            .containsExactly("mdpi", "en", "nodpi", "hdpi")
        Truth.assertThat(module.findVariant("paidBetaRelease")!!.resourceConfigurations)
            .containsExactly("mdpi", "en", "nodpi", "hdpi")

        // Suffix handling
        Truth.assertThat(module.findVariant("freeBetaDebug")!!.mainArtifact.applicationId).isEqualTo("test.pkg")
        Truth.assertThat(module.findVariant("paidBetaRelease")!!.`package`).isNull()
    }

    @Test
    fun testSourceSets() {
        val mocker = createMocker(
            """android {
    compileSdkVersion 25
    buildToolsVersion "25.0.0"
    defaultConfig {
        applicationId "com.android.tools.test"
        minSdkVersion 5
        targetSdkVersion 16
        versionCode 2
        versionName "MyName"
    }
}"""
        )
        val module = mocker.getLintModule()
        val variant = mocker.getLintVariant()!!

        Truth.assertThat(module.type).isEqualTo(LintModelModuleType.APP)

        val mainSourceProvider = variant.sourceProviders.first()
        val manifestFile = mainSourceProvider.manifestFile
        Truth.assertThat(manifestFile.path).endsWith("AndroidManifest.xml")
        Truth.assertThat(manifestFile.path.replace(File.separatorChar, '/'))
            .endsWith("src/main/AndroidManifest.xml")
        Truth.assertThat(mainSourceProvider.javaDirectories).isNotEmpty()
    }

    @Test
    fun testProvidedScopes() {
        val mocker = createMocker(
            """
apply plugin: 'android-library'

dependencies {
    provided "com.google.android.wearable:wearable:2.0.0-alpha4"
}
"""
        )
        val module = mocker.getLintModule()
        val variant = mocker.getLintVariant()!!

        Truth.assertThat(module.type).isEqualTo(LintModelModuleType.LIBRARY)

        val libraries = variant.mainArtifact.dependencies.compileDependencies.roots.map { it.artifactAddress }.toSet() -
            variant.mainArtifact.dependencies.packageDependencies.roots.map { it.artifactAddress }.toSet()

        Truth.assertThat(libraries)
            .containsExactly("com.google.android.wearable:wearable:2.0.0-alpha4")
    }

    @Test
    fun testDependencyPropertyForm() {
        val mocker = createMocker(
            """
apply plugin: 'android'

dependencies {
    implementation group: 'com.android.support',
            name: "support-v4", version: '19.0'
}
"""
        )
        val module = mocker.getLintModule()
        val variant = mocker.getLintVariant()!!

        Truth.assertThat(module.type).isEqualTo(LintModelModuleType.APP)

        val libraries = variant.mainArtifact.dependencies.compileDependencies
            .getAllLibraries()
            .filterIsInstance<LintModelJavaLibrary>()
            .map { it.artifactAddress }

        Truth.assertThat(libraries)
            .containsExactly(
                "com.android.support:support-v4:19.0",
                "com.android.support:support-annotations:19.0"
            )
    }

    @Test
    fun testModelVersion() {
        val mocker = createMocker(
            """buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:1.5.1'

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}"""
        )
        val module = mocker.getLintModule()

        Truth.assertThat(module.gradleVersion.toString()).isEqualTo("1.5.1")
    }

    @Test
    fun testVectors() {
        val mocker = createMocker(
            """android.defaultConfig.vectorDrawables {
    useSupportLibrary = true
}"""
        )
        val variant = mocker.getLintVariant()!!

        Truth.assertThat(variant.useSupportLibraryVectorDrawables).isTrue()
    }

    @Test
    fun testResValues() {
        val mocker = createMocker(
            """android {
    defaultConfig {
        resValue "string", "defaultConfigName", "Some DefaultConfig Data"
    }
    buildTypes {
        debug {
            resValue "string", "debugName", "Some Debug Data"
        }
        release {
            resValue "string", "releaseName1", "Some Release Data 1"
            resValue "string", "releaseName2", "Some Release Data 2"
        }
    }
    productFlavors {
         flavor1 {
             resValue "string", "VALUE_DEBUG",   "10"
             resValue "string", "VALUE_FLAVOR",  "10"
             resValue "string", "VALUE_VARIANT", "10"
         }
         flavor2 {
             resValue "string", "VALUE_DEBUG",   "20"
             resValue "string", "VALUE_FLAVOR",  "20"
             resValue "string", "VALUE_VARIANT", "20"
         }
     }
}"""
        )

        val module = mocker.getLintModule()

        fun LintModelVariant.testValue() = this.resValues.values.joinToString("\n") { "${it.name}/${it.type}/${it.value}" }

        Truth.assertThat(module.findVariant("flavor1Debug")!!.testValue())
            .isEqualTo(
                """
    defaultConfigName/string/Some DefaultConfig Data
    VALUE_DEBUG/string/10
    VALUE_FLAVOR/string/10
    VALUE_VARIANT/string/10
    debugName/string/Some Debug Data
                """.trimIndent()
            )
        Truth.assertThat(module.findVariant("flavor2Release")!!.testValue())
            .isEqualTo(
                """
    defaultConfigName/string/Some DefaultConfig Data
    VALUE_DEBUG/string/20
    VALUE_FLAVOR/string/20
    VALUE_VARIANT/string/20
    releaseName1/string/Some Release Data 1
    releaseName2/string/Some Release Data 2
                """.trimIndent()
            )
    }

    @Test
    fun testSetVariantName() {
        val mocker = createMocker(
            """android {
    defaultConfig {
        resValue "string", "defaultConfigName", "Some DefaultConfig Data"
    }
    buildTypes {
        debug {
            resValue "string", "debugName", "Some Debug Data"
        }
        release {
            resValue "string", "releaseName1", "Some Release Data 1"
            resValue "string", "releaseName2", "Some Release Data 2"
        }
    }
    productFlavors {
         flavor1 {
             resValue "string", "VALUE_DEBUG",   "10"
             resValue "string", "VALUE_FLAVOR",  "10"
             resValue "string", "VALUE_VARIANT", "10"
         }
         flavor2 {
             resValue "string", "VALUE_DEBUG",   "20"
             resValue "string", "VALUE_FLAVOR",  "20"
             resValue "string", "VALUE_VARIANT", "20"
         }
     }
}"""
        )
        Truth.assertThat(mocker.getLintVariant()!!.name).isEqualTo("flavor1Debug")
        mocker.setVariantName("flavor2Release")
        Truth.assertThat(mocker.getLintVariant()!!.name).isEqualTo("flavor2Release")
    }

    @Test
    fun testPlaceHolders() {
        val mocker = createMocker(
            """android {
    defaultConfig {
        manifestPlaceholders = [ localApplicationId:"com.example.manifest_merger_example"]
    }
    productFlavors {
        flavor {
            manifestPlaceholders = [ localApplicationId:"com.example.manifest_merger_example.flavor"]
        }
    }
    productFlavors {
        free {
            manifestPlaceholders = ["holder":"free"]
        }
        beta {
            manifestPlaceholders = ["holder":"beta"]
        }
    }
}"""
        )
        val module = mocker.getLintModule()

        fun LintModelVariant.testValue() = this.manifestPlaceholders.entries.joinToString("\n") { "${it.key}/${it.value}" }

        Truth.assertThat(module.findVariant("flavorDebug")!!.testValue())
            .isEqualTo(
                """
    localApplicationId/com.example.manifest_merger_example.flavor
                """.trimIndent()
            )

        Truth.assertThat(module.findVariant("freeRelease")!!.testValue())
            .isEqualTo(
                """
    localApplicationId/com.example.manifest_merger_example
    holder/free
                """.trimIndent()
            )

        Truth.assertThat(module.findVariant("betaDebug")!!.testValue())
            .isEqualTo(
                """
    localApplicationId/com.example.manifest_merger_example
    holder/beta
                """.trimIndent()
            )
    }

    @Test
    fun testMinifyEnabled() {
        val mocker = createMocker(
            """android {
    buildTypes {
        release {
            minifyEnabled true
        }
    }
}"""
        )
        val module = mocker.getLintModule()
        Truth.assertThat(module.findVariant("release")!!.shrinkable).isTrue()
        Truth.assertThat(module.findVariant("debug")!!.shrinkable).isFalse()
    }

    @Test(expected = AssertionError::class)
    fun testFailOnUnexpected() {
        val mocker = createMocker("android {\n    minSdkVersion 15\n}")
        mocker.getLintModule()
    }

    @Test
    fun testWarnOnUnrecognized() {
        val hasWarning = AtomicBoolean()
        val hasError = AtomicBoolean()
        val mocker = createMocker("apply plugin: 'java'\nfoo.bar\n")
            .withLogger(
                object : ILogger {
                    override fun error(
                        t: Throwable?,
                        msgFormat: String?,
                        vararg args: Any
                    ) {
                        hasError.set(true)
                    }

                    override fun warning(msgFormat: String, vararg args: Any) {
                        hasWarning.set(true)
                    }

                    override fun info(msgFormat: String, vararg args: Any) {}
                    override fun verbose(
                        msgFormat: String,
                        vararg args: Any
                    ) {
                    }
                })
            .withModelVersion("1.5.0")
            .allowUnrecognizedConstructs()
        Truth.assertThat(mocker.getLintModule().gradleVersion.toString()).isEqualTo("1.5.0")
        Truth.assertThat(hasWarning.get()).isTrue()
        Truth.assertThat(hasError.get()).isFalse()
    }

    @Test
    fun testVersionProperties() {
        val mocker = createMocker(
            """android {
    defaultConfig {
        applicationId "com.example.manifest_merger_example"
        minSdkVersion 15
        targetSdkVersion 21
        versionCode 1
        versionName "1.0"
    }
    productFlavors {
        flavor {
            applicationId "com.example.manifest_merger_example.flavor"
            minSdkVersion 16
            targetSdkVersion 22
            versionCode 2
            versionName "2.0"
        }
    }
}"""
        )
        mocker.setVariantName("flavorDebug")
        val variant = mocker.getLintVariant()!!

        Truth.assertThat(variant.mainArtifact.applicationId)
            .isEqualTo("com.example.manifest_merger_example.flavor")
        Truth.assertThat(variant.minSdkVersion!!.apiLevel).isEqualTo(16)
        Truth.assertThat(variant.targetSdkVersion!!.apiLevel).isEqualTo(22)
//        Truth.assertThat(flavor.versionCode).isEqualTo(2)
//        Truth.assertThat(flavor.versionName).isEqualTo("2.0")
    }

    @Test
    fun testApkSplits() {
        val mocker = createMocker(
            """android {
    splits {
        density {
            enable true
            reset()
            include "mdpi", "hdpi"
        }
        language {
            enable true
            include "fr", "fr-rCA", "en"
        }
        abi {
            enable = true
            include "x86_64", "mips64", "arm64-v8a", "armeabi"
        }
    }
}
"""
        )
        val module = mocker.getLintModule()
        val variant = mocker.getLintVariant()!!
// TODO:
//        val mainArtifact = variant.mainArtifact
//        val outputs: List<IdeAndroidArtifactOutput?> = mainArtifact.outputs
//        Truth.assertThat(outputs).hasSize(10)
//        val generatedSplits: List<Pair<String, String>?> = outputs.stream()
//            .filter { artifact: IdeAndroidArtifactOutput? -> !artifact!!.filters.isEmpty() }
//            .map { artifact: IdeAndroidArtifactOutput? -> artifact!!.filters.iterator().next()!! }
//            .map(
//                { filterData: FilterData ->
//                    Pair.of(
//                        filterData.filterType,
//                        filterData.identifier
//                    )
//                })
//            .collect(Collectors.toList())
//        Truth.assertThat(generatedSplits)
//            .containsExactly(
//                Pair.of("DENSITY", "mdpi"),
//                Pair.of("DENSITY", "hdpi"),
//                Pair.of("LANGUAGE", "fr"),
//                Pair.of("LANGUAGE", "fr-rCA"),
//                Pair.of("LANGUAGE", "en"),
//                Pair.of("ABI", "x86_64"),
//                Pair.of("ABI", "mips64"),
//                Pair.of("ABI", "arm64-v8a"),
//                Pair.of("ABI", "armeabi")
//            )
    }

    // @Test
    // TODO(b/158654131): Re-implement when possible.
    @Ignore
    fun testNestedDependencies() {
        // Nested dependencies are not supported in the IDE right now.
        /*
        GradleModelMocker mocker =
                createMocker(
                                ""
                                        + "apply plugin: 'com.android.application'\n"
                                        + "\n"
                                        + "dependencies {\n"
                                        + "    compile \"com.android.support:appcompat-v7:25.0.1\"\n"
                                        + "    compile \"com.android.support.constraint:constraint-layout:1.0.0-beta3\"\n"
                                        + "}")
                        .withDependencyGraph(
                                ""
                                        + "+--- com.android.support:appcompat-v7:25.0.1\n"
                                        + "|    +--- com.android.support:support-v4:25.0.1\n"
                                        + "|    |    +--- com.android.support:support-compat:25.0.1\n"
                                        + "|    |    |    \\--- com.android.support:support-annotations:25.0.1\n"
                                        + "|    |    +--- com.android.support:support-media-compat:25.0.1\n"
                                        + "|    |    |    \\--- com.android.support:support-compat:25.0.1 (*)\n"
                                        + "|    |    +--- com.android.support:support-core-utils:25.0.1\n"
                                        + "|    |    |    \\--- com.android.support:support-compat:25.0.1 (*)\n"
                                        + "|    |    +--- com.android.support:support-core-ui:25.0.1\n"
                                        + "|    |    |    \\--- com.android.support:support-compat:25.0.1 (*)\n"
                                        + "|    |    \\--- com.android.support:support-fragment:25.0.1\n"
                                        + "|    |         +--- com.android.support:support-compat:25.0.1 (*)\n"
                                        + "|    |         +--- com.android.support:support-media-compat:25.0.1 (*)\n"
                                        + "|    |         +--- com.android.support:support-core-ui:25.0.1 (*)\n"
                                        + "|    |         \\--- com.android.support:support-core-utils:25.0.1 (*)\n"
                                        + "|    +--- com.android.support:support-vector-drawable:25.0.1\n"
                                        + "|    |    \\--- com.android.support:support-compat:25.0.1 (*)\n"
                                        + "|    \\--- com.android.support:animated-vector-drawable:25.0.1\n"
                                        + "|         \\--- com.android.support:support-vector-drawable:25.0.1 (*)\n"
                                        + "+--- com.android.support.constraint:constraint-layout:1.0.0-beta3\n"
                                        + "|    \\--- com.android.support.constraint:constraint-layout-solver:1.0.0-beta3\n");
        Dependencies dependencies = mocker.getVariant().getMainArtifact().getDependencies();

        List<JavaLibrary> javaLibraries;
        List<AndroidLibrary> androidLibraries;
        javaLibraries = Lists.newArrayList(dependencies.getJavaLibraries());
        androidLibraries = Lists.newArrayList(dependencies.getLibraries());

        assertThat(javaLibraries).hasSize(0);
        assertThat(androidLibraries).hasSize(2);

        AndroidLibrary library;
        MavenCoordinates coordinates;

        library = androidLibraries.get(0);
        coordinates = library.getResolvedCoordinates();
        assertThat(coordinates.getGroupId()).isEqualTo("com.android.support");
        assertThat(coordinates.getArtifactId()).isEqualTo("appcompat-v7");
        assertThat(coordinates.getVersion()).isEqualTo("25.0.1");
        assertThat(coordinates.getPackaging()).isEqualTo("aar");

        library = androidLibraries.get(1);
        coordinates = library.getResolvedCoordinates();
        assertThat(coordinates.getGroupId()).isEqualTo("com.android.support.constraint");
        assertThat(coordinates.getArtifactId()).isEqualTo("constraint-layout");
        assertThat(coordinates.getVersion()).isEqualTo("1.0.0-beta3");
        assertThat(coordinates.getPackaging()).isEqualTo("aar");

        // Check recursive dependencies
        library = androidLibraries.get(0);
        androidLibraries = Lists.newArrayList(library.getLibraryDependencies());
        androidLibraries.sort(LIBRARY_COMPARATOR);
        library = androidLibraries.get(1);
        coordinates = library.getResolvedCoordinates();
        assertThat(coordinates.getGroupId()).isEqualTo("com.android.support");
        assertThat(coordinates.getArtifactId()).isEqualTo("support-v4");
        assertThat(coordinates.getVersion()).isEqualTo("25.0.1");
        assertThat(coordinates.getPackaging()).isEqualTo("aar");

        androidLibraries = Lists.newArrayList(library.getLibraryDependencies());
        androidLibraries.sort(LIBRARY_COMPARATOR);
        library = androidLibraries.get(0);
        coordinates = library.getResolvedCoordinates();
        assertThat(coordinates.getGroupId()).isEqualTo("com.android.support");
        assertThat(coordinates.getArtifactId()).isEqualTo("support-compat");
        assertThat(coordinates.getVersion()).isEqualTo("25.0.1");
        assertThat(coordinates.getPackaging()).isEqualTo("aar");

        //JavaLibrary javaLibrary = library.getJavaDependencies().iterator().next();
        //coordinates = javaLibrary.getResolvedCoordinates();
        //assertThat(coordinates.getGroupId()).isEqualTo("com.android.support");
        //assertThat(coordinates.getArtifactId()).isEqualTo("support-annotations");
        //assertThat(coordinates.getVersion()).isEqualTo("25.0.1");
        //assertThat(coordinates.getPackaging()).isEqualTo("jar");
        */
    }

    @Test
    fun testDependencyGraph() {
        val mocker = createMocker(
            """
apply plugin: 'com.android.application'

dependencies {
    compile "com.android.support:appcompat-v7:25.0.1"
    compile "com.android.support.constraint:constraint-layout:1.0.0-beta3"
}"""
        )

        val module = mocker.getLintModule()
        var variant = mocker.getLintVariant()!!

        val javaLibraries =
            variant.mainArtifact.dependencies.compileDependencies
                .getAllLibraries().filterIsInstance<LintModelJavaLibrary>()
                .map { it.artifactAddress }
        val androidLibraries =
            variant.mainArtifact.dependencies.compileDependencies
                .getAllLibraries().filterIsInstance<LintModelAndroidLibrary>()
                .map { it.artifactAddress }

        Truth.assertThat(javaLibraries).containsExactly(
            "com.android.support:support-annotations:25.0.1",
            "com.android.support.constraint:constraint-layout-solver:1.0.0-beta3"
        )
        Truth.assertThat(androidLibraries).containsExactly(
            "com.android.support:appcompat-v7:25.0.1",
            "com.android.support:support-v4:25.0.1",
            "com.android.support:support-compat:25.0.1",
            "com.android.support:support-media-compat:25.0.1",
            "com.android.support:support-core-utils:25.0.1",
            "com.android.support:support-core-ui:25.0.1",
            "com.android.support:support-fragment:25.0.1",
            "com.android.support:support-vector-drawable:25.0.1",
            "com.android.support:animated-vector-drawable:25.0.1",
            "com.android.support.constraint:constraint-layout:1.0.0-beta3",
        )
    }

    // @Test
    // TODO(b/158649269): Review whether this test is needed.
//    @Ignore
//    fun testPromotedDependencies() {
//        val mocker = createMocker(
//            """dependencies {
//    compile 'junit:junit:4.12'
//    compile 'org.hamcrest:hamcrest-core:1.3'
//    compile 'org.mockito:mockito-core:1.10.8'
//    compile 'org.powermock:powermock-api-mockito:1.6.4'
//    compile 'org.powermock:powermock-module-junit4-rule-agent:1.6.4'
//    compile 'org.powermock:powermock-module-junit4-rule:1.6.4'
//    compile 'org.powermock:powermock-module-junit4:1.6.4'
//    compile 'org.json:json:20090211'}
// """
//        )
//            .withDependencyGraph(
//                """
//                +--- junit:junit:4.12
//                |    \--- org.hamcrest:hamcrest-core:1.3
//                +--- org.hamcrest:hamcrest-core:1.3
//                +--- org.mockito:mockito-core:1.10.8 -> 1.10.19
//                |    +--- org.hamcrest:hamcrest-core:1.1 -> 1.3
//                |    \--- org.objenesis:objenesis:2.1
//                +--- org.powermock:powermock-api-mockito:1.6.4
//                |    +--- org.mockito:mockito-core:1.10.19 (*)
//                |    +--- org.hamcrest:hamcrest-core:1.3
//                |    \--- org.powermock:powermock-api-support:1.6.4
//                |         +--- org.powermock:powermock-core:1.6.4
//                |         |    +--- org.powermock:powermock-reflect:1.6.4
//                |         |    |    \--- org.objenesis:objenesis:2.1
//                |         |    \--- org.javassist:javassist:3.20.0-GA
//                |         \--- org.powermock:powermock-reflect:1.6.4 (*)
//                +--- org.powermock:powermock-module-junit4-rule-agent:1.6.4
//                |    +--- org.powermock:powermock-module-javaagent:1.6.4
//                |    |    \--- org.powermock:powermock-core:1.6.4 (*)
//                |    \--- org.powermock:powermock-core:1.6.4 (*)
//                +--- org.powermock:powermock-module-junit4-rule:1.6.4
//                |    +--- org.powermock:powermock-classloading-base:1.6.4
//                |    |    +--- org.powermock:powermock-api-support:1.6.4 (*)
//                |    |    \--- org.powermock:powermock-reflect:1.6.4 (*)
//                |    \--- org.powermock:powermock-core:1.6.4 (*)
//                +--- org.powermock:powermock-module-junit4:1.6.4
//                |    +--- junit:junit:4.12 (*)
//                |    \--- org.powermock:powermock-module-junit4-common:1.6.4
//                |         +--- junit:junit:4.4 -> 4.12 (*)
//                |         +--- org.powermock:powermock-core:1.6.4 (*)
//                |         \--- org.powermock:powermock-reflect:1.6.4 (*)
//                \--- org.json:json:20090211
//                """.trimIndent()
//            )
//        val javaLibraries: List<IdeLibrary?>
//        val androidLibraries: List<IdeLibrary?>
//        val dependencies = mocker.variant.mainArtifact.level2Dependencies
//        javaLibraries = Lists.newArrayList<IdeLibrary?>(dependencies.javaLibraries)
//        androidLibraries = Lists.newArrayList<IdeLibrary?>(dependencies.androidLibraries)
//        Truth.assertThat(javaLibraries).hasSize(1)
//        Truth.assertThat(androidLibraries).hasSize(7)
//
//        // org.mockito:mockito-core:1.10.8 -> 1.10.19
//        val library = androidLibraries[1]
//        Truth.assertThat(library!!.artifactAddress).isEqualTo("org.mockito:mockito-core:1.10.19")
//        Assert.fail("IdeDependencies do not support the concept of promoted versions")
//    }

//    @Test
//    fun testDependencyGraphs() {
//        val mocker = createMocker(
//            """buildscript {
//    dependencies {
//        classpath 'com.android.tools.build:gradle:2.5.0-alpha1'
//    }
// }
// dependencies {
//    compile 'junit:junit:4.12'
//    compile 'org.hamcrest:hamcrest-core:1.3'
//    compile 'org.mockito:mockito-core:1.10.8'
//    compile 'org.powermock:powermock-api-mockito:1.6.4'
//    compile 'org.powermock:powermock-module-junit4-rule-agent:1.6.4'
//    compile 'org.powermock:powermock-module-junit4-rule:1.6.4'
//    compile 'org.powermock:powermock-module-junit4:1.6.4'
//    compile 'org.json:json:20090211'}
// """
//        )
//            .withDependencyGraph(
//                """
//                +--- junit:junit:4.12
//                |    \--- org.hamcrest:hamcrest-core:1.3
//                +--- org.hamcrest:hamcrest-core:1.3
//                +--- org.mockito:mockito-core:1.10.8 -> 1.10.19
//                |    +--- org.hamcrest:hamcrest-core:1.1 -> 1.3
//                |    \--- org.objenesis:objenesis:2.1
//                +--- org.powermock:powermock-api-mockito:1.6.4
//                |    +--- org.mockito:mockito-core:1.10.19 (*)
//                |    +--- org.hamcrest:hamcrest-core:1.3
//                |    \--- org.powermock:powermock-api-support:1.6.4
//                |         +--- org.powermock:powermock-core:1.6.4
//                |         |    +--- org.powermock:powermock-reflect:1.6.4
//                |         |    |    \--- org.objenesis:objenesis:2.1
//                |         |    \--- org.javassist:javassist:3.20.0-GA
//                |         \--- org.powermock:powermock-reflect:1.6.4 (*)
//                +--- org.powermock:powermock-module-junit4-rule-agent:1.6.4
//                |    +--- org.powermock:powermock-module-javaagent:1.6.4
//                |    |    \--- org.powermock:powermock-core:1.6.4 (*)
//                |    \--- org.powermock:powermock-core:1.6.4 (*)
//                +--- org.powermock:powermock-module-junit4-rule:1.6.4
//                |    +--- org.powermock:powermock-classloading-base:1.6.4
//                |    |    +--- org.powermock:powermock-api-support:1.6.4 (*)
//                |    |    \--- org.powermock:powermock-reflect:1.6.4 (*)
//                |    \--- org.powermock:powermock-core:1.6.4 (*)
//                +--- org.powermock:powermock-module-junit4:1.6.4
//                |    +--- junit:junit:4.12 (*)
//                |    \--- org.powermock:powermock-module-junit4-common:1.6.4
//                |         +--- junit:junit:4.4 -> 4.12 (*)
//                |         +--- org.powermock:powermock-core:1.6.4 (*)
//                |         \--- org.powermock:powermock-reflect:1.6.4 (*)
//                \--- org.json:json:20090211
//                """.trimIndent()
//            )
//            .withFullDependencies(false)
//        val graph = mocker.variant.mainArtifact.level2Dependencies
//        val names = Streams.concat(
//            graph.androidLibraries.stream(),
//            graph.javaLibraries.stream()
//        )
//            .map { it: IdeLibrary -> it.artifactAddress }
//            .distinct()
//            .collect(Collectors.toList())
//        Collections.sort(names)
//        Truth.assertThat(names)
//            .containsExactly(
//                "junit:junit:4.12@jar",
//                "org.hamcrest:hamcrest-core:1.3@aar",
//                "org.javassist:javassist:3.20.0-GA@aar",
//                "org.json:json:20090211@aar",
//                "org.mockito:mockito-core:1.10.19@aar",
//                "org.objenesis:objenesis:2.1@aar",
//                "org.powermock:powermock-api-mockito:1.6.4@aar",
//                "org.powermock:powermock-api-support:1.6.4@aar",
//                "org.powermock:powermock-classloading-base:1.6.4@aar",
//                "org.powermock:powermock-core:1.6.4@aar",
//                "org.powermock:powermock-module-javaagent:1.6.4@aar",
//                "org.powermock:powermock-module-junit4-common:1.6.4@aar",
//                "org.powermock:powermock-module-junit4-rule-agent:1.6.4@aar",
//                "org.powermock:powermock-module-junit4-rule:1.6.4@aar",
//                "org.powermock:powermock-module-junit4:1.6.4@aar",
//                "org.powermock:powermock-reflect:1.6.4@aar"
//            )
//    }

    @Test
    fun testLintOptions() {
        val mocker = createMocker(
            """android {
    lintOptions {
        quiet true
        abortOnError false
        ignoreWarnings true
        absolutePaths false
        checkAllWarnings true
        warningsAsErrors true
        disable 'TypographyFractions','TypographyQuotes'
        enable 'RtlHardcoded','RtlCompat', 'RtlEnabled'
        check 'NewApi', 'InlinedApi'
        noLines true
        showAll true
        lintConfig file("default-lint.xml")
        informational 'LogConditional'
        checkTestSources true
    }
}
"""
        )
        Truth.assertThat(mocker).isNotNull()
        val flags = LintCliFlags()
        mocker.syncFlagsTo(flags)
        Truth.assertThat(flags.isQuiet).isTrue()
        Truth.assertThat(flags.isSetExitCode).isFalse()
        Truth.assertThat(flags.isIgnoreWarnings).isTrue()
        Truth.assertThat(flags.isCheckAllWarnings).isTrue()
        Truth.assertThat(flags.isWarningsAsErrors).isTrue()
        Truth.assertThat(flags.isFullPath).isFalse()
        Truth.assertThat(flags.suppressedIds)
            .containsExactly("TypographyFractions", "TypographyQuotes")
        Truth.assertThat(flags.enabledIds)
            .containsExactly("RtlHardcoded", "RtlCompat", "RtlEnabled")
        Truth.assertThat(flags.exactCheckedIds).containsExactly("NewApi", "InlinedApi")
    }

    @Test
    fun testLanguageOptions1() {
        val mocker = createMocker(
            """android {
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
}"""
        )

        val module = mocker.getLintModule()

        Truth.assertThat(module.javaSourceLevel).isEqualTo("1.8")
        // TODO: Truth.assertThat(module.compileTarget).isEqualTo("1.8")
    }

    @Test
    fun testLanguageOptions2() {
        val mocker = createMocker(
            """android {
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_7
        targetCompatibility JavaVersion.VERSION_1_7
    }
}"""
        )
        val module = mocker.getLintModule()

        Truth.assertThat(module.javaSourceLevel).isEqualTo("1.7")
        // TODO: Truth.assertThat(module.compileTarget).isEqualTo("1.8")
    }

    companion object {
        fun createMocker(
            @Language("Groovy") gradle: String?,
            tempFolder: TemporaryFolder
        ): GradleModelMocker {
            return try {
                GradleModelMocker(gradle!!, tempFolder.newFolder("build"))
                    .withLogger(
                        object : ILogger {
                            override fun error(
                                t: Throwable?,
                                msgFormat: String?,
                                vararg args: Any
                            ) {
                                Assert.fail(msgFormat)
                            }

                            override fun warning(msgFormat: String, vararg args: Any) {
                                println(msgFormat)
                            }

                            override fun info(msgFormat: String, vararg args: Any) {}
                            override fun verbose(msgFormat: String, vararg args: Any) {}
                        })
            } catch (e: IOException) {
                Assert.fail(e.message)
                error("")
            }
        }
    }
}

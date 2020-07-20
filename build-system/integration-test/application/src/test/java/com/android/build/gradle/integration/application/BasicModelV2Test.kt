/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.model.dump
import com.android.builder.model.v2.ide.SyncIssue
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class BasicModelV2Test {
    @get:Rule
    val project = builder()
        .fromTestProject("basic")
        // http://b/149978740 and http://b/146208910
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
        .create()

    @Test
    fun testModel() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchAndroidProjects()

        Truth.assertWithMessage("Dumped AndroidProject (full version in stdout)")
            .that(result.container.singleAndroidProject.dump(result.normalizer))
            .isEqualTo("""
> AndroidProject
    - apiVersion        = 3
    - projectType       = "APPLICATION"
    - path              = ":"
    - groupId           = ""
    - defaultVariant    = "debug"
    - flavorDimensions  = []
    - compileTarget     = "29"
    - buildFolder       = [PROJECT]/build
    - resourcePrefix    = (null)
    - buildToolsVersion = "29.0.2"
    - dynamicFeatures   = []
    > bootClasspath:
       * [SDK]/platforms/android-29/android.jar
    < bootClasspath
    > DefaultConfigContainer:
       > Flavor:
          - name                               = "main"
          - applicationIdSuffix                = (null)
          - versionNameSuffix                  = (null)
          > buildConfigFields:
             * field:
                * name          = "DEFAULT"
                * type          = "boolean"
                * value         = "true"
                * documentation = ""
                * annotations   = []
             * field:
                * name          = "FOO"
                * type          = "String"
                * value         = ""foo2""
                * documentation = ""
                * annotations   = []
          < buildConfigFields
          > resValues:
             * value:
                * name          = "foo"
                * value         = "foo"
                * documentation = ""
                * annotations   = []
          < resValues
          - proguardFiles                      = []
          - consumerProguardFiles              = []
          - testProguardFiles                  = []
          > manifestPlaceholders:
             * someKey -> 12
          < manifestPlaceholders
          - multiDexEnabled                    = (null)
          - multiDexKeepFile                   = (null)
          - multiDexKeepProguard               = (null)
          - dimension                          = (null)
          - versionCode                        = 12
          - versionName                        = "2.0"
          - minSdkVersion:
             * apiLevel = 16
             * codename = (null)
          - targetSdkVersion:
             * apiLevel = 16
             * codename = (null)
          - maxSdkVersion                      = (null)
          - renderscriptTargetApi              = (null)
          - renderscriptSupportModeEnabled     = (null)
          - renderscriptSupportModeBlasEnabled = (null)
          - renderscriptNdkModeEnabled         = (null)
          - testApplicationId                  = (null)
          - testInstrumentationRunner          = "android.support.test.runner.AndroidJUnitRunner"
          > testInstrumentationRunnerArguments:
             * size -> "medium"
          < testInstrumentationRunnerArguments
          - testHandleProfiling                = false
          - testFunctionalTest                 = (null)
          - resourceConfigurations             = ["en", "hdpi"]
          - signingConfig                      = (null)
          - wearAppUnbundled                   = (null)
          - vectorDrawables:
             * generatedDensities = ["hdpi", "ldpi", "mdpi", "xhdpi", "xxhdpi", "xxxhdpi"]
             * useSupportLibrary  = false
       < Flavor
       > Prod-SourceProvider:
          - name                = "main"
          - ManifestFile        = [PROJECT]/src/main/AndroidManifest.xml
          > javaDirectories:
             * [PROJECT]/src/main/java
          < javaDirectories
          > resourcesDirectories:
             * [PROJECT]/src/main/resources
          < resourcesDirectories
          > aidlDirectories:
             * [PROJECT]/src/main/aidl
          < aidlDirectories
          > renderscriptDirectories:
             * [PROJECT]/src/main/rs
          < renderscriptDirectories
          > cDirectories:
             * [PROJECT]/src/main/jni
          < cDirectories
          > cppDirectories:
             * [PROJECT]/src/main/jni
          < cppDirectories
          > resDirectories:
             * [PROJECT]/src/main/res
          < resDirectories
          > assetsDirectories:
             * [PROJECT]/src/main/assets
          < assetsDirectories
          > jniLibsDirectories:
             * [PROJECT]/src/main/jniLibs
          < jniLibsDirectories
          > shadersDirectories:
             * [PROJECT]/src/main/shaders
          < shadersDirectories
          - mlModelsDirectories = (null)
       < Prod-SourceProvider
       > AndroidTest-SourceProvider:
          - name                = "androidTest"
          - ManifestFile        = [PROJECT]/src/androidTest/AndroidManifest.xml
          > javaDirectories:
             * [PROJECT]/src/androidTest/java
          < javaDirectories
          > resourcesDirectories:
             * [PROJECT]/src/androidTest/resources
          < resourcesDirectories
          > aidlDirectories:
             * [PROJECT]/src/androidTest/aidl
          < aidlDirectories
          > renderscriptDirectories:
             * [PROJECT]/src/androidTest/rs
          < renderscriptDirectories
          > cDirectories:
             * [PROJECT]/src/androidTest/jni
          < cDirectories
          > cppDirectories:
             * [PROJECT]/src/androidTest/jni
          < cppDirectories
          > resDirectories:
             * [PROJECT]/src/androidTest/res
          < resDirectories
          > assetsDirectories:
             * [PROJECT]/src/androidTest/assets
          < assetsDirectories
          > jniLibsDirectories:
             * [PROJECT]/src/androidTest/jniLibs
          < jniLibsDirectories
          > shadersDirectories:
             * [PROJECT]/src/androidTest/shaders
          < shadersDirectories
          - mlModelsDirectories = (null)
       < AndroidTest-SourceProvider
       > UnitTest-SourceProvider:
          - name                = "test"
          - ManifestFile        = [PROJECT]/src/test/AndroidManifest.xml
          > javaDirectories:
             * [PROJECT]/src/test/java
          < javaDirectories
          > resourcesDirectories:
             * [PROJECT]/src/test/resources
          < resourcesDirectories
          > aidlDirectories:
             * [PROJECT]/src/test/aidl
          < aidlDirectories
          > renderscriptDirectories:
             * [PROJECT]/src/test/rs
          < renderscriptDirectories
          > cDirectories:
             * [PROJECT]/src/test/jni
          < cDirectories
          > cppDirectories:
             * [PROJECT]/src/test/jni
          < cppDirectories
          > resDirectories:
             * [PROJECT]/src/test/res
          < resDirectories
          > assetsDirectories:
             * [PROJECT]/src/test/assets
          < assetsDirectories
          > jniLibsDirectories:
             * [PROJECT]/src/test/jniLibs
          < jniLibsDirectories
          > shadersDirectories:
             * [PROJECT]/src/test/shaders
          < shadersDirectories
          - mlModelsDirectories = (null)
       < UnitTest-SourceProvider
    < DefaultConfigContainer
    > buildTypes:
       > buildTypeContainer(debug):
          > BuildType:
             - name                     = "debug"
             - applicationIdSuffix      = ".debug"
             - versionNameSuffix        = (null)
             > buildConfigFields:
                * field:
                   * name          = "FOO"
                   * type          = "String"
                   * value         = ""bar""
                   * documentation = ""
                   * annotations   = []
             < buildConfigFields
             > resValues:
                * value:
                   * name          = "foo"
                   * value         = "foo2"
                   * documentation = ""
                   * annotations   = []
             < resValues
             - proguardFiles            = []
             - consumerProguardFiles    = []
             - testProguardFiles        = []
             - manifestPlaceholders     = []
             - multiDexEnabled          = (null)
             - multiDexKeepFile         = (null)
             - multiDexKeepProguard     = (null)
             - isDebuggable             = true
             - isTestCoverageEnabled    = true
             - isPseudoLocalesEnabled   = false
             - isJniDebuggable          = false
             - isRenderscriptDebuggable = false
             - renderscriptOptimLevel   = 3
             - isMinifyEnabled          = false
             - isZipAlignEnabled        = true
             - isEmbedMicroApp          = false
             - signingConfig            = "debug"
          < BuildType
          > Prod-SourceProvider:
             - name                = "debug"
             - ManifestFile        = [PROJECT]/src/debug/AndroidManifest.xml
             > javaDirectories:
                * [PROJECT]/src/debug/java
             < javaDirectories
             > resourcesDirectories:
                * [PROJECT]/src/debug/resources
             < resourcesDirectories
             > aidlDirectories:
                * [PROJECT]/src/debug/aidl
             < aidlDirectories
             > renderscriptDirectories:
                * [PROJECT]/src/debug/rs
             < renderscriptDirectories
             > cDirectories:
                * [PROJECT]/src/debug/jni
             < cDirectories
             > cppDirectories:
                * [PROJECT]/src/debug/jni
             < cppDirectories
             > resDirectories:
                * [PROJECT]/src/debug/res
             < resDirectories
             > assetsDirectories:
                * [PROJECT]/src/debug/assets
             < assetsDirectories
             > jniLibsDirectories:
                * [PROJECT]/src/debug/jniLibs
             < jniLibsDirectories
             > shadersDirectories:
                * [PROJECT]/src/debug/shaders
             < shadersDirectories
             - mlModelsDirectories = (null)
          < Prod-SourceProvider
          > AndroidTest-SourceProvider:
             - name                = "androidTestDebug"
             - ManifestFile        = [PROJECT]/src/androidTestDebug/AndroidManifest.xml
             > javaDirectories:
                * [PROJECT]/src/androidTestDebug/java
             < javaDirectories
             > resourcesDirectories:
                * [PROJECT]/src/androidTestDebug/resources
             < resourcesDirectories
             > aidlDirectories:
                * [PROJECT]/src/androidTestDebug/aidl
             < aidlDirectories
             > renderscriptDirectories:
                * [PROJECT]/src/androidTestDebug/rs
             < renderscriptDirectories
             > cDirectories:
                * [PROJECT]/src/androidTestDebug/jni
             < cDirectories
             > cppDirectories:
                * [PROJECT]/src/androidTestDebug/jni
             < cppDirectories
             > resDirectories:
                * [PROJECT]/src/androidTestDebug/res
             < resDirectories
             > assetsDirectories:
                * [PROJECT]/src/androidTestDebug/assets
             < assetsDirectories
             > jniLibsDirectories:
                * [PROJECT]/src/androidTestDebug/jniLibs
             < jniLibsDirectories
             > shadersDirectories:
                * [PROJECT]/src/androidTestDebug/shaders
             < shadersDirectories
             - mlModelsDirectories = (null)
          < AndroidTest-SourceProvider
          > UnitTest-SourceProvider:
             - name                = "testDebug"
             - ManifestFile        = [PROJECT]/src/testDebug/AndroidManifest.xml
             > javaDirectories:
                * [PROJECT]/src/testDebug/java
             < javaDirectories
             > resourcesDirectories:
                * [PROJECT]/src/testDebug/resources
             < resourcesDirectories
             > aidlDirectories:
                * [PROJECT]/src/testDebug/aidl
             < aidlDirectories
             > renderscriptDirectories:
                * [PROJECT]/src/testDebug/rs
             < renderscriptDirectories
             > cDirectories:
                * [PROJECT]/src/testDebug/jni
             < cDirectories
             > cppDirectories:
                * [PROJECT]/src/testDebug/jni
             < cppDirectories
             > resDirectories:
                * [PROJECT]/src/testDebug/res
             < resDirectories
             > assetsDirectories:
                * [PROJECT]/src/testDebug/assets
             < assetsDirectories
             > jniLibsDirectories:
                * [PROJECT]/src/testDebug/jniLibs
             < jniLibsDirectories
             > shadersDirectories:
                * [PROJECT]/src/testDebug/shaders
             < shadersDirectories
             - mlModelsDirectories = (null)
          < UnitTest-SourceProvider
       < buildTypeContainer(debug)
       > buildTypeContainer(release):
          > BuildType:
             - name                     = "release"
             - applicationIdSuffix      = (null)
             - versionNameSuffix        = (null)
             - buildConfigFields        = []
             - resValues                = []
             - proguardFiles            = []
             - consumerProguardFiles    = []
             - testProguardFiles        = []
             - manifestPlaceholders     = []
             - multiDexEnabled          = (null)
             - multiDexKeepFile         = (null)
             - multiDexKeepProguard     = (null)
             - isDebuggable             = false
             - isTestCoverageEnabled    = false
             - isPseudoLocalesEnabled   = false
             - isJniDebuggable          = false
             - isRenderscriptDebuggable = false
             - renderscriptOptimLevel   = 3
             - isMinifyEnabled          = false
             - isZipAlignEnabled        = true
             - isEmbedMicroApp          = true
             - signingConfig            = (null)
          < BuildType
          > Prod-SourceProvider:
             - name                = "release"
             - ManifestFile        = [PROJECT]/src/release/AndroidManifest.xml
             > javaDirectories:
                * [PROJECT]/src/release/java
             < javaDirectories
             > resourcesDirectories:
                * [PROJECT]/src/release/resources
             < resourcesDirectories
             > aidlDirectories:
                * [PROJECT]/src/release/aidl
             < aidlDirectories
             > renderscriptDirectories:
                * [PROJECT]/src/release/rs
             < renderscriptDirectories
             > cDirectories:
                * [PROJECT]/src/release/jni
             < cDirectories
             > cppDirectories:
                * [PROJECT]/src/release/jni
             < cppDirectories
             > resDirectories:
                * [PROJECT]/src/release/res
             < resDirectories
             > assetsDirectories:
                * [PROJECT]/src/release/assets
             < assetsDirectories
             > jniLibsDirectories:
                * [PROJECT]/src/release/jniLibs
             < jniLibsDirectories
             > shadersDirectories:
                * [PROJECT]/src/release/shaders
             < shadersDirectories
             - mlModelsDirectories = (null)
          < Prod-SourceProvider
          > AndroidTest-SourceProvider:
             - name                = "androidTestRelease"
             - ManifestFile        = [PROJECT]/src/androidTestRelease/AndroidManifest.xml
             > javaDirectories:
                * [PROJECT]/src/androidTestRelease/java
             < javaDirectories
             > resourcesDirectories:
                * [PROJECT]/src/androidTestRelease/resources
             < resourcesDirectories
             > aidlDirectories:
                * [PROJECT]/src/androidTestRelease/aidl
             < aidlDirectories
             > renderscriptDirectories:
                * [PROJECT]/src/androidTestRelease/rs
             < renderscriptDirectories
             > cDirectories:
                * [PROJECT]/src/androidTestRelease/jni
             < cDirectories
             > cppDirectories:
                * [PROJECT]/src/androidTestRelease/jni
             < cppDirectories
             > resDirectories:
                * [PROJECT]/src/androidTestRelease/res
             < resDirectories
             > assetsDirectories:
                * [PROJECT]/src/androidTestRelease/assets
             < assetsDirectories
             > jniLibsDirectories:
                * [PROJECT]/src/androidTestRelease/jniLibs
             < jniLibsDirectories
             > shadersDirectories:
                * [PROJECT]/src/androidTestRelease/shaders
             < shadersDirectories
             - mlModelsDirectories = (null)
          < AndroidTest-SourceProvider
          > UnitTest-SourceProvider:
             - name                = "testRelease"
             - ManifestFile        = [PROJECT]/src/testRelease/AndroidManifest.xml
             > javaDirectories:
                * [PROJECT]/src/testRelease/java
             < javaDirectories
             > resourcesDirectories:
                * [PROJECT]/src/testRelease/resources
             < resourcesDirectories
             > aidlDirectories:
                * [PROJECT]/src/testRelease/aidl
             < aidlDirectories
             > renderscriptDirectories:
                * [PROJECT]/src/testRelease/rs
             < renderscriptDirectories
             > cDirectories:
                * [PROJECT]/src/testRelease/jni
             < cDirectories
             > cppDirectories:
                * [PROJECT]/src/testRelease/jni
             < cppDirectories
             > resDirectories:
                * [PROJECT]/src/testRelease/res
             < resDirectories
             > assetsDirectories:
                * [PROJECT]/src/testRelease/assets
             < assetsDirectories
             > jniLibsDirectories:
                * [PROJECT]/src/testRelease/jniLibs
             < jniLibsDirectories
             > shadersDirectories:
                * [PROJECT]/src/testRelease/shaders
             < shadersDirectories
             - mlModelsDirectories = (null)
          < UnitTest-SourceProvider
       < buildTypeContainer(release)
    < buildTypes
    - productFlavors    = []
    > signingConfigs:
       > signingConfig(debug):
          - name            = "debug"
          - storeFile       = [ANDROID_HOME]/.android/debug.keystore
          - storePassword   = "android"
          - keyAlias        = "AndroidDebugKey"
          - keyPassword     = "android"
          - storeType       = "jks"
          - enableV1Signing = (null)
          - enableV2Signing = (null)
          - enableV3Signing = (null)
          - enableV4Signing = (null)
       < signingConfig(debug)
    < signingConfigs
    - lintRuleJars      = []
    - aaptOptions:
       * namespacing = DISABLED
    > lintOptions:
       - disable                 = ["TypographyFractions", "TypographyQuotes"]
       - enable                  = ["RtlCompat", "RtlEnabled", "RtlHardcoded"]
       - check                   = ["InlinedApi", "NewApi"]
       - isAbortOnError          = false
       - isAbsolutePaths         = true
       - isNoLines               = true
       - isQuiet                 = true
       - isCheckAllWarnings      = true
       - isIgnoreWarnings        = true
       - isWarningsAsErrors      = true
       - isCheckTestSources      = true
       - isIgnoreTestSources     = false
       - isCheckGeneratedSources = true
       - isExplainIssues         = true
       - isShowAll               = true
       - lintConfig              = [PROJECT]/default-lint.xml
       - textReport              = true
       - textOutput              = stdout
       - htmlReport              = true
       - htmlOutput              = [PROJECT]/lint-report.html
       - xmlReport               = false
       - xmlOutput               = [PROJECT]/lint-report.xml
       - isCheckReleaseBuilds    = true
       - isCheckDependencies     = false
       - baselineFile            = (null)
       - severityOverrides       = (null)
    < lintOptions
    - javaCompileOptions:
       * encoding                       = "UTF-8"
       * sourceCompatibility            = "1.8"
       * targetCompatibility            = "1.8"
       * isCoreLibraryDesugaringEnabled = false
    - viewBindingOptions:
       * isEnabled = false
    - dependenciesInfo:
       * includeInApk    = true
       * includeInBundle = true
    > flags:
       * APPLICATION_R_CLASS_CONSTANT_IDS -> true
       * JETPACK_COMPOSE                  -> false
       * ML_MODEL_BINDING                 -> false
       * TEST_R_CLASS_CONSTANT_IDS        -> true
       * TRANSITIVE_R_CLASS               -> true
    < flags
< AndroidProject

""".trimIndent())
    }
}
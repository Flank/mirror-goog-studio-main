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

package com.android.tools.lint.model

import com.android.testutils.truth.PathSubject
import com.android.tools.lint.checks.infrastructure.GradleModelMocker
import com.android.tools.lint.checks.infrastructure.GradleModelMockerTest
import com.android.tools.lint.model.LintModelSerialization.LintModelSerializationFileAdapter
import com.android.tools.lint.model.LintModelSerialization.TargetFile
import com.android.utils.XmlUtils
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter
import java.io.Writer
import java.nio.file.Files

class LintModelSerializationTest {
    @get:Rule
    var temporaryFolder = TemporaryFolder()

    @Test
    fun testFlavors() {
        val mocker: GradleModelMocker = GradleModelMockerTest.createMocker(
            """
            buildscript {
                repositories {
                    jcenter()
                }
                dependencies {
                    classpath 'com.android.tools.build:gradle:4.0.0-beta01'
                }
            }

            apply plugin: 'com.android.application'
            apply plugin: 'kotlin-android'

            groupId = "com.android.tools.demo"

            android {
                compileSdkVersion 25
                buildToolsVersion "25.0.0"
                defaultConfig {
                    applicationId "com.android.tools.test"
                    minSdkVersion 5
                    targetSdkVersion 16
                    versionCode 2
                    versionName "MyName"
                    resConfigs "mdpi"
                    resValue "string", "defaultConfigName", "Some DefaultConfig Data"
                    manifestPlaceholders = [ localApplicationId:"com.example.manifest_merger_example"]
                }
                flavorDimensions  "pricing", "releaseType"
                productFlavors {
                    beta {
                        dimension "releaseType"
                        resConfig "en"
                        resConfigs "nodpi", "hdpi"
                        versionNameSuffix "-beta"
                        applicationIdSuffix '.beta'
                        resValue "string", "VALUE_DEBUG",   "10"
                        resValue "string", "VALUE_FLAVOR",  "10"
                        resValue "string", "VALUE_VARIANT", "10"
                        manifestPlaceholders = [ localApplicationId:"com.example.manifest_merger_example.flavor"]
                    }
                    normal { dimension "releaseType" }
                    free { dimension "pricing" }
                    paid { dimension "pricing" }
                }

                buildFeatures {
                    viewBinding true
                }

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
                    baseline file("baseline.xml")
                    warning 'FooBar'
                    informational 'LogConditional'
                    checkTestSources true
                    checkDependencies true
                }

                buildTypes {
                    debug {
                        resValue "string", "debugName", "Some Debug Data"
                        manifestPlaceholders = ["holder":"debug"]
                    }
                    release {
                        resValue "string", "releaseName1", "Some Release Data 1"
                        resValue "string", "releaseName2", "Some Release Data 2"
                    }
                }
            }

            dependencies {
                // Android libraries
                compile "com.android.support:appcompat-v7:25.0.1"
                compile "com.android.support.constraint:constraint-layout:1.0.0-beta3"
                // Java libraries
                implementation "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.0"
            }
            """.trimIndent(),
            temporaryFolder
        )

        checkSerialization(
            mocker,
            mapOf(
                "module" to """
                <lint-module
                    format="1"
                    dir="＄ROOT"
                    name="test_project"
                    type="APP"
                    maven="com.android.tools.demo:test_project:"
                    gradle="4.0.0-beta01"
                    buildFolder="build"
                    javaSourceLevel="1.7"
                    compileTarget="android-25"
                    neverShrinking="true">
                  <lintOptions
                      lintConfig="default-lint.xml"
                      baselineFile="baseline.xml"
                      checkDependencies="true"
                      checkTestSources="true"
                      abortOnError="true"
                      absolutePaths="true"
                      checkReleaseBuilds="true"
                      explainIssues="true"
                      htmlReport="true"
                      xmlReport="true">
                    <severities>
                      <severity
                        id="FooBar"
                        severity="WARNING" />
                      <severity
                        id="LogConditional"
                        severity="INFORMATIONAL" />
                      <severity
                        id="RtlCompat"
                        severity="WARNING" />
                      <severity
                        id="RtlEnabled"
                        severity="WARNING" />
                      <severity
                        id="RtlHardcoded"
                        severity="WARNING" />
                      <severity
                        id="TypographyFractions"
                        severity="IGNORE" />
                      <severity
                        id="TypographyQuotes"
                        severity="IGNORE" />
                    </severities>
                  </lintOptions>
                  <variant name="freeBetaDebug"/>
                  <variant name="betaDebug"/>
                  <variant name="normalDebug"/>
                  <variant name="freeDebug"/>
                  <variant name="paidDebug"/>
                  <variant name="betaRelease"/>
                  <variant name="normalRelease"/>
                  <variant name="freeRelease"/>
                  <variant name="paidRelease"/>
                </lint-module>
                """,
                "variant-freeBetaDebug" to """
                <variant
                    name="freeBetaDebug"
                    minSdkVersion="5"
                    targetSdkVersion="16"
                    debuggable="true"
                    resourceConfigurations="mdpi,hdpi,en,nodpi">
                  <buildFeatures
                      viewBinding="true"/>
                  <sourceProviders>
                    <sourceProvider
                        manifest="src/main/AndroidManifest.xml"
                        javaDirectories="src/main/java:src/main/kotlin"
                        resDirectories="src/main/res"
                        assetsDirectories="src/main/assets"/>
                    <sourceProvider
                        manifest="src/beta/AndroidManifest.xml"
                        javaDirectories="src/beta/java:src/beta/kotlin"
                        resDirectories="src/beta/res"
                        assetsDirectories="src/beta/assets"/>
                    <sourceProvider
                        manifest="src/free/AndroidManifest.xml"
                        javaDirectories="src/free/java:src/free/kotlin"
                        resDirectories="src/free/res"
                        assetsDirectories="src/free/assets"/>
                    <sourceProvider
                        manifest="src/freeBeta/AndroidManifest.xml"
                        javaDirectories="src/freeBeta/java:src/freeBeta/kotlin"
                        resDirectories="src/freeBeta/res"
                        assetsDirectories="src/freeBeta/assets"/>
                    <sourceProvider
                        manifest="src/debug/AndroidManifest.xml"
                        javaDirectories="src/debug/java:src/debug/kotlin"
                        resDirectories="src/debug/res"
                        assetsDirectories="src/debug/assets"
                        debugOnly="true"/>
                  </sourceProviders>
                  <testSourceProviders>
                    <sourceProvider
                        manifest="src/androidTest/AndroidManifest.xml"
                        javaDirectories="src/androidTest/java:src/androidTest/kotlin"
                        resDirectories="src/androidTest/res"
                        assetsDirectories="src/androidTest/assets"
                        androidTest="true"/>
                    <sourceProvider
                        manifest="src/test/AndroidManifest.xml"
                        javaDirectories="src/test/java:src/test/kotlin"
                        resDirectories="src/test/res"
                        assetsDirectories="src/test/assets"
                        unitTest="true"/>
                  </testSourceProviders>
                  <resValues>
                    <resValue
                        type="string"
                        name="VALUE_DEBUG"
                        value="10" />
                    <resValue
                        type="string"
                        name="VALUE_FLAVOR"
                        value="10" />
                    <resValue
                        type="string"
                        name="VALUE_VARIANT"
                        value="10" />
                    <resValue
                        type="string"
                        name="debugName"
                        value="Some Debug Data" />
                    <resValue
                        type="string"
                        name="defaultConfigName"
                        value="Some DefaultConfig Data" />
                  </resValues>
                  <manifestPlaceholders>
                    <placeholder
                        name="holder"
                        value="debug" />
                    <placeholder
                        name="localApplicationId"
                        value="com.example.manifest_merger_example.flavor" />
                  </manifestPlaceholders>
                  <mainArtifact
                      classOutputs="build/intermediates/javac/freeBetaDebug/classes:build/tmp/kotlin-classes/freeBetaDebug"
                      applicationId="com.android.tools.test">
                  </mainArtifact>
                  <androidTestArtifact
                      classOutputs="instrumentation-classes"
                      applicationId="com.android.tools.test">
                  </androidTestArtifact>
                  <testArtifact
                      classOutputs="test-classes">
                  </testArtifact>
                </variant>
              """,

                "dependencies-freeBetaDebug-mainArtifact" to """
                    <dependencies>
                      <compile
                          roots="com.android.support:appcompat-v7:25.0.1,com.android.support:support-v4:25.0.1,com.android.support:support-compat:25.0.1,com.android.support:support-media-compat:25.0.1,com.android.support:support-core-utils:25.0.1,com.android.support:support-core-ui:25.0.1,com.android.support:support-fragment:25.0.1,com.android.support:support-vector-drawable:25.0.1,com.android.support:animated-vector-drawable:25.0.1,com.android.support.constraint:constraint-layout:1.0.0-beta3,com.android.support:support-annotations:25.0.1,com.android.support.constraint:constraint-layout-solver:1.0.0-beta3,org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.0,org.jetbrains.kotlin:kotlin-stdlib:1.3.0,org.jetbrains.kotlin:kotlin-stdlib-common:1.3.0,org.jetbrains:annotations:13.0">
                        <dependency
                            name="com.android.support:appcompat-v7:25.0.1"/>
                        <dependency
                            name="com.android.support:support-v4:25.0.1"/>
                        <dependency
                            name="com.android.support:support-compat:25.0.1"/>
                        <dependency
                            name="com.android.support:support-media-compat:25.0.1"/>
                        <dependency
                            name="com.android.support:support-core-utils:25.0.1"/>
                        <dependency
                            name="com.android.support:support-core-ui:25.0.1"/>
                        <dependency
                            name="com.android.support:support-fragment:25.0.1"/>
                        <dependency
                            name="com.android.support:support-vector-drawable:25.0.1"/>
                        <dependency
                            name="com.android.support:animated-vector-drawable:25.0.1"/>
                        <dependency
                            name="com.android.support.constraint:constraint-layout:1.0.0-beta3"/>
                        <dependency
                            name="com.android.support:support-annotations:25.0.1"/>
                        <dependency
                            name="com.android.support.constraint:constraint-layout-solver:1.0.0-beta3"/>
                        <dependency
                            name="org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.0"/>
                        <dependency
                            name="org.jetbrains.kotlin:kotlin-stdlib:1.3.0"/>
                        <dependency
                            name="org.jetbrains.kotlin:kotlin-stdlib-common:1.3.0"/>
                        <dependency
                            name="org.jetbrains:annotations:13.0"/>
                      </compile>
                      <package
                          roots="com.android.support:appcompat-v7:25.0.1,com.android.support:support-v4:25.0.1,com.android.support:support-compat:25.0.1,com.android.support:support-media-compat:25.0.1,com.android.support:support-core-utils:25.0.1,com.android.support:support-core-ui:25.0.1,com.android.support:support-fragment:25.0.1,com.android.support:support-vector-drawable:25.0.1,com.android.support:animated-vector-drawable:25.0.1,com.android.support.constraint:constraint-layout:1.0.0-beta3,com.android.support:support-annotations:25.0.1,com.android.support.constraint:constraint-layout-solver:1.0.0-beta3,org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.0,org.jetbrains.kotlin:kotlin-stdlib:1.3.0,org.jetbrains.kotlin:kotlin-stdlib-common:1.3.0,org.jetbrains:annotations:13.0">
                        <dependency
                            name="com.android.support:appcompat-v7:25.0.1"/>
                        <dependency
                            name="com.android.support:support-v4:25.0.1"/>
                        <dependency
                            name="com.android.support:support-compat:25.0.1"/>
                        <dependency
                            name="com.android.support:support-media-compat:25.0.1"/>
                        <dependency
                            name="com.android.support:support-core-utils:25.0.1"/>
                        <dependency
                            name="com.android.support:support-core-ui:25.0.1"/>
                        <dependency
                            name="com.android.support:support-fragment:25.0.1"/>
                        <dependency
                            name="com.android.support:support-vector-drawable:25.0.1"/>
                        <dependency
                            name="com.android.support:animated-vector-drawable:25.0.1"/>
                        <dependency
                            name="com.android.support.constraint:constraint-layout:1.0.0-beta3"/>
                        <dependency
                            name="com.android.support:support-annotations:25.0.1"/>
                        <dependency
                            name="com.android.support.constraint:constraint-layout-solver:1.0.0-beta3"/>
                        <dependency
                            name="org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.0"/>
                        <dependency
                            name="org.jetbrains.kotlin:kotlin-stdlib:1.3.0"/>
                        <dependency
                            name="org.jetbrains.kotlin:kotlin-stdlib-common:1.3.0"/>
                        <dependency
                            name="org.jetbrains:annotations:13.0"/>
                      </package>
                    </dependencies>
                """,
                "dependencies-freeBetaDebug-testArtifact" to """
                <dependencies>
                </dependencies>
                """,
                "dependencies-freeBetaDebug-androidTestArtifact" to """
                <dependencies>
                </dependencies>
                """,
                "dependencies-betaDebug-mainArtifact" to """
                <dependencies>
                </dependencies>
                """,
                "library_table-freeBetaDebug-mainArtifact" to """
                <libraries>
                  <library
                      name="com.android.support:appcompat-v7:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/appcompat-v7/25.0.1/jars/classes.jar"
                      resolved="com.android.support:appcompat-v7:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/appcompat-v7/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:support-v4:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-v4/25.0.1/jars/classes.jar"
                      resolved="com.android.support:support-v4:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-v4/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:support-compat:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-compat/25.0.1/jars/classes.jar"
                      resolved="com.android.support:support-compat:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-compat/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:support-media-compat:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-media-compat/25.0.1/jars/classes.jar"
                      resolved="com.android.support:support-media-compat:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-media-compat/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:support-core-utils:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-core-utils/25.0.1/jars/classes.jar"
                      resolved="com.android.support:support-core-utils:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-core-utils/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:support-core-ui:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-core-ui/25.0.1/jars/classes.jar"
                      resolved="com.android.support:support-core-ui:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-core-ui/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:support-fragment:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-fragment/25.0.1/jars/classes.jar"
                      resolved="com.android.support:support-fragment:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-fragment/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:support-vector-drawable:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-vector-drawable/25.0.1/jars/classes.jar"
                      resolved="com.android.support:support-vector-drawable:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-vector-drawable/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:animated-vector-drawable:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/animated-vector-drawable/25.0.1/jars/classes.jar"
                      resolved="com.android.support:animated-vector-drawable:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/animated-vector-drawable/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support.constraint:constraint-layout:1.0.0-beta3"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support.constraint/constraint-layout/1.0.0-beta3/jars/classes.jar"
                      resolved="com.android.support.constraint:constraint-layout:1.0.0-beta3"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support.constraint/constraint-layout/1.0.0-beta3"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:support-annotations:25.0.1"
                      jars="$ROOT/caches/modules-2/files-2.1/com.android.support/support-annotations/25.0.19c6ef172e8de35fd8d4d8783e4821e57cdef7445/support-annotations-25.0.1.jar"
                      resolved="com.android.support:support-annotations:25.0.1"/>
                  <library
                      name="com.android.support.constraint:constraint-layout-solver:1.0.0-beta3"
                      jars="$ROOT/caches/modules-2/files-2.1/com.android.support.constraint/constraint-layout-solver/1.0.0-beta39c6ef172e8de35fd8d4d8783e4821e57cdef7445/constraint-layout-solver-1.0.0-beta3.jar"
                      resolved="com.android.support.constraint:constraint-layout-solver:1.0.0-beta3"/>
                  <library
                      name="org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.0"
                      jars="＄ROOT/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-jdk7/1.3.09c6ef172e8de35fd8d4d8783e4821e57cdef7445/kotlin-stdlib-jdk7-1.3.0.jar"
                      resolved="org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.0"/>
                  <library
                      name="org.jetbrains.kotlin:kotlin-stdlib:1.3.0"
                      jars="＄ROOT/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.3.09c6ef172e8de35fd8d4d8783e4821e57cdef7445/kotlin-stdlib-1.3.0.jar"
                      resolved="org.jetbrains.kotlin:kotlin-stdlib:1.3.0"/>
                  <library
                      name="org.jetbrains.kotlin:kotlin-stdlib-common:1.3.0"
                      jars="＄ROOT/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-common/1.3.09c6ef172e8de35fd8d4d8783e4821e57cdef7445/kotlin-stdlib-common-1.3.0.jar"
                      resolved="org.jetbrains.kotlin:kotlin-stdlib-common:1.3.0"/>
                  <library
                      name="org.jetbrains:annotations:13.0"
                      jars="$ROOT/caches/modules-2/files-2.1/org.jetbrains/annotations/13.09c6ef172e8de35fd8d4d8783e4821e57cdef7445/annotations-13.0.jar"
                      resolved="org.jetbrains:annotations:13.0"/>
                </libraries>
                """,
                "library_table-freeBetaDebug-testArtifact" to """
                <libraries>
                </libraries>
                """,
                "library_table-freeBetaDebug-androidTestArtifact" to """
                <libraries>
                </libraries>
                """,

                "variant-betaDebug" to """
                <variant
                    name="betaDebug"
                    minSdkVersion="5"
                    targetSdkVersion="16"
                    debuggable="true">
                  <buildFeatures
                      viewBinding="true"/>
                  <sourceProviders>
                    <sourceProvider
                        manifest="src/main/AndroidManifest.xml"
                        javaDirectories="src/main/java:src/main/kotlin"
                        resDirectories="src/main/res"
                        assetsDirectories="src/main/assets"/>
                    <sourceProvider
                        manifest="src/beta/AndroidManifest.xml"
                        javaDirectories="src/beta/java:src/beta/kotlin"
                        resDirectories="src/beta/res"
                        assetsDirectories="src/beta/assets"/>
                    <sourceProvider
                        manifest="src/debug/AndroidManifest.xml"
                        javaDirectories="src/debug/java:src/debug/kotlin"
                        resDirectories="src/debug/res"
                        assetsDirectories="src/debug/assets"
                        debugOnly="true"/>
                  </sourceProviders>
                  <testSourceProviders>
                    <sourceProvider
                        manifest="src/androidTest/AndroidManifest.xml"
                        javaDirectories="src/androidTest/java:src/androidTest/kotlin"
                        resDirectories="src/androidTest/res"
                        assetsDirectories="src/androidTest/assets"
                        androidTest="true"/>
                    <sourceProvider
                        manifest="src/test/AndroidManifest.xml"
                        javaDirectories="src/test/java:src/test/kotlin"
                        resDirectories="src/test/res"
                        assetsDirectories="src/test/assets"
                        unitTest="true"/>
                  </testSourceProviders>
                  <resValues>
                    <resValue
                        type="string"
                        name="debugName"
                        value="Some Debug Data" />
                  </resValues>
                  <manifestPlaceholders>
                    <placeholder
                        name="holder"
                        value="debug" />
                  </manifestPlaceholders>
                  <mainArtifact
                      classOutputs="build/intermediates/javac/betaDebug/classes:build/tmp/kotlin-classes/betaDebug"
                      applicationId="com.android.tools.test">
                  </mainArtifact>
                </variant>
              """,
                "variant-normalDebug" to """
                <variant
                    name="normalDebug"
                    minSdkVersion="5"
                    targetSdkVersion="16"
                    debuggable="true">
                  <buildFeatures
                      viewBinding="true"/>
                  <sourceProviders>
                    <sourceProvider
                        manifest="src/main/AndroidManifest.xml"
                        javaDirectories="src/main/java:src/main/kotlin"
                        resDirectories="src/main/res"
                        assetsDirectories="src/main/assets"/>
                    <sourceProvider
                        manifest="src/normal/AndroidManifest.xml"
                        javaDirectories="src/normal/java:src/normal/kotlin"
                        resDirectories="src/normal/res"
                        assetsDirectories="src/normal/assets"/>
                    <sourceProvider
                        manifest="src/debug/AndroidManifest.xml"
                        javaDirectories="src/debug/java:src/debug/kotlin"
                        resDirectories="src/debug/res"
                        assetsDirectories="src/debug/assets"
                        debugOnly="true"/>
                  </sourceProviders>
                  <testSourceProviders>
                    <sourceProvider
                        manifest="src/androidTest/AndroidManifest.xml"
                        javaDirectories="src/androidTest/java:src/androidTest/kotlin"
                        resDirectories="src/androidTest/res"
                        assetsDirectories="src/androidTest/assets"
                        androidTest="true"/>
                    <sourceProvider
                        manifest="src/test/AndroidManifest.xml"
                        javaDirectories="src/test/java:src/test/kotlin"
                        resDirectories="src/test/res"
                        assetsDirectories="src/test/assets"
                        unitTest="true"/>
                  </testSourceProviders>
                  <resValues>
                    <resValue
                        type="string"
                        name="debugName"
                        value="Some Debug Data" />
                  </resValues>
                  <manifestPlaceholders>
                    <placeholder
                        name="holder"
                        value="debug" />
                  </manifestPlaceholders>
                  <mainArtifact
                      classOutputs="build/intermediates/javac/normalDebug/classes:build/tmp/kotlin-classes/normalDebug"
                      applicationId="com.android.tools.test">
                  </mainArtifact>
                </variant>
              """,
                "variant-freeDebug" to """
                <variant
                    name="freeDebug"
                    minSdkVersion="5"
                    targetSdkVersion="16"
                    debuggable="true">
                  <buildFeatures
                      viewBinding="true"/>
                  <sourceProviders>
                    <sourceProvider
                        manifest="src/main/AndroidManifest.xml"
                        javaDirectories="src/main/java:src/main/kotlin"
                        resDirectories="src/main/res"
                        assetsDirectories="src/main/assets"/>
                    <sourceProvider
                        manifest="src/free/AndroidManifest.xml"
                        javaDirectories="src/free/java:src/free/kotlin"
                        resDirectories="src/free/res"
                        assetsDirectories="src/free/assets"/>
                    <sourceProvider
                        manifest="src/debug/AndroidManifest.xml"
                        javaDirectories="src/debug/java:src/debug/kotlin"
                        resDirectories="src/debug/res"
                        assetsDirectories="src/debug/assets"
                        debugOnly="true"/>
                  </sourceProviders>
                  <testSourceProviders>
                    <sourceProvider
                        manifest="src/androidTest/AndroidManifest.xml"
                        javaDirectories="src/androidTest/java:src/androidTest/kotlin"
                        resDirectories="src/androidTest/res"
                        assetsDirectories="src/androidTest/assets"
                        androidTest="true"/>
                    <sourceProvider
                        manifest="src/test/AndroidManifest.xml"
                        javaDirectories="src/test/java:src/test/kotlin"
                        resDirectories="src/test/res"
                        assetsDirectories="src/test/assets"
                        unitTest="true"/>
                  </testSourceProviders>
                  <resValues>
                    <resValue
                        type="string"
                        name="debugName"
                        value="Some Debug Data" />
                  </resValues>
                  <manifestPlaceholders>
                    <placeholder
                        name="holder"
                        value="debug" />
                  </manifestPlaceholders>
                  <mainArtifact
                      classOutputs="build/intermediates/javac/freeDebug/classes:build/tmp/kotlin-classes/freeDebug"
                      applicationId="com.android.tools.test">
                  </mainArtifact>
                </variant>
              """,
                "variant-paidDebug" to """
                <variant
                    name="paidDebug"
                    minSdkVersion="5"
                    targetSdkVersion="16"
                    debuggable="true">
                  <buildFeatures
                      viewBinding="true"/>
                  <sourceProviders>
                    <sourceProvider
                        manifest="src/main/AndroidManifest.xml"
                        javaDirectories="src/main/java:src/main/kotlin"
                        resDirectories="src/main/res"
                        assetsDirectories="src/main/assets"/>
                    <sourceProvider
                        manifest="src/paid/AndroidManifest.xml"
                        javaDirectories="src/paid/java:src/paid/kotlin"
                        resDirectories="src/paid/res"
                        assetsDirectories="src/paid/assets"/>
                    <sourceProvider
                        manifest="src/debug/AndroidManifest.xml"
                        javaDirectories="src/debug/java:src/debug/kotlin"
                        resDirectories="src/debug/res"
                        assetsDirectories="src/debug/assets"
                        debugOnly="true"/>
                  </sourceProviders>
                  <testSourceProviders>
                    <sourceProvider
                        manifest="src/androidTest/AndroidManifest.xml"
                        javaDirectories="src/androidTest/java:src/androidTest/kotlin"
                        resDirectories="src/androidTest/res"
                        assetsDirectories="src/androidTest/assets"
                        androidTest="true"/>
                    <sourceProvider
                        manifest="src/test/AndroidManifest.xml"
                        javaDirectories="src/test/java:src/test/kotlin"
                        resDirectories="src/test/res"
                        assetsDirectories="src/test/assets"
                        unitTest="true"/>
                  </testSourceProviders>
                  <resValues>
                    <resValue
                        type="string"
                        name="debugName"
                        value="Some Debug Data" />
                  </resValues>
                  <manifestPlaceholders>
                    <placeholder
                        name="holder"
                        value="debug" />
                  </manifestPlaceholders>
                  <mainArtifact
                      classOutputs="build/intermediates/javac/paidDebug/classes:build/tmp/kotlin-classes/paidDebug"
                      applicationId="com.android.tools.test">
                  </mainArtifact>
                </variant>
              """,
                "variant-betaRelease" to """
                <variant
                    name="betaRelease"
                    minSdkVersion="5"
                    targetSdkVersion="16">
                  <buildFeatures
                      viewBinding="true"/>
                  <sourceProviders>
                    <sourceProvider
                        manifest="src/main/AndroidManifest.xml"
                        javaDirectories="src/main/java:src/main/kotlin"
                        resDirectories="src/main/res"
                        assetsDirectories="src/main/assets"/>
                    <sourceProvider
                        manifest="src/beta/AndroidManifest.xml"
                        javaDirectories="src/beta/java:src/beta/kotlin"
                        resDirectories="src/beta/res"
                        assetsDirectories="src/beta/assets"/>
                    <sourceProvider
                        manifest="src/release/AndroidManifest.xml"
                        javaDirectories="src/release/java:src/release/kotlin"
                        resDirectories="src/release/res"
                        assetsDirectories="src/release/assets"/>
                  </sourceProviders>
                  <testSourceProviders>
                    <sourceProvider
                        manifest="src/androidTest/AndroidManifest.xml"
                        javaDirectories="src/androidTest/java:src/androidTest/kotlin"
                        resDirectories="src/androidTest/res"
                        assetsDirectories="src/androidTest/assets"
                        androidTest="true"/>
                    <sourceProvider
                        manifest="src/test/AndroidManifest.xml"
                        javaDirectories="src/test/java:src/test/kotlin"
                        resDirectories="src/test/res"
                        assetsDirectories="src/test/assets"
                        unitTest="true"/>
                  </testSourceProviders>
                  <resValues>
                    <resValue
                        type="string"
                        name="releaseName1"
                        value="Some Release Data 1" />
                    <resValue
                        type="string"
                        name="releaseName2"
                        value="Some Release Data 2" />
                  </resValues>
                  <mainArtifact
                      classOutputs="build/intermediates/javac/betaRelease/classes:build/tmp/kotlin-classes/betaRelease"
                      applicationId="com.android.tools.test">
                  </mainArtifact>
                </variant>
              """,
                "variant-normalRelease" to """
                <variant
                    name="normalRelease"
                    minSdkVersion="5"
                    targetSdkVersion="16">
                  <buildFeatures
                      viewBinding="true"/>
                  <sourceProviders>
                    <sourceProvider
                        manifest="src/main/AndroidManifest.xml"
                        javaDirectories="src/main/java:src/main/kotlin"
                        resDirectories="src/main/res"
                        assetsDirectories="src/main/assets"/>
                    <sourceProvider
                        manifest="src/normal/AndroidManifest.xml"
                        javaDirectories="src/normal/java:src/normal/kotlin"
                        resDirectories="src/normal/res"
                        assetsDirectories="src/normal/assets"/>
                    <sourceProvider
                        manifest="src/release/AndroidManifest.xml"
                        javaDirectories="src/release/java:src/release/kotlin"
                        resDirectories="src/release/res"
                        assetsDirectories="src/release/assets"/>
                  </sourceProviders>
                  <testSourceProviders>
                    <sourceProvider
                        manifest="src/androidTest/AndroidManifest.xml"
                        javaDirectories="src/androidTest/java:src/androidTest/kotlin"
                        resDirectories="src/androidTest/res"
                        assetsDirectories="src/androidTest/assets"
                        androidTest="true"/>
                    <sourceProvider
                        manifest="src/test/AndroidManifest.xml"
                        javaDirectories="src/test/java:src/test/kotlin"
                        resDirectories="src/test/res"
                        assetsDirectories="src/test/assets"
                        unitTest="true"/>
                  </testSourceProviders>
                  <resValues>
                    <resValue
                        type="string"
                        name="releaseName1"
                        value="Some Release Data 1" />
                    <resValue
                        type="string"
                        name="releaseName2"
                        value="Some Release Data 2" />
                  </resValues>
                  <mainArtifact
                      classOutputs="build/intermediates/javac/normalRelease/classes:build/tmp/kotlin-classes/normalRelease"
                      applicationId="com.android.tools.test">
                  </mainArtifact>
                </variant>
              """,
                "variant-freeRelease" to """
                <variant
                    name="freeRelease"
                    minSdkVersion="5"
                    targetSdkVersion="16">
                  <buildFeatures
                      viewBinding="true"/>
                  <sourceProviders>
                    <sourceProvider
                        manifest="src/main/AndroidManifest.xml"
                        javaDirectories="src/main/java:src/main/kotlin"
                        resDirectories="src/main/res"
                        assetsDirectories="src/main/assets"/>
                    <sourceProvider
                        manifest="src/free/AndroidManifest.xml"
                        javaDirectories="src/free/java:src/free/kotlin"
                        resDirectories="src/free/res"
                        assetsDirectories="src/free/assets"/>
                    <sourceProvider
                        manifest="src/release/AndroidManifest.xml"
                        javaDirectories="src/release/java:src/release/kotlin"
                        resDirectories="src/release/res"
                        assetsDirectories="src/release/assets"/>
                  </sourceProviders>
                  <testSourceProviders>
                    <sourceProvider
                        manifest="src/androidTest/AndroidManifest.xml"
                        javaDirectories="src/androidTest/java:src/androidTest/kotlin"
                        resDirectories="src/androidTest/res"
                        assetsDirectories="src/androidTest/assets"
                        androidTest="true"/>
                    <sourceProvider
                        manifest="src/test/AndroidManifest.xml"
                        javaDirectories="src/test/java:src/test/kotlin"
                        resDirectories="src/test/res"
                        assetsDirectories="src/test/assets"
                        unitTest="true"/>
                  </testSourceProviders>
                  <resValues>
                    <resValue
                        type="string"
                        name="releaseName1"
                        value="Some Release Data 1" />
                    <resValue
                        type="string"
                        name="releaseName2"
                        value="Some Release Data 2" />
                  </resValues>
                  <mainArtifact
                      classOutputs="build/intermediates/javac/freeRelease/classes:build/tmp/kotlin-classes/freeRelease"
                      applicationId="com.android.tools.test">
                  </mainArtifact>
                </variant>
              """,
                "variant-paidRelease" to """
                <variant
                    name="paidRelease"
                    minSdkVersion="5"
                    targetSdkVersion="16">
                  <buildFeatures
                      viewBinding="true"/>
                  <sourceProviders>
                    <sourceProvider
                        manifest="src/main/AndroidManifest.xml"
                        javaDirectories="src/main/java:src/main/kotlin"
                        resDirectories="src/main/res"
                        assetsDirectories="src/main/assets"/>
                    <sourceProvider
                        manifest="src/paid/AndroidManifest.xml"
                        javaDirectories="src/paid/java:src/paid/kotlin"
                        resDirectories="src/paid/res"
                        assetsDirectories="src/paid/assets"/>
                    <sourceProvider
                        manifest="src/release/AndroidManifest.xml"
                        javaDirectories="src/release/java:src/release/kotlin"
                        resDirectories="src/release/res"
                        assetsDirectories="src/release/assets"/>
                  </sourceProviders>
                  <testSourceProviders>
                    <sourceProvider
                        manifest="src/androidTest/AndroidManifest.xml"
                        javaDirectories="src/androidTest/java:src/androidTest/kotlin"
                        resDirectories="src/androidTest/res"
                        assetsDirectories="src/androidTest/assets"
                        androidTest="true"/>
                    <sourceProvider
                        manifest="src/test/AndroidManifest.xml"
                        javaDirectories="src/test/java:src/test/kotlin"
                        resDirectories="src/test/res"
                        assetsDirectories="src/test/assets"
                        unitTest="true"/>
                  </testSourceProviders>
                  <resValues>
                    <resValue
                        type="string"
                        name="releaseName1"
                        value="Some Release Data 1" />
                    <resValue
                        type="string"
                        name="releaseName2"
                        value="Some Release Data 2" />
                  </resValues>
                  <mainArtifact
                      classOutputs="build/intermediates/javac/paidRelease/classes:build/tmp/kotlin-classes/paidRelease"
                      applicationId="com.android.tools.test">
                  </mainArtifact>
                </variant>
              """
            )
        )
    }

    @Test
    fun testMissingTags() {
        tryParse(
            """
            <lint-module
                dir="root"
                name="test_project"
                type="APP"
                gradle="4.0.0-beta01"
                buildFolder="＄ROOT/build"
                javaSourceLevel="1.7"
                compileTarget="android-25">
            </lint-module>
            """,
            "Missing data at testfile.xml:11"
        )
    }

    @Test
    fun testMissingAttribute() {
        tryParse(
            """
            <lint-module
                dir="root"
                compileTarget="android-25">
            </lint-module>
            """,
            "Expected `name` attribute in <lint-module> tag at testfile.xml:4"
        )
    }

    @Test
    fun testUnexpectedTag() {
        tryParse(
            """
            <lint-module
                dir="root"
                name="test_project"
                type="APP"
                gradle="4.0.0-beta01"
                buildFolder="＄ROOT/build"
                javaSourceLevel="1.7"
                compileTarget="android-25">
                <foobar />
            </lint-module>
            """,
            "Unexpected tag `<foobar>` at testfile.xml:10"
        )
    }

    @Test
    fun testPathVariables() {
        val root = temporaryFolder.root
        fun String.cleanup() = replace(root.path, "＄ROOT").trim()
        val moduleFile = File(root, "module.xml")
        val folder1 = temporaryFolder.newFolder()
        val folder2 = temporaryFolder.newFolder()
        val file1 = File(folder1, "file1")
        file1.createNewFile()
        val file2 = File(folder2, "file2")
        file2.createNewFile()

        val pathVariables: MutableList<Pair<String, File>> = mutableListOf()
        pathVariables.add(Pair("SDK", folder1))
        pathVariables.add(Pair("GRADLE", folder2))
        val adapter = LintModelSerializationFileAdapter(root, pathVariables)

        assertEquals("module.xml", adapter.toPathString(moduleFile, root).cleanup())
        assertEquals("\$SDK/file1", adapter.toPathString(file1, root))
        assertEquals("\$GRADLE/file2", adapter.toPathString(file2, root))

        assertEquals(moduleFile, adapter.fromPathString("module.xml", root))
        assertEquals(file1, adapter.fromPathString("\$SDK/file1", root))
        assertEquals(file2, adapter.fromPathString("\$GRADLE/file2", root))
    }

    /** Check that the relative paths in variants are resolved against the project directory */
    @Test
    fun testLintModelSerializationFileAdapterRootHandling() {
        val temp = temporaryFolder.newFolder()
        val projectDirectory = temp.resolve("projectDir").createDirectories()
        projectDirectory.resolve("src/main/").createDirectories()
            .resolve("AndroidManifest.xml").writeText("Fake Android manifest")
        val buildDirectory = temp.resolve("buildDir").createDirectories()
        val modelsDir = buildDirectory.resolve("intermediates/lint-models").createDirectories()
        modelsDir.resolve("module.xml")
            .writeText(
                """<lint-module
                    format="1"
                    dir="${projectDirectory.absolutePath}"
                    name="test_project"
                    type="APP"
                    maven="com.android.tools.demo:test_project:"
                    gradle="4.0.0-beta01"
                    buildFolder="${buildDirectory.absolutePath}"
                    javaSourceLevel="1.7"
                    compileTarget="android-25"
                    neverShrinking="true">
                  <lintOptions />
                  <variant name="debug"/>
                </lint-module>"""
            )
        modelsDir.resolve("debug.xml")
            .writeText(
                """<variant
                    name="debug"
                    minSdkVersion="5"
                    targetSdkVersion="16"
                    debuggable="true">
                  <buildFeatures />
                  <sourceProviders>
                    <sourceProvider
                        manifest="src/main/AndroidManifest.xml"
                        javaDirectories="src/main/java:src/main/kotlin"
                        resDirectories="src/main/res"
                        assetsDirectories="src/main/assets"/>
                  </sourceProviders>
                  <mainArtifact
                      classOutputs="${buildDirectory.absolutePath}/intermediates/javac/freeBetaDebug/classes:${buildDirectory.absolutePath}/intermediates/kotlin-classes/freeBetaDebug"
                      applicationId="com.android.tools.test">
                  </mainArtifact>
                </variant>"""
            )

        val module = LintModelSerialization.readModule(
            source = modelsDir,
            readDependencies = false
        )

        val manifestFile = module.defaultVariant()!!.sourceProviders.first().manifestFile
        assertWithMessage("Source file should be resolved relative to the project directory, not the source directory")
            .about(PathSubject.paths())
            .that(manifestFile.toPath())
            .hasContents("Fake Android manifest")
    }

    @Test
    fun testLintModelSerializationManifest() {
        val temp = temporaryFolder.newFolder()
        val projectDirectory = temp.resolve("projectDir").createDirectories()
        val buildDirectory = temp.resolve("buildDir").createDirectories()
        val modelsDir = buildDirectory.resolve("intermediates/lint-models").createDirectories()
        val mergedManifest = buildDirectory.resolve("intermediates/merged_manifest/debug").createDirectories()
            .resolve("AndroidManifest.xml").apply { writeText("Merged manifest") }
        val mergeReport = buildDirectory.resolve("outputs/reports/manifest/debug").createDirectories()
            .resolve("ManifestMergeReport.xml").apply { writeText("Manifest merge report") }
        modelsDir.resolve("module.xml")
            .writeText(
                """<lint-module
                    format="1"
                    dir="${projectDirectory.absolutePath}"
                    name="test_project"
                    type="APP"
                    maven="com.android.tools.demo:test_project:"
                    gradle="4.0.0-beta01"
                    buildFolder="${buildDirectory.absolutePath}"
                    javaSourceLevel="1.7"
                    compileTarget="android-25"
                    neverShrinking="true">
                  <lintOptions />
                  <variant name="debug"/>
                </lint-module>"""
            )
        val debugXml =
            """<variant
                    name="debug"
                    minSdkVersion="5"
                    targetSdkVersion="16"
                    debuggable="true"
                    mergedManifest="${mergedManifest.absolutePath}"
                    manifestMergeReport="${mergeReport.absolutePath}">
                  <buildFeatures />
                  <mainArtifact applicationId="com.android.tools.test">
                  </mainArtifact>
                </variant>"""
        modelsDir.resolve("debug.xml").writeText(debugXml)

        val module = LintModelSerialization.readModule(
            source = modelsDir,
            readDependencies = false
        )

        val debugVariant = module.defaultVariant()!!

        assertWithMessage("Merged manifest is read correctly")
            .about(PathSubject.paths())
            .that(debugVariant.mergedManifest?.toPath())
            .hasContents("Merged manifest")

        assertWithMessage("Merged manifest is read correctly")
            .about(PathSubject.paths())
            .that(debugVariant.manifestMergeReport?.toPath())
            .hasContents("Manifest merge report")
    }

    // ----------------------------------------------------------------------------------
    // Test infrastructure below this line
    // ----------------------------------------------------------------------------------

    private fun tryParse(
        @Language("XML") xml: String,
        expectedErrors: String? = null
    ) {
        try {
            val reader = StringReader(xml)
            LintModelSerialization.readModule(LintModelSerializationStringAdapter(reader = { _, _, _ -> reader }))
            if (expectedErrors != null) {
                fail("Expected failure, got valid module instead")
            }
        } catch (error: Throwable) {
            val message = error.message ?: error.toString()
            if (expectedErrors != null) {
                assertThat(expectedErrors).isEqualTo(message)
            } else {
                fail(message)
            }
        }
    }

    private fun checkSerialization(
        mocker: GradleModelMocker,
        expectedXml: Map<String, String>
    ) {
        val project = mocker.project
        val variants = mocker.variants
        val dir = mocker.projectDir

        val path = mocker.projectDir.path
        fun String.cleanup() = replace(path, "＄ROOT").trim()

        // Test lint model stuff
        val module = LintModelFactory().create(project, variants, dir)
        val xml = writeModule(module)

        // Make sure all the generated XML is valid
        for ((_, s) in xml) {
            assertValidXml(s)
        }
        val remainingExpectedXml = expectedXml.toMutableMap()

        for (fileType in TargetFile.values()) {
            for (variant in module.variants) {
                for (
                    artifactName in listOf(
                        "mainArtifact",
                        "testArtifact",
                        "androidTestArtifact"
                    )
                ) {
                    val mapKey = getMapKey(fileType, variant.name, artifactName)
                    val writtenXml: String = xml[mapKey] ?: continue
                    assertValidXml(writtenXml)
                    val expected = remainingExpectedXml.remove(mapKey) ?: continue
                    assertThat(writtenXml.cleanup()).isEqualTo(expected.trimIndent().trim())
                }
            }
        }
        assertThat(remainingExpectedXml).isEmpty()

        val newModule =
            LintModelSerialization.readModule(
                LintModelSerializationStringAdapter(
                    reader = { target, variantName, artifact ->
                        val contents = xml[getMapKey(target, variantName, artifact)]!!
                        StringReader(contents)
                    }
                )
            )
        val newXml = writeModule(newModule)
        for ((key, contents) in xml) {
            assertEquals(
                "XML parsed and written back out does not match original for file " + key,
                contents, newXml[key]
            )
        }
    }

    private fun assertValidXml(xml: String) {
        try {
            val document =
                XmlUtils.parseDocument(xml, false)
            assertNotNull(document)
            assertNoTextNodes(document.documentElement)
        } catch (e: IOException) {
            fail(e.message)
        } catch (e: SAXException) {
            fail(e.message)
        }
    }

    private fun getMapKey(
        target: TargetFile,
        variantName: String = "",
        artifactName: String = ""
    ): String {
        val key = StringBuilder(target.name.toLowerCase())
        if (variantName.isNotEmpty() && target != TargetFile.MODULE) {
            key.append("-")
            key.append(variantName)
            if (artifactName.isNotEmpty() &&
                (target == TargetFile.DEPENDENCIES || target == TargetFile.LIBRARY_TABLE)
            ) {
                key.append("-")
                key.append(artifactName)
            }
        }

        return key.toString()
    }

    private fun writeModule(module: LintModelModule): Map<String, String> {
        val map = mutableMapOf<String, StringWriter>()
        LintModelSerialization.writeModule(
            module,
            LintModelSerializationStringAdapter(
                writer = { target, variantName, artifactName ->
                    val key = getMapKey(target, variantName, artifactName)
                    map[key] ?: StringWriter().also { map[key] = it }
                }
            )
        )
        return map.mapValues {
            it.value.toString()
        }
    }

    private fun writeVariant(variant: LintModelVariant): String {
        val writer = StringWriter()
        LintModelSerialization.writeVariant(
            variant,
            LintModelSerializationStringAdapter(
                writer = { _, _, _ ->
                    writer
                }
            )
        )
        return writer.toString()
    }

    private class LintModelSerializationStringAdapter(
        override val root: File? = null,
        private val reader: (TargetFile, String, String) -> Reader = { _, _, _ -> StringReader("<error>") },
        private val writer: (TargetFile, String, String) -> Writer = { _, _, _ -> StringWriter() },
        override val pathVariables: LintModelPathVariables = emptyList()
    ) : LintModelSerialization.LintModelSerializationAdapter {
        override fun file(target: TargetFile, variantName: String, artifactName: String): File {
            return if (variantName.isNotEmpty())
                File("variant-$variantName.xml")
            else
                File("testfile.xml")
        }

        override fun getReader(target: TargetFile, variantName: String, artifactName: String) =
            reader(target, variantName, artifactName)

        override fun getWriter(target: TargetFile, variantName: String, artifactName: String) =
            writer(target, variantName, artifactName)
    }

    private fun assertNoTextNodes(element: Element) {
        var curr = element.firstChild
        while (curr != null) {
            val nodeType = curr.nodeType
            if (nodeType == Node.ELEMENT_NODE) {
                assertNoTextNodes(curr as Element)
            } else if (nodeType == Node.TEXT_NODE) {
                val text = curr.nodeValue
                if (!text.isBlank()) {
                    fail("Found unexpected text " + text.trim { it <= ' ' } + " in the document")
                }
            }
            curr = curr.nextSibling
        }
    }

    private fun File.createDirectories(): File {
        Files.createDirectories(toPath())
        return this
    }
}
private const val ROOT: String = "\uFF04ROOT"

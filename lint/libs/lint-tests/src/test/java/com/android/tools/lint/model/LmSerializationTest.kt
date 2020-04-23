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

import com.android.tools.lint.checks.infrastructure.GradleModelMocker
import com.android.tools.lint.checks.infrastructure.GradleModelMockerTest
import com.android.utils.XmlUtils
import com.google.common.truth.Truth.assertThat
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

class LmSerializationTest {
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
            """
            <lint-module
                format="1"
                dir=""
                name="test_project"
                type="APP"
                maven="com.android.tools.demo:test_project:"
                gradle="4.0.0-beta01"
                buildFolder="build"
                javaSourceLevel="1.7"
                compileTarget="android-25"
                neverShrinking="true">
              <buildFeatures
                  viewBinding="true"/>
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
            mapOf(
                "freeBetaDebug" to """
                <variant
                    name="freeBetaDebug"
                    versionCode="2"
                    versionName="MyName"
                    minSdkVersion="5"
                    targetSdkVersion="16"
                    debuggable="true"
                    resourceConfigurations="mdpi,hdpi,en,nodpi">
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
                      name="_main_"
                      classFolders="main-classes"
                      applicationId="com.android.tools.test">
                    <dependencies>
                      <library
                          jars="build/intermediates/exploded-aar/com.android.support/appcompat-v7/25.0.1/jars/classes.jar"
                          requested="com.android.support:appcompat-v7:25.0.1"
                          resolved="com.android.support:appcompat-v7:25.0.1"
                          folder="build/intermediates/exploded-aar/com.android.support/appcompat-v7/25.0.1"
                          manifest="AndroidManifest.xml"
                          resFolder="res"
                          assetsFolder="assets"
                          lintJar="lint.jar"
                          publicResources="public.txt"
                          symbolFile="R.txt"
                          externalAnnotations="annotations.zip"
                          proguardRules="proguard.pro">
                        <dependencies>
                          <library
                              jars="build/intermediates/exploded-aar/com.android.support/support-v4/25.0.1/jars/classes.jar"
                              requested="com.android.support:support-v4:25.0.1"
                              resolved="com.android.support:support-v4:25.0.1"
                              folder="build/intermediates/exploded-aar/com.android.support/support-v4/25.0.1"
                              manifest="AndroidManifest.xml"
                              resFolder="res"
                              assetsFolder="assets"
                              lintJar="lint.jar"
                              publicResources="public.txt"
                              symbolFile="R.txt"
                              externalAnnotations="annotations.zip"
                              proguardRules="proguard.pro">
                            <dependencies>
                              <library
                                  jars="build/intermediates/exploded-aar/com.android.support/support-compat/25.0.1/jars/classes.jar"
                                  requested="com.android.support:support-compat:25.0.1"
                                  resolved="com.android.support:support-compat:25.0.1"
                                  folder="build/intermediates/exploded-aar/com.android.support/support-compat/25.0.1"
                                  manifest="AndroidManifest.xml"
                                  resFolder="res"
                                  assetsFolder="assets"
                                  lintJar="lint.jar"
                                  publicResources="public.txt"
                                  symbolFile="R.txt"
                                  externalAnnotations="annotations.zip"
                                  proguardRules="proguard.pro"/>
                              <library
                                  jars="build/intermediates/exploded-aar/com.android.support/support-media-compat/25.0.1/jars/classes.jar"
                                  requested="com.android.support:support-media-compat:25.0.1"
                                  resolved="com.android.support:support-media-compat:25.0.1"
                                  folder="build/intermediates/exploded-aar/com.android.support/support-media-compat/25.0.1"
                                  manifest="AndroidManifest.xml"
                                  resFolder="res"
                                  assetsFolder="assets"
                                  lintJar="lint.jar"
                                  publicResources="public.txt"
                                  symbolFile="R.txt"
                                  externalAnnotations="annotations.zip"
                                  proguardRules="proguard.pro">
                                <dependencies>
                                  <library
                                      jars="build/intermediates/exploded-aar/com.android.support/support-compat/25.0.1/jars/classes.jar"
                                      requested="com.android.support:support-compat:25.0.1"
                                      resolved="com.android.support:support-compat:25.0.1"
                                      folder="build/intermediates/exploded-aar/com.android.support/support-compat/25.0.1"
                                      manifest="AndroidManifest.xml"
                                      resFolder="res"
                                      assetsFolder="assets"
                                      lintJar="lint.jar"
                                      publicResources="public.txt"
                                      symbolFile="R.txt"
                                      externalAnnotations="annotations.zip"
                                      proguardRules="proguard.pro"/>
                                </dependencies>
                              </library>
                              <library
                                  jars="build/intermediates/exploded-aar/com.android.support/support-core-utils/25.0.1/jars/classes.jar"
                                  requested="com.android.support:support-core-utils:25.0.1"
                                  resolved="com.android.support:support-core-utils:25.0.1"
                                  folder="build/intermediates/exploded-aar/com.android.support/support-core-utils/25.0.1"
                                  manifest="AndroidManifest.xml"
                                  resFolder="res"
                                  assetsFolder="assets"
                                  lintJar="lint.jar"
                                  publicResources="public.txt"
                                  symbolFile="R.txt"
                                  externalAnnotations="annotations.zip"
                                  proguardRules="proguard.pro">
                                <dependencies>
                                  <library
                                      jars="build/intermediates/exploded-aar/com.android.support/support-compat/25.0.1/jars/classes.jar"
                                      requested="com.android.support:support-compat:25.0.1"
                                      resolved="com.android.support:support-compat:25.0.1"
                                      folder="build/intermediates/exploded-aar/com.android.support/support-compat/25.0.1"
                                      manifest="AndroidManifest.xml"
                                      resFolder="res"
                                      assetsFolder="assets"
                                      lintJar="lint.jar"
                                      publicResources="public.txt"
                                      symbolFile="R.txt"
                                      externalAnnotations="annotations.zip"
                                      proguardRules="proguard.pro"/>
                                </dependencies>
                              </library>
                              <library
                                  jars="build/intermediates/exploded-aar/com.android.support/support-core-ui/25.0.1/jars/classes.jar"
                                  requested="com.android.support:support-core-ui:25.0.1"
                                  resolved="com.android.support:support-core-ui:25.0.1"
                                  folder="build/intermediates/exploded-aar/com.android.support/support-core-ui/25.0.1"
                                  manifest="AndroidManifest.xml"
                                  resFolder="res"
                                  assetsFolder="assets"
                                  lintJar="lint.jar"
                                  publicResources="public.txt"
                                  symbolFile="R.txt"
                                  externalAnnotations="annotations.zip"
                                  proguardRules="proguard.pro">
                                <dependencies>
                                  <library
                                      jars="build/intermediates/exploded-aar/com.android.support/support-compat/25.0.1/jars/classes.jar"
                                      requested="com.android.support:support-compat:25.0.1"
                                      resolved="com.android.support:support-compat:25.0.1"
                                      folder="build/intermediates/exploded-aar/com.android.support/support-compat/25.0.1"
                                      manifest="AndroidManifest.xml"
                                      resFolder="res"
                                      assetsFolder="assets"
                                      lintJar="lint.jar"
                                      publicResources="public.txt"
                                      symbolFile="R.txt"
                                      externalAnnotations="annotations.zip"
                                      proguardRules="proguard.pro"/>
                                </dependencies>
                              </library>
                              <library
                                  jars="build/intermediates/exploded-aar/com.android.support/support-fragment/25.0.1/jars/classes.jar"
                                  requested="com.android.support:support-fragment:25.0.1"
                                  resolved="com.android.support:support-fragment:25.0.1"
                                  folder="build/intermediates/exploded-aar/com.android.support/support-fragment/25.0.1"
                                  manifest="AndroidManifest.xml"
                                  resFolder="res"
                                  assetsFolder="assets"
                                  lintJar="lint.jar"
                                  publicResources="public.txt"
                                  symbolFile="R.txt"
                                  externalAnnotations="annotations.zip"
                                  proguardRules="proguard.pro">
                                <dependencies>
                                  <library
                                      jars="build/intermediates/exploded-aar/com.android.support/support-compat/25.0.1/jars/classes.jar"
                                      requested="com.android.support:support-compat:25.0.1"
                                      resolved="com.android.support:support-compat:25.0.1"
                                      folder="build/intermediates/exploded-aar/com.android.support/support-compat/25.0.1"
                                      manifest="AndroidManifest.xml"
                                      resFolder="res"
                                      assetsFolder="assets"
                                      lintJar="lint.jar"
                                      publicResources="public.txt"
                                      symbolFile="R.txt"
                                      externalAnnotations="annotations.zip"
                                      proguardRules="proguard.pro"/>
                                  <library
                                      jars="build/intermediates/exploded-aar/com.android.support/support-media-compat/25.0.1/jars/classes.jar"
                                      requested="com.android.support:support-media-compat:25.0.1"
                                      resolved="com.android.support:support-media-compat:25.0.1"
                                      folder="build/intermediates/exploded-aar/com.android.support/support-media-compat/25.0.1"
                                      manifest="AndroidManifest.xml"
                                      resFolder="res"
                                      assetsFolder="assets"
                                      lintJar="lint.jar"
                                      publicResources="public.txt"
                                      symbolFile="R.txt"
                                      externalAnnotations="annotations.zip"
                                      proguardRules="proguard.pro"/>
                                  <library
                                      jars="build/intermediates/exploded-aar/com.android.support/support-core-ui/25.0.1/jars/classes.jar"
                                      requested="com.android.support:support-core-ui:25.0.1"
                                      resolved="com.android.support:support-core-ui:25.0.1"
                                      folder="build/intermediates/exploded-aar/com.android.support/support-core-ui/25.0.1"
                                      manifest="AndroidManifest.xml"
                                      resFolder="res"
                                      assetsFolder="assets"
                                      lintJar="lint.jar"
                                      publicResources="public.txt"
                                      symbolFile="R.txt"
                                      externalAnnotations="annotations.zip"
                                      proguardRules="proguard.pro"/>
                                  <library
                                      jars="build/intermediates/exploded-aar/com.android.support/support-core-utils/25.0.1/jars/classes.jar"
                                      requested="com.android.support:support-core-utils:25.0.1"
                                      resolved="com.android.support:support-core-utils:25.0.1"
                                      folder="build/intermediates/exploded-aar/com.android.support/support-core-utils/25.0.1"
                                      manifest="AndroidManifest.xml"
                                      resFolder="res"
                                      assetsFolder="assets"
                                      lintJar="lint.jar"
                                      publicResources="public.txt"
                                      symbolFile="R.txt"
                                      externalAnnotations="annotations.zip"
                                      proguardRules="proguard.pro"/>
                                </dependencies>
                              </library>
                            </dependencies>
                          </library>
                          <library
                              jars="build/intermediates/exploded-aar/com.android.support/support-vector-drawable/25.0.1/jars/classes.jar"
                              requested="com.android.support:support-vector-drawable:25.0.1"
                              resolved="com.android.support:support-vector-drawable:25.0.1"
                              folder="build/intermediates/exploded-aar/com.android.support/support-vector-drawable/25.0.1"
                              manifest="AndroidManifest.xml"
                              resFolder="res"
                              assetsFolder="assets"
                              lintJar="lint.jar"
                              publicResources="public.txt"
                              symbolFile="R.txt"
                              externalAnnotations="annotations.zip"
                              proguardRules="proguard.pro">
                            <dependencies>
                              <library
                                  jars="build/intermediates/exploded-aar/com.android.support/support-compat/25.0.1/jars/classes.jar"
                                  requested="com.android.support:support-compat:25.0.1"
                                  resolved="com.android.support:support-compat:25.0.1"
                                  folder="build/intermediates/exploded-aar/com.android.support/support-compat/25.0.1"
                                  manifest="AndroidManifest.xml"
                                  resFolder="res"
                                  assetsFolder="assets"
                                  lintJar="lint.jar"
                                  publicResources="public.txt"
                                  symbolFile="R.txt"
                                  externalAnnotations="annotations.zip"
                                  proguardRules="proguard.pro"/>
                            </dependencies>
                          </library>
                          <library
                              jars="build/intermediates/exploded-aar/com.android.support/animated-vector-drawable/25.0.1/jars/classes.jar"
                              requested="com.android.support:animated-vector-drawable:25.0.1"
                              resolved="com.android.support:animated-vector-drawable:25.0.1"
                              folder="build/intermediates/exploded-aar/com.android.support/animated-vector-drawable/25.0.1"
                              manifest="AndroidManifest.xml"
                              resFolder="res"
                              assetsFolder="assets"
                              lintJar="lint.jar"
                              publicResources="public.txt"
                              symbolFile="R.txt"
                              externalAnnotations="annotations.zip"
                              proguardRules="proguard.pro">
                            <dependencies>
                              <library
                                  jars="build/intermediates/exploded-aar/com.android.support/support-vector-drawable/25.0.1/jars/classes.jar"
                                  requested="com.android.support:support-vector-drawable:25.0.1"
                                  resolved="com.android.support:support-vector-drawable:25.0.1"
                                  folder="build/intermediates/exploded-aar/com.android.support/support-vector-drawable/25.0.1"
                                  manifest="AndroidManifest.xml"
                                  resFolder="res"
                                  assetsFolder="assets"
                                  lintJar="lint.jar"
                                  publicResources="public.txt"
                                  symbolFile="R.txt"
                                  externalAnnotations="annotations.zip"
                                  proguardRules="proguard.pro"/>
                            </dependencies>
                          </library>
                        </dependencies>
                      </library>
                      <library
                          jars="build/intermediates/exploded-aar/com.android.support.constraint/constraint-layout/1.0.0-beta3/jars/classes.jar"
                          requested="com.android.support.constraint:constraint-layout:1.0.0-beta3"
                          resolved="com.android.support.constraint:constraint-layout:1.0.0-beta3"
                          folder="build/intermediates/exploded-aar/com.android.support.constraint/constraint-layout/1.0.0-beta3"
                          manifest="AndroidManifest.xml"
                          resFolder="res"
                          assetsFolder="assets"
                          lintJar="lint.jar"
                          publicResources="public.txt"
                          symbolFile="R.txt"
                          externalAnnotations="annotations.zip"
                          proguardRules="proguard.pro"/>
                      <library
                          jars="caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-jdk7/1.3.09c6ef172e8de35fd8d4d8783e4821e57cdef7445/kotlin-stdlib-jdk7-1.3.0.jar"
                          requested="org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.0"
                          resolved="org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.0">
                        <dependencies>
                          <library
                              jars="caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.3.09c6ef172e8de35fd8d4d8783e4821e57cdef7445/kotlin-stdlib-1.3.0.jar"
                              requested="org.jetbrains.kotlin:kotlin-stdlib:1.3.0"
                              resolved="org.jetbrains.kotlin:kotlin-stdlib:1.3.0">
                            <dependencies>
                              <library
                                  jars="caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-common/1.3.09c6ef172e8de35fd8d4d8783e4821e57cdef7445/kotlin-stdlib-common-1.3.0.jar"
                                  requested="org.jetbrains.kotlin:kotlin-stdlib-common:1.3.0"
                                  resolved="org.jetbrains.kotlin:kotlin-stdlib-common:1.3.0"/>
                              <library
                                  jars="caches/modules-2/files-2.1/org.jetbrains/annotations/13.09c6ef172e8de35fd8d4d8783e4821e57cdef7445/annotations-13.0.jar"
                                  requested="org.jetbrains:annotations:13.0"
                                  resolved="org.jetbrains:annotations:13.0"/>
                            </dependencies>
                          </library>
                        </dependencies>
                      </library>
                      <library
                          jars="caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.3.09c6ef172e8de35fd8d4d8783e4821e57cdef7445/kotlin-stdlib-1.3.0.jar"
                          requested="org.jetbrains.kotlin:kotlin-stdlib:1.3.0"
                          resolved="org.jetbrains.kotlin:kotlin-stdlib:1.3.0"/>
                      <library
                          jars="caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-common/1.3.09c6ef172e8de35fd8d4d8783e4821e57cdef7445/kotlin-stdlib-common-1.3.0.jar"
                          requested="org.jetbrains.kotlin:kotlin-stdlib-common:1.3.0"
                          resolved="org.jetbrains.kotlin:kotlin-stdlib-common:1.3.0"/>
                    </dependencies>
                  </mainArtifact>
                  <androidTestArtifact
                      name="_android_test_"
                      classFolders="instrumentation-classes"
                      applicationId="com.android.tools.test">
                    <dependencies>
                    </dependencies>
                  </androidTestArtifact>
                  <testArtifact
                      name="_unit_test_"
                      classFolders="test-classes">
                    <dependencies>
                    </dependencies>
                  </testArtifact>
                </variant>
              """,
                "betaDebug" to """
                <variant
                    name="betaDebug"
                    versionCode="0"
                    minSdkVersion="5"
                    targetSdkVersion="16"
                    debuggable="true">
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
                      name="_main_"
                      classFolders="main-classes"
                      applicationId="com.android.tools.test">
                    <dependencies>
                    </dependencies>
                  </mainArtifact>
                </variant>
              """,
                "normalDebug" to """
                <variant
                    name="normalDebug"
                    versionCode="0"
                    minSdkVersion="5"
                    targetSdkVersion="16"
                    debuggable="true">
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
                      name="_main_"
                      classFolders="main-classes"
                      applicationId="com.android.tools.test">
                    <dependencies>
                    </dependencies>
                  </mainArtifact>
                </variant>
              """,
                "freeDebug" to """
                <variant
                    name="freeDebug"
                    versionCode="0"
                    minSdkVersion="5"
                    targetSdkVersion="16"
                    debuggable="true">
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
                      name="_main_"
                      classFolders="main-classes"
                      applicationId="com.android.tools.test">
                    <dependencies>
                    </dependencies>
                  </mainArtifact>
                </variant>
              """,
                "paidDebug" to """
                <variant
                    name="paidDebug"
                    versionCode="0"
                    minSdkVersion="5"
                    targetSdkVersion="16"
                    debuggable="true">
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
                      name="_main_"
                      classFolders="main-classes"
                      applicationId="com.android.tools.test">
                    <dependencies>
                    </dependencies>
                  </mainArtifact>
                </variant>
              """,
                "betaRelease" to """
                <variant
                    name="betaRelease"
                    versionCode="0"
                    minSdkVersion="5"
                    targetSdkVersion="16">
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
                      name="_main_"
                      classFolders="main-classes"
                      applicationId="com.android.tools.test">
                    <dependencies>
                    </dependencies>
                  </mainArtifact>
                </variant>
              """,
                "normalRelease" to """
                <variant
                    name="normalRelease"
                    versionCode="0"
                    minSdkVersion="5"
                    targetSdkVersion="16">
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
                      name="_main_"
                      classFolders="main-classes"
                      applicationId="com.android.tools.test">
                    <dependencies>
                    </dependencies>
                  </mainArtifact>
                </variant>
              """,
                "freeRelease" to """
                <variant
                    name="freeRelease"
                    versionCode="0"
                    minSdkVersion="5"
                    targetSdkVersion="16">
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
                      name="_main_"
                      classFolders="main-classes"
                      applicationId="com.android.tools.test">
                    <dependencies>
                    </dependencies>
                  </mainArtifact>
                </variant>
              """,
                "paidRelease" to """
                <variant
                    name="paidRelease"
                    versionCode="0"
                    minSdkVersion="5"
                    targetSdkVersion="16">
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
                      name="_main_"
                      classFolders="main-classes"
                      applicationId="com.android.tools.test">
                    <dependencies>
                    </dependencies>
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

    // ----------------------------------------------------------------------------------
    // Test infrastructure below this line
    // ----------------------------------------------------------------------------------

    private fun tryParse(
        @Language("XML") xml: String,
        expectedErrors: String? = null
    ) {
        try {
            val reader = StringReader(xml)
            LmSerialization.read(LmSerializationStringAdapter(reader = { reader }))
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
        @Language("XML") expectedModuleXml: String,
        expectedVariantXml: Map<String, String>
    ) {
        val project = mocker.project
        val dir = mocker.projectDir

        val path = mocker.projectDir.path
        fun String.cleanup() = replace(path, "＄ROOT").trim()

        // Test lint model stuff
        val module = LmFactory().create(project, dir)
        val xml = writeModule(module)

        val moduleXml = xml[""]!!
        assertThat(moduleXml.cleanup()).isEqualTo(expectedModuleXml.trimIndent().trim())

        // Make sure the XML is valid
        assertValidXml(moduleXml)

        for (variant in module.variants) {
            val variantXml: String = xml[variant.name] ?: continue
            assertValidXml(variantXml)
            val expected = expectedVariantXml[variant.name] ?: continue
            assertThat(variantXml.cleanup()).isEqualTo(expected.trimIndent().trim())
        }

        val newModule = LmSerialization.read(LmSerializationStringAdapter(reader = { variantName ->
            val contents = xml[variantName]!!
            StringReader(contents)
        }))
        val newXml = writeModule(newModule)
        for ((variantName, contents) in xml) {
            assertEquals(
                "XML parsed and written back out does not match original",
                contents, newXml[variantName]
            )
        }

        // Also check using relative path: make sure that if we strip out the project prefix
        // and read it back with a root specified, paths are resolved properly
        val newModule2 = LmSerialization.read(
            LmSerializationStringAdapter(
                reader = { variantName ->
                    val s = xml[variantName]!!
                    val relative = s.replace(mocker.projectDir.path + File.separator, "")
                    StringReader(relative)
                }), variantNames = null
        )
        val newXml2 = writeModule(newModule2)
        for ((variantName, contents) in xml) {
            assertEquals(
                "XML parsed and written back out does not match original",
                contents, newXml2[variantName]
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

    private fun writeModule(module: LmModule): Map<String, String> {
        val map = mutableMapOf<String, StringWriter>()
        LmSerialization.write(module, LmSerializationStringAdapter(writer = { variantName ->
            map[variantName] ?: StringWriter().also { map[variantName] = it }
        }))
        return map.mapValues {
            it.value.toString()
        }
    }

    private fun writeVariant(variant: LmVariant): String {
        val writer = StringWriter()
        LmSerialization.write(variant, LmSerializationStringAdapter(writer = { writer }))
        return writer.toString()
    }

    private class LmSerializationStringAdapter(
        private val root: File? = null,
        private val reader: (String) -> Reader = { StringReader("<error>") },
        private val writer: (String) -> Writer = { StringWriter() }
    ) : LmSerialization.LmSerializationAdapter {
        override fun root(): File? = root
        override fun file(variantName: String?): File? {
            return if (variantName != null)
                File("variant-$variantName.xml")
            else
                File("testfile.xml")
        }

        override fun getReader(variantName: String?): Reader = reader(variantName ?: "")
        override fun getWriter(variantName: String?): Writer = writer(variantName ?: "")
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
}

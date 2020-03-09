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

package com.android.build.gradle.internal.manifest

import com.android.build.gradle.internal.manifest.ManifestData.AndroidTarget
import com.google.common.truth.Truth
import org.junit.Test

/**
 * Test of [LazyManifestParser] that focuses on properties values
 */
class LazyManifestParserPropertiesTest : LazyManifestParserBaseTest() {

    @Test
    fun `basic manifest values`() {
        given {
            manifest = """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="com.android.tests.builder.core"
          split="com.android.tests.builder.core.split"
          android:versionCode="1"
          android:versionName="1.0">
    <uses-sdk android:minSdkVersion="21"
              android:targetSdkVersion="25"/>
    <instrumentation android:functionalTest= "true"
                     android:handleProfiling= "false"
                     android:label="instrumentation_label"
                     android:name="com.android.tests.builder.core.instrumentation.name"
                     android:targetProcesses="*" />
    <application android:label="app_name" android:icon="icon"
                 android:extractNativeLibs="true"
                 android:useEmbeddedDex="true">
        <activity android:name=".Main"
                  android:label="app_name">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest> 
""".trimIndent()
        }

        expect {
            packageName = "com.android.tests.builder.core"
            split = "com.android.tests.builder.core.split"
            minSdkVersion = AndroidTarget(apiLevel = 21, codeName = null)
            targetSdkVersion = AndroidTarget(apiLevel = 25, codeName = null)
            instrumentationRunner = "com.android.tests.builder.core.instrumentation.name"
            testLabel = "instrumentation_label"
            functionalTest = true
            handleProfiling = false
            extractNativeLibs = true
            useEmbeddedDex = true
        }
    }

    @Test
    fun `codename minSdkVersion`() {
        given {
            manifest ="""
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="com.android.tests.builder.core">
    <uses-sdk android:minSdkVersion="Q"
              android:targetSdkVersion="25"/>
</manifest> 
""".trimIndent()
        }

        expect {
            minSdkVersion = AndroidTarget(apiLevel = null, codeName = "Q")
        }
    }

    @Test
    fun `codename targetSdkVersion`() {
        given {
            manifest ="""
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="com.android.tests.builder.core">
    <uses-sdk android:minSdkVersion="21"
              android:targetSdkVersion="Q"/>
</manifest> 
""".trimIndent()
        }

        expect {
            targetSdkVersion = AndroidTarget(apiLevel = null, codeName = "Q")
        }
    }

    // ---------------------------------------------------------------------------------------------

    override fun compareResult(
        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE") expectedMaybe: ManifestData?,
        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE") actualMaybe: ManifestData?,
        given: GivenBuilder
    ) {
        val expected = expectedMaybe ?: throw RuntimeException("expected is null")
        val actual = actualMaybe ?: throw RuntimeException("actual is null")

        // only check the cases where expected value is not null

        expected.packageName?.let {
            Truth.assertThat(actual.packageName).named("packageName").isEqualTo(it)
        }

        expected.split?.let {
            Truth.assertThat(actual.split).named("split").isEqualTo(it)
        }

        expected.minSdkVersion?.let {
            Truth.assertThat(actual.minSdkVersion).named("minSdkVersion").isEqualTo(it)
        }

        expected.targetSdkVersion?.let {
            Truth.assertThat(actual.targetSdkVersion).named("targetSdkVersion").isEqualTo(it)
        }

        expected.instrumentationRunner?.let {
            Truth.assertThat(actual.instrumentationRunner).named("instrumentationRunner").isEqualTo(it)
        }

        expected.testLabel?.let {
            Truth.assertThat(actual.testLabel).named("testLabel").isEqualTo(it)
        }

        expected.functionalTest?.let {
            Truth.assertThat(actual.functionalTest).named("functionalTest").isEqualTo(it)
        }

        expected.handleProfiling?.let {
            Truth.assertThat(actual.handleProfiling).named("handleProfiling").isEqualTo(it)
        }

        expected.extractNativeLibs?.let {
            Truth.assertThat(actual.extractNativeLibs).named("extractNativeLibs").isEqualTo(it)
        }

        expected.useEmbeddedDex?.let {
            Truth.assertThat(actual.useEmbeddedDex).named("useEmbeddedDex").isEqualTo(it)
        }
    }
}
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

package com.android.build.gradle.integration.bundle

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.AabSubject.Companion.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Test for the legacy multidex dynamic app. */
class DynamicAppLegacyMultidexTest {

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("multiDex")
        .create()

    private val forcedPrimaryDexClasses = listOf(
        "com/android/tests/basic/Used",
        "com/android/tests/basic/manymethods/Big001",
        "com/android/tests/basic/manymethods/Big047",
        "com/android/tests/basic/manymethods/Big070"
    )

    /** Add a dynamic feature that the multidex project includes. */
    @Before
    fun before() {
        project.file("settings.gradle").writeText(
            """
            include ":", ":feature"
        """.trimIndent()
        )

        project.buildFile.appendText(
            """
            |android {
            |  dynamicFeatures = ["feature"]
            |  buildTypes {
            |    r8 {
            |      multiDexKeepFile file('multiDex.txt')
            |    }
            |  }
            |}
        """.trimMargin()
        )
        project.file("multiDex.txt").writeText(
            forcedPrimaryDexClasses.joinToString(separator = System.lineSeparator()) { "$it.class" }
        )
        project.file("proguard-android.txt").writeText("-keep class ** { *; }")

        project.file("feature/build.gradle").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
            |apply plugin: 'com.android.dynamic-feature'
            |
            |android {
            |  compileSdkVersion rootProject.latestCompileSdk
            |  defaultConfig {
            |    minSdkVersion 18
            |  }
            |  flavorDimensions 'foo'
            |  productFlavors {
            |    ics {
            |      minSdkVersion rootProject.supportLibMinSdk
            |    }
            |    lollipop {
            |      minSdkVersion 21
            |    }
            |  }
            |  buildTypes {
            |    r8 {
            |      initWith debug
            |    }
            |  }
            |}
            |dependencies {
            |  implementation project(':')
            |}
        """.trimMargin()
            )
        }

        project.file("feature/src/main/AndroidManifest.xml").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                |<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                |         xmlns:dist="http://schemas.android.com/apk/distribution"
                |    package="foo.feature">
                |    <dist:module dist:onDemand="true" dist:title="@string/app_name">
                |        <dist:fusing dist:include="false" />
                |    </dist:module>
                |    <application />
                |</manifest>
            """.trimMargin()
            )
        }

        project.file("feature/src/main/java/com/Hellow.java").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                package com;

                public class Hellow { }
            """.trimMargin()
            )
        }
    }

    /** Regression test for b/120845002 */
    @Test
    fun testPrimaryDexContainsAllNecessaryClasses() {
        project.executor().run("bundleIcsR8")

        project.getBundle(GradleTestProject.ApkType.of("icsR8", false)).use { aab ->
            // http://b/163114632
            multiDexSupportLibClasses.filter { it != "Landroid/support/multidex/MultiDex\$V4;"}.forEach {
                    className -> assertThat(aab).containsMainClass("base", className)
            }
            forcedPrimaryDexClasses.map { "L$it;" }.forEach {
                    className -> assertThat(aab).containsMainClass("base", className)
            }
        }
    }
}

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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.DESUGAR_DEPENDENCY_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.testutils.apk.AndroidArchive
import com.android.testutils.apk.Dex

import org.junit.Rule
import org.junit.Test
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class CoreLibraryDesugarDynamicFeatureTest {

    private val build = MultiModuleTestProject.builder().apply {
        val app = MinimalSubProject.app("com.example.app")
            .appendToBuild("""
                    android {
                        defaultConfig {
                            minSdkVersion 21
                        }
                        compileOptions {
                            sourceCompatibility JavaVersion.VERSION_1_8
                            targetCompatibility JavaVersion.VERSION_1_8
                            coreLibraryDesugaringEnabled true
                        }
                        dynamicFeatures = [":dynamicFeature"]
                    }
                    dependencies {
                        coreLibraryDesugaring "$DESUGAR_DEPENDENCY"
                    }
                """.trimMargin()
            )
            .withFile(
                "src/main/java/com/example/app/MainModuleClass.java",
                """package com.example.app;
                    |
                    |public class MainModuleClass {
                    |    public static int getThree() {
                    |        return 3;
                    |    };
                    |}""".trimMargin())
            .withFile(
                "src/main/res/values/strings.xml", """
                    |<resources>
                    |    <string name="df_title">Dynamic Feature Title</string>
                    |    <string name="app_title">App Title</string>
                    |</resources>
                """.trimMargin())
            .apply { replaceFile(
                TestSourceFile("src/main/AndroidManifest.xml",
                    """<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    |        xmlns:dist="http://schemas.android.com/apk/distribution"
                    |        package="com.example.app">
                    |    <dist:module dist:title="@string/app_title">
                    |    </dist:module>
                    |    <application />
                    |</manifest>""".trimMargin())
            )}
        val dynamicFeature = MinimalSubProject.dynamicFeature("com.example.app.dynamic.feature")
            .appendToBuild("""
                    android {
                        defaultConfig {
                            minSdkVersion 21
                        }
                        compileOptions {
                            sourceCompatibility JavaVersion.VERSION_1_8
                            targetCompatibility JavaVersion.VERSION_1_8
                            coreLibraryDesugaringEnabled true
                        }
                    }
                    dependencies {
                        coreLibraryDesugaring "$DESUGAR_DEPENDENCY"
                    }
            """.trimIndent())
            .apply { replaceFile(TestSourceFile("src/main/AndroidManifest.xml",
                """<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    |        xmlns:dist="http://schemas.android.com/apk/distribution"
                    |        package="com.example.app.dynamic.feature">
                    |    <dist:module dist:onDemand="true" dist:title="@string/df_title">
                    |        <dist:fusing dist:include="true" />
                    |    </dist:module>
                    |    <application />
                    |</manifest>""".trimMargin()))}
            .withFile("src/main/java/com/example/dynamic/feature/FeatureModuleClass.java",
                """package com.example.dynamic.feature;
                    |
                    |public class FeatureModuleClass {
                    |    public static int getFour() { return 4; }
                    |}""".trimMargin())

        subproject(":app", app)
        subproject(":dynamicFeature", dynamicFeature)
        dependency(dynamicFeature, app)
    }.build()

    private val desugarClass = "Lj$/util/stream/Stream;"

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(build).create()

    @Test
    fun testNoDesugarLibDexInFeatureModule() {
        project.executor().run("assembleDebug")
        val appApk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG)
        val featureApk = project.getSubproject(":dynamicFeature").getApk(GradleTestProject.ApkType.DEBUG)
        assertNotNull(getDexWithSpecificClass(desugarClass, appApk.allDexes))
        assertNull(getDexWithSpecificClass(desugarClass, featureApk.allDexes))
    }

    private fun getDexWithSpecificClass(className: String, dexes: Collection<Dex>) : Dex? =
        dexes.find {
            AndroidArchive.checkValidClassName(className)
            it.classes.keys.contains(className)
        }

    companion object {
        private const val DESUGAR_DEPENDENCY
                = "com.android.tools:desugar_jdk_libs:$DESUGAR_DEPENDENCY_VERSION"
    }
}
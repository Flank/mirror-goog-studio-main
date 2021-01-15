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

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.DESUGAR_DEPENDENCY_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.TestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.apk.AndroidArchive
import com.android.testutils.apk.Dex
import com.android.testutils.generateAarWithContent
import com.android.testutils.truth.DexSubject
import com.android.utils.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

class CoreLibraryDesugarDynamicFeatureTest {

    private val streamClass = "Lj$/util/stream/Stream;"
    private val monthClass = "Lj$/time/Month;"
    private val yearClass = "Lj$/time/Year;"
    private val localTimeClass = "Lj$/time/LocalTime;"

    private lateinit var app: GradleTestProject
    private lateinit var feature: GradleTestProject

    private val aar = generateAarWithContent(
        packageName = "com.example.myaar",
        mainJar = TestInputsGenerator.jarWithClasses(listOf(LocalTimeClass::class.java))
    )

    private val mavenRepo = MavenRepoGenerator(
        listOf(
            MavenRepoGenerator.Library("com.example:myaar:1", "aar", aar)
        )
    )

    @get:Rule
    val project = GradleTestProject.builder()
        .withAdditionalMavenRepo(mavenRepo)
        .fromTestApp(setUpTestProject())
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.WARN)
        // http://b/149978740
        .addGradleProperties("org.gradle.unsafe.configuration-cache.max-problems=1")
        .create()

    private fun setUpTestProject(): TestProject {
        return MultiModuleTestProject.builder()
            .subproject(APP_MODULE, HelloWorldApp.forPluginWithMinSdkVersion("com.android.application",21))
            .subproject(DYNAMIC_FEATURE, setUpDynamicFeatureModule())
            .build()
    }

    @Before
    fun setUp() {
        app = project.getSubproject(APP_MODULE)
        feature = project.getSubproject(DYNAMIC_FEATURE)
        setUpBaseModule()
    }

    @Test
    fun testNoDesugarLibDexInFeatureModule() {
        project.executor().run("assembleDebug")
        val appApk = app.getApk(GradleTestProject.ApkType.DEBUG)
        val featureApk = feature.getApk(GradleTestProject.ApkType.DEBUG)
        assertNotNull(getDexWithSpecificClass(streamClass, appApk.allDexes))
        assertNull(getDexWithSpecificClass(streamClass, featureApk.allDexes))
    }

    @Test
    fun testKeepRulePublicationFromFeatureModule() {
        project.executor().run(":app:assembleRelease")
        val appApk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.RELEASE)
        val desugarLibDex = getDexWithSpecificClass(streamClass, appApk.allDexes)
            ?: fail("Failed to find the dex with class name $streamClass")
        DexSubject.assertThat(desugarLibDex).containsClasses(monthClass)
        DexSubject.assertThat(desugarLibDex).doesNotContainClasses(yearClass)
    }

    @Test
    fun testKeepRuleConsumptionForMinifyBuild() {
        TestFileUtils.searchAndReplace(
            FileUtils.join(app.mainSrcDir, "com/example/helloworld/HelloWorld.java"),
            "// onCreate",
            "useStreamInBase(); useMonthInDynamicFeature();"
        )
        app.buildFile.appendText("""

            android.buildTypes.debug.minifyEnabled = true
            android.buildTypes.release.minifyEnabled = true
        """.trimIndent())
        project.executor().run(":app:assembleDebug")
        var appApk = app.getApk(GradleTestProject.ApkType.DEBUG)
        var desugarLibDex = getDexWithSpecificClass(streamClass, appApk.allDexes)
            ?: fail("Failed to find the dex with class name $streamClass")
        DexSubject.assertThat(desugarLibDex).containsClasses(monthClass)
        DexSubject.assertThat(desugarLibDex).doesNotContainClasses(yearClass)

        project.executor().run("clean", ":app:assembleRelease")
        appApk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.RELEASE)
        desugarLibDex = getDexWithSpecificClass(streamClass, appApk.allDexes)
            ?: fail("Failed to find the dex with class name $streamClass")
        DexSubject.assertThat(desugarLibDex).containsClasses(monthClass)
        DexSubject.assertThat(desugarLibDex).doesNotContainClasses(yearClass)
    }

    @Test
    fun testKeepRuleFromDynamicFeatureExternalLib() {
        feature.buildFile.appendText("""

            dependencies {
                implementation 'com.example:myaar:1'
            }
        """.trimIndent())

        project.executor().run("assembleRelease")
        val appApk = app.getApk(GradleTestProject.ApkType.RELEASE)
        assertNotNull(getDexWithSpecificClass(localTimeClass, appApk.allDexes))
    }

    private fun getDexWithSpecificClass(className: String, dexes: Collection<Dex>) : Dex? =
        dexes.find {
            AndroidArchive.checkValidClassName(className)
            it.classes.keys.contains(className)
        }

    private fun setUpBaseModule() {
        TestFileUtils.appendToFile(
            app.buildFile,
            """
                android {
                    compileOptions {
                        sourceCompatibility JavaVersion.VERSION_1_8
                        targetCompatibility JavaVersion.VERSION_1_8
                        coreLibraryDesugaringEnabled true
                    }
                    dynamicFeatures = [":dynamicFeature"]
                }
                android.defaultConfig.multiDexEnabled = true
                dependencies {
                    coreLibraryDesugaring "$DESUGAR_DEPENDENCY"
                }
            """.trimIndent())

        TestFileUtils.addMethod(
            FileUtils.join(app.mainSrcDir,"com/example/helloworld/HelloWorld.java"),
            """
                /** A method uses Java Stream API and always returns "first" */
                public static String useStreamInBase() {
                    java.util.Collection<String> collection
                    = java.util.Arrays.asList("first", "second", "third");
                    java.util.stream.Stream<String> streamOfCollection = collection.stream();
                    return streamOfCollection.findFirst().get();
                }
            """.trimIndent())
        TestFileUtils.appendToFile(
            FileUtils.join(app.mainSrcDir, "com/example/helloworld/Clock.java"),
            """
                package com.example.helloworld;

                public interface Clock {
                    public void checkTime();
                }
            """.trimIndent()
        )
        TestFileUtils.addMethod(
            FileUtils.join(app.mainSrcDir, "com/example/helloworld/HelloWorld.java"),
            """
                /** A method uses service loader to access function of dynamic feature module */
                public void useMonthInDynamicFeature() {
                    java.util.ServiceLoader<com.example.helloworld.Clock> loader =
                    java.util.ServiceLoader.load(com.example.helloworld.Clock.class, com.example.helloworld.Clock.class.getClassLoader());
                    loader.iterator().next().checkTime();
                }
            """.trimIndent()
        )
    }

    private fun setUpDynamicFeatureModule() = MinimalSubProject.dynamicFeature(DYNAMIC_FEATURE_PKG)
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
                    android.defaultConfig.multiDexEnabled = true
                    dependencies {
                        coreLibraryDesugaring "$DESUGAR_DEPENDENCY"
                        implementation project('::app')
                    }
            """.trimIndent())
        .apply { replaceFile(TestSourceFile("src/main/AndroidManifest.xml",
            """<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    |        xmlns:dist="http://schemas.android.com/apk/distribution"
                    |        package="com.example.app.dynamic.feature">
                    |    <dist:module dist:onDemand="true">
                    |        <dist:fusing dist:include="true" />
                    |    </dist:module>
                    |    <application />
                    |</manifest>""".trimMargin()))}
        .withFile("src/main/java/com/example/dynamic/feature/ClockImpl.java",
            """package com.example.dynamic.feature;
                    |import java.time.Month;
                    |import com.example.helloworld.Clock;
                    |
                    |public class ClockImpl implements Clock {
                    |    public void checkTime() { Month month = Month.JUNE; }
                    |}""".trimMargin())
        .withFile("src/main/resources//META-INF/services/com.example.helloworld.Clock",
            """
                com.example.dynamic.feature.ClockImpl
            """.trimIndent())

    companion object {
        private const val APP_MODULE = ":app"
        private const val DYNAMIC_FEATURE = ":dynamicFeature"
        private const val DYNAMIC_FEATURE_PKG = "com.example.app.dynamic.feature"
        private const val DESUGAR_DEPENDENCY
                = "com.android.tools:desugar_jdk_libs:$DESUGAR_DEPENDENCY_VERSION"
    }
}

class LocalTimeClass {
    val time: LocalTime = LocalTime.MIDNIGHT
}

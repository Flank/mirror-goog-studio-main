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

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.Adb
import com.android.build.gradle.integration.common.fixture.DESUGAR_DEPENDENCY_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.LoggingLevel
import com.android.build.gradle.integration.common.fixture.TestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.JavaSourceFileBuilder
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.AbiMatcher
import com.android.build.gradle.integration.common.utils.AndroidVersionMatcher
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.desugar.resources.ClassWithDesugarApi
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.AssumeUtil.assumeNotWindows
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestInputsGenerator.jarWithClasses
import com.android.testutils.apk.AndroidArchive
import com.android.testutils.apk.Dex
import com.android.testutils.generateAarWithContent
import com.android.testutils.truth.DexClassSubject
import com.android.testutils.truth.DexSubject
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import java.io.File
import java.nio.file.Files
import kotlin.test.assertTrue
import kotlin.test.fail

class CoreLibraryDesugarTest {

    private val aar = generateAarWithContent(
        packageName = "com.example.myaar",
        mainJar = jarWithClasses(listOf(ClassWithDesugarApi::class.java))
    )

    private val mavenRepo = MavenRepoGenerator(
        listOf(
            MavenRepoGenerator.Library("com.example:myaar:1", "aar", aar)
        )
    )

    @get:Rule
    val project = GradleTestProject.builder().withAdditionalMavenRepo(mavenRepo).fromTestApp(setUpTestProject()).create()

    @get:Rule
    var adb = Adb()

    private lateinit var app: GradleTestProject
    private lateinit var library: GradleTestProject

    private val programClass = "Lcom/example/helloworld/HelloWorld;"
    private val usedDesugarClass = "Lj$/util/stream/Stream;"
    private val usedDesugarClass2 = "Lj$/time/Month;"
    private val usedDesugarClass3 = "Lj$/time/LocalTime;"
    private val unusedDesugarClass = "Lj$/time/Year;"

    private fun setUpTestProject(): TestProject {
        return MultiModuleTestProject.builder()
            .subproject(APP_MODULE, HelloWorldApp.forPluginWithMinSdkVersion("com.android.application",21))
            .subproject(LIBRARY_MODULE, MinimalSubProject.lib(LIBRARY_PACKAGE))
            .build()
    }

    @Before
    fun setUp() {
        app = project.getSubproject(APP_MODULE)
        library = project.getSubproject(LIBRARY_MODULE)

        TestFileUtils.appendToFile(
            app.buildFile,
            """
                android {
                    compileOptions {
                        sourceCompatibility JavaVersion.VERSION_1_8
                        targetCompatibility JavaVersion.VERSION_1_8
                        coreLibraryDesugaringEnabled true
                    }
                }
                android.defaultConfig.multiDexEnabled = true
                dependencies {
                    implementation project("$LIBRARY_MODULE")
                    coreLibraryDesugaring "$DESUGAR_DEPENDENCY"
                }
            """.trimIndent())

        TestFileUtils.addMethod(
            FileUtils.join(app.mainSrcDir,"com/example/helloworld/HelloWorld.java"),
            """
                /** A method uses Java Stream API and always returns "first" */
                public static String getText() {
                    java.util.Collection<String> collection
                    = java.util.Arrays.asList("first", "second", "third");
                    java.util.stream.Stream<String> streamOfCollection = collection.stream();
                    return streamOfCollection.findFirst().get();
                }
            """.trimIndent())

        TestFileUtils.addMethod(
            FileUtils.join(app.testDir, "src/androidTest/java/com/example/helloworld/HelloWorldTest.java"),
            """
                @Test
                public void testApiInvocation() {
                    Assert.assertEquals("first", HelloWorld.getText());
                }
            """.trimIndent()
        )

        TestFileUtils.appendToFile(
            library.buildFile,
            """
                android {
                    compileOptions {
                        sourceCompatibility JavaVersion.VERSION_1_8
                        targetCompatibility JavaVersion.VERSION_1_8
                        coreLibraryDesugaringEnabled true
                    }
                    android.defaultConfig.multiDexEnabled = true
                }
                dependencies {
                    coreLibraryDesugaring "$DESUGAR_DEPENDENCY"
                }
            """.trimIndent()
        )

        addSourceWithDesugarApiToLibraryModule()
    }


    /**
     * Check if Java 8 API(e.g. Stream) can be used on devices with Android API level 23 or below
     */
    @Test
    @Category(DeviceTests::class)
    @Ignore
    //TODO Currently hit some regression, re-enable when r8/d8 is stable for core library desugaring
    fun testApiInvocation() {
        val device = adb.getDevice(AndroidVersionMatcher.exactly(21), AbiMatcher.anyAbi())
        project.executor()
            .with(StringOption.DEVICE_POOL_SERIAL, device.serialNumber)
            .run("app:connectedDebugAndroidTest")
    }

    /**
     * Check if Java 8 API(e.g. Stream) is rewritten properly by D8
     */
    @Test
    fun testApiRewriting() {
        project.executor().run("app:assembleDebug")
        val apk = app.getApk(GradleTestProject.ApkType.DEBUG)
        val dex = getDexWithSpecificClass(programClass, apk.allDexes)
            ?: fail("Failed to find the dex with class name $programClass")
        DexClassSubject.assertThat(dex.classes[programClass])
            .hasMethodThatInvokes("getText", "Lj$/util/stream/Stream;->findFirst()Lj$/util/Optional;")
    }

    @Test
    fun testLintPassesIfDesugaringEnabled() {
        app.buildFile.appendText("""

            android.lintOptions.abortOnError = true
        """.trimIndent())
        project.executor().run("app:lintDebug")
    }

    @Test
    fun testLintFailsIfDesugaringDisabled() {
        app.buildFile.appendText("""

            android.compileOptions.coreLibraryDesugaringEnabled = false
            android.lintOptions.abortOnError = true
        """.trimIndent())
        val result = project.executor().expectFailure().run("app:lintDebug")
        assertThat(result.failureMessage).contains(
            "Call requires API level 24 (current min is 14): java.util.Collection#stream [NewApi]")
    }

    @Test
    fun testModelFetching() {
        val model = app.model().fetchAndroidProjects().rootBuildModelMap[":app"]
        Truth.assertThat(model!!.javaCompileOptions.isCoreLibraryDesugaringEnabled).isTrue()
    }

    @Test
    fun testKeepRulesGenerationFromAppProject() {
        assumeNotWindows() //b/145232747
        project.executor().run("app:assembleRelease")
        val out = InternalArtifactType.DESUGAR_LIB_PROJECT_KEEP_RULES.getOutputDir(app.buildDir)
        val expectedKeepRules = "-keep class j\$.util.Optional {\n" +
                "    java.lang.Object get();\n" +
                "}\n" +
                "-keep class j\$.util.Collection\$-EL {\n" +
                "    j\$.util.stream.Stream stream(java.util.Collection);\n" +
                "}\n" +
                "-keep class j\$.util.stream.Stream {\n" +
                "    j\$.util.Optional findFirst();\n" +
                "}\n"
        assertTrue { collectKeepRulesUnderDirectory(out) == expectedKeepRules }
    }

    @Test
    fun testKeepRulesGenerationFromFileDependencies() {
        assumeNotWindows() //b/145232747
        addFileDependency(app)

        project.executor().run("app:assembleRelease")
        val out = InternalArtifactType.DESUGAR_LIB_EXTERNAL_FILE_LIB_KEEP_RULES
            .getOutputDir(app.buildDir)
        val expectedKeepRules = "-keep class j\$.time.LocalTime {\n" +
                "    j\$.time.LocalTime MIDNIGHT;\n" +
                "}\n"
        assertTrue { collectKeepRulesUnderDirectory(out) == expectedKeepRules }
    }

    @Test
    fun testKeepRulesConsumptionWithArtifactTransform() {
        addFileDependency(app)

        project.executor().run("app:assembleRelease")
        val apk = app.getApk(GradleTestProject.ApkType.RELEASE)
        // check consuming keep rules generated from project by d8 task
        val desugarLibDex = getDexWithSpecificClass(usedDesugarClass, apk.allDexes)
            ?: fail("Failed to find the dex with class name $usedDesugarClass")
        // check consuming keep rules generated from subproject by d8 artifact transform
        DexSubject.assertThat(desugarLibDex).containsClass(usedDesugarClass2)
        // check consuming keep rules generated from file dependencies by DexFileDependenciesTask
        DexSubject.assertThat(desugarLibDex).containsClass(usedDesugarClass3)
        // check unused API classes are removed from the from desugar lib dex.
        DexSubject.assertThat(desugarLibDex).doesNotContainClasses(unusedDesugarClass)
    }

    @Test
    fun testKeepRulesConsumptionWithoutArtifactTransform() {
        addFileDependency(app)

        project.executor()
            .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM, false)
            .run("app:assembleRelease")

        val apk = app.getApk(GradleTestProject.ApkType.RELEASE)
        // check consuming keep rules generated from project/subproject/externalLibs
        val desugarLibDex = getDexWithSpecificClass(usedDesugarClass, apk.allDexes)
            ?: fail("Failed to find the dex with class name $usedDesugarClass")
        DexSubject.assertThat(desugarLibDex).containsClass(usedDesugarClass2)
        DexSubject.assertThat(desugarLibDex).containsClass(usedDesugarClass3)
        // check unused API classes are removed from the from desugar lib dex.
        DexSubject.assertThat(desugarLibDex).doesNotContainClasses(unusedDesugarClass)
    }

    @Test
    fun testKeepRulesConsumptionWithTwoConsecutiveBuilds() {
        project.executor().run("app:assembleRelease")
        var apk = app.getApk(GradleTestProject.ApkType.RELEASE)
        getDexWithSpecificClass(usedDesugarClass, apk.allDexes)
            ?: fail("Failed to find the dex with class name $usedDesugarClass")

        TestFileUtils.addMethod(
            FileUtils.join(app.mainSrcDir,"com/example/helloworld/HelloWorld.java"),
            """
                public static java.time.LocalTime getTime() {
                    return java.time.LocalTime.MIDNIGHT;
                }
            """.trimIndent())

        project.executor().run("app:assembleRelease")
        apk = app.getApk(GradleTestProject.ApkType.RELEASE)
        val desugarLibDex = getDexWithSpecificClass(usedDesugarClass, apk.allDexes)
            ?: fail("Failed to find the dex with class name $usedDesugarClass")
        DexSubject.assertThat(desugarLibDex).containsClass(usedDesugarClass3)
    }

    @Test
    fun testExternalLibsKeepRulesGenerationWithoutArtifactTransform() {
        assumeNotWindows() //b/145232747
        addExternalDependency(app)

        project.executor()
            .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM, false)
            .with(StringOption.BUILD_CACHE_DIR, CACHE_DIR)
            .run("clean", "app:assembleRelease")

        val out =
            InternalArtifactType.DESUGAR_LIB_EXTERNAL_LIBS_KEEP_RULES.getOutputDir(app.buildDir)
        val expectedKeepRules = "-keep class j\$.time.LocalTime {\n" +
                "    j\$.time.LocalTime MIDNIGHT;\n" +
                "}\n"
        assertTrue { collectKeepRulesUnderDirectory(out) == expectedKeepRules }
        val cacheFile = getKeepRulesCacheDir().listFiles()!!.first { it.isFile }
        val cacheTimeStamp = cacheFile.lastModified()

        project.executor()
            .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM, false)
            .with(StringOption.BUILD_CACHE_DIR, CACHE_DIR)
            .run("clean", "app:assembleRelease")

        val outputFile = out.resolve("release/out/" + cacheFile.name)
        assertTrue { outputFile.lastModified() == cacheTimeStamp }
    }

    @Test
    fun testKeepRulesGenerationAndConsumptionForMinifyBuild() {
        app.buildFile.appendText("""

            android.buildTypes.release.minifyEnabled = true
        """.trimIndent())
        // In the onCreate method of HelloWorld activity class, the getText() method with desugar
        // APIs needs to be called. Otherwise, R8 would shrink this getText() method and therefore
        // not generating keep rules for those desugar APIs.
        TestFileUtils.searchAndReplace(
            FileUtils.join(app.mainSrcDir, "com/example/helloworld/HelloWorld.java"),
            "// onCreate",
            "getText();"
        )

        project.executor().run("app:assembleRelease")

        val keepRulesOutputDir =
            InternalArtifactType.DESUGAR_LIB_PROJECT_KEEP_RULES.getOutputDir(app.buildDir)
        val expectedKeepRules = "-keep class j\$.util.Optional {$lineSeparator" +
                "    java.lang.Object get();$lineSeparator" +
                "}$lineSeparator" +
                "-keep class j\$.util.Collection\$-EL {$lineSeparator" +
                "    j\$.util.stream.Stream stream(java.util.Collection);$lineSeparator" +
                "}$lineSeparator" +
                "-keep class j\$.util.stream.Stream {$lineSeparator" +
                "    j\$.util.Optional findFirst();$lineSeparator" +
                "}$lineSeparator"
        assertTrue { collectKeepRulesUnderDirectory(keepRulesOutputDir) == expectedKeepRules }

        val apk = app.getApk(GradleTestProject.ApkType.RELEASE)
        val desugarLibDex = getDexWithSpecificClass(usedDesugarClass, apk.allDexes)
            ?: fail("Failed to find the dex with class name $usedDesugarClass")
        // check unused API classes are removed from the from desugar lib dex.
        DexSubject.assertThat(desugarLibDex).doesNotContainClasses(unusedDesugarClass)
    }

    @Test
    fun testWithDifferentMinSdkPerFlavor() {
        app.buildFile.appendText("""

            android {
                flavorDimensions 'sdk'
                productFlavors {
                    sdk21 {
                        minSdkVersion 21
                    }
                    sdk28 {
                        minSdkVersion 28
                    }
                    sdk28b {
                        minSdkVersion 28
                    }
                }
            }
        """.trimIndent())

        project.executor().run(":app:assembleSdk21Debug")
        val apk = app.getApk(GradleTestProject.ApkType.DEBUG, "sdk21")
        val dex = getDexWithSpecificClass(programClass, apk.allDexes)
            ?: fail("Failed to find the dex with class name $programClass")
        DexClassSubject.assertThat(dex.classes[programClass])
            .hasMethodThatInvokes("getText", "Lj$/util/stream/Stream;->findFirst()Lj$/util/Optional;")
        val desugarLibDex = getDexWithSpecificClass(usedDesugarClass, apk.allDexes)
            ?: fail("Failed to find the dex with class name $usedDesugarClass")
        DexSubject.assertThat(desugarLibDex).containsClass(usedDesugarClass3)
    }

    @Test
    fun testWithHighMinSdkDoesNotRewriteAnything() {
        app.buildFile.appendText("""

            android.defaultConfig.minSdkVersion = 28
        """.trimIndent())

        project.executor().run(":app:assembleDebug")
        val apk = app.getApk(GradleTestProject.ApkType.DEBUG)
        val dex = getDexWithSpecificClass(programClass, apk.allDexes)
            ?: fail("Failed to find the dex with class name $programClass")
        DexClassSubject.assertThat(dex.classes[programClass])
            .hasMethodThatInvokes("getText", "Ljava/util/stream/Stream;->findFirst()Ljava/util/Optional;")
        val desugarLibDex = getDexWithSpecificClass(usedDesugarClass, apk.allDexes)
        DexSubject.assertThat(desugarLibDex).isEqualTo(null)
    }

    private fun addFileDependency(project: GradleTestProject) {
        val fileDependencyName = "withDesugarApi.jar"
        project.buildFile.appendText("""

            dependencies {
                implementation files('$fileDependencyName')
            }
        """.trimIndent())
        val fileLib = project.file(fileDependencyName).toPath()
        TestInputsGenerator.pathWithClasses(fileLib, listOf(ClassWithDesugarApi::class.java))
    }

    private fun addExternalDependency(project: GradleTestProject) {
        project.buildFile.appendText("""

            dependencies {
                implementation 'com.example:myaar:1'
            }
        """.trimIndent())
    }

    private fun collectKeepRulesUnderDirectory(dir: File) : String {
        val stringBuilder = StringBuilder()
        Files.walk(dir.toPath()).use { paths ->
            paths
                .filter{ it.toFile().isFile }
                .forEach { stringBuilder.append(it.toFile().readText(Charsets.UTF_8))
                }
        }
        return stringBuilder.toString()
    }

    private fun getDexWithSpecificClass(className: String, dexes: Collection<Dex>) : Dex? =
        dexes.find {
            AndroidArchive.checkValidClassName(className)
            it.classes.keys.contains(className)
        }

    private fun addSourceWithDesugarApiToLibraryModule() {
        val source = with(JavaSourceFileBuilder(LIBRARY_PACKAGE)) {
            addImports("java.time.Month")
            addClass("""
                public class Calendar {
                    public static Month getTime() {
                        return Month.JUNE;
                    }
                }
            """.trimIndent())
            build()
        }
        val file = File(library.mainSrcDir, "${LIBRARY_PACKAGE.replace('.', '/')}/Calendar.java")
        file.parentFile.mkdirs()
        file.writeText(source)
    }

    private fun getKeepRulesCacheDir(): File {
        val cacheFolder = project.file(CACHE_DIR).listFiles().find { it.isDirectory }
        val keepRulesCacheFolder
                = cacheFolder!!.listFiles().find { File(it, "output").isDirectory }
        return File(keepRulesCacheFolder, "output")
    }

    companion object {
        private const val APP_MODULE = ":app"
        private const val LIBRARY_MODULE = ":library"
        private const val LIBRARY_PACKAGE = "com.example.lib"
        private const val CACHE_DIR = "agp_cache_dir"
        private const val DESUGAR_DEPENDENCY
                = "com.android.tools:desugar_jdk_libs:$DESUGAR_DEPENDENCY_VERSION"
    }

    private val lineSeparator: String = System.lineSeparator()
}
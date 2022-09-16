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

import com.android.build.gradle.integration.common.fixture.Adb
import com.android.build.gradle.integration.common.fixture.DESUGAR_DEPENDENCY_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.TestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.JavaSourceFileBuilder
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.desugar.resources.ClassWithDesugarApi
import com.android.build.gradle.integration.desugar.resources.ClassWithDesugarApi2
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestInputsGenerator.jarWithClasses
import com.android.testutils.apk.AndroidArchive
import com.android.testutils.apk.Dex
import com.android.testutils.generateAarWithContent
import com.android.testutils.truth.DexClassSubject
import com.android.testutils.truth.DexSubject
import com.android.tools.profgen.ArtProfile
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.jf.dexlib2.immutable.debug.ImmutableStartLocal
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.assertNotNull
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
    val project = GradleTestProject.builder()
        .withAdditionalMavenRepo(mavenRepo)
        .withGradleBuildCacheDirectory(File("local-build-cache"))
        .fromTestApp(setUpTestProject()).create()

    @get:Rule
    var adb = Adb()

    private lateinit var app: GradleTestProject
    private lateinit var library: GradleTestProject

    private val programClass = "Lcom/example/helloworld/HelloWorld;"
    private val usedDesugarClass = "Lj$/util/stream/Stream;"
    private val usedDesugarClass2 = "Lj$/time/Month;"
    private val usedDesugarClass3 = "Lj$/time/LocalTime;"
    private val unusedDesugarClass = "Lj$/time/Year;"
    // Class java.util.stream.StreamOpFlag is selected as it is package private, and used
    // indirectly by the test.
    private val unObfuscatedClass = "Lj$/util/stream/StreamOpFlag;"
    // Use name 'a', to indirectly check that no minification (obfuscation) happens, as 'a'
    // is the first name to be selected by R8 when minifying.
    private val obfuscatedClass = "Lj$/util/stream/a;"
    private val desugarConfigClass = "Lj$/time/TimeConversions;"

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
            FileUtils.join(app.projectDir, "src/androidTest/java/com/example/helloworld/HelloWorldTest.java"),
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

    @Test
    fun testLintFailsForMainSourceSetOnlyIfTestDesugaringEnabledAndGlobalDesugaringDisabled() {
        TestFileUtils.appendToFile(
                project.gradlePropertiesFile,
                "${BooleanOption.ENABLE_INSTRUMENTATION_TEST_DESUGARING.propertyName}=true"
        )
        app.buildFile.appendText("""

            android.compileOptions.coreLibraryDesugaringEnabled = false
            android.lintOptions.abortOnError = true
            android.lint.checkTestSources = true
        """.trimIndent())

        TestFileUtils.addMethod(
                FileUtils.join(app.projectDir, "src/androidTest/java/com/example/helloworld/HelloWorldTest.java"),
                """
                @Test
                public void testLocalDate() {
                    java.util.Collection<String> collection
                    = java.util.Arrays.asList("first", "second", "third");
                    java.util.stream.Stream<String> streamOfCollection = collection.stream();

                    java.time.LocalDate date = java.time.LocalDate.now();
                    String month = date.getMonth().name();
                    Assert.assertEquals("first", HelloWorld.getText());
                }
            """.trimIndent()
        )
        executor().expectFailure().run("app:lintDebug")
        val reportXml = app.file("build/reports/lint-results-debug.html").readText()
        // main
        assertThat(reportXml).contains("Call requires API level 24 (current min is 21): <code>java.util.Collection#stream</code>")
        // android test - regex ??
        assertThat(reportXml).doesNotContain("Call requires API level 26 (current min is 21): <code>java.time.LocalDate#now</code>")
        assertThat(reportXml).doesNotContain("Call requires API level 26 (current min is 21): <code>java.time.LocalDate#getMonth</code>")
        assertThat(reportXml).doesNotContain(
                """
                   <span class="location"><a href="../../src/androidTest/java/com/example/helloworld/HelloWorldTest.java">
                   ../../src/androidTest/java/com/example/helloworld/HelloWorldTest.java</a>:41</span>:
                   <span class="message">Call requires API level 24 (current min is 21): <code>java.util.Collection#stream
                """.trimIndent()
        )
    }

    @Test
    fun testLintPassesWhenDesugaringEnabled() {
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "${BooleanOption.ENABLE_INSTRUMENTATION_TEST_DESUGARING.propertyName}=false"
        )
        app.buildFile.appendText("""

            android.compileOptions.coreLibraryDesugaringEnabled = true
            android.lintOptions.abortOnError = true
            android.lint.checkTestSources = true
        """.trimIndent())

        TestFileUtils.addMethod(
            FileUtils.join(app.projectDir, "src/androidTest/java/com/example/helloworld/HelloWorldTest.java"),
            """
                @Test
                public void testLocalDate() {
                    java.util.Collection<String> collection
                    = java.util.Arrays.asList("first", "second", "third");
                    java.util.stream.Stream<String> streamOfCollection = collection.stream();

                    java.time.LocalDate date = java.time.LocalDate.now();
                    String month = date.getMonth().name();
                    Assert.assertEquals("first", HelloWorld.getText());
                }
            """.trimIndent()
        )
        executor().run("app:lintDebug")
        // Run it twice as a regression test for http://b/218289804.
        executor().run("app:lintDebug")
    }

    @Test
    fun testModelFetchingWhenDesugaringTestIsEnabled() {
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "${BooleanOption.ENABLE_INSTRUMENTATION_TEST_DESUGARING.propertyName}=true"
        )
        app.buildFile.appendText("""

            android.compileOptions.coreLibraryDesugaringEnabled = false
        """.trimIndent())

        val model = app.modelV2().ignoreSyncIssues().fetchModels().container.getProject(":app").androidProject!!
        val mainArtifact = model.variants.find { it.name == "debug" }?.mainArtifact
        val testArtifact = model.variants.find { it.name == "debug" }?.androidTestArtifact

        val desugarMethodsFileTestArtifact = testArtifact
            ?.desugaredMethodsFiles
            ?.find { it.name.contains("desugar_jdk_libs_configuration") }
        val desugarMethodsFileMainArtifact = mainArtifact
            ?.desugaredMethodsFiles
            ?.find { it.name.contains("desugar_jdk_libs_configuration") }

        Truth.assertThat(desugarMethodsFileTestArtifact).isNotNull()
        Truth.assertThat(desugarMethodsFileMainArtifact).isNull()
    }

    /**
     * Check if Java 8 API(e.g. Stream) is rewritten properly by D8
     */
    @Test
    fun testApiRewriting() {
        executor().run("app:assembleDebug")
        val apk = app.getApk(GradleTestProject.ApkType.DEBUG)
        val dex = getDexWithSpecificClass(programClass, apk.allDexes)
            ?: fail("Failed to find the dex with class name $programClass")
        DexClassSubject.assertThat(dex.classes[programClass])
            .hasMethodThatInvokes("getText", "Lj$/util/stream/Stream;->findFirst()Lj$/util/Optional;")
        val desugarLibDex = getDexWithSpecificClass(usedDesugarClass, apk.allDexes)!!
        assertThat(getAllStartLocals(desugarLibDex)).named("debug locals info").isNotEmpty()
        assertThat(getAllDexWithJDollarTypes(apk.allDexes)).named("all dex files with desugar jdk lib classes").hasSize(1)
    }

    @Test
    fun testLintPassesIfDesugaringEnabled() {
        app.buildFile.appendText("""

            android.lintOptions.abortOnError = true
        """.trimIndent())
        executor().run("app:lintDebug")
        // Run it twice as a regression test for http://b/218289804.
        executor().run("app:lintDebug")
    }

    @Test
    fun testLintFailsIfDesugaringDisabled() {
        app.buildFile.appendText("""

            android.compileOptions.coreLibraryDesugaringEnabled = false
            android.lintOptions.abortOnError = true
        """.trimIndent())
        val result =
            executor()
                .expectFailure().run("app:lintDebug")
        assertThat(result.failureMessage).contains("Lint found errors in the project")
        val reportXml = app.file("build/reports/lint-results-debug.html").readText()
        assertThat(reportXml).contains(
            "Call requires API level 24 (current min is 21): <code>java.util.Collection#stream</code>")
    }

    @Test
    fun testModelFetching() {
        val model = app.modelV2().fetchModels().container.getProject(":app").androidProject
        Truth.assertThat(model!!.javaCompileOptions.isCoreLibraryDesugaringEnabled).isTrue()
    }

    @Test
    fun testModelLintFileFetching() {
        var model = app.modelV2().fetchModels().container.getProject(":app").androidProject
        var desugarMethodsFile = model!!.variants.first().desugaredMethods
            .find { it.name.contains("desugar_jdk_libs_configuration") }
        Truth.assertThat(desugarMethodsFile).isNotNull()

        // coreLibraryDesugaring is disabled
        app.buildFile.appendText("""

            android.compileOptions.coreLibraryDesugaringEnabled = false
        """.trimIndent())
        model = app.modelV2().fetchModels().container.getProject(":app").androidProject
        desugarMethodsFile = model!!.variants.first().desugaredMethods
            .find { it.name.contains("desugar_jdk_libs_configuration") }
        Truth.assertThat(desugarMethodsFile).isNull()
    }

    @Test
    fun testKeepRulesGenerationFromAppProject() {
        executor().run("app:assembleRelease")
        val out = InternalArtifactType.DESUGAR_LIB_PROJECT_KEEP_RULES.getOutputDir(app.buildDir)
        val expectedKeepRules = "-keep class j\$.util.Collection\$-EL {$lineSeparator" +
                "    j\$.util.stream.Stream stream(java.util.Collection);$lineSeparator" +
                "}$lineSeparator" +
                "-keep class j\$.util.Optional {$lineSeparator" +
                "    java.lang.Object get();$lineSeparator" +
                "}$lineSeparator" +
                "-keep class j\$.util.stream.Stream {$lineSeparator" +
                "    j\$.util.Optional findFirst();$lineSeparator" +
                "}$lineSeparator"
        Truth.assertThat(collectKeepRulesUnderDirectory(out)).isEqualTo(expectedKeepRules)
    }

    @Test
    fun testKeepRulesGenerationFromFileDependencies() {
        addFileDependencies(app)

        executor().run("app:assembleRelease")
        val out = InternalArtifactType.DESUGAR_LIB_EXTERNAL_FILE_LIB_KEEP_RULES
            .getOutputDir(app.buildDir)
        val expectedKeepRule1 = "-keep class j\$.time.LocalTime {$lineSeparator" +
                "    j\$.time.LocalTime MIDNIGHT;$lineSeparator" +
                "}$lineSeparator"
        val expectedKeepRule2 = "-keep class j\$.time.LocalTime {$lineSeparator" +
                "    j\$.time.LocalTime NOON;$lineSeparator" +
                "}$lineSeparator"
        assertTrue { collectKeepRulesUnderDirectory(out).contains(expectedKeepRule1) }
        assertTrue { collectKeepRulesUnderDirectory(out).contains(expectedKeepRule2) }
    }

    @Test
    fun testKeepRulesConsumption() {
        addFileDependencies(app)

        executor().run("app:assembleRelease")
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
        // check non minified release builds are not obfuscated.
        DexSubject.assertThat(desugarLibDex).containsClass(unObfuscatedClass)
        assertThat(getAllStartLocals(desugarLibDex)).named("debug locals info").isEmpty()
        DexSubject.assertThat(desugarLibDex).doesNotContainClasses(obfuscatedClass)
        assertThat(getAllDexWithJDollarTypes(apk.allDexes)).named("all dex files with desugar jdk lib classes")
            .hasSize(1)
    }

    @Test
    fun testKeepRulesConsumptionWithTwoConsecutiveBuilds() {
        executor().run("app:assembleRelease")
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

        executor().run("app:assembleRelease")
        apk = app.getApk(GradleTestProject.ApkType.RELEASE)
        val desugarLibDex = getDexWithSpecificClass(usedDesugarClass, apk.allDexes)
            ?: fail("Failed to find the dex with class name $usedDesugarClass")
        DexSubject.assertThat(desugarLibDex).containsClass(usedDesugarClass3)
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

        executor().run("app:assembleRelease")

        val keepRulesOutputDir =
            InternalArtifactType.DESUGAR_LIB_PROJECT_KEEP_RULES.getOutputDir(app.buildDir)
        val expectedKeepRules = "-keep class j\$.util.Collection\$-EL {$lineSeparator" +
                "    j\$.util.stream.Stream stream(java.util.Collection);$lineSeparator" +
                "}$lineSeparator" +
                "-keep class j\$.util.Optional {$lineSeparator" +
                "    java.lang.Object get();$lineSeparator" +
                "}$lineSeparator" +
                "-keep class j\$.util.stream.Stream {$lineSeparator" +
                "    j\$.util.Optional findFirst();$lineSeparator" +
                "}$lineSeparator"
        Truth.assertThat(collectKeepRulesUnderDirectory(keepRulesOutputDir)).isEqualTo(expectedKeepRules)

        val apk = app.getApk(GradleTestProject.ApkType.RELEASE)
        val desugarLibDex = getDexWithSpecificClass(usedDesugarClass, apk.allDexes)
            ?: fail("Failed to find the dex with class name $usedDesugarClass")
        // check unused API classes are removed from the from desugar lib dex.
        DexSubject.assertThat(desugarLibDex).doesNotContainClasses(unusedDesugarClass)
    }

    @Test
    fun testNoAmbiguousTransformsForKeepRulesArtifact() {
        app.buildFile.appendText("""

            android.buildTypes.debug.minifyEnabled = true
        """.trimIndent())

        executor().run("app:assembleRelease")
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

        executor().run(":app:assembleSdk21Debug")
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

        executor().run(":app:assembleDebug")
        val apk = app.getApk(GradleTestProject.ApkType.DEBUG)
        val dex = getDexWithSpecificClass(programClass, apk.allDexes)
            ?: fail("Failed to find the dex with class name $programClass")
        DexClassSubject.assertThat(dex.classes[programClass])
            .hasMethodThatInvokes("getText", "Ljava/util/stream/Stream;->findFirst()Ljava/util/Optional;")
        val desugarLibDex = getDexWithSpecificClass(usedDesugarClass, apk.allDexes)
        DexSubject.assertThat(desugarLibDex).isEqualTo(null)
    }

    @Test
    fun testNonMinifyAndroidTestAppApk() {
        executor().run(":app:assembleDebugAndroidTest")
        val apk = app.getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG)
        // desugar_lib should be packaged in the tested APK, not in androidTest one
        DexSubject.assertThat(getDexWithSpecificClass(usedDesugarClass, apk.allDexes)).isNull()
    }

    @Test
    fun testMinifyAndroidTestAppApk() {
        TestFileUtils.addMethod(
            FileUtils.join(app.mainSrcDir,"com/example/helloworld/HelloWorld.java"),
            """
                public static java.time.LocalTime getTime() {
                    return java.time.LocalTime.now();
                }
            """.trimIndent())

        TestFileUtils.addMethod(
            FileUtils.join(app.projectDir, "src/androidTest/java/com/example/helloworld/HelloWorldTest.java"),
            """
                @Test
                public void test() {
                    Assert.assertEquals("first", HelloWorld.getTime().toString());
                }
            """.trimIndent()
        )

        app.buildFile.appendText("""

            android.buildTypes.debug.minifyEnabled = true
        """.trimIndent())
        executor().run(":app:assembleDebugAndroidTest")
        val apk = app.getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG)
        DexSubject.assertThat(getDexWithSpecificClass(usedDesugarClass3, apk.allDexes))
            .isNotEqualTo(null)
    }

    @Test
    fun testNonMinifyAndroidTestLibraryApk() {
        executor().run(":library:assembleDebugAndroidTest")
        val apk = library.getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG)
        DexSubject.assertThat(getDexWithSpecificClass(usedDesugarClass, apk.allDexes))
            .isNotEqualTo(null)
    }

    // There are some classes in desugar lib configuration jar which need to be processed by L8
    // and packaged as a separated dex
    @Test
    fun testConfigJarPackagedAsSeparateDex() {
        executor().run(":app:assembleDebug")
        var apk = app.getApk(GradleTestProject.ApkType.DEBUG)
        var desugarConfigLibDex = getDexWithSpecificClass(desugarConfigClass, apk.allDexes)
            ?: fail("Failed to find the dex with class name $desugarConfigClass")
        DexSubject.assertThat(desugarConfigLibDex).doesNotContainClasses(programClass)

        // Invoke a Android API taking a java.time class as argument. This will
        // require conversion, a the API will not be able to take a j$.time class.
        TestFileUtils.addMethod(
            FileUtils.join(app.mainSrcDir,"com/example/helloworld/HelloWorld.java"),
            """
                public static void useConversion(java.time.ZonedDateTime zonedDateTime) {
                    android.view.textclassifier.TextClassification.Request.Builder builder = null;
                    builder.setReferenceTime(zonedDateTime);
                }
            """.trimIndent())
        executor().run(":app:assembleRelease")
        apk = app.getApk(GradleTestProject.ApkType.RELEASE)
        desugarConfigLibDex = getDexWithSpecificClass(desugarConfigClass, apk.allDexes)
            ?: fail("Failed to find the dex with class name $desugarConfigClass")
        DexSubject.assertThat(desugarConfigLibDex).doesNotContainClasses(programClass)
    }

    @Test
    fun testArtProfileDexNamingForApk() {
      TestFileUtils.appendToFile(
            FileUtils.join(app .getMainSrcDir(""),"baseline-prof.txt"),
          programClass + "\n" + usedDesugarClass +"\n")

        executor().run(":app:assembleRelease")

        val apkFile = app.getApk(GradleTestProject.ApkType.RELEASE).file.toFile()
        val zip = ZipFile(apkFile)
        val entry = zip.entries().asSequence().first { it.name == "assets/dexopt/baseline.prof" }
        zip.getInputStream(entry)
        val profile = ArtProfile(zip.getInputStream(entry))
        Truth.assertThat(profile!!.profileData.keys.map { it.name })
            .containsExactly("classes.dex","classes2.dex")
    }

    @Test
    fun testArtProfileDexNamingForAab() {
        TestFileUtils.appendToFile(
            FileUtils.join(app .getMainSrcDir(""),"baseline-prof.txt"),
            programClass + "\n" + usedDesugarClass +"\n")

        executor().run(":app:bundleRelease")

        val apkFile = app.getBundle(GradleTestProject.ApkType.RELEASE).file.toFile()
        val zip = ZipFile(apkFile)
        val entry = zip.entries().asSequence()
            .first { it.name == "BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof" }
        zip.getInputStream(entry)
        val profile = ArtProfile(zip.getInputStream(entry))
        Truth.assertThat(profile!!.profileData.keys.map { it.name })
            .containsExactly("classes.dex","classes2.dex")
    }

    @Test
    fun testL8TaskInvocationForBundleReleaseBuild() {
        val build = executor().run(":app:bundleRelease")
        Truth.assertThat(build.didWorkTasks).contains(":app:l8DexDesugarLibRelease")
    }

    @Test
    fun testKeepRuleConsumptionForExternalLibOnRuntimeClasspath() {
        // External lib dependencies of subproject end up in the app runtime classpath, this test
        // makes sure their keep rules are consumed.
        addExternalDependency(library)
        executor().run("app:assembleRelease")
        val apk = app.getApk(GradleTestProject.ApkType.RELEASE)
        assertNotNull(getDexWithSpecificClass(usedDesugarClass3, apk.allDexes))
    }

    @Test
    fun testKeepRuleConsumptionForSubprojectOnRuntimeClasspath() {
        TestFileUtils.searchAndReplace(
            app.buildFile,
            """implementation project("$LIBRARY_MODULE")""",
            """runtimeOnly project("$LIBRARY_MODULE")"""
        )
        executor().run("app:assembleRelease")
        val apk = app.getApk(GradleTestProject.ApkType.RELEASE)
        assertNotNull(getDexWithSpecificClass(usedDesugarClass2, apk.allDexes))
    }

    private fun addFileDependencies(project: GradleTestProject) {
        val fileDep = "withDesugarApi.jar"
        val fileDep2 = "withDesugarApi2.jar"
        project.buildFile.appendText("""

            dependencies {
                implementation files('$fileDep')
                implementation files('$fileDep2')
            }
        """.trimIndent())
        val fileLib = project.file(fileDep).toPath()
        TestInputsGenerator.pathWithClasses(fileLib, listOf(ClassWithDesugarApi::class.java))
        val fileLib2 = project.file(fileDep2).toPath()
        TestInputsGenerator.pathWithClasses(fileLib2, listOf(ClassWithDesugarApi2::class.java))
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

    private fun getAllStartLocals(desugarLibDex: Dex): Collection<ImmutableStartLocal> {
        return desugarLibDex.classes.values.flatMap { dexClass ->
            dexClass.methods.flatMap { method ->
                method.implementation?.debugItems?.let { debugItems ->
                    debugItems.mapNotNull { it as? ImmutableStartLocal }
                } ?: emptyList()
            }
        }
    }

    private fun getAllDexWithJDollarTypes(dexes: Collection<Dex>): List<Dex> =
        dexes.filter {
            it.classes.keys.any { it.startsWith("Lj$/") }
        }

    private fun executor() = project.executor()

    companion object {
        private const val APP_MODULE = ":app"
        private const val LIBRARY_MODULE = ":library"
        private const val LIBRARY_PACKAGE = "com.example.lib"
        private const val DESUGAR_DEPENDENCY
                = "com.android.tools:desugar_jdk_libs:$DESUGAR_DEPENDENCY_VERSION"
    }

    private val lineSeparator: String = System.lineSeparator()
}

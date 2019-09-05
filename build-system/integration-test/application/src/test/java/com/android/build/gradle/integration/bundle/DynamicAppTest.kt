/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.apksig.ApkVerifier
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.common.utils.getOutputByName
import com.android.build.gradle.integration.common.utils.getVariantByName
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.OptionalBooleanOption
import com.android.build.gradle.options.StringOption
import com.android.builder.model.AndroidProject
import com.android.builder.model.AppBundleProjectBuildOutput
import com.android.builder.model.AppBundleVariantBuildOutput
import com.android.ide.common.signing.KeystoreHelper
import com.android.testutils.TestUtils
import com.android.testutils.apk.Dex
import com.android.testutils.apk.Zip
import com.android.testutils.truth.DexSubject.assertThat
import com.android.testutils.truth.FileSubject
import com.android.testutils.truth.FileSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.base.Throwables
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.fail

private const val MAIN_DEX_LIST_PATH = "/BUNDLE-METADATA/com.android.tools.build.bundletool/mainDexList.txt"

internal val multiDexSupportLibClasses = listOf(
    "Landroid/support/multidex/MultiDex;",
    "Landroid/support/multidex/MultiDexApplication;",
    "Landroid/support/multidex/MultiDexExtractor;",
    "Landroid/support/multidex/MultiDexExtractor\$1;",
    "Landroid/support/multidex/MultiDexExtractor\$ExtractedDex;",
    "Landroid/support/multidex/MultiDex\$V14;",
    "Landroid/support/multidex/MultiDex\$V19;",
    "Landroid/support/multidex/MultiDex\$V4;",
    "Landroid/support/multidex/ZipUtil;",
    "Landroid/support/multidex/ZipUtil\$CentralDirectory;"
)

class DynamicAppTest {

    @get:Rule
    val tmpFile= TemporaryFolder()

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("dynamicApp")
        .withoutNdk()
        .create()

    private val bundleContent: Array<String> = arrayOf(
        MAIN_DEX_LIST_PATH,
        "/BundleConfig.pb",
        "/base/dex/classes.dex",
        "/base/manifest/AndroidManifest.xml",
        "/base/res/layout/base_layout.xml",
        "/base/resources.pb",
        "/feature1/dex/classes.dex",
        "/feature1/manifest/AndroidManifest.xml",
        "/feature1/res/layout/feature_layout.xml",
        "/feature1/resources.pb",
        "/feature2/dex/classes.dex",
        "/feature2/manifest/AndroidManifest.xml",
        "/feature2/res/layout/feature2_layout.xml",
        "/feature2/resources.pb")

    // Debuggable Bundles are always unsigned.
    private val debugUnsignedContent: Array<String> = bundleContent.plus(arrayOf(
        "/base/dex/classes2.dex" // Legacy multidex has minimal main dex in debug mode
    ))

    private val releaseUnsignedContent: Array<String> = bundleContent.plus(arrayOf(
        // Only the release variant is shrunk, so only it will contain a proguard mapping file.
        "/BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map",
        // Only the release variant would have the dependencies file.
        "/BUNDLE-METADATA/com.android.tools.build.libraries/dependencies.pb"
    ))

    private val mainDexClasses: List<String> =
        multiDexSupportLibClasses.plus("Lcom/example/app/AppClassNeededInMainDexList;")

    private val mainDexListClassesInBundle: List<String> =
        mainDexClasses.plus("Lcom/example/feature1/Feature1ClassNeededInMainDexList;")

    private val jarClasses:  List<String> = listOf("Lcom/example/Foo/Foo;")

    @Test
    @Throws(IOException::class)
    fun `test model contains feature information`() {
        val rootBuildModelMap = project.model()
            .fetchAndroidProjects()
            .rootBuildModelMap

        val appModel = rootBuildModelMap[":app"]
        Truth.assertThat(appModel).named("app model").isNotNull()
        Truth.assertThat(appModel!!.dynamicFeatures)
            .named("feature list in app model")
            .containsExactly(":feature1", ":feature2")

        val featureModel = rootBuildModelMap[":feature1"]
        Truth.assertThat(featureModel).named("feature model").isNotNull()
        Truth.assertThat(featureModel!!.projectType)
            .named("feature model type")
            .isEqualTo(AndroidProject.PROJECT_TYPE_DYNAMIC_FEATURE)
    }

    @Test
    fun `test buildInstantApk task`() {
        project.executor()
            .with(BooleanOption.IDE_DEPLOY_AS_INSTANT_APP, true)
            .run("assembleDebug")


        for (moduleName in listOf("app", "feature1", "feature2")) {
            val manifestFile =
                FileUtils.join(
                    project.getSubproject(moduleName).buildDir,
                    "intermediates",
                    "instant_app_manifest",
                    "debug",
                    "AndroidManifest.xml")
            FileSubject.assertThat(manifestFile).isFile()
            FileSubject.assertThat(manifestFile).contains("android:targetSandboxVersion=\"2\"")
        }
    }

    @Test
    fun `test bundleMinSdkDifference task`() {
        TestFileUtils.searchAndReplace(
            project.getSubproject(":feature1").buildFile,
            "minSdkVersion 18",
            "minSdkVersion 21")
        val bundleTaskName = getBundleTaskName("debug")
        project.execute("app:$bundleTaskName")

        val bundleFile = getApkFolderOutput("debug").bundleFile
        FileSubject.assertThat(bundleFile).exists()

        val manifestFile = FileUtils.join(project.getSubproject("feature1").buildDir,
            "intermediates",
            "merged_manifests",
            "debug",
            "AndroidManifest.xml")
        FileSubject.assertThat(manifestFile).isFile()
        FileSubject.assertThat(manifestFile).doesNotContain("splitName")
        FileSubject.assertThat(manifestFile).contains("minSdkVersion=\"21\"")

        val bundleManifest = FileUtils.join(project.getSubproject("feature1").buildDir,
            "intermediates",
            "bundle_manifest",
            "debug",
            "bundle-manifest",
            "AndroidManifest.xml")
        FileSubject.assertThat(bundleManifest).isFile()
        FileSubject.assertThat(bundleManifest).contains("android:splitName=\"feature1\"")
        FileSubject.assertThat(bundleManifest).contains("minSdkVersion=\"21\"")

        val baseManifest = FileUtils.join(project.getSubproject("app").buildDir,
            "intermediates",
            "merged_manifests",
            "debug",
            "AndroidManifest.xml")
        FileSubject.assertThat(baseManifest).isFile()
        FileSubject.assertThat(baseManifest).doesNotContain("splitName")
        FileSubject.assertThat(baseManifest).contains("minSdkVersion=\"18\"")

        val baseBundleManifest = FileUtils.join(project.getSubproject("app").buildDir,
            "intermediates",
            "bundle_manifest",
            "debug",
            "bundle-manifest",
            "AndroidManifest.xml")
        FileSubject.assertThat(baseBundleManifest).isFile()
        FileSubject.assertThat(baseBundleManifest).contains("android:splitName=\"feature1\"")
        FileSubject.assertThat(baseBundleManifest).contains("minSdkVersion=\"18\"")
    }

    @Test
    fun `test bundleDebug task`() {
        val bundleTaskName = getBundleTaskName("debug")
        project.execute("app:$bundleTaskName")

        val bundleFile = getApkFolderOutput("debug").bundleFile
        FileSubject.assertThat(bundleFile).exists()

        Zip(bundleFile).use {
            Truth.assertThat(it.entries.map { it.toString() }).containsExactly(*debugUnsignedContent)
            val dex = Dex(it.getEntry("base/dex/classes.dex")!!)
            // Legacy multidex is applied to the dex of the base directly for the case
            // when the build author has excluded all the features from fusing.
            assertThat(dex).containsExactlyClassesIn(mainDexClasses)

            val dex2 = Dex(it.getEntry("base/dex/classes2.dex")!!)
            assertThat(dex2).containsClassesIn(jarClasses)

            // The main dex list must also analyze the classes from features.
            val mainDexListInBundle =
                Files.readAllLines(it.getEntry(MAIN_DEX_LIST_PATH)).map { "L" + it.removeSuffix(".class") + ";" }

            assertThat(mainDexListInBundle).containsExactlyElementsIn(mainDexListClassesInBundle)

        }

        // also test that the feature manifest contains the feature name.
        val manifestFile = FileUtils.join(project.getSubproject("feature1").buildDir,
            "intermediates",
            "merged_manifests",
            "debug",
            "AndroidManifest.xml")
        FileSubject.assertThat(manifestFile).isFile()
        FileSubject.assertThat(manifestFile).doesNotContain("splitName")
        FileSubject.assertThat(manifestFile).contains("featureSplit=\"feature1\"")

        // check that the feature1 source manifest has not been changed so we can verify that
        // it is automatically reset to the base module value.
        val originalManifestFile = FileUtils.join(
            project.getSubproject("feature1").getMainSrcDir(""),
            "AndroidManifest.xml")
        FileSubject.assertThat(originalManifestFile).doesNotContain("android:versionCode=\"11\"")

        // and finally check that the resulting manifest has had its versionCode changed from the
        // base module value
        FileSubject.assertThat(manifestFile).contains("android:versionCode=\"11\"")

        // Check that neither splitName or featureSplit are merged back to the base.
        val baseManifest = FileUtils.join(project.getSubproject("app").buildDir,
            "intermediates",
            "merged_manifests",
            "debug",
            "AndroidManifest.xml")
        assertThat(baseManifest).isFile()
        assertThat(baseManifest).doesNotContain("splitName")
        assertThat(baseManifest).doesNotContain("featureSplit")

        // Check that the bundle_manifests contain splitName
        val featureBundleManifest = FileUtils.join(project.getSubproject("feature1").buildDir,
            "intermediates",
            "bundle_manifest",
            "debug",
            "bundle-manifest",
            "AndroidManifest.xml")
        assertThat(featureBundleManifest).isFile()
        assertThat(featureBundleManifest).contains("android:splitName=\"feature1\"")

        val baseBundleManifest = FileUtils.join(project.getSubproject("app").buildDir,
            "intermediates",
            "bundle_manifest",
            "debug",
            "bundle-manifest",
            "AndroidManifest.xml")
        assertThat(baseBundleManifest).isFile()
        assertThat(baseBundleManifest).contains("android:splitName=\"feature1\"")
    }

    @Test
    fun `test unsigned bundleRelease task with proguard`() {
        val bundleTaskName = getBundleTaskName("release")
        project.executor().with(OptionalBooleanOption.ENABLE_R8, false).run("app:$bundleTaskName")

        val bundleFile = getApkFolderOutput("release").bundleFile
        FileSubject.assertThat(bundleFile).exists()

        Zip(bundleFile).use {
            assertThat(it.entries.map { it.toString() }).containsExactly(*releaseUnsignedContent)
            val dex = Dex(it.getEntry("base/dex/classes.dex")!!)
            assertThat(dex).containsClass("Landroid/support/multidex/MultiDexApplication;")
        }
    }

    @Test
    fun `test unsigned bundleRelease task with r8`() {
        val bundleTaskName = getBundleTaskName("release")
        project.executor().with(OptionalBooleanOption.ENABLE_R8, true).run("app:$bundleTaskName")

        val bundleFile = getApkFolderOutput("release").bundleFile
        FileSubject.assertThat(bundleFile).exists()

        Zip(bundleFile).use {
            assertThat(it.entries.map { it.toString() }).containsExactly(*releaseUnsignedContent)
            val dex = Dex(it.getEntry("base/dex/classes.dex")!!)
            assertThat(dex).containsClass("Landroid/support/multidex/MultiDexApplication;")
        }
    }

    @Test
    fun `test unsigned bundleRelease task with r8 dontminify`() {
        project.getSubproject("app").testDir.resolve("proguard-rules.pro")
            .writeText("-dontobfuscate")
        val bundleTaskName = getBundleTaskName("release")
        project.executor().with(OptionalBooleanOption.ENABLE_R8, true).run("app:$bundleTaskName")

        val bundleFile = getApkFolderOutput("release").bundleFile
        FileSubject.assertThat(bundleFile).exists()

        Zip(bundleFile).use {
            assertThat(it.entries.map { it.toString() }).containsExactly(*releaseUnsignedContent)

            val mainDexList = Files.readAllLines(it.getEntry(MAIN_DEX_LIST_PATH))
            val expectedMainDexList =
                multiDexSupportLibClasses.map { it.substring(1, it.length - 1) + ".class" }
            assertThat(mainDexList).containsExactlyElementsIn(expectedMainDexList)
        }
    }

    @Test
    fun `test packagingOptions`() {
        // add a new res file and exclude.
        val appProject = project.getSubproject(":app")
        TestFileUtils.appendToFile(appProject.buildFile, "\nandroid.packagingOptions {\n" +
                "  exclude 'foo.txt'\n" +
                "}")
        val fooTxt = FileUtils.join(appProject.testDir, "src", "main", "resources", "foo.txt")
        FileUtils.mkdirs(fooTxt.parentFile)
        Files.write(fooTxt.toPath(), "foo".toByteArray(Charsets.UTF_8))

        val bundleTaskName = getBundleTaskName("debug")
        project.execute("app:$bundleTaskName")

        val bundleFile = getApkFolderOutput("debug").bundleFile
        FileSubject.assertThat(bundleFile).exists()

        Zip(bundleFile).use {
            Truth.assertThat(it.entries.map { it.toString() }).containsExactly(*debugUnsignedContent)
        }
    }

    @Test
    fun `test abiFilter with Bundle task`() {
        TestUtils.disableIfOnWindowsWithBazel()
        val appProject = project.getSubproject(":app")
        createAbiFile(appProject, SdkConstants.ABI_ARMEABI_V7A, "libbase.so")
        createAbiFile(appProject, SdkConstants.ABI_INTEL_ATOM, "libbase.so")
        createAbiFile(appProject, SdkConstants.ABI_INTEL_ATOM64, "libbase.so")

        TestFileUtils.appendToFile(appProject.buildFile,
            "\n" +
                    "android.defaultConfig.ndk {\n" +
                    "  abiFilters('${SdkConstants.ABI_ARMEABI_V7A}')\n" +
                    "}")

        val featureProject = project.getSubproject(":feature1")
        createAbiFile(featureProject, SdkConstants.ABI_ARMEABI_V7A, "libfeature1.so")
        createAbiFile(featureProject, SdkConstants.ABI_INTEL_ATOM, "libfeature1.so")
        createAbiFile(featureProject, SdkConstants.ABI_INTEL_ATOM64, "libfeature1.so")

        TestFileUtils.appendToFile(featureProject.buildFile,
            "\n" +
                    "android.defaultConfig.ndk {\n" +
                    "  abiFilters('${SdkConstants.ABI_ARMEABI_V7A}')\n" +
                    "}")

        val bundleTaskName = getBundleTaskName("debug")
        project.execute("app:$bundleTaskName")

        val bundleFile = getApkFolderOutput("debug").bundleFile
        FileSubject.assertThat(bundleFile).exists()

        val bundleContentWithAbis = debugUnsignedContent.plus(listOf(
                "/base/native.pb",
                "/base/lib/${SdkConstants.ABI_ARMEABI_V7A}/libbase.so",
                "/feature1/native.pb",
                "/feature1/lib/${SdkConstants.ABI_ARMEABI_V7A}/libfeature1.so"))
        Zip(bundleFile).use {
            Truth.assertThat(it.entries.map { it.toString() })
                    .containsExactly(*bundleContentWithAbis)
        }
    }

    @Test
    fun `test making APKs from bundle`() {
        val apkFromBundleTaskName = getApkFromBundleTaskName("debug")

        // -------------
        // build apks for API 27
        // create a small json file with device filtering
        var jsonFile = getJsonFile(27)

        project
            .executor()
            .with(StringOption.IDE_APK_SELECT_CONFIG, jsonFile.toString())
            .run("app:$apkFromBundleTaskName")

        // fetch the build output model
        var apkFolder = getApkFolderOutput("debug").apkFolder
        FileSubject.assertThat(apkFolder).isDirectory()

        var apkFileArray = apkFolder.list() ?: fail("No Files at $apkFolder")
        Truth.assertThat(apkFileArray.toList()).named("APK List for API 27")
            .containsExactly(
                "base-master.apk",
                "base-xxhdpi.apk")

        val baseApk = File(apkFolder, "base-master.apk")
        Zip(baseApk).use {
            Truth.assertThat(it.entries.map { it.toString() })
                    .containsAllOf("/META-INF/CERT.RSA", "/META-INF/CERT.SF")
        }

        // -------------
        // build apks for API 18
        // create a small json file with device filtering
        jsonFile = getJsonFile(18)

        project
            .executor()
            .with(StringOption.IDE_APK_SELECT_CONFIG, jsonFile.toString())
            .run("app:$apkFromBundleTaskName")

        // fetch the build output model
        apkFolder = getApkFolderOutput("debug").apkFolder
        FileSubject.assertThat(apkFolder).isDirectory()

        apkFileArray = apkFolder.list() ?: fail("No Files at $apkFolder")
        Truth.assertThat(apkFileArray.toList()).named("APK List for API 18")
            .containsExactly("standalone-xxhdpi.apk")


        // Check universal APK generation too.
        project
            .executor()
            .run(":app:packageDebugUniversalApk")

        project.getSubproject("app").getBundleUniversalApk(GradleTestProject.ApkType.DEBUG).use {
            Truth.assertThat(it.entries.map { it.toString() })
                .containsAllOf("/META-INF/CERT.RSA", "/META-INF/CERT.SF")
        }

        val result = project
            .executor()
            .run(":app:packageDebugUniversalApk")
        assertThat(result.didWorkTasks).isEmpty()
    }


    @Test
    fun `test extracting instant APKs from bundle`() {
        val apkFromBundleTaskName = getApkFromBundleTaskName("debug")

        // -------------
        // build apks for API 27
        // create a small json file with device filtering
        var jsonFile = getJsonFile(27)

        val appProject = project.getSubproject(":app")
        TestFileUtils.searchAndReplace(
            File(appProject.mainSrcDir.parent, "/AndroidManifest.xml"),
            "package=",
            "xmlns:dist=\"http://schemas.android.com/apk/distribution\" package=")
        TestFileUtils.searchAndReplace(
            File(appProject.mainSrcDir.parent, "AndroidManifest.xml"),
            "<application>",
            "<dist:module dist:instant=\"true\" /> <application>")

        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":feature1").mainSrcDir.parent, "AndroidManifest.xml"),
            "dist:title=",
            "dist:instant=\"false\" dist:title=")

        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":feature2").mainSrcDir.parent, "AndroidManifest.xml"),
            "dist:onDemand=\"true\"",
            "dist:onDemand=\"false\" dist:instant=\"true\"")

        project
            .executor()
            .with(StringOption.IDE_APK_SELECT_CONFIG, jsonFile.toString())
            .run("app:$apkFromBundleTaskName")

        // fetch the build output model
        var apkFolder = getApkFolderOutput("debug").apkFolder
        FileSubject.assertThat(apkFolder).isDirectory()

        var apkFileArray = apkFolder.list() ?: fail("No Files at $apkFolder")
        Truth.assertThat(apkFileArray.toList()).named("APK List when extract instant is false")
            .containsExactly(
                "base-master.apk",
                "base-xxhdpi.apk",
                "feature2-master.apk",
                "feature2-xxhdpi.apk")

        project
            .executor()
            .with(StringOption.IDE_APK_SELECT_CONFIG, jsonFile.toString())
            .with(BooleanOption.IDE_EXTRACT_INSTANT, true)
            .run("app:$apkFromBundleTaskName")

        // fetch the build output model
        apkFolder = getApkFolderOutput("debug").apkFolder
        FileSubject.assertThat(apkFolder).isDirectory()

        apkFileArray = apkFolder.list() ?: fail("No Files at $apkFolder")
        Truth.assertThat(apkFileArray.toList()).named("APK List when extract instant is true")
            .containsExactly(
                "instant-base-master.apk",
                "instant-base-xxhdpi.apk",
                "instant-feature2-master.apk",
                "instant-feature2-xxhdpi.apk")
    }


    @Test
    fun `test overriding bundle output location`() {
        for (projectPath in listOf(":app", ":feature1", ":feature2")) {
            project.getSubproject(projectPath).buildFile.appendText(
                """
                android {
                    flavorDimensions "color"
                    productFlavors {
                        blue {
                            dimension "color"
                        }
                        red {
                            dimension "color"
                        }
                    }
                }
            """
            )
        }

        // use a relative path to the project build dir.
        project
            .executor()
            .with(StringOption.IDE_APK_LOCATION, "out/test/my-bundle")
            .run("app:bundle")


        for (flavor in listOf("red", "blue")) {
            val bundleFile = getApkFolderOutput("${flavor}Debug").bundleFile
            FileSubject.assertThat(
                FileUtils.join(
                    project.getSubproject(":app").testDir,
                    "out",
                    "test",
                    "my-bundle",
                    flavor,
                    "debug",
                    bundleFile.name))
                .exists()
        }

        // redo the test with an absolute output path this time.
        val absolutePath = tmpFile.newFolder("my-bundle").absolutePath
        project
            .executor()
            .with(StringOption.IDE_APK_LOCATION, absolutePath)
            .run("app:bundle")

        for (flavor in listOf("red", "blue")) {
            val bundleFile = getApkFolderOutput("${flavor}Debug").bundleFile
            FileSubject.assertThat(
                FileUtils.join(File(absolutePath), flavor, "debug", bundleFile.name))
                .exists()
        }
    }

    @Test
    fun `test DSL update to bundle name`() {
        val bundleTaskName = getBundleTaskName("debug")

        project.execute("app:$bundleTaskName")

        val bundleFile = getApkFolderOutput("debug").bundleFile
        FileSubject.assertThat(bundleFile).exists()

        project.getSubproject(":app").buildFile.appendText("\narchivesBaseName ='foo'")

        project.execute("app:$bundleTaskName")

        val newBundleFile = getApkFolderOutput("debug").bundleFile
        FileSubject.assertThat(newBundleFile).exists()

        // test the folder is the same as the previous one.
        Truth.assertThat(bundleFile.parentFile).isEqualTo(newBundleFile.parentFile)

        // check that the previous bundle does not exist anymore
        FileSubject.assertThat(bundleFile).doesNotExist()

    }

    @Test
    fun `test invalid debuggable combination`() {
        project.file("feature2/build.gradle").appendText(
            """
                 android.buildTypes.debug.debuggable = false
            """
        )
        val apkFromBundleTaskName = getBundleTaskName("debug")

        // use a relative path to the project build dir.
        val failure = project
            .executor()
            .expectFailure()
            .run("app:$apkFromBundleTaskName")

        val exception = Throwables.getRootCause(failure.exception!!)

        assertThat(exception).hasMessageThat().startsWith(
            "Dynamic Feature ':feature2' (build type 'debug') is not debuggable,\n" +
                    "and the corresponding build type in the base application is debuggable."
        )
        assertThat(exception).hasMessageThat()
            .contains("set android.buildTypes.debug.debuggable = true")
    }

    @Test
    fun `test ResConfig is applied`() {
        val apkFromBundleTaskName = getBundleTaskName("debug")

        val content = """
<vector xmlns:android="http://schemas.android.com/apk/res/android"
        android:width="24dp"
        android:height="24dp"
        android:viewportWidth="24.0"
        android:viewportHeight="24.0">
    <path
        android:fillColor="#FF000000"
        android:pathData="M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>
</vector>"""

        // Add some drawables in different configs.
        val resDir = FileUtils.join(project.getSubproject(":app").testDir, "src", "main", "res")
        val img1 = FileUtils.join(resDir, "drawable-mdpi", "density.xml")
        FileUtils.mkdirs(img1.parentFile)
        Files.write(img1.toPath(), content.toByteArray(Charsets.UTF_8))
        val img2 = FileUtils.join(resDir, "drawable-hdpi", "density.xml")
        FileUtils.mkdirs(img2.parentFile)
        Files.write(img2.toPath(), content.toByteArray(Charsets.UTF_8))
        val img3 = FileUtils.join(resDir, "drawable-xxxhdpi", "density.xml")
        FileUtils.mkdirs(img3.parentFile)
        Files.write(img3.toPath(), content.toByteArray(Charsets.UTF_8))
        val img4 = FileUtils.join(resDir, "drawable", "lang.xml")
        FileUtils.mkdirs(img4.parentFile)
        Files.write(img4.toPath(), content.toByteArray(Charsets.UTF_8))
        val img5 = FileUtils.join(resDir, "drawable-en", "lang.xml")
        FileUtils.mkdirs(img5.parentFile)
        Files.write(img5.toPath(), content.toByteArray(Charsets.UTF_8))
        val img6 = FileUtils.join(resDir, "drawable-es", "lang.xml")
        FileUtils.mkdirs(img6.parentFile)
        Files.write(img6.toPath(), content.toByteArray(Charsets.UTF_8))


        // First check without resConfigs.
        project.executor().run("app:$apkFromBundleTaskName")
        val bundleFile = getApkFolderOutput("debug").bundleFile
        FileSubject.assertThat(bundleFile).exists()

        Zip(bundleFile).use {
            val entries = it.entries.map { it.toString() }
            Truth.assertThat(entries).contains("/base/res/drawable-mdpi-v21/density.xml")
            Truth.assertThat(entries).contains("/base/res/drawable-hdpi-v21/density.xml")
            Truth.assertThat(entries).contains("/base/res/drawable-xxxhdpi-v21/density.xml")
            Truth.assertThat(entries).contains("/base/res/drawable-anydpi-v21/lang.xml")
            Truth.assertThat(entries).contains("/base/res/drawable-en-anydpi-v21/lang.xml")
            Truth.assertThat(entries).contains("/base/res/drawable-es-anydpi-v21/lang.xml")
        }

        project.file("app/build.gradle").appendText("""
            android.defaultConfig.resConfigs "en", "mdpi"
        """)

        project.executor().run("app:$apkFromBundleTaskName")

        // Check that unwanted configs were filtered out.
        FileSubject.assertThat(bundleFile).exists()
        Zip(bundleFile).use {
            val entries = it.entries.map { it.toString() }
            Truth.assertThat(entries).contains("/base/res/drawable-mdpi-v21/density.xml")
            Truth.assertThat(entries).doesNotContain("/base/res/drawable-hdpi-v21/density.xml")
            Truth.assertThat(entries).doesNotContain("/base/res/drawable-xxxhdpi-v21/density.xml")
            Truth.assertThat(entries).contains("/base/res/drawable-anydpi-v21/lang.xml")
            Truth.assertThat(entries).contains("/base/res/drawable-en-anydpi-v21/lang.xml")
            Truth.assertThat(entries).doesNotContain("/base/res/drawable-es-anydpi-v21/lang.xml")
        }
    }

    @Test
    fun `test versionCode and versionName overrides`() {
        val bundleTaskName = getBundleTaskName("debug")

        project.getSubproject(":app").buildFile.appendText(
            """
            android.applicationVariants.all { variant ->
                variant.outputs.each { output ->
                    output.versionCodeOverride = 12
                    output.versionNameOverride = "12.0"
                }
            }
            """
        )

        project.execute("app:$bundleTaskName")

        // first check that the app metadata file contains overridden values
        val appMetadataFile =
            FileUtils.join(
                project.getSubproject("app").buildDir,
                "intermediates",
                "metadata_base_module_declaration",
                "debug",
                "application-metadata.json")
        FileSubject.assertThat(appMetadataFile).isFile()
        FileSubject.assertThat(appMetadataFile).contains("\"versionCode\":\"12\"")
        FileSubject.assertThat(appMetadataFile).contains("\"versionName\":\"12.0\"")


        // then check that overridden values were incorporated into all of the merged manifests.
        for (moduleName in listOf("app", "feature1", "feature2")) {
            val manifestFile =
                FileUtils.join(
                    project.getSubproject(moduleName).buildDir,
                    "intermediates",
                    "merged_manifests",
                    "debug",
                    "AndroidManifest.xml")
            FileSubject.assertThat(manifestFile).isFile()
            FileSubject.assertThat(manifestFile).contains("android:versionCode=\"12\"")
            FileSubject.assertThat(manifestFile).contains("android:versionName=\"12.0\"")
        }
    }

    @Test
    fun `test bundleRelease is signed correctly`() {
        val unicodeStorePass = "парольОтStoreåçêÏñüöé"
        val unicodeKeyPass = "парольОтKeyåçêÏñüöé"
        val keyAlias = "key0"

        val keyStoreFile = tmpFile.root.resolve("keystore")
        KeystoreHelper.createNewStore(
            "jks",
            keyStoreFile,
            unicodeStorePass,
            unicodeKeyPass,
            keyAlias,
            "CN=Bundle signing test",
            100)

        val bundleTaskName = getBundleTaskName("release")
        project.executor()
            .with(StringOption.IDE_SIGNING_STORE_FILE, keyStoreFile.path)
            .with(StringOption.IDE_SIGNING_STORE_PASSWORD, unicodeStorePass)
            .with(StringOption.IDE_SIGNING_KEY_ALIAS, keyAlias)
            .with(StringOption.IDE_SIGNING_KEY_PASSWORD, unicodeKeyPass)
            .run("app:$bundleTaskName")

        val bundleFile = getApkFolderOutput("release").bundleFile
        FileSubject.assertThat(bundleFile).exists()

        Zip(bundleFile).use {
            val entries = it.entries.map { it.toString() }
            Truth.assertThat(entries).contains("/META-INF/MANIFEST.MF")
            Truth.assertThat(entries).contains("/META-INF/${keyAlias.toUpperCase()}.RSA")
            Truth.assertThat(entries).contains("/META-INF/${keyAlias.toUpperCase()}.SF")
        }

        val result = ApkVerifier.Builder(bundleFile)
            .setMaxCheckedPlatformVersion(18)
            .setMinCheckedPlatformVersion(18)
            .build()
            .verify()
        assertThat(result.isVerified).isTrue()

        assertThat(
            ProcessBuilder(
                listOf(
                    getJarSignerPath(),
                    "-verify",
                    bundleFile.absolutePath))
                .start()
                .waitFor())
            .isEqualTo(0)
    }

    @Test
    fun `test excluding sources for release`() {
        val bundleTaskName = getBundleTaskName("release")

        // First run without the flag so that we get the original sizes
        project.executor()
            .with(BooleanOption.EXCLUDE_RES_SOURCES_FOR_RELEASE_BUNDLES, false)
            .run("app:$bundleTaskName")

        val bundleFile = getApkFolderOutput("release").bundleFile
        FileSubject.assertThat(bundleFile).exists()

        val bundleTimestamp = bundleFile.lastModified()

        val fileToSizeMap = HashMap<String, Int>()
        Zip(bundleFile).use {bundle ->
            bundle.entries.filter { it.toString().endsWith("resources.pb") }.forEach {
                val size = Files.readAllBytes(it).size
                assertThat(size).isGreaterThan(0)
                fileToSizeMap[it.toString()] = size
            }
        }
        assertThat(fileToSizeMap.keys).hasSize(3)

        // Now run with the flag turned on - it should re-link the resources
        project.executor()
            .with(BooleanOption.EXCLUDE_RES_SOURCES_FOR_RELEASE_BUNDLES, true)
            .run("app:$bundleTaskName")

        // Make sure that the file exists and that it was updated
        FileSubject.assertThat(bundleFile).exists()
        assertThat(bundleFile.lastModified()).isNotEqualTo(bundleTimestamp)

        // Make sure the resources.pb files are smaller. 
        // For this project the savings are (for the current version of aapt2):
        // /feature2/resources.pb: 456 -> 158
        // /feature1/resources.pb: 454 -> 156
        // /base/resources.pb: 11527 -> 9215
        Zip(bundleFile).use {bundle ->
            fileToSizeMap.forEach { file, size ->
                val newEntry = bundle.getEntry(file)!!
                assertThat(size).isGreaterThan(Files.readAllBytes(newEntry).size)
            }
        }
    }

    private fun getBundleTaskName(name: String): String {
        // query the model to get the task name
        val syncModels = project.model()
            .fetchAndroidProjects()
        val appModel =
            syncModels.rootBuildModelMap[":app"] ?: fail("Failed to get sync model for :app module")

        val debugArtifact = appModel.getVariantByName(name).mainArtifact
        return debugArtifact.bundleTaskName ?: fail("Module App does not have bundle task name")
    }

    private fun getApkFromBundleTaskName(name: String): String {
        // query the model to get the task name
        val syncModels = project.model()
            .fetchAndroidProjects()
        val appModel =
            syncModels.rootBuildModelMap[":app"] ?: fail("Failed to get sync model for :app module")

        val debugArtifact = appModel.getVariantByName(name).mainArtifact
        return debugArtifact.apkFromBundleTaskName ?: fail("Module App does not have apkFromBundle task name")
    }

    private fun getApkFolderOutput(variantName: String): AppBundleVariantBuildOutput {
        val outputModels = project.model()
            .fetchContainer(AppBundleProjectBuildOutput::class.java)

        val outputAppModel = outputModels.rootBuildModelMap[":app"]
                ?: fail("Failed to get output model for :app module")

        return outputAppModel.getOutputByName(variantName)
    }

    private fun getJsonFile(api: Int): Path {
        val tempFile = Files.createTempFile("", "dynamic-app-test")

        Files.write(
            tempFile, listOf(
                "{ \"supportedAbis\": [ \"X86\", \"ARMEABI_V7A\" ], \"supportedLocales\": [ \"en\", \"fr\" ], \"screenDensity\": 480, \"sdkVersion\": $api }"
            )
        )

        return tempFile
    }

    private fun createAbiFile(
        project: GradleTestProject,
        abiName: String,
        libName: String
    ) {
        val abiFolder = File(project.getMainSrcDir("jniLibs"), abiName)
        FileUtils.mkdirs(abiFolder)

        Files.write(File(abiFolder, libName).toPath(), "some content".toByteArray())
    }

    companion object {

        private val jarSignerExecutable =
            if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) "jarsigner.exe"
            else "jarsigner"

        /**
         * Return the "jarsigner" tool location or null if it cannot be determined.
         */
        private fun locatedJarSigner(): File? {
            // Look in the java.home bin folder, on jdk installations or Mac OS X, this is where the
            // javasigner will be located.
            val javaHome = File(System.getProperty("java.home"))
            var jarSigner = getJarSigner(javaHome)
            if (jarSigner.exists()) {
                return jarSigner
            } else {
                // if not in java.home bin, it's probable that the java.home points to a JRE
                // installation, we should then look one folder up and in the bin folder.
                jarSigner = getJarSigner(javaHome.parentFile)
                // if still cant' find it, give up.
                return if (jarSigner.exists()) jarSigner else null
            }
        }

        /**
         * Returns the jarsigner tool location with the bin folder.
         */
        private fun getJarSigner(parentDir: File) = File(File(parentDir, "bin"), jarSignerExecutable)

        fun getJarSignerPath(): String {
            val jarSigner = locatedJarSigner()
            return if (jarSigner!=null) jarSigner.absolutePath else jarSignerExecutable
        }
    }
}

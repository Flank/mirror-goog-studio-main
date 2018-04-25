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
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.common.utils.getOutputByName
import com.android.build.gradle.integration.common.utils.getVariantByName
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.builder.model.AndroidProject
import com.android.builder.model.AppBundleProjectBuildOutput
import com.android.builder.model.AppBundleVariantBuildOutput
import com.android.testutils.apk.Dex
import com.android.testutils.apk.Zip
import com.android.testutils.truth.DexSubject.assertThat
import com.android.testutils.truth.FileSubject
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.fail

class DynamicAppTest {

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("dynamicApp")
        .withoutNdk()
        .create()

    private val bundleContent: Array<String> = arrayOf(
        "/BUNDLE-METADATA/com.android.tools.build.bundletool/mainDexList.txt",
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

    private val debugSignedContent: Array<String> = bundleContent.plus(arrayOf(
        "/base/dex/classes2.dex", // Legacy multidex has minimal main dex in debug mode.
        "/META-INF/ANDROIDD.RSA",
        "/META-INF/ANDROIDD.SF",
        "/META-INF/MANIFEST.MF"))
    
    private val mainDexClasses: List<String> = listOf(
        "Landroid/support/multidex/MultiDex;",
        "Landroid/support/multidex/MultiDexApplication;",
        "Landroid/support/multidex/MultiDexExtractor;",
        "Landroid/support/multidex/MultiDexExtractor\$1;",
        "Landroid/support/multidex/MultiDexExtractor\$ExtractedDex;",
        "Landroid/support/multidex/MultiDex\$V14;",
        "Landroid/support/multidex/MultiDex\$V19;",
        "Landroid/support/multidex/MultiDex\$V4;",
        "Landroid/support/multidex/ZipUtil;",
        "Landroid/support/multidex/ZipUtil\$CentralDirectory;",
        "Lcom/example/app/AppClassNeededInMainDexList;")

    private val mainDexListClassesInBundle: List<String> =
        mainDexClasses.plus("Lcom/example/feature1/Feature1ClassNeededInMainDexList;")
    
    @Test
    @Throws(IOException::class)
    fun `test model contains feature information`() {
        val rootBuildModelMap = project.model()
            .allowOptionWarning(BooleanOption.USE_AAPT2_FROM_MAVEN)
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
    fun `test bundleDebug task`() {
        val bundleTaskName = getBundleTaskName("debug")
        project.execute("app:$bundleTaskName")

        val bundleFile = getApkFolderOutput("debug").bundleFile
        FileSubject.assertThat(bundleFile).exists()

        Zip(bundleFile).use {
            Truth.assertThat(it.entries.map { it.toString() }).containsExactly(*debugSignedContent)
            val dex = Dex(it.getEntry("base/dex/classes.dex")!!)
            // Legacy multidex is applied to the dex of the base directly for the case
            // when the build author has excluded all the features from fusing.
            assertThat(dex).containsExactlyClassesIn(mainDexClasses)

            // The main dex list must also analyze the classes from features.
            val mainDexListInBundle =
                Files.readAllLines(it.getEntry("/BUNDLE-METADATA/com.android.tools.build.bundletool/mainDexList.txt")).map { "L" + it.removeSuffix(".class") + ";" }

            assertThat(mainDexListInBundle).containsExactlyElementsIn(mainDexListClassesInBundle)

        }

        // also test that the feature manifest contains the feature name.
        val manifestFile = FileUtils.join(project.getSubproject("feature1").buildDir,
            "intermediates",
            "merged_manifests",
            "debug",
            "processDebugManifest",
            "merged",
            "AndroidManifest.xml")
        FileSubject.assertThat(manifestFile).isFile()
        FileSubject.assertThat(manifestFile).contains("android:splitName=\"feature1\"")
    }

    @Test
    fun `test unsigned bundleRelease task`() {
        val bundleTaskName = getBundleTaskName("release")
        project.execute("app:$bundleTaskName")

        val bundleFile = getApkFolderOutput("release").bundleFile
        FileSubject.assertThat(bundleFile).exists()

        Zip(bundleFile).use {
            Truth.assertThat(it.entries.map { it.toString() }).containsExactly(*bundleContent)
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
            Truth.assertThat(it.entries.map { it.toString() }).containsExactly(*debugSignedContent)
        }
    }

    @Test
    fun `test abiFilter with Bundle task`() {
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

        val bundleContentWithAbis = debugSignedContent.plus(listOf(
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
                "base-xxhdpi.apk",
                "feature1-master.apk",
                "feature1-xxhdpi.apk",
                "feature2-master.apk",
                "feature2-xxhdpi.apk")

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
    }

    private fun getBundleTaskName(name: String): String {
        // query the model to get the task name
        val syncModels = project.model()
            .allowOptionWarning(BooleanOption.USE_AAPT2_FROM_MAVEN)
            .fetchAndroidProjects()
        val appModel =
            syncModels.rootBuildModelMap[":app"] ?: fail("Failed to get sync model for :app module")

        val debugArtifact = appModel.getVariantByName(name).mainArtifact
        return debugArtifact.bundleTaskName ?: fail("Module App does not have bundle task name")
    }

    private fun getApkFromBundleTaskName(name: String): String {
        // query the model to get the task name
        val syncModels = project.model()
            .allowOptionWarning(BooleanOption.USE_AAPT2_FROM_MAVEN)
            .fetchAndroidProjects()
        val appModel =
            syncModels.rootBuildModelMap[":app"] ?: fail("Failed to get sync model for :app module")

        val debugArtifact = appModel.getVariantByName(name).mainArtifact
        return debugArtifact.apkFromBundleTaskName ?: fail("Module App does not have apkFromBundle task name")
    }

    private fun getApkFolderOutput(variantName: String): AppBundleVariantBuildOutput {
        val outputModels = project.model()
            .allowOptionWarning(BooleanOption.USE_AAPT2_FROM_MAVEN)
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
}

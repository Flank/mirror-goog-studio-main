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

package com.android.tools.lint.client.api

import com.android.prefs.FakeAndroidLocationsProvider
import com.android.repository.api.ConsoleProgressIndicator
import com.android.repository.api.ProgressIndicatorAdapter
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.OptionalLibrary
import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.lint.checks.infrastructure.TestLintClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Test for [SimplePlatformLookup].
 *
 * Idea: run the SDK lookup on the user's real SDK folder too
 * and compare [SimplePlatformLookup] with one backed with
 * [AndroidSdkHandler] as the [testAllPlatforms] test currently does.
 */
class SimplePlatformLookupTest {
    @get:Rule
    var temporaryFolder = TemporaryFolder()

    @get:Rule
    val homeFolder = TemporaryFolder()

    private fun checkQueries(checks: (PlatformLookup) -> Unit) {
        val sdkFolder = createSampleSdk()
        checkQueries(sdkFolder, checks)
    }

    private fun checkQueries(sdkFolder: File, checks: (PlatformLookup) -> Unit) {
        checkWithSimple(sdkFolder, checks)
        checkWithFull(sdkFolder, checks)
    }

    private fun checkWithFull(
        sdkFolder: File,
        checks: (PlatformLookup) -> Unit
    ) {
        // Now process the same folder with the real SDK manager to see
        // how it does
        val handler = AndroidSdkHandler.getInstance(
            FakeAndroidLocationsProvider(homeFolder.root.toPath()),
            sdkFolder.toPath()
        )

        val logger = object : ConsoleProgressIndicator() {
            override fun logInfo(s: String) {
            }

            override fun logVerbose(s: String) {
            }

            override fun logError(s: String) {
                fail(s)
            }

            override fun logWarning(s: String) {
            }
        }
        val sdkLookup = SdkManagerPlatformLookup(handler, logger)
        checks(sdkLookup)
    }

    private fun createSimpleLookup(sdkFolder: File): PlatformLookup {
        // SimplePlatformLookup is internal so can't access it directly;
        // we'll have LintClient do it on our behalf:
        val client = object : TestLintClient() {
            override fun getSdkHome(): File = sdkFolder
        }
        val platformLookup = client.getPlatformLookup()!!
        assertNotSame(platformLookup.javaClass, SdkManagerPlatformLookup::class.java)
        return platformLookup
    }

    private fun checkWithSimple(
        sdkFolder: File,
        checks: (PlatformLookup) -> Unit
    ) {
        val lookup = createSimpleLookup(sdkFolder)
        checks(lookup)
    }

    @Test
    fun testNotExistent() {
        assertEquals(
            "[]",
            createSimpleLookup(File("/non/exist/ent")).getTargets().toString()
        )
    }

    @Test
    fun testEmpty() {
        val root = temporaryFolder.newFolder("mysdk")
        File(root, "platforms").mkdirs()

        assertEquals(
            "[]",
            createSimpleLookup(root).getTargets().toString()
        )
    }

    @Test
    fun testLatest() {
        checkQueries { lookup ->
            assertEquals(
                "Platform android-30; api=API 30, rev=3",
                lookup.getLatestSdkTarget(includePreviews = false).describe()
            )
//            assertEquals(
//                "Platform android-S; api=API 30, S preview, rev=2",
//                lookup.getLatestSdkTarget(includePreviews = true).describe()
//            )
        }
    }

    fun IAndroidTarget?.describe(): String {
        if (this == null) {
            return "null"
        }
        val sb = StringBuilder()
        with(sb) {
            append(if (isPlatform) "Platform" else "Add-on")
            append(" ")
            append(hashString())
            append("; api=")
            append(version)
            append(", rev=")
            append(revision)
            append("\n")
        }
        return sb.toString().trim()
    }

    @Test
    fun testLookupVersion() {
        checkQueries { lookup ->
            assertEquals(
                "null", lookup.getTarget("unknown").describe()
            )

            // Has both stable and preview at this level: return the stable one
            assertEquals(
                "Platform android-29; api=API 29, rev=5",
                lookup.getTarget(29).describe()
            )
            // Return preview if we specifically ask for it
            assertEquals(
                "Platform android-R; api=API 29, R preview, rev=4",
                lookup.getTarget("android-R").describe()
            )
            // Only has preview at that API level: return it
            assertEquals(
                "Platform android-O; api=API 25, O preview, rev=1",
                lookup.getTarget(25).describe()
            )
        }
    }

    fun testGetFile() {
        checkQueries { lookup ->
            val target = lookup.getTarget("android-R")!!
            val file = target.getPath(IAndroidTarget.ANDROID_JAR).toFile()
            assertEquals("android.jar", file.name)
            assertTrue(file.path.contains("android-R"))
        }
    }

    @Test
    fun testAddOns() {
        if (!SUPPORTS_ADD_ONS) {
            return
        }
        val sdk = createSampleSdk()
        val lookup = createSimpleLookup(sdk)

        lookup.getTargets().asSequence().filter { !it.isPlatform }.firstOrNull {
            error("Expected only platforms")
        }

        assertEquals(
            "Add-on google:google_apis:18: api=18",
            lookup.getTarget("google:google_apis:18").describe()
        )
        assertEquals(
            "Add-on barnes_and_noble_inc:nook_tablet:10: api=10",
            lookup.getTarget("barnes_and_noble_inc:nook_tablet:10").describe()
        )
    }

    @Test
    fun testAllPlatforms() {
        checkQueries { lookup ->
            val list = lookup.getTargets()
            val sb = StringBuilder()
            with(sb) {
                list.forEach { target ->
                    append(target.describe())
                    append("\n")
                }
            }
            val string = sb.toString().trim()

            // Check sorting, correct parsing of package.xml and source.properties files
            assertEquals(
                "Platform android-2; api=API 2, rev=1\n" +
                    "Platform android-3; api=API 3, rev=4\n" +
                    "Platform android-4; api=API 4, rev=3\n" +
                    "Platform android-5; api=API 5, rev=1\n" +
                    "Platform android-6; api=API 6, rev=1\n" +
                    "Platform android-7; api=API 7, rev=3\n" +
                    "Platform android-8; api=API 8, rev=3\n" +
                    "Platform android-9; api=API 9, rev=2\n" +
                    "Platform android-10; api=API 10, rev=2\n" +
                    "Platform android-11; api=API 11, rev=2\n" +
                    "Platform android-12; api=API 12, rev=3\n" +
                    "Platform android-13; api=API 13, rev=1\n" +
                    "Platform android-15; api=API 15, rev=5\n" +
                    "Platform android-16; api=API 16, rev=5\n" +
                    "Platform android-17; api=API 17, rev=3\n" +
                    "Platform android-18; api=API 18, rev=3\n" +
                    "Platform android-19; api=API 19, rev=4\n" +
                    "Platform android-20; api=API 20, rev=2\n" +
                    "Platform android-21; api=API 21, rev=2\n" +
                    "Platform android-22; api=API 22, rev=2\n" +
                    "Platform android-23; api=API 23, rev=3\n" +
                    "Platform android-24; api=API 24, rev=2\n" +
                    "Platform android-O; api=API 25, O preview, rev=1\n" +
                    "Platform android-26; api=API 26, rev=2\n" +
                    "Platform android-27; api=API 27, rev=3\n" +
                    "Platform android-28; api=API 28, rev=6\n" +
                    "Platform android-29; api=API 29, rev=5\n" +
                    "Platform android-R; api=API 29, R preview, rev=4\n" +
                    "Platform android-30; api=API 30, rev=3\n" +
                    "Platform android-S; api=API 30, S preview, rev=2",
                string
            )
        }
    }

    @Test
    fun testPseudoTarget() {
        val sdk = temporaryFolder.newFolder("sdk")
        createSamplePlatform(sdk, "stable", 26, null, 8)
        createSamplePlatform(sdk, "experimental", 30, null, 1)

        // Only checking with the non-SDK manager since SDK manager doesn't support
        // these pseudo locales (and only the non-SDK manager lookup will be used
        // in the context where this is relevant
        checkWithSimple(sdk) { lookup ->
            val list = lookup.getTargets()
            val sb = StringBuilder()
            with(sb) {
                list.forEach { target ->
                    append(target.describe())
                    append("\n")
                }
            }
            val string = sb.toString().trim()

            // Check sorting, correct parsing of package.xml and source.properties files
            assertEquals(
                "Platform stable; api=API 26, rev=8\n" +
                    "Platform experimental; api=API 30, rev=1",
                string
            )
        }
    }

    @Test
    fun testOptional() {
        fun OptionalLibrary.describe(): String {
            return "OptionalLibrary(" + name + "," + localJarPath + "," + isManifestEntryRequired + ")"
        }
        checkQueries { lookup ->
            val target = lookup.getTarget("android-26")
            assertNotNull(target)
            assertEquals(
                // Order should be same as in the optional.json file
                "OptionalLibrary(org.apache.http.legacy,org.apache.http.legacy.jar,false)\n" +
                    "OptionalLibrary(android.test.mock,android.\"test\".mock.jar,false)\n" +
                    "OptionalLibrary(android.test.base,android.test.base.jar,false)\n" +
                    "OptionalLibrary(android.test.runner,android.test.runner.jar,true)",
                target?.optionalLibraries?.joinToString(separator = "\n") { it.describe() }
            )
        }
    }

    @Test
    fun testCompareRealAndroidSdk() {
        // Runs a getTargets() and prettyprint on a real SDK folder in the
        // test user's account, and make sure that they're the same.
        // This will catch problems if for some reason a future SDK has
        // a different shape not covered by the mocks.
        val home = System.getenv("ANDROID_SDK_ROOT")
            ?: System.getenv("ANDROID_HOME")
            ?: return
        val sdkHome = File(home)
        if (!sdkHome.exists()) {
            return
        }

        var full = ""

        // Capture targets found and sorted by the real SDK manager
        checkWithFull(sdkHome) { lookup ->
            val targets = lookup.getTargets().asSequence()
            full = targets.sorted().joinToString("\n") { it.describe() }
        }

        // Compare with targets found by the light weight SDK manager
        checkWithSimple(sdkHome) { lookup ->
            val targets = lookup.getTargets().asSequence()
            val simple = targets.sorted().joinToString("\n") { it.describe() }
            assertEquals(full, simple)
        }
    }

    // --------------------------------------------------------------------------
    // Test infrastructure beyond this point
    // --------------------------------------------------------------------------

    private fun createSampleSdk(): File {
        val sdk = temporaryFolder.newFolder("sdk")
        // Based on actual data in my SDK folder
        createSamplePlatform(sdk, "android-2", 2, null, 1)
        createSamplePlatform(sdk, "android-3", 3, null, 4)
        createSamplePlatform(sdk, "android-4", 4, null, 3)
        createSamplePlatform(sdk, "android-5", 5, null, 1)
        createSamplePlatform(sdk, "android-6", 6, null, 1)
        createSamplePlatform(sdk, "android-7", 7, null, 3)
        createSamplePlatform(sdk, "android-8", 8, null, 3)
        createSamplePlatform(sdk, "android-9", 9, null, 2)
        createSamplePlatform(sdk, "android-10", 10, null, 2)
        createSamplePlatform(sdk, "android-11", 11, null, 2)
        createSamplePlatform(sdk, "android-12", 12, null, 3)
        createSamplePlatform(sdk, "android-13", 13, null, 1)
        createSamplePlatform(sdk, "android-15", 15, null, 5)
        createSamplePlatform(sdk, "android-16", 16, null, 5)
        createSamplePlatform(sdk, "android-17", 17, null, 3)
        createSamplePlatform(sdk, "android-18", 18, null, 3)
        createSamplePlatform(sdk, "android-19", 19, null, 4)
        createSamplePlatform(sdk, "android-20", 20, null, 2)
        createSamplePlatform(sdk, "android-21", 21, null, 2)
        createSamplePlatform(sdk, "android-22", 22, null, 2)
        createSamplePlatform(sdk, "android-23", 23, null, 3)
        createSamplePlatform(sdk, "android-24", 24, null, 2)
        // Deliberately removed to test scenario where an API level only has a preview
        // createSamplePlatform(sdk, "android-25", 25, null, 3)
        createSamplePlatform(sdk, "android-O", 25, "O", 1)
        createSamplePlatform(sdk, "android-26", 26, null, 2)
        createSamplePlatform(sdk, "android-27", 27, null, 3)
        createSamplePlatform(sdk, "android-28", 28, null, 6)
        createSamplePlatform(sdk, "android-29", 29, null, 5)
        createSamplePlatform(sdk, "android-R", 29, "R", 4)
        createSamplePlatform(sdk, "android-30", 30, null, 3)

        // Not a real target as of this writing but here to
        // test getLatest+includePreviews
        createSamplePlatform(sdk, "android-S", 30, "S", 2)

        createSampleAddOn(sdk, "google", "google_apis", 22)
        if (!SUPPORTS_ADD_ONS) {
            // Keep one in the install directory to make sure we properly
            // ignore it, but no need to test all the corner cases
            return sdk
        }
        createSampleAddOn(sdk, "google", "google_apis", 21)
        createSampleAddOn(sdk, "barnes_and_noble_inc", "nookcolor", 8)
        createSampleAddOn(sdk, "google", "google_apis", 16)
        createSampleAddOn(sdk, "google", "google_apis", 17)
        createSampleAddOn(sdk, "amazon", "amazon_fire_tablet_addon", 19)
        createSampleAddOn(sdk, "google", "google_apis", 19)
        createSampleAddOn(sdk, "barnes_and_noble_inc", "nook_tablet", 10)
        createSampleAddOn(sdk, "google", "google_apis", 15)
        createSampleAddOn(sdk, "nook", "nooksdk", 15)
        createSampleAddOn(sdk, "google", "google_apis_x86", 19)
        createSampleAddOn(sdk, "google", "google_gdk", 19)
        createSampleAddOn(sdk, "google", "google_apis", 10)
        createSampleAddOn(sdk, "amazon", "amazon_fire_tv_addon", 17)
        createSampleAddOn(sdk, "google", "google_apis", 18)
        createSampleAddOn(sdk, "amazon", "amazon_fire_phone_addon", 17)
        createSampleAddOn(sdk, "google", "google_apis", 23)
        // Make sure we never return this as the latest "platform"
        createSampleAddOn(sdk, "fake", "fake", 100)

        return sdk
    }

    private fun createSamplePlatform(
        sdk: File,
        hash: String,
        api: Int,
        codename: String?,
        revision: Int
    ) {
        val platforms = File(sdk, "platforms")
        val folder = File(platforms, hash)
        folder.mkdirs()
        val androidJar = File(folder, "android.jar")
        androidJar.createNewFile()
        val version: String = SdkVersionInfo.getVersionString(api) ?: api.toString()
        // Randomize if we're using source.properties files or package.xml
        if (Math.random() >= 0.5) {
            val content =
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?><ns5:sdk-repository xmlns:ns2="http://schemas.android.com/repository/android/common/01" xmlns:ns3="http://schemas.android.com/sdk/android/repo/addon2/01" xmlns:ns4="http://schemas.android.com/sdk/android/repo/sys-img2/01" xmlns:ns5="http://schemas.android.com/sdk/android/repo/repository2/01">
                <localPackage path="platforms;$hash" obsolete="false"><type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns5:platformDetailsType"><api-level>$api</api-level>${if (codename != null || api <= 21) "<codename>${codename ?: ""}</codename>" else ""}<layoutlib api="12"/></type-details><revision><major>$revision</major></revision><display-name>Android SDK Platform 15, rev 5</display-name><dependencies><dependency path="tools"><min-revision><major>21</major></min-revision></dependency></dependencies></localPackage></ns5:sdk-repository>
                """.trimIndent()
            File(folder, "package.xml").writeText(content)
        } else {
            val content = if (api <= 21)
                """
                ### Android Tool: Source of this archive.
                #Fri Oct 17 09:46:34 PDT 2014
                AndroidVersion.ApiLevel=$api
                ${if (codename != null) "AndroidVersion.CodeName=$codename\n" else ""}Layoutlib.Revision=$api
                Pkg.Desc=Android SDK Platform $version
                Pkg.License=To get started ... Abbreviated in test.\n
                Pkg.Revision=$revision
                Pkg.SourceUrl=https\://dl-ssl.google.com/android/repository/repository-$api.xml
                Platform.MinToolsRev=21
                Platform.Version=$version
                Platform.CodeName=${SdkVersionInfo.getBuildCode(api) ?: ""}
                """.trimIndent()
            else
                """
                Pkg.Desc=Android SDK Platform $version
                Pkg.UserSrc=false
                Platform.Version=$version
                Platform.CodeName=
                Pkg.Revision=$revision
                AndroidVersion.ApiLevel=$api
                ${if (codename != null) "AndroidVersion.CodeName=$codename\n" else ""}Layoutlib.Api=$api
                Layoutlib.Revision=$revision
                Platform.MinToolsRev=22
                """.trimIndent()
            File(folder, "source.properties").writeText(content)
        }

        val buildProp =
            """
            # begin build properties
            # autogenerated by buildinfo.sh
            ro.build.display.id=sdk-eng ${SdkVersionInfo.getVersionString(api)} 1406430 test-keys
            ro.build.version.incremental=1406430
            ro.build.version.sdk=$api
            ro.build.version.codename=REL
            ro.build.version.release=${SdkVersionInfo.getVersionString(api)}
            ro.build.date=Thu Sep  4 02:06:48 UTC 2014
            ro.build.date.utc=1409796408
            ro.build.type=eng
            """.trimIndent()
        File(folder, "build.prop").writeText(buildProp)

        if (api >= 23) {
            val optional = File(folder, "optional")
            optional.mkdirs()
            File(optional, "optional.json").writeText(
                """
                [
                  {
                    "name": "org.apache.http.legacy",
                    "jar": "org.apache.http.legacy.jar",
                    "manifest": false
                  },
                  {
                    "name": "android.test.mock",
                    "jar": "android.\"test\".mock.jar",
                    "manifest": false
                  },
                  {
                    "name": "android.test.base",
                    "jar": "android.test.base.jar",
                    "manifest": false
                  },
                  {
                    "name": "android.test.runner",
                    "jar": "android.test.runner.jar",
                    "manifest": true
                  }
                ]
                """.trimIndent()
            )
        }
    }

    private fun createSampleAddOn(
        sdk: File,
        vendor: String,
        id: String,
        api: Int
    ) {
        val platforms = File(sdk, "add-ons")
        val folderName = "addon-$id-$vendor-$api"
        val folder = File(platforms, folderName)
        folder.mkdirs()

        val vendorName = vendor.capitalize()
        val name = id.capitalize()

        // Randomize if we're using source.properties files or package.xml
        val content =
            """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?><ns2:repository xmlns:ns2="http://schemas.android.com/repository/android/common/01" xmlns:ns3="http://schemas.android.com/sdk/android/repo/addon2/01" xmlns:ns4="http://schemas.android.com/sdk/android/repo/sys-img2/01" xmlns:ns5="http://schemas.android.com/repository/android/generic/01" xmlns:ns6="http://schemas.android.com/sdk/android/repo/repository2/01">
            <localPackage path="add-ons;addon-$id-$vendor-$api" obsolete="false"><type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns3:addonDetailsType"><api-level>$api</api-level><codename></codename><vendor><id>$vendor</id><display>$vendorName</display></vendor><tag><id>$id</id><display>$name</display></tag><libraries><library localJarPath="$id.jar" name="com.example.$id"><description>blahblahblah</description></library><library localJarPath="usb.jar" name="com.android.future.usb.accessory"><description>API for USB Accessories</description></library><library localJarPath="effects.jar" name="com.google.android.media.effects"><description>Collection of video effects</description></library></libraries></type-details><revision><major>4</major></revision><display-name>$name</display-name></localPackage></ns2:repository>
            """.trimIndent()
        File(folder, "package.xml").writeText(content)
    }

    class SdkManagerPlatformLookup(
        private val sdkHandler: AndroidSdkHandler,
        private val logger: ProgressIndicatorAdapter = TestLogger()
    ) : PlatformLookup {
        override fun getLatestSdkTarget(
            minApi: Int,
            includePreviews: Boolean,
            includeAddOns: Boolean
        ): IAndroidTarget? {
            val targets = getTargets(includeAddOns)
            for (i in targets.indices.reversed()) {
                val target = targets[i]
                if ((includeAddOns || target.isPlatform) &&
                    target.version.featureLevel >= minApi &&
                    (includePreviews || target.version.codename == null)
                ) {
                    return target
                }
            }

            return null
        }

        override fun getTarget(buildTargetHash: String): IAndroidTarget? {
            if (targets == null) {
                val manager = sdkHandler.getAndroidTargetManager(logger)
                val target = manager.getTargetFromHashString(buildTargetHash, logger)
                if (target != null) {
                    return target
                }

                return null
            } else {
                return targets!!.lastOrNull { it.hashString() == buildTargetHash }
            }
        }

        private var targets: List<IAndroidTarget>? = null

        override fun getTargets(includeAddOns: Boolean): List<IAndroidTarget> {
            return targets
                ?: run {
                    sdkHandler.getAndroidTargetManager(logger)
                        .getTargets(logger).filter { includeAddOns || it.isPlatform }.toList()
                        .also { targets = it }
                }
        }
    }

    private class TestLogger : ProgressIndicatorAdapter() {
        // Intentionally not logging these: the SDK manager is
        // logging events such as package.xml parsing
        //   Parsing /path/to/sdk//build-tools/19.1.0/package.xml
        //   Parsing /path/to/sdk//build-tools/20.0.0/package.xml
        //   Parsing /path/to/sdk//build-tools/21.0.0/package.xml
        // which we don't want to spam on the console.
        // It's also warning about packages that it's encountering
        // multiple times etc; that's not something we should include
        // in lint command line output.

        override fun logError(s: String, e: Throwable?) = Unit

        override fun logInfo(s: String) = Unit

        override fun logWarning(s: String, e: Throwable?) = Unit
    }
}

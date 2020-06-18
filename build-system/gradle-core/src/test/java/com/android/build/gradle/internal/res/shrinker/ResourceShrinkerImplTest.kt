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
package com.android.build.gradle.internal.res.shrinker

import com.android.aapt.Resources.Attribute.FormatFlags
import com.android.aapt.Resources.Package
import com.android.aapt.Resources.PackageId
import com.android.aapt.Resources.ResourceTable
import com.android.aapt.Resources.XmlNode
import com.android.build.gradle.internal.res.shrinker.LinkedResourcesFormat.BINARY
import com.android.build.gradle.internal.res.shrinker.gatherer.ProtoResourceTableGatherer
import com.android.build.gradle.internal.res.shrinker.graph.ProtoResourcesGraphBuilder
import com.android.build.gradle.internal.res.shrinker.obfuscation.ProguardMappingsRecorder
import com.android.build.gradle.internal.res.shrinker.usages.DexUsageRecorder
import com.android.build.gradle.internal.res.shrinker.usages.ProtoAndroidManifestUsageRecorder
import com.android.build.gradle.internal.res.shrinker.usages.ToolsAttributeUsageRecorder
import com.android.build.gradle.internal.res.shrinker.util.addAttribute
import com.android.build.gradle.internal.res.shrinker.util.addChild
import com.android.build.gradle.internal.res.shrinker.util.addNamespace
import com.android.build.gradle.internal.res.shrinker.util.addType
import com.android.build.gradle.internal.res.shrinker.util.attrEntry
import com.android.build.gradle.internal.res.shrinker.util.buildNode
import com.android.build.gradle.internal.res.shrinker.util.containsString
import com.android.build.gradle.internal.res.shrinker.util.dimenEntry
import com.android.build.gradle.internal.res.shrinker.util.externalFile
import com.android.build.gradle.internal.res.shrinker.util.idEntry
import com.android.build.gradle.internal.res.shrinker.util.setText
import com.android.build.gradle.internal.res.shrinker.util.stringEntry
import com.android.build.gradle.internal.res.shrinker.util.styleEntry
import com.android.build.gradle.internal.res.shrinker.util.toXmlString
import com.android.build.gradle.internal.res.shrinker.util.xmlElement
import com.android.build.gradle.internal.res.shrinker.util.xmlFile
import com.android.ide.common.resources.usage.ResourceUsageModel
import com.google.common.io.ByteStreams
import com.google.common.io.Resources
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.streams.toList

class ResourceShrinkerImplTest {
    companion object {
        @ClassRule
        @JvmField
        var sTemporaryFolder = TemporaryFolder()

        private val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private val AAPT_NS = "http://schemas.android.com/aapt"
        private val TOOLS_NS = "http://schemas.android.com/tools"
    }

    internal enum class CodeInput {
        NO_SHRINKER, PROGUARD, R8
    }

    enum class ResourcesVariant {
        RAW, PROTO_COMPILED
    }

    @Test
    fun `test shrinking with proguard obfuscated classes`() {
        check(CodeInput.PROGUARD, false)
        check(CodeInput.PROGUARD, true)
    }

    @Test
    fun `test shrinking with no obfuscation`() {
        check(CodeInput.NO_SHRINKER, false)
        check(CodeInput.NO_SHRINKER, true)
    }

    @Test
    fun `test shrinking with R8 obfuscation`() {
        check(CodeInput.R8, false)
        check(CodeInput.R8, true)
    }

    @Test
    fun `test support for tools_keep and tools_discard attributes`() {
        val dir = sTemporaryFolder.newFolder()

        val resources =
            createResourceFolder(dir, ResourcesVariant.PROTO_COMPILED, addKeepXml = true)
        val rawResources =
            createResourceFolder(dir.resolve("raw"), ResourcesVariant.RAW, addKeepXml = true)
        val classes = createR8Dex(dir)
        val mergedManifest = createMergedManifest(dir)
        val mapping = createMappingFile(dir)
        val resourceTable = createResourceTable(dir, addKeepXml = true)

        val analyzer = ResourceShrinkerImpl(
            resourcesGatherers = listOf(
                ProtoResourceTableGatherer(resourceTable.toPath())
            ),
            obfuscationMappingsRecorder = ProguardMappingsRecorder(mapping.toPath()),
            usageRecorders = listOf(
                DexUsageRecorder(classes.toPath()),
                ProtoAndroidManifestUsageRecorder(mergedManifest.toPath()),
                ToolsAttributeUsageRecorder(rawResources.toPath())
            ),
            graphBuilders = listOf(
                ProtoResourcesGraphBuilder(resources.toPath(), resourceTable.toPath())
            ),
            debugReporter = NoDebugReporter,
            supportMultipackages = false,
            usePreciseShrinking = false
        )

        analyzer.analyze()
        checkState(analyzer)

        assertEquals(
            """
                @attr/myAttr1 : reachable=false
                @attr/myAttr2 : reachable=false
                @dimen/activity_horizontal_margin : reachable=true
                @dimen/activity_vertical_margin : reachable=true
                @drawable/avd_heart_fill : reachable=false
                    @drawable/avd_heart_fill_1
                    @drawable/avd_heart_fill_2
                @drawable/avd_heart_fill_1 : reachable=true
                @drawable/avd_heart_fill_2 : reachable=false
                @drawable/ic_launcher : reachable=true
                @drawable/unused : reachable=false
                @id/action_settings : reachable=true
                @id/action_settings2 : reachable=false
                @layout/activity_main : reachable=true
                    @dimen/activity_vertical_margin
                    @dimen/activity_horizontal_margin
                    @string/hello_world
                    @style/MyStyle_Child
                @menu/main : reachable=false
                    @string/action_settings
                @menu/menu2 : reachable=false
                    @string/action_settings2
                @raw/android_wear_micro_apk : reachable=true
                @raw/index1 : reachable=false
                    @raw/my_used_raw_drawable
                @raw/keep : reachable=false
                @raw/my_js : reachable=false
                @raw/my_used_raw_drawable : reachable=false
                @raw/styles2 : reachable=false
                @string/action_settings : reachable=false
                @string/action_settings2 : reachable=false
                @string/alias : reachable=false
                    @string/app_name
                @string/app_name : reachable=true
                @string/hello_world : reachable=true
                @style/AppTheme : reachable=false
                @style/MyStyle : reachable=true
                @style/MyStyle_Child : reachable=true
                    @style/MyStyle
                @xml/android_wear_micro_apk : reachable=true
                    @raw/android_wear_micro_apk
            """.trimIndent(),
            analyzer.model.resourceStore.dumpResourceModel().trim()
        )
    }

    @Test
    fun `test analyze phase`() {
        val dir = sTemporaryFolder.newFolder()

        val classes = createR8Dex(dir)
        val mergedManifest = createMergedManifest(dir)
        val resources = createResourceFolder(dir, ResourcesVariant.PROTO_COMPILED)
        val resourceTable = createResourceTable(dir, addKeepXml = false)

        val analyzer = ResourceShrinkerImpl(
            resourcesGatherers = listOf(
                ProtoResourceTableGatherer(resourceTable.toPath())
            ),
            obfuscationMappingsRecorder = null,
            usageRecorders = listOf(
                DexUsageRecorder(classes.toPath()),
                ProtoAndroidManifestUsageRecorder(mergedManifest.toPath())
            ),
            graphBuilders = listOf(
                ProtoResourcesGraphBuilder(resources.toPath(), resourceTable.toPath())
            ),
            debugReporter = NoDebugReporter,
            supportMultipackages = false,
            usePreciseShrinking = false
        )

        analyzer.analyze()
        checkState(analyzer)

        assertEquals(
            """
                attr/myAttr1#remove
                attr/myAttr2#remove
                dimen/activity_horizontal_margin#
                dimen/activity_vertical_margin#
                drawable/avd_heart_fill#remove
                drawable/avd_heart_fill_1#remove
                drawable/avd_heart_fill_2#remove
                drawable/ic_launcher#
                drawable/unused#remove
                id/action_settings#
                id/action_settings2#remove
                layout/activity_main#
                menu/main#
                menu/menu2#remove
                raw/android_wear_micro_apk#
                raw/index1#remove
                raw/my_js#remove
                raw/my_used_raw_drawable#remove
                raw/styles2#remove
                string/action_settings#
                string/action_settings2#remove
                string/alias#remove
                string/app_name#
                string/hello_world#
                style/AppTheme#remove
                style/MyStyle#
                style/MyStyle_Child#
                xml/android_wear_micro_apk#
            """.trimIndent(),
            analyzer.model.resourceStore.dumpConfig().trim()
        )
    }

    private fun check(codeInput: CodeInput, usesPreciseShrinking: Boolean) {
        val dir = sTemporaryFolder.newFolder()

        val (classes, mapping) = when (codeInput) {
            CodeInput.PROGUARD -> Pair(createProguardedDex(dir), createMappingFile(dir))
            CodeInput.NO_SHRINKER -> Pair(createUnproguardedDex(dir), null)
            CodeInput.R8 -> Pair(createR8Dex(dir), createMappingFile(dir))
        }
        val mergedManifest = createMergedManifest(dir)
        val resources = createResourceFolder(dir, ResourcesVariant.PROTO_COMPILED)
        val resourceTable = createResourceTable(dir, addKeepXml = false)
        val analyzer = ResourceShrinkerImpl(
            resourcesGatherers = listOf(
                ProtoResourceTableGatherer(resourceTable.toPath())
            ),
            obfuscationMappingsRecorder = mapping?.let { ProguardMappingsRecorder(it.toPath()) },
            usageRecorders = listOf(
                DexUsageRecorder(classes.toPath()),
                ProtoAndroidManifestUsageRecorder(mergedManifest.toPath())
            ),
            graphBuilders = listOf(
                ProtoResourcesGraphBuilder(resources.toPath(), resourceTable.toPath())
            ),
            debugReporter = NoDebugReporter,
            supportMultipackages = false,
            usePreciseShrinking = usesPreciseShrinking
        )

        analyzer.analyze()
        checkState(analyzer)

        assertEquals(
            """
                @attr/myAttr1 : reachable=false
                @attr/myAttr2 : reachable=false
                @dimen/activity_horizontal_margin : reachable=true
                @dimen/activity_vertical_margin : reachable=true
                @drawable/avd_heart_fill : reachable=false
                    @drawable/avd_heart_fill_1
                    @drawable/avd_heart_fill_2
                @drawable/avd_heart_fill_1 : reachable=false
                @drawable/avd_heart_fill_2 : reachable=false
                @drawable/ic_launcher : reachable=true
                @drawable/unused : reachable=false
                @id/action_settings : reachable=true
                @id/action_settings2 : reachable=false
                @layout/activity_main : reachable=true
                    @dimen/activity_vertical_margin
                    @dimen/activity_horizontal_margin
                    @string/hello_world
                    @style/MyStyle_Child
                @menu/main : reachable=true
                    @string/action_settings
                @menu/menu2 : reachable=false
                    @string/action_settings2
                @raw/android_wear_micro_apk : reachable=true
                @raw/index1 : reachable=false
                    @raw/my_used_raw_drawable
                @raw/my_js : reachable=false
                @raw/my_used_raw_drawable : reachable=false
                @raw/styles2 : reachable=false
                @string/action_settings : reachable=true
                @string/action_settings2 : reachable=false
                @string/alias : reachable=false
                    @string/app_name
                @string/app_name : reachable=true
                @string/hello_world : reachable=true
                @style/AppTheme : reachable=false
                @style/MyStyle : reachable=true
                @style/MyStyle_Child : reachable=true
                    @style/MyStyle
                @xml/android_wear_micro_apk : reachable=true
                    @raw/android_wear_micro_apk
            """.trimIndent(),
            analyzer.model.resourceStore.dumpResourceModel().trim()
        )

        val unusedBitmap = resources.resolve("drawable/unused.png")
        assertTrue(unusedBitmap.exists())

        // Generate a .zip file from a directory
        val uncompressedFile = zipResourcesTo(
            resourcesDir = resources,
            inlinedResources = listOf(
                "res/drawable/avd_heart_fill_1.xml",
                "res/drawable/avd_heart_fill_2.xml"
            ),
            resourcesArsc = resourceTable,
            outZip = File(dir, "uncompressed.ap_")
        )

        assertEquals(
            """
                res/drawable-hdpi/
                res/drawable-hdpi/ic_launcher.png
                res/drawable-mdpi/
                res/drawable-mdpi/ic_launcher.png
                res/drawable-xxhdpi/
                res/drawable-xxhdpi/ic_launcher.png
                res/drawable/
                res/drawable/avd_heart_fill.xml
                res/drawable/avd_heart_fill_1.xml
                res/drawable/avd_heart_fill_2.xml
                res/drawable/unused.png
                res/layout/
                res/layout/activity_main.xml
                res/menu/
                res/menu/main.xml
                res/menu/menu2.xml
                res/raw/
                res/raw/android_wear_micro_apk.apk
                res/raw/index1.html
                res/raw/my_js.js
                res/raw/styles2.css
                res/values/
                res/xml/
                res/xml/android_wear_micro_apk.xml
                resources.pb
            """.trimIndent(),
            dumpZipContents(uncompressedFile)
        )

        val compressedFile = File(dir, "compressed.ap_")
        analyzer.rewriteResourcesInApkFormat(uncompressedFile, compressedFile, BINARY)

        if (usesPreciseShrinking) {
            assertEquals(
                """
                res/drawable-hdpi/
                res/drawable-hdpi/ic_launcher.png
                res/drawable-mdpi/
                res/drawable-mdpi/ic_launcher.png
                res/drawable-xxhdpi/
                res/drawable-xxhdpi/ic_launcher.png
                res/drawable/
                res/layout/
                res/layout/activity_main.xml
                res/menu/
                res/menu/main.xml
                res/raw/
                res/raw/android_wear_micro_apk.apk
                res/values/
                res/xml/
                res/xml/android_wear_micro_apk.xml
                resources.pb
            """.trimIndent(),
                dumpZipContents(compressedFile)
            )

            val beforeOnly = listOf(
                "res/drawable/avd_heart_fill.xml",
                "res/drawable/avd_heart_fill_1.xml",
                "res/drawable/avd_heart_fill_2.xml",
                "res/drawable/unused.png",
                "res/menu/menu2.xml",
                "res/raw/index1.html",
                "res/raw/my_js.js",
                "res/raw/styles2.css")

            val beforeAndAfter =
                listOf("res/drawable-hdpi/ic_launcher.png",
                       "res/drawable-mdpi/ic_launcher.png",
                       "res/drawable-xxhdpi/ic_launcher.png",
                       "res/layout/activity_main.xml",
                       "res/menu/main.xml",
                       "res/raw/android_wear_micro_apk.apk",
                       "res/xml/android_wear_micro_apk.xml")

            val resourceTableBefore =
                ResourceTable.parseFrom(getZipContents(uncompressedFile, "resources.pb"))
            val resourceTableAfter =
                ResourceTable.parseFrom(getZipContents(compressedFile, "resources.pb"))

            for (beforeFile in beforeOnly) {
                assertFalse(resourceTableAfter.containsString(beforeFile))
                assertTrue(resourceTableBefore.containsString(beforeFile))
            }
            for (both in beforeAndAfter) {
                assertTrue(resourceTableAfter.containsString(both))
                assertTrue(resourceTableBefore.containsString(both))
            }
            validateResourceStore(analyzer.model)
        } else {
            assertEquals(
                dumpZipContents(uncompressedFile),
                dumpZipContents(compressedFile)
            )
            assertArrayEquals(
                DummyContent.TINY_PNG,
                getZipContents(compressedFile, "res/drawable/unused.png")
            )
            assertArrayEquals(
                DummyContent.TINY_BINARY_XML,
                getZipContents(compressedFile, "res/drawable/avd_heart_fill.xml")
            )
        }
    }

    private fun validateResourceStore(model: ResourceShrinkerModel) {
        model.resourceStore.resources.forEach { resource ->
            // Validate that if a resource is reachable, then all referenced resources are also
            // reachable.
            if (resource.isReachable && resource.references != null) {
                resource.references.forEach { assertTrue(it.isReachable) }
            }
        }
    }

    /* ktlint-disable */
    private fun createResourceTable(dir: File, addKeepXml: Boolean): File {
        val basePackage = Package.newBuilder()
            .setPackageName("com.example.shrinkunittest.app")
            .setPackageId(PackageId.newBuilder().setId(0x7f))
            .addType(
                1,
                "attr",
                attrEntry(0, "myAttr1", FormatFlags.INTEGER),
                attrEntry(1, "myAttr2", FormatFlags.BOOLEAN)
            )
            .addType(
                2,
                "drawable",
                externalFile(
                    0,
                    "ic_launcher",
                    "res/drawable-hdpi/ic_launcher.png",
                    "res/drawable-mdpi/ic_launcher.png",
                    "res/drawable-xxhdpi/ic_launcher.png"
                ),
                externalFile(1, "unused", "res/drawable/unused.png"),
                xmlFile(2, "avd_heart_fill", "res/drawable/avd_heart_fill.xml"),
                xmlFile(3, "avd_heart_fill_1", "res/drawable/avd_heart_fill_1.xml"),
                xmlFile(4, "avd_heart_fill_2", "res/drawable/avd_heart_fill_2.xml")
            )
            .addType(
                3,
                "layout",
                xmlFile(0, "activity_main", "res/layout/activity_main.xml")
            )
            .addType(
                4,
                "dimen",
                dimenEntry(0, "activity_horizontal_margin", 16),
                dimenEntry(1, "activity_vertical_margin", 16)
            )
            .addType(
                5,
                "string",
                stringEntry(0, "action_settings", "Settings"),
                stringEntry(1, "alias", refId = 0x7f050002, refName = "string/app_name"),
                stringEntry(2, "app_name", "ShrinkUnitTest"),
                stringEntry(3, "hello_world", "Hello world!"),
                stringEntry(4, "action_settings2", "Settings2")
            )
            .addType(
                6,
                "style",
                styleEntry(0, "AppTheme"),
                styleEntry(1, "MyStyle"),
                styleEntry(2, "MyStyle.Child", 0x7f060001, "style/MyStyle")
            )
            .addType(
                7,
                "menu",
                xmlFile(0, "main", "res/menu/main.xml"),
                xmlFile(1, "menu2", "res/menu/menu2.xml")
            )
            .addType(
                8,
                "id",
                idEntry(0, "action_settings"),
                idEntry(1, "action_settings2")
            )
            .addType(
                9,
                "raw",
                externalFile(0, "android_wear_micro_apk", "res/raw/android_wear_micro_apk.apk"),
                externalFile(1, "index1", "res/raw/index1.html"),
                externalFile(2, "styles2", "res/raw/styles2.css"),
                externalFile(3, "my_js", "res/raw/my_js.js"),
                externalFile(4, "my_used_raw_drawable", "res/raw/my_used_raw_drawable.js"),
                if (addKeepXml) externalFile(5, "keep", "res/raw/keep.xml") else null
            )
            .addType(
                10,
                "xml",
                xmlFile(0, "android_wear_micro_apk", "res/xml/android_wear_micro_apk.xml")
            )

        val resourceTable = ResourceTable.newBuilder()
            .addPackage(basePackage)
            .build()

        return createFile(dir, "resources.pb", resourceTable.toByteArray())
    }

    private fun createResourceFolder(
        dir: File,
        format: ResourcesVariant,
        addKeepXml: Boolean = false
    ): File {
        val resources = File(
            dir,
            "app/build/res/all/release".replace('/', File.separatorChar)
        )
        resources.mkdirs()

        createFile(resources, "drawable-hdpi/ic_launcher.png", ByteArray(0))
        createFile(resources, "drawable-mdpi/ic_launcher.png", ByteArray(0))
        createFile(resources, "drawable-xxhdpi/ic_launcher.png", ByteArray(0))
        createFile(resources, "drawable/unused.png", ByteArray(0))

        createXml(
            resources,
            "layout/activity_main.xml",
            format,
            xmlElement("RelativeLayout")
                .addNamespace("android", ANDROID_NS)
                .addNamespace("tools", TOOLS_NS)
                .addAttribute("layout_width", ANDROID_NS, value = "match_parent")
                .addAttribute("layout_height", ANDROID_NS, value = "match_parent")
                .addAttribute("paddingTop",
                              ANDROID_NS,
                              "@dimen/activity_vertical_margin",
                              0x7f040001)
                .addAttribute("paddingBottom",
                              ANDROID_NS,
                              "@dimen/activity_vertical_margin",
                              0x7f040001)
                .addAttribute("paddingLeft",
                              ANDROID_NS,
                              "@dimen/activity_horizontal_margin",
                              0x7f040000)
                .addAttribute("paddingRight",
                              ANDROID_NS,
                              "@dimen/activity_horizontal_margin",
                              0x7f040000)
                .addAttribute("context", TOOLS_NS, "com.example.shrinkunittest.app.MainActivity")
                .addChild(
                    xmlElement("TextView")
                        .addAttribute("text", ANDROID_NS, "@string/hello_world", 0x7f050003)
                        .addAttribute("style", value = "@style/MyStyle.Child", refId = 0x7f060002)
                        .addAttribute("layout_width", ANDROID_NS, value = "wrap_content")
                        .addAttribute("layout_height", ANDROID_NS, value = "wrap_content")
                )
                .buildNode()
        )

        createXml(
            resources,
            "menu/main.xml",
            format,
            xmlElement("menu")
                .addNamespace("android", ANDROID_NS)
                .addNamespace("tools", TOOLS_NS)
                .addAttribute("context",
                              TOOLS_NS,
                              value = "com.example.shrinkunittest.app.MainActivity")
                .addChild(
                    xmlElement("item")
                        .addAttribute("id", ANDROID_NS, "@+id/action_settings", 0x7f080000)
                        .addAttribute("title", ANDROID_NS, "@string/action_settings", 0x7f050000)
                        .addAttribute("orderInCategory", ANDROID_NS, value = "100")
                        .addAttribute("showAsAction", ANDROID_NS, value = "never")
                )
                .buildNode()
        )

        createXml(
            resources,
            "menu/menu2.xml",
            format,
            xmlElement("menu")
                .addNamespace("android", ANDROID_NS)
                .addNamespace("tools", TOOLS_NS)
                .addAttribute("context",
                              TOOLS_NS,
                              value = "com.example.shrinkunittest.app.MainActivity")
                .addChild(
                    xmlElement("item")
                        .addAttribute("id", ANDROID_NS, "@+id/action_settings2", 0x7f080001)
                        .addAttribute("title", ANDROID_NS, "@string/action_settings2", 0x7f050004)
                        .addAttribute("orderInCategory", ANDROID_NS, value = "100")
                        .addAttribute("showAsAction", ANDROID_NS, value = "never")
                )
                .buildNode()
        )

        createFile(
            resources,
            "values/values.xml",
            """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>

                    <attr name="myAttr1" format="integer" />
                    <attr name="myAttr2" format="boolean" />

                    <dimen name="activity_horizontal_margin">16dp</dimen>
                    <dimen name="activity_vertical_margin">16dp</dimen>

                    <string name="action_settings">Settings</string>
                    <string name="action_settings2">Settings2</string>
                    <string name="alias"> @string/app_name </string>
                    <string name="app_name">ShrinkUnitTest</string>
                    <string name="hello_world">Hello world!</string>

                    <style name="AppTheme" parent="android:Theme.Holo"></style>

                    <style name="MyStyle">
                        <item name="myAttr1">50</item>
                    </style>

                    <style name="MyStyle.Child">
                        <item name="myAttr2">true</item>
                    </style>

                </resources>
            """.trimIndent()
        )
        createFile(
            resources,
            "raw/android_wear_micro_apk.apk",
            "<binary data>"
        )

        createXml(
            resources,
            "xml/android_wear_micro_apk.xml",
            format,
            xmlElement("wearableApp")
                .addAttribute("package", value = "com.example.shrinkunittest.app")
                .addChild(
                    xmlElement("versionCode")
                        .setText("1")
                )
                .addChild(
                    xmlElement("versionName")
                        .setText("1.0' platformBuildVersionName='5.0-1521886")
                )
                .addChild(
                    xmlElement("rawPathResId")
                        .setText("android_wear_micro_apk")
                )
                .buildNode()
        )
        if (addKeepXml) {
            createFile(
                resources,
                "raw/keep.xml",
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <resources xmlns:tools="http://schemas.android.com/tools"
                        tools:keep="@drawable/avd_heart_fill_1"     tools:discard="@menu/main" />
                """.trimIndent()
            )
        }
        // RAW content for HTML/web
        createFile(
            resources,
            "raw/index1.html",
            """
                <!DOCTYPE html>
                <html>
                <!--
                 Blah blah
                -->
                <head>
                  <meta charset="utf-8">
                  <link href="http://fonts.googleapis.com/css?family=Alegreya:400italic,900italic|Alegreya+Sans:300" rel="stylesheet">
                  <link href="http://yui.yahooapis.com/2.8.0r4/build/reset/reset-min.css" rel="stylesheet">
                  <link href="static/landing.css" rel="stylesheet">
                  <script src="http://ajax.googleapis.com/ajax/libs/jquery/2.0.3/jquery.min.js"></script>
                  <script src="static/modernizr.custom.14469.js"></script>
                  <meta name="viewport" content="width=690">
                  <style type="text/css">
                html, body {
                  margin: 0;
                  height: 100%;
                  background-image: url(file:///android_res/raw/my_used_raw_drawable);
                }
                </style></head>
                <body>

                <div id="container">

                  <div id="logo"></div>

                  <div id="text">
                    <p>
                      More ignored text here
                    </p>
                  </div>

                  <a id="playlink" href="file/foo.png">&nbsp;</a>
                </div>
                <script>

                if (Modernizr.cssanimations &&
                    Modernizr.svg &&
                    Modernizr.csstransforms3d &&
                    Modernizr.csstransitions) {

                  // progressive enhancement
                  ${'$'}('#device-screen').css('display', 'block');
                  ${'$'}('#device-frame').css('background-image', 'url( 'drawable-mdpi/tilted.png')' );
                  ${'$'}('#opentarget').css('visibility', 'visible');
                  ${'$'}('body').addClass('withvignette');
                </script>

                </body>
                </html>
            """.trimIndent()
        )
        createFile(
            resources,
            "raw/styles2.css",
            """
                /**
                 * Copyright 2014 Google Inc.
                 */

                html, body {
                  margin: 0;
                  height: 100%;
                  -webkit-font-smoothing: antialiased;
                }
                #logo {
                  position: absolute;
                  left: 0;
                  top: 60px;
                  width: 250px;
                  height: 102px;
                  background-image: url(img2.png);
                  background-repeat: no-repeat;
                  background-size: contain;
                  opacity: 0.7;
                  z-index: 100;
                }
                device-frame {
                  position: absolute;
                  right: -70px;
                  top: 0;
                  width: 420px;
                  height: 500px;
                  background-image: url(tilted_fallback.jpg);
                  background-size: cover;
                  -webkit-user-select: none;
                  -moz-user-select: none;
                }
            """.trimIndent()
        )
        createFile(
            resources,
            "raw/my_js.js",
            """
                function ${'$'}(id) {
                  return document.getElementById(id);
                }

                /* Ignored block comment: "ignore me" */
                function show(id) {
                  ${'$'}(id).style.display = "block";
                }

                function hide(id) {
                  ${'$'}(id).style.display = "none";
                }
                // Line comment
                function onStatusBoxFocus(elt) {
                  elt.value = '';
                  elt.style.color = "#000";
                  show('status_submit');
                }
            """.trimIndent()
        )

        // Inlined resources
        val drawable =
            xmlElement("vector")
                .addAttribute("width", ANDROID_NS, value = "56dp")
                .addAttribute("height", ANDROID_NS, value = "56dp")
                .addAttribute("viewportWidth", ANDROID_NS, value = "56")
                .addAttribute("viewportHeight", ANDROID_NS, value = "56")
        val animation =
            xmlElement("objectAnimator")
                .addAttribute("propertyName", ANDROID_NS, value = "pathData")
                .addAttribute("interpolator",
                              ANDROID_NS,
                              value = "@android:interpolator/fast_out_slow_in")

        when (format) {
            ResourcesVariant.RAW -> {
                createXml(
                    resources,
                    "drawable/avd_heart_fill.xml",
                    format,
                    xmlElement("animated-vector")
                        .addNamespace("android", ANDROID_NS)
                        .addNamespace("aapt", AAPT_NS)
                        .addChild(
                            xmlElement("attr", AAPT_NS)
                                .addAttribute("name", value = "android:drawable")
                                .addChild(drawable)
                        )
                        .addChild(
                            xmlElement("target")
                                .addAttribute("name", value = "clip")
                                .addChild(
                                    xmlElement("attr", AAPT_NS)
                                        .addAttribute("name", value = "android:animation")
                                        .addChild(animation)
                                )
                        )
                        .buildNode()
                )
            }

            ResourcesVariant.PROTO_COMPILED -> {
                createXml(
                    resources,
                    "drawable/avd_heart_fill.xml",
                    format,
                    xmlElement("animated-vector")
                        .addNamespace("android", ANDROID_NS)
                        .addAttribute("drawable",
                                      ANDROID_NS,
                                      "@drawable/avd_heart_fill_1.xml",
                                      0x7f020003)
                        .addChild(
                            xmlElement("target")
                                .addAttribute("animation",
                                              ANDROID_NS,
                                              "@drawable/avd_heart_fill_2.xml",
                                              0x7f020004)
                        )
                        .buildNode()
                )
                createXml(
                    resources,
                    "drawable/avd_heart_fill_1.xml",
                    format,
                    drawable.buildNode()
                )
                createXml(
                    resources,
                    "drawable/avd_heart_fill_2.xml",
                    format,
                    animation.buildNode()
                )
            }
        }

        return resources
    }

    private fun createMergedManifest(dir: File): File {
        return createXml(
            dir,
            "app/build/manifests/release/AndroidManifest.xml",
            ResourcesVariant.PROTO_COMPILED,
            xmlElement("manifest")
                .addNamespace("android", ANDROID_NS)
                .addAttribute("versionCode", ANDROID_NS, "1")
                .addAttribute("versionName", ANDROID_NS, "1.0")
                .addAttribute("package", value = "com.example.shrinkunittest.app")
                .addChild(
                    xmlElement("uses-sdk")
                        .addAttribute("minSdkVersion", ANDROID_NS, "19")
                        .addAttribute("targetSdkVersion", ANDROID_NS, "20")
                )
                .addChild(
                    xmlElement("application")
                        .addAttribute("allowBackup", ANDROID_NS, "true")
                        .addAttribute("icon", ANDROID_NS, "@drawable/ic_launcher", 0x7f020000)
                        .addAttribute("label", ANDROID_NS, "@string/app_name", 0x7f050002)
                        .addChild(
                            xmlElement("activity")
                                .addAttribute("label", ANDROID_NS, "@string/app_name", 0x7f050002)
                                .addAttribute("name",
                                              ANDROID_NS,
                                              "com.example.shrinkunittest.app.MainActivity")
                                .addChild(
                                    xmlElement("intent-filter")
                                        .addChild(
                                            xmlElement("action")
                                                .addAttribute("name",
                                                              ANDROID_NS,
                                                              "android.intent.action.MAIN")
                                        )
                                        .addChild(
                                            xmlElement("category")
                                                .addAttribute("name",
                                                              ANDROID_NS,
                                                              "android.intent.category.LAUNCHER")
                                        )
                                )
                        )
                        .addChild(
                            xmlElement("meta-data")
                                .addAttribute("name",
                                              ANDROID_NS,
                                              "com.google.android.wearable.beta.app")
                                .addAttribute("resource",
                                              ANDROID_NS,
                                              "@xml/android_wear_micro_apk",
                                              0x7f0a0000)
                        )
                )
                .buildNode()
        )
    }

    private fun createR8Dex(dir: File): File {
        /*
         Dex file contain the activity below, it has been produced with R8 with minSdkVersion 25.

         package com.example.shrinkunittest.app;
         import android.app.Activity;
         import android.os.Bundle;
         import android.view.Menu;
         import android.view.MenuItem;

         public class MainActivity extends Activity {
           public MainActivity() {
           }
           protected void onCreate(Bundle var1) {
             super.onCreate(var1);
             this.setContentView(2130903040);
           }
           public boolean onCreateOptionsMenu(Menu var1) {
             this.getMenuInflater().inflate(2131165184, var1);
             return true;
           }
           public boolean onOptionsItemSelected(MenuItem var1) {
             int var2 = var1.getItemId();
             return var2 == 2131230720 ? true : super.onOptionsItemSelected(var1);
           }
         }
        */
        val dexContent = Resources.toByteArray(
            Resources.getResource("resourceShrinker/classes.dex")
        )
        return createFile(
            dir, "app/build/intermediates/transforms/r8/debug/0/classes.dex", dexContent
        )
    }

    private fun createMappingFile(dir: File): File {
        return createFile(
            dir,
            "app/build/proguard/release/mapping.txt",
            """
                com.example.shrinkunittest.app.MainActivity -> com.example.shrinkunittest.app.MainActivity:
                    void onCreate(android.os.Bundle) -> onCreate
                    boolean onCreateOptionsMenu(android.view.Menu) -> onCreateOptionsMenu
                    boolean onOptionsItemSelected(android.view.MenuItem) -> onOptionsItemSelected
                com.foo.bar.R${'$'}layout -> com.foo.bar.t:
                    int checkable_option_view_layout -> a
                    int error_layout -> b
                    int glyph_button_icon_only -> c
                    int glyph_button_icon_with_text_below -> d
                    int glyph_button_icon_with_text_right -> e
                    int structure_status_view -> f
                android.support.annotation.FloatRange -> android.support.annotation.FloatRange:
                    double from() -> from
                    double to() -> to
                    boolean fromInclusive() -> fromInclusive
                    boolean toInclusive() -> toInclusive
            """.trimIndent()
        )
    }

    private fun createProguardedDex(dir: File): File {
        val dexContent = Resources.toByteArray(
            Resources.getResource("resourceShrinker/proguarded.dex")
        )
        return createFile(
            dir, "app/build/intermediates/transforms/r8/debug/0/classes.dex", dexContent
        )
    }

    private fun createUnproguardedDex(dir: File): File {
        val dexContent = Resources.toByteArray(
            Resources.getResource("resourceShrinker/notshrinked.dex")
        )
        return createFile(
            dir, "app/build/intermediates/transforms/r8/debug/0/classes.dex", dexContent
        )
    }
    /* ktlint-enable */

    private fun dumpZipContents(zipFile: File): String {
        return ZipFile(zipFile).use { zip ->
            zip.entries().asSequence()
                .map { it.name }
                .sorted()
                .joinToString(separator = "\n")
        }
    }

    private fun zipResourcesTo(
        resourcesDir: File,
        inlinedResources: List<String>,
        resourcesArsc: File,
        outZip: File
    ): File {
        val root = resourcesDir.toPath()
        val paths = Files.walk(root).filter { it != root }.toList().sorted()

        val inlinedNotFound = inlinedResources.toMutableSet()
        FileOutputStream(outZip).use { fos ->
            ZipOutputStream(fos).use { zos ->
                for (path in paths) {
                    var relative = "res/" +
                                   root.relativize(path).toString().replace(File.separatorChar, '/')
                    val isValuesFile = relative == "res/values/values.xml"
                    if (isValuesFile) {
                        // We explicitly add a resources.pb file
                        continue
                    }
                    if (Files.isDirectory(path)) {
                        relative += '/'
                    }
                    zos.putNextEntry(ZipEntry(relative))
                    if (!Files.isDirectory(path) && !isValuesFile) {
                        val bytes = Files.readAllBytes(path)
                        zos.write(bytes)
                    }
                    zos.closeEntry()
                    inlinedNotFound -= relative
                }
                inlinedNotFound.forEach {
                    zos.putNextEntry(ZipEntry(it))
                    zos.write(ByteArray(0))
                    zos.closeEntry()
                }
                zos.putNextEntry(ZipEntry("resources.pb"))
                zos.write(Files.readAllBytes(resourcesArsc.toPath()))
                zos.closeEntry()
            }
        }
        return outZip
    }

    private fun getZipContents(zipFile: File, name: String): ByteArray? {
        return ZipFile(zipFile).use { zip ->
            zip.getEntry(name)?.let {
                ByteStreams.toByteArray(zip.getInputStream(it))
            }
        }
    }

    private fun createXml(dir: File, relative: String, format: ResourcesVariant, xml: XmlNode) =
        when (format) {
            ResourcesVariant.RAW -> createFile(dir, relative, xml.toXmlString())
            ResourcesVariant.PROTO_COMPILED -> createFile(dir, relative, xml.toByteArray())
        }

    private fun createFile(dir: File, relative: String, contents: ByteArray): File {
        val file = createFile(dir, relative)
        Files.write(file.toPath(), contents)
        return file
    }

    private fun createFile(dir: File, relative: String, contents: String): File {
        return createFile(dir, relative, contents.toByteArray(StandardCharsets.UTF_8))
    }

    private fun createFile(dir: File, relative: String): File {
        val file = File(dir, relative.replace('/', File.separatorChar))
        file.parentFile.mkdirs()
        return file
    }

    private fun checkState(analyzer: ResourceShrinkerImpl) {
        val resources = analyzer.model.resourceStore.resources
            .sortedWith(compareBy({ it.type }, { it.name }))

        var prev: ResourceUsageModel.Resource? = null
        for (resource in resources) {
            assertTrue(
                "$resource and $prev",
                prev == null || resource.type != prev.type || resource.name != prev.name
            )
            prev = resource
        }
    }
}

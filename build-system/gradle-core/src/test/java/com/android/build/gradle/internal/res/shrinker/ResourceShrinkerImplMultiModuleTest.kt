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

import com.android.aapt.Resources
import com.android.build.gradle.internal.res.shrinker.gatherer.ProtoResourceTableGatherer
import com.android.build.gradle.internal.res.shrinker.graph.ProtoResourcesGraphBuilder
import com.android.build.gradle.internal.res.shrinker.usages.DexUsageRecorder
import com.android.build.gradle.internal.res.shrinker.usages.ProtoAndroidManifestUsageRecorder
import com.android.build.gradle.internal.res.shrinker.usages.ToolsAttributeUsageRecorder
import com.android.build.gradle.internal.res.shrinker.util.addAttribute
import com.android.build.gradle.internal.res.shrinker.util.addChild
import com.android.build.gradle.internal.res.shrinker.util.addNamespace
import com.android.build.gradle.internal.res.shrinker.util.addType
import com.android.build.gradle.internal.res.shrinker.util.attrEntry
import com.android.build.gradle.internal.res.shrinker.util.buildNode
import com.android.build.gradle.internal.res.shrinker.util.buildResourceTable
import com.android.build.gradle.internal.res.shrinker.util.externalFile
import com.android.build.gradle.internal.res.shrinker.util.idEntry
import com.android.build.gradle.internal.res.shrinker.util.setText
import com.android.build.gradle.internal.res.shrinker.util.stringEntry
import com.android.build.gradle.internal.res.shrinker.util.styleEntry
import com.android.build.gradle.internal.res.shrinker.util.xmlElement
import com.android.build.gradle.internal.res.shrinker.util.xmlFile
import com.android.utils.FileUtils
import com.android.utils.FileUtils.createFile
import com.android.utils.FileUtils.writeToFile
import com.google.common.io.Files as CommonIoFiles
import com.google.common.io.Resources as CommonIoResources
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.streams.toList
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path

class ResourceShrinkerImplMultiModuleTest{
    companion object {
        private val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private val TOOLS_NS = "http://schemas.android.com/tools"
    }

    @get:Rule
    var temporaryFolder = TemporaryFolder()

    @Test
    fun `shrink resources in a basic multi-modules project`() {
        // Basic project with two modules: base and feature where all resources are referenced by
        // values and type+names inside code, android manifests, resource tables and resources.
        // Feature module does not contain any dex files.
        val app = createBasicMultiModuleApp()

        val shrinker = ResourceShrinkerImpl(
          resourcesGatherers = listOf(
              ProtoResourceTableGatherer(app.baseResourceTable),
              ProtoResourceTableGatherer(app.featureResourceTable)
          ),
          obfuscationMappingsRecorder = null,
          usageRecorders = listOf(
              ProtoAndroidManifestUsageRecorder(app.baseManifest),
              ProtoAndroidManifestUsageRecorder(app.featureManifest),
              DexUsageRecorder(app.baseDex),
              ToolsAttributeUsageRecorder(app.featureResources.resolve("raw"))
          ),
          graphBuilders = listOf(
              ProtoResourcesGraphBuilder(app.baseResources, app.baseResourceTable),
              ProtoResourcesGraphBuilder(app.featureResources, app.featureResourceTable)
          ),
          debugReporter = NoDebugReporter,
          supportMultipackages = true,
          usePreciseShrinking = false
        )

        shrinker.analyze()

        assertThat(shrinker.model.resourceStore.dumpResourceModel().trim())
            .isEqualTo(
                """
                @example.app.feature:attr/myAttr1 : reachable=false
                @example.app.feature:attr/myAttr2 : reachable=false
                @example.app:drawable/avd_heart_fill : reachable=false
                @example.app:drawable/feature_img : reachable=true
                @example.app:drawable/ic_launcher : reachable=true
                @example.app:drawable/unused : reachable=false
                @example.app:id/action_settings : reachable=true
                @example.app:id/action_settings2 : reachable=false
                @example.app.feature:layout/activity_feature : reachable=true
                    @example.app.feature:string/feature_activity
                    @example.app:style/AppTheme
                @example.app:layout/activity_main : reachable=true
                    @example.app:string/activity_main_text
                    @example.app:style/AppTheme
                @example.app:layout/layout_1 : reachable=false
                @example.app.feature:layout/layout_feat : reachable=false
                @example.app:menu/main : reachable=true
                    @example.app:string/action_settings
                @example.app.feature:menu/menu2 : reachable=false
                    @example.app:string/action_settings2
                @example.app:raw/android_wear_micro_apk : reachable=true
                @example.app:raw/base_style : reachable=false
                @example.app.feature:raw/keep : reachable=false
                @example.app.feature:raw/myjs : reachable=false
                @example.app.feature:raw/style : reachable=false
                @example.app.feature:raw/webpage : reachable=false
                @example.app:string/action_settings : reachable=true
                @example.app:string/action_settings2 : reachable=false
                @example.app:string/activity_main_text : reachable=true
                @example.app:string/alias : reachable=false
                    @example.app:string/app_name
                @example.app.feature:string/alias : reachable=true
                    @example.app.feature:string/feature_name
                @example.app:string/app_name : reachable=true
                @example.app.feature:string/feature_activity : reachable=true
                @example.app.feature:string/feature_name : reachable=true
                    @example.app:string/hello_world
                @example.app:string/hello_world : reachable=true
                @example.app:style/AppTheme : reachable=true
                @example.app:style/MyStyle : reachable=true
                @example.app.feature:style/MyStyle_Child : reachable=true
                    @example.app:style/MyStyle
                @example.app:xml/android_wear_micro_apk : reachable=true
                    @example.app:raw/android_wear_micro_apk
            """.trimIndent().removeEmptyLines()
            )

        val shrunkZip = temporaryFolder.newFile()
        shrinker.rewriteResourcesInBundleFormat(
            zipDirectory(app.root, temporaryFolder.newFile()),
            shrunkZip,
            mapOf("base" to "example.app", "feature" to "example.app.feature")
        )

        assertThat(getDummyEntries(shrunkZip)).containsExactly(
            "feature/res/raw/keep.xml",
            "feature/res/menu/menu2.xml",
            "base/res/layout/layout_1.xml",
            "base/res/drawable/unused.png",
            "feature/res/layout/layout_feat.xml"
        )
    }

    @Test
    fun `shrink resources in a project with web content`() {
        // The same as basic project but adds web content to a feature module where resources are
        // referenced by web urls.
        val app = createBasicMultiModuleApp()
        createFeatureWebContent(app.root)

        val shrinker = ResourceShrinkerImpl(
          resourcesGatherers = listOf(
              ProtoResourceTableGatherer(app.baseResourceTable),
              ProtoResourceTableGatherer(app.featureResourceTable)
          ),
          obfuscationMappingsRecorder = null,
          usageRecorders = listOf(
              ProtoAndroidManifestUsageRecorder(app.baseManifest),
              ProtoAndroidManifestUsageRecorder(app.featureManifest),
              DexUsageRecorder(app.baseDex),
              ToolsAttributeUsageRecorder(app.featureResources.resolve("raw"))
          ),
          graphBuilders = listOf(
              ProtoResourcesGraphBuilder(app.baseResources, app.baseResourceTable),
              ProtoResourcesGraphBuilder(app.featureResources, app.featureResourceTable)
          ),
          debugReporter = NoDebugReporter,
          supportMultipackages = true,
          usePreciseShrinking = false
        )

        shrinker.analyze()

        assertThat(shrinker.model.resourceStore.dumpResourceModel().trim())
            .isEqualTo(
                """
                @example.app.feature:attr/myAttr1 : reachable=false
                @example.app.feature:attr/myAttr2 : reachable=false
                @example.app:drawable/avd_heart_fill : reachable=false
                @example.app:drawable/feature_img : reachable=true
                @example.app:drawable/ic_launcher : reachable=true
                @example.app:drawable/unused : reachable=false
                @example.app:id/action_settings : reachable=true
                @example.app:id/action_settings2 : reachable=false
                @example.app.feature:layout/activity_feature : reachable=true
                    @example.app.feature:string/feature_activity
                    @example.app:style/AppTheme
                @example.app:layout/activity_main : reachable=true
                    @example.app:string/activity_main_text
                    @example.app:style/AppTheme
                @example.app:layout/layout_1 : reachable=false
                @example.app.feature:layout/layout_feat : reachable=false
                @example.app:menu/main : reachable=true
                    @example.app:string/action_settings
                @example.app.feature:menu/menu2 : reachable=false
                    @example.app:string/action_settings2
                @example.app:raw/android_wear_micro_apk : reachable=true
                @example.app:raw/base_style : reachable=true
                @example.app.feature:raw/keep : reachable=false
                @example.app.feature:raw/myjs : reachable=true
                @example.app.feature:raw/style : reachable=false
                @example.app.feature:raw/webpage : reachable=true
                    @example.app.feature:raw/myjs
                    @example.app:raw/base_style
                @example.app:string/action_settings : reachable=true
                @example.app:string/action_settings2 : reachable=false
                @example.app:string/activity_main_text : reachable=true
                @example.app:string/alias : reachable=false
                    @example.app:string/app_name
                @example.app.feature:string/alias : reachable=true
                    @example.app.feature:string/feature_name
                @example.app:string/app_name : reachable=true
                @example.app.feature:string/feature_activity : reachable=true
                @example.app.feature:string/feature_name : reachable=true
                    @example.app:string/hello_world
                @example.app:string/hello_world : reachable=true
                @example.app:style/AppTheme : reachable=true
                @example.app:style/MyStyle : reachable=true
                @example.app.feature:style/MyStyle_Child : reachable=true
                    @example.app:style/MyStyle
                @example.app:xml/android_wear_micro_apk : reachable=true
                    @example.app:raw/android_wear_micro_apk
            """.trimIndent().removeEmptyLines()
            )

        val shrunkZip = temporaryFolder.newFile()
        shrinker.rewriteResourcesInBundleFormat(
            zipDirectory(app.root, temporaryFolder.newFile()),
            shrunkZip,
            mapOf("base" to "example.app", "feature" to "example.app.feature")
        )

        assertThat(getDummyEntries(shrunkZip)).containsExactly(
            "feature/res/raw/keep.xml",
            "feature/res/menu/menu2.xml",
            "base/res/layout/layout_1.xml",
            "base/res/drawable/unused.png",
            "feature/res/layout/layout_feat.xml",
            "feature/res/raw/style.css"
        )
    }

    @Test
    fun `shrink resources in a project with keep discard rules`() {
        // The same as basic projects but adds raw/keep.xml file to a feature module where
        // enumerated all resources that should be explicitly kept and discard via  'tools:keep'
        // and 'tools:discard' attributes.
        val app = createBasicMultiModuleApp()
        createKeepDiscardFile(app.root)

        val shrinker = ResourceShrinkerImpl(
          resourcesGatherers = listOf(
              ProtoResourceTableGatherer(app.baseResourceTable),
              ProtoResourceTableGatherer(app.featureResourceTable)
          ),
          obfuscationMappingsRecorder = null,
          usageRecorders = listOf(
              ProtoAndroidManifestUsageRecorder(app.baseManifest),
              ProtoAndroidManifestUsageRecorder(app.featureManifest),
              DexUsageRecorder(app.baseDex),
              ToolsAttributeUsageRecorder(app.featureResources.resolve("raw"))
          ),
          graphBuilders = listOf(
              ProtoResourcesGraphBuilder(app.baseResources, app.baseResourceTable),
              ProtoResourcesGraphBuilder(app.featureResources, app.featureResourceTable)
          ),
          debugReporter = NoDebugReporter,
          supportMultipackages = true,
          usePreciseShrinking = false
        )

        shrinker.analyze()

        assertThat(shrinker.model.resourceStore.dumpResourceModel().trim())
            .isEqualTo(
                """
                @example.app.feature:attr/myAttr1 : reachable=true
                @example.app.feature:attr/myAttr2 : reachable=true
                @example.app:drawable/avd_heart_fill : reachable=false
                @example.app:drawable/feature_img : reachable=true
                @example.app:drawable/ic_launcher : reachable=true
                @example.app:drawable/unused : reachable=true
                @example.app:id/action_settings : reachable=true
                @example.app:id/action_settings2 : reachable=false
                @example.app.feature:layout/activity_feature : reachable=true
                    @example.app.feature:string/feature_activity
                    @example.app:style/AppTheme
                @example.app:layout/activity_main : reachable=true
                    @example.app:string/activity_main_text
                    @example.app:style/AppTheme
                @example.app:layout/layout_1 : reachable=false
                @example.app.feature:layout/layout_feat : reachable=false
                @example.app:menu/main : reachable=false
                    @example.app:string/action_settings
                @example.app.feature:menu/menu2 : reachable=false
                    @example.app:string/action_settings2
                @example.app:raw/android_wear_micro_apk : reachable=true
                @example.app:raw/base_style : reachable=false
                @example.app.feature:raw/keep : reachable=false
                @example.app.feature:raw/myjs : reachable=false
                @example.app.feature:raw/style : reachable=false
                @example.app.feature:raw/webpage : reachable=false
                @example.app:string/action_settings : reachable=false
                @example.app:string/action_settings2 : reachable=false
                @example.app:string/activity_main_text : reachable=true
                @example.app:string/alias : reachable=false
                    @example.app:string/app_name
                @example.app.feature:string/alias : reachable=true
                    @example.app.feature:string/feature_name
                @example.app:string/app_name : reachable=true
                @example.app.feature:string/feature_activity : reachable=true
                @example.app.feature:string/feature_name : reachable=true
                    @example.app:string/hello_world
                @example.app:string/hello_world : reachable=true
                @example.app:style/AppTheme : reachable=true
                @example.app:style/MyStyle : reachable=true
                @example.app.feature:style/MyStyle_Child : reachable=true
                    @example.app:style/MyStyle
                @example.app:xml/android_wear_micro_apk : reachable=true
                    @example.app:raw/android_wear_micro_apk
            """.trimIndent().removeEmptyLines()
            )

        val shrunkZip = temporaryFolder.newFile()
        shrinker.rewriteResourcesInBundleFormat(
            zipDirectory(app.root, temporaryFolder.newFile()),
            shrunkZip,
            mapOf("base" to "example.app", "feature" to "example.app.feature")
        )

        assertThat(getDummyEntries(shrunkZip)).containsExactly(
            "feature/res/raw/keep.xml",
            "feature/res/menu/menu2.xml",
            "base/res/layout/layout_1.xml",
            "feature/res/layout/layout_feat.xml",
            "base/res/menu/main.xml"
        )
    }

    @Test
    fun `shrink resources in a project with accessing resources by name`() {
        // The same as basic but adds dex file to a feature modules which invokes
        // Resources.getIdentifier to take resource by name. String constants that are provided:
        // 'layout', 'activity_feature_java', 'true', 'debug'.
        val app = createBasicMultiModuleApp()
        val featureDex = createDexWithGetIdentifier(app.root).toPath()

        val shrinker = ResourceShrinkerImpl(
          resourcesGatherers = listOf(
              ProtoResourceTableGatherer(app.baseResourceTable),
              ProtoResourceTableGatherer(app.featureResourceTable)
          ),
          obfuscationMappingsRecorder = null,
          usageRecorders = listOf(
              ProtoAndroidManifestUsageRecorder(app.baseManifest),
              ProtoAndroidManifestUsageRecorder(app.featureManifest),
              DexUsageRecorder(app.baseDex),
              ToolsAttributeUsageRecorder(app.featureResources.resolve("raw")),
              DexUsageRecorder(featureDex)
          ),
          graphBuilders = listOf(
              ProtoResourcesGraphBuilder(app.baseResources, app.baseResourceTable),
              ProtoResourcesGraphBuilder(app.featureResources, app.featureResourceTable)
          ),
          debugReporter = NoDebugReporter,
          supportMultipackages = true,
          usePreciseShrinking = false
        )

        shrinker.analyze()

        assertThat(shrinker.model.resourceStore.dumpResourceModel().trim())
            .isEqualTo(
                """
                @example.app.feature:attr/myAttr1 : reachable=false
                @example.app.feature:attr/myAttr2 : reachable=false
                @example.app:drawable/avd_heart_fill : reachable=false
                @example.app:drawable/feature_img : reachable=true
                @example.app:drawable/ic_launcher : reachable=true
                @example.app:drawable/unused : reachable=false
                @example.app:id/action_settings : reachable=true
                @example.app:id/action_settings2 : reachable=false
                @example.app.feature:layout/activity_feature : reachable=true
                    @example.app.feature:string/feature_activity
                    @example.app:style/AppTheme
                @example.app:layout/activity_main : reachable=true
                    @example.app:string/activity_main_text
                    @example.app:style/AppTheme
                @example.app:layout/layout_1 : reachable=true
                @example.app.feature:layout/layout_feat : reachable=true
                @example.app:menu/main : reachable=true
                    @example.app:string/action_settings
                @example.app.feature:menu/menu2 : reachable=false
                    @example.app:string/action_settings2
                @example.app:raw/android_wear_micro_apk : reachable=true
                @example.app:raw/base_style : reachable=false
                @example.app.feature:raw/keep : reachable=false
                @example.app.feature:raw/myjs : reachable=false
                @example.app.feature:raw/style : reachable=false
                @example.app.feature:raw/webpage : reachable=false
                @example.app:string/action_settings : reachable=true
                @example.app:string/action_settings2 : reachable=false
                @example.app:string/activity_main_text : reachable=true
                @example.app:string/alias : reachable=false
                    @example.app:string/app_name
                @example.app.feature:string/alias : reachable=true
                    @example.app.feature:string/feature_name
                @example.app:string/app_name : reachable=true
                @example.app.feature:string/feature_activity : reachable=true
                @example.app.feature:string/feature_name : reachable=true
                    @example.app:string/hello_world
                @example.app:string/hello_world : reachable=true
                @example.app:style/AppTheme : reachable=true
                @example.app:style/MyStyle : reachable=true
                @example.app.feature:style/MyStyle_Child : reachable=true
                    @example.app:style/MyStyle
                @example.app:xml/android_wear_micro_apk : reachable=true
                    @example.app:raw/android_wear_micro_apk
            """.trimIndent().removeEmptyLines()
            )

        val shrunkZip = temporaryFolder.newFile()
        shrinker.rewriteResourcesInBundleFormat(
            zipDirectory(app.root, temporaryFolder.newFile()),
            shrunkZip,
            mapOf("base" to "example.app", "feature" to "example.app.feature")
        )

        assertThat(getDummyEntries(shrunkZip)).containsExactly(
            "feature/res/raw/keep.xml",
            "feature/res/menu/menu2.xml",
            "base/res/drawable/unused.png"
        )
    }

    private fun createBasicMultiModuleApp(): MultiModuleApp {
        val root = temporaryFolder.newFolder()
        return MultiModuleApp(
            root = root,
            baseResourceTable = createBaseModuleResourceTable(root).toPath(),
            baseManifest = createBaseManifest(root).toPath(),
            baseResources = createBaseResources(root).toPath(),
            baseDex = createR8Dex(root).toPath(),
            featureResourceTable = createFeatureModuleResourceTable(root).toPath(),
            featureManifest = createFeatureManifest(root).toPath(),
            featureResources = createFeatureResources(root).toPath()
        )
    }


    private fun createBaseModuleResourceTable(root: File): File {
        val basePackage = Resources.Package.newBuilder()
            .setPackageName("example.app")
            .setPackageId(Resources.PackageId.newBuilder().setId(0x7f))
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
                externalFile(5, "feature_img", "res/drawable/feature_img.9.png")
            )
            .addType(
                3,
                "layout",
                xmlFile(0, "activity_main", "res/layout/activity_main.xml"),
                xmlFile(1, "layout_1", "res/layout/layout_1.xml")
            )
            .addType(
                5,
                "string",
                stringEntry(0, "action_settings", "Settings"),
                stringEntry(1, "alias", refId = 0x7f050002, refName = "string/app_name"),
                stringEntry(2, "app_name", "ShrinkUnitTest"),
                stringEntry(3, "hello_world", "Hello world!"),
                stringEntry(4, "action_settings2", "Settings2"),
                stringEntry(5, "activity_main_text", "Main activity")
            )
            .addType(
                6,
                "style",
                styleEntry(0, "AppTheme"),
                styleEntry(1, "MyStyle")
            )
            .addType(
                7,
                "menu",
                xmlFile(0, "main", "res/menu/main.xml")
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
                externalFile(1, "base-style", "res/raw/base_style.css")
            )
            .addType(
                10,
                "xml",
                xmlFile(0, "android_wear_micro_apk", "res/xml/android_wear_micro_apk.xml")
            )

        return writeBinaryFile(
            File(root, "base/resources.pb"),
            basePackage.buildResourceTable().toByteArray()
        )
    }

    private fun createFeatureModuleResourceTable(root: File): File {
        val featurePackage = Resources.Package.newBuilder()
            .setPackageName("example.app.feature")
            .setPackageId(Resources.PackageId.newBuilder().setId(0x80))
            .addType(
                1,
                "attr",
                attrEntry(0, "myAttr1", Resources.Attribute.FormatFlags.INTEGER),
                attrEntry(1, "myAttr2", Resources.Attribute.FormatFlags.BOOLEAN)
            )
            .addType(
                3,
                "layout",
                xmlFile(0, "activity_feature", "res/layout/activity_feature.xml"),
                xmlFile(1, "layout_feat", "res/layout/layout_feat.xml")
            )
            .addType(
                5,
                "string",
                stringEntry(0, "feature_name", refId = 0x7f050003, refName = "string/hello_world"),
                stringEntry(1, "alias", refId = 0x80050000, refName = "string/feature_name"),
                stringEntry(2, "feature_activity", value = "Feature Activity")

            )
            .addType(
                6,
                "style",
                styleEntry(0, "MyStyle.Child", 0x7f060001)
            )
            .addType(
                7,
                "menu",
                xmlFile(0, "menu2", "res/menu/menu2.xml")
            )
            .addType(
                8,
                "raw",
                externalFile(0, "keep", "res/raw/keep.xml"),
                externalFile(1, "myjs", "res/raw/myjs.js"),
                externalFile(2, "webpage", "res/raw/webpage.html"),
                externalFile(3, "style", "res/raw/style.css")
            )

        return writeBinaryFile(
            File(root, "feature/resources.pb"),
            featurePackage.buildResourceTable().toByteArray()
        )
    }

    private fun createFeatureManifest(dir: File): File {
        val manifest =
            xmlElement("manifest")
                .addNamespace("android", ANDROID_NS)
                .addAttribute("versionCode", ANDROID_NS, "1")
                .addAttribute("versionName", ANDROID_NS, "1.0")
                .addAttribute("package", value = "example.app.feature")
                .addChild(
                    xmlElement("uses-sdk")
                        .addAttribute("minSdkVersion", ANDROID_NS, "19")
                        .addAttribute("targetSdkVersion", ANDROID_NS, "20")
                )
                .addChild(
                    xmlElement("application")
                        .addAttribute("allowBackup", ANDROID_NS, "true")
                        .addAttribute("icon", ANDROID_NS, "@drawable/feature_img", 0x7f020005)
                        .addAttribute("label", ANDROID_NS, "@string/feature_name", 0x80050000)
                        .addAttribute("theme", ANDROID_NS, "@style/MyStyle.Child", 0x80060000)
                        .addChild(
                            xmlElement("activity")
                                .addAttribute("label", ANDROID_NS, "@string/app_name", 0x80050001)
                                .addAttribute(
                                    "name",
                                    ANDROID_NS,
                                    "example.app.feature.AnotherActivity"
                                )
                                .addAttribute(
                                    "layout",
                                    ANDROID_NS,
                                    "@layout/activity_feature",
                                    0x80030000
                                )
                        )
                )
                .buildNode()

        return writeBinaryFile(
            File(dir, "feature/manifest/AndroidManifest.xml"),
            manifest.toByteArray()
        )
    }

    private fun createBaseManifest(dir: File): File {
        val manifest =
            xmlElement("manifest")
                .addNamespace("android", ANDROID_NS)
                .addAttribute("versionCode", ANDROID_NS, "1")
                .addAttribute("versionName", ANDROID_NS, "1.0")
                .addAttribute("package", value = "example.app")
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
                                .addAttribute("name", ANDROID_NS, "example.app.MainActivity")
                        )
                        .addChild(
                            xmlElement("meta-data")
                                .addAttribute(
                                    "name",
                                    ANDROID_NS,
                                    "com.google.android.wearable.beta.app"
                                )
                                .addAttribute(
                                    "resource",
                                    ANDROID_NS,
                                    "@xml/android_wear_micro_apk",
                                    0x7f0a0000
                                )
                        )
                )
                .buildNode()

        return writeBinaryFile(
            File(dir, "base/manifest/AndroidManifest.xml"),
            manifest.toByteArray()
        )
    }

    private fun createBaseResources(root: File): File {
        writeBinaryFile(
            File(root, "base/res/layout/activity_main.xml"),
            xmlElement("RelativeLayout")
                .addNamespace("android", ANDROID_NS)
                .addNamespace("tools", TOOLS_NS)
                .addAttribute("layout_width", ANDROID_NS, value = "match_parent")
                .addAttribute("layout_height", ANDROID_NS, value = "match_parent")
                .addAttribute("paddingTop", ANDROID_NS, value = "10")
                .addAttribute("paddingBottom", ANDROID_NS, value = "10")
                .addAttribute("paddingLeft", ANDROID_NS, value = "10")
                .addAttribute("paddingRight", ANDROID_NS, value = "10")
                .addAttribute("context", TOOLS_NS, "example.app.MainActivity")
                .addChild(
                    xmlElement("TextView")
                        .addAttribute("text", ANDROID_NS, "@string/activity_main_text", 0x7f050005)
                        .addAttribute("style", value = "@style/AppTheme", refId = 0x7f060000)
                        .addAttribute("layout_width", ANDROID_NS, value = "wrap_content")
                        .addAttribute("layout_height", ANDROID_NS, value = "wrap_content")
                )
                .buildNode()
                .toByteArray()
        )

        writeBinaryFile(
            File(root, "base/res/xml/android_wear_micro_apk.xml"),
            xmlElement("wearableApp")
                .addAttribute("package", value = "example.app")
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
                .toByteArray()
        )

        writeBinaryFile(
            File(root, "base/res/menu/main.xml"),
            xmlElement("menu")
                .addNamespace("android", ANDROID_NS)
                .addNamespace("tools", TOOLS_NS)
                .addAttribute("context", TOOLS_NS, value = "example.app.MainActivity")
                .addChild(
                    xmlElement("item")
                        .addAttribute("id", ANDROID_NS, "@+id/action_settings", 0x7f080000)
                        .addAttribute("title", ANDROID_NS, "@string/action_settings", 0x7f050000)
                        .addAttribute("orderInCategory", ANDROID_NS, value = "100")
                        .addAttribute("showAsAction", ANDROID_NS, value = "never")
                )
                .buildNode()
                .toByteArray()
        )

        writeBinaryFile(
            File(root, "base/res/layout/layout_1.xml"),
            xmlElement("SomeBaseLayout")
                .buildNode()
                .toByteArray()
        )

        writeBinaryFile(
            File(root, "base/res/drawable/unused.png"),
            ByteArray(100)
        )

        return File(root, "base/res")
    }

    private fun createFeatureResources(root: File): File {
        createFile(
            File(root, "feature/res/raw/keep.xml"),
            """
                <?xml version="1.0" encoding="utf-8"?>
                <resources xmlns:tools="http://schemas.android.com/tools" />
            """.trimIndent()
        )

        writeBinaryFile(
            File(root, "feature/res/layout/activity_feature.xml"),
            xmlElement("RelativeLayout")
                .addNamespace("android", ANDROID_NS)
                .addNamespace("tools", TOOLS_NS)
                .addAttribute("context", TOOLS_NS, "example.app.feature.AnotherActivity")
                .addChild(
                    xmlElement("TextView")
                        .addAttribute("text", ANDROID_NS, "@string/feature_activity", 0x80050002)
                        .addAttribute("style", value = "@style/AppTheme", refId = 0x7f060000)
                        .addAttribute("layout_width", ANDROID_NS, value = "wrap_content")
                        .addAttribute("layout_height", ANDROID_NS, value = "wrap_content")
                )
                .buildNode()
                .toByteArray()
        )

        writeBinaryFile(
            File(root, "feature/res/layout/layout_feat.xml"),
            xmlElement("SomeFeatureLayout")
                .buildNode()
                .toByteArray()
        )

        writeBinaryFile(
            File(root, "feature/res/menu/menu2.xml"),
            xmlElement("menu")
                .addNamespace("android", ANDROID_NS)
                .addNamespace("tools", TOOLS_NS)
                .addAttribute("context", TOOLS_NS, value = "example.app.feature.AnotherActivity")
                .addChild(
                    xmlElement("item")
                        .addAttribute("id", ANDROID_NS, "@+id/action_settings2", 0x7f080001)
                        .addAttribute("title", ANDROID_NS, "@string/action_settings2", 0x7f050004)
                        .addAttribute("orderInCategory", ANDROID_NS, value = "100")
                        .addAttribute("showAsAction", ANDROID_NS, value = "never")
                )
                .buildNode()
                .toByteArray()
        )

        return File(root, "feature/res")
    }

    private fun createKeepDiscardFile(root: File) {
        writeToFile(
            File(root, "feature/res/raw/keep.xml"),
            """
                <?xml version="1.0" encoding="utf-8"?>
                <resources xmlns:tools="http://schemas.android.com/tools"
                    tools:keep="@drawable/unused,@attr/myAttr*" tools:discard="@menu/main" />
            """.trimIndent()
        )
    }

    private fun createFeatureWebContent(root: File) {
        createFile(
            File(root, "feature/res/raw/webpage.html"),
            """
                <html>
                <head>
                    <script src="file:///android_res/raw/myjs.js"></script>
                    <link href="file:///android_res/raw/base-style.css"></script>
                </head>
                </html>
            """.trimIndent()
        )

        createFile(
            File(root, "feature/res/raw/style.css"),
            """
                .a {
                  font-size: 14px;
                }
            """.trimIndent()
        )

        createFile(
            File(root, "feature/res/raw/myjs.js"),
            """
                window.alert('webpa');
            """.trimIndent()
        )
    }

    private fun createR8Dex(root: File): File {
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
        val dexContent = CommonIoResources.toByteArray(
            CommonIoResources.getResource("resourceShrinker/classes.dex")
        )
        return writeBinaryFile(
            File(root, "base/dex/classes.dex"),
            dexContent
        )
    }

    private fun createDexWithGetIdentifier(root: File): File {
        val dexContent = CommonIoResources.toByteArray(
            CommonIoResources.getResource("resourceShrinker/getidentifier.dex")
        )
        return writeBinaryFile(
            File(root, "feature/dex/getidentifier.dex"),
            dexContent
        )
    }

    private fun zipDirectory(root: File, out: File): File {
        ZipOutputStream(FileOutputStream(out)).use { output ->
            Files.walk(root.toPath())
                .filter { Files.isRegularFile(it) }
                .map { root.toPath().relativize(it) to Files.readAllBytes(it) }
                .forEach { (relativePath, content) ->
                    val zipPath = relativePath.toString().replace(File.separatorChar, '/')
                    output.putNextEntry(ZipEntry(zipPath))
                    output.write(content)
                    output.closeEntry()
                }
        }
        return out
    }

    private fun getDummyEntries(zip: File): List<String> {
        FileUtils.createZipFilesystem(zip.toPath()).use { fs ->
            return Files.walk(fs.getPath("/"))
                .filter { Files.isRegularFile(it) }
                .filter { path ->
                    val dummy = when (path.last().toString().substringAfter('.')) {
                        "png" -> DummyContent.TINY_PNG
                        "xml" -> DummyContent.TINY_PROTO_XML
                        "9.png" -> DummyContent.TINY_9PNG
                        else -> ByteArray(0)
                    }
                    Files.readAllBytes(path).contentEquals(dummy)
                }
                .map { it.toString().removePrefix("/") }
                .toList()
        }
    }

    private fun writeBinaryFile(file: File, content: ByteArray): File {
        CommonIoFiles.createParentDirs(file)
        CommonIoFiles.write(content, file)
        return file
    }

    private fun String.removeEmptyLines() =
        split('\n').filter { it.isNotBlank() }.joinToString("\n")
}

private data class MultiModuleApp(
    val root: File,

    val baseResourceTable: Path,
    val baseManifest: Path,
    val baseResources: Path,
    val baseDex: Path,

    val featureResourceTable: Path,
    val featureManifest: Path,
    val featureResources: Path
)

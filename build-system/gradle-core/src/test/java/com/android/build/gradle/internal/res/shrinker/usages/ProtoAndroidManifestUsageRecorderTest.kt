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

package com.android.build.gradle.internal.res.shrinker.usages

import com.android.aapt.Resources.XmlNode
import com.android.build.gradle.internal.res.shrinker.NoDebugReporter
import com.android.build.gradle.internal.res.shrinker.ResourceShrinkerModel
import com.android.build.gradle.internal.res.shrinker.util.addAttribute
import com.android.build.gradle.internal.res.shrinker.util.addAttributeWithRefNameOnly
import com.android.build.gradle.internal.res.shrinker.util.addChild
import com.android.build.gradle.internal.res.shrinker.util.addNamespace
import com.android.build.gradle.internal.res.shrinker.util.buildNode
import com.android.build.gradle.internal.res.shrinker.util.xmlElement
import com.android.resources.ResourceType.DRAWABLE
import com.android.resources.ResourceType.STRING
import com.android.resources.ResourceType.STYLE
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProtoAndroidManifestUsageRecorderTest {
    private val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private val packageName = "com.my.package"

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `test recording usages from proto AndroidManifest stored in compiled items`() {
        val model = ResourceShrinkerModel(NoDebugReporter, false)
        model.addResource(DRAWABLE, packageName, "icon", 0x7f010000)
        model.addResource(STRING, packageName, "app_name", 0x7f020000)
        model.addResource(STRING, packageName, "another_name", 0x7f020001)
        model.addResource(STRING, packageName, "main_name", 0x7f020003)
        model.addResource(STYLE, packageName, "CustomTheme_Example", 0x7f080002)

        val manifest =
            xmlElement("manifest")
                .addNamespace("android", ANDROID_NS)
                .addChild(
                    xmlElement("application")
                        .addAttribute("allowBackup", ANDROID_NS, "true")
                        // resource referenced by id
                        .addAttribute("icon", ANDROID_NS, "@drawable/icon", 0x7f010000)
                        // resource referenced by name, without id
                        .addAttributeWithRefNameOnly("label", "string/app_name")
                        .addChild(
                            xmlElement("example")
                                // resources should not be referenced from text
                                .addChild(XmlNode.newBuilder().setText("@string/another_name"))
                        )
                        .addChild(
                            xmlElement("activity")
                                // resourced reference by name with `.` inside ref name
                                .addAttributeWithRefNameOnly("theme", "style/CustomTheme.Example")
                                // resources should not be referenced without compiled item
                                .addAttribute("some", value = "@string/another_name")
                                .addAttribute("label", ANDROID_NS, "@string/main_name", 0x7f020003)
                        )
                )
                .buildNode()

        val manifestPath = temporaryFolder.newFile().toPath()
        Files.write(manifestPath, manifest.toByteArray())

        ProtoAndroidManifestUsageRecorder(manifestPath).recordUsages(model)

        assertTrue(model.resourceStore.getResource(0x7f010000)!!.isReachable)
        assertTrue(model.resourceStore.getResource(0x7f020000)!!.isReachable)
        assertFalse(model.resourceStore.getResource(0x7f020001)!!.isReachable)
        assertTrue(model.resourceStore.getResource(0x7f020003)!!.isReachable)
        assertTrue(model.resourceStore.getResource(0x7f080002)!!.isReachable)
    }
}

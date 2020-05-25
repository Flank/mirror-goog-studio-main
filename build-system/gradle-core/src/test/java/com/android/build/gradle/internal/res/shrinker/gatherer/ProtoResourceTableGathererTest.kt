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

package com.android.build.gradle.internal.res.shrinker.gatherer

import com.android.aapt.Resources.Attribute.FormatFlags
import com.android.aapt.Resources.Package
import com.android.aapt.Resources.PackageId
import com.android.aapt.Resources.ResourceTable
import com.android.build.gradle.internal.res.shrinker.NoDebugReporter
import com.android.build.gradle.internal.res.shrinker.ResourceShrinkerModel
import com.android.build.gradle.internal.res.shrinker.util.addType
import com.android.build.gradle.internal.res.shrinker.util.attrEntry
import com.android.build.gradle.internal.res.shrinker.util.externalFile
import com.android.build.gradle.internal.res.shrinker.util.noValueEntry
import com.android.build.gradle.internal.res.shrinker.util.styleEntry
import com.android.build.gradle.internal.res.shrinker.util.xmlFile
import com.android.resources.ResourceType
import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProtoResourceTableGathererTest {
    companion object {
        private fun ResourceShrinkerModel.getResourceTypeAndName(
            id: Long
        ): Pair<ResourceType, String>? =
            resourceStore.getResource(id.toInt())?.let { Pair(it.type, it.name) }
    }

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `test gatering resources from resource table`() {
        val basePackage = Package.newBuilder()
            .setPackageId(PackageId.newBuilder().setId(0x7f))
            .setPackageName("com.testapp")
            .addType(
                1,
                "layout",
                xmlFile(0, "my_layout", "/my_layout.xml"),
                xmlFile(1, "another_layout", "/another_layout.xml")
            )
            .addType(
                2,
                "attr",
                attrEntry(0, "my_attr1", FormatFlags.COLOR)
            )
            .addType(
                3,
                "unknown",
                externalFile(0, "log", "/log.log")
            )
            .addType(
                4,
                "style",
                styleEntry(0, "AppTheme.Child.1")
            )
            .addType(
                5,
                "styleable",
                noValueEntry(0, "MyView")
            )
        val featurePackage = Package.newBuilder()
            .setPackageId(PackageId.newBuilder().setId(0x80))
            .setPackageName("com.testapp.feature1")
            .addType(
                1,
                "drawable",
                externalFile(0, "feature_drawable", "/feature_drawable.png"),
                xmlFile(1, "xml_drawable", "/xml_drawable.xml")
            )

        val resourceTable = ResourceTable.newBuilder()
            .addPackage(basePackage)
            .addPackage(featurePackage)
            .build()

        val file = temporaryFolder.newFile("resources.pb")
        Files.write(file.toPath(), resourceTable.toByteArray())

        val model = ResourceShrinkerModel(NoDebugReporter, false)
        ProtoResourceTableGatherer(file.toPath()).gatherResourceValues(model)

        assertThat(model.getResourceTypeAndName(0x7f010001))
            .isEqualTo(Pair(ResourceType.LAYOUT, "another_layout"))
        assertThat(model.getResourceTypeAndName(0x7f020000))
            .isEqualTo(Pair(ResourceType.ATTR, "my_attr1"))
        assertThat(model.getResourceTypeAndName(0x7f030000))
            .isNull()
        assertThat(model.getResourceTypeAndName(0x7f040000))
            .isEqualTo(Pair(ResourceType.STYLE, "AppTheme_Child_1"))
        assertThat(model.getResourceTypeAndName(0x7f050000))
            .isNull()

        assertThat(model.getResourceTypeAndName(0x80010000))
            .isEqualTo(Pair(ResourceType.DRAWABLE, "feature_drawable"))
        assertThat(model.getResourceTypeAndName(0x80010001))
            .isEqualTo(Pair(ResourceType.DRAWABLE, "xml_drawable"))
    }
}

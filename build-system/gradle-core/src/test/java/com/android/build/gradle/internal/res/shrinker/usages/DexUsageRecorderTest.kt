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

import com.android.build.gradle.internal.res.shrinker.NoDebugReporter
import com.android.build.gradle.internal.res.shrinker.ResourceShrinkerModel
import com.android.resources.ResourceType
import com.google.common.io.Resources
import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DexUsageRecorderTest {
    private val PACKAGE_NAME = "com.stub"

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `mark all resources referenced by value as reachable`() {
        val model = createModelWithResources()
        DexUsageRecorder(extractResourceAsDex("resourceShrinker/classes.dex")).recordUsages(model)

        assertThat(model.resourceStore.getResource(0x7f030000)?.isReachable).isTrue()
        assertThat(model.resourceStore.getResource(0x7f080001)?.isReachable).isFalse()
        assertThat(model.resourceStore.getResource(0x7f080000)?.isReachable).isTrue()
        assertThat(model.isFoundGetIdentifier).isFalse()
        assertThat(model.isFoundWebContent).isFalse()
    }

    @Test
    fun `detect Resources_getIdentifier and gather all strings in pool`() {
        val model = createModelWithResources()
        DexUsageRecorder(extractResourceAsDex("resourceShrinker/getidentifier.dex"))
            .recordUsages(model)

        assertThat(model.isFoundGetIdentifier).isTrue()
        assertThat(model.isFoundWebContent).isFalse()
        assertThat(model.strings).containsExactly(
            "com.google.android.samples.dynamicfeatures.ondemand",
            "com.google.android.samples.dynamicfeatures.ondemand.java",
            "true",
            "debug",
            "layout",
            "activity_feature_java"
        )
    }

    @Test
    fun `detect web content via references to WebView_load methods`() {
        val model = createModelWithResources()
        DexUsageRecorder(extractResourceAsDex("resourceShrinker/webcontent.dex"))
            .recordUsages(model)
        assertThat(model.isFoundWebContent).isTrue()
    }

    private fun createModelWithResources(): ResourceShrinkerModel {
        val model = ResourceShrinkerModel(NoDebugReporter, false)
        model.addResource(ResourceType.LAYOUT, PACKAGE_NAME, "activity_main", "0x7f030000")
        model.addResource(ResourceType.ID, PACKAGE_NAME, "action_settings", "0x7f080000")
        model.addResource(ResourceType.ID, PACKAGE_NAME, "action_settings2", "0x7f080001")
        return model
    }

    private fun extractResourceAsDex(resourceName: String): Path {
        val content = Resources.toByteArray(Resources.getResource(resourceName))
        return Files.write(temporaryFolder.newFile("classes.dex").toPath(), content)
    }
}

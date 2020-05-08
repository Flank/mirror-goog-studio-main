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

import com.android.build.gradle.internal.res.shrinker.NoDebugReporter
import com.android.build.gradle.internal.res.shrinker.ResourceShrinkerModel
import com.android.resources.ResourceType.STRING
import com.android.resources.ResourceType.STYLE
import com.android.resources.ResourceType.STYLEABLE
import com.google.common.base.Charsets
import com.google.common.io.Files
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ResourcesGathererFromRTxtTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `test gatering resources from R text file`() {
        val model = ResourceShrinkerModel(NoDebugReporter)
        ResourcesGathererFromRTxt(createSampleRTxt(), "").gatherResourceValues(model)

        assertThat(model.usageModel.resources.map { it.name }).containsExactly(
            "support_simple_spinner_dropdown_item", "ic_launcher", "ic_launcher_round", "text",
            "AppTheme", "AppTheme_AppBarOverlay", "ActionBar"
        )
        assertThat(model.usageModel.getResource(STRING, "text")?.value)
            .isEqualTo(0x7f0d0000)
        assertThat(model.usageModel.getResource(STYLE, "AppTheme")?.value)
            .isEqualTo(0x7f0e0006)
        assertThat(model.usageModel.getResource(STYLEABLE, "ActionBar")?.value)
            .isEqualTo(-1)
    }

    private fun createSampleRTxt(): File {
        val tempFile = temporaryFolder.newFile("R.txt")
        Files.asCharSink(tempFile, Charsets.UTF_8).write(
            """
            int layout support_simple_spinner_dropdown_item 0x7f0b0037
            int mipmap ic_launcher 0x7f0c0000
            int mipmap ic_launcher_round 0x7f0c0001
            int string text 0x7f0d0000
            int style AppTheme 0x7f0e0006
            int style AppTheme_AppBarOverlay 0x7f0e0007
            int[] styleable ActionBar { 0x7f030031, 0x7f030032, 0x7f030033 }
            int styleable ActionBar_background 0
            int styleable ActionBar_backgroundSplit 1
            int styleable ActionBar_backgroundStacked 2
        """.trimIndent()
        )
        return tempFile
    }
}

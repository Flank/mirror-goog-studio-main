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
import com.android.utils.FileUtils.writeToFile
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ToolsAttributeUsageRecorderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `record tools_keep tools_discard attributes from xml files`() {
        writeToFile(
            File(temporaryFolder.root, "keep.xml"),
            """
                <resources xmlns:tools="http://schemas.android.com/tools"
                    tools:keep="layout/a,string/*" />
            """.trimIndent()
        )
        writeToFile(
            File(temporaryFolder.root, "keep2.xml"),
            """
                <resources xmlns:tools="http://schemas.android.com/tools"
                    tools:keep="layout/b" />
            """.trimIndent()
        )
        writeToFile(
            File(temporaryFolder.root, "discard_shrinkMode.XML"),
            """
                <resources xmlns:tools="http://schemas.android.com/tools"
                    tools:discard="drawable/hello" tools:shrinkMode="strict" />
            """.trimIndent()
        )

        val model = ResourceShrinkerModel(NoDebugReporter, false)
        ToolsAttributeUsageRecorder(temporaryFolder.root.toPath()).recordUsages(model)

        assertThat(model.resourceStore.keepAttributes)
            .containsExactly("layout/a", "string/*", "layout/b")
        assertThat(model.resourceStore.discardAttributes).containsExactly("drawable/hello")
        assertThat(model.resourceStore.safeMode).isFalse()
    }

    @Test
    fun `do not record tools attributes from inner element`() {
        writeToFile(
            File(temporaryFolder.root, "keep.xml"),
            """
                <root>
                  <resources xmlns:tools="http://schemas.android.com/tools"
                      tools:keep="layout/a,string/*" />
                </root>      
            """.trimIndent()
        )

        val model = ResourceShrinkerModel(NoDebugReporter, false)
        ToolsAttributeUsageRecorder(temporaryFolder.root.toPath()).recordUsages(model)

        assertThat(model.resourceStore.keepAttributes).isEmpty()
    }

    @Test
    fun `do not record tools attributes from wrong namespace`() {
        writeToFile(
            File(temporaryFolder.root, "keep.xml"),
            """
                <resources xmlns:tools="http://noschemas.com/tools"
                    tools:keep="layout/a,string/*" />
            """.trimIndent()
        )

        val model = ResourceShrinkerModel(NoDebugReporter, false)
        ToolsAttributeUsageRecorder(temporaryFolder.root.toPath()).recordUsages(model)

        assertThat(model.resourceStore.keepAttributes).isEmpty()
    }
}

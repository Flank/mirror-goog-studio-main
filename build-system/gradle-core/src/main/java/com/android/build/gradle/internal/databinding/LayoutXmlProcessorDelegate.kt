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

package com.android.build.gradle.internal.databinding

import android.databinding.tool.LayoutXmlProcessor
import android.databinding.tool.writer.JavaFileWriter
import com.android.ide.common.blame.MergingLog
import com.android.ide.common.blame.SourceFile
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import java.io.File

/**
 * Delegate to handle [LayoutXmlProcessor] in tasks.
 *
 * An instance of this class should be a [org.gradle.api.tasks.Nested] field on the task.
 */
class LayoutXmlProcessorDelegate(
    @get:Input
    val packageName: String,
    @get:Input
    val useAndroidX: Boolean,
    // output is already handled by the task, but this should be clean-up (along with all VariantPathHelper)
    private val resourceBlameLogDir: File
) {

    @get:Internal
    val layoutXmlProcessor: LayoutXmlProcessor by lazy {
        LayoutXmlProcessor(
            packageName,
            CustomJavaFileWriter(),
            LayoutXmlProcessor.OriginalFileLookup { file: File ->
                val input = SourceFile(file)
                val original = MergingLog(resourceBlameLogDir).find(input)
                if (original === input) null else original.sourceFile
            },
            useAndroidX
        )
    }

    private class CustomJavaFileWriter: JavaFileWriter() {
        override fun deleteFile(canonicalName: String?) {
            throw UnsupportedOperationException("Not supported in this mode")
        }

        override fun writeToFile(canonicalName: String?, contents: String?) {
            throw UnsupportedOperationException("Not supported in this mode")
        }
    }
}
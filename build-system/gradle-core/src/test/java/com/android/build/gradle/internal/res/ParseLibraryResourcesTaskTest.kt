/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.res

import com.android.build.gradle.internal.res.ParseLibraryResourcesTask.Companion.canBeProcessedIncrementally
import com.android.build.gradle.internal.res.ParseLibraryResourcesTask.Companion.canGenerateSymbols
import com.android.builder.files.SerializableChange
import com.android.ide.common.resources.FileStatus
import com.android.resources.ResourceFolderType
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ParseLibraryResourcesTaskTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun testCanGenerateSymbols() {
        val xmlFile = temporaryFolder.newFile("tmp.XML")
        val pngFile = temporaryFolder.newFile("tmp.PNG")

        assertThat(canGenerateSymbols(ResourceFolderType.VALUES, xmlFile)).isTrue()
        assertThat(canGenerateSymbols(ResourceFolderType.LAYOUT, xmlFile)).isTrue()
        assertThat(canGenerateSymbols(ResourceFolderType.DRAWABLE, xmlFile)).isTrue()
        assertThat(canGenerateSymbols(ResourceFolderType.DRAWABLE, pngFile)).isFalse()
        assertThat(canGenerateSymbols(ResourceFolderType.RAW, xmlFile)).isFalse()
        assertThat(canGenerateSymbols(ResourceFolderType.RAW, pngFile)).isFalse()
    }

    @Test
    fun testCanBeProcessedIncrementally() {
        val valuesDir = temporaryFolder.newFolder("values-en")
        val valuesFile = File(valuesDir, "values-en.xml")

        val drawableDir = temporaryFolder.newFolder("drawable")
        val drawableXml = File(drawableDir, "vector.xml")
        val drawablePng = File(drawableDir, "img.png")

        val removedFile =
            SerializableChange(valuesFile, FileStatus.REMOVED, valuesFile.absolutePath)
        val addedFile =
            SerializableChange(valuesFile, FileStatus.NEW, valuesFile.absolutePath)
        val modifiedValuesFile =
            SerializableChange(valuesFile, FileStatus.CHANGED, valuesFile.absolutePath)
        assertThat(canBeProcessedIncrementally(removedFile)).isFalse()
        assertThat(canBeProcessedIncrementally(addedFile)).isTrue()
        assertThat(canBeProcessedIncrementally(modifiedValuesFile)).isFalse()

        val modifiedDrawableXml =
            SerializableChange(drawableXml, FileStatus.CHANGED, drawableXml.absolutePath)
        val modifiedDrawablePng =
            SerializableChange(drawablePng, FileStatus.CHANGED, drawablePng.absolutePath)
        assertThat(canBeProcessedIncrementally(modifiedDrawableXml)).isFalse()
        assertThat(canBeProcessedIncrementally(modifiedDrawablePng)).isTrue()
    }
}
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
import com.android.utils.FileUtils
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

    @Test
    fun testDoFullTaskAction_producesExpectedSymbolTableFile() {
        val parentFolder = temporaryFolder.newFolder("parent")
        val resourcesFolder = createFakeResourceDirectory(parentFolder)

        val platformAttrsRTxtFile = File(parentFolder, "R.txt")
        val librarySymbolsFile = File(parentFolder, "R-def.txt")

        val params = ParseLibraryResourcesTask.ParseResourcesParams(
          inputResDir = resourcesFolder,
          changedResources = emptyList(),
          platformAttrsRTxt = platformAttrsRTxtFile,
          librarySymbolsFile = librarySymbolsFile,
          incremental = false
        )
        doFullTaskAction(params)
        assertThat(librarySymbolsFile.readLines()).containsExactly(
          "R_DEF: Internal format may change without notice",
          "local",
          "drawable img",
          "layout main_activity",
          "layout content_layout",
          "string greeting"
        )
    }

    @Test
    fun testDoIncrementalTaskAction_producesExpectedSymbolTableFileFromAddedResource() {
        val parentFolder = temporaryFolder.newFolder("parent")
        val resourcesFolder = createFakeResourceDirectory(parentFolder)

        val platformAttrsRTxtFile = File(parentFolder, "R-def.txt")
        val librarySymbolsFile = File(parentFolder, "Symbols.txt")
        val addedLayout = File(
          FileUtils.join(resourcesFolder.path, "/layout"), "second_activity.xml")

        FileUtils.createFile(librarySymbolsFile,
          "R_DEF: Internal format may change without notice\n" +
              "local\n" +
              "drawable img\n" +
              "layout main_activity\n" +
              "layout content_layout\n" +
              "string greeting")
        FileUtils.createFile(addedLayout, "<root></root>")

        val changedResources = listOf(
          SerializableChange(addedLayout, FileStatus.NEW, addedLayout.absolutePath)
        )

        val params = ParseLibraryResourcesTask.ParseResourcesParams(
          inputResDir = resourcesFolder,
          changedResources = changedResources,
          platformAttrsRTxt = platformAttrsRTxtFile,
          librarySymbolsFile = librarySymbolsFile,
          incremental = true
        )

        doIncrementalTaskAction(params)
        assertThat(librarySymbolsFile.readLines()).containsExactly(
          "R_DEF: Internal format may change without notice",
          "local",
          "drawable img",
          "layout main_activity",
          "layout content_layout",
          "layout second_activity",
          "string greeting"
        )
    }

    private fun createFakeResourceDirectory(parentFolder : File): File {
        val resourcesFolder = File(parentFolder, "res")
        val drawableFolder = File(resourcesFolder, "drawable")
        val layoutFolder = File(resourcesFolder, "layout")
        val valuesFolder = File(resourcesFolder, "values")

        val mainActivityFile = File(layoutFolder, "main_activity.xml")
        val contentLayoutFile = File(layoutFolder, "content_layout.xml")
        val imageFile = File(drawableFolder, "img.png")
        val stringsFile = File(valuesFolder, "strings.xml")

        FileUtils.createFile(mainActivityFile, "<root></root>")
        FileUtils.createFile(contentLayoutFile, "<root></root>")
        FileUtils.createFile(imageFile, "34324234")
        FileUtils.createFile(
          stringsFile,
          """<resources><string name="greeting">Hello</string></resources>"""
        )
        return resourcesFolder
    }
}
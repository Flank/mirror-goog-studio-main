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

import com.android.SdkConstants
import com.android.builder.files.SerializableChange
import com.android.ide.common.resources.FileStatus
import com.android.ide.common.symbols.SymbolTable
import com.android.resources.ResourceFolderType
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class ParseLibraryResourcesTaskTest(private val enablePartialRIncrementalBuilds: Boolean) {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun enablePartialRIncrementalBuilds() = arrayOf(true, false)
    }

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
        val partialRDirectory = File(parentFolder, SdkConstants.FD_PARTIAL_R)

        val params = ParseLibraryResourcesTask.ParseResourcesParams(
          inputResDir = resourcesFolder,
          changedResources = emptyList(),
          platformAttrsRTxt = platformAttrsRTxtFile,
          librarySymbolsFile = librarySymbolsFile,
          incremental = false,
          partialRDir = partialRDirectory,
          enablePartialRIncrementalBuilds = enablePartialRIncrementalBuilds
        )
        doFullTaskAction(params)
        assertThat(librarySymbolsFile.readLines()).containsExactly(
          "R_DEF: Internal format may change without notice",
          "local",
          "drawable img",
          "layout main_activity",
          "layout content_layout",
          "string greeting",
          "string farewell"
        )
    }

    @Test
    fun testDoFullTaskAction_producesExpectedPartialRFiles() {
        val parentFolder = temporaryFolder.newFolder("parent")
        val resourcesFolder = createFakeResourceDirectory(parentFolder)

        val platformAttrsRTxtFile = File(parentFolder, "R.txt")
        val librarySymbolsFile = File(parentFolder, "R-def.txt")
        val partialRDirectory = File(parentFolder, SdkConstants.FD_PARTIAL_R)

        val params = ParseLibraryResourcesTask.ParseResourcesParams(
                inputResDir = resourcesFolder,
                changedResources = emptyList(),
                platformAttrsRTxt = platformAttrsRTxtFile,
                librarySymbolsFile = librarySymbolsFile,
                incremental = false,
                partialRDir = partialRDirectory,
                enablePartialRIncrementalBuilds = enablePartialRIncrementalBuilds
        )
        doFullTaskAction(params)

        val createdPartialRFiles = partialRDirectory.walkTopDown()
                .toList().filter { it.isFile }.sortedBy { it.name }
        if (enablePartialRIncrementalBuilds){
            assertThat(createdPartialRFiles.size).isEqualTo(4)
            assertThat(createdPartialRFiles[1].name).isEqualTo("layout_content_layout.xml.flat-R.txt")
            assertThat(createdPartialRFiles[3].readLines()).containsExactly(
                    "undefined int string farewell", "undefined int string greeting")
        } else {
            assertThat(createdPartialRFiles.size).isEqualTo(0)
        }
    }

    @Test
    fun testDoIncrementalTaskAction_producesExpectedSymbolTableFileFromAddedResource() {
        val parentFolder = temporaryFolder.newFolder("parent")
        val resourcesFolder = createFakeResourceDirectory(parentFolder)

        val platformAttrsRTxtFile = File(parentFolder, "R-def.txt")
        val librarySymbolsFile = File(parentFolder, "Symbols.txt")
        val partialRFiles = temporaryFolder.newFolder(SdkConstants.FD_PARTIAL_R)
        val addedLayout = File(
          FileUtils.join(resourcesFolder.path, "layout"), "second_activity.xml")

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
          incremental = true,
          partialRDir = partialRFiles,
          enablePartialRIncrementalBuilds = enablePartialRIncrementalBuilds
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

    @Test
    fun testGenerateResourceSymbolTables_generatesExpectedSymbolTables() {
        val parentFolder = temporaryFolder.newFolder("parent")
        val fakeResourceDirectory = createFakeResourceDirectory(parentFolder)

        val symbolTables = generateResourceSymbolTables(fakeResourceDirectory, null).toTypedArray()

        assertThat(symbolTables.count()).isEqualTo(4)
        assertThat(symbolTables[0].symbolTable.symbols.values().toList().toString()).isEqualTo(
                "[UNDEFINED drawable img = 0x0]"
        )
        assertThat(symbolTables[1].symbolTable.symbols.values().toList().toString()).isEqualTo(
                "[UNDEFINED layout content_layout = 0x0]"
        )
        assertThat(symbolTables[3].symbolTable.symbols.values().toList().toString()).isEqualTo(
                "[UNDEFINED string greeting = 0x0, UNDEFINED string farewell = 0x0]"
        )
    }

    @Test
    fun testSavePartialRFilesToDirectory_checkAllPartialFilesSaved() {
        val parentFolder = temporaryFolder.newFolder("parent")
        val fakeResourceDirectory = createFakeResourceDirectory(parentFolder)
        val partialRFileDirectory = File(parentFolder, "partialR")

        val emptyPlatformAttrSymbolTable = SymbolTable.builder().build()
        val symbolTables =
                generateResourceSymbolTables(fakeResourceDirectory, emptyPlatformAttrSymbolTable)

        savePartialRFilesToDirectory(symbolTables, partialRFileDirectory)

        val createdPartialRFiles = partialRFileDirectory.walkTopDown().toList().filter { it.isFile }
        assertThat(createdPartialRFiles.size).isEqualTo(4)
        assertThat(createdPartialRFiles
                .first { it.name == "values_strings.arsc.flat-R.txt" }
                .readLines())
                .containsExactly(
                        "undefined int string greeting",
                        "undefined int string farewell"
                )
        assertThat(createdPartialRFiles
                .first { it.name == "layout_main_activity.xml.flat-R.txt" }
                .readLines())
                .containsExactly(
                        "undefined int layout main_activity"
                )
        assertThat(createdPartialRFiles
                .first { it.name == "drawable_img.png.flat-R.txt" }
                .readLines())
                .containsExactly(
                        "undefined int drawable img"
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
          """<resources><string name="greeting">Hello</string>
              <string name="farewell">Goodbye</string></resources>"""
        )
        return resourcesFolder
    }
}
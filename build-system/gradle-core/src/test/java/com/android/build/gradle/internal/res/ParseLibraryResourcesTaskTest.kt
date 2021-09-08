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
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.builder.files.SerializableChange
import com.android.ide.common.resources.FileStatus
import com.android.ide.common.symbols.SymbolTable
import com.android.resources.ResourceFolderType
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(Parameterized::class)
class ParseLibraryResourcesTaskTest(
        private val usePartialR: Boolean,
        private val useResourceValidation: Boolean
) {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var objects: ObjectFactory

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name="usePartialR={0}, useResourceValidation={1}")
        fun data() = arrayOf(
                arrayOf(true, true), arrayOf(false, false),
                arrayOf(true, false), arrayOf(false, true)
        )
    }

    @Before
    fun setUp() {
        objects = ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build().objects
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
        val modifiedDrawableXml =
                SerializableChange(drawableXml, FileStatus.CHANGED, drawableXml.absolutePath)
        val modifiedDrawablePng =
                SerializableChange(drawablePng, FileStatus.CHANGED, drawablePng.absolutePath)

        if (!usePartialR) {
            assertThat(canBeProcessedIncrementallyWithRDef(removedFile))
                    .isFalse()
            assertThat(canBeProcessedIncrementallyWithRDef(addedFile))
                    .isTrue()
            assertThat(canBeProcessedIncrementallyWithRDef(modifiedValuesFile))
                    .isFalse()
            assertThat(canBeProcessedIncrementallyWithRDef(modifiedDrawableXml))
                    .isFalse()
        }
        assertThat(canBeProcessedIncrementallyWithRDef(addedFile))
                .isTrue()
        assertThat(canBeProcessedIncrementallyWithRDef(modifiedDrawablePng))
                .isTrue()
    }

    @Test
    fun testDoFullTaskAction_producesExpectedSymbolTableFile() {
        val parentFolder = temporaryFolder.newFolder("parent")
        val resourcesFolder = createFakeResourceDirectory(parentFolder)

        val platformAttrsRTxtFile = File(parentFolder, "R.txt")
        val librarySymbolsFile = File(parentFolder, "R-def.txt")
        val partialRDirectory = File(parentFolder, SdkConstants.FD_PARTIAL_R)

        val params = object : ParseLibraryResourcesTask.ParseResourcesParams() {
            override val inputResDir = objects.directoryProperty().fileValue(resourcesFolder)
            override val platformAttrsRTxt = objects.fileProperty().fileValue(platformAttrsRTxtFile)
            override val librarySymbolsFile = objects.fileProperty().fileValue(librarySymbolsFile)
            override val incremental = FakeGradleProperty(false)
            override val changedResources = objects.listProperty(SerializableChange::class.java)
            override val partialRDir = objects.directoryProperty().fileValue(partialRDirectory)
            override val enablePartialRIncrementalBuilds =
                FakeGradleProperty(this@ParseLibraryResourcesTaskTest.usePartialR)
            override val projectPath = FakeGradleProperty("projectName")
            override val taskOwner = FakeGradleProperty("taskOwner")
            override val workerKey = FakeGradleProperty("workerKey")
            override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(
                FakeNoOpAnalyticsService()
            )
            override val validateResources = FakeGradleProperty(useResourceValidation)
        }
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

        val params = object : ParseLibraryResourcesTask.ParseResourcesParams() {
            override val inputResDir = objects.directoryProperty().fileValue(resourcesFolder)
            override val platformAttrsRTxt = objects.fileProperty().fileValue(platformAttrsRTxtFile)
            override val librarySymbolsFile = objects.fileProperty().fileValue(librarySymbolsFile)
            override val incremental = FakeGradleProperty(false)
            override val changedResources = objects.listProperty(SerializableChange::class.java)
            override val partialRDir = objects.directoryProperty().fileValue(partialRDirectory)
            override val enablePartialRIncrementalBuilds =
                FakeGradleProperty(this@ParseLibraryResourcesTaskTest.usePartialR)
            override val projectPath = FakeGradleProperty("projectName")
            override val taskOwner = FakeGradleProperty("taskOwner")
            override val workerKey = FakeGradleProperty("workerKey")
            override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(FakeNoOpAnalyticsService())
            override val validateResources = FakeGradleProperty(useResourceValidation)
        }

        doFullTaskAction(params)

        val createdPartialRFiles = partialRDirectory.walkTopDown()
                .toList().filter { it.isFile }.sortedBy { it.name }
        if (usePartialR){
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
        val partialRFolder = createPartialRDirectory(File(parentFolder, SdkConstants.FD_PARTIAL_R))

        val platformAttrsRTxtFile = File(parentFolder, "R-def.txt")
        val librarySymbolsFile = File(parentFolder, "Symbols.txt")
        val addedLayout = File(
          FileUtils.join(resourcesFolder.path, "layout"), "second_activity.xml")

        FileUtils.createFile(librarySymbolsFile,
          "R_DEF: Internal format may change without notice\n" +
              "local\n" +
              "drawable img\n" +
              "layout main_activity\n" +
              "layout content_layout\n" +
              "string greeting\n"+
              "string farewell")
        FileUtils.createFile(addedLayout, "<root></root>")

        val changedResources = listOf(
          SerializableChange(addedLayout, FileStatus.NEW, addedLayout.absolutePath)
        )

        val params = object : ParseLibraryResourcesTask.ParseResourcesParams() {
            override val inputResDir = objects.directoryProperty().fileValue(resourcesFolder)
            override val platformAttrsRTxt = objects.fileProperty().fileValue(platformAttrsRTxtFile)
            override val librarySymbolsFile = objects.fileProperty().fileValue(librarySymbolsFile)
            override val incremental = FakeGradleProperty(true)
            override val changedResources = objects.listProperty(SerializableChange::class.java).value(changedResources)
            override val partialRDir = objects.directoryProperty().fileValue(partialRFolder)
            override val enablePartialRIncrementalBuilds =
                FakeGradleProperty(this@ParseLibraryResourcesTaskTest.usePartialR)
            override val projectPath = FakeGradleProperty("projectName")
            override val taskOwner = FakeGradleProperty("taskOwner")
            override val workerKey = FakeGradleProperty("workerKey")
            override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(FakeNoOpAnalyticsService())
            override val validateResources = FakeGradleProperty(useResourceValidation)
        }

        if (usePartialR){
            doIncrementalPartialRTaskAction(params)
        } else {
            doIncrementalRDefTaskAction(params)
        }

        assertThat(librarySymbolsFile.readLines()).containsExactly(
          "R_DEF: Internal format may change without notice",
          "local",
          "drawable img",
          "layout main_activity",
          "layout content_layout",
          "layout second_activity",
          "string farewell",
          "string greeting"
        )
    }

    @Test
    fun testDoIncrementalTaskAction_producesExpectedSymbolTableFileFromModifiedResource() {
        val parentFolder = temporaryFolder.newFolder("parent")
        val resourcesFolder = createFakeResourceDirectory(parentFolder)
        val partialRFolder = createPartialRDirectory(File(parentFolder, SdkConstants.FD_PARTIAL_R))

        val platformAttrsRTxtFile = File(parentFolder, "R-def.txt")
        val librarySymbolsFile = File(parentFolder, "Symbols.txt")
        val modifiedLayout =
                File(FileUtils.join(resourcesFolder.path, "layout"), "second_activity.xml")
        val modifiedLayoutPartialR =
                File(partialRFolder, "layout_second_activity.xml.flat-R.txt")

        FileUtils.createFile(librarySymbolsFile,
                "R_DEF: Internal format may change without notice\n" +
                        "local\n" +
                        "drawable img\n" +
                        "layout main_activity\n" +
                        "layout content_layout\n" +
                        "layout second_activity\n" +
                        "id chipOne\n" +
                        "string greeting\n" +
                        "string farewell")
        FileUtils.createFile(modifiedLayoutPartialR,
                "undefined int layout second_activity\n" +
                        "undefined int id chipOne"
        )
        FileUtils.createFile(modifiedLayout,
                """<root>
                        <com.google.android.material.chip.Chip
                            android:id="@+id/chipOne"/>
                     </root>""")
        FileUtils.writeToFile(modifiedLayout,
                """<root>
                        <com.google.android.material.chip.Chip
                            android:id="@+id/chipTwo"/>
                     </root>""")
        val changedResources = listOf(
                SerializableChange(modifiedLayout, FileStatus.CHANGED, modifiedLayout.absolutePath)
        )
        val params = object : ParseLibraryResourcesTask.ParseResourcesParams() {
            override val inputResDir = objects.directoryProperty().fileValue(resourcesFolder)
            override val platformAttrsRTxt = objects.fileProperty().fileValue(platformAttrsRTxtFile)
            override val librarySymbolsFile = objects.fileProperty().fileValue(librarySymbolsFile)
            override val incremental = FakeGradleProperty(true)
            override val changedResources =
                objects.listProperty(SerializableChange::class.java).value(changedResources)
            override val partialRDir = objects.directoryProperty().fileValue(partialRFolder)
            override val enablePartialRIncrementalBuilds =
                FakeGradleProperty(this@ParseLibraryResourcesTaskTest.usePartialR)
            override val projectPath = FakeGradleProperty("projectName")
            override val taskOwner = FakeGradleProperty("taskOwner")
            override val workerKey = FakeGradleProperty("workerKey")
            override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(FakeNoOpAnalyticsService())
            override val validateResources = FakeGradleProperty(useResourceValidation)
        }

        if (usePartialR) {
            doIncrementalPartialRTaskAction(params)
            assertThat(librarySymbolsFile.readLines()).containsExactly(
                    "R_DEF: Internal format may change without notice",
                    "local",
                    "drawable img",
                    "id chipTwo",
                    "layout main_activity",
                    "layout content_layout",
                    "layout second_activity",
                    "string farewell",
                    "string greeting"
            )
            assertThat(librarySymbolsFile.readLines()).doesNotContain("id chipOne")
        }
        parentFolder.delete()
    }

    @Test
    fun testDoIncrementalTaskAction_producesExpectedSymbolTableFileFromRemovedResource() {
        val parentFolder = temporaryFolder.newFolder("parent")
        val resourcesFolder = createFakeResourceDirectory(parentFolder)
        val partialRFolder = createPartialRDirectory(File(parentFolder, SdkConstants.FD_PARTIAL_R))

        val platformAttrsRTxtFile = File(parentFolder, "R-def.txt")
        val librarySymbolsFile = File(parentFolder, "Symbols.txt")
        val removedLayout = File(
                FileUtils.join(resourcesFolder.path, "layout"), "second_activity.xml")
        val removedLayoutPartialR = File(partialRFolder, "layout_second_activity.xml.flat-R.txt")

        FileUtils.createFile(librarySymbolsFile,
                "R_DEF: Internal format may change without notice\n" +
                        "local\n" +
                        "drawable img\n" +
                        "layout main_activity\n" +
                        "layout content_layout\n" +
                        "layout second_activity\n" +
                        "id chipOne\n" +
                        "string greeting\n" +
                        "string farewell")
        FileUtils.createFile(removedLayout,
                """<root>
                        <com.google.android.material.chip.Chip
                            android:id="@+id/chipOne"/>
                     </root>""")
        FileUtils.createFile(removedLayoutPartialR,
                "undefined int layout second_activity\n" +
                "undefined int id chipOne")
        FileUtils.delete(removedLayout)
        val changedResources = listOf(
                SerializableChange(removedLayout, FileStatus.REMOVED, removedLayout.absolutePath)
        )
        val params = object : ParseLibraryResourcesTask.ParseResourcesParams() {
            override val inputResDir = objects.directoryProperty().fileValue(resourcesFolder)
            override val platformAttrsRTxt = objects.fileProperty().fileValue(platformAttrsRTxtFile)
            override val librarySymbolsFile = objects.fileProperty().fileValue(librarySymbolsFile)
            override val incremental = FakeGradleProperty(true)
            override val changedResources =
                objects.listProperty(SerializableChange::class.java).value(changedResources)
            override val partialRDir = objects.directoryProperty().fileValue(partialRFolder)
            override val enablePartialRIncrementalBuilds = FakeGradleProperty(true)
            override val projectPath = FakeGradleProperty("projectName")
            override val taskOwner = FakeGradleProperty("taskOwner")
            override val workerKey = FakeGradleProperty("workerKey")
            override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(FakeNoOpAnalyticsService())
            override val validateResources = FakeGradleProperty(useResourceValidation)
        }

        if (usePartialR) {
            doIncrementalPartialRTaskAction(params)
            assertThat(librarySymbolsFile.readLines()).containsExactly(
                    "R_DEF: Internal format may change without notice",
                    "local",
                    "drawable img",
                    "layout main_activity",
                    "layout content_layout",
                    "string farewell",
                    "string greeting"
            )
        }
    }

    @Test
    fun testGenerateResourceSymbolTables_generatesExpectedSymbolTables() {
        val parentFolder = temporaryFolder.newFolder("parent")
        val fakeResourceDirectory = createFakeResourceDirectory(parentFolder)
        val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

        val symbolTables = getResourceDirectorySymbolTables(
                fakeResourceDirectory, null, documentBuilder)
                .toTypedArray()

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
        val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

        val emptyPlatformAttrSymbolTable = SymbolTable.builder().build()
        val symbolTables =
                getResourceDirectorySymbolTables(fakeResourceDirectory,
                        emptyPlatformAttrSymbolTable, documentBuilder)

        writeSymbolTablesToPartialRFiles(symbolTables, partialRFileDirectory)

        val createdPartialRFiles =
                partialRFileDirectory.walkTopDown().toList().filter { it.isFile }
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

    @Test
    fun testResourceValidation_isDisabledByFlag() {
        val parentFolder = temporaryFolder.newFolder("parent")
        val fakeResourceDirectory = createFakeResourceDirectory(parentFolder)
        val partialRFolder = temporaryFolder.newFolder()
        val platformAttrsRTxtFile = File(parentFolder, "R-def.txt")
        val librarySymbolsFile = File(parentFolder, "Symbols.txt")

        // Invalid layout (extension is png rather than xml).
        File(FileUtils.join(fakeResourceDirectory.path, "layout"), "second_activity.png").also {
            FileUtils.createFile(it, "")
        }

        val params = object : ParseLibraryResourcesTask.ParseResourcesParams() {
            override val inputResDir = objects.directoryProperty().fileValue(fakeResourceDirectory)
            override val platformAttrsRTxt = objects.fileProperty().fileValue(platformAttrsRTxtFile)
            override val librarySymbolsFile = objects.fileProperty().fileValue(librarySymbolsFile)
            override val incremental = FakeGradleProperty(false)
            override val changedResources =
                    objects.listProperty(SerializableChange::class.java).value(emptyList())
            override val partialRDir = objects.directoryProperty().fileValue(partialRFolder)
            override val enablePartialRIncrementalBuilds = FakeGradleProperty(true)
            override val projectPath = FakeGradleProperty("projectName")
            override val taskOwner = FakeGradleProperty("taskOwner")
            override val workerKey = FakeGradleProperty("workerKey")
            override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(FakeNoOpAnalyticsService())
            override val validateResources = FakeGradleProperty(false)
        }
        doFullTaskAction(params)
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

    private fun createPartialRDirectory(partialRFolder: File): File {
        val partialRMap = mapOf(
                "values_strings.arsc.flat-R.txt" to "undefined int string farewell\n" +
                        "undefined int string greeting",
                "layout_main_activity.xml.flat-R.txt" to "undefined int layout main_activity",
                "layout_content_layout.xml.flat-R.txt" to "undefined int layout content_layout",
                "drawable_img.png.flat-R.txt" to "undefined int drawable img"
        )
        partialRMap.forEach { (fileName, fileContent) ->
            FileUtils.createFile(File(partialRFolder, fileName), fileContent)
        }
        return partialRFolder
    }
}

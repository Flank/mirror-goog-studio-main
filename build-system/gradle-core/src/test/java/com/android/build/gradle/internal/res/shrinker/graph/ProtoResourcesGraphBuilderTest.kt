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

package com.android.build.gradle.internal.res.shrinker.graph

import com.android.aapt.Resources.Package
import com.android.aapt.Resources.PackageId
import com.android.aapt.Resources.XmlNode
import com.android.build.gradle.internal.res.shrinker.NoDebugReporter
import com.android.build.gradle.internal.res.shrinker.ResourceShrinkerModel
import com.android.build.gradle.internal.res.shrinker.ShrinkerDebugReporter
import com.android.build.gradle.internal.res.shrinker.util.addAttribute
import com.android.build.gradle.internal.res.shrinker.util.addAttributeWithRefNameOnly
import com.android.build.gradle.internal.res.shrinker.util.addChild
import com.android.build.gradle.internal.res.shrinker.util.addNamespace
import com.android.build.gradle.internal.res.shrinker.util.addType
import com.android.build.gradle.internal.res.shrinker.util.arrayEntry
import com.android.build.gradle.internal.res.shrinker.util.buildResourceTable
import com.android.build.gradle.internal.res.shrinker.util.externalFile
import com.android.build.gradle.internal.res.shrinker.util.pluralsEntry
import com.android.build.gradle.internal.res.shrinker.util.stringEntry
import com.android.build.gradle.internal.res.shrinker.util.writeToFile
import com.android.build.gradle.internal.res.shrinker.util.xmlElement
import com.android.build.gradle.internal.res.shrinker.util.xmlFile
import com.android.resources.ResourceType
import com.android.resources.ResourceType.DRAWABLE
import com.android.resources.ResourceType.ID
import com.android.resources.ResourceType.MENU
import com.android.resources.ResourceType.RAW
import com.android.resources.ResourceType.STRING
import com.android.resources.ResourceType.XML
import com.android.utils.FileUtils.writeToFile
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import org.gradle.api.logging.LogLevel
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProtoResourcesGraphBuilderTest {
    private val PACKAGE_NAME = "com.test"
    private val DYNAMIC_FEATURE_PACKAGE_NAME = "com.test.feature"
    private val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    companion object {
        fun ResourceShrinkerModel.referencesFor(resourceId: Int) =
            resourceStore.getResource(resourceId)?.references?.map { it.name } ?: emptyList()

        fun ResourceShrinkerModel.referencesWithPackage(resourceId: Int) =
            resourceStore.getResource(resourceId)
                ?.references?.map { "${it.packageName}:${it.name}" } ?: emptyList()
    }

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `find simple references between resources`() {
        val model = ResourceShrinkerModel(NoDebugReporter, false)
        (1..4).forEach { i ->
            model.addResource(STRING, PACKAGE_NAME, "string$i", i)
        }

        val table = createResourceTable {
            it.addType(
                0,
                "string",
                stringEntry(1, "string1", "Hello world"),
                stringEntry(2, "string2", refName = "string/string1"),
                stringEntry(3, "string3", "Bye-bye"),
                stringEntry(4, "string4", refId = 3)
            )
        }

        ProtoResourcesGraphBuilder(temporaryFolder.root.toPath(), table.toPath())
            .buildGraph(model)

        assertThat(model.referencesFor(1)).isEmpty()
        assertThat(model.referencesFor(2)).containsExactly("string1")
        assertThat(model.referencesFor(4)).containsExactly("string3")
    }

    @Test
    fun `find simple references between resources in multi-modules project`() {
        val model = ResourceShrinkerModel(NoDebugReporter, true)
        (1..4).forEach { i ->
            model.addResource(STRING, PACKAGE_NAME, "string$i", i)
            model.addResource(STRING, DYNAMIC_FEATURE_PACKAGE_NAME, "string$i", 0x01000000 + i)
        }

        val table = createResourceTable {
            it.addType(
                0,
                "string",
                stringEntry(1, "string1", "Hello world"),
                // reference by name (resource from both packages are counted as referenced)
                stringEntry(2, "string2", refName = "string/string1"),
                // reference to the same package
                stringEntry(3, "string3", refId = 0x00000001),
                // reference to another package
                stringEntry(4, "string4", refId = 0x01000003)
            )
        }

        ProtoResourcesGraphBuilder(temporaryFolder.root.toPath(), table.toPath())
            .buildGraph(model)

        assertThat(model.referencesWithPackage(1)).isEmpty()
        assertThat(model.referencesWithPackage(2))
            .containsExactly("com.test:string1", "com.test.feature:string1")
        assertThat(model.referencesWithPackage(3)).containsExactly("com.test:string1")
        assertThat(model.referencesWithPackage(4)).containsExactly("com.test.feature:string3")
    }

    @Test
    fun `find references from array`() {
        val model = ResourceShrinkerModel(NoDebugReporter, false)
        model.addResource(ResourceType.ARRAY, PACKAGE_NAME, "string-array", 0x10000)
        model.addResource(STRING, PACKAGE_NAME, "string1", 1)
        model.addResource(STRING, PACKAGE_NAME, "string2", 2)
        model.addResource(STRING, PACKAGE_NAME, "string3", 3)

        val table = createResourceTable {
            it.addType(
                0,
                "string",
                stringEntry(1, "string1", "Hello world"),
                stringEntry(2, "string2", "Bye-bye"),
                stringEntry(3, "string3", "Hey")
            )
            it.addType(
                1,
                "array",
                arrayEntry(0, "string-array", listOf("inlined"), listOf(3, 1))
            )
        }

        ProtoResourcesGraphBuilder(temporaryFolder.root.toPath(), table.toPath())
            .buildGraph(model)

        assertThat(model.referencesFor(0x10000)).containsExactly("string1", "string3")
    }

    @Test
    fun `find references from plurals`() {
        val model = ResourceShrinkerModel(NoDebugReporter, false)
        model.addResource(ResourceType.PLURALS, PACKAGE_NAME, "my_plurals", 0x10000)
        model.addResource(STRING, PACKAGE_NAME, "string1", 1)
        model.addResource(STRING, PACKAGE_NAME, "string2", 2)

        val table = createResourceTable {
            it.addType(
                0,
                "string",
                stringEntry(1, "string1", "Hello world"),
                stringEntry(2, "string2", "Bye-bye")
            )
            it.addType(
                1,
                "plurals",
                pluralsEntry(0, "my_plurals", "Zero", 2, "Two")
            )
        }

        ProtoResourcesGraphBuilder(temporaryFolder.root.toPath(), table.toPath())
            .buildGraph(model)

        assertThat(model.referencesFor(0x10000)).containsExactly("string2")
    }

    @Test
    fun `find references from external file in compiled XML format, skip ID resources`() {
        val model = ResourceShrinkerModel(NoDebugReporter, false)
        model.addResource(STRING, PACKAGE_NAME, "string1", 1)
        model.addResource(STRING, PACKAGE_NAME, "string_bye", 2)
        model.addResource(ID, PACKAGE_NAME, "menu_item", 0x10001)
        model.addResource(MENU, PACKAGE_NAME, "my_menu", 0x20001)

        val table = createResourceTable {
            it.addType(
                0,
                "string",
                stringEntry(1, "string1", "Hello world"),
                stringEntry(2, "string.bye", "Bye-bye")
            )
            it.addType(
                2,
                "menu",
                xmlFile(1, "my_menu", "res/menu/my_menu.xml")
            )
        }
        xmlElement("menu")
            .addNamespace("android", ANDROID_NS)
            .addChild(
                xmlElement("item")
                    .addAttribute("id", ANDROID_NS, "@id/menu_item", 0x10001)
                    .addAttribute("title", ANDROID_NS, "@string/string1", 1)
            )
            .addChild(
                xmlElement("item")
                    .addAttributeWithRefNameOnly("title", refName = "string/string.bye")
            )
            .writeToFile(temporaryFolder.root.toPath().resolve("res/menu/my_menu.xml"))

        ProtoResourcesGraphBuilder(temporaryFolder.root.toPath().resolve("res"), table.toPath())
            .buildGraph(model)

        assertThat(model.referencesFor(0x20001)).containsExactly("string1", "string_bye")
    }

    @Test
    fun `find references from raw external files`() {
        val model = ResourceShrinkerModel(NoDebugReporter, false)
        model.addResource(DRAWABLE, PACKAGE_NAME, "drawable_1", 1)
        model.addResource(DRAWABLE, PACKAGE_NAME, "drawable_2", 2)
        model.addResource(RAW, PACKAGE_NAME, "html", 0x10001)
        model.addResource(RAW, PACKAGE_NAME, "css", 0x10002)
        model.addResource(RAW, PACKAGE_NAME, "js", 0x10003)
        model.addResource(RAW, PACKAGE_NAME, "unknown", 0x10004)

        val table = createResourceTable {
            it.addType(
                1,
                "raw",
                externalFile(1, "html", "res/raw/html.html"),
                externalFile(2, "css", "res/raw/css.css"),
                externalFile(3, "js", "res/raw/js.js"),
                externalFile(4, "readme", "res/raw/readme.txt")
            )
        }

        writeToFile(
            File(temporaryFolder.root, "res/raw/css.css"),
            """
                .topbanner {
                  /*
                  background: url("file:///android_res/drawable/drawable_1.png") #00D fixed;
                  */
                  background: url("file:///android_res/drawable/drawable_2.png") #00D fixed;
                }
            """.trimIndent()
        )
        writeToFile(
            File(temporaryFolder.root, "res/raw/html.html"),
            """
                <html>
                  <head>
                    <link rel="stylesheet" type="text/css"
                      href="file:///android_res/raw/css.css">
                  </head>
                  <body>
                    <img src="file:///android_res/drawable/drawable_1.png">
                    <!--
                      <img src="file:///android_res/drawable/drawable_2.png">
                    -->
                  </body>
                </html>
            """.trimIndent()
        )
        writeToFile(
            File(temporaryFolder.root, "res/raw/js.js"),
            """
                const func = () => {
                  let a = "Welcome";
                  // a += ' Home';
                  return a + 'to JS';
                }
            """.trimIndent()
        )
        writeToFile(
            File(temporaryFolder.root, "res/raw/readme.txt"),
            """
                This is RAW resources dir, the start point is android_res/raw/html.html.
            """.trimIndent()
        )

        ProtoResourcesGraphBuilder(temporaryFolder.root.toPath().resolve("res"), table.toPath())
            .buildGraph(model)

        assertThat(model.referencesFor(0x10001)).containsExactly("drawable_1", "css")
        assertThat(model.referencesFor(0x10002)).containsExactly("drawable_2")
        assertThat(model.referencesFor(0x10004)).containsExactly("html")
        assertThat(model.strings).containsExactly("Welcome", "to JS")
    }

    fun `find inlined android_res references inside XML resources`() {
        val model = ResourceShrinkerModel(NoDebugReporter, false)
        model.addResource(DRAWABLE, PACKAGE_NAME, "drawable_1", 1)
        model.addResource(DRAWABLE, PACKAGE_NAME, "drawable_2", 2)
        model.addResource(DRAWABLE, PACKAGE_NAME, "drawable_3", 3)
        model.addResource(XML, PACKAGE_NAME, "some_xml", 0x10001)
        model.addResource(MENU, PACKAGE_NAME, "menu_xml", 0x20001)

        val rootPath = temporaryFolder.root.toPath()
        val androidRes =
            xmlElement("root")
                .addAttribute("attrib", "android_res/drawable/drawable_2")
                .addChild(
                    xmlElement("body")
                        .addChild(
                            XmlNode.newBuilder().setText(
                                """
                                Here is original 'android_res/drawable/drawable_1' for
                                'android_res/drawable/drawable_3'.
                            """.trimIndent()
                            )
                        )
                )

        androidRes.writeToFile(rootPath.resolve("res/xml/some_xml.xml"))
        androidRes.writeToFile(rootPath.resolve("res/menu/menu_xml.xml"))

        val table = createResourceTable {
            it.addType(
                1,
                "xml",
                xmlFile(1, "some_xml", "res/xml/some_xml.xml")
            )
            it.addType(
                2,
                "menu",
                xmlFile(1, "menu_xml", "res/xml/menu_xml.xml")
            )
        }

        ProtoResourcesGraphBuilder(rootPath.resolve("res"), table.toPath()).buildGraph(model)

        assertThat(model.referencesFor(0x10001))
            .containsExactly("drawable_1", "drawable_2", "drawable_3")
        assertThat(model.referencesFor(0x20001)).isEmpty()
    }

    @Test
    fun `skip external files which are not found in res directory`() {
        val messages = mutableListOf<String>()
        val model = ResourceShrinkerModel(object : ShrinkerDebugReporter {
            override fun report(f: () -> String, logLevel: LogLevel) {
                messages += f()
            }

            override fun close() = Unit
        }, false)
        model.addResource(DRAWABLE, PACKAGE_NAME, "drawable_1", 1)
        model.addResource(RAW, PACKAGE_NAME, "css2", 0x10001)
        model.addResource(RAW, PACKAGE_NAME, "html", 0x10002)
        model.addResource(RAW, PACKAGE_NAME, "css", 0x10003)

        val table = createResourceTable {
            it.addType(
                1,
                "raw",
                externalFile(1, "css2", "another-dir/css2.css"),
                externalFile(2, "html", "res/raw/html.html"),
                externalFile(3, "css", "res/raw/css.css")
            )
        }
        writeToFile(
            File(temporaryFolder.root, "res/raw/css.css"),
            """
                .topbanner {
                  background: url("file:///android_res/drawable/drawable_1.png") #00D fixed;
                }
            """.trimIndent()
        )
        ProtoResourcesGraphBuilder(temporaryFolder.root.toPath().resolve("res"), table.toPath())
            .buildGraph(model)

        assertThat(model.referencesFor(0x10002)).isEmpty()
        assertThat(model.referencesFor(0x10003)).containsExactly("drawable_1")
        assertThat(messages)
            .containsExactly("File 'res/raw/html.html' can not be processed. Skipping.")
    }

    fun createResourceTable(fn: (Package.Builder) -> Package.Builder): File {
        val file = temporaryFolder.newFile()
        val resourceTable = fn(
            Package.newBuilder()
                .setPackageId(PackageId.newBuilder().setId(0))
                .setPackageName(PACKAGE_NAME)
        ).buildResourceTable()
        Files.write(file.toPath(), resourceTable.toByteArray())
        return file
    }
}

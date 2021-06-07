/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.integration.common.fixture.model

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.relativeToOrNull
import com.android.builder.model.v2.AndroidModel
import com.google.common.truth.Truth
import com.google.gson.JsonElement
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.io.File

internal class SnapshotItemRegistrarTest {

    private val normalizer: FileNormalizer = FakeFileNormalizer(
        mapOf(
            File("/some/important/path") to "IMPORTANT_PATH1",
            File("/some/other/important/path") to "IMPORTANT_PATH2",
        )
    )

    private val notImportantPath = if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
        "C:\\some\\not-important\\path"
    } else {
        "/some/not-important/path"
    }

    @get:Rule
    val expectedException: ExpectedException = ExpectedException.none()

    @Test
    fun `test small object`() {
        val smallObject = SmallObject("a", null)

        val snapshot = snapshot(smallObject) {
            item("property1", it.property1.toValueString(normalizer))
            item("property2", it.property2.toValueString(normalizer))
        }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
               - property1 = "a"
               - property2 = (null)

        """.trimIndent())
    }

    @Test
    fun `test large object with normalizer`() {
        val largeObject = LargeObject(
            "a",
            12,
            File(notImportantPath),
            FakeEnum.ENUM_1,
            1.2,
            true,
            listOf("string1", "string2"),
            listOf(100, 101),
            File("/some/important/path/with/some/leaf"),
            listOf(File("/some/other/important/path"), File(notImportantPath))
        )

        val snapshot = snapshot(largeObject) {
            item("property1", it.property1.toValueString(normalizer))
            item("property2", it.property2.toValueString(normalizer))
            item("property3", it.property3.toValueString(normalizer))
            item("property4", it.property4.toValueString(normalizer))
            item("property5", it.property5.toValueString(normalizer))
            item("property6", it.property6.toValueString(normalizer))
            item("property7", it.property7.toValueString(normalizer))
            item("property8", it.property8.toValueString(normalizer))
            item("property9", it.property9.toValueString(normalizer))
            item("property10", it.property10.toValueString(normalizer))
        }

        Truth.assertThat(snapshot).isEqualTo("""
            > LargeObject:
               - property1  = "a"
               - property2  = 12
               - property3  = $notImportantPath
               - property4  = ENUM_1
               - property5  = 1.2
               - property6  = true
               - property7  = ["string1", "string2"]
               - property8  = [100, 101]
               - property9  = {IMPORTANT_PATH1}/with/some/leaf
               - property10 = [{IMPORTANT_PATH2}/, $notImportantPath]
            < LargeObject

        """.trimIndent())
    }

    @Test
    fun `test valueList`() {
        val smallObject = SmallObject("a", listOf("b", "c"))

        val snapshot = snapshot(smallObject) {
            item("property1", it.property1.toValueString(normalizer))
            valueList("property2", it.property2?.map { it.toNormalizedStrings(normalizer) })
        }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
               - property1 = "a"
               - property2:
                  * "b"
                  * "c"

        """.trimIndent())
    }

    @Test
    fun `test valueList with empty collection`() {
        val smallObject = SmallObject("a", listOf())

        val snapshot = snapshot(smallObject,) {
            item("property1", it.property1.toValueString(normalizer))
            valueList("property2", it.property2?.map { it.toNormalizedStrings(normalizer) })
        }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
               - property1 = "a"
               - property2 = []

        """.trimIndent())
    }

    @Test
    fun `test valueList with null collection`() {
        val smallObject = SmallObject("a", null)

        val snapshot = snapshot(smallObject) {
            item("property1", it.property1.toValueString(normalizer))
            valueList("property2", it.property2?.map { it.toNormalizedStrings(normalizer) })
        }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
               - property1 = "a"
               - property2 = (null)

        """.trimIndent())
    }

    @Test
    fun `test nested size`() {
        // check that large-ish nested object can force the (small) enclosing object to become large
        val smallObject = SmallObject("a", listOf("b", "c", "d", "e", "f", "g", "h"))

        val snapshot = snapshot(smallObject) {
            item("property1", it.property1.toValueString(normalizer))
            valueList("property2", it.property2?.map { it.toNormalizedStrings(normalizer) })
        }

        Truth.assertThat(snapshot).isEqualTo("""
            > SmallObject:
               - property1 = "a"
               - property2:
                  * "b"
                  * "c"
                  * "d"
                  * "e"
                  * "f"
                  * "g"
                  * "h"
            < SmallObject

        """.trimIndent())
    }

    @Test
    fun `test dataObject`() {
        val mainObject = EnclosingObject(
            property1 = SmallObject("a", listOf()),
            property2 = LargeObject(
                "a",
                12,
                File(notImportantPath),
                FakeEnum.ENUM_1,
                1.2,
                true,
                listOf("string1", "string2"),
                listOf(100, 101),
                File("/some/important/path/with/some/leaf"),
                listOf(File("/some/other/important/path"), File(notImportantPath))
            )
        )

        val snapshot = snapshot(mainObject) { enclosingObject ->
            dataObject("property1", enclosingObject.property1) {
                it.item("property1", property1.toValueString(normalizer))
                it.item("property2", property2.toValueString(normalizer))
            }
            dataObject("property2", enclosingObject.property2) {
                it.item("property1", property1.toValueString(normalizer))
                it.item("property2", property2.toValueString(normalizer))
                it.item("property3", property3.toValueString(normalizer))
                it.item("property4", property4.toValueString(normalizer))
                it.item("property5", property5.toValueString(normalizer))
                it.item("property6", property6.toValueString(normalizer))
                it.item("property7", property7.toValueString(normalizer))
                it.item("property8", property8.toValueString(normalizer))
                it.item("property9", property9.toValueString(normalizer))
                it.item("property10", property10.toValueString(normalizer))
            }
        }

        Truth.assertThat(snapshot).isEqualTo("""
            > EnclosingObject:
               - property1:
                  - property1 = "a"
                  - property2 = []
               > property2:
                  - property1  = "a"
                  - property2  = 12
                  - property3  = $notImportantPath
                  - property4  = ENUM_1
                  - property5  = 1.2
                  - property6  = true
                  - property7  = ["string1", "string2"]
                  - property8  = [100, 101]
                  - property9  = {IMPORTANT_PATH1}/with/some/leaf
                  - property10 = [{IMPORTANT_PATH2}/, $notImportantPath]
               < property2
            < EnclosingObject

        """.trimIndent())
    }

    @Test
    fun `test null dataObject`() {
        val mainObject = EnclosingObject(
            property1 = SmallObject("a", listOf()),
            property2 = null
        )

        val snapshot = snapshot(mainObject) { enclosingObject ->
            dataObject("property1", enclosingObject.property1) {
                it.item("property1", property1.toValueString(normalizer))
                it.item("property2", property2.toValueString(normalizer))
            }
            dataObject("property2", enclosingObject.property2) {
                it.item("property1", property1.toValueString(normalizer))
                it.item("property2", property2.toValueString(normalizer))
                it.item("property3", property3.toValueString(normalizer))
                it.item("property4", property4.toValueString(normalizer))
                it.item("property5", property5.toValueString(normalizer))
                it.item("property6", property6.toValueString(normalizer))
                it.item("property7", property7.toValueString(normalizer))
                it.item("property8", property8.toValueString(normalizer))
                it.item("property9", property9.toValueString(normalizer))
                it.item("property10", property10.toValueString(normalizer))
            }
        }

        Truth.assertThat(snapshot).isEqualTo("""
            - EnclosingObject:
               - property1:
                  - property1 = "a"
                  - property2 = []
               - property2 = (null)

        """.trimIndent())
    }

    @Test
    fun `test objectList`() {
        val mainObject = ObjectWithList(listOf(
            SmallObject("a", listOf("b")),
            SmallObject("c", null)
        ))

        val snapshot = snapshot(mainObject) {
            objectList("list", it.list) { items ->
                for (item in items) {
                    dataObject("item: ${item.property1}", item) { registrar ->
                        registrar.item("property1", property1.toValueString(normalizer))
                        registrar.item("property2", property2.toValueString(normalizer))
                    }
                }
            }
        }

        Truth.assertThat(snapshot).isEqualTo("""
            - ObjectWithList:
               - list:
                  - item: a:
                     - property1 = "a"
                     - property2 = ["b"]
                  - item: c:
                     - property1 = "c"
                     - property2 = (null)

        """.trimIndent())
    }

    private fun <ModelT> snapshot(
        model: ModelT,
        action: SnapshotItemRegistrarImpl.(ModelT) -> Unit
    ): String {
        val registrar = SnapshotItemRegistrarImpl(
            model!!::class.java.simpleName,
            SnapshotContainer.ContentType.OBJECT_PROPERTIES
        )
        action(registrar, model)
        val writer = SnapshotItemWriter()
        return writer.write(registrar)
    }
}

class FakeFileNormalizer(private val map: Map<File, String> = mapOf()): FileNormalizer {
    override fun normalize(file: File): String {
        for (entry in map.entries) {
            val result = file.relativeToOrNull(
                entry.key, entry.value
            )
            if (result != null) {
                return result
            }
        }

        return file.absolutePath
    }
    override fun normalize(value: JsonElement): JsonElement = value
}

data class SmallObject(
    val property1: String?,
    val property2: List<String>? = null
): AndroidModel {
    constructor(values: List<String>?): this(null, values)
    constructor(): this(null, null)
}

enum class FakeEnum {
    ENUM_1, ENUM_2;
}

data class LargeObject(
    val property1: String,
    val property2: Int,
    val property3: File,
    val property4: FakeEnum,
    val property5: Double,
    val property6: Boolean,
    val property7: Collection<String>,
    val property8: Collection<Int>,
    val property9: File,
    val property10: Collection<File>,
): AndroidModel

data class EnclosingObject(
    val property1: SmallObject,
    val property2: LargeObject?
): AndroidModel

data class ObjectWithList(
    val list: List<SmallObject>?
)

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
import com.google.common.truth.Truth
import org.junit.Test
import java.io.File

class ModelSnapshotterTest {

    private val normalizer = FakeFileNormalizer(
        mapOf(
            File("/some/important/path") to "IMPORTANT_PATH1",
            File("/some/other/important/path") to "IMPORTANT_PATH2",
        )
    )

    private val referenceNormalizer = FakeFileNormalizer(
        mapOf(
            File("/reference/some/important/path") to "IMPORTANT_PATH1",
            File("/reference/some/other/important/path") to "IMPORTANT_PATH2",
        )
    )

    private val notImportantPath = if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
        "C:\\some\\not-important\\path"
    } else {
        "/some/not-important/path"
    }

    @Test
    fun item() {
        val smallObject = SmallObject("a", listOf("b", "c"))

        val snapshot = snapshot(smallObject) { snapshotWithItem() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
                - property1 = "a"
                - property2 = ["b", "c"]

        """.trimIndent())
    }

    @Test
    fun `item with empty list`() {
        val smallObject = SmallObject("a", listOf())

        val snapshot = snapshot(smallObject) { snapshotWithItem() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
                - property1 = "a"
                - property2 = []

        """.trimIndent())
    }

    @Test
    fun `item with null list`() {
        val smallObject = SmallObject("a", null)

        val snapshot = snapshot(smallObject) { snapshotWithItem() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
                - property1 = "a"
                - property2 = (null)

        """.trimIndent())
    }

    @Test
    fun `item with modifier`() {
        val smallObject = SmallObject("a", listOf("b", "c"))

        val snapshot = snapshot(smallObject) {
            item("property1", SmallObject::property1)
            item("property2", SmallObject::property2) {
                it?.joinToString(separator = "")
            }
        }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
                - property1 = "a"
                - property2 = "bc"

        """.trimIndent())
    }

    @Test
    fun `item vs reference`() {
        val smallObject = SmallObject("a", listOf("a"))
        val referenceObject = SmallObject("b", listOf("b"))

        val snapshot = snapshot(smallObject, referenceObject) { snapshotWithItem() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
                - property1 = "a"
                - property2 = ["a"]

        """.trimIndent())
    }

    @Test
    fun `item vs identical reference`() {
        val smallObject = SmallObject("a", listOf("a"))
        val referenceObject = SmallObject("a", listOf("a"))

        val snapshot = snapshot(smallObject, referenceObject) { snapshotWithItem() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:

        """.trimIndent())
    }

    @Test
    fun `item vs reference with null properties`() {
        val smallObject = SmallObject("a", listOf("a"))
        val referenceObject = SmallObject(null, null)

        val snapshot = snapshot(smallObject, referenceObject) { snapshotWithItem() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
                - property1 = "a"
                - property2 = ["a"]

        """.trimIndent())
    }

    @Test
    fun `item with null properties vs reference`() {
        val smallObject = SmallObject(null, null)
        val referenceObject = SmallObject("a", listOf("a"))

        val snapshot = snapshot(smallObject, referenceObject) { snapshotWithItem() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
                - property1 = (null)
                - property2 = (null)

        """.trimIndent())
    }

    @Test
    fun `item vs reference with modifier`() {
        val smallObject = SmallObject("a", listOf("b", "c"))
        val referenceObject = SmallObject("a", listOf("bc"))

        val snapshot = snapshot(smallObject, referenceObject) {
            item("property1", SmallObject::property1)
            item("property2", SmallObject::property2) {
                it?.joinToString(separator = "")
            }
        }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:

        """.trimIndent())
    }

    @Test
    fun `item vs reference with empty list`() {
        val smallObject = SmallObject("a", listOf("b"))
        val referenceObject = SmallObject("a", listOf())

        val snapshot = snapshot(smallObject, referenceObject) { snapshotWithItem() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
                - property2 = ["b"]

        """.trimIndent())
    }

    @Test
    fun `item with empty list vs reference`() {
        val smallObject = SmallObject("a", listOf())
        val referenceObject = SmallObject("a", listOf("b"))

        val snapshot = snapshot(smallObject, referenceObject) { snapshotWithItem() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
                - property2 = []

        """.trimIndent())
    }

    @Test
    fun `many types + normalizer`() {
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
            listOf(File("/some/other/important/path"), File("/some/other/important/path/with/a/leaf"))
        )

        val snapshot = snapshot(largeObject) {
            snapshotLargeObject()
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
                - property10 = [{IMPORTANT_PATH2}/, {IMPORTANT_PATH2}/with/a/leaf]
            < LargeObject

        """.trimIndent())
    }

    @Test
    fun valueList() {
        val smallObject = SmallObject("a", listOf("b", "c"))

        val snapshot = snapshot(smallObject) { snapshotWithValueList() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
                - property1 = "a"
                - property2:
                   * "b"
                   * "c"

        """.trimIndent())
    }

    @Test
    fun `valueList with empty list`() {
        val smallObject = SmallObject("a", listOf())

        val snapshot = snapshot(smallObject) { snapshotWithValueList() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
                - property1 = "a"
                - property2 = []

        """.trimIndent())
    }

    @Test
    fun `valueList with null list`() {
        val smallObject = SmallObject("a", null)

        val snapshot = snapshot(smallObject) { snapshotWithValueList() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
                - property1 = "a"
                - property2 = (null)

        """.trimIndent())
    }

    @Test
    fun `valueList with formatter`() {
        val smallObject = SmallObject("a", listOf("b", "c"))

        val snapshot = snapshot(smallObject) {
            item("property1", SmallObject::property1)
            valueList("property2", SmallObject::property2, formatAction = { "item($this)" })
        }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
                - property1 = "a"
                - property2:
                   * "item(b)"
                   * "item(c)"

        """.trimIndent())
    }

    @Test
    fun `valueList vs reference`() {
        val smallObject = SmallObject("a", listOf("b", "c"))
        val referenceObject = SmallObject("b", listOf("d"))

        val snapshot = snapshot(smallObject, referenceObject) { snapshotWithValueList() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
                - property1 = "a"
                - property2:
                   * "b"
                   * "c"

        """.trimIndent())
    }

    @Test
    fun `valueList vs identical reference`() {
        val smallObject = SmallObject("a", listOf("b", "c"))
        val referenceObject = SmallObject("a", listOf("b", "c"))

        val snapshot = snapshot(smallObject, referenceObject) { snapshotWithValueList() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:

        """.trimIndent())
    }

    @Test
    fun `valueList vs reference with null properties`() {
        val smallObject = SmallObject("a", listOf("a"))
        val referenceObject = SmallObject(null, null)

        val snapshot = snapshot(smallObject, referenceObject) { snapshotWithValueList() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
                - property1 = "a"
                - property2:
                   * "a"

        """.trimIndent())
    }

    @Test
    fun `valueList with null properties vs reference`() {
        val smallObject = SmallObject(null, null)
        val referenceObject = SmallObject("a", listOf("a"))

        val snapshot = snapshot(smallObject, referenceObject) { snapshotWithValueList() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
                - property1 = (null)
                - property2 = (null)

        """.trimIndent())
    }

    @Test
    fun `valueList vs reference with formatter`() {
        val smallObject = SmallObject("a", listOf("item1"))
        val referenceObject = SmallObject("a", listOf("item2"))

        val snapshot = snapshot(smallObject, referenceObject) {
            item("property1", SmallObject::property1)
            valueList(
                name = "property2",
                propertyAction = SmallObject::property2,
                formatAction = { substring(0, 4) })
        }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:

        """.trimIndent())
    }

    @Test
    fun `valueList vs reference with empty list`() {
        val smallObject = SmallObject("a", listOf("b"))
        val referenceObject = SmallObject("a", listOf())

        val snapshot = snapshot(smallObject, referenceObject) { snapshotWithValueList() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
                - property2:
                   * "b"

        """.trimIndent())
    }

    @Test
    fun `valueList with empty list vs reference`() {
        val smallObject = SmallObject("a", listOf())
        val referenceObject = SmallObject("a", listOf("b"))

        val snapshot = snapshot(smallObject, referenceObject) { snapshotWithValueList() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
                - property2 = []

        """.trimIndent())
    }


    // entry is mostly the same as item (just different formatting) so no need to test all the
    // different possible scenarios
    @Test
    fun entry() {
        val smallObject = SmallObject("a", null)

        val snapshot = snapshot(
            smallObject
        ) {
            entry("property1", SmallObject::property1)
        }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
                - property1 -> "a"

        """.trimIndent())
    }

    @Test
    fun dataObject() {
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
                listOf(File("/some/other/important/path"), File("/some/other/important/path/with/a/leaf"))
            )
        )

        val snapshot = snapshot(mainObject) { snapshotEnclosingObject() }

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
                   - property10 = [{IMPORTANT_PATH2}/, {IMPORTANT_PATH2}/with/a/leaf]
                < property2
            < EnclosingObject

        """.trimIndent())
    }

    @Test
    fun `dataObject with null object`() {
        val mainObject = EnclosingObject(
            property1 = SmallObject("a", listOf()),
            property2 = null
        )

        val snapshot = snapshot(mainObject) { snapshotEnclosingObject() }

        Truth.assertThat(snapshot).isEqualTo("""
            - EnclosingObject:
                - property1:
                   - property1 = "a"
                   - property2 = []
                - property2 = (null)

        """.trimIndent())
    }

    @Test
    fun `dataObject vs reference`() {
        val mainObject = EnclosingObject(
            property1 = SmallObject("a", listOf()),
            property2 = LargeObject(
                "a",
                12,
                File("/some/not-important/path"),
                FakeEnum.ENUM_1,
                1.2,
                true,
                listOf("string1", "string2"),
                listOf(100, 101),
                File("/some/important/path/with/some/leaf"),
                listOf(File("/some/other/important/path"), File("/some/other/important/path/with/a/leaf"))
            )
        )

        // use different paths since the normalizer for the reference object normalize different
        // paths. These means the normalized paths should be identical
        val referenceObject = EnclosingObject(
            property1 = SmallObject("b", listOf()),
            property2 = LargeObject(
                "a",
                13,
                File("/some/not-important/path"),
                FakeEnum.ENUM_2,
                1.3,
                false,
                listOf("string3", "string2"),
                listOf(100, 102),
                File("/reference/some/important/path/with/some/leaf"),
                listOf(File("/reference/some/other/important/path"), File("/reference/some/other/important/path/with/a/leaf"))
            )
        )

        val snapshot = snapshot(mainObject, referenceObject) { snapshotEnclosingObject() }

        Truth.assertThat(snapshot).isEqualTo("""
            > EnclosingObject:
                - property1:
                   - property1 = "a"
                - property2:
                   - property2 = 12
                   - property4 = ENUM_1
                   - property5 = 1.2
                   - property6 = true
                   - property7 = ["string1", "string2"]
                   - property8 = [100, 101]
            < EnclosingObject

        """.trimIndent())
    }

    @Test
    fun convertedObjectList() {
        val mainObject = ObjectWithMap(mapOf(
            "1" to SmallObject("1", listOf("a")),
            "2" to SmallObject("2", listOf("b")),
        ))

        val snapshot = snapshot(mainObject) { snapshotObjectWithMap() }

        Truth.assertThat(snapshot).isEqualTo("""
            - ObjectWithMap:
                - map:
                   - object(1):
                      - property1 = "1"
                      - property2 = ["a"]
                   - object(2):
                      - property1 = "2"
                      - property2 = ["b"]

        """.trimIndent())
    }

    @Test
    fun `convertedObjectList with empty map`() {
        val snapshot = snapshot(ObjectWithMap(mapOf())) { snapshotObjectWithMap() }

        Truth.assertThat(snapshot).isEqualTo("""
            - ObjectWithMap:
                - map = []

        """.trimIndent())
    }

    @Test
    fun `convertedObjectList with null map`() {
        val snapshot = snapshot(ObjectWithMap(null)) { snapshotObjectWithMap() }

        Truth.assertThat(snapshot).isEqualTo("""
            - ObjectWithMap:
                - map = (null)

        """.trimIndent())
    }

    @Test
    fun `convertedObjectList vs identical reference`() {
        val mainObject = ObjectWithMap(mapOf(
            "1" to SmallObject("1", listOf()),
            "2" to SmallObject("2", listOf()),
        ))

        val referenceObject = ObjectWithMap(mapOf(
            "1" to SmallObject("1", listOf()),
            "2" to SmallObject("2", listOf()),
        ))

        val snapshot = snapshot(mainObject, referenceObject) { snapshotObjectWithMap() }

        Truth.assertThat(snapshot).isEqualTo("""
            - ObjectWithMap:

        """.trimIndent())
    }

    @Test
    fun `convertedObjectList vs reference with empty list`() {
        val mainObject = ObjectWithMap(mapOf(
            "1" to SmallObject("1", listOf("a")),
            "2" to SmallObject("2", listOf()),
        ))

        val referenceObject = ObjectWithMap(mapOf(
            "1" to SmallObject("1", listOf()),
            "2" to SmallObject("2", listOf("b")),
        ))

        val snapshot = snapshot(mainObject, referenceObject) { snapshotObjectWithMap() }

        Truth.assertThat(snapshot).isEqualTo("""
            - ObjectWithMap:
                - map:
                   - object(1):
                      - property2 = ["a"]
                   - object(2):
                      - property2 = []

        """.trimIndent())
    }

    @Test
    fun `convertedObjectList vs reference with null list`() {
        val mainObject = ObjectWithMap(mapOf(
            "1" to SmallObject("1", listOf("a")),
            "2" to SmallObject("2", null),
        ))

        val referenceObject = ObjectWithMap(mapOf(
            "1" to SmallObject("1", null),
            "2" to SmallObject("2", listOf("b")),
        ))

        val snapshot = snapshot(mainObject, referenceObject) { snapshotObjectWithMap() }

        Truth.assertThat(snapshot).isEqualTo("""
            - ObjectWithMap:
                - map:
                   - object(1):
                      - property2 = ["a"]
                   - object(2):
                      - property2 = (null)

        """.trimIndent())
    }

    private fun <ModelT> snapshot(
        model: ModelT,
        referenceModel: ModelT? = null,
        action: ModelSnapshotter<ModelT>.() -> Unit
    ): String {
        val registrar = SnapshotItemRegistrarImpl(model!!::class.java.simpleName, mapOf())

        action(ModelSnapshotter(registrar, model, normalizer, referenceModel, referenceNormalizer))

        val writer = SnapshotItemWriter()
        return writer.write(registrar)
    }
}

private fun ModelSnapshotter<SmallObject>.snapshotWithItem() {
    item("property1", SmallObject::property1)
    item("property2", SmallObject::property2)
}

private fun ModelSnapshotter<SmallObject>.snapshotWithValueList() {
    item("property1", SmallObject::property1)
    valueList("property2", SmallObject::property2)
}

private fun ModelSnapshotter<LargeObject>.snapshotLargeObject() {
    item("property1", LargeObject::property1)
    item("property2", LargeObject::property2)
    item("property3", LargeObject::property3)
    item("property4", LargeObject::property4)
    item("property5", LargeObject::property5)
    item("property6", LargeObject::property6)
    item("property7", LargeObject::property7)
    item("property8", LargeObject::property8)
    item("property9", LargeObject::property9)
    item("property10", LargeObject::property10)
}

private fun ModelSnapshotter<EnclosingObject>.snapshotEnclosingObject() {
    dataObject("property1", EnclosingObject::property1) {
        snapshotWithItem()
    }
    dataObject("property2", EnclosingObject::property2) {
        snapshotLargeObject()
    }
}

private fun ModelSnapshotter<ObjectWithMap>.snapshotObjectWithMap() {
    convertedObjectList(
        name = "map",
        propertyAction = { map?.entries },
        nameAction = { "object(${value.property1})" },
        objectAction = { value },
        idAction = { key },
        sortAction = { collection -> collection?.sortedBy { it.key } }
    ) {
        item("property1", SmallObject::property1)
        item("property2", SmallObject::property2)
    }
}

data class ObjectWithMap(
    val map: Map<String, SmallObject>?
)


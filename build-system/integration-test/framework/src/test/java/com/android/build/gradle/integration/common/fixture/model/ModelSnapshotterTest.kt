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

    private val notImportantPath = if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
        "C:\\some\\not-important\\path"
    } else {
        "/some/not-important/path"
    }

    @Test
    fun item() {
        val smallObject = SmallObject("a")

        val snapshot = snapshot(smallObject) { snapshotWithItem() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
               - property1 = "a"

        """.trimIndent())
    }

    @Test
    fun `item with modifier`() {
        val smallObject = SmallObject("a")

        val snapshot = snapshot(smallObject) {
            item("property1", SmallObject::property1) {
                it?.toUpperCase()
            }
        }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
               - property1 = "A"

        """.trimIndent())
    }

    @Test
    fun list() {
        val smallObject = SmallObject(listOf("b", "c"))

        val snapshot = snapshot(smallObject) { snapshotWithList() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
               - property2 = ["b", "c"]

        """.trimIndent())
    }

    @Test
    fun `list with empty list`() {
        val smallObject = SmallObject(listOf())

        val snapshot = snapshot(smallObject) { snapshotWithList() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
               - property2 = []

        """.trimIndent())
    }

    @Test
    fun `list with null list`() {
        val smallObject = SmallObject()

        val snapshot = snapshot(smallObject) { snapshotWithList() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
               - property2 = (null)

        """.trimIndent())
    }

    @Test
    fun `list with format`() {
        val smallObject = SmallObject(listOf("b", "c"))

        val snapshot = snapshot(smallObject) {
            list("property2", SmallObject::property2) { toUpperCase() }
        }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
               - property2 = ["B", "C"]

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
        val smallObject = SmallObject(listOf("b", "c"))

        val snapshot = snapshot(smallObject) { snapshotWithValueList() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
               - property2:
                  * "b"
                  * "c"

        """.trimIndent())
    }

    @Test
    fun `valueList with empty list`() {
        val smallObject = SmallObject(listOf())

        val snapshot = snapshot(smallObject) { snapshotWithValueList() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
               - property2 = []

        """.trimIndent())
    }

    @Test
    fun `valueList with null list`() {
        val smallObject = SmallObject()

        val snapshot = snapshot(smallObject) { snapshotWithValueList() }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
               - property2 = (null)

        """.trimIndent())
    }

    @Test
    fun `valueList with formatter`() {
        val smallObject = SmallObject(listOf("b", "c"))

        val snapshot = snapshot(smallObject) {
            valueList("property2", SmallObject::property2, formatAction = { "item($this)" })
        }

        Truth.assertThat(snapshot).isEqualTo("""
            - SmallObject:
               - property2:
                  * "item(b)"
                  * "item(c)"

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

    private fun <ModelT> snapshot(
        model: ModelT,
        action: ModelSnapshotter<ModelT>.() -> Unit
    ): String {
        val registrar =
                SnapshotItemRegistrarImpl(
                    model!!::class.java.simpleName,
                    SnapshotContainer.ContentType.OBJECT_PROPERTIES
                )

        action(ModelSnapshotter(registrar, model, normalizer, mapOf()))

        val writer = SnapshotItemWriter()
        return writer.write(registrar)
    }
}

private fun ModelSnapshotter<SmallObject>.snapshotSmallObject() {
    item("property1", SmallObject::property1)
    list("property2", SmallObject::property2)
}

private fun ModelSnapshotter<SmallObject>.snapshotWithItem() {
    item("property1", SmallObject::property1)
}

private fun ModelSnapshotter<SmallObject>.snapshotWithList() {
    list("property2", SmallObject::property2)
}

private fun ModelSnapshotter<SmallObject>.snapshotWithValueList() {
    valueList("property2", SmallObject::property2)
}

private fun ModelSnapshotter<LargeObject>.snapshotLargeObject() {
    item("property1", LargeObject::property1)
    item("property2", LargeObject::property2)
    item("property3", LargeObject::property3)
    item("property4", LargeObject::property4)
    item("property5", LargeObject::property5)
    item("property6", LargeObject::property6)
    list("property7", LargeObject::property7)
    list("property8", LargeObject::property8)
    item("property9", LargeObject::property9)
    list("property10", LargeObject::property10)
}

private fun ModelSnapshotter<EnclosingObject>.snapshotEnclosingObject() {
    dataObject("property1", EnclosingObject::property1) {
        snapshotSmallObject()
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
        sortAction = { collection -> collection?.sortedBy { it.key } }
    ) {
        item("property1", SmallObject::property1)
        list("property2", SmallObject::property2)
    }
}

data class ObjectWithMap(
    val map: Map<String, SmallObject>?
)

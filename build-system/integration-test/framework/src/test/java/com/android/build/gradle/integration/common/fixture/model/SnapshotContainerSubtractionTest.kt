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

import com.android.build.gradle.integration.common.fixture.model.SnapshotContainer.ContentType.OBJECT_LIST
import com.android.build.gradle.integration.common.fixture.model.SnapshotContainer.ContentType.OBJECT_PROPERTIES
import com.android.build.gradle.integration.common.fixture.model.SnapshotContainer.ContentType.VALUE_LIST
import com.google.common.truth.Truth.assertThat
import org.junit.Test


internal class SnapshotContainerSubtractionTest {

    @Test
    fun `valueList diff with same content`() {
        assertThat(
            getValueListSubtraction(
                main = listOf(ValueOnlyItem("A")),
                reference = listOf(ValueOnlyItem("A"))
            )
        ).isNull()
    }

    @Test
    fun `valueList diff with new value`() {
        val difference = getValueListSubtraction(
            main = listOf(ValueOnlyItem("A"), ValueOnlyItem("B")),
            reference = listOf(ValueOnlyItem("A"))
        )

        assertThat(difference).isNotNull()
        difference!!
        assertThat(difference.items).containsExactly(ValueOnlyItem("B{ADDED}"))
    }

    @Test
    fun `valueList diff with removed value`() {
        val difference = getValueListSubtraction(
            main = listOf(ValueOnlyItem("A")),
            reference = listOf(ValueOnlyItem("A"), ValueOnlyItem("B"))
        )

        assertThat(difference).isNotNull()
        difference!!
        assertThat(difference.items).containsExactly(ValueOnlyItem("B{REMOVED}"))
    }

    @Test
    fun `valueList diff with all values removed (empty list)`() {
        val difference = getValueListSubtraction(
            main = listOf(),
            reference = listOf(ValueOnlyItem("A"), ValueOnlyItem("B"))
        )

        assertThat(difference).isNotNull()
        difference!!
        assertThat(difference.items).containsExactly(
            ValueOnlyItem("A{REMOVED}"),
            ValueOnlyItem("B{REMOVED}")
        )
    }

    @Test
    fun `valueList diff with all values removed (null)`() {
        val difference = getValueListSubtraction(
            main = null,
            reference = listOf(ValueOnlyItem("A"), ValueOnlyItem("B"))
        )

        assertThat(difference).isNotNull()
        difference!!
        assertThat(difference.items).containsExactly(
            ValueOnlyItem("A{REMOVED}"),
            ValueOnlyItem("B{REMOVED}")
        )
    }

    @Test
    fun `valueList diff with all values added (empty list)`() {
        val difference = getValueListSubtraction(
            main = listOf(ValueOnlyItem("A"), ValueOnlyItem("B")),
            reference = listOf()
        )

        assertThat(difference).isNotNull()
        difference!!
        assertThat(difference.items).containsExactly(
            ValueOnlyItem("A{ADDED}"),
            ValueOnlyItem("B{ADDED}")
        )
    }

    @Test
    fun `valueList diff with all values added (null)`() {
        val difference = getValueListSubtraction(
            main = listOf(ValueOnlyItem("A"), ValueOnlyItem("B")),
            reference = null
        )

        assertThat(difference).isNotNull()
        difference!!
        assertThat(difference.items).containsExactly(
            ValueOnlyItem("A{ADDED}"),
            ValueOnlyItem("B{ADDED}")
        )
    }

    @Test
    fun `object prop diff with same key-value items`() {
        assertThat(
            getObjectPropertiesSubtraction(
                main = listOf(KeyValueItem(name = "foo", value = "A")),
                reference = listOf(KeyValueItem(name = "foo", value = "A"))
            )
        ).isNull()
    }

    @Test
    fun `object prop diff with different key-value`() {
        val difference = getObjectPropertiesSubtraction(
            main = listOf(KeyValueItem(name = "foo", value = "A")),
            reference = listOf(KeyValueItem(name = "foo", value = "B"))
        )
        assertThat(difference).isNotNull()
        difference!!
        assertThat(difference.items).containsExactly(
            KeyValueItem(name = "foo", value = "A")
        )
    }

    @Test
    fun `object prop diff with same nested container key-value`() {
        assertThat(
            getObjectPropertiesSubtraction(
                main = listOf(
                    SnapshotContainerImpl(
                        "foo",
                        listOf(
                            KeyValueItem(name = "bar", value = "A"),
                            KeyValueItem(name = "bar2", value = "B")
                        ),
                        OBJECT_PROPERTIES
                    )
                ),
                reference = listOf(
                    SnapshotContainerImpl(
                        "foo",
                        listOf(
                            KeyValueItem(name = "bar", value = "A"),
                            KeyValueItem(name = "bar2", value = "B")
                        ),
                        OBJECT_PROPERTIES
                    )
                )
            )
        ).isNull()
    }

    @Test
    fun `object prop diff with different nested container key-value`() {
        val difference = getObjectPropertiesSubtraction(
            main = listOf(
                SnapshotContainerImpl(
                    "foo",
                    listOf(
                        KeyValueItem(name = "bar", value = "A"),
                        KeyValueItem(name = "bar2", value = "B")
                    ),
                    OBJECT_PROPERTIES
                )
            ),
            reference = listOf(
                SnapshotContainerImpl(
                    "foo",
                    listOf(
                        KeyValueItem(name = "bar", value = "A"),
                        KeyValueItem(name = "bar2", value = "C")
                    ),
                    OBJECT_PROPERTIES
                )
            )
        )
        assertThat(difference).isNotNull()
        difference!!
        assertThat(difference.items).containsExactly(
            SnapshotContainerImpl(
                "foo",
                listOf(
                    KeyValueItem(name = "bar2", value = "B")
                ),
                OBJECT_PROPERTIES
            )
        )
    }

    @Test
    fun `objectList diff with same content`() {
        assertThat(
            getObjectListSubtraction(
                main = listOf(
                    SnapshotContainerImpl(
                        "foo",
                        listOf(
                            KeyValueItem(name = "bar", value = "A"),
                            KeyValueItem(name = "bar2", value = "B")
                        ),
                        OBJECT_PROPERTIES
                    )
                ),
                reference = listOf(
                    SnapshotContainerImpl(
                        "foo",
                        listOf(
                            KeyValueItem(name = "bar", value = "A"),
                            KeyValueItem(name = "bar2", value = "B")
                        ),
                        OBJECT_PROPERTIES
                    )
                )
            )
        ).isNull()
    }

    @Test
    fun `objectList diff with new value`() {
        val difference = getObjectListSubtraction(
            main = listOf(
                SnapshotContainerImpl(
                    "foo",
                    listOf(
                        KeyValueItem(name = "bar", value = "A"),
                        KeyValueItem(name = "bar2", value = "B")
                    ),
                    OBJECT_PROPERTIES
                ),
                SnapshotContainerImpl(
                    "bar",
                    listOf(
                        KeyValueItem(name = "bar3", value = "A"),
                    ),
                    OBJECT_PROPERTIES
                )
            ),
            reference = listOf(
                SnapshotContainerImpl(
                    "foo",
                    listOf(
                        KeyValueItem(name = "bar", value = "A"),
                        KeyValueItem(name = "bar2", value = "B")
                    ),
                    OBJECT_PROPERTIES
                )
            )
        )

        assertThat(difference).isNotNull()
        difference!!
        assertThat(difference.items).containsExactly(
            SnapshotContainerImpl(
                "bar{ADDED}",
                listOf(
                    KeyValueItem(name = "bar3", value = "A"),
                ),
                OBJECT_PROPERTIES
            )
        )
    }

    @Test
    fun `objectList diff with removed value`() {
        val difference = getObjectListSubtraction(
            main = listOf(
                SnapshotContainerImpl(
                    "foo",
                    listOf(
                        KeyValueItem(name = "bar", value = "A"),
                        KeyValueItem(name = "bar2", value = "B")
                    ),
                    OBJECT_PROPERTIES
                )
            ),
            reference = listOf(
                SnapshotContainerImpl(
                    "foo",
                    listOf(
                        KeyValueItem(name = "bar", value = "A"),
                        KeyValueItem(name = "bar2", value = "B")
                    ),
                    OBJECT_PROPERTIES
                ),
                SnapshotContainerImpl(
                    "bar",
                    listOf(
                        KeyValueItem(name = "bar3", value = "A"),
                    ),
                    OBJECT_PROPERTIES
                )
            )
        )

        assertThat(difference).isNotNull()
        difference!!
        assertThat(difference.items).containsExactly(
            SnapshotContainerImpl("bar{REMOVED}", null, OBJECT_PROPERTIES)
        )
    }

    @Test
    fun `objectList diff with same item but different value`() {
        val difference = getObjectListSubtraction(
            main = listOf(
                SnapshotContainerImpl(
                    "foo",
                    listOf(
                        KeyValueItem(name = "bar", value = "A"),
                        KeyValueItem(name = "bar2", value = "C")
                    ),
                    OBJECT_PROPERTIES
                ),
            ),
            reference = listOf(
                SnapshotContainerImpl(
                    "foo",
                    listOf(
                        KeyValueItem(name = "bar", value = "A"),
                        KeyValueItem(name = "bar2", value = "B")
                    ),
                    OBJECT_PROPERTIES
                )
            )
        )

        assertThat(difference).isNotNull()
        difference!!
        assertThat(difference.items).containsExactly(
            SnapshotContainerImpl(
                "foo",
                listOf(
                    KeyValueItem(name = "bar2", value = "C")
                ),
                OBJECT_PROPERTIES
            )
        )
    }

    @Test
    fun `objectList diff with all values removed (empty list)`() {
        val difference = getObjectListSubtraction(
            main = listOf(),
            reference = listOf(
                SnapshotContainerImpl(
                    "foo",
                    listOf(
                        KeyValueItem(name = "bar", value = "A"),
                        KeyValueItem(name = "bar2", value = "B")
                    ),
                    OBJECT_PROPERTIES
                )
            )
        )

        assertThat(difference).isNotNull()
        difference!!
        assertThat(difference.items).containsExactly(
            SnapshotContainerImpl("foo{REMOVED}", null, OBJECT_PROPERTIES)
        )
    }

    @Test
    fun `objectList diff with all values removed (null)`() {
        val difference = getObjectListSubtraction(
            main = null,
            reference = listOf(
                SnapshotContainerImpl(
                    "foo",
                    listOf(
                        KeyValueItem(name = "bar", value = "A"),
                        KeyValueItem(name = "bar2", value = "B")
                    ),
                    OBJECT_PROPERTIES
                )
            )
        )

        assertThat(difference).isNotNull()
        difference!!
        assertThat(difference.items).containsExactly(
            SnapshotContainerImpl("foo{REMOVED}", null, OBJECT_PROPERTIES)
        )
    }

    @Test
    fun `objectList diff with all values added (empty list)`() {
        val difference = getObjectListSubtraction(
            main = listOf(
                SnapshotContainerImpl(
                    "foo",
                    listOf(
                        KeyValueItem(name = "bar", value = "A"),
                        KeyValueItem(name = "bar2", value = "B")
                    ),
                    OBJECT_PROPERTIES
                )
            ),
            reference = listOf()
        )

        assertThat(difference).isNotNull()
        difference!!
        assertThat(difference.items).containsExactly(
            SnapshotContainerImpl(
                "foo{ADDED}", listOf(
                    KeyValueItem(name = "bar", value = "A"),
                    KeyValueItem(name = "bar2", value = "B")
                ), OBJECT_PROPERTIES
            )
        )
    }

    @Test
    fun `objectList diff with all values added (null)`() {
        val difference = getObjectListSubtraction(
            main = listOf(
                SnapshotContainerImpl(
                    "foo",
                    listOf(
                        KeyValueItem(name = "bar", value = "A"),
                        KeyValueItem(name = "bar2", value = "B")
                    ),
                    OBJECT_PROPERTIES
                )
            ),
            reference = null
        )

        assertThat(difference).isNotNull()
        difference!!
        assertThat(difference.items).containsExactly(
            SnapshotContainerImpl(
                "foo{ADDED}", listOf(
                    KeyValueItem(name = "bar", value = "A"),
                    KeyValueItem(name = "bar2", value = "B")
                ), OBJECT_PROPERTIES
            )
        )
    }

    private fun getValueListSubtraction(
        main: List<SnapshotItem>?,
        reference: List<SnapshotItem>?
    ): SnapshotContainer? = getSubtraction(main, reference, VALUE_LIST)

    private fun getObjectPropertiesSubtraction(
        main: List<SnapshotItem>?,
        reference: List<SnapshotItem>?
    ): SnapshotContainer? = getSubtraction(main, reference, OBJECT_PROPERTIES)

    private fun getObjectListSubtraction(
        main: List<SnapshotItem>?,
        reference: List<SnapshotItem>?
    ): SnapshotContainer? = getSubtraction(main, reference, OBJECT_LIST)

    private fun getSubtraction(
        main: List<SnapshotItem>?,
        reference: List<SnapshotItem>?,
        type: SnapshotContainer.ContentType
    ) = SnapshotContainerImpl("name", main, type)
        .subtract(SnapshotContainerImpl("name", reference, type)
    )
}

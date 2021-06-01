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

interface SnapshotItem

interface NamedSnapshotItem: SnapshotItem {
    val name: String

    val keyLen: Int
        get() = name.length
}

interface SnapshotContainer: NamedSnapshotItem {
    enum class ContentType(val isList: Boolean) {
        OBJECT_PROPERTIES(isList = false),
        VALUE_LIST(isList = true),
        OBJECT_LIST(isList = true)
    }

    val items: List<SnapshotItem>?
    val contentType: ContentType

    // key spacing is useful to 2 categories of items. Both will output something like
    //     name = value
    // They are:
    // - KeyValueItem
    // - SnapshotContainer where the content is a list and the list is null or empty.
    // The latter is because instead of displaying
    //     <container-name>:
    //       - item1 ...
    // they display
    //     <container-name> = [] (or (null))
    fun computeKeySpacing(): Int =
            items
                ?.filterIsInstance(NamedSnapshotItem::class.java)
                ?.filter { it !is SnapshotContainer || it.items.isNullOrEmpty() }
                ?.map { it.keyLen }
                ?.max()
                ?.let { it + 1 }
                    ?: 0
}

data class KeyValueItem(
    override val name: String,
    val value: String?,
    val separator: String = "="
): NamedSnapshotItem

data class ValueOnlyItem(
    val value: String?
): SnapshotItem

/**
 * Object to receive and hold the items representing a dumped model.
 */
interface SnapshotItemRegistrar: SnapshotContainer {

    /**
     * Adds a property and its value
     */
    fun item(key: String, value: String?)

    /**
     * Represents a list value
     */
    fun value(value: String?)

    /**
     * Adds a list of simple items, each displayed on a new line
     *
     * @param name the name of the list
     * @param list the list. Should be sorted already
     * @param formatAction an optional action to format the item
     */
    fun <T> valueList(
        name: String,
        list: Collection<T>?,
        formatAction: (T.() -> String)? = null
    )

    /**
     * Adds a list of complex items, each displayed on a new line
     *
     * @param name the name of the list
     * @param list the list. Should be sorted already
     * @param formatAction an optional action to format the item
     */
    fun <T> objectList(
        name: String,
        list: Collection<T>?,
        action: SnapshotItemRegistrar.(Collection<T>) -> Unit
    )

    /**
     * Adds the content of an object.
     *
     * The object parameters is not used directly. It is passed back to the lambda which must
     * be used to write each property into the new [SnapshotItemRegistrar]
     *
     * @param name the name of the list
     * @param obj the object
     * @param action a callback to add the items to the builder
     */
    fun <T> dataObject(
        name: String,
        obj: T?,
        action: T.(SnapshotItemRegistrar) -> Unit)
}

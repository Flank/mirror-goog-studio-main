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

import com.android.build.gradle.integration.common.fixture.model.SnapshotItemWriter.Companion.NULL_STRING

class SnapshotItemRegistrarImpl(
    override val name: String,
    override val contentType: SnapshotContainer.ContentType,
): SnapshotItemRegistrar {

    private var _items: MutableList<SnapshotItem>? = null

    override val items: List<SnapshotItem>?
        get() = _items

    /**
     * Adds a property and its value
     */
    override fun item(key: String, value: String?) {
        check(contentType == SnapshotContainer.ContentType.OBJECT_PROPERTIES)
        addItem(KeyValueItem(key, value))
    }

    /**
     * Represents a list value
     */
    override fun value(value: String?) {
        check(contentType == SnapshotContainer.ContentType.VALUE_LIST)
        addItem(ValueOnlyItem(value))
    }

    /**
     * Adds a list of simple items, each displayed on a new line
     *
     * @param name the name of the list
     * @param list the list. Should be sorted already
     * @param formatAction an optional action to format the item
     */
    override fun <T> valueList(
        name: String,
        list: Collection<T>?,
        formatAction: (T.() -> String)?
    ) {
        check(contentType == SnapshotContainer.ContentType.OBJECT_PROPERTIES || contentType == SnapshotContainer.ContentType.OBJECT_LIST)

        // create the holder always, and add it.
        val subHolder = SnapshotItemRegistrarImpl(name, SnapshotContainer.ContentType.VALUE_LIST)
        addItem(subHolder)

        if (list != null) {
            if (list.isEmpty()) {
                // ensure the list is initialized
                subHolder.initItems()
            } else {
                for (item in list) {
                    subHolder.value(formatAction?.invoke(item) ?: item?.toString() ?: NULL_STRING)
                }
            }
        }
    }

    /**
     * Adds a list of simple items, each displayed on a new line
     *
     * @param name the name of the list
     * @param list the list. Should be sorted already
     * @param action a callback to add the items to the builder.
     */
    override fun <T> objectList(
        name: String,
        list: Collection<T>?,
        action: SnapshotItemRegistrar.(Collection<T>) -> Unit
    ) {
        check(contentType == SnapshotContainer.ContentType.OBJECT_PROPERTIES || contentType == SnapshotContainer.ContentType.OBJECT_LIST)

        // create the holder always, and add it.
        val subHolder = SnapshotItemRegistrarImpl(name, SnapshotContainer.ContentType.OBJECT_LIST)
        addItem(subHolder)

        if (list != null) {
            if (list.isEmpty()) {
                // ensure the list is initialized
                subHolder.initItems()
            } else {
                subHolder.action(list)
            }
        }
    }

    /**
     * Adds the content of an object.
     *
     * The object parameters is not used directly. It is passed back to the lambda which must
     * be used to write each property into the new [SnapshotItemRegistrar]
     *
     * @param name the name of the list
     * @param objectType the type of the object.
     * @param obj the object
     * @param action a callback to add the items to the builder
     */
    override fun <T> dataObject(
        name: String,
        obj: T?,
        action: T.(SnapshotItemRegistrar) -> Unit
    ) {
        check(contentType == SnapshotContainer.ContentType.OBJECT_PROPERTIES || contentType == SnapshotContainer.ContentType.OBJECT_LIST)

        // create the holder always, and add it.
        val subHolder = SnapshotItemRegistrarImpl(name, SnapshotContainer.ContentType.OBJECT_PROPERTIES)
        addItem(subHolder)

        obj?.action(subHolder)
    }

    private fun addItem(item: SnapshotItem) {
        val itemList = _items ?: mutableListOf<SnapshotItem>().also { _items = it }
        itemList.add(item)
    }

    private fun initItems() {
        if (_items == null) {
            _items = mutableListOf()
        }
    }
}

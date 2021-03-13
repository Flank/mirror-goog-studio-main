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
    private val buildIdMap: Map<String, String>
): SnapshotItemRegistrar {

    private val _items = mutableListOf<SnapshotItem>()

    val items: List<SnapshotItem>
        get() = _items

    fun isNotEmpty(): Boolean = _items.isNotEmpty()

    fun computeKeySpacing(): Int =
            _items.filterIsInstance<KeyValueItem>().map { it.keyLen }.max()?.let { it + 1 } ?: 0

    /**
     * Adds a property and its value
     */
    override fun item(key: String, value: String?) {
        _items.add(KeyValueItem(key, value))
    }

    /**
     * Represents a list value
     */
    override fun value(value: String?) {
        _items.add(ValueOnlyItem(value))
    }

    /**
     * Adds a map entry (key + value)
     */
    override fun entry(key: String, value: String?) {
        _items.add(KeyValueItem(key, value, separator = "->"))
    }

    /**
     * Handles an Artifact address.
     *
     * Addresses for subprojects are made up of <build-ID>@@<projectpath>::<variant> and build IDs
     * are the location of the root project of the build.
     */
    override fun artifactAddress(key: String, value: String) {
        if (value.contains("@@")) {
            val buildId =  value.substringBefore("@@")
            val projectPathAndVariant = value.substringAfter("@@")

            val newID = buildIdMap[buildId] ?: buildId

            item(key, "{${newID}}@@${projectPathAndVariant}")
        } else {
            item(key, value)
        }
    }

    /**
     * Handles an Build ID
     *
     * build IDs are the location of the root project of the build.
     */
    override fun buildId(key: String, value: String?) {
        item(key, value?.let { buildIdMap[it]?.let { "{$it}" } } ?: value)
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
        if (list.isNullOrEmpty()) {
            _items.add(KeyValueItem(name, list?.toString() ?: NULL_STRING))
        } else {
            val subHolder = SnapshotItemRegistrarImpl(
                buildIdMap = buildIdMap,
                name = name
            )

            for (item in list) {
                subHolder.value(formatAction?.invoke(item) ?: item?.toString() ?: NULL_STRING)
            }

            // validate that items were added to the new builder.
            if (subHolder.isNotEmpty()) {
                _items.add(subHolder)
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
        if (list.isNullOrEmpty()) {
            _items.add(KeyValueItem(name, list?.toString() ?: NULL_STRING))
        } else {
            SnapshotItemRegistrarImpl(
                buildIdMap = buildIdMap,
                name = name
            ).also {
                it.action(list)

                // validate that items were added to the new builder.
                if (it.isNotEmpty()) {
                    _items.add(it)
                }
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
        action: T.(SnapshotItemRegistrar) -> Unit) {
        if (obj == null) {
            _items.add(KeyValueItem(name, NULL_STRING))
        } else {
            SnapshotItemRegistrarImpl(
                buildIdMap = buildIdMap,
                name = name
            ).also {
                obj.action(it)

                if (it.isNotEmpty()) {
                    _items.add(it)
                }
            }
        }
    }
}

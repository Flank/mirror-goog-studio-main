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

/**
 * Takes 2 list of snapshot items and remove the identical content.
 * This also changes the names of [NamedSnapshotItem], and possibly some values to indicate
 * whether they are ADDED/REMOVED.
 */
fun SnapshotContainer.subtract(reference: SnapshotContainer): SnapshotContainer? {
    // There are three different scenarios for the content of a SnapshotContainer:
    // 1. the container contains a mix of NamedSnapshotItem. These are the properties of an
    //    "object", and the ype is OBJECT_PROPERTIES.
    //    In this case, both containers will contain the same number of entries, with the same name,
    //    but possibly different content.
    //    There are 2 types of Named items:
    //    1a. KeyValueItem. This are single value (String) items. Just compare the value.
    //    1b. SnapshotContainer. Recursively call into the same method again (unless #2)
    //    Everytime a value is identical, return null, of if all the entries are identical, return
    //    null as well.
    // 2. The container contains only ValueOnlyItems. ContentType is VALUE_LIST.
    //    The items are only of type ValueOnlyItem, with no named. In this case we compare each
    //    value and find ADDED/REMOVED ones.
    // 3. The container contains a list of named "object". ContentType is OBJECT_LIST.
    //    Unlike #1 where the properties are identical in both containers, here the content will
    //    differ as objects can be added or removed from the list. We will compare using the name
    //    of each containers to identify matching objects since there is no directly values to use
    //    (unlike #2)

    return when (this.contentType) {
        SnapshotContainer.ContentType.OBJECT_PROPERTIES -> {
            subtractObjectProperties(reference)
        }
        SnapshotContainer.ContentType.VALUE_LIST -> {
            subtractValueList(reference)
        }
        SnapshotContainer.ContentType.OBJECT_LIST -> {
            subtractObjectList(reference)
        }
    }
}

/**
 * Subtract a current container of [NamedSnapshotItem] against another one.
 *
 * This is for the case where the content is [SnapshotContainer.ContentType.OBJECT_PROPERTIES].
 *
 * The 2 containers must have the same named entries. This compares them and return a container
 * with the entries having different values, or null if they are all the same.
 */
private fun SnapshotContainer.subtractObjectProperties(
    reference: SnapshotContainer
): SnapshotContainer? {
    val name = this.name

    return if (this.items == null && reference.items == null) {
        null
    } else if (items == null) {
        // the container should be considered removed.
        SnapshotContainerImpl("$name{REMOVED}", null, SnapshotContainer.ContentType.OBJECT_PROPERTIES)
    } else if (reference.items == null) {
        // the whole container is added.
        this.cloneWithNewName("{ADDED}") as SnapshotContainer
    } else {
        // compare the items.
        val originalItems = this.items!!.map { it as NamedSnapshotItem }
        val referenceItems = reference.items!!.map { it as NamedSnapshotItem }

        if (originalItems.isNotEmpty() && referenceItems.isNotEmpty() && originalItems.size != referenceItems.size) {
            throw RuntimeException("main and reference items for $name have different number of properties")
        }

        val referenceMap = referenceItems.associateBy { it.name }

        // at this level the 2 list must contains the same entries. Just compare their content.
        val newList = mutableListOf<SnapshotItem>()

        for (item in originalItems) {
            val referenceItem =
                    referenceMap[item.name]
                            ?: throw RuntimeException("Failed to find reference item for ${item.name}")

            // compute the difference and add it to the list if it's not-null
            item.computeDifference(referenceItem)?.let { newList.add(it) }
        }

        // if the new list is empty this means the objects where the same, so just return null
        if (newList.isEmpty()) {
            null
        } else {
            SnapshotContainerImpl(name, newList, SnapshotContainer.ContentType.OBJECT_PROPERTIES)
        }
    }
}

/**
 * Subtract a named item with another named item and returns a new item if they are different, or
 * null if they are the same.
 *
 * In this method, only this or reference can be null but not both.
 */
private fun NamedSnapshotItem.computeDifference(reference: NamedSnapshotItem): SnapshotItem? =
        when (this) {
            is KeyValueItem -> {
                // if the values are different, return the new one.
                if (this.value == (reference as KeyValueItem).value) { null } else { this }
            }
            is SnapshotContainer -> {
                this.subtract(reference as SnapshotContainer)
            }
            else -> throw RuntimeException("Unsupported NamedSnapshotItem: ${this.javaClass}")
        }

private fun SnapshotContainer.subtractObjectList(reference: SnapshotContainer): SnapshotContainer? {
    val originalItems = this.items?.map { it as SnapshotContainer }
    val referenceItems = reference.items?.map { it as SnapshotContainer }

    // if both list are null, it's the same, return null
    if (originalItems == null && referenceItems == null) {
        return null
    }

    val name = this.name

    return if (referenceItems == null) {
        // originalItems cannot be null here (Kotlin sadly can't figure that out.)
        originalItems!!
        // in this case this is all new values. Add all the items but append {ADDED} at to the name.
        SnapshotContainerImpl(
            name,
            originalItems.map { it.cloneWithNewName("{ADDED}") },
            SnapshotContainer.ContentType.OBJECT_LIST
        )
    } else {
        if (originalItems != null) {
            // both are non null, compare the two lists
            val referenceMap = referenceItems.associateBy { it.name }
            val originalMap = originalItems.associateBy { it.name }

            // at this level the 2 list must contains the same entries. Just compare their content.
            val newList = mutableListOf<SnapshotItem>()

            // first go through the list of existing items, and compare to the reference by
            // search for a match by name
            for (item in originalItems) {
                val referenceItem = referenceMap[item.name]
                if (referenceItem == null) {
                    // new object.
                    newList.add(item.cloneWithNewName("{ADDED}"))
                } else {
                    // compute the difference and add it to the list if it's not-null
                    item.computeDifference(referenceItem)?.let { newList.add(it) }
                }
            }

            // now go through the reference items searching for deleted items
            for (item in referenceItems) {
                // if the object are present in both, they were already processed above.
                if (!originalMap.containsKey(item.name)) {
                    newList.add(item.cloneWithNewName("{REMOVED}", stripContent = true))
                }
            }

            // if the list is empty, the 2 content are identical, return null
            if (newList.isEmpty()) {
                null
            } else {
                SnapshotContainerImpl(name, newList, SnapshotContainer.ContentType.OBJECT_LIST)
            }
        } else {
            // special handling for the case where the new list is null and the old list is empty.
            // In this case since we cannot list the REMOVED items (there aren't any) and we don't
            // want to display an empty list, we create a container with null inside.
            if (referenceItems.isEmpty()) {
                SnapshotContainerImpl(name, null, SnapshotContainer.ContentType.OBJECT_LIST)
            } else {

                // all values are removed. Take items from reference and add {REMOVED} at the end.
                SnapshotContainerImpl(
                    name,
                    referenceItems.map { it.cloneWithNewName("{REMOVED}", stripContent = true) },
                    SnapshotContainer.ContentType.OBJECT_LIST
                )
            }
        }
    }
}

private fun SnapshotItem.cloneWithNewName(
    suffix: String,
    stripContent: Boolean = false
): SnapshotItem =
        when (this) {
            is KeyValueItem -> KeyValueItem(name + suffix, if (stripContent) null else value)
            is SnapshotContainer -> SnapshotContainerImpl(
                name + suffix,
                if (stripContent) null else items,
                contentType
            )
            else -> throw RuntimeException("Unsupported SnapshotItem: $javaClass")
        }

/**
 * Subtract a current container of [ValueOnlyItem] against another one.
 *
 * Returns a new container with ADDED/REMOVED values, or null if the values are the same.
 */
private fun SnapshotContainer.subtractValueList(reference: SnapshotContainer): SnapshotContainer? {
    val originalItems = this.items?.map { it as ValueOnlyItem }
    val referenceItems = reference.items?.map { it as ValueOnlyItem }

    // if both list are null, it's the same, return null
    if (originalItems == null && referenceItems == null) {
        return null
    }

    val name = this.name

    return if (referenceItems == null) {
        // originalItems cannot be null here (Kotlin sadly can't figure that out.)
        originalItems!!
        // in this case this is all new values. Add all the items but append {ADDED} at the end.
        SnapshotContainerImpl(
            name,
            originalItems.map { ValueOnlyItem("${it.value}{ADDED}") },
            SnapshotContainer.ContentType.VALUE_LIST
        )
    } else {
        if (originalItems != null) {
            // both are non null, compare the two lists
            val newList = mutableListOf<ValueOnlyItem>()
            for (item in originalItems) {
                if (!referenceItems.contains(item)) {
                    newList.add(ValueOnlyItem("${item.value}{ADDED}"))
                }
            }
            for (item in referenceItems) {
                if (!originalItems.contains(item)) {
                    newList.add(ValueOnlyItem("${item.value}{REMOVED}"))
                }
            }

            // if the list is empty, the 2 content are identical, return null
            if (newList.isEmpty()) {
                null
            } else {
                SnapshotContainerImpl(name, newList, SnapshotContainer.ContentType.VALUE_LIST)
            }
        } else {
            // all values are removed. Take items from reference and add {REMOVED} at the end.
            // special handling for the case where the new list is null and the old list is empty.
            // In this case since we cannot list the REMOVED items (there aren't any) and we don't
            // want to display an empty list, we create a container with null inside.
            if (referenceItems.isEmpty()) {
                SnapshotContainerImpl(name, null, SnapshotContainer.ContentType.VALUE_LIST)
            } else {
                SnapshotContainerImpl(
                    name,
                    referenceItems.map { ValueOnlyItem("${it.value}{REMOVED}") },
                    SnapshotContainer.ContentType.VALUE_LIST
                )
            }
        }
    }
}

internal data class SnapshotContainerImpl(
    override val name: String,
    override val items: List<SnapshotItem>?,
    override val contentType: SnapshotContainer.ContentType
) : SnapshotContainer

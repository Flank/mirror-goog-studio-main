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
import java.io.File


/**
 * Main entry point of the snapshot feature.
 *
 * if a reference model is provided, only the properties that are different are snapshotted.
 *
 * Each instance only handles the direct properties of the provided model. For nested modeks,
 * new instances are created with their own matching reference model (if applicable)
 *
 * @param modelName the name of the root model
 * @param normalizer the file normalizer for the model to snapshot
 * @param model the model to snapshot
 * @param referenceModel an optional reference model to compare to.
 * @param referenceNormalizer an optional normalizer for the reference model
 * @param includedBuilds an optional list of included builds.
 * @param action the action configuring a [ModelSnapshotter]
 *
 * @return the strings with the dumped model
 */
internal fun <T> snapshotModel(
    modelName: String,
    normalizer: FileNormalizer,
    model: T,
    referenceModel: T? = null,
    referenceNormalizer: FileNormalizer? = null,
    includedBuilds: List<String>? = null,
    action: ModelSnapshotter<T>.() -> Unit
): String {

    val map = includedBuilds
        ?.mapIndexed { index, str -> str to "BUILD_$index" }
        ?.toMap()
            ?: mapOf()

    val registrar = SnapshotItemRegistrarImpl(modelName, map)

    action(ModelSnapshotter(registrar, model, normalizer, referenceModel, referenceNormalizer))

    val writer = SnapshotItemWriter()
    return writer.write(registrar)
}

/**
 * Class providing method to snapshot the content of a model.
 *
 * This allows dumping basic property, list, etc.. but also mode complex objects.
 *
 * This class is used only for the current top level object. Each new nested object
 * will use its own instance.
 */
class ModelSnapshotter<ModelT>(
    private val registrar: SnapshotItemRegistrar,
    private val model: ModelT,
    private val normalizer: FileNormalizer,
    private val referenceModel: ModelT? = null,
    private val referenceNormalizer: FileNormalizer? = null
) {
    fun <PropertyT> item(
        name: String,
        propertyAction: ModelT.() -> PropertyT?,
        modifyAction: (PropertyT?) -> Any? = { it }
    ) {
        basicProperty(propertyAction, modifyAction) {
            registrar.item(name, it)
        }
    }

    fun entry(name: String, propertyAction: ModelT.() -> Any?) {
        basicProperty(propertyAction) {
            registrar.entry(name, it)
        }
    }

    fun value(propertyAction: ModelT.() -> Any?) {
        basicProperty(propertyAction) {
            registrar.value(it)
        }
    }

    fun artifactAddress(
        name: String,
        propertyAction: ModelT.() -> String,
    ) {
        val value = propertyAction(model)

        if (referenceModel == null || value != propertyAction(referenceModel)) {
            registrar.artifactAddress(name, value)
        }
    }

    fun buildId(
        name: String,
        propertyAction: ModelT.() -> String?,
    ) {
        val value = propertyAction(model)

        if (referenceModel == null || value != propertyAction(referenceModel)) {
            registrar.buildId(name, value)
        }
    }

    fun <PropertyT> dataObject(
        name: String,
        propertyAction: (ModelT) -> PropertyT?,
        action: ModelSnapshotter<PropertyT>.() -> Unit
    ) {
        val referenceObject = referenceModel?.let { propertyAction(it) }
        val valueObject = propertyAction(model)

        // first compare if both object are null, in which case we just skip
        // everything. This is to bypass the registrar's dataObject shortcut in case of null
        // value.
        // Otherwise, we'll let the registrar get filled and if it's empty, it'll get skipped at
        // the end.
        if (referenceModel == null || !(valueObject == null && referenceObject == null)) {
            registrar.dataObject(name, valueObject) {
                action(
                    ModelSnapshotter(
                        registrar = it,
                        model = this,
                        normalizer = normalizer,
                        referenceModel = referenceObject,
                        referenceNormalizer = referenceNormalizer,
                    )
                )
            }
        }
    }

    private fun <PropertyT> Collection<PropertyT>?.format(formatAction: (PropertyT.() -> String)?): Collection<Any?>? =
            formatAction?.let { this?.map(it)} ?: this

    /**
     * Displays a list on multiple lines
     *
     * If the list content is different from the referenceModel, the whole list is displayed
     */
    fun <PropertyT> valueList(
        name: String,
        propertyAction: ModelT.() -> Collection<PropertyT>?,
        formatAction: (PropertyT.() -> String)? = null,
        sortAction: (Collection<PropertyT>?) -> Collection<PropertyT>? = { it }
    ) {
        val list =
                sortAction(propertyAction(model))
                    .format(formatAction)
                    ?.map { it.toNormalizedStrings(normalizer) }

        if (referenceModel == null ||
            list.differentThan(
                sortAction(propertyAction(referenceModel))
                    .format(formatAction)
                    ?.map { it.toNormalizedStrings(referenceNormalizer!!) })) {
            registrar.valueList(name, list)
        }
    }

    fun <PropertyT> objectList(
        name: String,
        propertyAction: ModelT.() -> Collection<PropertyT>?,
        nameAction: PropertyT.() -> String = { toString() },
        idAction: PropertyT.() -> String,
        sortAction: (Collection<PropertyT>?) -> Collection<PropertyT>? = { it },
        action: ModelSnapshotter<PropertyT>.() -> Unit
    ) {
        fun findMatch(list: Collection<PropertyT>, id: String): PropertyT? {
            for (item in list) {
                if (idAction(item) == id) {
                    return item
                }
            }

            return null
        }

        val referenceObject = referenceModel?.let { sortAction(propertyAction(it)) }
        val theObject = sortAction(propertyAction(model))

        // first compare if both object are null, or both are empty, in which case we just skip
        // everything. This is to bypass the registrar's dataObject shortcut in case of null or
        // empty values.
        // Otherwise, we'll let the registrar get filled and if it's empty, it'll get skipped at
        // the end.
        if (referenceModel == null || !bothCollectionsAreNullOrEmpty(referenceObject, theObject)) {
            registrar.objectList(name, theObject) { itemList ->
                for (item in itemList) {
                    // get a matching item from the reference objectList
                    val id = idAction(item)
                    val referenceItem = referenceObject?.let { findMatch(it, id) }
                    dataObject(nameAction(item), item) { itemHolder ->
                        action(
                            ModelSnapshotter(
                                itemHolder,
                                this,
                                normalizer,
                                referenceItem,
                                referenceNormalizer
                            )
                        )
                    }
                }
            }
        }
    }

    fun <PropertyT, ConvertedPropertyT> convertedObjectList(
        name: String,
        propertyAction: ModelT.() -> Collection<PropertyT>?,
        nameAction: PropertyT.() -> String = { toString() },
        objectAction: PropertyT.() -> ConvertedPropertyT?,
        idAction: PropertyT.() -> String,
        sortAction: (Collection<PropertyT>?) -> Collection<PropertyT>? = { it },
        action: ModelSnapshotter<ConvertedPropertyT>.() -> Unit
    ) {
        fun findMatch(list: Collection<PropertyT>, id: String): PropertyT? {
            for (item in list) {
                if (idAction(item) == id) {
                    return item
                }
            }

            return null
        }

        val referenceObject = referenceModel?.let { sortAction(propertyAction(it)) }
        val theObject = sortAction(propertyAction(model))

        // first compare if both object are null, or both are empty, in which case we just skip
        // everything. This is to bypass the registrar's dataObject shortcut in case of null or
        // empty values.
        // Otherwise, we'll let the registrar get filled and if it's empty, it'll get skipped at
        // the end.
        if (referenceModel == null || !bothCollectionsAreNullOrEmpty(referenceObject, theObject)) {
            registrar.objectList(name, theObject) { itemList ->
                for (item in itemList) {
                    // get a matching item from the reference objectList
                    val id = idAction(item)
                    val referenceItem =
                            referenceObject?.let { findMatch(it, id)?.let { objectAction(it) } }
                    dataObject(nameAction(item), objectAction(item)) { itemHolder ->
                        action(
                            ModelSnapshotter(
                                itemHolder,
                                this,
                                normalizer,
                                referenceItem,
                                referenceNormalizer
                            )
                        )
                    }
                }
            }
        }
    }

    private fun <PropertyT> basicProperty(
        propertyAction: ModelT.() -> PropertyT?,
        modifyAction: (PropertyT?) -> Any? = { it },
        action: (String?) -> Unit
    ) {
        val value = modifyAction(propertyAction(model)).toValueString(normalizer)

        // if toValueString is called on the reference object, then the normalizer is guaranteed to be non-null
        if (referenceModel == null || value.differentThan(
                modifyAction(propertyAction(referenceModel)).toValueString(referenceNormalizer!!)
            )
        ) {
            action(value)
        }
    }
}

private fun Any?.differentThan(otherValue: Any?): Boolean {
    if (this == null && otherValue == null) {
        return false
    } else if (this != null && otherValue != null) {
        return !equals(otherValue)
    }

    return true
}

private fun <T> bothCollectionsAreNullOrEmpty(
    collection1: Collection<T>?,
    collection2: Collection<T>?
): Boolean {
    if (collection1 == null && collection2 == null) {
        return true
    }

    if (collection1 != null && collection2 != null) {
        return collection1.isEmpty() && collection2.isEmpty()
    }

    return false
}

/**
 * Converts a value into a String depending on its type (null, File, String, Collection, Any)
 */
internal fun Any?.toValueString(normalizer: FileNormalizer): String {
    fun Collection<*>.toValueString(normalizer: FileNormalizer): String {
        return this.map { it.toValueString(normalizer) }.toString()
    }

    return when (this) {
        null -> NULL_STRING
        is File -> normalizer.normalize(this)
        is Collection<*> -> toValueString(normalizer)
        is String -> "\"$this\""
        is Enum<*> -> this.name
        else -> toString()
    }
}

/**
 * Normalize an object, recursively if a collection.
 */
internal fun Any?.toNormalizedStrings(normalizer: FileNormalizer): Any = when (this) {
    null -> NULL_STRING
    is File -> normalizer.normalize(this)
    is Collection<*> -> map { it.toNormalizedStrings(normalizer) }
    is String -> "\"$this\""
    is Enum<*> -> name
    else -> toString()
}

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

import com.android.build.gradle.integration.common.fixture.ModelBuilderV2
import com.android.build.gradle.integration.common.fixture.ModelContainerV2
import com.android.build.gradle.integration.common.fixture.model.SnapshotItemWriter.Companion.NULL_STRING
import com.android.build.gradle.internal.ide.dependencies.LOCAL_AAR_GROUPID
import java.io.File


/**
 * Main entry point of the snapshot feature.
 *
 * if a reference model is provided, only the properties that are different are snapshotted.
 *
 * Each instance only handles the direct properties of the provided model. For nested models,
 * new instances are created with their own matching reference model (if applicable)
 *
 * @param modelName the name of the root model
 * @param modelAction an action to get the model from a [ModelBuilderV2.FetchResult]
 * @param project the main [ModelBuilderV2.FetchResult]
 * @param referenceProject and optional reference [ModelBuilderV2.FetchResult]
 * @param action the action configuring a [ModelSnapshotter]
 *
 * @return the strings with the dumped model
 */
internal fun <ModelT> snapshotModel(
    modelName: String,
    modelAction: ModelBuilderV2.FetchResult<ModelContainerV2>.() -> ModelT,
    project: ModelBuilderV2.FetchResult<ModelContainerV2>,
    referenceProject: ModelBuilderV2.FetchResult<ModelContainerV2>? = null,
    action: ModelSnapshotter<ModelT>.() -> Unit
): String {

    val projectSnapshotContainer = getSnapshotContainer(
        modelName,
        modelAction,
        project,
        action
    )

    val finalContainer = if (referenceProject != null) {
        projectSnapshotContainer.subtract(
            getSnapshotContainer(
                modelName,
                modelAction,
                referenceProject,
                action
            )
        )
    } else {
        projectSnapshotContainer
    }

    return if (finalContainer != null) {
        val writer = SnapshotItemWriter()
        writer.write(finalContainer)
    } else {
        ""
    }
}

private fun <ModelT> getSnapshotContainer(
    modelName: String,
    modelAction: ModelBuilderV2.FetchResult<ModelContainerV2>.() -> ModelT,
    project: ModelBuilderV2.FetchResult<ModelContainerV2>,
    action: ModelSnapshotter<ModelT>.() -> Unit
): SnapshotContainer {

    val includedBuilds = project.container.infoMaps.keys.map { it.rootDir.absolutePath }.sorted()

    val buildIdMap = includedBuilds
        .mapIndexed { index, str -> str to "BUILD_$index" }
        .toMap()

    val registrar =
            SnapshotItemRegistrarImpl(modelName, SnapshotContainer.ContentType.OBJECT_PROPERTIES)
    action(ModelSnapshotter(registrar, modelAction(project), project.normalizer, buildIdMap))

    return registrar
}

internal fun <ModelT> checkEmptyDelta(
    modelName: String,
    modelAction: ModelBuilderV2.FetchResult<ModelContainerV2>.() -> ModelT,
    project: ModelBuilderV2.FetchResult<ModelContainerV2>,
    referenceProject: ModelBuilderV2.FetchResult<ModelContainerV2>,
    action: ModelSnapshotter<ModelT>.() -> Unit,
    failureAction: (SnapshotContainer) -> Unit
) {

    val projectSnapshotContainer = getSnapshotContainer(
        modelName,
        modelAction,
        project,
        action
    )

    val referenceSnapshotContainer = getSnapshotContainer(
        modelName,
        modelAction,
        referenceProject,
        action
    )

    val diffSnapshotContainer = projectSnapshotContainer.subtract(referenceSnapshotContainer)

    if (diffSnapshotContainer != null && !diffSnapshotContainer.items.isNullOrEmpty()) {
        failureAction(diffSnapshotContainer)
    }
}

/**
 * Class providing method to snapshot the content of a model.
 *
 * This allows dumping basic property, list, etc.. but also mode complex objects.
 *
 * The instance is used only for the provided [model] object. Each new nested object
 * will use its own instance.
 */
class ModelSnapshotter<ModelT>(
    private val registrar: SnapshotItemRegistrar,
    private val model: ModelT,
    private val normalizer: FileNormalizer,
    private val buildIdMap: Map<String, String>
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

    fun value(propertyAction: ModelT.() -> Any?) {
        basicProperty(propertyAction) {
            registrar.value(it)
        }
    }

    fun artifactAddress(
        name: String,
        propertyAction: ModelT.() -> String,
    ) {
        registrar.item(name, normalizeArtifactAddress(propertyAction(model)))
    }

    internal fun normalizeArtifactAddress(address: String): String {
        // normalize the value if it contains a local jar path,
        val value = if (address.startsWith("$LOCAL_AAR_GROUPID:")) {
            // extract the path. The format is __local_aars__:PATH:unspecified@jar
            val path = File(address.subSequence(15, address.length - 16).toString())

            // reformat the address with the normalized path
            "$LOCAL_AAR_GROUPID:${path.toNormalizedStrings(normalizer)}:unspecified@jar"
        } else {
            address
        }

        // normalize the value if it contains a buildID
        return if (value.contains("@@")) {
            val buildId =  value.substringBefore("@@")
            val projectPathAndVariant = value.substringAfter("@@")

            val newID = buildIdMap[buildId] ?: buildId

            "{${newID}}@@${projectPathAndVariant}"

        } else {
            value
        }
    }

    fun buildId(
        name: String,
        propertyAction: ModelT.() -> String?,
    ) {
        val value = propertyAction(model)
        val buildId = value?.let { buildIdMap[it]?.let { "{$it}" } } ?: value
        registrar.item(name, buildId)
    }

    fun <PropertyT> list(
        name: String,
        propertyAction: (ModelT) -> Collection<PropertyT>?,
        sorted: Boolean = true,
        formatAction: (PropertyT.() -> String)? = null
    ) {
        val valueObject =
                propertyAction(model)
                    ?.format(formatAction)
                    ?.map { it.toValueString(normalizer) }
                    ?.let { if (sorted) it.sorted() else it }

        registrar.item(name, valueObject?.toString() ?: NULL_STRING)
    }

    fun <PropertyT> dataObject(
        name: String,
        propertyAction: (ModelT) -> PropertyT?,
        action: ModelSnapshotter<PropertyT>.() -> Unit
    ) {
        registrar.dataObject(name, propertyAction(model)) {
            action(
                ModelSnapshotter(
                    registrar = it,
                    model = this,
                    normalizer = normalizer,
                    buildIdMap = buildIdMap
                )
            )
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
        registrar.valueList(
            name,
            sortAction(propertyAction(model))
                .format(formatAction)
                ?.map { it.toNormalizedStrings(normalizer) })
    }

    fun <PropertyT> objectList(
        name: String,
        propertyAction: ModelT.() -> Collection<PropertyT>?,
        nameAction: PropertyT.(ModelSnapshotter<ModelT>) -> String,
        sortAction: (Collection<PropertyT>?) -> Collection<PropertyT>? = { it },
        action: ModelSnapshotter<PropertyT>.() -> Unit
    ) {
        val objects = sortAction(propertyAction(model))

        registrar.objectList(name, objects) { itemList ->
            for (item in itemList) {
                // no reference, just output the object
                dataObject(nameAction(item, this@ModelSnapshotter), item) { itemHolder ->
                    action(
                        ModelSnapshotter(
                            registrar = itemHolder,
                            model = this,
                            normalizer = normalizer,
                            buildIdMap = buildIdMap
                        )
                    )
                }
            }
        }
    }

    fun <PropertyT, ConvertedPropertyT> convertedObjectList(
        name: String,
        propertyAction: ModelT.() -> Collection<PropertyT>?,
        nameAction: PropertyT.(ModelSnapshotter<ModelT>) -> String,
        objectAction: PropertyT.() -> ConvertedPropertyT?,
        sortAction: (Collection<PropertyT>?) -> Collection<PropertyT>? = { it },
        action: ModelSnapshotter<ConvertedPropertyT>.() -> Unit
    ) {
        val objects = sortAction(propertyAction(model))

        registrar.objectList(name, objects) { itemList ->
            for (item in itemList) {
                dataObject(nameAction(item, this@ModelSnapshotter), objectAction(item)) { itemHolder ->
                    action(
                        ModelSnapshotter(
                            registrar = itemHolder,
                            model = this,
                            normalizer = normalizer,
                            buildIdMap = buildIdMap
                        )
                    )
                }
            }
        }
    }

    private fun <PropertyT> basicProperty(
        propertyAction: ModelT.() -> PropertyT?,
        modifyAction: (PropertyT?) -> Any? = { it },
        action: (String?) -> Unit
    ) {
        val property = propertyAction(model)
        if (property is Collection<*>) {
            throw RuntimeException("Do not call item/entry/value with collections. Use list instead")
        }

        val modifiedProperty = modifyAction(property)
        if (modifiedProperty is Collection<*>) {
            throw RuntimeException("Do not use a modifyAction that returns a Collection. Use List instead")
        }

        action(modifiedProperty.toValueString(normalizer))
    }
}

/**
 * Converts a value into a single String depending on its type (null, File, String, Collection, Any)
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
 *
 * In case of a collection, still return a collection.
 */
internal fun Any?.toNormalizedStrings(normalizer: FileNormalizer): Any = when (this) {
    null -> NULL_STRING
    is File -> normalizer.normalize(this)
    is Collection<*> -> map { it.toNormalizedStrings(normalizer) }
    is String -> "\"$this\""
    is Enum<*> -> name
    else -> toString()
}


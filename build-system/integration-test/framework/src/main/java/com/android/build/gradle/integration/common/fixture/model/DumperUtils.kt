/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.builder.model.v2.AndroidModel
import com.google.common.truth.Truth
import com.google.gson.JsonElement
import java.io.File

// Sets of classes and functions to provide a little DSL facilitating
// dumping a model into a String.

interface FileNormalizer {
    fun normalize(file: File): String

    /**
     * Normalizes any strings that match the build environment in the given [JsonElement] so that
     * the returned result is invariant across build.
     *
     * Note that this method replace all matched strings and it could return non-sense result if
     * some well-known paths are too common.
     */
    fun normalize(value: JsonElement): JsonElement
}

/**
 * Main entry point of the dump functions.
 *
 * @param name of the main block
 * @param action the action configuring a [DumpBuilder]
 *
 * @return the strings with the dumped model
 */
internal fun <T> dump(
    theClass: Class<T>,
    normalizer: FileNormalizer,
    includedBuilds: List<String>? = null,
    action: DumpBuilder.() -> Unit
): String {

    val map = mutableMapOf<String, String>()
    if (includedBuilds != null) {
        var index = 1
        for (includedBuild in includedBuilds) {
            map[includedBuild] = "BUILD_${index++}"
        }
    }

    val rootBuilder = DumpBuilder(map)

    val name = theClass.simpleName
    rootBuilder.startObject(name)
    val actionBuilder = rootBuilder.builder()
    action(actionBuilder)
    rootBuilder.endObject(name)

    // check the properties have all been written.
    actionBuilder.checkBuilderItemAgainst(theClass)

    val sb = StringBuilder()
    rootBuilder.write(sb, normalizer)
    return sb.toString()
}

typealias BuildIdMap = Map<String, String>

/**
 * an object that can be written
 */
abstract class Writeable(protected val indent: Int) {
    abstract fun write(
        sb: StringBuilder,
        normalizer: FileNormalizer,
        spacing: Int = 0,
        prefix: Char = '-'
    )
}

interface PropertyEntry {
    val name: String
}

/**
 * A writeable Key/Value pair.
 *
 * This is written as
 * (prefix)key(separator)value
 *
 * The value is formatted depending on the type (null, string, other)
 */
class KeyValuePair(
    indent: Int,
    override val name: String,
    private val value: Any?,
    private val separator: String = "="
): Writeable(indent), PropertyEntry {

    val keyLen: Int
        get() = name.length

    override fun write(sb: StringBuilder, normalizer: FileNormalizer, spacing: Int, prefix: Char) {
        if (indent > 0) for (i in 0..indent) sb.append(' ')

        sb.append(prefix).append(' ').append(name)

        if (spacing > 0) {
            val spaceLen = spacing - name.length
            for (i in 0..spaceLen) sb.append(' ')

        } else {
            sb.append(' ')
        }

        sb.append(separator).append(' ').append(value.toValueString(normalizer)).append("\n")
    }
}

/**
 * Converts a value into a String depending on its type (null, File, String, Any)
 */
private fun Any?.toValueString(normalizer: FileNormalizer): String = when (this) {
    null -> "(null)"
    is File -> normalizer.normalize(this)
    is Collection<*> -> this.toValueStringList(normalizer)
    is String -> "\"$this\""
    else -> toString()
}

private fun Collection<*>.toValueStringList(normalizer: FileNormalizer): String {
    return this.map { it.toValueString(normalizer) }.toString()
}

/**
 * A Writeable Header
 */
class StartObjectHeader(
    indent: Int,
    override val name: String
): Writeable(indent), PropertyEntry {
    override fun write(sb: StringBuilder, normalizer: FileNormalizer, spacing: Int, prefix: Char) {
        if (indent > 0) for (i in 0..indent) sb.append(' ')

        sb.append("> ").append(name).append(":\n")
    }
}

/**
 * A Writeable Header
 */
class EndObjectHeader(
    indent: Int,
    private val name: String
): Writeable(indent) {
    override fun write(sb: StringBuilder, normalizer: FileNormalizer, spacing: Int, prefix: Char) {
        if (indent > 0) for (i in 0..indent) sb.append(' ')

        sb.append("< ").append(name).append("\n")
    }
}

/**
 * A writeable key only.
 */
class KeyOnly(
    indent: Int,
    override val name: String
): Writeable(indent), PropertyEntry {
    override fun write(sb: StringBuilder, normalizer: FileNormalizer, spacing: Int, prefix: Char) {
        if (indent > 0) for (i in 0..indent) sb.append(' ')

        sb.append(prefix).append(' ').append(name).append(":\n")
    }
}

/**
 * A writeable Value only.
 */
class ValueOnly(
    indent: Int,
    private val value: Any?
): Writeable(indent) {
    override fun write(sb: StringBuilder, normalizer: FileNormalizer, spacing: Int, prefix: Char) {
        if (indent > 0) for (i in 0..indent) sb.append(' ')

        sb.append(prefix).append(' ').append(value.toValueString(normalizer)).append("\n")
    }
}


const val INDENT_STEP = 3

/**
 * A dump builder.
 *
 * this can accumulate [Writeable] items (which includes sub-dump builders)
 */
class DumpBuilder(
    private val buildIdMap: BuildIdMap,
    indent: Int = 0,
    private val prefix: Char = '-'): Writeable(indent) {
    private val items = mutableListOf<Writeable>()

    /**
     * Adds a property and its value
     */
    fun item(key: String, value: Any?) {
        items.add(KeyValuePair(indent, key, value))
    }

    /**
     * Represents a list value
     */
    fun value(value: Any?) {
        items.add(ValueOnly(indent, value))
    }

    /**
     * Adds a map entry (key + value)
     */
    fun entry(key: String, value: Any?) {
        items.add(KeyValuePair(indent, key, value, separator = "->"))
    }

    internal fun startObject(name: String) {
        items.add(StartObjectHeader(indent, name))
    }

    internal fun endObject(name: String) {
        items.add(EndObjectHeader(indent, name))
    }

    internal fun builder() = DumpBuilder(
        buildIdMap = buildIdMap,
        indent = indent + INDENT_STEP
    ).also {
        items.add(it)
    }

    /**
     * Handles an Artifact address.
     *
     * Addresses for subprojects are made up of <build-ID>@@<projectpath>::<variant> and build IDs
     * are the location of the root project of the build.
     */
    fun artifactAddress(key: String, value: String) {
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
    fun buildId(key: String, value: String?) {
        item(key, value?.let { buildIdMap[it]?.let { "{$it}" } } ?: value)
    }

    /**
     * Adds a list of complex items, each displayed on a new lie
     *
     * @param name the name of the list
     * @param list the list. Should be sorted already
     * @param action a callback to add the items to the builder.
     */
    fun <T> multiLineList(
        name: String,
        list: Collection<T>?,
        action: DumpBuilder.(T) -> Unit
    ) {
        if (list.isNullOrEmpty()) {
            items.add(KeyValuePair(indent, name, list))
        } else {
            startObject(name)
            val newBuilder = DumpBuilder(
                buildIdMap = buildIdMap,
                indent = indent + INDENT_STEP,
                prefix = '*')
            items.add(newBuilder)
            newBuilder.apply {
                for (item in list) {
                    action(newBuilder, item)
                }
            }
            // validate that items were added to the new builder.
            // since empty list go through a different code paths, this can detect
            // the action not doing the right thing
            if (newBuilder.items.isEmpty()) {
                throw RuntimeException("Builder for list items (list: $name) is empty but list isn't. Do filtering before calling multiLineList, and remember to call item/value/entry in the lambda")
            }
            endObject(name)
        }
    }

    /**
     * Adds a list of simple items.
     *
     * The items are written in line with the name. It the content is complex
     * (nested objects for instance), consider using [multiLineList]
     *
     * @param name the name of the list
     * @param list the list. Should be sorted already
     */
    fun <T> list(
        name: String,
        list: Collection<T?>?
    ) {
        items.add(KeyValuePair(indent, name, list))
    }

    /**
     * Adds the content of a struct/small object. Should be used for small objects with only a few
     * properties, and not containing other objects
     *
     * For larger one, use [largeObject]
     *
     * @param name the name of the list
     * @param obj the object
     * @param action a callback to add the items to the builder
     */
    fun <T: AndroidModel> struct(name: String, obj: T?, action: DumpBuilder.(T) -> Unit) {
        if (obj == null) {
            items.add(KeyValuePair(indent, name, obj))
        } else {
            items.add(KeyOnly(indent, name))
            val newBuilder = DumpBuilder(
                buildIdMap = buildIdMap,
                indent = indent + INDENT_STEP,
                prefix = '*')
            items.add(newBuilder)
            action(newBuilder, obj)

            // check the properties have all been written.
            newBuilder.checkBuilderItemAgainst(obj.javaClass)
        }
    }

    /**
     * Adds the content of a large object, with many properties or with nested objects.
     *
     * This adds a footer after the properties to help visualize the end of the object
     *
     * @param name the name of the list
     * @param obj the object
     * @param action a callback to add the items to the builder
     */
    fun <T: AndroidModel> largeObject(name: String, obj: T?, action: DumpBuilder.(T) -> Unit) {
        if (obj == null) {
            items.add(KeyValuePair(indent, name, obj))
        } else {
            startObject(name)
            val newBuilder = DumpBuilder(
                buildIdMap = buildIdMap,
                indent = indent + INDENT_STEP
            )
            items.add(newBuilder)
            action(newBuilder, obj)
            endObject(name)

            // check the properties have all been written.
            newBuilder.checkBuilderItemAgainst(obj.javaClass)
        }
    }

    override fun write(sb: StringBuilder, normalizer: FileNormalizer, spacing: Int, prefix: Char) {
        val keySpacing = items.filterIsInstance<KeyValuePair>().map { it.keyLen }.max() ?: 0

        for (item in items) {
            item.write(sb, normalizer, keySpacing, this.prefix)
        }
    }

    internal fun checkBuilderItemAgainst(clazz: Class<*>) {
        // find the super interface that implements AndroidModel
        val modelInterface = clazz.interfaces.firstOrNull { extendsAndroidModel(it) }
            ?: clazz

        Truth.assertWithMessage("Properties for $modelInterface")
            .that(items.filterIsInstance<PropertyEntry>().map { it.name })
            .containsExactlyElementsIn(clazz.gatherProperties())
    }
}

private fun extendsAndroidModel(clazz: Class<*>): Boolean = if (clazz == AndroidModel::class.java) {
    true
} else {
    clazz.interfaces.any { extendsAndroidModel(it) }
}

internal fun Class<*>.gatherProperties(): Set<String> {
    return this.methods
        .map { it.name }
        .filter {
            (it.startsWith("get") || it.startsWith("is"))
                    && it != "getProxyClass"
                    && it != "isProxyClass"
                    && it != "getInvocationHandler"
                    && it!= "getClass" }
        .map {
            if (it.startsWith("get")) {
                // strip "get" prefix and reduce fist char to lower case
                it[3].toLowerCase() + it.substring(4)
            } else {
                it
            }
        }
        .toSet()
}

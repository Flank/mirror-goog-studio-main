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

package com.android.build.gradle.internal.res.shrinker.util

import com.android.aapt.Resources
import com.android.aapt.Resources.Attribute.FormatFlags
import com.android.aapt.Resources.Entry
import com.android.aapt.Resources.FileReference
import com.android.aapt.Resources.Item

internal fun Resources.Package.Builder.buildResourceTable() =
    Resources.ResourceTable.newBuilder().addPackage(this).build()

internal fun Resources.Package.Builder.addType(id: Int, name: String, vararg entry: Entry?) =
    this.addType(
        Resources.Type.newBuilder()
            .setTypeId(Resources.TypeId.newBuilder().setId(id))
            .setName(name)
            .addAllEntry(entry.toList().filterNotNull())
            .build()
    )

internal fun Resources.ResourceTable.containsString(string: String): Boolean =
  this.packageList.any {
    it.typeList.any {
      it.entryList.any {
        it.configValueList.any {
          // Ignore any difference in types of entry, just ensure the string is not in the table.
          it.value.toString().contains(string)
        }
      }
    }
  }


internal fun attrEntry(id: Int, name: String, format: FormatFlags): Entry =
    Entry.newBuilder()
        .setEntryId(Resources.EntryId.newBuilder().setId(id))
        .setName(name)
        .addConfigValue(
            Resources.ConfigValue.newBuilder()
                .setValue(
                    Resources.Value.newBuilder()
                        .setCompoundValue(
                            Resources.CompoundValue.newBuilder()
                                .setAttr(
                                    Resources.Attribute.newBuilder()
                                        .setFormatFlags(format.number)
                                        .setMinInt(Integer.MIN_VALUE)
                                        .setMinInt(Integer.MAX_VALUE)
                                )
                        )
                )
        )
        .build()

internal fun styleEntry(
    id: Int,
    name: String,
    parentRefId: Int? = null,
    parentRefName: String = ""
): Entry {
    val style = Resources.Style.newBuilder()
    parentRefId?.let {
        style.setParent(
            Resources.Reference.newBuilder()
                .setType(Resources.Reference.Type.REFERENCE)
                .setName(parentRefName)
                .setId(it)
        )
    }
    return Entry.newBuilder()
        .setEntryId(Resources.EntryId.newBuilder().setId(id))
        .setName(name)
        .addConfigValue(
            Resources.ConfigValue.newBuilder()
                .setValue(
                    Resources.Value.newBuilder()
                        .setCompoundValue(
                            Resources.CompoundValue.newBuilder().setStyle(style)
                        )
                )
        )
        .build()
}

internal fun dimenEntry(id: Int, name: String, value: Int): Entry =
    Entry.newBuilder()
        .setEntryId(Resources.EntryId.newBuilder().setId(id))
        .setName(name)
        .addConfigValue(
            Resources.ConfigValue.newBuilder()
                .setValue(
                    Resources.Value.newBuilder()
                        .setItem(
                            Item.newBuilder()
                                .setPrim(
                                    Resources.Primitive.newBuilder().setDimensionValue(value)
                                )
                        )
                )
        )
        .build()

internal fun stringEntry(
    id: Int,
    name: String,
    value: String? = null,
    refId: Long? = null,
    refName: String? = null
): Entry =
    Entry.newBuilder()
        .setEntryId(Resources.EntryId.newBuilder().setId(id))
        .setName(name)
        .addConfigValue(
            Resources.ConfigValue.newBuilder()
                .setValue(
                    Resources.Value.newBuilder().setItem(createItem(value, refId?.toInt(), refName))
                )
        )
        .build()

private fun createItem(value: String? = null, refId: Int? = null, refName: String? = null): Item =
    when {
        value != null ->
            Item.newBuilder().setStr(
                Resources.String.newBuilder().setValue(value)
            )
        refId != null ->
            Item.newBuilder().setRef(
                Resources.Reference.newBuilder()
                    .setId(refId)
                    .setName(refName ?: "")
            )
        refName != null ->
            Item.newBuilder().setRef(
                Resources.Reference.newBuilder()
                    .setName(refName)
            )
        else ->
            Item.newBuilder()
    }.build()

internal fun arrayEntry(
    id: Int,
    name: String,
    inlinedValues: List<String>,
    refValues: List<Int>
): Entry {
    val inlinedElements = inlinedValues.map {
        Resources.Array.Element.newBuilder()
            .setItem(createItem(value = it))
            .build()
    }
    val refElements = refValues.map {
        Resources.Array.Element.newBuilder()
            .setItem(createItem(refId = it))
            .build()
    }
    return Entry.newBuilder()
        .setEntryId(Resources.EntryId.newBuilder().setId(id))
        .setName(name)
        .addConfigValue(
            Resources.ConfigValue.newBuilder()
                .setValue(
                    Resources.Value.newBuilder()
                        .setCompoundValue(
                            Resources.CompoundValue.newBuilder()
                                .setArray(
                                    Resources.Array.newBuilder()
                                        .addAllElement(inlinedElements)
                                        .addAllElement(refElements)
                                )
                        )
                )
        )
        .build()
}

internal fun pluralsEntry(
    id: Int,
    name: String,
    zeroInlined: String,
    oneRefId: Int,
    twoInlined: String
) =
    Entry.newBuilder()
        .setEntryId(Resources.EntryId.newBuilder().setId(id))
        .setName(name)
        .addConfigValue(
            Resources.ConfigValue.newBuilder()
                .setValue(
                    Resources.Value.newBuilder()
                        .setCompoundValue(
                            Resources.CompoundValue.newBuilder()
                                .setPlural(
                                    Resources.Plural.newBuilder()
                                        .addEntry(
                                            Resources.Plural.Entry.newBuilder()
                                                .setArity(Resources.Plural.Arity.ZERO)
                                                .setItem(createItem(value = zeroInlined))
                                        )
                                        .addEntry(
                                            Resources.Plural.Entry.newBuilder()
                                                .setArity(Resources.Plural.Arity.ONE)
                                                .setItem(createItem(refId = oneRefId))
                                        )
                                        .addEntry(
                                            Resources.Plural.Entry.newBuilder()
                                                .setArity(Resources.Plural.Arity.TWO)
                                                .setItem(createItem(value = twoInlined))
                                        )
                                )
                        )
                )
        )
        .build()

internal fun noValueEntry(id: Int, name: String): Entry =
    Entry.newBuilder()
        .setEntryId(Resources.EntryId.newBuilder().setId(id))
        .setName(name)
        .build()

internal fun idEntry(id: Int, name: String): Entry =
    Entry.newBuilder()
        .setEntryId(Resources.EntryId.newBuilder().setId(id))
        .setName(name)
        .addConfigValue(
            Resources.ConfigValue.newBuilder()
                .setValue(
                    Resources.Value.newBuilder()
                        .setItem(
                            Item.newBuilder().setId(Resources.Id.getDefaultInstance())
                        )
                )
        )
        .build()

internal fun xmlFile(id: Int, name: String, path: String): Entry =
    fileEntry(
        id,
        name,
        listOf(path),
        FileReference.Type.PROTO_XML
    )

internal fun externalFile(id: Int, name: String, vararg paths: String): Entry =
    fileEntry(
        id,
        name,
        paths.toList(),
        FileReference.Type.UNKNOWN
    )

internal fun fileEntry(
    id: Int,
    name: String,
    paths: List<String>,
    type: FileReference.Type = FileReference.Type.UNKNOWN
): Entry =
    Entry.newBuilder()
        .setEntryId(Resources.EntryId.newBuilder().setId(id))
        .setName(name)
        .addAllConfigValue(
            paths.map { path ->
                Resources.ConfigValue.newBuilder()
                    .setValue(
                        Resources.Value.newBuilder()
                            .setItem(
                                Item.newBuilder()
                                    .setFile(
                                        FileReference.newBuilder()
                                            .setPath(path)
                                            .setType(type)
                                    )
                            )
                    )
                    .build()
            }
        )
        .build()

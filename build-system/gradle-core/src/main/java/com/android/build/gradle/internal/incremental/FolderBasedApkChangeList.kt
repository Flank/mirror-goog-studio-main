/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.incremental

import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.google.gson.JsonParser
import com.google.gson.stream.JsonWriter
import java.io.Reader
import java.io.Writer

class FolderBasedApkChangeList(val changes: Iterable<String>, val deletions: Iterable<String>) {

    companion object {

        const val CHANGE_LIST_FN = "__adt_change_list__.json"

        @JvmStatic
        fun write(source: FolderBasedApkCreator, writer: Writer) {

            val jsonWriter = JsonWriter(writer)
            jsonWriter.beginObject()
            if (source.changedItems.size > 0) {
                jsonWriter.name("changed")
                    .value(Joiner.on(",").join(source.changedItems))
            }
            if (source.deletedItems.size > 0) {
                jsonWriter.name("deleted")
                    .value(Joiner.on(",").join(source.deletedItems))
            }
            jsonWriter.endObject()
        }

        @JvmStatic
        fun read(reader: Reader): FolderBasedApkChangeList {
            val jsonParser = JsonParser()
            val element = jsonParser.parse(reader)
            val changed = element.asJsonObject.get("changed")
            val changedItems = if (changed != null) {
                Splitter.on(",").split(changed.asString)
            } else listOf()

            val deleted = element.asJsonObject.get("deleted")
            val deletedItems = if (deleted != null) {
                Splitter.on(",").split(deleted.asString)
            } else listOf()
            return FolderBasedApkChangeList(
                changedItems,
                deletedItems
            )
        }
    }
}

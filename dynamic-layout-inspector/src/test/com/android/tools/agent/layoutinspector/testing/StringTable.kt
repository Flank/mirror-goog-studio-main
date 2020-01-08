/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.agent.layoutinspector.testing

import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Resource
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.StringEntry

class StringTable(strings: List<StringEntry>) {
    private val table = strings.associateBy({ it.id }, { it.str })

    operator fun get(id: Int): String = table[id].orEmpty()

    operator fun get(resource: Resource?): ResourceEntry? {
        if (resource == null) {
            return null
        }
        val type = table[resource.type] ?: return null
        val namespace = table[resource.namespace] ?: return null
        val name = table[resource.name] ?: return null
        return ResourceEntry(type, namespace, name)
    }
}

data class ResourceEntry(val type: String, val namespace: String, val name: String)

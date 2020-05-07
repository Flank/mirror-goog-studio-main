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

package androidx.ui.tooling

import androidx.compose.SlotTable
import androidx.ui.unit.IntPxBounds

/**
 * During testing this is used instead of the version in androidx-ui-tooling, since that library
 * contains only stubbed out classes.
 */
sealed class Group(
    val key: Any?,
    val box: IntPxBounds,
    val data: Collection<Any?>,
    val children: Collection<Group>
)

class CallGroup(key: Any?, box: IntPxBounds, data: Collection<Any?>, children: Collection<Group>) :
    Group(key, box, data, children)

class NodeGroup(
    key: Any?,
    val node: Any,
    box: IntPxBounds,
    data: Collection<Any?>,
    children: Collection<Group>
) : Group(key, box, data, children)

fun SlotTable.asTree(): Group = slots[0] as Group

val Group.position: String? get() = key as? String

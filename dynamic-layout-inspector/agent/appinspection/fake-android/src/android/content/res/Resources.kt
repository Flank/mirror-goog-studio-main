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

package android.content.res

import androidx.annotation.VisibleForTesting

@Suppress("MemberVisibilityCanBePrivate")
class Resources @VisibleForTesting constructor(
    // name format: "namespace.type/entry", e.g. "android.id/next_button"
    val resourceNames: Map<Int, String>,
    val configuration: Configuration = Configuration(),
) {

    class NotFoundException : RuntimeException()

    fun getResourceName(resourceId: Int): String =
        resourceNames[resourceId] ?: throw NotFoundException()

    fun getResourceTypeName(resourceId: Int): String =
        getResourceName(resourceId).substringAfter(".").substringBefore("/")

    fun getResourcePackageName(resourceId: Int): String =
        getResourceName(resourceId).substringBefore("/")

    fun getResourceEntryName(resourceId: Int): String =
        getResourceName(resourceId).substringAfter("/")

}

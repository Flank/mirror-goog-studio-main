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

package com.android.ide.common.gradle.model.impl.ndk.v1

import com.android.ide.common.gradle.model.ndk.v1.IdeNativeVariantInfo
import java.io.File
import java.io.Serializable

class IdeNativeVariantInfoImpl(
    override val abiNames : List<String>,
    override val buildRootFolderMap : Map<String, File>
) : IdeNativeVariantInfo, Serializable {
    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        val stub = o as? IdeNativeVariantInfo ?: return false
        return (abiNames == stub.abiNames) && (buildRootFolderMap == stub.buildRootFolderMap)
    }

    override fun hashCode(): Int {
        return abiNames.hashCode() + buildRootFolderMap.hashCode()
    }

    override fun toString(): String {
        return "abiNames=$abiNames buildRootFolders=$buildRootFolderMap"
    }
}
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

package com.android.ide.common.gradle.model.stubs

import com.android.builder.model.NativeVariantInfo

class NativeVariantInfoStub(private val abiNames : List<String> = listOf("abiName")) : NativeVariantInfo {
    override fun getAbiNames() = abiNames
    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        val stub = o as? NativeVariantInfo ?: return false
        return (abiNames == stub.abiNames)
    }

    override fun hashCode(): Int {
        return getAbiNames().hashCode()
    }

    override fun toString(): String {
        return "abiNames=$abiNames"
    }
}
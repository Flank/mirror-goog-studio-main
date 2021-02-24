/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.build.gradle.integration.common.utils

import com.android.builder.model.ApiVersion

/**
 * Custom implementation of ApiVersion.
 *
 * This is used to test other implementation of ApiVersion in during tests.
 */
internal data class FakeApiVersion @JvmOverloads constructor(
    private val mApiLevel: Int,
    private val mCodename: String? = null
) : ApiVersion {

    override fun getApiLevel(): Int {
        return mApiLevel
    }

    override fun getCodename(): String? {
        return mCodename
    }

    override fun getApiString(): String {
        return mCodename ?: mApiLevel.toString()
    }

    companion object {
        fun create(value: Any): ApiVersion {
            if (value is Int) {
                return FakeApiVersion(value, null)
            } else if (value is String) {
                return FakeApiVersion(1, value)
            }
            throw IllegalArgumentException("value is not Int or String")
        }
    }
}


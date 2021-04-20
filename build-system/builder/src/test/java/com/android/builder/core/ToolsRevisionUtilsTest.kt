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
package com.android.builder.core

import com.android.sdklib.AndroidVersion
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.lang.reflect.Modifier

class ToolsRevisionUtilsTest {
    @Test
    fun `check max recommended compile sdk version is kept up to date`() {
        val maxVersionCode = getMaxKnownVersion()
        assertThat(ToolsRevisionUtils.MAX_RECOMMENDED_COMPILE_SDK_VERSION.featureLevel).isAtLeast(maxVersionCode)
    }

    private fun getMaxKnownVersion() = AndroidVersion.VersionCodes::class.java.fields.asSequence()
        .filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.java }
        .map { it.getInt(null) }
        .max() ?: throw AssertionError()
}

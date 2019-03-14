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

package com.android.build.gradle.internal.res

import com.android.builder.internal.aapt.AaptOptions
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class LinkingTaskInputAaptOptionsTest {

    @Test
    fun checkAllDataClassFieldsReflectedInLinkingTaskInputAaptOptions() {
        val dataProperties = AaptOptions::class.memberProperties.map { it.name }
        val inputProperties =
            LinkingTaskInputAaptOptions::class.memberProperties.map { it.name }
                .filter { it != "aaptOptions" } // Ignore internal impl field.
        assertThat(inputProperties).containsExactlyElementsIn(dataProperties)
    }
}
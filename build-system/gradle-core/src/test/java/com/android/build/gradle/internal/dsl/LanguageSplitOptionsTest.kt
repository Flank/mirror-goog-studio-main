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

package com.android.build.gradle.internal.dsl

import com.android.build.gradle.internal.fixtures.FakeDeprecationReporter
import com.google.common.collect.Iterables
import com.google.common.truth.Truth
import org.junit.Test

class LanguageSplitOptionsTest {

    @Test
    fun testAutoDeprecation() {

        val deprecationReporter = FakeDeprecationReporter()
        val options = LanguageSplitOptions(deprecationReporter)
        options.isAuto = true

        val deprecationWarnings = deprecationReporter.deprecationWarnings
        Truth.assertThat(deprecationWarnings).hasSize(1)
        Truth.assertThat(Iterables.getOnlyElement(deprecationWarnings))
                .contains("LanguageSplitOptions.auto")
    }
}
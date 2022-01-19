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

package com.android.build.gradle.tasks.sync

import com.android.ide.model.sync.ModuleVariantModel
import com.google.common.truth.Truth

fun ModuleVariantModelTask.setupModuleTaskInputs() {
    manifestPlaceholders.set(
            project.objects.mapProperty(String::class.java, String::class.java).also {
                it.put("key1", "value1")
                it.put("key2", "value2")
            }
    )
}

fun ModuleVariantModelTask.assertModuleTaskInputs() {
    Truth.assertThat(manifestPlaceholders.get()).containsExactly(
            "key1", "value1",
            "key2", "value2"
    )
}

fun ModuleVariantModel.testModuleFields() {
    Truth.assertThat(manifestPlaceholdersMap).containsExactly(
            "key1", "value1",
            "key2", "value2"
    )
}

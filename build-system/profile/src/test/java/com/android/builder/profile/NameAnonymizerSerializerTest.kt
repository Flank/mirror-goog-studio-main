/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.builder.profile

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NameAnonymizerSerializerTest {

    @Test
    fun anonymizeVariant() {
        val nameAnonymizer = NameAnonymizer()

        val projectA = nameAnonymizer.anonymizeProjectPath(":a")
        val projectB = nameAnonymizer.anonymizeProjectPath(":b")

        val projectAVariantDebug = nameAnonymizer.anonymizeVariant(":a", "debug")
        val projectAVariantRelease = nameAnonymizer.anonymizeVariant(":a", "release")
        val projectBVariantRelease = nameAnonymizer.anonymizeVariant(":b", "release")
        val projectBVariantDebug = nameAnonymizer.anonymizeVariant(":b", "debug")


        val serialized: String = NameAnonymizerSerializer().toJson(nameAnonymizer)
        println("Serialized $serialized")
        val deserialized = NameAnonymizerSerializer().fromJson(serialized)
        // Run in a different order to check that the ids are reloaded correctly.
        assertThat(deserialized.anonymizeProjectPath(":b")).isEqualTo(projectB)
        assertThat(deserialized.anonymizeProjectPath(":a")).isEqualTo(projectA)
        assertThat(deserialized.anonymizeVariant(":b", "debug")).isEqualTo(projectBVariantDebug)
        assertThat(deserialized.anonymizeVariant(":b", "release")).isEqualTo(projectBVariantRelease)
        assertThat(deserialized.anonymizeVariant(":a", "debug")).isEqualTo(projectAVariantDebug)
        assertThat(deserialized.anonymizeVariant(":a", "release")).isEqualTo(projectAVariantRelease)
    }
}

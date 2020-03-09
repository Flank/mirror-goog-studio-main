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

package com.android.tools.appinspection.livestore

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveStoreTest {
    private enum class State {
        AL,
        AK,
        AZ,

        // ... and many more ...
        WI,
        WY
    }

    @Test
    fun canAddKeysOfAllTypesToLiveStoreAndThenQueryThem() {
        val liveStore = LiveStore("Unused name")
        val nameEntry = liveStore.addString("Name", "John Doe")
        val ageEntry = liveStore.addInt("Age", 21)
        val heightEntry = liveStore.addFloat("Height (Feet)", 5.8f)
        val donorEntry = liveStore.addBool("Organ donor", true)
        val stateEntry = liveStore.addEnum("State of Residence", State.AZ)
        val colorEntry = liveStore.addColor("Favorite Color", Color(0, 255, 0))

        assertThat(nameEntry.value).isEqualTo("John Doe")
        assertThat(ageEntry.value).isEqualTo(21)
        assertThat(heightEntry.value).isEqualTo(5.8f)
        assertThat(donorEntry.value).isEqualTo(true)
        assertThat(stateEntry.value).isEqualTo(State.AZ)
        assertThat(colorEntry.value).isEqualTo(Color(0, 255, 0))
    }

    @Test
    fun canSpecifyIntAndFloatRanges() {
        val liveStore = LiveStore("Unused name")
        val intEntry = liveStore.addInt("Clamped int", 50, 0..100)
        val floatEntry = liveStore.addFloat("Clamped float", .5f, 0f..1f)

        assertThat(intEntry.value).isEqualTo(50)
        assertThat(floatEntry.value).isEqualTo(.5f)

        intEntry.value = -50
        floatEntry.value = -1f
        assertThat(intEntry.value).isEqualTo(0)
        assertThat(floatEntry.value).isEqualTo(0f)

        intEntry.value = 150
        floatEntry.value = 10f
        assertThat(intEntry.value).isEqualTo(100)
        assertThat(floatEntry.value).isEqualTo(1f)
    }
}
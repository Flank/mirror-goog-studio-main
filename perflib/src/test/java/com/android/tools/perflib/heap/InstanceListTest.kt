/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.perflib.heap

import com.google.common.truth.Truth.assertThat
import junit.framework.TestCase

class InstanceListTest: TestCase() {
    fun testAddition() {
        val l1 = InstanceList.Empty +
                RootObj(RootType.INVALID_TYPE, 0, 0, null) +
                RootObj(RootType.INVALID_TYPE, 1, 0, null)
        assertThat(l1.asList()).hasSize(2)
        val l2 = l1 + RootObj(RootType.INVALID_TYPE, 0, 0, null)
        assertThat(l2.asList()).hasSize(3)
    }

    fun testEliminator() {
        fun size(l: InstanceList) = l.onCases( { 1 }, { it.size })
        assertThat(size(InstanceList.Empty)).isEqualTo(0)
        assertThat(size(InstanceList.Empty +
                                RootObj(RootType.INVALID_TYPE, 0, 0, null) +
                                RootObj(RootType.INVALID_TYPE, 1, 0, null)))
            .isEqualTo(2)
    }
}

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

package com.android.tools.agent.layoutinspector.property

import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Property.Type
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ValueTypeTest {

    @Test
    fun testValueTypeAndPropertyTypeAreInSync() {
        for (type in Type.values()) {
            if (type != Type.UNRECOGNIZED) {
                val valueType = ValueType.valueOf(type.valueDescriptor.name)
                assertThat(type.number).isEqualTo(valueType.ordinal)
            }
        }
        assertThat(Type.values().size)
            .isEqualTo(ValueType.values().size + 1)
    }
}

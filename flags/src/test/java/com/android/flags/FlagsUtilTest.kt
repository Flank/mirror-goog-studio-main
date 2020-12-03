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
package com.android.flags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FlagsUtilTest {
  @Test
  fun `check ifEnabled is executed only when the flag is true`() {
    val flags = Flags()
    val group = FlagGroup(flags, "group", "Unused")
    val flagBoolFalse = Flag.create(group, "bool.false", "Unused", "Unused", false)
    val flagBoolTrue = Flag.create(group, "bool.true", "Unused", "Unused", true)

    assertNull(flagBoolFalse.ifEnabled { fail("The callback should not run for a flag set to false") })
    var isExecuted = false
    assertEquals(12, flagBoolTrue.ifEnabled {
      isExecuted = true
      12
    })
    assertTrue("ifEnabled should have executed the callback", isExecuted)
  }

  @Test
  fun `check ifDisabled is executed only when the flag is false`() {
    val flags = Flags()
    val group = FlagGroup(flags, "group", "Unused")
    val flagBoolFalse = Flag.create(group, "bool.false", "Unused", "Unused", false)
    val flagBoolTrue = Flag.create(group, "bool.true", "Unused", "Unused", true)

    assertNull(flagBoolTrue.ifDisabled { fail("The callback should not run for a flag set to true") })
    var isExecuted = false
    assertEquals(12, flagBoolFalse.ifDisabled {
      isExecuted = true
      12
    })
    assertTrue("ifDisabled should have executed the callback", isExecuted)
  }
}

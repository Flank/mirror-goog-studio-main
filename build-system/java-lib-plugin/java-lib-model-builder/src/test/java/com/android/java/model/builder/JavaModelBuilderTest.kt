/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.java.model.builder

import com.android.java.model.builder.JavaModelBuilder.Companion.isGradleAtLeast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Test class for [JavaModelBuilder].  */
class JavaModelBuilderTest {
  @Test
  fun gradleVersionCheck() {
    assertFalse(isGradleAtLeast("2.2", "2.12"))
    assertFalse(isGradleAtLeast("2.2", "2.6"))
    assertTrue(isGradleAtLeast("4.2", "2.6"))
    assertTrue(isGradleAtLeast("4.2", "2.12"))
    assertTrue(isGradleAtLeast("4.2", "4.2"))
  }
}

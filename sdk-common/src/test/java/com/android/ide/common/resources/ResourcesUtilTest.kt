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
package com.android.ide.common.resources

import com.android.ide.common.util.PathString
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Test

class ResourcesUtilTest {
  @Test
  fun testFlattenResourceName() {
    assertEquals("", resourceNameToFieldName(""))
    assertEquals("my_key_test", resourceNameToFieldName("my.key:test"))
    assertEquals("my_key_test", resourceNameToFieldName("my_key_test"))
    assertEquals("_key_test", resourceNameToFieldName(".key_test"))
    assertEquals("_key_test_", resourceNameToFieldName(".key_test:"))
    assertEquals("_key test_", resourceNameToFieldName("-key test:"))
  }

  @Test
  fun testToFileResourcePathString() {
    assertThat(toFileResourcePathString("apk:///foo.apk!/bar.baz"))
        .isEqualTo(PathString("apk", "/foo.apk!/bar.baz"))
  }
}

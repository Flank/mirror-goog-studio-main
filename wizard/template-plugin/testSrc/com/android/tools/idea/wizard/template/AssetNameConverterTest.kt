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
package com.android.tools.idea.wizard.template

import com.google.common.truth.Truth.assertThat
import com.android.tools.idea.wizard.template.AssetNameConverter.Type
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AssetNameConverterTest {

  @Nested
  inner class StripSuffixTest {
    @Test
    fun `No-op test`() {
      assertThat("").isEqualTo("".stripSuffix("", false))
      assertThat("").isEqualTo( "".stripSuffix("foo", false))
    }

    @Test
    fun `Whole string test`() {
      assertThat("").isEqualTo( "foo".stripSuffix("foo", false))
    }

    @Test
    fun `Suffix test`() {
      assertThat("Foo").isEqualTo( "FooBar".stripSuffix("Bar", false))
      assertThat("Foo").isEqualTo( "FooBar".stripSuffix("Bar", false))
    }

    @Test
    fun `Double Suffix test`() {
      assertThat("Foo").isEqualTo( "FooBarBar".stripSuffix("Bar", true))
      assertThat("FooBar").isEqualTo( "FooBarBar".stripSuffix( "Bar", false))
    }
  }

  @Test
  fun canConvertFromClassName() {
    val c = AssetNameConverter(Type.CLASS_NAME, "TestName")
    assertThat(c.getValue(Type.CLASS_NAME)).isEqualTo("TestName")
    assertThat(c.getValue(Type.ACTIVITY)).isEqualTo("TestNameActivity")
    assertThat(c.getValue(Type.LAYOUT)).isEqualTo("activity_test_name")
    assertThat(c.getValue(Type.RESOURCE)).isEqualTo("test_name")
  }

  @Test
  fun canConvertFromActivity() {
    val c = AssetNameConverter(Type.ACTIVITY, "TestNameActivity")
    assertThat(c.getValue(Type.CLASS_NAME)).isEqualTo("TestName")
    assertThat(c.getValue(Type.ACTIVITY)).isEqualTo("TestNameActivity")
    assertThat(c.getValue(Type.LAYOUT)).isEqualTo("activity_test_name")
    assertThat(c.getValue(Type.RESOURCE)).isEqualTo("test_name")
  }

  @Test
  fun canConvertFromLayout() {
    val c = AssetNameConverter(Type.LAYOUT, "activity_test_name")
    assertThat(c.getValue(Type.CLASS_NAME)).isEqualTo("TestName")
    assertThat(c.getValue(Type.ACTIVITY)).isEqualTo("TestNameActivity")
    assertThat(c.getValue(Type.LAYOUT)).isEqualTo("activity_test_name")
    assertThat(c.getValue(Type.RESOURCE)).isEqualTo("test_name")
  }

  @Test
  fun canConvertFromResource() {
    val c = AssetNameConverter(Type.RESOURCE, "test_name")
    assertThat(c.getValue(Type.CLASS_NAME)).isEqualTo("TestName")
    assertThat(c.getValue(Type.ACTIVITY)).isEqualTo("TestNameActivity")
    assertThat(c.getValue(Type.LAYOUT)).isEqualTo("activity_test_name")
    assertThat(c.getValue(Type.RESOURCE)).isEqualTo("test_name")
  }

  @Test
  fun canConvertFromActivityEvenWithoutActivitySuffix() {
    val c = AssetNameConverter(Type.ACTIVITY, "TestName")
    assertThat(c.getValue(Type.CLASS_NAME)).isEqualTo("TestName")
    assertThat(c.getValue(Type.ACTIVITY)).isEqualTo("TestNameActivity")
    assertThat(c.getValue(Type.LAYOUT)).isEqualTo("activity_test_name")
    assertThat(c.getValue(Type.RESOURCE)).isEqualTo("test_name")
  }

  @Test
  fun canConvertFromLayoutEvenWithoutActivityPrefix() {
    val c = AssetNameConverter(Type.LAYOUT, "test_name")
    assertThat(c.getValue(Type.CLASS_NAME)).isEqualTo("TestName")
    assertThat(c.getValue(Type.ACTIVITY)).isEqualTo("TestNameActivity")
    assertThat(c.getValue(Type.LAYOUT)).isEqualTo("activity_test_name")
    assertThat(c.getValue(Type.RESOURCE)).isEqualTo("test_name")
  }

  @Test
  fun canConvertFromLayoutWithCustomPrefix() {
    val c = AssetNameConverter(Type.LAYOUT, "custom_test_name").overrideLayoutPrefix("custom")
    assertThat(c.getValue(Type.ACTIVITY)).isEqualTo("TestNameActivity")
  }

  @Test
  fun canConvertToLayoutWithCustomPrefix() {
    val c = AssetNameConverter(Type.ACTIVITY, "TestName").overrideLayoutPrefix("custom")
    assertThat(c.getValue(Type.LAYOUT)).isEqualTo("custom_test_name")
  }

  @Test
  fun layoutPrefixesAreStripped() {
    val c = AssetNameConverter(Type.CLASS_NAME, "TestAppBar").overrideLayoutPrefix("app_bar")
    // "app_bar_test", not "app_bar_test_app_bar"
    assertThat(c.getValue(Type.LAYOUT)).isEqualTo("app_bar_test")
  }

  @Test
  fun classNameStripsSeveralSuffixesRecursively() {
    for (s in STRIP_CLASS_SUFFIXES) {
      run {
        // Simple suffixes like "Activity" or "Fragment" are stripped
        val c = AssetNameConverter(Type.CLASS_NAME, "TestName$s")
        assertThat(c.getValue(Type.CLASS_NAME)).isEqualTo("TestName")
        assertThat(c.getValue(Type.ACTIVITY)).isEqualTo("TestNameActivity")
        assertThat(c.getValue(Type.LAYOUT)).isEqualTo("activity_test_name")
        assertThat(c.getValue(Type.RESOURCE)).isEqualTo("test_name")
      }

      run {
        // Combo suffixes like "ActivityActivity" are stripped
        val c = AssetNameConverter(Type.CLASS_NAME, "TestName$s$s")
        assertThat(c.getValue(Type.CLASS_NAME)).isEqualTo("TestName")
        assertThat(c.getValue(Type.ACTIVITY)).isEqualTo("TestNameActivity")
        assertThat(c.getValue(Type.LAYOUT)).isEqualTo("activity_test_name")
        assertThat(c.getValue(Type.RESOURCE)).isEqualTo("test_name")
      }
    }
  }

  @Test
  fun classNameStripsSuffixEvenWithTrailingDigits() {
    // Simple suffixes like "Activity" or "Fragment" are stripped
    val c = AssetNameConverter(Type.ACTIVITY, "TestNameActivity9876")
    assertThat(c.getValue(Type.CLASS_NAME)).isEqualTo("TestName9876")
    assertThat(c.getValue(Type.ACTIVITY)).isEqualTo("TestName9876Activity")
    assertThat(c.getValue(Type.LAYOUT)).isEqualTo("activity_test_name9876")
    assertThat(c.getValue(Type.RESOURCE)).isEqualTo("test_name9876")
  }

  @Test
  fun emptyClassNameDefaultsToMainActivity() {
    val c = AssetNameConverter(Type.CLASS_NAME, "")
    assertThat(c.getValue(Type.ACTIVITY)).isEqualTo("MainActivity")
  }
}
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
import org.junit.Test

class AssetNameConverterTest {
  @Test
  fun `Strip suffix from empty string`() {
    assertThat("").isEqualTo("".stripSuffix("", false))
    assertThat("").isEqualTo("".stripSuffix("foo", false))
  }

  @Test
  fun `Strip with whole string suffix`() {
    assertThat("").isEqualTo("foo".stripSuffix("foo", false))
  }

  @Test
  fun `Strip with normal suffix`() {
    assertThat("Foo").isEqualTo( "FooBar".stripSuffix("Bar", false))
    assertThat("Foo").isEqualTo( "FooBar".stripSuffix("Bar", false))
  }

  @Test
  fun `Strip with double suffix`() {
    assertThat("Foo").isEqualTo( "FooBarBar".stripSuffix("Bar", true))
    assertThat("FooBar").isEqualTo( "FooBarBar".stripSuffix( "Bar", false))
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

  @Test
  fun additionalActivityToLayoutTest() {
    val data = mapOf(
      "FooActivity" to "activity_foo",
      "FooActiv" to "activity_foo",
      "FooActivityActivity" to "activity_foo_activity",
      "Foo" to "activity_foo",
      "Activity" to "activity_",
      "ActivityActivity" to "activity_activity",
      "MainActivityTest1" to "activity_main_test1",
      "" to "",
      "x" to "activity_x",
      "X" to "activity_x",
      "Ac" to "activity_",
      "ac" to "activity_",
      "FooActivity2" to "activity_foo2",
      "FooActivity200" to "activity_foo200",
      "Activity200" to "activity_200",
      "BaseNameActivityActiv" to "activity_base_name_activity",
      "MY_LOGIN_ACTIVITY" to "activity_my_login",
      "MY_login_ACTIVITY" to "activity_my_login",
      "MY_L_OGIN_ACTIVITY" to "activity_my_login",
      "My___lOGIN___ACTIVITY" to "activity_my_login",
      "MyCLASsName" to "activity_my_class_name",
      "my_class_name" to "activity_my_class_name"
    )
    for ((activity, layout) in data) {
      assertThat(activityToLayout(activity)).named(activity).isEqualTo(layout)
    }

    assertThat(activityToLayout("MainActivity", "simple")).isEqualTo("simple_main")
    assertThat(activityToLayout("FullScreenActivity", "content")).isEqualTo("content_full_screen")
  }

  @Test
  fun additionalLayoutToActivityTest() {
    val data = mapOf(
      "foo" to "FooActivity",
      "activity_foo" to "FooActivity",
      "activity_" to "MainActivity",
      "activ" to "ActivActivity",
      "" to "MainActivity",
      "activity_foo2" to "Foo2Activity",
      "activity_foo200" to "Foo200Activity",
      "activity_200" to "MainActivity"
    )
    for ((layout, activity) in data) {
      assertThat(layoutToActivity(layout)).isEqualTo(activity)
    }
  }

  @Test
  fun additionalLayoutToFragmentTest() {
    val data = mapOf(
      "foo" to "FooFragment",
      "fragment_foo" to "FooFragment",
      "fragment_" to "MainFragment",
      "fragmen" to "FragmenFragment",
      "" to "MainFragment",
      "fragment_foo2" to "Foo2Fragment",
      "fragment_foo200" to "Foo200Fragment",
      "fragment_200" to "MainFragment"
    )
    for ((layout, fragment) in data) {
      assertThat(layoutToFragment(layout)).isEqualTo(fragment)
    }
  }

  @Test
  fun additionalFragmentToLayoutTest() {
    val data = mapOf(
      "FooFragment" to "fragment_foo",
      "FooFragm" to "fragment_foo",
      "FooFragmentFragment" to "fragment_foo_fragment",
      "Foo" to "fragment_foo",
      "Fragment" to "fragment_",
      "FragmentFragment" to "fragment_fragment",
      "MainFragmentTest1" to "fragment_main_test1",
      "" to "",
      "x" to "fragment_x",
      "X" to "fragment_x",
      "Fr" to "fragment_",
      "fr" to "fragment_fr",
      "FooFragment2" to "fragment_foo2",
      "FooFragment200" to "fragment_foo200",
      "Fragment200" to "fragment_200"
      // TODO(qumeric):
      // check("BaseNameFragmentFragm", "fragment", "fragment_base_name_fragment")
      // check("MainFragment", "simple", "simple_main")
      // check("FullScreenFragment", "content", "content_full_screen")
    )
    for ((fragment, layout) in data) {
      assertThat(fragmentToLayout(fragment)).isEqualTo(layout)
    }
  }

  @Test
  fun additionalClassNameToResourceTest() {
    val data = mapOf(
      "FooActivity" to "foo",
      "FooActiv" to "foo",
      "Foo" to "foo",
      "FooBar" to "foo_bar",
      "" to "",
      "FooFragment" to "foo",
      "FooService" to "foo",
      "FooProvider" to "foo"
    )
    for ((className, resource) in data) {
      assertThat(classToResource(className)).isEqualTo(resource)
    }
  }
}
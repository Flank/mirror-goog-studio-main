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
package com.android.ide.common.resources.escape.string

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class StringResourceEscaperTest {
  @Test
  fun escape_empty() {
    assertThat(StringResourceEscaper.escape("", true)).isEqualTo("")
  }

  @Test
  fun escape_firstQuestionMark() {
    assertThat(StringResourceEscaper.escape("???", true)).isEqualTo("""\???""")
  }

  @Test
  fun escape_firstAtSign() {
    assertThat(StringResourceEscaper.escape("@@@", true)).isEqualTo("""\@@@""")
  }

  @Test
  fun escape_quotationMarks() {
    assertThat(StringResourceEscaper.escape(""""""", true)).isEqualTo("""\"""")
  }

  @Test
  fun escape_backslashes() {
    assertThat(StringResourceEscaper.escape("""\""", true)).isEqualTo("""\\""")
  }

  @Test
  fun escape_newlines() {
    assertThat(StringResourceEscaper.escape("""
""", true)).isEqualTo("""\n""")
  }

  @Test
  fun escape_tabs() {
    // Can't have tabs in source so no raw strings here.
    assertThat(StringResourceEscaper.escape("\t", true)).isEqualTo("\\t")
  }

  @Test
  fun escape_apostrophesLeadingAndTrailingSpaces() {
    assertThat(StringResourceEscaper.escape("'", true)).isEqualTo("""\'""")
    assertThat(StringResourceEscaper.escape("' ", true)).isEqualTo(""""' """")
    assertThat(StringResourceEscaper.escape(" '", true)).isEqualTo("""" '"""")
    assertThat(StringResourceEscaper.escape(" ' ", true)).isEqualTo("""" ' """")
  }

  @Test
  fun escape_ampersands() {
    assertThat(StringResourceEscaper.escape("&", true)).isEqualTo("&amp;")
    assertThat(StringResourceEscaper.escape("&", false)).isEqualTo("&")
  }

  @Test
  fun escape_lessThanSigns() {
    assertThat(StringResourceEscaper.escape("<", true)).isEqualTo("&lt;")
    assertThat(StringResourceEscaper.escape("<", false)).isEqualTo("<")
  }
}

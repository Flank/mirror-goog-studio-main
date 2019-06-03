package com.android.aaptcompiler

import com.android.aaptcompiler.android.ResValue
import com.google.common.truth.Truth
import org.junit.Test

class ResourceUtilsTest {

  @Test
  fun testParseBool() {
    Truth.assertThat(parseAsBool("true")).isTrue()
    Truth.assertThat(parseAsBool("TRUE")).isTrue()
    Truth.assertThat(parseAsBool("True")).isTrue()

    Truth.assertThat(parseAsBool("false")).isFalse()
    Truth.assertThat(parseAsBool("FALSE")).isFalse()
    Truth.assertThat(parseAsBool("False")).isFalse()

    Truth.assertThat(parseAsBool(" False\n ")).isFalse()
  }

  @Test
  fun testNullIsEmptyReference() {
    Truth.assertThat(makeNull()).isEqualTo(Reference())
    Truth.assertThat(tryParseNullOrEmpty("@null")).isEqualTo(Reference())
  }

  @Test
  fun testEmptyIsBinaryPrimitive() {
    Truth.assertThat(makeEmpty())
      .isEqualTo(BinaryPrimitive(ResValue(ResValue.DataType.NULL, ResValue.NullFormat.EMPTY)))
    Truth.assertThat(tryParseNullOrEmpty("@empty"))
      .isEqualTo(BinaryPrimitive(ResValue(ResValue.DataType.NULL, ResValue.NullFormat.EMPTY)))
  }
}
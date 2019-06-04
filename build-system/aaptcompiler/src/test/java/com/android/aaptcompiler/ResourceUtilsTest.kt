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

  @Test
  fun testParseColorTypes() {
    // needs leading '#' character
    Truth.assertThat(tryParseColor("ff80a12b")).isNull()

    Truth.assertThat(tryParseColor("#e12"))
      .isEqualTo(BinaryPrimitive(ResValue(ResValue.DataType.INT_COLOR_RGB4, 0xffee1122.toInt())))
    Truth.assertThat(tryParseColor("#8e12"))
      .isEqualTo(BinaryPrimitive(ResValue(ResValue.DataType.INT_COLOR_ARGB4, 0x88ee1122.toInt())))
    Truth.assertThat(tryParseColor("#00ffff"))
      .isEqualTo(BinaryPrimitive(ResValue(ResValue.DataType.INT_COLOR_RGB8, 0xff00ffff.toInt())))
    Truth.assertThat(tryParseColor("#ff80a12b"))
      .isEqualTo(BinaryPrimitive(ResValue(ResValue.DataType.INT_COLOR_ARGB8, 0xff80a12b.toInt())))

    // Ensure that invalid lengths fail to parse
    Truth.assertThat(tryParseColor("#884a2")).isNull()
    Truth.assertThat(tryParseColor("#123456789")).isNull()
  }
}
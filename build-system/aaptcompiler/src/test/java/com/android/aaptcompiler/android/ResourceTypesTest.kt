package com.android.aaptcompiler.android

import com.google.common.truth.Truth
import org.junit.Test

class ResourceTypesTest {

  private fun testStringToIntCase(input: String, expectedValue: Int, expectedHex: Boolean = false) {
    val result = stringToInt(input)
    val expectedOutput =
      ResValue(
        if(expectedHex) ResValue.DataType.INT_HEX else ResValue.DataType.INT_DEC,
        expectedValue)
    Truth.assertThat(result).isEqualTo(expectedOutput)
  }

  private fun testStringToIntFailure(input: String) {
    val result = stringToInt(input)
    Truth.assertThat(result).isNull()
  }

  @Test
  fun testStringToInt() {
    testStringToIntFailure("")
    testStringToIntFailure("    ")
    testStringToIntFailure("\t\n")

    testStringToIntFailure("abcd")
    testStringToIntFailure("10abcd")
    testStringToIntFailure("42 42")
    testStringToIntFailure("- 42")
    testStringToIntFailure("-")

    testStringToIntFailure("0x")
    testStringToIntFailure("0xnope")
    testStringToIntFailure("0X42")
    testStringToIntFailure("0x42 0x42")
    testStringToIntFailure("-0x0")
    testStringToIntFailure("-0x42")
    testStringToIntFailure("- 0x42")

    // Note that u" 42" would pass. This preserves the old behavior, but it may not be desired.
    testStringToIntFailure("42 ")
    testStringToIntFailure("0x42 ")

    // Decimal cases.
    testStringToIntCase("0", 0)
    testStringToIntCase("-0", 0)
    testStringToIntCase("42", 42)
    testStringToIntCase(" 42", 42)
    testStringToIntCase("-42", -42)
    testStringToIntCase("\n  -42", -42)
    testStringToIntCase("042", 42)
    testStringToIntCase("-042", -42)

    // Hex cases.
    testStringToIntCase("0x0", 0x0, true)
    testStringToIntCase("0x42", 0x42, true)
    testStringToIntCase("\t0x42", 0x42, true)

    // Just Before overflow cases
    testStringToIntCase("2147483647", Int.MAX_VALUE)
    testStringToIntCase("-2147483648", Int.MIN_VALUE)
    testStringToIntCase("0xffffffff", -1, true)

    // Overflow cases:
    testStringToIntFailure("2147483648")
    testStringToIntFailure("-2147483649")
    testStringToIntFailure("0x1ffffffff")
  }
}

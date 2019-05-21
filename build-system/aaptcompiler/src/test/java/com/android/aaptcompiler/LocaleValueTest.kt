package com.android.aaptcompiler

import com.google.common.truth.Truth
import org.junit.Test

class LocaleValueTest {

  fun testLanguage(input: String, expectedLang: String, expectFailure: Boolean = false) {
    val parts = input.split('-').map { it.toLowerCase() }
    val locale = LocaleValue()

    val count = locale.initFromParts(parts, 0)

    Truth.assertThat(count).isEqualTo(if (expectFailure) 0 else 1)
    Truth.assertThat(locale.language).isEqualTo(expectedLang)
  }

  fun testLanguageRegion(input: String, expectedLang: String, expectedRegion: String) {
    val parts = input.split('-').map { it.toLowerCase() }
    val locale = LocaleValue()

    val count = locale.initFromParts(parts, 0)

    Truth.assertThat(count).isEqualTo(2)
    Truth.assertThat(locale.language).isEqualTo(expectedLang)
    Truth.assertThat(locale.region).isEqualTo(expectedRegion)
  }

  @Test
  fun testParseLanguage() {
    testLanguage("en", "en")
    testLanguage("fr", "fr")
    testLanguage("land", "", true)
    testLanguage("fr-land", "fr")

    testLanguageRegion("fr-rCA", "fr","CA")
  }
}
package com.android.aaptcompiler

import com.android.aaptcompiler.testutils.parseNameOrFail
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Test

class XmlProcessorTest {

  lateinit var processor: XmlProcessor

  @Before
  fun beforeTest() {
    processor = XmlProcessor(Source(""))
  }

  private fun processTest(input: String): Boolean =
    processor.process(input.byteInputStream())

  @Test
  fun testEmpty() {
    Truth.assertThat(processTest("")).isTrue()
  }

  @Test
  fun testCollectsIds() {
    val input = """
      <View xmlns:android="http://schemas.android.com/apk/res/android"
        android:id="@+id/foo"
        text="@+id/bar">
        <SubView
          android:id="@+id/car"
          class="@+id/bar"/>
      </View>
    """.trimIndent()

    Truth.assertThat(processTest(input)).isTrue()

    val collectedIds = processor.createdIds
    Truth.assertThat(collectedIds).hasSize(3)

    Truth.assertThat(collectedIds[0]).isEqualTo(SourcedResourceName(parseNameOrFail("id/bar"), 3))
    Truth.assertThat(collectedIds[1]).isEqualTo(SourcedResourceName(parseNameOrFail("id/car"), 6))
    Truth.assertThat(collectedIds[2]).isEqualTo(SourcedResourceName(parseNameOrFail("id/foo"), 3))
  }

  @Test
  fun testNoCollectNonIds() {
    Truth.assertThat(processTest("""<View foo="@+string/foo"/>""")).isTrue()

    Truth.assertThat(processor.createdIds).isEmpty()

    val input = """
      <View xmlns:android="http://schemas.android.com/apk/res/android"
        android:id="@+id/foo"
        text="@+string/bar"/>
    """.trimIndent()
    Truth.assertThat(processTest(input)).isTrue()

    Truth.assertThat(processor.createdIds).hasSize(1)
    Truth.assertThat(processor.createdIds[0])
      .isEqualTo(SourcedResourceName(parseNameOrFail("id/foo"), 3))
  }

  @Test
  fun failOnInvalidIds() {
    Truth.assertThat(processTest("""<View foo="@+id/foo${'$'}bar"/>""")).isFalse()
  }
}

package com.android.aaptcompiler

import com.android.aaptcompiler.android.ResValue
import com.android.aapt.Resources
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Test
import javax.xml.stream.XMLInputFactory

const val XML_PREAMBLE = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"

class TableExtractorTest {
  private val xmlInputFactory = XMLInputFactory.newFactory()!!

  lateinit var table: ResourceTable

  @Before
  fun setup() {
    table = ResourceTable()
  }

  private fun testParse(input: String, config: ConfigDescription = ConfigDescription()): Boolean {
    val parseInput =
      """$XML_PREAMBLE
      <resources>
      $input
      </resources>
    """.trimIndent()

    val extractor =
      TableExtractor(table, Source("test.xml"), config, TableExtractorOptions())

    return extractor.extract(parseInput.byteInputStream())
  }

  private fun getValue(
    resName: String,
    config: ConfigDescription = ConfigDescription(),
    productName: String = "") =
    getValue(table, resName, config, productName)

  @Test
  fun testParseEmptyInput() {
    val input = "$XML_PREAMBLE\n"

    val extractor =
      TableExtractor(table, Source("test.xml"), ConfigDescription(), TableExtractorOptions())

    Truth.assertThat(extractor.extract(input.byteInputStream())).isTrue()
  }

  @Test
  fun failToParseWithNoRoot() {
    val input = """$XML_PREAMBLE<attr name="foo"/>"""
    val extractor =
      TableExtractor(table, Source("test.xml"), ConfigDescription(), TableExtractorOptions())

    Truth.assertThat(extractor.extract(input.byteInputStream())).isFalse()
  }

  @Test
  fun testParseBoolean() {
    val input = """
      <bool name="a">true</bool>
      <bool name="b">false</bool>""".trimIndent()

    Truth.assertThat(testParse(input)).isTrue()

    val boolA = getValue("bool/a") as? BinaryPrimitive
    Truth.assertThat(boolA).isNotNull()
    Truth.assertThat(boolA!!.resValue.dataType).isEqualTo(ResValue.DataType.INT_BOOLEAN)
    Truth.assertThat(boolA.resValue.data).isEqualTo(-1)

    val boolB = getValue("bool/b") as? BinaryPrimitive
    Truth.assertThat(boolB).isNotNull()
    Truth.assertThat(boolB!!.resValue.dataType).isEqualTo(ResValue.DataType.INT_BOOLEAN)
    Truth.assertThat(boolB.resValue.data).isEqualTo(0)
  }

  @Test
  fun testParseColor() {
    val input = """
      <color name="a">#7fa87f</color>
      <color name="b">@android:color/black</color>
    """.trimIndent()

    Truth.assertThat(testParse(input)).isTrue()

    val colorA = getValue("color/a") as? BinaryPrimitive
    Truth.assertThat(colorA).isNotNull()
    Truth.assertThat(colorA!!.resValue.dataType).isEqualTo(ResValue.DataType.INT_COLOR_RGB8)
    Truth.assertThat(colorA.resValue.data).isEqualTo(0xff7fa87f.toInt())

    val colorB = getValue("color/b") as? Reference
    Truth.assertThat(colorB).isNotNull()
    Truth.assertThat(colorB!!.name)
      .isEqualTo(parseResourceName("android:color/black")!!.resourceName)
  }

  @Test
  fun parseDimen() {
    val input =
      """
        <dimen name="a">16dp</dimen>
        <dimen name="b">@dimen/abc_control_padding_material</dimen>
        <item name="c" type="dimen">10%</item>
      """.trimIndent()

    Truth.assertThat(testParse(input)).isTrue()

    val dimenA = getValue("dimen/a") as? BinaryPrimitive
    Truth.assertThat(dimenA).isNotNull()
    Truth.assertThat(dimenA!!.resValue.dataType).isEqualTo(ResValue.DataType.DIMENSION)
    Truth.assertThat(dimenA.resValue.data).isEqualTo(0x1001)

    val dimenB = getValue("dimen/b") as? Reference
    Truth.assertThat(dimenB).isNotNull()
    Truth.assertThat(dimenB!!.name)
      .isEqualTo(parseResourceName("dimen/abc_control_padding_material")!!.resourceName)

    val dimenC = getValue("dimen/c") as? BinaryPrimitive
    Truth.assertThat(dimenC).isNotNull()
    Truth.assertThat(dimenC!!.resValue.dataType).isEqualTo(ResValue.DataType.FRACTION)
    // 0           00011001100110011001101     0011                        0000
    // ^ positive  ^ 10% or .1 with rounding.  ^ Radix 0p23 (all fraction) ^ non-parent fraction
    // i.e. 0x0ccccd30
    Truth.assertThat(dimenC.resValue.data).isEqualTo(0x0ccccd30)
  }

  @Test
  fun testParseDrawable() {
    Truth.assertThat(testParse("""<drawable name="foo">#0cffffff</drawable>""")).isTrue()

    val drawable = getValue("drawable/foo") as? BinaryPrimitive
    Truth.assertThat(drawable).isNotNull()
    Truth.assertThat(drawable!!.resValue.dataType).isEqualTo(ResValue.DataType.INT_COLOR_ARGB8)
    Truth.assertThat(drawable!!.resValue.data).isEqualTo(0xcffffff)
  }

  @Test
  fun testParseInteger() {
    val input = """
      <integer name="a">10</integer>
      <integer name="b">0x10</integer>
      <item name="c" type="integer">0xA</item>
    """.trimIndent()

    Truth.assertThat(testParse(input)).isTrue()

    val integerA = getValue("integer/a") as? BinaryPrimitive
    Truth.assertThat(integerA).isNotNull()
    Truth.assertThat(integerA!!.resValue.dataType).isEqualTo(ResValue.DataType.INT_DEC)
    Truth.assertThat(integerA.resValue.data).isEqualTo(10)

    val integerB = getValue("integer/b") as? BinaryPrimitive
    Truth.assertThat(integerB).isNotNull()
    Truth.assertThat(integerB!!.resValue.dataType).isEqualTo(ResValue.DataType.INT_HEX)
    Truth.assertThat(integerB.resValue.data).isEqualTo(16)

    val integerC = getValue("integer/c") as? BinaryPrimitive
    Truth.assertThat(integerC).isNotNull()
    Truth.assertThat(integerC!!.resValue.dataType).isEqualTo(ResValue.DataType.INT_HEX)
    Truth.assertThat(integerC.resValue.data).isEqualTo(10)
  }

  @Test
  fun testParseId() {
    Truth.assertThat(testParse("""<item name="foo" type="id"/>""")).isTrue()

    Truth.assertThat(getValue("id/foo") as? Id).isNotNull()
  }

  @Test
  fun testParseQuotedString() {

    Truth.assertThat(testParse("""<string name="foo">   "  hey there " </string>""")).isTrue()
    var str = getValue("string/foo") as BasicString
    Truth.assertThat(str.toString()).isEqualTo("  hey there ")
    Truth.assertThat(str.untranslatables).isEmpty()

    Truth.assertThat(testParse("""<string name="bar">Isn\'t it cool?</string>""")).isTrue()
    str = getValue("string/bar") as BasicString
    Truth.assertThat(str.toString()).isEqualTo("Isn't it cool?")

    Truth.assertThat(testParse("""<string name="baz">"Isn't it cool?"</string>""")).isTrue()
    str = getValue("string/baz") as BasicString
    Truth.assertThat(str.toString()).isEqualTo("Isn't it cool?")
  }

  @Test
  fun testParseEscapedString() {
    Truth.assertThat(testParse("""<string name="foo">\?123</string>""")).isTrue()
    var str = getValue("string/foo") as BasicString
    Truth.assertThat(str.toString()).isEqualTo("?123")
    Truth.assertThat(str.untranslatables).isEmpty()

    Truth.assertThat(testParse("""<string name="bar">This isn\'t a bad string</string>"""))
    str = getValue("string/bar") as BasicString
    Truth.assertThat(str.toString()).isEqualTo("This isn't a bad string")
  }

  @Test
  fun testParseFormattedString() {
    Truth.assertThat(testParse("""<string name="foo">%d %s</string>""")).isFalse()
    Truth.assertThat(
      testParse("""<string name="foo">%1${"$"}d %2${"$"}s</string>""")).isTrue()
  }

  @Test
  fun testParseStyledString() {
    val input =
      "<string name=\"foo\">This is my aunt\u2019s <b>fickle <small>string</small></b></string>"
    Truth.assertThat(testParse(input)).isTrue()

    val str = getValue("string/foo") as StyledString

    Truth.assertThat(str.toString()).isEqualTo("This is my aunt\u2019s fickle string")
    Truth.assertThat(str.spans()).hasSize(2)
    Truth.assertThat(str.untranslatableSections).isEmpty()

    val span0 = str.spans()[0]
    Truth.assertThat(span0.name.value()).isEqualTo("b")
    Truth.assertThat(span0.firstChar).isEqualTo(18)
    Truth.assertThat(span0.lastChar).isEqualTo(30)

    val span1 = str.spans()[1]
    Truth.assertThat(span1.name.value()).isEqualTo("small")
    Truth.assertThat(span1.firstChar).isEqualTo(25)
    Truth.assertThat(span1.lastChar).isEqualTo(30)
  }

  @Test
  fun testParseStringWithWhitespace() {
    Truth.assertThat(testParse("""<string name="foo">  This is what   I think  </string>"""))

    var str = getValue("string/foo") as BasicString
    Truth.assertThat(str.toString()).isEqualTo("This is what I think")
    Truth.assertThat(str.untranslatables).isEmpty()

    Truth.assertThat(
      testParse("""<string name="foo2">"  This is what   I think  "</string>"""))

    str = getValue("string/foo2") as BasicString
    Truth.assertThat(str.toString()).isEqualTo("  This is what   I think  ")
    Truth.assertThat(str.untranslatables).isEmpty()
  }

  @Test
  fun testIgnoreXliffTagsOtherThanG() {
    val input = """
      <string name="foo" xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
          There are <xliff:source>no</xliff:source> apples</string>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isTrue()

    var str = getValue("string/foo") as? BasicString
    Truth.assertThat(str).isNotNull()
    str = str!!
    Truth.assertThat(str.toString()).isEqualTo("There are no apples")
    Truth.assertThat(str.untranslatables).isEmpty()
  }

  @Test
  fun failToParseNestedXliffGTags() {
    val input = """
      <string name="foo" xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
          Do not <xliff:g>translate <xliff:g>this</xliff:g></xliff:g></string>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isFalse()
  }

  @Test
  fun testParseUntranslatableSections() {
    val input = """
      <string name="foo" xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
          There are <xliff:g id="count">%1${"$"}d</xliff:g> apples</string>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isTrue()

    val str = getValue("string/foo") as? BasicString
    Truth.assertThat(str).isNotNull()
    str!!
    Truth.assertThat(str.toString()).isEqualTo("There are %1\$d apples")
    Truth.assertThat(str.untranslatables).hasSize(1)

    val untranslatables0 = str.untranslatables[0]
    Truth.assertThat(untranslatables0.startIndex).isEqualTo(10)
    Truth.assertThat(untranslatables0.endIndex).isEqualTo(14)
  }

  @Test
  fun testParseUntranslatablesInStyledString() {
    val input = """
      <string name="foo" xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
          There are <b><xliff:g id="count">%1${"$"}d</xliff:g></b> apples</string>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isTrue()

    val str = getValue("string/foo") as? StyledString
    Truth.assertThat(str).isNotNull()
    str!!
    Truth.assertThat(str.toString()).isEqualTo(" There are %1\$d apples")
    Truth.assertThat(str.spans()).hasSize(1)
    Truth.assertThat(str.untranslatableSections).hasSize(1)

    val untranslatables0 = str.untranslatableSections[0]
    Truth.assertThat(untranslatables0.startIndex).isEqualTo(11)
    Truth.assertThat(untranslatables0.endIndex).isEqualTo(15)

    val span0 = str.spans()[0]
    Truth.assertThat(span0.name.value()).isEqualTo("b")
    Truth.assertThat(span0.firstChar).isEqualTo(11)
    Truth.assertThat(span0.lastChar).isEqualTo(14)
  }

  @Test
  fun testParseNull() {
    val input = """<integer name="foo">@null</integer>"""
    Truth.assertThat(testParse(input)).isTrue()

    // The Android runtime treats a value of android::Res_value::TYPE_NULL as a non-existing value,
    // and this causes problems in styles when trying to resolve an attribute. Null values must be
    // encoded as android::Res_value::TYPE_REFERENCE with a data value of 0.
    val nullRef = getValue("integer/foo") as? Reference
    Truth.assertThat(nullRef).isNotNull()
    nullRef!!
    Truth.assertThat(nullRef.name).isEqualTo(ResourceName("", AaptResourceType.RAW, ""))
    Truth.assertThat(nullRef.id).isNull()
    Truth.assertThat(nullRef.referenceType).isEqualTo(Reference.Type.RESOURCE)
  }

  @Test
  fun testParseEmptyValue() {
    val input = """<integer name="foo">@empty</integer>"""
    Truth.assertThat(testParse(input)).isTrue()

    val integer = getValue("integer/foo") as? BinaryPrimitive
    Truth.assertThat(integer).isNotNull()
    integer!!
    Truth.assertThat(integer.resValue.dataType).isEqualTo(ResValue.DataType.NULL)
    Truth.assertThat(integer.resValue.data).isEqualTo(ResValue.NullFormat.EMPTY)
  }
}
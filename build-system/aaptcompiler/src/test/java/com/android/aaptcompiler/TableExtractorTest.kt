package com.android.aaptcompiler

import com.android.aaptcompiler.android.ResValue
import com.android.aapt.Resources
import com.android.resources.ResourceVisibility
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

  @Test
  fun testParseAttr() {
    val input = """
      <attr name="foo" format="string"/>
      <attr name="bar"/>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isTrue()

    val attr1 = getValue("attr/foo") as? AttributeResource
    Truth.assertThat(attr1).isNotNull()
    attr1!!
    Truth.assertThat(attr1.typeMask).isEqualTo(Resources.Attribute.FormatFlags.STRING_VALUE)

    val attr2 = getValue("attr/bar") as? AttributeResource
    Truth.assertThat(attr2).isNotNull()
    attr2!!
    Truth.assertThat(attr2.typeMask).isEqualTo(Resources.Attribute.FormatFlags.ANY_VALUE)
  }

  // Old AAPT allowed attributes to be defined under different configurations, but ultimately
  // stored them with the default configuration. Check that we have the same behavior.
  @Test
  fun testParseAttrAndDeclareStyleableUnderConfigButRecordAsNoConfig() {
    val watchConfig = parse("watch")
    val input = """
      <attr name="foo" />
      <declare-styleable name="bar">
        <attr name="baz" />
      </declare-styleable>
    """.trimIndent()
    Truth.assertThat(testParse(input, watchConfig)).isTrue()

    Truth.assertThat(getValue("attr/foo", watchConfig)).isNull()
    Truth.assertThat(getValue("attr/baz", watchConfig)).isNull()
    Truth.assertThat(getValue("styleable/bar", watchConfig)).isNull()

    Truth.assertThat(getValue("attr/foo")).isNotNull()
    Truth.assertThat(getValue("attr/baz")).isNotNull()
    Truth.assertThat(getValue("styleable/bar")).isNotNull()
  }

  @Test
  fun testParseAttrWithMinMax() {
    val input = """<attr name="foo" min="10" max="23" format="integer"/>"""
    Truth.assertThat(testParse(input)).isTrue()

    val attr = getValue("attr/foo") as? AttributeResource
    Truth.assertThat(attr).isNotNull()
    attr!!
    Truth.assertThat(attr.typeMask).isEqualTo(Resources.Attribute.FormatFlags.INTEGER_VALUE)
    Truth.assertThat(attr.minInt).isEqualTo(10)
    Truth.assertThat(attr.maxInt).isEqualTo(23)
  }

  @Test
  fun failToParseAttrWithMinMaxButNotInteger() {
    val input = """<attr name="foo" min="10" max="23" format="string"/>"""
    Truth.assertThat(testParse(input)).isFalse()
  }

  @Test
  fun testParseUseAndDeclarationOfAttr() {
    val input = """
      <declare-styleable name="Styleable">
        <attr name="foo" />
      </declare-styleable>
      <attr name="foo" format="string"/>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isTrue()

    val attr = getValue("attr/foo") as? AttributeResource
    Truth.assertThat(attr).isNotNull()
    attr!!
    Truth.assertThat(attr.typeMask).isEqualTo(Resources.Attribute.FormatFlags.STRING_VALUE)
  }

  @Test
  fun testParseDoubleUseOfAttr() {
    val input = """
      <declare-styleable name="Theme">
        <attr name="foo" />
      </declare-styleable>
      <declare-styleable name="Window">
        <attr name="foo" format="boolean"/>
      </declare-styleable>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isTrue()

    val attr = getValue("attr/foo") as? AttributeResource
    Truth.assertThat(attr).isNotNull()
    attr!!
    Truth.assertThat(attr.typeMask).isEqualTo(Resources.Attribute.FormatFlags.BOOLEAN_VALUE)
  }

  @Test
  fun testParseEnumAttr() {
    val input = """
      <attr name="foo">
        <enum name="bar" value="0"/>
        <enum name="bat" value="1"/>
        <enum name="baz" value="2"/>
      </attr>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isTrue()

    val attr = getValue("attr/foo") as? AttributeResource
    Truth.assertThat(attr).isNotNull()
    attr!!
    Truth.assertThat(attr.typeMask).isEqualTo(Resources.Attribute.FormatFlags.ENUM_VALUE)
    Truth.assertThat(attr.symbols).hasSize(3)

    val symbol0 = attr.symbols[0]
    Truth.assertThat(symbol0.symbol.name.entry).isEqualTo("bar")
    Truth.assertThat(symbol0.value).isEqualTo(0)

    val symbol1 = attr.symbols[1]
    Truth.assertThat(symbol1.symbol.name.entry).isEqualTo("bat")
    Truth.assertThat(symbol1.value).isEqualTo(1)

    val symbol2 = attr.symbols[2]
    Truth.assertThat(symbol2.symbol.name.entry).isEqualTo("baz")
    Truth.assertThat(symbol2.value).isEqualTo(2)
  }

  @Test
  fun testParseFlagAttr() {
    val input = """
      <attr name="foo">
        <flag name="bar" value="0"/>
        <flag name="bat" value="1"/>
        <flag name="baz" value="2"/>
      </attr>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isTrue()

    val attr = getValue("attr/foo") as? AttributeResource
    Truth.assertThat(attr).isNotNull()
    attr!!
    Truth.assertThat(attr.typeMask).isEqualTo(Resources.Attribute.FormatFlags.FLAGS_VALUE)
    Truth.assertThat(attr.symbols).hasSize(3)

    val symbol0 = attr.symbols[0]
    Truth.assertThat(symbol0.symbol.name.entry).isEqualTo("bar")
    Truth.assertThat(symbol0.value).isEqualTo(0)

    val symbol1 = attr.symbols[1]
    Truth.assertThat(symbol1.symbol.name.entry).isEqualTo("bat")
    Truth.assertThat(symbol1.value).isEqualTo(1)

    val symbol2 = attr.symbols[2]
    Truth.assertThat(symbol2.symbol.name.entry).isEqualTo("baz")
    Truth.assertThat(symbol2.value).isEqualTo(2)

    val flagValue = tryParseFlagSymbol(attr, "baz | bat")
    Truth.assertThat(flagValue).isNotNull()
    flagValue!!
    Truth.assertThat(flagValue.resValue.data).isEqualTo(1 or 2)
  }

  @Test
  fun failParseEnumNonUniqueKeys() {
    val input = """
      <attr name="foo">
        <enum name="bar" value="0"/>
        <enum name="bat" value="1"/>
        <enum name="bat" value="2"/>
      </attr>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isFalse()
  }

  @Test
  fun testParseStyle() {
    val input = """
      <style name="foo" parent="@style/fu">
        <item name="bar">#ffffffff</item>
        <item name="bat">@string/hey</item>
        <item name="baz"><b>hey</b></item>
      </style>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isTrue()

    val style = getValue("style/foo") as? Style
    Truth.assertThat(style).isNotNull()
    style!!
    Truth.assertThat(style.parent).isNotNull()
    Truth.assertThat(style.parent!!.name)
      .isEqualTo(parseResourceName("style/fu")!!.resourceName)
    Truth.assertThat(style.entries).hasSize(3)

    Truth.assertThat(style.entries[0].key.name)
      .isEqualTo(parseResourceName("attr/bar")!!.resourceName)
    Truth.assertThat(style.entries[1].key.name)
      .isEqualTo(parseResourceName("attr/bat")!!.resourceName)
    Truth.assertThat(style.entries[2].key.name)
      .isEqualTo(parseResourceName("attr/baz")!!.resourceName)
  }

  @Test
  fun testParseStyleWithShorthandParent() {
    Truth.assertThat(testParse("""<style name="foo" parent="com.app:Theme"/>""")).isTrue()

    val style = getValue("style/foo") as? Style
    Truth.assertThat(style).isNotNull()
    style!!
    Truth.assertThat(style.parent).isNotNull()
    Truth.assertThat(style.parent!!.name)
      .isEqualTo(parseResourceName("com.app:style/Theme")!!.resourceName)
  }

  @Test
  fun testParseStyleWithPackageAliasedParent() {
    val input = """
      <style xmlns:app="http://schemas.android.com/apk/res/android"
          name="foo" parent="app:Theme"/>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isTrue()

    val style = getValue("style/foo") as? Style
    Truth.assertThat(style).isNotNull()
    style!!
    Truth.assertThat(style.parent).isNotNull()
    Truth.assertThat(style.parent!!.name)
      .isEqualTo(parseResourceName("android:style/Theme")!!.resourceName)
  }

  @Test
  fun testParseStyleWithPackageAliasedItems() {
    val input = """
      <style xmlns:app="http://schemas.android.com/apk/res/android" name="foo">
        <item name="app:bar">0</item>
      </style>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isTrue()

    val style = getValue("style/foo") as? Style
    Truth.assertThat(style).isNotNull()
    style!!
    Truth.assertThat(style.entries).hasSize(1)
    Truth.assertThat(style.entries[0].key.name)
      .isEqualTo(parseResourceName("android:attr/bar")!!.resourceName)
  }

  @Test
  fun testParseStyleWithInferredParent() {
    Truth.assertThat(testParse("""<style name="foo.bar"/>""")).isTrue()

    val style = getValue("style/foo.bar") as? Style
    Truth.assertThat(style).isNotNull()
    style!!
    Truth.assertThat(style.parent).isNotNull()
    Truth.assertThat(style.parentInferred).isTrue()
    Truth.assertThat(style.parent!!.name)
      .isEqualTo(parseResourceName("style/foo")!!.resourceName)
  }

  @Test
  fun testParseStyleWithOverwrittenInferredParent() {
    Truth.assertThat(testParse("""<style name="foo.bar" parent=""/>""")).isTrue()

    val style = getValue("style/foo.bar") as? Style
    Truth.assertThat(style).isNotNull()
    style!!
    Truth.assertThat(style.parent).isNull()
    Truth.assertThat(style.parentInferred).isFalse()
  }

  @Test
  fun testParseStyleWithPrivateParent() {
    Truth.assertThat(
      testParse("""<style name="foo" parent="*android:style/bar" />""")).isTrue()

    val style = getValue("style/foo") as? Style
    Truth.assertThat(style).isNotNull()
    style!!
    Truth.assertThat(style.parent).isNotNull()
    Truth.assertThat(style.parent!!.isPrivate).isTrue()
  }

  @Test
  fun testParseAutoGeneratedId() {
    Truth.assertThat(testParse("""<string name="foo">@+id/bar</string>""")).isTrue()
    Truth.assertThat(getValue("id/bar")).isNotNull()
  }

  @Test
  fun testParseAttributesInDeclareStyleable() {
    val input = """
      <declare-styleable name="foo">
        <attr name="bar" />
        <attr name="bat" format="string|reference"/>
        <attr name="baz">
          <enum name="foo" value="1"/>
        </attr>
      </declare-styleable>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isTrue()

    val tableResult = table.findResource(parseResourceName("styleable/foo")!!.resourceName)
    Truth.assertThat(tableResult).isNotNull()
    Truth.assertThat(tableResult!!.entry.visibility.level).isEqualTo(ResourceVisibility.PUBLIC)

    val attr1 = getValue("attr/bar") as? AttributeResource
    Truth.assertThat(attr1).isNotNull()
    Truth.assertThat(attr1!!.weak).isTrue()

    val attr2 = getValue("attr/bat") as? AttributeResource
    Truth.assertThat(attr2).isNotNull()
    Truth.assertThat(attr2!!.weak).isTrue()

    val attr3 = getValue("attr/baz") as? AttributeResource
    Truth.assertThat(attr3).isNotNull()
    Truth.assertThat(attr3!!.weak).isTrue()
    Truth.assertThat(attr3.symbols).hasSize(1)

    Truth.assertThat(getValue("id/foo")).isNotNull()

    val styleable = getValue("styleable/foo") as? Styleable
    Truth.assertThat(styleable).isNotNull()
    styleable!!
    Truth.assertThat(styleable.entries).hasSize(3)

    Truth.assertThat(styleable.entries[0].name)
      .isEqualTo(parseResourceName("attr/bar")!!.resourceName)
    Truth.assertThat(styleable.entries[1].name)
      .isEqualTo(parseResourceName("attr/bat")!!.resourceName)
    Truth.assertThat(styleable.entries[2].name)
      .isEqualTo(parseResourceName("attr/baz")!!.resourceName)
  }

  @Test
  fun testParsePrivateAttributesDeclareStyleable() {
    val input = """
      <declare-styleable xmlns:privAndroid="http://schemas.android.com/apk/prv/res/android"
          name="foo">
        <attr name="*android:bar" />
        <attr name="privAndroid:bat" />
      </declare-styleable>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isTrue()

    val styleable = getValue("styleable/foo") as? Styleable
    Truth.assertThat(styleable).isNotNull()
    styleable!!
    Truth.assertThat(styleable.entries).hasSize(2)

    val attr0 = styleable.entries[0]
    Truth.assertThat(attr0.isPrivate).isTrue()
    Truth.assertThat(attr0.name.pck).isEqualTo("android")

    val attr1 = styleable.entries[1]
    Truth.assertThat(attr1.isPrivate).isTrue()
    Truth.assertThat(attr1.name.pck).isEqualTo("android")
  }

  @Test
  fun testParseArray() {
    val input = """
      <array name="foo">
        <item>@string/ref</item>
        <item>hey</item>
        <item>23</item>
      </array>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isTrue()

    val array = getValue("array/foo") as? ArrayResource
    Truth.assertThat(array).isNotNull()
    array!!
    Truth.assertThat(array.elements).hasSize(3)

    val item0 = array.elements[0] as? Reference
    Truth.assertThat(item0).isNotNull()
    val item1 = array.elements[1] as? BasicString
    Truth.assertThat(item1).isNotNull()
    val item2 = array.elements[2] as? BinaryPrimitive
    Truth.assertThat(item2).isNotNull()
  }

  @Test
  fun testParseStringArray() {
    val input = """
      <string-array name="foo">
        <item>"Werk"</item>"
      </string-array>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isTrue()

    val array = getValue("array/foo") as? ArrayResource
    Truth.assertThat(array).isNotNull()
    Truth.assertThat(array!!.elements).hasSize(1)
  }

  @Test
  fun testParseArrayWithFormat() {
    val input = """
      <array name="foo" format="string">
        <item>100</item>
      </array>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isTrue()

    val array = getValue("array/foo") as? ArrayResource
    Truth.assertThat(array).isNotNull()
    array!!
    Truth.assertThat(array.elements).hasSize(1)

    val str = array.elements[0] as? BasicString
    Truth.assertThat(str).isNotNull()
    Truth.assertThat(str!!.toString()).isEqualTo("100")
  }

  @Test
  fun testParseArrayWithBadFormat() {
    val input = """
      <array name="foo" format="integer">
        <item>Hi</item>
      </array>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isFalse()
  }

  @Test
  fun testParsePlural() {
    val input = """
      <plurals name="foo">
        <item quantity="other">apples</item>
        <item quantity="one">apple</item>
      </plurals>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isTrue()

    val plural = getValue("plurals/foo") as? Plural
    Truth.assertThat(plural).isNotNull()
    plural!!
    Truth.assertThat(plural.values[Plural.Type.ZERO.ordinal]).isNull()
    Truth.assertThat(plural.values[Plural.Type.TWO.ordinal]).isNull()
    Truth.assertThat(plural.values[Plural.Type.FEW.ordinal]).isNull()
    Truth.assertThat(plural.values[Plural.Type.MANY.ordinal]).isNull()

    Truth.assertThat(plural.values[Plural.Type.ONE.ordinal]).isNotNull()
    Truth.assertThat(plural.values[Plural.Type.OTHER.ordinal]).isNotNull()
  }

  @Test
  fun testParseCommentsWithResource() {
    val input = """
      <!--This is a comment-->
      <string name="foo">Hi</string>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isTrue()

    val value = getValue("string/foo") as? BasicString
    Truth.assertThat(value).isNotNull()
    Truth.assertThat(value!!.comment).isEqualTo("This is a comment")
  }

  @Test
  fun testDoNotCombineMultipleComments() {
    val input = """
      <!--One-->
      <!--Two-->
      <string name="foo">Hi</string>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isTrue()

    val value = getValue("string/foo") as? BasicString
    Truth.assertThat(value).isNotNull()
    Truth.assertThat(value!!.comment).isEqualTo("Two")
  }

  @Test
  fun testIgnoreCommentBeforeEndTag() {
    val input = """
      <!--One-->
      <string name="foo">
        Hi
      <!--Two-->
      </string>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isTrue()

    val value = getValue("string/foo") as? BasicString
    Truth.assertThat(value).isNotNull()
    Truth.assertThat(value!!.comment).isEqualTo("One")
  }

  @Test
  fun testParseNestedComments() {
    // We only care about declare-styleable and enum/flag attributes because comments from those end
    // up in R.java
    val input = """
      <declare-styleable name="foo">
        <!-- The name of the bar -->
        <attr name="barName" format="string|reference" />
      </declare-styleable>

      <attr name="foo">
        <!-- The very first -->
        <enum name="one" value="1" />
      </attr>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isTrue()

    val styleable = getValue("styleable/foo") as? Styleable
    Truth.assertThat(styleable).isNotNull()
    Truth.assertThat(styleable!!.entries).hasSize(1)
    Truth.assertThat(styleable.entries[0].comment).isEqualTo("The name of the bar")

    val attr = getValue("attr/foo") as? AttributeResource
    Truth.assertThat(attr).isNotNull()
    Truth.assertThat(attr!!.symbols).hasSize(1)
    Truth.assertThat(attr.symbols[0].symbol.comment).isEqualTo("The very first")
  }

  @Test
  fun testParsePublicIdAsDefinition() {
    // Declaring an id as public should not require a separate definition (as an id has no value)
    Truth.assertThat(testParse("""<public type="id" name="foo"/>""")).isTrue()
    Truth.assertThat(getValue("id/foo") as? Id).isNotNull()
  }

  @Test
  fun testKeepAllProducts() {
    val input = """
      <string name="foo" product="phone">hi</string>
      <string name="foo" product="no-sdcard">ho</string>
      <string name="bar" product="">wee</string>
      <string name="baz">woo</string>
      <string name="bit" product="phablet">hoot</string>
      <string name="bot" product="default">yes</string>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isTrue()

    Truth.assertThat(getValue("string/foo", productName = "phone") as? BasicString)
      .isNotNull()
    Truth.assertThat(getValue("string/foo", productName = "no-sdcard") as? BasicString)
      .isNotNull()
    Truth.assertThat(getValue("string/bar") as? BasicString).isNotNull()
    Truth.assertThat(getValue("string/baz") as? BasicString).isNotNull()
    Truth.assertThat(getValue("string/bit", productName = "phablet") as? BasicString)
      .isNotNull()
    Truth.assertThat(getValue("string/bot", productName = "default") as? BasicString)
      .isNotNull()
  }

  @Test
  fun testAutoIncrementIdsInPublicGroup() {
    val input = """
      <public-group type="attr" first-id="0x01010040">
        <public name="foo" />
        <public name="bar" />
      </public-group>
    """.trimIndent()
    Truth.assertThat(testParse(input)).isTrue()

    val tableResult0 = table.findResource(parseResourceName("attr/foo")!!.resourceName)
    Truth.assertThat(tableResult0).isNotNull()
    tableResult0!!
    Truth.assertThat(tableResult0.tablePackage.id).isNotNull()
    Truth.assertThat(tableResult0.group.id).isNotNull()
    Truth.assertThat(tableResult0.entry.id).isNotNull()
    val actualId0 = resourceIdFromParts(
      tableResult0.tablePackage.id!!,
      tableResult0.group.id!!,
      tableResult0.entry.id!!)
    Truth.assertThat(actualId0).isEqualTo(0x01010040)

    val tableResult1 = table.findResource(parseResourceName("attr/bar")!!.resourceName)
    Truth.assertThat(tableResult1).isNotNull()
    tableResult1!!
    Truth.assertThat(tableResult1.tablePackage.id).isNotNull()
    Truth.assertThat(tableResult1.group.id).isNotNull()
    Truth.assertThat(tableResult1.entry.id).isNotNull()
    val actualId1 = resourceIdFromParts(
      tableResult1.tablePackage.id!!,
      tableResult1.group.id!!,
      tableResult1.entry.id!!)
    Truth.assertThat(actualId1).isEqualTo(0x01010041)
  }

  @Test
  fun testStrongestSymbolVisibilityWins() {
    val input = """
      <!-- private -->
      <java-symbol type="string" name="foo" />
      <!-- public -->
      <public type="string" name="foo" id="0x01020000" />
      <!-- private2 -->
      <java-symbol type="string" name="foo" />
    """.trimIndent()
    Truth.assertThat(testParse(input)).isTrue()

    val tableResult = table.findResource(parseResourceName("string/foo")!!.resourceName)
    Truth.assertThat(tableResult).isNotNull()

    val entry = tableResult!!.entry
    Truth.assertThat(entry.visibility.level).isEqualTo(ResourceVisibility.PUBLIC)
    Truth.assertThat(entry.visibility.comment).isEqualTo("public")
  }

  @Test
  fun testExternalTypesShouldBeReferences() {
    Truth.assertThat(
      testParse("""<item type="layout" name="foo">@layout/bar</item>""")).isTrue()
    Truth.assertThat(
      testParse("""<item type="layout" name="bar">"this is a string"</item>""")).isFalse()
  }

  @Test
  fun testAddResourcesElementShouldAddEntryWithUndefinedSymbol() {
    Truth.assertThat(testParse("""<add-resource name="bar" type="string" />""")).isTrue()

    val tableResult = table.findResource(parseResourceName("string/bar")!!.resourceName)
    Truth.assertThat(tableResult).isNotNull()
    val entry = tableResult!!.entry
    Truth.assertThat(entry.visibility.level).isEqualTo(ResourceVisibility.UNDEFINED)
    Truth.assertThat(entry.allowNew).isNotNull()
  }

  @Test
  fun testParseItemElementWithFormat() {
    Truth.assertThat(
      testParse("""<item name="foo" type="integer" format="float">0.3</item>""")).isTrue()

    val primitive = getValue("integer/foo") as? BinaryPrimitive
    Truth.assertThat(primitive).isNotNull()
    Truth.assertThat(primitive!!.resValue.dataType).isEqualTo(ResValue.DataType.FLOAT)

    Truth.assertThat(
      testParse("""<item name="bar" type="integer" format="fraction">100</item>""")).isFalse()
  }
}
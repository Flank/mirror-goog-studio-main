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

  private fun processTest(input: String, configDescription: String = ""): ResourceFile? {
    val file = ResourceFile(
      parseNameOrFail("layout/test"),
      parse(configDescription),
      Source(""),
      ResourceFile.Type.ProtoXml)
    if (!processor.process(file, input.byteInputStream())) {
      return null
    }
    return file
  }

  private fun getAttrName(inputFile: ResourceFile, attrNum: Int): ResourceName =
    inputFile.name.copy(entry = "${inputFile.name.pck}${'$'}${inputFile.name.entry}__$attrNum")

  @Test
  fun testEmpty() {
    Truth.assertThat(processTest("")).isNotNull()
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

    Truth.assertThat(processTest(input)).isNotNull()

    val collectedIds = processor.primaryFile.exportedSymbols
    Truth.assertThat(collectedIds).hasSize(3)

    Truth.assertThat(collectedIds[0]).isEqualTo(SourcedResourceName(parseNameOrFail("id/bar"), 3))
    Truth.assertThat(collectedIds[1]).isEqualTo(SourcedResourceName(parseNameOrFail("id/car"), 6))
    Truth.assertThat(collectedIds[2]).isEqualTo(SourcedResourceName(parseNameOrFail("id/foo"), 3))
  }

  @Test
  fun testNoCollectNonIds() {
    Truth.assertThat(processTest("""<View foo="@+string/foo"/>""")).isNotNull()

    Truth.assertThat(processor.primaryFile.exportedSymbols).isEmpty()

    val input = """
      <View xmlns:android="http://schemas.android.com/apk/res/android"
        android:id="@+id/foo"
        text="@+string/bar"/>
    """.trimIndent()
    Truth.assertThat(processTest(input)).isNotNull()

    Truth.assertThat(processor.primaryFile.exportedSymbols).hasSize(1)
    Truth.assertThat(processor.primaryFile.exportedSymbols[0])
      .isEqualTo(SourcedResourceName(parseNameOrFail("id/foo"), 3))
  }

  @Test
  fun failOnInvalidIds() {
    Truth.assertThat(processTest("""<View foo="@+id/foo${'$'}bar"/>""")).isNull()
  }

  @Test
  fun testNoInlineXml() {
    val input = """
      <View xmlns:android="http://schemas.android.com/apk/res/android">
        <View android:text="hey">
          <View android:id="hi" />
        </View>
      </View>
    """.trimIndent()

    val inputFile = processTest(input)
    Truth.assertThat(inputFile).isNotNull()

    Truth.assertThat(processor.xmlResources).hasSize(1)

    val proto = processor.xmlResources[0].xmlProto
    Truth.assertThat(proto).isNotNull()
    Truth.assertThat(proto.hasElement()).isTrue()

    val element = proto.getElement()
    Truth.assertThat(element.getName()).isEqualTo("View")
    Truth.assertThat(element.getNamespaceDeclarationList()).hasSize(1)
    Truth.assertThat(element.getAttributeList()).hasSize(0)
    Truth.assertThat(element.getChildList()).hasSize(1)

    val namespace = element.getNamespaceDeclarationList()[0]
    Truth.assertThat(namespace.getUri()).isEqualTo("http://schemas.android.com/apk/res/android")
    Truth.assertThat(namespace.getPrefix()).isEqualTo("android")

    val childNode = element.getChildList()[0]
    Truth.assertThat(childNode.hasElement()).isTrue()

    val child = childNode.getElement()
    Truth.assertThat(child.getName()).isEqualTo("View")
    Truth.assertThat(child.getNamespaceDeclarationList()).hasSize(0)
    Truth.assertThat(child.getAttributeList()).hasSize(1)
    Truth.assertThat(child.getChildList()).hasSize(1)

    val childAttr = child.getAttributeList()[0]
    Truth.assertThat(childAttr.getNamespaceUri())
      .isEqualTo("http://schemas.android.com/apk/res/android")
    Truth.assertThat(childAttr.getName()).isEqualTo("text")
    Truth.assertThat(childAttr.getValue()).isEqualTo("hey")

    val grandchildNode = child.getChildList()[0]
    Truth.assertThat(grandchildNode.hasElement()).isTrue()

    val grandchild = grandchildNode.getElement()
    Truth.assertThat(grandchild.getName()).isEqualTo("View")
    Truth.assertThat(grandchild.getNamespaceDeclarationList()).hasSize(0)
    Truth.assertThat(grandchild.getAttributeList()).hasSize(1)
    Truth.assertThat(grandchild.getChildList()).hasSize(0)

    val grandchildAttr = grandchild.getAttributeList()[0]
    Truth.assertThat(grandchildAttr.getNamespaceUri())
      .isEqualTo("http://schemas.android.com/apk/res/android")
    Truth.assertThat(grandchildAttr.getName()).isEqualTo("id")
    Truth.assertThat(grandchildAttr.getValue()).isEqualTo("hi")
  }

  @Test
  fun testExtractOneXmlResource() {
    val input = """
      <View1 xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:aapt="http://schemas.android.com/aapt">
        <aapt:attr name="android:text">
          <View2 android:text="hey">
            <View3 android:id="hi" />
          </View2>
        </aapt:attr>
      </View1>
    """.trimIndent()

    val inputFile = processTest(input)
    Truth.assertThat(inputFile).isNotNull()

    Truth.assertThat(processor.xmlResources).hasSize(2)

    val proto = processor.xmlResources[0].xmlProto
    Truth.assertThat(proto).isNotNull()
    Truth.assertThat(proto.hasElement()).isTrue()

    val element = proto.getElement()
    Truth.assertThat(element.getName()).isEqualTo("View1")
    Truth.assertThat(element.getNamespaceDeclarationList()).hasSize(2)
    Truth.assertThat(element.getAttributeList()).hasSize(1)
    Truth.assertThat(element.getChildList()).hasSize(0)

    val namespace1 = element.getNamespaceDeclarationList()[0]
    Truth.assertThat(namespace1.getUri()).isEqualTo("http://schemas.android.com/apk/res/android")
    Truth.assertThat(namespace1.getPrefix()).isEqualTo("android")

    val namespace2 = element.getNamespaceDeclarationList()[1]
    Truth.assertThat(namespace2.getUri()).isEqualTo("http://schemas.android.com/aapt")
    Truth.assertThat(namespace2.getPrefix()).isEqualTo("aapt")

    // the aapt:attr should be pulled out as an attribute.
    // i.e. 'android:text="@layout/$test__0"'
    val attr = element.getAttributeList()[0]
    Truth.assertThat(attr.getNamespaceUri()).isEqualTo("http://schemas.android.com/apk/res/android")
    Truth.assertThat(attr.getName()).isEqualTo("text")
    Truth.assertThat(attr.getValue()).isEqualTo("@${getAttrName(inputFile!!, 0)}")

    val outlinedProto = processor.xmlResources[1].xmlProto
    Truth.assertThat(outlinedProto).isNotNull()
    Truth.assertThat(outlinedProto.hasElement()).isTrue()

    val outlinedElement = outlinedProto.getElement()
    Truth.assertThat(outlinedElement.getName()).isEqualTo("View2")
    // The outlined element inherits all active namespace declarations.
    Truth.assertThat(outlinedElement.getNamespaceDeclarationList()).hasSize(2)
    Truth.assertThat(outlinedElement.getAttributeList()).hasSize(1)
    Truth.assertThat(outlinedElement.getChildList()).hasSize(1)

    val outlinedNamespace1 = outlinedElement.getNamespaceDeclarationList()[0]
    Truth.assertThat(outlinedNamespace1.getUri())
      .isEqualTo("http://schemas.android.com/apk/res/android")
    Truth.assertThat(outlinedNamespace1.getPrefix()).isEqualTo("android")

    val outlinedNamespace2 = outlinedElement.getNamespaceDeclarationList()[1]
    Truth.assertThat(outlinedNamespace2.getUri()).isEqualTo("http://schemas.android.com/aapt")
    Truth.assertThat(outlinedNamespace2.getPrefix()).isEqualTo("aapt")

    val outlinedAttr = outlinedElement.getAttributeList()[0]
    Truth.assertThat(outlinedAttr.getNamespaceUri())
      .isEqualTo("http://schemas.android.com/apk/res/android")
    Truth.assertThat(outlinedAttr.getName()).isEqualTo("text")
    Truth.assertThat(outlinedAttr.getValue()).isEqualTo("hey")

    val outlinedChildNode = outlinedElement.getChildList()[0]
    Truth.assertThat(outlinedChildNode.hasElement()).isTrue()

    val outlinedChild = outlinedChildNode.getElement()
    Truth.assertThat(outlinedChild.getName()).isEqualTo("View3")
    Truth.assertThat(outlinedChild.getNamespaceDeclarationList()).hasSize(0)
    Truth.assertThat(outlinedChild.getAttributeList()).hasSize(1)
    Truth.assertThat(outlinedChild.getChildList()).hasSize(0)

    val outlinedChildAttr = outlinedChild.getAttributeList()[0]
    Truth.assertThat(outlinedChildAttr.getNamespaceUri())
      .isEqualTo("http://schemas.android.com/apk/res/android")
    Truth.assertThat(outlinedChildAttr.getName()).isEqualTo("id")
    Truth.assertThat(outlinedChildAttr.getValue()).isEqualTo("hi")
  }

  @Test
  fun testExtractTwoSiblingResources() {
    val input = """
      <View1 xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:aapt="http://schemas.android.com/aapt">
        <aapt:attr name="android:text">
          <View2 android:text="hey">
            <View3 android:id="hi" />
          </View2>
        </aapt:attr>

        <aapt:attr name="android:drawable">
          <vector />
        </aapt:attr>
      </View1>
    """.trimIndent()

    val inputFile = processTest(input)
    Truth.assertThat(inputFile).isNotNull()

    Truth.assertThat(processor.xmlResources).hasSize(3)

    val proto = processor.xmlResources[0].xmlProto
    Truth.assertThat(proto).isNotNull()
    Truth.assertThat(proto.hasElement()).isTrue()

    val element = proto.getElement()
    Truth.assertThat(element.getName()).isEqualTo("View1")
    Truth.assertThat(element.getNamespaceDeclarationList()).hasSize(2)
    Truth.assertThat(element.getAttributeList()).hasSize(2)
    Truth.assertThat(element.getChildList()).hasSize(0)

    val namespace1 = element.getNamespaceDeclarationList()[0]
    Truth.assertThat(namespace1.getUri()).isEqualTo("http://schemas.android.com/apk/res/android")
    Truth.assertThat(namespace1.getPrefix()).isEqualTo("android")

    val namespace2 = element.getNamespaceDeclarationList()[1]
    Truth.assertThat(namespace2.getUri()).isEqualTo("http://schemas.android.com/aapt")
    Truth.assertThat(namespace2.getPrefix()).isEqualTo("aapt")

    // the aapt:attr should be pulled out as an attribute.
    // i.e. 'android:text="@layout/$test__0"'
    val attr1 = element.getAttributeList()[0]
    Truth.assertThat(attr1.getNamespaceUri())
      .isEqualTo("http://schemas.android.com/apk/res/android")
    Truth.assertThat(attr1.getName()).isEqualTo("text")
    Truth.assertThat(attr1.getValue()).isEqualTo("@${getAttrName(inputFile!!, 0)}")

    // The second should be pulled out as well.
    // i.e. 'android:drawable="@layout/$test__1"'
    val attr2 = element.getAttributeList()[1]
    Truth.assertThat(attr2.getNamespaceUri())
      .isEqualTo("http://schemas.android.com/apk/res/android")
    Truth.assertThat(attr2.getName()).isEqualTo("drawable")
    Truth.assertThat(attr2.getValue()).isEqualTo("@${getAttrName(inputFile, 1)}")

    val outlinedProto = processor.xmlResources[1].xmlProto
    Truth.assertThat(outlinedProto).isNotNull()
    Truth.assertThat(outlinedProto.hasElement()).isTrue()

    val outlinedElement = outlinedProto.getElement()
    Truth.assertThat(outlinedElement.getName()).isEqualTo("View2")
    // The outlined element inherits all active namespace declarations.
    Truth.assertThat(outlinedElement.getNamespaceDeclarationList()).hasSize(2)
    Truth.assertThat(outlinedElement.getAttributeList()).hasSize(1)
    Truth.assertThat(outlinedElement.getChildList()).hasSize(1)

    val outlinedNamespace1 = outlinedElement.getNamespaceDeclarationList()[0]
    Truth.assertThat(outlinedNamespace1.getUri())
      .isEqualTo("http://schemas.android.com/apk/res/android")
    Truth.assertThat(outlinedNamespace1.getPrefix()).isEqualTo("android")

    val outlinedNamespace2 = outlinedElement.getNamespaceDeclarationList()[1]
    Truth.assertThat(outlinedNamespace2.getUri()).isEqualTo("http://schemas.android.com/aapt")
    Truth.assertThat(outlinedNamespace2.getPrefix()).isEqualTo("aapt")

    val outlinedAttr = outlinedElement.getAttributeList()[0]
    Truth.assertThat(outlinedAttr.getNamespaceUri())
      .isEqualTo("http://schemas.android.com/apk/res/android")
    Truth.assertThat(outlinedAttr.getName()).isEqualTo("text")
    Truth.assertThat(outlinedAttr.getValue()).isEqualTo("hey")

    val outlinedChildNode = outlinedElement.getChildList()[0]
    Truth.assertThat(outlinedChildNode.hasElement()).isTrue()

    val outlinedChild = outlinedChildNode.getElement()
    Truth.assertThat(outlinedChild.getName()).isEqualTo("View3")
    Truth.assertThat(outlinedChild.getNamespaceDeclarationList()).hasSize(0)
    Truth.assertThat(outlinedChild.getAttributeList()).hasSize(1)
    Truth.assertThat(outlinedChild.getChildList()).hasSize(0)

    val outlinedChildAttr = outlinedChild.getAttributeList()[0]
    Truth.assertThat(outlinedChildAttr.getNamespaceUri())
      .isEqualTo("http://schemas.android.com/apk/res/android")
    Truth.assertThat(outlinedChildAttr.getName()).isEqualTo("id")
    Truth.assertThat(outlinedChildAttr.getValue()).isEqualTo("hi")

    val outlinedProto2 = processor.xmlResources[2].xmlProto
    Truth.assertThat(outlinedProto2).isNotNull()
    Truth.assertThat(outlinedProto2.hasElement()).isTrue()

    val outlined2Element = outlinedProto2.getElement()
    Truth.assertThat(outlined2Element.getName()).isEqualTo("vector")
    Truth.assertThat(outlined2Element.getNamespaceDeclarationList()).hasSize(2)
    Truth.assertThat(outlined2Element.getAttributeList()).hasSize(0)
    Truth.assertThat(outlined2Element.getChildList()).hasSize(0)

    val outlined2Namespace1 = outlinedElement.getNamespaceDeclarationList()[0]
    Truth.assertThat(outlined2Namespace1.getUri())
      .isEqualTo("http://schemas.android.com/apk/res/android")
    Truth.assertThat(outlined2Namespace1.getPrefix()).isEqualTo("android")

    val outlined2Namespace2 = outlinedElement.getNamespaceDeclarationList()[1]
    Truth.assertThat(outlined2Namespace2.getUri()).isEqualTo("http://schemas.android.com/aapt")
    Truth.assertThat(outlined2Namespace2.getPrefix()).isEqualTo("aapt")
  }

  @Test
  fun testExtractNestedXmlResource() {
    val input = """
      <base_root xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:aapt="http://schemas.android.com/aapt">
          <aapt:attr name="inline_xml">
              <inline_root>
                  <aapt:attr name="nested_inline_xml">
                      <nested_inline_root/>
                  </aapt:attr>
                  <aapt:attr name="another_nested_inline_xml">
                      <root/>
                  </aapt:attr>
              </inline_root>
          </aapt:attr>
          <aapt:attr name="turtles">
              <root1>
                  <aapt:attr name="all">
                      <root2>
                          <aapt:attr name="the">
                              <root3>
                                  <aapt:attr name="way">
                                      <root4>
                                          <aapt:attr name="down">
                                              <root5/>
                                          </aapt:attr>
                                      </root4>
                                  </aapt:attr>
                              </root3>
                          </aapt:attr>
                      </root2>
                  </aapt:attr>
              </root1>
          </aapt:attr>
      </base_root>
    """.trimIndent()

    val inputFile = processTest(input)
    Truth.assertThat(inputFile).isNotNull()

    // The primary file and the 8 outlined aapt:attr's
    Truth.assertThat(processor.xmlResources).hasSize(9)
  }

  @Test
  fun testExtractIntoAppAttr() {
    val input = """
      <parent xmlns:app="http://schemas.android.com/apk/res-auto"
          xmlns:aapt="http://schemas.android.com/aapt">
        <aapt:attr name="app:foo">
            <child />
        </aapt:attr>
      </parent>
    """.trimIndent()

    val inputFile = processTest(input)
    Truth.assertThat(inputFile).isNotNull()

    Truth.assertThat(processor.xmlResources).hasSize(2)

    val proto = processor.xmlResources[0].xmlProto
    Truth.assertThat(proto).isNotNull()
    Truth.assertThat(proto.hasElement()).isTrue()

    val element = proto.getElement()
    Truth.assertThat(element.getName()).isEqualTo("parent")
    Truth.assertThat(element.getNamespaceDeclarationList()).hasSize(2)
    Truth.assertThat(element.getAttributeList()).hasSize(1)
    Truth.assertThat(element.getChildList()).hasSize(0)

    val namespace1 = element.getNamespaceDeclarationList()[0]
    Truth.assertThat(namespace1.getUri()).isEqualTo("http://schemas.android.com/apk/res-auto")
    Truth.assertThat(namespace1.getPrefix()).isEqualTo("app")

    val namespace2 = element.getNamespaceDeclarationList()[1]
    Truth.assertThat(namespace2.getUri()).isEqualTo("http://schemas.android.com/aapt")
    Truth.assertThat(namespace2.getPrefix()).isEqualTo("aapt")

    val attr1 = element.getAttributeList()[0]
    Truth.assertThat(attr1.getNamespaceUri())
      .isEqualTo("http://schemas.android.com/apk/res-auto")
    Truth.assertThat(attr1.getName()).isEqualTo("foo")
    Truth.assertThat(attr1.getValue()).isEqualTo("@${getAttrName(inputFile!!, 0)}")
  }

  @Test
  fun testExtractIntoNoNamespaceAttr() {
    val input = """
      <parent xmlns:aapt="http://schemas.android.com/aapt">
        <aapt:attr name="foo">
            <child />
        </aapt:attr>
      </parent>
    """.trimIndent()

    val inputFile = processTest(input)
    Truth.assertThat(inputFile).isNotNull()

    Truth.assertThat(processor.xmlResources).hasSize(2)

    val proto = processor.xmlResources[0].xmlProto
    Truth.assertThat(proto).isNotNull()
    Truth.assertThat(proto.hasElement()).isTrue()

    val element = proto.getElement()
    Truth.assertThat(element.getName()).isEqualTo("parent")
    Truth.assertThat(element.getNamespaceDeclarationList()).hasSize(1)
    Truth.assertThat(element.getAttributeList()).hasSize(1)
    Truth.assertThat(element.getChildList()).hasSize(0)

    val namespace = element.getNamespaceDeclarationList()[0]
    Truth.assertThat(namespace.getUri()).isEqualTo("http://schemas.android.com/aapt")
    Truth.assertThat(namespace.getPrefix()).isEqualTo("aapt")

    val attr1 = element.getAttributeList()[0]
    Truth.assertThat(attr1.getNamespaceUri()).isEqualTo("")
    Truth.assertThat(attr1.getName()).isEqualTo("foo")
    Truth.assertThat(attr1.getValue()).isEqualTo("@${getAttrName(inputFile!!, 0)}")
  }

  @Test
  fun failAttrRoot() {
    val input = """
      <aapt:attr xmlns:aapt="http://schemas.android.com/aapt" name="foo">
        <child/>
      </aapt:attr>
    """.trimIndent()

    Truth.assertThat(processTest(input)).isNull()
  }

  @Test
  fun failConsecutiveNestedAttrDeclarations() {
    val input ="""
      <parent xmlns:aapt="http://schemas.android.com/aapt">
        <aapt:attr name="foo">
          <aapt:attr name="bar">
            <child/>
          </aapt:attr>
        </aapt:attr>
      </parent>
    """.trimIndent()

    Truth.assertThat(processTest(input)).isNull()
  }

  @Test
  fun failToOverwriteExistingAttr() {
    val input = """
      <parent xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:aapt="http://schemas.android.com/aapt"
          android:drawable="@drawable/hi">
        <aapt:attr name="android:drawable">
          <vector/>
        </aapt:attr>
      </parent>
    """.trimIndent()

    Truth.assertThat(processTest(input)).isNull()
  }

  @Test
  fun failMultipleWritesToSameAttr() {
    val input = """
      <View1 xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:aapt="http://schemas.android.com/aapt">
        <aapt:attr name="android:text">
          <View2 android:text="hey">
            <View3 android:id="hi" />
          </View2>
        </aapt:attr>
        <aapt:attr name="android:text">
          <View4 android:text="How's it going?">
            <View5 android:id="How are you?" />
          </View4>
        </aapt:attr>
      </View1>
    """.trimIndent()

    Truth.assertThat(processTest(input)).isNull()
  }

  @Test
  fun failEmptyAttr() {
    val input = """
      <View1 xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:aapt="http://schemas.android.com/aapt">
        <aapt:attr name="android:text">
        </aapt:attr>
      </View1>
    """.trimIndent()

    Truth.assertThat(processTest(input)).isNull()
  }
}

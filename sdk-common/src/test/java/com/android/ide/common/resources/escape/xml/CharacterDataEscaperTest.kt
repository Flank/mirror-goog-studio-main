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
package com.android.ide.common.resources.escape.xml

import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CharacterDataEscaperTest {
  @Test
  fun escape_empty() {
    assertThat(CharacterDataEscaper.escape("")).isEqualTo("")
  }

  @Test
  fun escape_decimalReference() {
    assertThat(CharacterDataEscaper.escape("&#38;")).isEqualTo("&#38;")
  }

  @Test
  fun escape_hexadecimalReference() {
    assertThat(CharacterDataEscaper.escape("&#x26;")).isEqualTo("&#x26;")
  }

  @Test
  fun escape_firstQuestionMark() {
    assertThat(CharacterDataEscaper.escape("???")).isEqualTo("""\???""")
    assertThat(CharacterDataEscaper.escape("""?<xliff:g id="id">?</xliff:g>?"""))
        .isEqualTo("""\?<xliff:g id="id">?</xliff:g>?""")
    assertThat(CharacterDataEscaper.escape("""?<xliff:g id='id'>?</xliff:g>?"""))
        .isEqualTo("""\?<xliff:g id="id">?</xliff:g>?""")
    assertThat(CharacterDataEscaper.escape("""?<![CDATA[?]]>?""")).isEqualTo("""\?<![CDATA[?]]>?""")
  }

  @Test
  fun escape_firstAtSign() {
    assertThat(CharacterDataEscaper.escape("@@@")).isEqualTo("""\@@@""")
    assertThat(CharacterDataEscaper.escape("""@<xliff:g id="id">@</xliff:g>@"""))
        .isEqualTo("""\@<xliff:g id="id">@</xliff:g>@""")
    assertThat(CharacterDataEscaper.escape("""@<xliff:g id='id'>@</xliff:g>@"""))
        .isEqualTo("""\@<xliff:g id="id">@</xliff:g>@""")
    assertThat(CharacterDataEscaper.escape("""@<![CDATA[@]]>@""")).isEqualTo("""\@<![CDATA[@]]>@""")
  }

  @Test
  fun escape_quotationMarks() {
    assertThat(CharacterDataEscaper.escape(""""""")).isEqualTo("""\"""")
    assertThat(CharacterDataEscaper.escape(""""<xliff:g id="id">"</xliff:g>""""))
        .isEqualTo("""\"<xliff:g id="id">\"</xliff:g>\"""")
    assertThat(CharacterDataEscaper.escape(""""<xliff:g id='id'>"</xliff:g>""""))
        .isEqualTo("""\"<xliff:g id="id">\"</xliff:g>\"""")
    assertThat(CharacterDataEscaper.escape(""""<![CDATA["]]>""""))
        .isEqualTo("""\"<![CDATA["]]>\"""")
  }

  @Test
  fun escape_backslashes() {
    assertThat(CharacterDataEscaper.escape("""\""")).isEqualTo("""\\""")
    assertThat(CharacterDataEscaper.escape("""\<xliff:g id="id">\</xliff:g>\"""))
        .isEqualTo("""\\<xliff:g id="id">\\</xliff:g>\\""")
    assertThat(CharacterDataEscaper.escape("""\<xliff:g id='id'>\</xliff:g>\"""))
        .isEqualTo("""\\<xliff:g id="id">\\</xliff:g>\\""")
    assertThat(CharacterDataEscaper.escape("""\<![CDATA[\]]>\"""))
        .isEqualTo("""\\<![CDATA[\]]>\\""")
  }

  @Test
  fun escape_newlines() {
    assertThat(CharacterDataEscaper.escape("""
""")).isEqualTo("""\n""")
    assertThat(CharacterDataEscaper.escape("""
<xliff:g id="id">
</xliff:g>
"""))
        .isEqualTo("""\n<xliff:g id="id">\n</xliff:g>\n""")
    assertThat(CharacterDataEscaper.escape("""
<xliff:g id='id'>
</xliff:g>
"""))
        .isEqualTo("""\n<xliff:g id="id">\n</xliff:g>\n""")
    assertThat(CharacterDataEscaper.escape("""
<![CDATA[
]]>
"""))
        .isEqualTo("""\n<![CDATA[
]]>\n""")
  }

  @Test
  fun escape_tabs() {
    // No raw string literals here as tabs are not allowed in our source.
    assertThat(CharacterDataEscaper.escape("\t")).isEqualTo("\\t")
    assertThat(CharacterDataEscaper.escape("\t<xliff:g id=\"id\">\t</xliff:g>\t"))
        .isEqualTo("\\t<xliff:g id=\"id\">\\t</xliff:g>\\t")
    assertThat(CharacterDataEscaper.escape("\t<xliff:g id='id'>\t</xliff:g>\t"))
        .isEqualTo("\\t<xliff:g id=\"id\">\\t</xliff:g>\\t")
    assertThat(CharacterDataEscaper.escape("\t<![CDATA[\t]]>\t")).isEqualTo("\\t<![CDATA[\t]]>\\t")
  }

  @Test
  fun escape_apostrophesLeadingAndTrailingSpaces() {
    assertThat(CharacterDataEscaper.escape("'")).isEqualTo("""\'""")
    assertThat(CharacterDataEscaper.escape("' ")).isEqualTo(""""' """")
    assertThat(CharacterDataEscaper.escape(" '")).isEqualTo("""" '"""")
    assertThat(CharacterDataEscaper.escape(" ' ")).isEqualTo("""" ' """")
    assertThat(CharacterDataEscaper.escape("""'<xliff:g id="id">'</xliff:g>'"""))
        .isEqualTo("""\'<xliff:g id="id">\'</xliff:g>\'""")
    assertThat(CharacterDataEscaper.escape("""'<xliff:g id="id">'</xliff:g>' """))
        .isEqualTo(""""'<xliff:g id="id">'</xliff:g>' """")
    assertThat(CharacterDataEscaper.escape(""" '<xliff:g id="id">'</xliff:g>'"""))
        .isEqualTo("""" '<xliff:g id="id">'</xliff:g>'"""")
    assertThat(CharacterDataEscaper.escape(""" '<xliff:g id="id">'</xliff:g>' """))
        .isEqualTo("""" '<xliff:g id="id">'</xliff:g>' """")
    assertThat(CharacterDataEscaper.escape("""'<xliff:g id='id'>'</xliff:g>'"""))
        .isEqualTo("""\'<xliff:g id="id">\'</xliff:g>\'""")
    assertThat(CharacterDataEscaper.escape("""'<xliff:g id='id'>'</xliff:g>' """))
        .isEqualTo(""""'<xliff:g id="id">'</xliff:g>' """")
    assertThat(CharacterDataEscaper.escape(""" '<xliff:g id='id'>'</xliff:g>'"""))
        .isEqualTo("""" '<xliff:g id="id">'</xliff:g>'"""")
    assertThat(CharacterDataEscaper.escape(""" '<xliff:g id='id'>'</xliff:g>' """))
        .isEqualTo("""" '<xliff:g id="id">'</xliff:g>' """")
    assertThat(CharacterDataEscaper.escape("'<![CDATA[']]>'")).isEqualTo("""\'<![CDATA[']]>\'""")
    assertThat(CharacterDataEscaper.escape("'<![CDATA[']]>' ")).isEqualTo(""""'<![CDATA[']]>' """")
    assertThat(CharacterDataEscaper.escape(" '<![CDATA[']]>'")).isEqualTo("""" '<![CDATA[']]>'"""")
    assertThat(CharacterDataEscaper.escape(" '<![CDATA[']]>' "))
        .isEqualTo("""" '<![CDATA[']]>' """")
  }

  @Test
  fun escape_entities() {
    assertThat(CharacterDataEscaper.escape("&amp;")).isEqualTo("&amp;")
    assertThat(CharacterDataEscaper.escape("&apos;")).isEqualTo("&apos;")
    assertThat(CharacterDataEscaper.escape("&gt;")).isEqualTo("&gt;")
    assertThat(CharacterDataEscaper.escape("&lt;")).isEqualTo("&lt;")
    assertThat(CharacterDataEscaper.escape("&quot;")).isEqualTo("&quot;")
  }

  @Test
  fun escape_emptyElement() {
    assertThat(CharacterDataEscaper.escape("<br/>")).isEqualTo("<br/>")
  }

  @Test
  fun escape_comment() {
    assertThat(CharacterDataEscaper.escape("<!-- This is a comment -->"))
        .isEqualTo("<!-- This is a comment -->")
  }

  @Test
  fun escape_processingInstruction() {
    assertThat(
            CharacterDataEscaper.escape("""<?xml-stylesheet type="text/css" href="style.css"?>"""))
        .isEqualTo("""<?xml-stylesheet type="text/css" href="style.css"?>""")
  }

  @Test
  fun escape_characterDataInvalidXml() {
    assertFailsWith<IllegalArgumentException> { CharacterDataEscaper.escape("<") }
  }

  @Test
  fun unescape_empty() {
    assertThat(CharacterDataEscaper.unescape("")).isEqualTo("")
  }

  @Test
  fun unescape_decimalReference() {
    assertThat(CharacterDataEscaper.unescape("&#38;")).isEqualTo("&#38;")
  }

  @Test
  fun unescape_hexadecimalReference() {
    assertThat(CharacterDataEscaper.unescape("&#x26;")).isEqualTo("&#x26;")
  }

  @Test
  fun unescape_firstQuestionMark() {
    assertThat(CharacterDataEscaper.unescape("""\???""")).isEqualTo("???")
    assertThat(CharacterDataEscaper.unescape("""\?<xliff:g id="id">?</xliff:g>?"""))
        .isEqualTo("""?<xliff:g id="id">?</xliff:g>?""")
    assertThat(CharacterDataEscaper.unescape("""\?<xliff:g id='id'>?</xliff:g>?"""))
        .isEqualTo("""?<xliff:g id="id">?</xliff:g>?""")
    assertThat(CharacterDataEscaper.unescape("""\?<![CDATA[?]]>?"""))
        .isEqualTo("""?<![CDATA[?]]>?""")
  }

  @Test
  fun unescape_firstAtSign() {
    assertThat(CharacterDataEscaper.unescape("""\@@@""")).isEqualTo("@@@")
    assertThat(CharacterDataEscaper.unescape("""\@<xliff:g id="id">@</xliff:g>@"""))
        .isEqualTo("""@<xliff:g id="id">@</xliff:g>@""")
    assertThat(CharacterDataEscaper.unescape("""\@<xliff:g id='id'>@</xliff:g>@"""))
        .isEqualTo("""@<xliff:g id="id">@</xliff:g>@""")
    assertThat(CharacterDataEscaper.unescape("""\@<![CDATA[@]]>@""")).isEqualTo("@<![CDATA[@]]>@")
  }

  @Test
  fun unescape_quotationMarks() {
    assertThat(CharacterDataEscaper.unescape("""\"""")).isEqualTo(""""""")
    assertThat(CharacterDataEscaper.unescape("""\"<xliff:g id="id">\"</xliff:g>\""""))
        .isEqualTo(""""<xliff:g id="id">"</xliff:g>"""")
    assertThat(CharacterDataEscaper.unescape("""\"<xliff:g id='id'>\"</xliff:g>\""""))
        .isEqualTo(""""<xliff:g id="id">"</xliff:g>"""")
    assertThat(CharacterDataEscaper.unescape("""\"<![CDATA["]]>\""""))
        .isEqualTo(""""<![CDATA["]]>"""")
  }

  @Test
  fun unescape_backslashes() {
    assertThat(CharacterDataEscaper.unescape("""\\""")).isEqualTo("""\""")
    assertThat(CharacterDataEscaper.unescape("""\\<xliff:g id="id">\\</xliff:g>\\"""))
        .isEqualTo("""\<xliff:g id="id">\</xliff:g>\""")
    assertThat(CharacterDataEscaper.unescape("""\\<xliff:g id='id'>\\</xliff:g>\\"""))
        .isEqualTo("""\<xliff:g id="id">\</xliff:g>\""")
    assertThat(CharacterDataEscaper.unescape("""\\<![CDATA[\]]>\\"""))
        .isEqualTo("""\<![CDATA[\]]>\""")
  }

  @Test
  fun unescape_newlines() {
    assertThat(CharacterDataEscaper.unescape("""\n""")).isEqualTo("""
""")
    assertThat(CharacterDataEscaper.unescape("""\n<xliff:g id="id">\n</xliff:g>\n"""))
        .isEqualTo("""
<xliff:g id="id">
</xliff:g>
""")
    assertThat(CharacterDataEscaper.unescape("""\n<xliff:g id='id'>\n</xliff:g>\n"""))
        .isEqualTo("""
<xliff:g id="id">
</xliff:g>
""")
    assertThat(CharacterDataEscaper.unescape("""\n<![CDATA[
]]>\n"""))
        .isEqualTo("""
<![CDATA[
]]>
""")
  }

  @Test
  fun unescape_tabs() {
    // Tabs are not allowed so this does not use raw strings literals.
    assertThat(CharacterDataEscaper.unescape("\\t")).isEqualTo("\t")
    assertThat(CharacterDataEscaper.unescape("\\t<xliff:g id=\"id\">\\t</xliff:g>\\t"))
        .isEqualTo("\t<xliff:g id=\"id\">\t</xliff:g>\t")
    assertThat(CharacterDataEscaper.unescape("\\t<xliff:g id='id'>\\t</xliff:g>\\t"))
        .isEqualTo("\t<xliff:g id=\"id\">\t</xliff:g>\t")
    assertThat(CharacterDataEscaper.unescape("\\t<![CDATA[\t]]>\\t"))
        .isEqualTo("\t<![CDATA[\t]]>\t")
  }

  @Test
  fun unescape_apostrophesLeadingAndTrailingSpaces() {
    assertThat(CharacterDataEscaper.unescape("""\'""")).isEqualTo("'")
    assertThat(CharacterDataEscaper.unescape(""""' """")).isEqualTo("' ")
    assertThat(CharacterDataEscaper.unescape("""" '"""")).isEqualTo(" '")
    assertThat(CharacterDataEscaper.unescape("""" ' """")).isEqualTo(" ' ")
    assertThat(CharacterDataEscaper.unescape("""\'<xliff:g id="id">\'</xliff:g>\'"""))
        .isEqualTo("""'<xliff:g id="id">'</xliff:g>'""")
    assertThat(CharacterDataEscaper.unescape(""""'<xliff:g id="id">'</xliff:g>' """"))
        .isEqualTo("""'<xliff:g id="id">'</xliff:g>' """)
    assertThat(CharacterDataEscaper.unescape("""" '<xliff:g id="id">'</xliff:g>'""""))
        .isEqualTo(""" '<xliff:g id="id">'</xliff:g>'""")
    assertThat(CharacterDataEscaper.unescape("""" '<xliff:g id="id">'</xliff:g>' """"))
        .isEqualTo(""" '<xliff:g id="id">'</xliff:g>' """)
    assertThat(CharacterDataEscaper.unescape("""\'<xliff:g id='id'>\'</xliff:g>\'"""))
        .isEqualTo("""'<xliff:g id="id">'</xliff:g>'""")
    assertThat(CharacterDataEscaper.unescape(""""'<xliff:g id='id'>'</xliff:g>' """"))
        .isEqualTo("""'<xliff:g id="id">'</xliff:g>' """)
    assertThat(CharacterDataEscaper.unescape("""" '<xliff:g id='id'>'</xliff:g>'""""))
        .isEqualTo(""" '<xliff:g id="id">'</xliff:g>'""")
    assertThat(CharacterDataEscaper.unescape("""" '<xliff:g id='id'>'</xliff:g>' """"))
        .isEqualTo(""" '<xliff:g id="id">'</xliff:g>' """)
    assertThat(CharacterDataEscaper.unescape("""\'<![CDATA[']]>\'"""))
        .isEqualTo("'<![CDATA[']]>'")
    assertThat(CharacterDataEscaper.unescape(""""'<![CDATA[']]>' """"))
        .isEqualTo("'<![CDATA[']]>' ")
    assertThat(CharacterDataEscaper.unescape("""" '<![CDATA[']]>'""""))
        .isEqualTo(" '<![CDATA[']]>'")
    assertThat(CharacterDataEscaper.unescape("""" '<![CDATA[']]>' """"))
        .isEqualTo(" '<![CDATA[']]>' ")
  }

  @Test
  fun unescape_invalidXml() {
    assertFailsWith<IllegalArgumentException> { CharacterDataEscaper.unescape("<") }
  }

  @Test
  fun unescape_entities() {
    assertThat(CharacterDataEscaper.unescape("&amp;")).isEqualTo("&amp;")
    assertThat(CharacterDataEscaper.unescape("&apos;")).isEqualTo("&apos;")
    assertThat(CharacterDataEscaper.unescape("&gt;")).isEqualTo("&gt;")
    assertThat(CharacterDataEscaper.unescape("&lt;")).isEqualTo("&lt;")
    assertThat(CharacterDataEscaper.unescape("&quot;")).isEqualTo("&quot;")
  }

  @Test
  fun unescape_emptyElement() {
    assertThat(CharacterDataEscaper.unescape("<br/>")).isEqualTo("<br/>")
  }

  @Test
  fun unescape_comment() {
    assertThat(CharacterDataEscaper.unescape("<!-- This is a comment -->"))
        .isEqualTo("<!-- This is a comment -->")
  }

  @Test
  fun unescape_processingInstruction() {
    assertThat(
            CharacterDataEscaper.unescape(
                """<?xml-stylesheet type="text/css" href="style.css"?>"""))
        .isEqualTo("""<?xml-stylesheet type="text/css" href="style.css"?>""")
  }
}

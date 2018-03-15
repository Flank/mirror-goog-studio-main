/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.utils

import junit.framework.TestCase

class DomExtensionsTest : TestCase() {
    fun testXmlExtensionMethods() {
        val xml = """
        <root>
           <tag1 />
           <tag2 />
           <tag3 />
           <tag4 />
           <tag5>
              Prefix
              <b>Bolded</b>
              <i>Italics</i>
              <b>Bolded again</b>
              Suffix
           </tag5>
        </root>
        """.trimIndent()
        val document = XmlUtils.parseDocumentSilently(xml, false)
        document!!

        val root = document.documentElement
        val tag3 = root.subtag("tag3")
        tag3!!
        assertEquals(tag3.tagName, "tag3")
        assertNull(tag3.next("tag1"))
        assertEquals(tag3.next()?.tagName, "tag4")
        val tag5 = tag3.next("tag5")
        assertEquals(tag5?.tagName, "tag5")
        tag5!!
        assertEquals("""
                Prefix
                Bolded
                Italics
                Bolded again
                Suffix
            """.trimIndent().trim(), tag5.text().trimIndent().trim())
        assertEquals("b", tag5.subtags("b").next().tagName)
        val sb = StringBuilder()
        for (element in root) {
            sb.append(element.tagName).append(' ')
        }
        assertEquals(3, tag5.subtagCount())
        assertEquals(5, root.subtagCount())
        assertEquals("tag1 tag2 tag3 tag4 tag5", sb.trim().toString())
    }
}
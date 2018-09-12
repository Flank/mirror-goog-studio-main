/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.resources;

import junit.framework.TestCase;

/**
 * Test that a sample of ResourceType enum variants and their methods.
 */
public class ResourceTypeTest extends TestCase {

  public void testName() {
    assertEquals("array", ResourceType.ARRAY.getName());
    assertEquals("mipmap", ResourceType.MIPMAP.getName());
  }

  public void testGetDisplayName() {
    assertEquals("Array", ResourceType.ARRAY.getDisplayName());
    assertEquals("Mip Map", ResourceType.MIPMAP.getDisplayName());
  }

    public void testFromXmlTag() {
        assertEquals(ResourceType.ANIM, ResourceType.fromXmlTagName("anim"));
        assertEquals(ResourceType.ANIMATOR, ResourceType.fromXmlTagName("animator"));
        assertEquals(ResourceType.FONT, ResourceType.fromXmlTagName("font"));
        assertEquals(ResourceType.MIPMAP, ResourceType.fromXmlTagName("mipmap"));
        assertEquals(ResourceType.STRING, ResourceType.fromXmlTagName("string"));
        assertEquals(ResourceType.STYLE, ResourceType.fromXmlTagName("style"));
        assertEquals(ResourceType.XML, ResourceType.fromXmlTagName("xml"));

        // In XML, "declare-styleable" has to be used.
        assertEquals(ResourceType.STYLEABLE, ResourceType.fromXmlTagName("declare-styleable"));
        assertNull(ResourceType.fromXmlTagName("styleable"));

        // There can be a "public" tag.
        assertEquals(ResourceType.PUBLIC, ResourceType.fromXmlTagName("public"));

        // Alternate names should work:
        assertEquals(ResourceType.ARRAY, ResourceType.fromXmlTagName("array"));
        assertEquals(ResourceType.ARRAY, ResourceType.fromXmlTagName("string-array"));
        assertEquals(ResourceType.ARRAY, ResourceType.fromXmlTagName("integer-array"));

        // Display names should not work.
        assertNull(ResourceType.fromXmlTagName("Array"));
        assertNull(ResourceType.fromXmlTagName("Declare Styleable"));

        // Misc values should not work.
        assertNull(ResourceType.fromXmlTagName(""));
        assertNull(ResourceType.fromXmlTagName("declare"));
        assertNull(ResourceType.fromXmlTagName("pluralz"));
        assertNull(ResourceType.fromXmlTagName("strin"));
    }

    public void testClassName() {
        assertEquals(ResourceType.ANIM, ResourceType.fromClassName("anim"));
        assertEquals(ResourceType.ANIMATOR, ResourceType.fromClassName("animator"));
        assertEquals(ResourceType.FONT, ResourceType.fromClassName("font"));
        assertEquals(ResourceType.MIPMAP, ResourceType.fromClassName("mipmap"));
        assertEquals(ResourceType.STRING, ResourceType.fromClassName("string"));
        assertEquals(ResourceType.STYLE, ResourceType.fromClassName("style"));
        assertEquals(ResourceType.XML, ResourceType.fromClassName("xml"));

        // The class is called "styleable".
        assertEquals(ResourceType.STYLEABLE, ResourceType.fromClassName("styleable"));
        assertNull(ResourceType.fromClassName("declare-styleable"));

        // There's no public class.
        assertNull(ResourceType.fromClassName("public"));

        // Alternate names should not work:
        assertEquals(ResourceType.ARRAY, ResourceType.fromXmlValue("array"));
        assertNull(ResourceType.fromClassName("string-array"));
        assertNull(ResourceType.fromClassName("integer-array"));

        // Display names should not work.
        assertNull(ResourceType.fromClassName("Array"));
        assertNull(ResourceType.fromClassName("Declare Styleable"));

        // Misc values should not work.
        assertNull(ResourceType.fromClassName(""));
        assertNull(ResourceType.fromClassName("declare"));
        assertNull(ResourceType.fromClassName("pluralz"));
        assertNull(ResourceType.fromClassName("strin"));
    }

    public void testFromXmlValue() {
        assertEquals(ResourceType.ANIM, ResourceType.fromXmlValue("anim"));
        assertEquals(ResourceType.ANIMATOR, ResourceType.fromXmlValue("animator"));
        assertEquals(ResourceType.FONT, ResourceType.fromXmlValue("font"));
        assertEquals(ResourceType.MIPMAP, ResourceType.fromXmlValue("mipmap"));
        assertEquals(ResourceType.STRING, ResourceType.fromXmlValue("string"));
        assertEquals(ResourceType.STYLE, ResourceType.fromXmlValue("style"));
        assertEquals(ResourceType.XML, ResourceType.fromXmlValue("xml"));

        // Styleable doesn't work at all.
        assertNull(ResourceType.fromXmlValue("declare-styleable"));
        assertNull(ResourceType.fromXmlValue("styleable"));

        // No way to "reference" a public declaration.
        assertNull(ResourceType.fromXmlValue("public"));

        // Alternate names should not work:
        assertEquals(ResourceType.ARRAY, ResourceType.fromXmlValue("array"));
        assertNull(ResourceType.fromXmlValue("string-array"));
        assertNull(ResourceType.fromXmlValue("integer-array"));

        // Display names should not work.
        assertNull(ResourceType.fromXmlValue("Array"));
        assertNull(ResourceType.fromXmlValue("Declare Styleable"));

        // Misc values should not work.
        assertNull(ResourceType.fromXmlValue(""));
        assertNull(ResourceType.fromXmlValue("declare"));
        assertNull(ResourceType.fromXmlValue("pluralz"));
        assertNull(ResourceType.fromXmlValue("strin"));

        // Required by Layoutlib for dealing with appt attributes
        assertEquals(ResourceType.AAPT, ResourceType.fromXmlValue("_aapt"));
  }

}

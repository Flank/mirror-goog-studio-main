/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.ide.common.resources

import com.android.resources.ResourceType.AAPT
import com.android.resources.ResourceType.ATTR
import com.android.resources.ResourceType.DIMEN
import com.android.resources.ResourceType.ID
import com.android.resources.ResourceType.LAYOUT
import com.android.resources.ResourceType.SAMPLE_DATA
import com.android.resources.ResourceType.STRING
import com.android.resources.ResourceType.STYLE
import com.android.resources.ResourceUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceUrlTest {
    @Test
    fun parse() {
        assertNull(ResourceUrl.parse(""))
        assertNull(ResourceUrl.parse("not_a_resource"))
        assertNull(ResourceUrl.parse("@null"))
        assertNull(ResourceUrl.parse("@empty"))
        assertNull(ResourceUrl.parse("@undefined"))
        assertNull(ResourceUrl.parse("@?"))
        assertNull(ResourceUrl.parse("@android:layout"))
        assertNull(ResourceUrl.parse("@layout"))
        assertNull(ResourceUrl.parse("@layout/"))
        assertNull(ResourceUrl.parse("@:layout/foo"))
        assertNull(ResourceUrl.parse("@com.app:/foo"))
        assertNull(ResourceUrl.parse("@com.app:layout/"))

        assertEquals("foo", ResourceUrl.parse("@id/foo")!!.name)
        assertEquals(ID, ResourceUrl.parse("@id/foo")!!.type)
        assertFalse(ResourceUrl.parse("@id/foo")!!.isFramework)
        assertFalse(ResourceUrl.parse("@id/foo")!!.isCreate)
        assertFalse(ResourceUrl.parse("@id/foo")!!.isTheme)

        assertEquals("foo", ResourceUrl.parse("@+id/foo")!!.name)
        assertEquals(ID, ResourceUrl.parse("@+id/foo")!!.type)
        assertFalse(ResourceUrl.parse("@+id/foo")!!.isFramework)
        assertTrue(ResourceUrl.parse("@+id/foo")!!.isCreate)

        assertEquals(LAYOUT, ResourceUrl.parse("@layout/foo")!!.type)
        assertEquals(DIMEN, ResourceUrl.parse("@dimen/foo")!!.type)
        assertFalse(ResourceUrl.parse("@dimen/foo")!!.isFramework)
        assertEquals("foo", ResourceUrl.parse("@android:dimen/foo")!!.name)
        assertEquals(DIMEN, ResourceUrl.parse("@android:dimen/foo")!!.type)
        assertTrue(ResourceUrl.parse("@android:dimen/foo")!!.isFramework)
        assertEquals("foo", ResourceUrl.parse("@layout/foo")!!.name)
        assertEquals("foo", ResourceUrl.parse("@dimen/foo")!!.name)
        assertEquals(ATTR, ResourceUrl.parse("?attr/foo")!!.type)
        assertTrue(ResourceUrl.parse("?attr/foo")!!.isTheme)
        assertEquals("foo", ResourceUrl.parse("?attr/foo")!!.name)
        assertFalse(ResourceUrl.parse("?attr/foo")!!.isFramework)
        assertEquals(ATTR, ResourceUrl.parse("?foo")!!.type)
        assertEquals("foo", ResourceUrl.parse("?foo")!!.name)
        assertFalse(ResourceUrl.parse("?foo")!!.isFramework)
        assertEquals(ATTR, ResourceUrl.parse("?android:foo")!!.type)
        assertEquals("foo", ResourceUrl.parse("?android:foo")!!.name)
        assertTrue(ResourceUrl.parse("?android:foo")!!.isFramework)
        assertTrue(ResourceUrl.parse("?android:foo")!!.isTheme)
        assertFalse(ResourceUrl.parse("?androidx:foo")!!.isFramework)
        assertTrue(ResourceUrl.parse("?androidx:foo")!!.isTheme)
        assertFalse(ResourceUrl.parse("?foo", false)!!.isFramework)
        assertTrue(ResourceUrl.parse("?android:foo", false)!!.isFramework)
        assertTrue(ResourceUrl.parse("?foo", true)!!.isFramework)
        assertTrue(ResourceUrl.parse("?attr/foo", true)!!.isFramework)
        assertFalse(ResourceUrl.parse("@my_package:layout/my_name")!!.isPrivateAccessOverride)
        assertTrue(ResourceUrl.parse("@*my_package:layout/my_name")!!.isPrivateAccessOverride)
        assertEquals(AAPT, ResourceUrl.parse("@aapt:_aapt/34")!!.type)
    }

    @Test
    fun parseAttrReference() {
        assertNull(ResourceUrl.parseAttrReference(""))
        assertEquals(
                ResourceUrl.createAttrReference(null, "foo"),
                ResourceUrl.parseAttrReference("foo")
        )
        assertEquals(
                ResourceUrl.createAttrReference("android", "foo"),
                ResourceUrl.parseAttrReference("android:foo")
        )
        assertEquals(
                ResourceUrl.createAttrReference("com.app", "foo"),
                ResourceUrl.parseAttrReference("com.app:foo")
        )
        assertEquals("*com.app:foo", ResourceUrl.parseAttrReference("*com.app:foo")!!.toString())

        assertNull(ResourceUrl.parseAttrReference("@attr/foo"))
        assertNull(ResourceUrl.parseAttrReference("@foo"))
        assertNull(ResourceUrl.parseAttrReference("attr/foo"))
        assertNull(ResourceUrl.parseAttrReference("com.app:attr/foo"))
        assertNull(ResourceUrl.parseAttrReference("com.app:attr/foo"))
        assertNull(ResourceUrl.parseAttrReference("@com.app:attr/foo"))
        assertNull(ResourceUrl.parseAttrReference("@com.app:foo"))
    }

    @Test
    fun parseStyleParentReference() {
        assertNull(ResourceUrl.parseStyleParentReference(""))
        assertEquals(
                ResourceUrl.create(null, STYLE, "foo"),
                ResourceUrl.parseStyleParentReference("foo")
        )
        assertEquals(
                ResourceUrl.create(null, STYLE, "foo"),
                ResourceUrl.parseStyleParentReference("@foo")
        )
        assertEquals(
                ResourceUrl.create(null, STYLE, "foo"),
                ResourceUrl.parseStyleParentReference("@style/foo")
        )
        assertEquals(
                ResourceUrl.create(null, STYLE, "foo"),
                ResourceUrl.parseStyleParentReference("?style/foo")
        )
        assertEquals(
                ResourceUrl.create(null, STYLE, "foo"),
                ResourceUrl.parseStyleParentReference("?foo")
        )
        assertEquals(
                ResourceUrl.create("com.app", STYLE, "foo"),
                ResourceUrl.parseStyleParentReference("com.app:foo")
        )
        assertEquals(
                ResourceUrl.create("com.app", STYLE, "foo"),
                ResourceUrl.parseStyleParentReference("com.app:foo")
        )
        assertEquals(
                ResourceUrl.create("com.app", STYLE, "foo"),
                ResourceUrl.parseStyleParentReference("@com.app:foo")
        )
        assertEquals(
                ResourceUrl.create("com.app", STYLE, "foo"),
                ResourceUrl.parseStyleParentReference("@com.app:style/foo")
        )
        assertEquals("@*com.app:style/foo", ResourceUrl.parseStyleParentReference("*com.app:foo")!!.toString())
    }

    @Test
    fun namespaces() {
        assertNull(ResourceUrl.parse("?foo")!!.namespace)
        assertNull(ResourceUrl.parse("@string/foo")!!.namespace)
        assertEquals("android", ResourceUrl.parse("?android:foo")!!.namespace)
        assertEquals("android", ResourceUrl.parse("@android:string/foo")!!.namespace)
        assertEquals("my.pkg", ResourceUrl.parse("@my.pkg:string/foo")!!.namespace)
    }

    @Test
    fun hasValidName() {
        assertTrue(ResourceUrl.parse("@id/foo")!!.hasValidName())
        assertFalse(ResourceUrl.parse("@id/foo bar")!!.hasValidName())
        assertFalse(ResourceUrl.parse("@id/?")!!.hasValidName())
        assertFalse(ResourceUrl.parse("@id/123")!!.hasValidName())
        assertFalse(ResourceUrl.parse("@id/ab+")!!.hasValidName())
        assertEquals(
                "?android:attr/foo",
                ResourceUrl.createThemeReference("android", ATTR, "foo").toString()
        )

        assertTrue(ResourceUrl.parse("@sample/test.json/user/email")!!.hasValidName())
        assertFalse(ResourceUrl.parse("@string/test.json/user/email")!!.hasValidName())
    }

    @Test
    fun testToString() {
        assertEquals("@+id/foo", ResourceUrl.parse("@+id/foo")!!.toString())
        assertEquals("@layout/foo", ResourceUrl.parse("@layout/foo")!!.toString())
        assertEquals("?android:attr/foo", ResourceUrl.parse("?android:foo")!!.toString())
        assertEquals("@android:layout/foo", ResourceUrl.parse("@android:layout/foo")!!.toString())
        assertEquals("@*my_package:layout/my_name", ResourceUrl.parse("@*my_package:layout/my_name")!!.toString())
        assertEquals("foo", ResourceUrl.parseAttrReference("foo")!!.toString())
        assertEquals("android:foo", ResourceUrl.parseAttrReference("android:foo")!!.toString())
        assertEquals("androidx:foo", ResourceUrl.parseAttrReference("androidx:foo")!!.toString())
    }

    @Test
    fun qualifiedName() {
        assertEquals("android:Theme", ResourceUrl.parse("@android:style/Theme")!!.qualifiedName)
        assertEquals("MyStyle", ResourceUrl.parse("@style/MyStyle")!!.qualifiedName)
        assertEquals("myColor", ResourceUrl.parse("?myColor")!!.qualifiedName)
        assertEquals("android:colorPrimary", ResourceUrl.parse("?android:colorPrimary")!!.qualifiedName)
        assertEquals("my_package:my_name", ResourceUrl.parse("@*my_package:layout/my_name")!!.qualifiedName)
    }

    @Test
    fun namespaceAfterType() {
        var url = "?attr/android:foo"
        var resourceUrl = ResourceUrl.parse(url)!!
        assertEquals("android:foo", resourceUrl.qualifiedName)
        assertEquals("foo", resourceUrl.name)
        assertEquals("android", resourceUrl.namespace)
        assertTrue(resourceUrl.isFramework)
        assertTrue(resourceUrl.isTheme)
        assertEquals(ATTR, resourceUrl.type)

        url = "@string/android:foo"
        resourceUrl = ResourceUrl.parse(url)!!
        assertEquals("android:foo", resourceUrl.qualifiedName)
        assertEquals("foo", resourceUrl.name)
        assertEquals("android", resourceUrl.namespace)
        assertTrue(resourceUrl.isFramework)
        assertFalse(resourceUrl.isTheme)
        assertEquals(STRING, resourceUrl.type)

        url = "@attr/bar:foo"
        resourceUrl = ResourceUrl.parse(url)!!
        assertEquals("bar:foo", resourceUrl.qualifiedName)
        assertEquals("foo", resourceUrl.name)
        assertEquals("bar", resourceUrl.namespace)
        assertFalse(resourceUrl.isFramework)
        assertFalse(resourceUrl.isTheme)
        assertEquals(ATTR, resourceUrl.type)
    }

    @Test
    fun urlWithParameters() {
        var url = "@sample/lorem[4:10]"
        var resourceUrl = ResourceUrl.parse(url)!!
        assertEquals("lorem[4:10]", resourceUrl.qualifiedName)
        assertEquals("lorem[4:10]", resourceUrl.name)
        assertNull(resourceUrl.namespace)
        assertFalse(resourceUrl.isFramework)
        assertFalse(resourceUrl.isTheme)
        assertEquals(SAMPLE_DATA, resourceUrl.type)

        url = "@android:sample/lorem[4:10]"
        resourceUrl = ResourceUrl.parse(url)!!
        assertEquals("android:lorem[4:10]", resourceUrl.qualifiedName)
        assertEquals("lorem[4:10]", resourceUrl.name)
        assertEquals("android", resourceUrl.namespace)
        assertTrue(resourceUrl.isFramework)
        assertFalse(resourceUrl.isTheme)
        assertEquals(SAMPLE_DATA, resourceUrl.type)

        url = "@sample/android:lorem[4:10]"
        resourceUrl = ResourceUrl.parse(url)!!
        assertEquals("android:lorem[4:10]", resourceUrl.qualifiedName)
        assertEquals("lorem[4:10]", resourceUrl.name)
        assertEquals("android", resourceUrl.namespace)
        assertTrue(resourceUrl.isFramework)
        assertFalse(resourceUrl.isTheme)
        assertEquals(SAMPLE_DATA, resourceUrl.type)
    }
}

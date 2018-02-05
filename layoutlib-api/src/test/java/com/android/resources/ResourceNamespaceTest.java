// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.resources;

import static com.android.ide.common.rendering.api.ResourceNamespace.ANDROID;
import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.android.ide.common.rendering.api.ResourceNamespace.Resolver.EMPTY_RESOLVER;
import static com.android.ide.common.rendering.api.ResourceNamespace.TOOLS;
import static com.android.ide.common.rendering.api.ResourceNamespace.fromNamespacePrefix;
import static com.android.ide.common.rendering.api.ResourceNamespace.fromPackageName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

public class ResourceNamespaceTest {
    @Test
    public void packageName() {
        assertEquals("android", ANDROID.getPackageName());
        assertNull(RES_AUTO.getPackageName());
    }

    @Test
    @SuppressWarnings("ConstantConditions") // suppress null warnings, that's what we're testing.
    public void fromPrefix() {
        assertEquals(
                "com.example",
                fromNamespacePrefix("com.example", RES_AUTO, EMPTY_RESOLVER).getPackageName());
        assertEquals(
                SdkConstants.ANDROID_NS_NAME,
                fromNamespacePrefix("android", RES_AUTO, EMPTY_RESOLVER).getPackageName());
        assertNull(fromNamespacePrefix(null, RES_AUTO, EMPTY_RESOLVER).getPackageName());

        assertEquals(
                "android", fromNamespacePrefix(null, ANDROID, EMPTY_RESOLVER).getPackageName());

        ImmutableMap<String, String> namespaces =
                ImmutableMap.of(
                        "aaa", "http://schemas.android.com/apk/res/madeup",
                        "ex", "http://schemas.android.com/apk/res/com.example");
        assertEquals(
                "com.example",
                fromNamespacePrefix("ex", RES_AUTO, namespaces::get).getPackageName());
    }

    @Test
    public void androidSingleton() {
        assertSame(ANDROID, ResourceNamespace.fromPackageName("android"));

        assertSame(
                ANDROID,
                ResourceNamespace.fromNamespacePrefix(
                        "android", RES_AUTO, prefix -> SdkConstants.ANDROID_URI));
    }

    @Test
    public void testEquals() {
        ResourceNamespace aaa = fromPackageName("aaa");
        ResourceNamespace bbb1 = fromPackageName("bbb");
        ResourceNamespace bbb2 = fromPackageName("bbb");

        assertEquals(aaa, aaa);
        assertEquals(bbb1, bbb2);
        assertNotEquals(aaa, bbb1);
        assertNotEquals(bbb1, aaa);
        assertNotEquals(ANDROID, aaa);
        assertNotEquals(TOOLS, aaa);
        assertNotEquals(RES_AUTO, aaa);
        assertNotEquals(ANDROID, bbb2);
        assertNotEquals(TOOLS, bbb2);
        assertNotEquals(RES_AUTO, bbb2);
    }

    @Test
    public void xmlNamespaceUri() {
        assertEquals(SdkConstants.ANDROID_URI, ANDROID.getXmlNamespaceUri());
    }
}

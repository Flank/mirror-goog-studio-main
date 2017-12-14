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
import static com.android.ide.common.rendering.api.ResourceNamespace.EMPTY_NAMESPACE_CONTEXT;
import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.android.ide.common.rendering.api.ResourceNamespace.TOOLS;
import static com.android.ide.common.rendering.api.ResourceNamespace.fromNamespacePrefix;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

public class ResourceNamespaceTest {
    @Test
    public void packageName() {
        assertEquals("android", ANDROID.getPackageName());
        assertEquals(null, RES_AUTO.getPackageName());
    }

    @Test
    @SuppressWarnings("ConstantConditions") // suppress null warnings, that's what we're testing.
    public void fromPrefix() {
        assertEquals(
                "com.example",
                fromNamespacePrefix("com.example", RES_AUTO, EMPTY_NAMESPACE_CONTEXT)
                        .getPackageName());
        assertEquals(
                SdkConstants.ANDROID_NS_NAME,
                fromNamespacePrefix("android", RES_AUTO, EMPTY_NAMESPACE_CONTEXT).getPackageName());
        assertEquals(
                null,
                fromNamespacePrefix(null, RES_AUTO, EMPTY_NAMESPACE_CONTEXT).getPackageName());

        assertEquals(
                "android",
                fromNamespacePrefix(null, ANDROID, EMPTY_NAMESPACE_CONTEXT).getPackageName());

        ImmutableMap<String, String> namespaces =
                ImmutableMap.of(
                        "aaa", "http://schemas.android.com/apk/res/madeup",
                        "ex", "http://schemas.android.com/apk/res/com.example");
        assertEquals(
                "com.example",
                fromNamespacePrefix("ex", RES_AUTO, namespaces::get).getPackageName());
    }

    @Test
    public void testEquals() {
        ResourceNamespace aaa = ResourceNamespace.fromPackageName("aaa");
        ResourceNamespace bbb1 = ResourceNamespace.fromPackageName("bbb");
        ResourceNamespace bbb2 = ResourceNamespace.fromPackageName("bbb");

        assertEquals(aaa, aaa);
        assertEquals(bbb1, bbb2);
        assertNotEquals(aaa, bbb1);
        assertNotEquals(bbb1, aaa);
        assertNotEquals(aaa, ANDROID);
        assertNotEquals(aaa, TOOLS);
        assertNotEquals(aaa, RES_AUTO);
        assertNotEquals(ANDROID, bbb2);
        assertNotEquals(TOOLS, bbb2);
        assertNotEquals(RES_AUTO, bbb2);
    }
}

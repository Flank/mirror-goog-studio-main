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
package com.android.ide.common.rendering.api;

import static org.junit.Assert.assertEquals;

import com.android.resources.ResourceType;
import org.junit.Test;

/** Unit tests for the {@link ResourceReference} class. */
public class ResourceReferenceTest {

    @Test
    public void testRelativeResourceUrl() {
        ResourceReference inResAuto =
                new ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STRING, "app_name");

        assertEquals(
                "@string/app_name",
                inResAuto.getRelativeResourceUrl(ResourceNamespace.RES_AUTO).toString());

        ResourceReference inAndroid =
                new ResourceReference(ResourceNamespace.ANDROID, ResourceType.COLOR, "black");

        assertEquals(
                "@color/black",
                inAndroid.getRelativeResourceUrl(ResourceNamespace.ANDROID).toString());
        assertEquals(
                "@android:color/black",
                inAndroid.getRelativeResourceUrl(ResourceNamespace.RES_AUTO).toString());

        ResourceNamespace appNs = ResourceNamespace.fromPackageName("com.example.app");
        ResourceNamespace libNs = ResourceNamespace.fromPackageName("com.example.lib");

        ResourceReference inLib = new ResourceReference(libNs, ResourceType.STRING, "app_name");

        assertEquals("@string/app_name", inLib.getRelativeResourceUrl(libNs).toString());
        assertEquals(
                "@com.example.lib:string/app_name", inLib.getRelativeResourceUrl(appNs).toString());
    }
}

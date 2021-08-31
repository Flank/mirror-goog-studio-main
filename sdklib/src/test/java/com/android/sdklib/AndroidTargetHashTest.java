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

package com.android.sdklib;

import com.android.sdklib.internal.androidTarget.MockAddonTarget;
import com.android.sdklib.internal.androidTarget.MockPlatformTarget;

import junit.framework.TestCase;

public class AndroidTargetHashTest extends TestCase {

    public final void testGetPlatformHashString() {
        assertEquals("android-10",
                     AndroidTargetHash.getPlatformHashString(new AndroidVersion(10,
                                                                                null,
                                                                                null,
                                                                                true)));

        //Base SDKs with extension levels are equals to SDKs where the extension level is not known.
        assertEquals("android-10",
                     AndroidTargetHash.getPlatformHashString(new AndroidVersion(10,
                                                                                null,
                                                                                3,
                                                                                true)));

        assertEquals("android-CODE_NAME",
                     AndroidTargetHash.getPlatformHashString(new AndroidVersion(10, "CODE_NAME")));

        assertEquals("android-10-ext3",
                     AndroidTargetHash.getPlatformHashString(new AndroidVersion(10,
                                                                                null,
                                                                                3,
                                                                                false)));
    }

    public final void testGetAddonHashString() {
        assertEquals("The Vendor Inc.:My Addon:10",
                AndroidTargetHash.getAddonHashString(
                        "The Vendor Inc.",
                        "My Addon",
                        new AndroidVersion(10, null)));
    }

    public final void testGetTargetHashString() {
        MockPlatformTarget t = new MockPlatformTarget(10, 1);
        assertEquals("android-10", AndroidTargetHash.getTargetHashString(t));
        MockAddonTarget a = new MockAddonTarget("My Addon", t, 2);
        assertEquals("vendor 10:My Addon:10", AndroidTargetHash.getTargetHashString(a));
    }

    public void testGetPlatformVersion() {
        assertNull(AndroidTargetHash.getPlatformVersion("blah-5"));
        assertNull(AndroidTargetHash.getPlatformVersion("5-blah"));
        assertNull(AndroidTargetHash.getPlatformVersion("android-"));
        assertNull(AndroidTargetHash.getPlatformVersion("android-2-ext"));

        AndroidVersion version = AndroidTargetHash.getPlatformVersion("android-5");
        assertNotNull(version);
        assertEquals(5, version.getApiLevel());
        assertTrue(version.isBaseExtension());
        assertNull(version.getCodename());

        version = AndroidTargetHash.getPlatformVersion("5");
        assertNotNull(version);
        assertEquals(5, version.getApiLevel());
        assertEquals(5, version.getFeatureLevel());
        assertTrue(version.isBaseExtension());
        assertNull(version.getCodename());

        version = AndroidTargetHash.getPlatformVersion("android-CUPCAKE");
        assertNotNull(version);
        assertEquals(2, version.getApiLevel());
        assertEquals(3, version.getFeatureLevel());
        assertTrue(version.isBaseExtension());
        assertEquals("CUPCAKE", version.getCodename());

        version = AndroidTargetHash.getPlatformVersion("android-KITKAT");
        assertNotNull(version);
        assertEquals(18, version.getApiLevel());
        assertEquals(19, version.getFeatureLevel());
        assertTrue(version.isBaseExtension());
        assertEquals("KITKAT", version.getCodename());

        version = AndroidTargetHash.getPlatformVersion("android-N");
        assertNotNull(version);
        assertEquals(23, version.getApiLevel());
        assertEquals(24, version.getFeatureLevel());
        assertTrue(version.isBaseExtension());
        assertEquals("N", version.getCodename());

        version = AndroidTargetHash.getPlatformVersion("android-UNKNOWN");
        assertNotNull(version);
        assertEquals(SdkVersionInfo.HIGHEST_KNOWN_API, version.getApiLevel());
        assertEquals(SdkVersionInfo.HIGHEST_KNOWN_API + 1, version.getFeatureLevel());
        assertTrue(version.isBaseExtension());
        assertEquals("UNKNOWN", version.getCodename());

        version = AndroidTargetHash.getPlatformVersion("android-10-ext3");
        assertNotNull(version);
        assertEquals(10, version.getApiLevel());
        assertEquals(3, version.getExtensionLevel().intValue());
        assertFalse(version.isBaseExtension());
        assertNull(version.getCodename());
    }
}

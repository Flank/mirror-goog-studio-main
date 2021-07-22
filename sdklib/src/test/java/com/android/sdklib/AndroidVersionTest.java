/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.sdklib.AndroidVersion.AndroidVersionException;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link AndroidVersion}.
 */
public class AndroidVersionTest {

    @Test
    public final void testAndroidVersionWithExtensions() {
        // Extension levels for an API level are greater than versions where the extension level is
        // not given.
        AndroidVersion v = new AndroidVersion(30, null);
        assertFalse(v.isPreview());
        assertNull(v.getCodename());
        assertEquals("30", v.getApiString());

        // AndroidVersions that are base SDKs are equal no matter if the extension level is given or
        // not (in the case where the SDK was downloaded by versions of studio before extensions
        // were known).
        assertEquals(v.hashCode(), new AndroidVersion(30, null, 4, true).hashCode());
        assertNotEquals(
                new AndroidVersion(30, null, 6, false).hashCode(),
                new AndroidVersion(30, null, 4, false).hashCode());

        assertTrue(v.isGreaterOrEqualThan(0));
        assertTrue(v.isGreaterOrEqualThan(14));
        assertTrue(v.isGreaterOrEqualThan(15));
        assertFalse(v.isGreaterOrEqualThan(31));

        assertTrue(v.isGreaterOrEqualThan(30, 1));
        assertTrue(v.isGreaterOrEqualThan(30, 2));

        v = new AndroidVersion(30, null, 4, false);
        assertFalse(v.isPreview());
        assertNull(v.getCodename());
        assertEquals("30", v.getApiString());

        assertTrue(v.isGreaterOrEqualThan(0));
        assertTrue(v.isGreaterOrEqualThan(14));
        assertTrue(v.isGreaterOrEqualThan(15));
        assertFalse(v.isGreaterOrEqualThan(31));

        assertTrue(v.isGreaterOrEqualThan(30, 1));
        assertTrue(v.isGreaterOrEqualThan(30, 2));
        assertTrue(v.isGreaterOrEqualThan(30, 4));
        assertFalse(v.isGreaterOrEqualThan(30, 5));

        assertThat(v).isGreaterThan(new AndroidVersion(29, "codename"));
        assertThat(v).isLessThan(new AndroidVersion(30, "codename"));
        assertEquals("API 30, extension level 4", v.toString());

        // AndroidVersions with extension level but is the base SDK, is the same as an
        // AndroidVersions with no extension information.
        AndroidVersion base = new AndroidVersion(10);
        AndroidVersion baseWithExtensionInfo = new AndroidVersion(10, null, 3, true);
        assertEquals(base, baseWithExtensionInfo);

        assertThat(base.compareTo(baseWithExtensionInfo)).isEqualTo(0);
    }

    @Test
    public final void testAndroidVersion() {
        AndroidVersion v = new AndroidVersion(1, "  CODENAME   ");
        assertEquals(1, v.getApiLevel());
        assertEquals("CODENAME", v.getApiString());
        assertTrue(v.isPreview());
        assertEquals("CODENAME", v.getCodename());
        assertEquals("API 1, CODENAME preview", v.toString());

        v = new AndroidVersion(15, "REL");
        assertEquals(15, v.getApiLevel());
        assertEquals("15", v.getApiString());
        assertFalse(v.isPreview());
        assertNull(v.getCodename());
        assertTrue(v.equals(15));
        assertEquals("API 15", v.toString());

        v = new AndroidVersion(15, null);
        assertEquals(15, v.getApiLevel());
        assertEquals("15", v.getApiString());
        assertFalse(v.isPreview());
        assertNull(v.getCodename());
        assertTrue(v.equals(15));
        assertEquals("API 15", v.toString());

        // An empty codename is like a null codename
        v = new AndroidVersion(15, "   ");
        assertFalse(v.isPreview());
        assertNull(v.getCodename());
        assertEquals("15", v.getApiString());

        v = new AndroidVersion(15, "");
        assertFalse(v.isPreview());
        assertNull(v.getCodename());
        assertEquals("15", v.getApiString());

        assertTrue(v.isGreaterOrEqualThan(0));
        assertTrue(v.isGreaterOrEqualThan(14));
        assertTrue(v.isGreaterOrEqualThan(15));
        assertFalse(v.isGreaterOrEqualThan(16));
        assertFalse(v.isGreaterOrEqualThan(Integer.MAX_VALUE));
    }

    @Test
    public final void testAndroidVersion_apiOrCodename() throws AndroidVersionException {
        // A valid integer is considered an API level
        AndroidVersion v = new AndroidVersion("15");
        assertEquals(15, v.getApiLevel());
        assertEquals("15", v.getApiString());
        assertFalse(v.isPreview());
        assertNull(v.getCodename());
        assertTrue(v.equals(15));
        assertEquals("API 15", v.toString());

        // A valid name is considered a codename
        v = new AndroidVersion("CODE_NAME");
        assertEquals("CODE_NAME", v.getApiString());
        assertTrue(v.isPreview());
        assertTrue(v.isBaseExtension());
        assertEquals("CODE_NAME", v.getCodename());
        assertEquals(v, new AndroidVersion("CODE_NAME"));
        assertEquals(0, v.getApiLevel());
        assertEquals("API 0, CODE_NAME preview", v.toString());

        // invalid code name should fail
        for (String s : new String[] { "REL", "code.name", "10val", "" }) {
            try {
                //noinspection ResultOfObjectAllocationIgnored
                new AndroidVersion(s);
                fail("Invalid code name '" + s + "': Expected to fail. Actual: did not fail.");
            } catch (AndroidVersionException e) {
                assertEquals("Invalid android API or codename " + s, e.getMessage());
            }
        }
    }

    @Test
    public void testGetFeatureLevel() {
        assertEquals(1, AndroidVersion.DEFAULT.getFeatureLevel());

        assertEquals(5, new AndroidVersion(5, null).getApiLevel());
        assertEquals(5, new AndroidVersion(5, null).getFeatureLevel());

        assertEquals(5, new AndroidVersion(5, "codename").getApiLevel());
        assertEquals(6, new AndroidVersion(5, "codename").getFeatureLevel());
    }
}

/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.instantapp.utils;

import static com.android.instantapp.utils.DeviceUtils.getOsBuildType;
import static com.android.instantapp.utils.DeviceUtils.isPostO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.ddmlib.IDevice;
import org.junit.Test;

/** Unit tests for {@link DeviceUtils}. */
public class DeviceUtilsTest {
    @Test
    public void testIsPostOReturnsTrueWhenOPreview() throws Throwable {
        IDevice device = new InstantAppTests.DeviceGenerator().setApiLevel(25, "O").getDevice();
        assertTrue(isPostO(device));
    }

    @Test
    public void testIsPostOReturnsFalseWhenPreO() throws Throwable {
        IDevice device = new InstantAppTests.DeviceGenerator().setApiLevel(25, null).getDevice();
        assertFalse(isPostO(device));
    }

    @Test
    public void testGetOsBuildType() throws Throwable {
        IDevice device =
                new InstantAppTests.DeviceGenerator().setOsBuildType("test-keys").getDevice();
        assertEquals("test-keys", getOsBuildType(device));
    }
}

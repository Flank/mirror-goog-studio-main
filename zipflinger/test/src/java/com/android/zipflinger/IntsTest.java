/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.zipflinger;

import org.junit.Assert;
import org.junit.Test;

public class IntsTest {
    @Test
    public void testLongToUintOverflow() {
        boolean exceptionCaught = false;
        long i = 0x1_FF_FF_FF_FFL;
        try {
            Ints.longToUint(i);
        } catch (IllegalStateException e) {
            exceptionCaught = true;
        }
        Assert.assertTrue(exceptionCaught);
    }

    @Test
    public void testLongToUint() {
        boolean exceptionCaught = false;
        long i = 0xFF_FF_FF_FFL;
        try {
            Ints.longToUint(i);
        } catch (IllegalStateException e) {
            exceptionCaught = true;
        }
        Assert.assertFalse(exceptionCaught);
    }

    @Test
    public void testIntToUshortOverflow() {
        boolean exceptionCaught = false;
        int i = 0x1_FF_FF;
        try {
            Ints.intToUshort(i);
        } catch (IllegalStateException e) {
            exceptionCaught = true;
        }
        Assert.assertTrue(exceptionCaught);
    }

    @Test
    public void testIntToUshort() {
        boolean exceptionCaught = false;
        int i = 0xFF_FF;
        try {
            Ints.intToUshort(i);
        } catch (IllegalStateException e) {
            exceptionCaught = true;
        }
        Assert.assertFalse(exceptionCaught);
    }

    @Test
    public void testULongToLongOverflow() {
        boolean exceptionCaught = false;
        long i = 0x80_00_00_00_00_00_00_00L;
        try {
            Ints.ulongToLong(i);
        } catch (IllegalStateException e) {
            exceptionCaught = true;
        }
        Assert.assertTrue(exceptionCaught);
    }

    @Test
    public void testULongToLong() {
        boolean exceptionCaught = false;
        long i = 0x70_00_00_00_00_00_00_00L;
        try {
            Ints.ulongToLong(i);
        } catch (IllegalStateException e) {
            exceptionCaught = true;
        }
        Assert.assertFalse(exceptionCaught);
    }
}

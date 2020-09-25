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
package com.android.ddmlib;

import static com.android.ddmlib.AdbHelper.DEFAULT_CHARSET;
import static com.android.ddmlib.AdbHelper.formAdbRequest;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class AdbHelperTest {
    @Test
    public void defaultCharsetPreservesChinese() {
        String path = "/sdcard/中文";

        byte[] bytes = path.getBytes(DEFAULT_CHARSET);
        assertEquals(path, new String(bytes, DEFAULT_CHARSET));
    }

    @Test
    public void formAdbRequestHandlesUtf8() {
        String asciiTestString = "foo";
        // Note the last letter below is not an ASCII "O",
        // instead it's a 3-byte character when encoded in UTF-8.
        String utf8TestString = "fōଠ";

        assertArrayEquals(("0003"+asciiTestString).getBytes(DEFAULT_CHARSET),
                          formAdbRequest(asciiTestString));
        assertArrayEquals(("0006"+utf8TestString).getBytes(DEFAULT_CHARSET),
                          formAdbRequest(utf8TestString));
    }
}

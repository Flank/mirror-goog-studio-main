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
package com.android.utils;

import junit.framework.TestCase;

/**
 * Test for OffsetTrackingDecodedXmlValue class.
 */
public class OffsetTrackingDecodedXmlValueTest extends TestCase {
    public void testUnescaping() throws Exception {
        OffsetTrackingDecodedXmlValue decoded =
                new OffsetTrackingDecodedXmlValue("&lt;&gt;&amp;&apos;&quot;&quot");
        assertEquals("<>&'\"&quot", decoded.getDecodedCharacters().toString());
        assertEquals(-1, decoded.getEncodedOffset(-1));
        int[] expectedOffsets = {0, 4, 8, 13, 19, 25};
        for (int i = 0; i < expectedOffsets.length; i++) {
            assertEquals(expectedOffsets[i], decoded.getEncodedOffset(i));
        }
        for (int i = 0; i < 10; i++) {
            assertEquals(expectedOffsets[expectedOffsets.length - 1] + i,
                    decoded.getEncodedOffset(expectedOffsets.length + i));
        }
    }

    public void testNoEscapedCharacters() throws Exception {
        String original = "no encoded characters";
        OffsetTrackingDecodedXmlValue decoded = new OffsetTrackingDecodedXmlValue(original);
        assertEquals(original, decoded.getDecodedCharacters().toString());
        for (int i = -1; i <= original.length(); i++) {
            assertEquals(i, decoded.getEncodedOffset(i));
        }
    }
}

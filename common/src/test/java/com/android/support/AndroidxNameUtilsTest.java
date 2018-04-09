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
package com.android.support;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AndroidxNameUtilsTest {

    @Test
    public void notExistingMappings() {
        assertEquals("a.a.a.a:a", AndroidxNameUtils.getCoordinateMapping("a.a.a.a:a"));
        assertEquals(":a", AndroidxNameUtils.getCoordinateMapping(":a"));
        assertEquals("", AndroidxNameUtils.getCoordinateMapping(""));
    }

    @Test
    public void androidxMappings() {
        for (String oldCoordinate : AndroidxNameUtils.ANDROIDX_COORDINATES_MAPPING.keySet()) {
            String androidxCoordinate = AndroidxNameUtils.getCoordinateMapping(oldCoordinate);
            assertNotEquals(oldCoordinate, androidxCoordinate);
            assertFalse(oldCoordinate.startsWith("androidx"));
            // It seems material won't be in the androidx package name
            assertTrue(
                    androidxCoordinate,
                    androidxCoordinate.startsWith("androidx")
                            || androidxCoordinate.startsWith("com.google.android.material")
                            || androidxCoordinate.startsWith("android.test.legacy"));
            assertEquals(androidxCoordinate, AndroidxNameUtils.getCoordinateMapping(oldCoordinate));
        }
    }
}

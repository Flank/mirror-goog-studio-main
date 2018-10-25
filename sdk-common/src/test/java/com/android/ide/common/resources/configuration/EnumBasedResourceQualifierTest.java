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
package com.android.ide.common.resources.configuration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.resources.Density;
import org.junit.Test;

public class EnumBasedResourceQualifierTest {

    @SuppressWarnings("SimplifiableJUnitAssertion")
    @Test
    public void equalityTest() {
        DensityQualifier highDensity1 = new DensityQualifier(Density.HIGH);
        DensityQualifier highDensity2 = new DensityQualifier(Density.HIGH);
        DensityQualifier lowDensity = new DensityQualifier(Density.LOW);
        LayoutDirectionQualifier layoutDirection1 = new LayoutDirectionQualifier(null);
        LayoutDirectionQualifier layoutDirection2 = new LayoutDirectionQualifier(null);
        HighDynamicRangeQualifier highDynamicRangeQualifier = new HighDynamicRangeQualifier(null);
        assertTrue(highDensity1.equals(highDensity2));
        assertFalse(highDensity1.equals(null));
        assertFalse(highDensity1.equals(lowDensity));
        assertFalse(highDensity1.equals(layoutDirection1));
        assertTrue(layoutDirection2.equals(layoutDirection1));
        assertFalse(highDynamicRangeQualifier.equals(layoutDirection1));
    }
}

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
package com.android.ide.common.resources.sampledata;

import static org.junit.Assert.*;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class SampleDataManagerTest {
    /**
     * Verify that the data manager cursor handles correctly end of content, empty lines and
     * trailing new lines at the end of the file.
     */
    @Test
    public void sampleDataLine() throws Exception {
        SampleDataManager manager = new SampleDataManager();
        ImmutableList<String> content = ImmutableList.of("Line 1", "", "Line 3");

        assertEquals("Line 1", manager.getSampleDataLine("res1", content));
        assertEquals("Line 1", manager.getSampleDataLine("res2", content));
        assertEquals("", manager.getSampleDataLine("res1", content));
        assertEquals("Line 3", manager.getSampleDataLine("res1", content));
        assertEquals("Line 1", manager.getSampleDataLine("res1", content));
        assertEquals("", manager.getSampleDataLine("res2", content));

        ImmutableList<String> emptyContent = ImmutableList.of();
        assertEquals("", manager.getSampleDataLine("res3", emptyContent));
        assertEquals("", manager.getSampleDataLine("res3", emptyContent));

        ImmutableList<String> oneLine = ImmutableList.of("One line");
        assertEquals("One line", manager.getSampleDataLine("res4", oneLine));
        assertEquals("One line", manager.getSampleDataLine("res4", oneLine));
    }

    @Test
    public void indexReferences() {
        SampleDataManager manager = new SampleDataManager();
        ImmutableList<String> content =
                ImmutableList.of("Line A", "Line B", "Line C", "Line D", "Line E");

        assertEquals("Line A", manager.getSampleDataLine("res1", content));
        assertEquals("Line A", manager.getSampleDataLine("res1[0]", content));
        assertEquals("Line A", manager.getSampleDataLine("res1[0]", content));
        assertEquals("Line E", manager.getSampleDataLine("res1[4]", content));
        // The cursor should be at the same position it was before
        assertEquals("Line B", manager.getSampleDataLine("res1", content));

        // Out of bounds (wraps around)
        assertEquals("Line A", manager.getSampleDataLine("res1[5]", content));
        // Invalid index
        assertEquals("", manager.getSampleDataLine("res1[-1]", content));

        // String matching (used for searching image file names e.g: @sample/images[biking.png])
        assertEquals("Line C", manager.getSampleDataLine("res1[C]", content));
    }

    @Test
    public void subArrays() {
        SampleDataManager manager = new SampleDataManager();
        ImmutableList<String> content =
                ImmutableList.of("Line A", "Line B", "Line C", "Line D", "Line E");

        assertEquals("Line B", manager.getSampleDataLine("res1[1:2]", content));
        assertEquals("Line C", manager.getSampleDataLine("res1[1:2]", content));
        assertEquals("Line B", manager.getSampleDataLine("res1[1:2]", content));
        assertEquals("Line C", manager.getSampleDataLine("res1[1:2]", content));

        // Invalid indexes
        assertEquals("", manager.getSampleDataLine("res1[-1:2]", content));
        assertEquals("", manager.getSampleDataLine("res1[2:1]", content));

        // Test only lower bound
        // Using a different resource name to start a different cursor (it will start again from Line A)
        assertEquals("Line D", manager.getSampleDataLine("res2[3:]", content));
        assertEquals("Line E", manager.getSampleDataLine("res2[3:]", content));
        assertEquals("Line D", manager.getSampleDataLine("res2[3:]", content));
        assertEquals("", manager.getSampleDataLine("res2[9:]", content));

        // Test only upper bound
        assertEquals("Line A", manager.getSampleDataLine("res3[:1]", content));
        assertEquals("Line B", manager.getSampleDataLine("res3[:1]", content));
        assertEquals("Line A", manager.getSampleDataLine("res3[:1]", content));
        assertEquals("", manager.getSampleDataLine("res3[:-1]", content));
    }

    @Test
    public void resourceNameFromSampleReference() {
        assertEquals(
                "resourceName",
                SampleDataManager.getResourceNameFromSampleReference("resourceName"));
        assertEquals(
                "resourceName",
                SampleDataManager.getResourceNameFromSampleReference("resourceName[50]"));
        assertEquals(
                "resourceName",
                SampleDataManager.getResourceNameFromSampleReference("resourceName[something]"));
        assertEquals(
                "resourceName",
                SampleDataManager.getResourceNameFromSampleReference("resourceName[[]"));
        assertEquals("", SampleDataManager.getResourceNameFromSampleReference(""));
    }
}

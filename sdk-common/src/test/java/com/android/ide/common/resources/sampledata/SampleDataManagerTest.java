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

import org.junit.Test;

public class SampleDataManagerTest {
    /**
     * Verify that the data manager cursor handles correctly end of content, empty lines and
     * trailing new lines at the end of the file.
     */
    @Test
    public void sampleDataLine() throws Exception {
        SampleDataManager manager = new SampleDataManager();
        String content = "Line 1\n" + "\n" + "Line 3";

        assertEquals("Line 1", manager.getSampleDataLine("res1", content));
        assertEquals("Line 1", manager.getSampleDataLine("res2", content));
        assertEquals("", manager.getSampleDataLine("res1", content));
        assertEquals("Line 3", manager.getSampleDataLine("res1", content));
        assertEquals("Line 1", manager.getSampleDataLine("res1", content));
        assertEquals("", manager.getSampleDataLine("res2", content));

        String emptyContent = "";
        assertEquals("", manager.getSampleDataLine("res3", emptyContent));
        assertEquals("", manager.getSampleDataLine("res3", emptyContent));

        String oneLine = "One line\n";
        assertEquals("One line", manager.getSampleDataLine("res4", oneLine));
        assertEquals("One line", manager.getSampleDataLine("res4", oneLine));
    }
}

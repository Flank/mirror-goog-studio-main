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
package com.android.tools.apk.analyzer;

import static com.google.common.truth.Truth.assertThat;

import com.android.testutils.TestResources;
import com.android.tools.apk.analyzer.internal.GzipSizeCalculator;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class GzipSizeCalculatorParsingTest {
    private ApkSizeCalculator calculator;

    @Before
    public void setup() {
        calculator = new GzipSizeCalculator();
    }

    @Test
    public void virtualEntryParsing() throws IOException {
        Path apkWithVirtualEntry = TestResources.getFile("/app_with_virtual_entry.apk").toPath();

        // Make sure the archive has one virtual entry.
        VirtualEntryCalculator veCalculator = new VirtualEntryCalculator(apkWithVirtualEntry);
        assertThat(veCalculator.getCount()).isEqualTo(new Long(1));

        Map<String, Long> entries = calculator.getDownloadSizePerFile(apkWithVirtualEntry);
        assertThat(entries.size()).isNotEqualTo(0);
        assertThat(entries.get(GzipSizeCalculator.VIRTUAL_ENTRY_NAME)).isNull();
    }

    @Test
    public void virtualEntriesParsing() throws IOException {
        Path apkWithVirtualEntries =
                TestResources.getFile("/app_with_virtual_entries.apk").toPath();

        // Make sure the archive has more than one virtual entries.
        VirtualEntryCalculator veCalculator = new VirtualEntryCalculator(apkWithVirtualEntries);
        assertThat(veCalculator.getCount()).isEqualTo(new Long(3));

        Map<String, Long> entries = calculator.getDownloadSizePerFile(apkWithVirtualEntries);
        assertThat(entries.size()).isNotEqualTo(0);
        assertThat(entries.get(GzipSizeCalculator.VIRTUAL_ENTRY_NAME)).isNull();
    }
}

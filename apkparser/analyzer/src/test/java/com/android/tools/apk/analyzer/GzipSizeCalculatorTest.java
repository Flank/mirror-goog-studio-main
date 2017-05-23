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

package com.android.tools.apk.analyzer;

import static com.google.common.truth.Truth.assertThat;

import com.android.testutils.TestResources;
import com.android.tools.apk.analyzer.internal.GzipSizeCalculator;
import java.nio.file.Path;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class GzipSizeCalculatorTest {
    private ApkSizeCalculator calculator;
    private Path apk;

    @Before
    public void setup() {
        apk = TestResources.getFile("/test.apk").toPath();
        calculator = new GzipSizeCalculator();
    }

    @Test
    public void getFullApkDownloadSize() {
        long expected = 502; // gzip -9 test.apk; ls -l test.apk.gz
        assertThat(calculator.getFullApkDownloadSize(apk)).isEqualTo(expected);
    }

    @Test
    public void getFullApkRawSize() {
        long expected = 960; // ls -l test.apk
        assertThat(calculator.getFullApkRawSize(apk)).isEqualTo(expected);
    }

    @Test
    public void getDownloadSizePerFile() {
        Map<String, Long> downloadSizePerFile = calculator.getDownloadSizePerFile(apk);

        // The expected values can be seen via unzip -lv resources/test.apk
        // Note: for this test apk, the re-compressing at "zip -9" actually has no impact.
        assertThat(downloadSizePerFile.get("/AndroidManifest.xml")).isEqualTo(11);
        assertThat(downloadSizePerFile.get("/instant-run.zip")).isEqualTo(153);
        assertThat(downloadSizePerFile.get("/res/"))
                .isNull(); // directories should not have any size
    }

    @Test
    public void getRawSizePerFile() {
        Map<String, Long> rawSizePerFile = calculator.getRawSizePerFile(apk);

        // The expected values can be seen via unzip -lv resources/test.apk
        assertThat(rawSizePerFile.get("/AndroidManifest.xml")).isEqualTo(11);
        assertThat(rawSizePerFile.get("/instant-run.zip")).isEqualTo(153);
        assertThat(rawSizePerFile.get("/res/")).isNull(); // directories should not have any size
    }
}

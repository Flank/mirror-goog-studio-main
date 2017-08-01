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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link ApkAnalyzerCli} */
public class ApkAnalyzerCliTest {
    private ApkAnalyzerCli cli;
    private ByteArrayOutputStream baos;
    private ByteArrayOutputStream baosErr;
    private ApkAnalyzerImpl impl;

    @Before
    public void setUp() throws Exception {
        baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        baosErr = new ByteArrayOutputStream();
        PrintStream psErr = new PrintStream(baosErr);
        impl = mock(ApkAnalyzerImpl.class);
        cli =
                new ApkAnalyzerCli(ps, psErr, impl) {
                    @Override
                    protected void exit(int code) {
                        throw new RuntimeException("Exiting with code " + code);
                    }
                };
    }

    @Test
    public void commandRoutingTest() {
        cli.run("apk", "summary", "apk1");
        verify(impl).apkSummary(Paths.get("apk1"));

        cli.run("apk", "compare", "apk1", "apk2");
        verify(impl).apkCompare(Paths.get("apk1"), Paths.get("apk2"), false, false, false);

        cli.run("apk", "compare", "--files-only", "apk1", "apk2");
        cli.run("-h", "apk", "compare", "--files-only", "apk1", "apk2");
        verify(impl, times(2)).apkCompare(Paths.get("apk1"), Paths.get("apk2"), false, true, false);
    }

    @Test
    public void humanSizesTest() {
        cli.run("-h", "apk", "summary", "apk1");
        verify(impl).setHumanReadableFlag(true);

        cli.run( "apk", "summary", "apk1");
        verify(impl).setHumanReadableFlag(false);
    }
}

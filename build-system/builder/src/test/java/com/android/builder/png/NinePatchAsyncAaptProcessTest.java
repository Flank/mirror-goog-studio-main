/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.builder.png;

import com.android.annotations.NonNull;
import com.android.ide.common.internal.ResourceCompilationException;
import com.android.ide.common.internal.ResourceProcessor;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.collect.Maps;
import com.google.common.truth.Expect;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.DataFormatException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Asynchronous version of the aapt cruncher test.
 */
@RunWith(Parameterized.class)
public class NinePatchAsyncAaptProcessTest {

    @ClassRule
    public static Expect expect = Expect.createAndEnableStackTrace();

    private static Map<File, File> sSourceAndCrunchedFiles;

    private static final AtomicLong sClassStartTime = new AtomicLong();
    private static final AtomicInteger sCruncherKey = new AtomicInteger();
    private static final ResourceProcessor sCruncher = getCruncher();

    private final File mFile;

    public NinePatchAsyncAaptProcessTest(File file, String testName) {
        mFile = file;
    }

    @BeforeClass
    public static void setup() {
        sSourceAndCrunchedFiles = Maps.newHashMap();
    }

    @Test
    public void run() throws ResourceCompilationException, IOException {
        File outFile = NinePatchAaptProcessorTestUtils.crunchFile(
                sCruncherKey.get(), mFile, sCruncher);
        sSourceAndCrunchedFiles.put(mFile, outFile);
    }

    @AfterClass
    public static void tearDownAndCheck()
            throws IOException, DataFormatException, InterruptedException {

        NinePatchAaptProcessorTestUtils.tearDownAndCheck(
                sCruncherKey.get(), sSourceAndCrunchedFiles, sCruncher, sClassStartTime, expect);
        sSourceAndCrunchedFiles = null;
    }

    @NonNull
    private static ResourceProcessor getCruncher() {
        ILogger logger = new StdLogger(StdLogger.Level.VERBOSE);
        File aapt = NinePatchAaptProcessorTestUtils.getAapt();
        return QueuedCruncher.builder()
                .executablePath(aapt.getAbsolutePath())
                .logger(logger)
                .build();
    }

    @Parameters(name = "{1}")
    public static Collection<Object[]> getNinePatches() {
        Collection<Object[]> params = NinePatchAaptProcessorTestUtils.getNinePatches();
        sClassStartTime.set(System.currentTimeMillis());
        sCruncherKey.set(sCruncher.start());
        return params;
    }
}

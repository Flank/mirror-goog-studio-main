/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.builder.internal.aapt.v2;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.AaptTestUtils;
import com.android.ide.common.process.DefaultProcessExecutor;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.res2.CompileResourceRequest;
import com.android.repository.Revision;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.testutils.TestUtils;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import java.io.File;
import java.util.concurrent.Future;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for {@code aapt2}.
 */
public class AaptV2Test {

    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    /**
     * Creates the {@link Aapt} instance.
     *
     * @return the instance
     * @throws Exception failed to create the {@link Aapt} instance
     */
    @NonNull
    private Aapt makeAapt() throws Exception {
        ILogger logger = new StdLogger(StdLogger.Level.VERBOSE);
        Revision revision = Revision.parseRevision("24.0.0 rc2");

        FakeProgressIndicator progress = new FakeProgressIndicator();
        BuildToolInfo buildToolInfo =
                AndroidSdkHandler.getInstance(TestUtils.getSdk())
                        .getLatestBuildTool(progress, true);
        if (buildToolInfo == null || buildToolInfo.getRevision().compareTo(revision) < 0) {
            throw new RuntimeException(
                    "Test requires at least build-tools revision " + revision.toShortString());
        }

        return new OutOfProcessAaptV2(
                new DefaultProcessExecutor(logger),
                new LoggedProcessOutputHandler(logger),
                buildToolInfo,
                mTemporaryFolder.newFolder(),
                logger);
    }

    @Test
    public void pngCrunchingTest() throws Exception {
        Aapt aapt = makeAapt();
        Future<File> compiledFuture =
                aapt.compile(
                        new CompileResourceRequest(
                                AaptTestUtils.getTestPng(mTemporaryFolder),
                                AaptTestUtils.getOutputDir(mTemporaryFolder),
                                "test"));
        File compiled = compiledFuture.get();
        assertNotNull(compiled);
        assertTrue(compiled.isFile());
    }

    @Test
    public void pngWithLongPathCrunchingTest() throws Exception {
        // This fails on Windows due to issues in aapt handling of long paths.
        Assume.assumeFalse(SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS);

        Aapt aapt = makeAapt();
        Future<File> compiledFuture =
                aapt.compile(
                        new CompileResourceRequest(
                                AaptTestUtils.getTestPngWithLongFileName(mTemporaryFolder),
                                AaptTestUtils.getOutputDir(mTemporaryFolder),
                                "test"));
        File compiled = compiledFuture.get();
        assertNotNull(compiled);
        assertTrue(compiled.isFile());
    }

    @Test
    public void resourceProcessingTest() throws Exception {
        Aapt aapt = makeAapt();
        Future<File> compiledFuture =
                aapt.compile(
                        new CompileResourceRequest(
                                AaptTestUtils.getTestTxt(mTemporaryFolder),
                                AaptTestUtils.getOutputDir(mTemporaryFolder),
                                "test"));
        File compiled = compiledFuture.get();
        assertNotNull(compiled);
        assertTrue(compiled.isFile());
    }
}

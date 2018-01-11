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

package com.android.builder.internal.aapt.v2;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.AaptTestUtils;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link QueueableAapt2}. */
public class QueueableAapt2Test {
    @Rule public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    /**
     * Creates the {@link Aapt} instance.
     *
     * @return the instance
     * @throws Exception failed to create the {@link Aapt} instance
     */
    @NonNull
    private Aapt makeAapt() throws Exception {
        Revision daemonRevision = BuildToolInfo.PathId.DAEMON_AAPT2.getMinRevision();

        FakeProgressIndicator daemonProgress = new FakeProgressIndicator();
        BuildToolInfo daemonBuildToolInfo =
                AndroidSdkHandler.getInstance(TestUtils.getSdk())
                        .getLatestBuildTool(daemonProgress, true);
        if (daemonBuildToolInfo == null
                || daemonBuildToolInfo.getRevision().compareTo(daemonRevision) < 0) {
            throw new RuntimeException(
                    "Test requires at least build-tools revision "
                            + daemonRevision.toShortString());
        }
        ILogger logger = new StdLogger(StdLogger.Level.VERBOSE);
        return new QueueableAapt2(
                new LoggedProcessOutputHandler(logger),
                daemonBuildToolInfo,
                logger,
                5);
    }

    @Test
    public void pngCrunchingTest() throws Exception {
        try (Aapt aapt = makeAapt()) {
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
    }

    @Test
    public void noStartTest() throws Exception {

        String notValidAapt2Executable =
                mTemporaryFolder
                        .newFolder()
                        .toPath()
                        .resolve("aapt2-that-does-not-exist")
                        .toString();

        ILogger logger = new StdLogger(StdLogger.Level.VERBOSE);
        try (Aapt aapt =
                new QueueableAapt2(
                        new LoggedProcessOutputHandler(logger),
                        notValidAapt2Executable,
                        logger,
                        5)) {
            aapt.compile(
                            new CompileResourceRequest(
                                    AaptTestUtils.getTestPng(mTemporaryFolder),
                                    AaptTestUtils.getOutputDir(mTemporaryFolder),
                                    "test"))
                    .get();
            fail("expected to not work");
        } catch (ExecutionException expected) {
            //yay
        }
    }
}

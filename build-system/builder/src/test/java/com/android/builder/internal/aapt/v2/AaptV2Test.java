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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.AaptTestUtils;
import com.android.builder.utils.FileCache;
import com.android.ide.common.internal.WaitableExecutor;
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
import java.nio.file.Files;
import java.util.concurrent.Future;
import org.junit.Assume;
import org.junit.AssumptionViolatedException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Tests for {@code aapt2}. */
@RunWith(Parameterized.class)
public class AaptV2Test {

    private enum AaptGeneration {
        AAPT_V2,
        AAPT_V2_JNI
    }

    @Parameterized.Parameters(name = "{0}")
    public static Object[] getParams() {
        return AaptGeneration.values();
    }

    @NonNull private final AaptGeneration generation;

    public AaptV2Test(@NonNull AaptGeneration generation) {
        this.generation = generation;
    }

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
        switch (generation) {
            case AAPT_V2:
                Revision revision = Revision.parseRevision("24.0.0 rc2");

                FakeProgressIndicator progress = new FakeProgressIndicator();
                BuildToolInfo buildToolInfo =
                        AndroidSdkHandler.getInstance(TestUtils.getSdk())
                                .getLatestBuildTool(progress, true);
                if (buildToolInfo == null || buildToolInfo.getRevision().compareTo(revision) < 0) {
                    throw new RuntimeException(
                            "Test requires at least build-tools revision "
                                    + revision.toShortString());
                }

                return new OutOfProcessAaptV2(
                        new DefaultProcessExecutor(logger),
                        new LoggedProcessOutputHandler(logger),
                        buildToolInfo,
                        mTemporaryFolder.newFolder(),
                        logger);
            case AAPT_V2_JNI:
                return new AaptV2Jni(
                        mTemporaryFolder.newFolder(),
                        WaitableExecutor.useDirectExecutor(),
                        new LoggedProcessOutputHandler(logger),
                        FileCache.getInstanceWithSingleProcessLocking(
                                mTemporaryFolder.newFolder()));
            default:
                throw new IllegalArgumentException();
        }
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
    public void crunchFlagIsRespected() throws Exception {
        if (generation == AaptGeneration.AAPT_V2) {
            throw new AssumptionViolatedException("Not in SDK version of AAPT2 yet");
        }

        Aapt aapt = makeAapt();
        File in = AaptTestUtils.getTestPng(mTemporaryFolder);
        File outDir = AaptTestUtils.getOutputDir(mTemporaryFolder);
        Future<File> compiledFuture =
                aapt.compile(new CompileResourceRequest(in, outDir, "test", false, true));
        long withCrunchEnabled = Files.size(compiledFuture.get().toPath());
        compiledFuture = aapt.compile(new CompileResourceRequest(in, outDir, "test", false, false));
        long withCrunchDisabled = Files.size(compiledFuture.get().toPath());
        assertThat(withCrunchEnabled).isLessThan(withCrunchDisabled);
    }

    @Test
    public void ninePatchPngsAlwaysProcessedEvenWhenCrunchingDisabled() throws Exception {
        Aapt aapt = makeAapt();
        File in = AaptTestUtils.getTest9Patch(mTemporaryFolder);
        File outDir = AaptTestUtils.getOutputDir(mTemporaryFolder);
        Future<File> compiledFuture =
                aapt.compile(new CompileResourceRequest(in, outDir, "test", false, true));
        byte[] withCrunchEnabled = Files.readAllBytes(compiledFuture.get().toPath());
        compiledFuture = aapt.compile(new CompileResourceRequest(in, outDir, "test", false, false));
        byte[] withCrunchDisabled = Files.readAllBytes(compiledFuture.get().toPath());
        assertThat(withCrunchDisabled).isEqualTo(withCrunchEnabled);
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

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

package com.android.builder.internal.aapt.v1;

import static com.android.testutils.TestUtils.eventually;
import static com.google.common.base.Verify.verifyNotNull;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.file.Files.readAllBytes;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.core.VariantType;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.AaptOptions;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.internal.aapt.AaptTestUtils;
import com.android.ide.common.process.DefaultProcessExecutor;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.res2.CompileResourceRequest;
import com.android.repository.Revision;
import com.android.repository.io.impl.FileOpImpl;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.targets.AndroidTargetManager;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.base.Charsets;
import com.google.common.base.Verify;
import com.google.common.collect.Range;
import com.google.common.io.Files;
import java.io.File;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for {@link AaptV1}.
 */
public class AaptV1Test {

    /**
     * Temporary folder to use in tests.
     */
    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    /**
     * Progress indicator to obtain managers.
     */
    @NonNull
    private final FakeProgressIndicator mProgressIndicator = new FakeProgressIndicator();

    /**
     * SDK handler that can obtain SDKs.
     */
    @NonNull
    private final AndroidSdkHandler mSdkHandler =
            AndroidSdkHandler.getInstance(TestUtils.getSdk());

    /**
     * Logger to use.
     */
    @NonNull
    private final ILogger mLogger = new StdLogger(StdLogger.Level.VERBOSE);

    /**
     * Target manager to use.
     */
    @NonNull
    private final AndroidTargetManager mTargetManager =
            new AndroidTargetManager(mSdkHandler, new FileOpImpl());

    @NonNull
    private final BuildToolInfo buildToolInfo =
            BuildToolInfo.fromLocalPackage(
                    Verify.verifyNotNull(
                            mSdkHandler.getPackageInRange(
                                    SdkConstants.FD_BUILD_TOOLS,
                                    Range.atLeast(AaptV1.VERSION_FOR_SERVER_AAPT),
                                    mProgressIndicator),
                            "Build tools above %s not found",
                            AaptV1.VERSION_FOR_SERVER_AAPT.toShortString()));


    /**
     * Creates the {@link Aapt} instance.
     *
     * @return the instance
     * @throws Exception failed to create the {@link Aapt} instance
     */
    @NonNull
    private Aapt makeAapt() throws Exception {
        return makeAapt(AaptV1.PngProcessMode.ALL, Range.atLeast(AaptV1.VERSION_FOR_SERVER_AAPT));
    }

    /**
     * Creates the {@link Aapt} instance.
     *
     * @param mode the PNG processing mode
     * @param revisionRange range of revisions of the build tools that can be used
     * @return the instance
     * @throws Exception failed to create the {@link Aapt} instance
     */
    @NonNull
    private Aapt makeAapt(
            @NonNull AaptV1.PngProcessMode mode,
            @NonNull Range<Revision> revisionRange)
            throws Exception {
        return new AaptV1(
                new DefaultProcessExecutor(mLogger),
                new LoggedProcessOutputHandler(mLogger),
                BuildToolInfo.fromLocalPackage(
                        verifyNotNull(
                                mSdkHandler.getPackageInRange(
                                        SdkConstants.FD_BUILD_TOOLS,
                                        revisionRange,
                                        mProgressIndicator),
                                "Build tools in range %s not found.",
                                revisionRange)),
                mLogger,
                mode,
                0);
    }

    @Test
    public void compilePng() throws Exception {
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
    public void compilePngWithLongPath() throws Exception {
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
    public void compileTxt() throws Exception {
        Aapt aapt = makeAapt();
        File shouldBeCopied = AaptTestUtils.getTestTxt(mTemporaryFolder);
        Future<File> compiledFuture =
                aapt.compile(
                        new CompileResourceRequest(
                                shouldBeCopied,
                                AaptTestUtils.getOutputDir(mTemporaryFolder),
                                "test"));
        File compiled = compiledFuture.get();
        assertTrue(compiled.isFile());
        assertThat(readAllBytes(compiled.toPath()))
                .isEqualTo(readAllBytes(shouldBeCopied.toPath()));
    }

    @Test
    public void parallelInterface() throws Exception {
        Aapt aapt = makeAapt();

        int parallel = 10;
        File[] imgs = new File[parallel];
        for (int i = 0; i < parallel; i++) {
            imgs[i] = mTemporaryFolder.newFile("i" + i + ".png");
            Files.copy(AaptTestUtils.getTestPng(mTemporaryFolder), imgs[i]);
        }

        @SuppressWarnings("unchecked")
        Future<File>[] futures = new Future[parallel];
        for (int i = 0; i < parallel; i++) {
            futures[i] =
                    aapt.compile(
                            new CompileResourceRequest(
                                    imgs[i], AaptTestUtils.getOutputDir(mTemporaryFolder), "test"));
            assertFalse(futures[i].isDone());
        }

        Set<File> results = new HashSet<>();
        for (int i = 0; i < parallel; i++) {
            File f = futures[i].get();
            assertTrue(results.add(f));
        }
    }

    @Test
    public void noCrunchPngIfBigger() throws Exception {
        Aapt aapt = makeAapt();

        File originalFile = AaptTestUtils.getNonCrunchableTestPng();

        Future<File> compiledFuture =
                aapt.compile(
                        new CompileResourceRequest(
                                originalFile,
                                AaptTestUtils.getOutputDir(mTemporaryFolder),
                                "test"));
        File compiled = compiledFuture.get();
        assertNotNull(compiled);
        assertTrue(compiled.isFile());

        eventually(
                () -> assertTrue(
                        "originalFile.length() ["
                                + originalFile.length()
                                + "] != compiled.length() ["
                                + compiled.length()
                                + "]",
                        originalFile.length() == compiled.length()),
                Duration.ofMinutes(2));
    }

    @Test
    public void crunchPngIfSmaller() throws Exception {
        Aapt aapt = makeAapt();

        File originalFile = AaptTestUtils.getCrunchableTestPng();

        Future<File> compiledFuture =
                aapt.compile(
                        new CompileResourceRequest(
                                originalFile,
                                AaptTestUtils.getOutputDir(mTemporaryFolder),
                                "test"));
        File compiled = compiledFuture.get();
        assertNotNull(compiled);
        assertTrue(compiled.isFile());

        eventually(
                () -> assertTrue(
                    "originalFile.length() ["
                            + originalFile.length()
                            + "] < compiled.length() ["
                            + compiled.length()
                            + "]",
                    originalFile.length() > compiled.length()));
    }

    @Test
    public void ninePatchPngsAlwaysProcessedEvenIfBigger() throws Exception {
        Aapt aapt = makeAapt();

        File originalFile = AaptTestUtils.getNinePatchTestPng();

        Future<File> compiledFuture =
                aapt.compile(
                        new CompileResourceRequest(
                                originalFile,
                                AaptTestUtils.getOutputDir(mTemporaryFolder),
                                "test"));
        File compiled = compiledFuture.get();
        assertNotNull(compiled);
        assertTrue(compiled.isFile());

        /*
         * We may have to wait until aapt flushes the file.
         */
        eventually(
                () -> assertTrue(
                        "originalFile.length() ["
                                + originalFile.length()
                                + "] > compiled.length() ["
                                + compiled.length()
                                + "]",
                        originalFile.length() < compiled.length()));
    }

    @Test
    public void generateRJavaInApplication() throws Exception {
        Aapt aapt = makeAapt();

        File outputDir = AaptTestUtils.getOutputDir(mTemporaryFolder);

        File originalFile = AaptTestUtils.getTestPng(mTemporaryFolder);
        Future<File> compiledFuture =
                aapt.compile(new CompileResourceRequest(originalFile, outputDir, "drawable"));
        if (compiledFuture.get() == null) {
            File ddir = new File(outputDir, "drawable");
            assertTrue(ddir.mkdir());
            Files.copy(originalFile, new File(ddir, originalFile.getName()));
        }

        File manifestFile = mTemporaryFolder.newFile("AndroidManifest.xml");
        Files.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\""
                + " package=\"com.example.aapt\"></manifest>", manifestFile, Charsets.US_ASCII);

        IAndroidTarget target23 = mTargetManager.getTargetOfAtLeastApiLevel(23, mProgressIndicator);

        File sourceOutput = mTemporaryFolder.newFolder("source-output");

        AaptPackageConfig config =
                new AaptPackageConfig.Builder()
                        .setAndroidTarget(target23)
                        .setBuildToolInfo(buildToolInfo)
                        .setLogger(mLogger)
                        .setManifestFile(manifestFile)
                        .setOptions(new AaptOptions(null, false, null))
                        .setSourceOutputDir(sourceOutput)
                        .setVariantType(VariantType.DEFAULT)
                        .setResourceDir(outputDir)
                        .build();
        aapt.link(config).get();

        File rJava = FileUtils.join(sourceOutput, "com", "example", "aapt", "R.java");
        assertTrue(rJava.isFile());

        String lenaResource = null;
        for (String line : Files.readLines(rJava, Charsets.US_ASCII)) {
            if (line.contains("int lena")) {
                lenaResource = line;
                break;
            }
        }

        assertNotNull(lenaResource);

        assertTrue(lenaResource.contains("public"));
        assertTrue(lenaResource.contains("static"));
        assertTrue(lenaResource.contains("final"));
    }

    @Test
    public void generateRJavaInLibrary() throws Exception {
        Aapt aapt = makeAapt();

        File outputDir = AaptTestUtils.getOutputDir(mTemporaryFolder);

        File originalFile = AaptTestUtils.getTestPng(mTemporaryFolder);
        Future<File> compiledFuture =
                aapt.compile(new CompileResourceRequest(originalFile, outputDir, "drawable"));
        if (compiledFuture.get() == null) {
            File ddir = new File(outputDir, "drawable");
            assertTrue(ddir.mkdir());
            Files.copy(originalFile, new File(ddir, originalFile.getName()));
        }

        File manifestFile = mTemporaryFolder.newFile("AndroidManifest.xml");
        Files.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\""
                + " package=\"com.example.aapt\"></manifest>", manifestFile, Charsets.US_ASCII);

        IAndroidTarget target23 = mTargetManager.getTargetOfAtLeastApiLevel(23, mProgressIndicator);

        File sourceOutput = mTemporaryFolder.newFolder("source-output");

        AaptPackageConfig config =
                new AaptPackageConfig.Builder()
                        .setAndroidTarget(target23)
                        .setBuildToolInfo(buildToolInfo)
                        .setLogger(mLogger)
                        .setManifestFile(manifestFile)
                        .setOptions(new AaptOptions(null, false, null))
                        .setSourceOutputDir(sourceOutput)
                        .setVariantType(VariantType.LIBRARY)
                        .setResourceDir(outputDir)
                        .build();
        aapt.link(config).get();

        File rJava = FileUtils.join(sourceOutput, "com", "example", "aapt", "R.java");
        assertTrue(rJava.isFile());

        String lenaResource = null;
        for (String line : Files.readLines(rJava, Charsets.US_ASCII)) {
            if (line.contains("int lena")) {
                lenaResource = line;
                break;
            }
        }

        assertNotNull(lenaResource);

        assertTrue(lenaResource.contains("public"));
        assertTrue(lenaResource.contains("static"));
        assertFalse(lenaResource.contains("final"));
    }
}

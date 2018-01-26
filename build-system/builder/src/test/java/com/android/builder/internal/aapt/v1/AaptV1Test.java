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
import com.google.common.collect.Range;
import com.google.common.io.Files;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    /**
     * Creates the {@link Aapt} instance.
     *
     * @return the instance
     */
    @NonNull
    private Aapt makeAapt() {
        return makeAapt(AaptV1.PngProcessMode.ALL, Range.atLeast(AaptV1.VERSION_FOR_SERVER_AAPT));
    }

    /**
     * Creates the {@link Aapt} instance.
     *
     * @param revisionRange range of revisions of the build tools that can be used
     */
    @NonNull
    private Aapt makeAapt(
            @NonNull AaptV1.PngProcessMode mode, @NonNull Range<Revision> revisionRange) {
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
    public void compilePngWithLongPath() throws Exception {
        try (Aapt aapt = makeAapt()) {
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
    }

    @Test
    public void compileTxt() throws Exception {
        try (Aapt aapt = makeAapt()) {
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
    }

    @Test
    public void parallelInterface() throws Exception {
        try (Aapt aapt = makeAapt()) {

            int parallel = 10;
            List<File> imgs = new ArrayList<>(parallel);
            for (int i = 0; i < parallel; i++) {
                File img = mTemporaryFolder.newFile("i" + i + ".png");
                Files.copy(AaptTestUtils.getTestPng(mTemporaryFolder), img);
                imgs.add(img);
            }

            List<Future<File>> futures = new ArrayList<>(parallel);
            for (File img : imgs) {
                Future<File> f =
                        aapt.compile(
                                new CompileResourceRequest(
                                        img, AaptTestUtils.getOutputDir(mTemporaryFolder), "test"));
                assertFalse(f.isDone());
                futures.add(f);
            }

            Set<File> results = new HashSet<>();
            for (Future<File> future : futures) {
                File f = future.get();
                assertTrue(results.add(f));
            }
        }
    }

    @Test
    public void noCrunchPngIfBigger() throws Exception {
        try (Aapt aapt = makeAapt()) {

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
                    () ->
                            assertTrue(
                                    "originalFile.length() ["
                                            + originalFile.length()
                                            + "] != compiled.length() ["
                                            + compiled.length()
                                            + "]",
                                    originalFile.length() == compiled.length()),
                    Duration.ofMinutes(2));
        }
    }

    @Test
    public void crunchPngIfSmaller() throws Exception {
        try (Aapt aapt = makeAapt()) {

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
                    () ->
                            assertTrue(
                                    "originalFile.length() ["
                                            + originalFile.length()
                                            + "] < compiled.length() ["
                                            + compiled.length()
                                            + "]",
                                    originalFile.length() > compiled.length()));
        }
    }

    @Test
    public void ninePatchPngsAlwaysProcessedEvenIfBigger() throws Exception {
        try (Aapt aapt = makeAapt()) {

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
                    () ->
                            assertTrue(
                                    "originalFile.length() ["
                                            + originalFile.length()
                                            + "] > compiled.length() ["
                                            + compiled.length()
                                            + "]",
                                    originalFile.length() < compiled.length()));
        }
    }

    @Test
    public void generateRJavaInApplication() throws Exception {
        try (Aapt aapt = makeAapt()) {

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
            FileUtils.writeToFile(
                    manifestFile,
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\""
                            + " package=\"com.example.aapt\"></manifest>");

            IAndroidTarget target23 =
                    mTargetManager.getTargetOfAtLeastApiLevel(23, mProgressIndicator);

            File sourceOutput = mTemporaryFolder.newFolder("source-output");

            AaptPackageConfig config =
                    new AaptPackageConfig.Builder()
                            .setAndroidTarget(target23)
                            .setManifestFile(manifestFile)
                            .setOptions(new AaptOptions(null, false, null))
                            .setSourceOutputDir(sourceOutput)
                            .setVariantType(VariantType.DEFAULT)
                            .setResourceDir(outputDir)
                            .build();
            aapt.link(config, mLogger);

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
    }

    @Test
    public void generateRJavaInLibrary() throws Exception {
        try (Aapt aapt = makeAapt()) {
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
            FileUtils.writeToFile(
                    manifestFile,
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\""
                            + " package=\"com.example.aapt\"></manifest>");

            IAndroidTarget target23 =
                    mTargetManager.getTargetOfAtLeastApiLevel(23, mProgressIndicator);

            File sourceOutput = mTemporaryFolder.newFolder("source-output");

            AaptPackageConfig config =
                    new AaptPackageConfig.Builder()
                            .setAndroidTarget(target23)
                            .setManifestFile(manifestFile)
                            .setOptions(new AaptOptions(null, false, null))
                            .setSourceOutputDir(sourceOutput)
                            .setVariantType(VariantType.LIBRARY)
                            .setResourceDir(outputDir)
                            .build();
            aapt.link(config, mLogger);

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

    @Test
    public void callToCompileOutputForDoesNotCreateDirectories() throws Exception {
        try (Aapt aapt = makeAapt()) {
            File outputDir = mTemporaryFolder.newFolder("empty");
            File input = new File(mTemporaryFolder.newFolder("values"), "values.xml");

            CompileResourceRequest request = new CompileResourceRequest(input, outputDir, "values");
            File output = aapt.compileOutputFor(request);

            assertFalse(output.exists());
            assertFalse(output.getParentFile().exists());
        }
    }
}

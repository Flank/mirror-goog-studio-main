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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.core.VariantType;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.internal.aapt.AaptTestUtils;
import com.android.builder.model.AaptOptions;
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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.io.Files;
import java.io.File;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
     * Dummy aapt options.
     */
    @NonNull
    private final AaptOptions mDummyOptions = new AaptOptions() {
        @Override
        public String getIgnoreAssets() {
            return null;
        }

        @Override
        public Collection<String> getNoCompress() {
            return null;
        }

        @Override
        public boolean getFailOnMissingConfigEntry() {
            return false;
        }

        @Override
        public List<String> getAdditionalParameters() {
            return null;
        }
    };



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

        AaptPackageConfig config = new AaptPackageConfig.Builder()
                .setAndroidTarget(target23)
                .setBuildToolInfo(buildToolInfo)
                .setLogger(mLogger)
                .setManifestFile(manifestFile)
                .setOptions(mDummyOptions)
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

        AaptPackageConfig config = new AaptPackageConfig.Builder()
                .setAndroidTarget(target23)
                .setBuildToolInfo(buildToolInfo)
                .setLogger(mLogger)
                .setManifestFile(manifestFile)
                .setOptions(mDummyOptions)
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

    /**
     * Tests the feature split functionality. Ensures resources IDs do not collide across resources.
     *
     * This test creates one base feature and two features splits called featA and featB. The
     * resource package and R class are then generated for each feature and the generated resource
     * IDs are compared to ensure they are globally unique.
     */
    @Test
    public void generateRJavaWithFeatureSplit() throws Exception {
        Aapt aapt = makeAapt();
        IAndroidTarget target24 = mTargetManager.getTargetOfAtLeastApiLevel(24, mProgressIndicator);
        Pattern hexPattern = Pattern.compile("0x(\\p{XDigit}{8})");

        // Generate base feature resources.
        File baseDir = mTemporaryFolder.newFolder("base");

        String baseManifest = ""
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.aapt\"\n"
                + "    split=\"base\">\n"
                + "    <application\n"
                + "        android:label=\"@string/base\"\n>"
                + "    </application>\n"
                + "</manifest>\n";
        File baseManifestFile = new File(baseDir, "AndroidManifest.xml");
        FileUtils.createFile(baseManifestFile, baseManifest);

        File baseResourceDir = new File(baseDir, "res");
        String baseResource = ""
                + "<resources>\n"
                + "    <string name=\"base\">base</string>\n"
                + "</resources>";
        File baseResourceFile = FileUtils.join(baseResourceDir, "values", "strings.xml");
        FileUtils.createFile(baseResourceFile, baseResource);

        File baseSourceOutput = new File(baseDir, "source-output");
        FileUtils.mkdirs(baseSourceOutput);
        File baseResourceApk = new File(baseDir, "resources.ap_");

        // Build base resource package and its R class.
        AaptPackageConfig baseConfig = new AaptPackageConfig.Builder()
                .setAndroidTarget(target24)
                .setBuildToolInfo(buildToolInfo)
                .setLogger(mLogger)
                .setManifestFile(baseManifestFile)
                .setOptions(mDummyOptions)
                .setSourceOutputDir(baseSourceOutput)
                .setVariantType(VariantType.DEFAULT)
                .setResourceDir(baseResourceDir)
                .setResourceOutputApk(baseResourceApk)
                .build();
        aapt.link(baseConfig).get();

        File baseRJava = FileUtils.join(baseSourceOutput, "com", "example", "aapt", "R.java");
        assertTrue(baseRJava.isFile());

        String baseResLine = null;
        for (String line : Files.readLines(baseRJava, Charsets.US_ASCII)) {
            if (line.contains("int base")) {
                baseResLine = line;
                break;
            }
        }

        assertNotNull(baseResLine);

        assertTrue(baseResLine.contains("public"));
        assertTrue(baseResLine.contains("static"));
        assertTrue(baseResLine.contains("final"));

        Matcher baseMatcher = hexPattern.matcher(baseResLine);
        assertTrue(baseMatcher.find());
        Long baseResValue = Long.parseLong(baseMatcher.group(1), 16);

        // Generate feature A resources.
        File featADir = mTemporaryFolder.newFolder("featA");

        String featAManifest = ""
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.aapt\"\n"
                + "    split=\"featA\">\n"
                + "    <application\n"
                + "        android:label=\"@string/base\"\n>"
                + "    </application>\n"
                + "</manifest>\n";
        File featAManifestFile = new File(featADir, "AndroidManifest.xml");
        FileUtils.createFile(featAManifestFile, featAManifest);

        File featAResourceDir = new File(featADir, "res");
        String featAResource = ""
                + "<resources>\n"
                + "    <string name=\"featA\">featA</string>\n"
                + "</resources>";
        File featAResourceFile = FileUtils.join(featAResourceDir, "values", "strings.xml");
        FileUtils.createFile(featAResourceFile, featAResource);

        File featASourceOutput = new File(featADir, "source-output");
        FileUtils.mkdirs(featASourceOutput);
        File featAResourceApk = new File(featADir, "resources.ap_");

        // Build feature A resource package and its R class.
        AaptPackageConfig featAConfig = new AaptPackageConfig.Builder()
                .setAndroidTarget(target24)
                .setBuildToolInfo(buildToolInfo)
                .setLogger(mLogger)
                .setManifestFile(featAManifestFile)
                .setOptions(mDummyOptions)
                .setSourceOutputDir(featASourceOutput)
                .setVariantType(VariantType.DEFAULT)
                .setResourceDir(featAResourceDir)
                .setResourceOutputApk(featAResourceApk)
                .setBaseFeature(baseResourceApk)
                .build();
        aapt.link(featAConfig).get();

        File featARJava = FileUtils.join(featASourceOutput, "com", "example", "aapt", "R.java");
        assertTrue(featARJava.isFile());

        String featAResLine = null;
        for (String line : Files.readLines(featARJava, Charsets.US_ASCII)) {
            if (line.contains("int featA")) {
                featAResLine = line;
                break;
            }
        }

        assertNotNull(featAResLine);

        assertTrue(featAResLine.contains("public"));
        assertTrue(featAResLine.contains("static"));
        assertTrue(featAResLine.contains("final"));

        // Tests that the resource IDs are different.
        Matcher featAMatcher = hexPattern.matcher(featAResLine);
        assertTrue(featAMatcher.find());
        Long featAResValue = Long.parseLong(featAMatcher.group(1), 16);
        assertNotSame(baseResValue, featAResValue);

        // Generate feature B resources.
        File featBDir = mTemporaryFolder.newFolder("featB");

        String featBManifest = ""
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.aapt\"\n"
                + "    split=\"featB\">\n"
                + "    <application\n"
                + "        android:label=\"@string/base\"\n>"
                + "    </application>\n"
                + "</manifest>\n";
        File featBManifestFile = new File(featBDir, "AndroidManifest.xml");
        FileUtils.createFile(featBManifestFile, featBManifest);

        File featBResourceDir = new File(featBDir, "res");
        String featBResource = ""
                + "<resources>\n"
                + "    <string name=\"featB\">featB</string>\n"
                + "</resources>";
        File featBResourceFile = FileUtils.join(featBResourceDir, "values", "strings.xml");
        FileUtils.createFile(featBResourceFile, featBResource);

        File featBSourceOutput = new File(featBDir, "source-output");
        FileUtils.mkdirs(featBSourceOutput);
        File featBResourceApk = new File(featBDir, "resources.ap_");

        // Build feature B resource package and its R class.
        AaptPackageConfig featBConfig = new AaptPackageConfig.Builder()
                .setAndroidTarget(target24)
                .setBuildToolInfo(buildToolInfo)
                .setLogger(mLogger)
                .setManifestFile(featBManifestFile)
                .setOptions(mDummyOptions)
                .setSourceOutputDir(featBSourceOutput)
                .setVariantType(VariantType.DEFAULT)
                .setResourceDir(featBResourceDir)
                .setResourceOutputApk(featBResourceApk)
                .setBaseFeature(baseResourceApk)
                .setPreviousFeatures(ImmutableSet.of(featAResourceApk))
                .build();
        aapt.link(featBConfig).get();

        File featBRJava = FileUtils.join(featBSourceOutput, "com", "example", "aapt", "R.java");
        assertTrue(featBRJava.isFile());

        String featBResLine = null;
        for (String line : Files.readLines(featBRJava, Charsets.US_ASCII)) {
            if (line.contains("int featB")) {
                featBResLine = line;
                break;
            }
        }

        assertNotNull(featBResLine);

        assertTrue(featBResLine.contains("public"));
        assertTrue(featBResLine.contains("static"));
        assertTrue(featBResLine.contains("final"));

        // Tests that the resource IDs are different.
        Matcher featBMatcher = hexPattern.matcher(featBResLine);
        assertTrue(featBMatcher.find());
        Long featBResValue = Long.parseLong(featBMatcher.group(1), 16);
        assertNotSame(baseResValue, featBResValue);
        assertNotSame(featAResValue, featBResValue);
    }
}

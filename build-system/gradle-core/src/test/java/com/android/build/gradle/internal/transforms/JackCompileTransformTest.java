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

package com.android.build.gradle.internal.transforms;

import static com.android.build.gradle.internal.transforms.JackTestUtils.fileForClass;
import static com.android.testutils.truth.MoreTruth.assertThat;
import static com.android.testutils.truth.MoreTruth.assertThatDex;
import static com.android.testutils.truth.MoreTruth.assertThatZip;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.transforms.JackTestUtils.SourceFile;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.DefaultApiVersion;
import com.android.builder.core.ErrorReporter;
import com.android.builder.core.JackProcessOptions;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.repository.Revision;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.testutils.TestResources;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.io.Files;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileTree;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Tests Jack source compilation that is done by the {@link JackCompileTransform} transform.
 * Incremental and non-incremental mode is being tested, alongside the DEX output and other options.
 */
public class JackCompileTransformTest {

    private static final int API_LEVEL = SdkVersionInfo.HIGHEST_KNOWN_API;
    private static final String INCREMENTAL_DIR = "incremental";
    // this is a constant location of the incremental log
    private static final String INCREMENTAL_LOG = "90/DB6FA453FAAEE694DDA1D7F367D63ED388E56D";

    private static final String INCREMENTAL = "incremental";
    private static final String FULL = "full";

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /* Mocks needed to run the transform. */
    @Mock JavaProcessExecutor javaProcessExecutor;
    @Mock Context context;
    @Mock TransformOutputProvider transformOutputProvider;
    @Mock DefaultConfigurableFileTree sourceFiles;

    private ErrorReporter errorReporter;
    private BuildToolInfo buildToolInfo;
    private File androidJackOutput;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        AndroidSdkHandler handler = AndroidSdkHandler.getInstance(TestUtils.getSdk());
        Range<Revision> acceptedVersions =
                Range.closedOpen(AndroidBuilder.MIN_BUILD_TOOLS_REV, new Revision(26, 0, 0, 1));
        buildToolInfo =
                BuildToolInfo.fromLocalPackage(
                        Verify.verifyNotNull(
                                handler.getPackageInRange(
                                        SdkConstants.FD_BUILD_TOOLS,
                                        acceptedVersions,
                                        new FakeProgressIndicator())));

        androidJackOutput =
                TestResources.getFile(this.getClass(), "/testData/testing/android-25.jack");
        errorReporter = new NoOpErrorReporter();
    }

    @Test
    public void testInitialIncrementalCompilation()
            throws IOException, TransformException, InterruptedException {
        when(sourceFiles.getFiles())
                .thenReturn(
                        ImmutableSet.of(
                                fileForClass(temporaryFolder.getRoot(), SourceFile.USER_MODEL)));
        compileCurrentSourceTree();

        checkIncrementalLog(
                FULL,
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableSet.of(fileForClass(temporaryFolder.getRoot(), SourceFile.USER_MODEL)));
    }

    @Test
    public void testIncrementalAddedFile()
            throws IOException, TransformException, InterruptedException {
        when(sourceFiles.getFiles())
                .thenReturn(
                        ImmutableSet.of(
                                fileForClass(temporaryFolder.getRoot(), SourceFile.USER_MODEL)));
        compileCurrentSourceTree();

        when(sourceFiles.getFiles())
                .thenReturn(
                        ImmutableSet.of(
                                fileForClass(temporaryFolder.getRoot(), SourceFile.USER_MODEL),
                                fileForClass(temporaryFolder.getRoot(), SourceFile.ACCOUNT)));
        compileCurrentSourceTree();

        checkIncrementalLog(
                INCREMENTAL,
                ImmutableSet.of(fileForClass(temporaryFolder.getRoot(), SourceFile.ACCOUNT)),
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableSet.of(fileForClass(temporaryFolder.getRoot(), SourceFile.ACCOUNT)));
    }

    @Test
    public void testIncrementalRemovedFile()
            throws IOException, TransformException, InterruptedException {
        when(sourceFiles.getFiles())
                .thenReturn(
                        ImmutableSet.of(
                                fileForClass(temporaryFolder.getRoot(), SourceFile.USER_MODEL),
                                fileForClass(temporaryFolder.getRoot(), SourceFile.ACCOUNT),
                                fileForClass(temporaryFolder.getRoot(), SourceFile.PAYMENT)));
        compileCurrentSourceTree();

        if (!fileForClass(temporaryFolder.getRoot(), SourceFile.PAYMENT).delete()) {
            throw new IllegalStateException("Unable to remove the test source file");
        }
        when(sourceFiles.getFiles())
                .thenReturn(
                        ImmutableSet.of(
                                fileForClass(temporaryFolder.getRoot(), SourceFile.USER_MODEL),
                                fileForClass(temporaryFolder.getRoot(), SourceFile.ACCOUNT)));
        compileCurrentSourceTree();

        checkIncrementalLog(
                INCREMENTAL,
                ImmutableSet.of(),
                ImmutableList.of(fileForClass(temporaryFolder.getRoot(), SourceFile.PAYMENT)),
                ImmutableList.of(),
                ImmutableSet.of());
    }

    @Test
    public void testIncrementalModifiedFile()
            throws IOException, TransformException, InterruptedException {
        when(sourceFiles.getFiles())
                .thenReturn(
                        ImmutableSet.of(
                                fileForClass(temporaryFolder.getRoot(), SourceFile.USER_MODEL),
                                fileForClass(temporaryFolder.getRoot(), SourceFile.ACCOUNT),
                                fileForClass(temporaryFolder.getRoot(), SourceFile.PAYMENT)));
        compileCurrentSourceTree();

        // Jack uses timestamps, so we need to make sure this one is updated
        TestUtils.waitForFileSystemTick();
        Files.append(
                "\n",
                fileForClass(temporaryFolder.getRoot(), SourceFile.USER_MODEL),
                Charsets.UTF_8);
        TestUtils.waitForFileSystemTick();
        compileCurrentSourceTree();

        checkIncrementalLog(
                INCREMENTAL,
                ImmutableSet.of(),
                ImmutableList.of(),
                ImmutableList.of(fileForClass(temporaryFolder.getRoot(), SourceFile.USER_MODEL)),
                ImmutableSet.of());
    }

    @Test
    public void testIncrementalWithDex()
            throws TransformException, InterruptedException, IOException {
        when(sourceFiles.getFiles())
                .thenReturn(
                        ImmutableSet.of(
                                fileForClass(temporaryFolder.getRoot(), SourceFile.USER_MODEL),
                                fileForClass(temporaryFolder.getRoot(), SourceFile.ACCOUNT)));

        JackProcessOptions options =
                JackProcessOptions.builder(createJackOptions())
                        .setMultiDex(true)
                        .setGenerateDex(true)
                        .build();
        File dexOutput = FileUtils.join(temporaryFolder.getRoot(), "output_dex", "classes.dex");
        setDexOutput(dexOutput.getParentFile());
        compileCurrentSourceTree(options);

        assertThatDex(dexOutput).containsClass("Lcom/example/jack/UserModel;");
        assertThatDex(dexOutput).containsClass("Lcom/example/jack/Account;");

        when(sourceFiles.getFiles())
                .thenReturn(
                        ImmutableSet.of(
                                fileForClass(temporaryFolder.getRoot(), SourceFile.USER_MODEL),
                                fileForClass(temporaryFolder.getRoot(), SourceFile.ACCOUNT),
                                fileForClass(temporaryFolder.getRoot(), SourceFile.PAYMENT)));
        compileCurrentSourceTree(options);
        assertThatDex(dexOutput).containsClass("Lcom/example/jack/UserModel;");
        assertThatDex(dexOutput).containsClass("Lcom/example/jack/Account;");
        assertThatDex(dexOutput).containsClass("Lcom/example/jack/Payment;");
    }

    @Test
    public void testNonIncrementalWithMultiDex()
            throws IOException, TransformException, InterruptedException {
        when(sourceFiles.getFiles())
                .thenReturn(
                        ImmutableSet.of(
                                fileForClass(temporaryFolder.getRoot(), SourceFile.USER_MODEL),
                                fileForClass(temporaryFolder.getRoot(), SourceFile.ACCOUNT),
                                fileForClass(temporaryFolder.getRoot(), SourceFile.PAYMENT)));
        File outputJack = FileUtils.join(temporaryFolder.getRoot(), "output.jack");
        JackProcessOptions options =
                JackProcessOptions.builder(createJackOptions())
                        .setIncrementalDir(null)
                        .setMultiDex(true)
                        .setGenerateDex(true)
                        .setJackOutputFile(outputJack)
                        .build();
        File dexOutput = FileUtils.join(temporaryFolder.getRoot(), "output_dex", "classes.dex");
        setDexOutput(dexOutput.getParentFile());

        compileCurrentSourceTree(options);

        assertThatZip(outputJack).contains("jayce/com/example/jack/UserModel.jayce");
        assertThatZip(outputJack).contains("jayce/com/example/jack/Account.jayce");
        assertThatZip(outputJack).contains("jayce/com/example/jack/Payment.jayce");

        assertThatDex(dexOutput).containsClass("Lcom/example/jack/UserModel;");
        assertThatDex(dexOutput).containsClass("Lcom/example/jack/Account;");
        assertThatDex(dexOutput).containsClass("Lcom/example/jack/Payment;");
    }

    @Test
    public void testAdditionalParameters()
            throws IOException, TransformException, InterruptedException {
        when(sourceFiles.getFiles())
                .thenReturn(
                        ImmutableSet.of(
                                fileForClass(temporaryFolder.getRoot(), SourceFile.USER_MODEL),
                                fileForClass(temporaryFolder.getRoot(), SourceFile.ACCOUNT),
                                fileForClass(temporaryFolder.getRoot(), SourceFile.PAYMENT)));
        File outputJack = FileUtils.join(temporaryFolder.getRoot(), "output.jack");
        JackProcessOptions options =
                JackProcessOptions.builder(createJackOptions())
                        .setIncrementalDir(null)
                        .setJackOutputFile(outputJack)
                        .setAdditionalParameters(
                                ImmutableMap.of(
                                        "jack.dex.debug.source", "true",
                                        "jack.dex.debug.vars", "true",
                                        "jack.dex.debug.lines", "true"))
                        .build();
        compileCurrentSourceTree(options);

        assertThatZip(outputJack)
                .containsFileWithMatch("jack.properties", "config.jack.dex.debug.source=true");
        assertThatZip(outputJack)
                .containsFileWithMatch("jack.properties", "config.jack.dex.debug.vars=true");
        assertThatZip(outputJack)
                .containsFileWithMatch("jack.properties", "config.jack.dex.debug.lines=true");
    }

    @Test
    public void testLegacyMultiDex() throws IOException, TransformException, InterruptedException {
        when(sourceFiles.getFiles())
                .thenReturn(
                        ImmutableSet.of(
                                fileForClass(temporaryFolder.getRoot(), SourceFile.USER_MODEL),
                                fileForClass(temporaryFolder.getRoot(), SourceFile.ACCOUNT),
                                fileForClass(temporaryFolder.getRoot(), SourceFile.PAYMENT)));
        File jackOutput = FileUtils.join(temporaryFolder.getRoot(), "output.jack");
        JackProcessOptions options =
                JackProcessOptions.builder(createJackOptions())
                        .setMultiDex(true)
                        .setMinSdkVersion(new DefaultApiVersion(19))
                        .setJackOutputFile(jackOutput)
                        .build();
        File dexOutput = FileUtils.join(temporaryFolder.getRoot(), "output_dex", "classes.dex");
        setDexOutput(dexOutput.getParentFile());
        compileCurrentSourceTree(options);

        // when compiling sources we ignore the multidex settings
        assertThat(FileUtils.join(temporaryFolder.getRoot(), INCREMENTAL_DIR, INCREMENTAL_LOG))
                .exists();
        assertThat(dexOutput).doesNotExist();
        assertThat(jackOutput).doesNotExist();
    }

    @Test
    public void testInvalidIncDir() throws IOException, TransformException, InterruptedException {
        when(sourceFiles.getFiles())
                .thenReturn(
                        ImmutableSet.of(
                                fileForClass(temporaryFolder.getRoot(), SourceFile.USER_MODEL),
                                fileForClass(temporaryFolder.getRoot(), SourceFile.ACCOUNT),
                                fileForClass(temporaryFolder.getRoot(), SourceFile.PAYMENT)));
        File jackOutput = FileUtils.join(temporaryFolder.getRoot(), "output.jack");
        JackProcessOptions options =
                JackProcessOptions.builder(createJackOptions())
                        .setMultiDex(true)
                        .setMinSdkVersion(new DefaultApiVersion(19))
                        .setJackOutputFile(jackOutput)
                        .build();
        assertNotNull(options.getIncrementalDir());
        Files.write("this was suppose to be a dir", options.getIncrementalDir(), Charsets.UTF_8);
        compileCurrentSourceTree(options);

        // when compiling sources we ignore the multidex settings
        assertThat(FileUtils.join(temporaryFolder.getRoot(), INCREMENTAL_DIR, INCREMENTAL_LOG))
                .doesNotExist();
        assertThat(jackOutput).exists();
    }

    @Test
    public void testMinifiedHasNoDex()
            throws IOException, TransformException, InterruptedException {
        when(sourceFiles.getFiles())
                .thenReturn(
                        ImmutableSet.of(
                                fileForClass(temporaryFolder.getRoot(), SourceFile.USER_MODEL),
                                fileForClass(temporaryFolder.getRoot(), SourceFile.ACCOUNT),
                                fileForClass(temporaryFolder.getRoot(), SourceFile.PAYMENT)));
        File jackOutput = FileUtils.join(temporaryFolder.getRoot(), "output.jack");
        JackProcessOptions options =
                JackProcessOptions.builder(createJackOptions())
                        .setMultiDex(true)
                        .setMinified(true)
                        .setJackOutputFile(jackOutput)
                        .build();
        File dexOutput = FileUtils.join(temporaryFolder.getRoot(), "output_dex", "classes.dex");
        setDexOutput(dexOutput.getParentFile());
        compileCurrentSourceTree(options);

        // in minified mode there is no DEX file, but incremental mode works
        assertThat(FileUtils.join(temporaryFolder.getRoot(), INCREMENTAL_DIR, INCREMENTAL_LOG))
                .exists();
        assertThat(dexOutput).doesNotExist();
        assertThat(jackOutput).doesNotExist();
    }

    @Test
    public void testNoSources() throws TransformException, InterruptedException, IOException {
        when(sourceFiles.getFiles()).thenReturn(ImmutableSet.of());
        compileCurrentSourceTree();

        checkIncrementalLog(
                FULL, ImmutableSet.of(), ImmutableSet.of(), ImmutableSet.of(), ImmutableSet.of());
    }

    private void compileCurrentSourceTree()
            throws IOException, TransformException, InterruptedException {
        compileCurrentSourceTree(createJackOptions());
    }

    private void compileCurrentSourceTree(@NonNull JackProcessOptions options)
            throws IOException, TransformException, InterruptedException {

        JackTestUtils.compileSources(
                sourceFiles.getFiles(),
                options,
                buildToolInfo,
                errorReporter,
                javaProcessExecutor,
                context,
                androidJackOutput,
                transformOutputProvider);
    }

    private void checkIncrementalLog(
            @NonNull String expectedType,
            @NonNull Collection<File> added,
            @NonNull Collection<File> deleted,
            @NonNull Collection<File> modified,
            @NonNull Collection<File> compiled)
            throws IOException {
        File incrementalLog =
                FileUtils.join(temporaryFolder.getRoot(), INCREMENTAL_DIR, INCREMENTAL_LOG);

        String wholeFile = Files.toString(incrementalLog, Charsets.UTF_8);
        String logContent =
                wholeFile.substring(wholeFile.lastIndexOf("***" + System.lineSeparator()));

        Truth.assertThat(logContent).containsMatch("type: " + expectedType);
        Truth.assertThat(logContent).containsMatch("added.*" + Joiner.on(',').join(added));
        Truth.assertThat(logContent).containsMatch("deleted.*" + Joiner.on(',').join(deleted));
        Truth.assertThat(logContent).containsMatch("modified.*" + Joiner.on(',').join(modified));
        Truth.assertThat(logContent).containsMatch("compiled.*" + Joiner.on(',').join(compiled));
    }

    private JackProcessOptions createJackOptions() throws IOException {
        return JackProcessOptions.builder()
                .setIncrementalDir(FileUtils.join(temporaryFolder.getRoot(), INCREMENTAL_DIR))
                .setMinSdkVersion(new DefaultApiVersion(API_LEVEL))
                .setAdditionalParameters(ImmutableMap.of("jack.incremental.log", "true"))
                .setRunInProcess(true)
                .build();
    }

    private void setDexOutput(@NonNull File dexOutputDirectory) {
        when(transformOutputProvider.getContentLocation(
                        Mockito.anyString(),
                        Mockito.eq(TransformManager.CONTENT_DEX),
                        Mockito.anySetOf(QualifiedContent.Scope.class),
                        Mockito.any(Format.class)))
                .thenReturn(dexOutputDirectory);
    }
}

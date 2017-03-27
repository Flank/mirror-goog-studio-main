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
import static com.android.testutils.truth.MoreTruth.assertThatDex;
import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.scope.AndroidTask;
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
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.io.Files;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileTree;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Tests for the {@link JackGenerateDexTransform} that is an optional transform to produce the final
 * DEX file(s).
 */
public class JackGenerateDexTransformTest {

    private static final int API_LEVEL = SdkVersionInfo.HIGHEST_KNOWN_API;
    private static final String DEX_DIR = "dex_output";

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /* Mocks needed to run the transform. */
    @Mock TransformOutputProvider transformOutputProvider;
    @Mock DefaultConfigurableFileTree sourceFiles;
    @Mock AndroidTask<? extends TransformTask> task;
    @Mock FileCollection emptyFileCollection;

    private static Context context;
    private static JavaProcessExecutor noOpJavaExecutor;
    private static ErrorReporter errorReporter;
    private static BuildToolInfo buildToolInfo;

    // jack files needed for running the tests, we generate them in the setUpJackFiles method
    private static File androidJack;
    private static File modelJack;
    private static File mainActivityJack;
    private static File mainActivityIncremental;

    @ClassRule public static TemporaryFolder jackPrebuilts = new TemporaryFolder();

    @BeforeClass
    public static void setUpJackFiles()
            throws IOException, TransformException, InterruptedException {
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
        errorReporter = new NoOpErrorReporter();
        noOpJavaExecutor = (javaProcessInfo, processOutputHandler) -> null;
        androidJack =
                TestResources.getFile(
                        JackGenerateDexTransformTest.class, "/testData/testing/android-25.jack");
        context = Mockito.mock(Context.class);

        createFirstJackFile();
        createSecondJackAndIncrementalOutput();
    }

    private static void createSecondJackAndIncrementalOutput()
            throws IOException, TransformException, InterruptedException {
        File mainActivity = FileUtils.join(jackPrebuilts.getRoot(), "MainActivity.java");
        Files.write(
                "package com.example.jack;\n"
                        + "import android.app.Activity;\n"
                        + "import android.os.Bundle;\n"
                        + "public class MainActivity extends Activity {\n"
                        + "    public void onCreate(Bundle savedInstanceState) {\n"
                        + "        super.onCreate(savedInstanceState);\n"
                        + "    }\n"
                        + "}\n",
                mainActivity,
                Charsets.UTF_8);
        mainActivityJack = FileUtils.join(jackPrebuilts.getRoot(), "main-activity.jack");
        JackProcessOptions mainActJackOptions =
                JackProcessOptions.builder(defaultOptions())
                        .setJackOutputFile(mainActivityJack)
                        .build();
        JackTestUtils.compileSources(
                ImmutableList.of(mainActivity),
                mainActJackOptions,
                buildToolInfo,
                errorReporter,
                noOpJavaExecutor,
                context,
                androidJack);

        mainActivityIncremental = FileUtils.join(jackPrebuilts.getRoot(), "main-activity-inc");
        JackProcessOptions mainActIncrementalOptions =
                JackProcessOptions.builder(defaultOptions())
                        .setIncrementalDir(mainActivityIncremental)
                        .build();
        JackTestUtils.compileSources(
                ImmutableList.of(mainActivity),
                mainActIncrementalOptions,
                buildToolInfo,
                errorReporter,
                noOpJavaExecutor,
                context,
                androidJack);
    }

    private static void createFirstJackFile()
            throws TransformException, InterruptedException, IOException {
        File userModel = fileForClass(jackPrebuilts.getRoot(), SourceFile.USER_MODEL);
        File account = fileForClass(jackPrebuilts.getRoot(), SourceFile.ACCOUNT);
        File payment = fileForClass(jackPrebuilts.getRoot(), SourceFile.PAYMENT);

        modelJack = FileUtils.join(jackPrebuilts.getRoot(), "model.jack");
        JackProcessOptions modelOptions =
                JackProcessOptions.builder(defaultOptions()).setJackOutputFile(modelJack).build();

        JackTestUtils.compileSources(
                ImmutableList.of(userModel, account, payment),
                modelOptions,
                buildToolInfo,
                errorReporter,
                noOpJavaExecutor,
                context,
                androidJack);
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(emptyFileCollection.getFiles()).thenReturn(ImmutableSet.of());
    }

    @Test
    public void testSingleJackInput() throws TransformException, InterruptedException, IOException {
        File dexOutput = FileUtils.join(temporaryFolder.getRoot(), DEX_DIR, "classes.dex");
        convertJackToDex(dexOutput, modelJack);

        assertSingleDex();
        assertThatDex(dexOutput)
                .containsClasses(
                        "Lcom/example/jack/UserModel;",
                        "Lcom/example/jack/Account;",
                        "Lcom/example/jack/Payment;");
    }

    @Test
    public void testSingleIncrementalDirInput()
            throws TransformException, InterruptedException, IOException {
        File dexOutput = FileUtils.join(temporaryFolder.getRoot(), DEX_DIR, "classes.dex");
        convertJackToDex(dexOutput, mainActivityIncremental);

        assertSingleDex();
        assertThatDex(dexOutput).containsClasses("Lcom/example/jack/MainActivity;");
    }

    @Test
    public void testTwoJackFilesInput()
            throws TransformException, InterruptedException, IOException {
        File dexOutput = FileUtils.join(temporaryFolder.getRoot(), DEX_DIR, "classes.dex");
        convertJackToDex(dexOutput, modelJack, mainActivityJack);

        assertSingleDex();
        assertThatDex(dexOutput)
                .containsClasses(
                        "Lcom/example/jack/UserModel;",
                        "Lcom/example/jack/Account;",
                        "Lcom/example/jack/Payment;",
                        "Lcom/example/jack/MainActivity;");
    }

    @Test
    public void testJackFileAndIncrementalDirInput()
            throws TransformException, InterruptedException, IOException {
        File dexOutput = FileUtils.join(temporaryFolder.getRoot(), DEX_DIR, "classes.dex");
        convertJackToDex(dexOutput, modelJack, mainActivityIncremental);

        assertSingleDex();
        assertThatDex(dexOutput)
                .containsClasses(
                        "Lcom/example/jack/UserModel;",
                        "Lcom/example/jack/Account;",
                        "Lcom/example/jack/Payment;",
                        "Lcom/example/jack/MainActivity;");
    }

    @Test
    public void testMinified() throws TransformException, InterruptedException, IOException {
        File proguardFile = temporaryFolder.newFile("rules.pro");
        Files.write(
                "-keep public class com.example.jack.MainActivity", proguardFile, Charsets.UTF_8);
        JackProcessOptions options =
                JackProcessOptions.builder(defaultOptions())
                        .setMinified(true)
                        .setProguardFiles(ImmutableList.of(proguardFile))
                        .build();

        File dexOutput = FileUtils.join(temporaryFolder.getRoot(), DEX_DIR, "classes.dex");
        convertJackToDex(options, dexOutput, modelJack, mainActivityJack);

        assertSingleDex();
        assertThatDex(dexOutput).containsClasses("Lcom/example/jack/MainActivity;");
        assertThatDex(dexOutput)
                .doesNotContainClasses(
                        "Lcom/example/jack/UserModel;",
                        "Lcom/example/jack/Account;",
                        "Lcom/example/jack/Payment;");
    }

    @Ignore("Skipped due to http://b.android.com/224026")
    @Test
    public void testLegacyMultiDex() throws TransformException, InterruptedException, IOException {
        JackProcessOptions options =
                JackProcessOptions.builder(defaultOptions())
                        .setMultiDex(true)
                        .setMinSdkVersion(new DefaultApiVersion(19))
                        .build();

        File dexOutput = FileUtils.join(temporaryFolder.getRoot(), DEX_DIR, "classes.dex");
        convertJackToDex(options, dexOutput, modelJack, mainActivityJack);

        assertSingleDex();
        assertThatDex(dexOutput)
                .containsClasses(
                        "Lcom/example/jack/UserModel;",
                        "Lcom/example/jack/Account;",
                        "Lcom/example/jack/Payment;",
                        "Lcom/example/jack/MainActivity;");
    }

    @Ignore("Skipped due to http://b.android.com/224026")
    @Test
    public void testLegacyMultiDexIncrementalDir()
            throws TransformException, InterruptedException, IOException {
        JackProcessOptions options =
                JackProcessOptions.builder(defaultOptions())
                        .setMultiDex(true)
                        .setMinSdkVersion(new DefaultApiVersion(19))
                        .build();

        File dexOutput = FileUtils.join(temporaryFolder.getRoot(), DEX_DIR, "classes.dex");
        convertJackToDex(options, dexOutput, modelJack, mainActivityIncremental);

        assertSingleDex();
        assertThatDex(dexOutput)
                .containsClasses(
                        "Lcom/example/jack/UserModel;",
                        "Lcom/example/jack/Account;",
                        "Lcom/example/jack/Payment;",
                        "Lcom/example/jack/MainActivity;");
    }

    @Ignore("Skipped due to http://b.android.com/224026")
    @Test
    public void testLegacyMultiDexMinified()
            throws IOException, TransformException, InterruptedException {
        File proguardFile = temporaryFolder.newFile("rules.pro");
        Files.write("-keep public class **.MainActivity", proguardFile, Charsets.UTF_8);
        JackProcessOptions options =
                JackProcessOptions.builder(defaultOptions())
                        .setMinified(true)
                        .setMinSdkVersion(new DefaultApiVersion(19))
                        .setProguardFiles(ImmutableList.of(proguardFile))
                        .build();

        File dexOutput = FileUtils.join(temporaryFolder.getRoot(), DEX_DIR, "classes.dex");
        convertJackToDex(options, dexOutput, modelJack, mainActivityJack);

        assertSingleDex();
        assertThatDex(dexOutput).containsClasses("Lcom/example/jack/MainActivity;");
        assertThatDex(dexOutput)
                .doesNotContainClasses(
                        "Lcom/example/jack/UserModel;",
                        "Lcom/example/jack/Account;",
                        "Lcom/example/jack/Payment;");
    }

    @Test
    public void testNativeMultiDexMinified()
            throws IOException, TransformException, InterruptedException {
        File proguardFile = temporaryFolder.newFile("rules.pro");
        Files.write("-keep public class **.MainActivity", proguardFile, Charsets.UTF_8);
        JackProcessOptions options =
                JackProcessOptions.builder(defaultOptions())
                        .setMultiDex(true)
                        .setMinified(true)
                        .setProguardFiles(ImmutableList.of(proguardFile))
                        .build();

        File dexOutput = FileUtils.join(temporaryFolder.getRoot(), DEX_DIR, "classes.dex");
        convertJackToDex(options, dexOutput, modelJack, mainActivityJack);

        assertSingleDex();
        assertThatDex(dexOutput).containsClasses("Lcom/example/jack/MainActivity;");
        assertThatDex(dexOutput)
                .doesNotContainClasses(
                        "Lcom/example/jack/UserModel;",
                        "Lcom/example/jack/Account;",
                        "Lcom/example/jack/Payment;");
    }

    private void convertJackToDex(@NonNull File dexOutput, @NonNull File... otherJackInputs)
            throws TransformException, InterruptedException, IOException {
        convertJackToDex(defaultOptions(), dexOutput, otherJackInputs);
    }

    private void convertJackToDex(
            @NonNull JackProcessOptions jackProcessOptions,
            @NonNull File dexOutput,
            @NonNull File... otherJackInputs)
            throws TransformException, InterruptedException, IOException {
        FileCollection jackFileCollection = Mockito.mock(FileCollection.class);
        when(jackFileCollection.getFiles()).thenReturn(ImmutableSet.of(otherJackInputs[0]));

        JackGenerateDexTransform transform =
                new JackGenerateDexTransform(
                        jackProcessOptions,
                        jackFileCollection,
                        () -> buildToolInfo,
                        errorReporter,
                        noOpJavaExecutor,
                        emptyFileCollection);

        ImmutableList.Builder<TransformInput> inputs = ImmutableList.builder();
        for (int i = 1; i < otherJackInputs.length; i++) {
            File jackInput = otherJackInputs[i];
            TransformInput transformInput =
                    new SimpleJarTransformInput(
                            SimpleJarInput.builder(jackInput)
                                    .setContentTypes(TransformManager.CONTENT_JACK)
                                    .create());
            inputs.add(transformInput);
        }

        TransformInput androidTransform =
                new SimpleJarTransformInput(
                        SimpleJarInput.builder(androidJack)
                                .setContentTypes(TransformManager.CONTENT_JACK)
                                .create());

        setDexOutput(dexOutput);
        transform.transform(
                new TransformInvocationBuilder(context)
                        .addInputs(inputs.build())
                        .addOutputProvider(transformOutputProvider)
                        .addReferencedInputs(ImmutableList.of(androidTransform))
                        .build());
    }

    private void assertSingleDex() {
        @SuppressWarnings("ConstantConditions")
        List<File> files =
                Arrays.asList(FileUtils.join(temporaryFolder.getRoot(), DEX_DIR).listFiles());
        Truth.assertThat(files.stream().map(File::getName).collect(Collectors.toList()))
                .containsExactly("classes.dex");
    }

    private void setDexOutput(@NonNull File dexFile) {
        when(transformOutputProvider.getContentLocation(
                        Mockito.anyString(),
                        Mockito.eq(TransformManager.CONTENT_DEX),
                        Mockito.anySetOf(QualifiedContent.Scope.class),
                        Mockito.any(Format.class)))
                .thenReturn(dexFile.getParentFile());
    }

    private static JackProcessOptions defaultOptions() {
        return JackProcessOptions.builder()
                .setMinSdkVersion(new DefaultApiVersion(API_LEVEL))
                .setDebuggable(false)
                .setRunInProcess(true)
                .build();
    }
}

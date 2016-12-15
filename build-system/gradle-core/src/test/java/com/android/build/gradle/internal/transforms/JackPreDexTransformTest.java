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

import static com.android.testutils.truth.MoreTruth.assertThat;
import static com.android.testutils.truth.MoreTruth.assertThatDex;
import static com.android.testutils.truth.MoreTruth.assertThatZip;
import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.DefaultApiVersion;
import com.android.builder.core.ErrorReporter;
import com.android.builder.core.JackProcessOptions;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.testutils.TestResources;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.gradle.api.file.FileCollection;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Testing the Jack library pre-dexing. This will make sure that we convert the jars into the jack
 * library format properly.
 */
public class JackPreDexTransformTest {

    private static final List<String> JACK_OUTPUTS =
            ImmutableList.of("output.jack", "output_1.jack");
    private static final List<String> DEX_OUTPUTS = ImmutableList.of("output_dir", "output_dir_1");
    private static final int API_LEVEL = SdkVersionInfo.HIGHEST_KNOWN_API;

    private static JavaProcessExecutor javaProcessExecutor;
    private static ErrorReporter errorReporter;
    private static BuildToolInfo buildToolInfo;


    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /* Mocks for running the transforms. */
    @Mock Context context;
    @Mock TransformOutputProvider transformOutputProvider;
    @Mock FileCollection emptyJackPluginsClassPath;

    @BeforeClass
    public static void setUpClass() {
        AndroidSdkHandler handler = AndroidSdkHandler.getInstance(TestUtils.getSdk());
        buildToolInfo =
                BuildToolInfo.fromLocalPackage(
                        Verify.verifyNotNull(
                                handler.getPackageInRange(
                                        SdkConstants.FD_BUILD_TOOLS,
                                        Range.atLeast(AndroidBuilder.MIN_BUILD_TOOLS_REV),
                                        new FakeProgressIndicator())));
        errorReporter = new NoOpErrorReporter();
        javaProcessExecutor = (javaProcessInfo, processOutputHandler) -> null;
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(transformOutputProvider.getContentLocation(
                        Mockito.anyString(),
                        Mockito.eq(TransformManager.CONTENT_JACK),
                        Mockito.anySetOf(QualifiedContent.Scope.class),
                        Mockito.any(Format.class)))
                .thenReturn(
                        FileUtils.join(temporaryFolder.getRoot(), JACK_OUTPUTS.get(0)),
                        FileUtils.join(temporaryFolder.getRoot(), JACK_OUTPUTS.get(1)));
        when(transformOutputProvider.getContentLocation(
                        Mockito.anyString(),
                        Mockito.eq(TransformManager.CONTENT_DEX),
                        Mockito.anySetOf(QualifiedContent.Scope.class),
                        Mockito.any(Format.class)))
                .thenReturn(
                        FileUtils.join(temporaryFolder.getRoot(), DEX_OUTPUTS.get(0)),
                        FileUtils.join(temporaryFolder.getRoot(), DEX_OUTPUTS.get(1)));

        when(emptyJackPluginsClassPath.getFiles()).thenReturn(ImmutableSet.of());
    }

    /**
     * Runtime libs should use Jill to convert jars to jack libraries. Also, jars on the
     * bootClasspath will be converted.
     */
    @Test
    public void testClasspathLibs() throws IOException, TransformException, InterruptedException {
        File androidJar = temporaryFolder.newFile("android.jar");
        FileUtils.copyFile(
                TestResources.getFile(this.getClass(), "/testData/testing/classes.jar"),
                androidJar);

        JackPreDexTransform transform =
                new JackPreDexTransform(
                        defaultJackOptions(),
                        () -> ImmutableList.of(androidJar),
                        () -> buildToolInfo,
                        errorReporter,
                        javaProcessExecutor,
                        JackPreDexTransform.InputType.CLASSPATH_LIBRARY,
                        emptyJackPluginsClassPath);

        File classesJar = temporaryFolder.newFile("classes.jar");
        FileUtils.copyFile(TestResources.getFile("/testData/testing/classes.jar"), classesJar);
        TransformInput otherJarInput =
                new SimpleJarTransformInput(SimpleJarInput.builder(classesJar).create());

        transform.transform(
                new TransformInvocationBuilder(context)
                        .addInputs(ImmutableList.of(otherJarInput))
                        .addOutputProvider(transformOutputProvider)
                        .build());

        for (String jackOutputFile : JACK_OUTPUTS) {
            File outputJack = FileUtils.join(temporaryFolder.getRoot(), jackOutputFile);
            assertThat(outputJack).exists();

            assertThatZip(outputJack).containsFileWithMatch("jack.properties", "lib.emitter=jill");
            assertThatZip(outputJack).contains("jayce/NonFinalClass.jayce");
            assertThatZip(outputJack).contains("jayce/FinalClass.jayce");
            assertThatZip(outputJack).doesNotContain("prebuilt/NonFinalClass.dex");
            assertThatZip(outputJack).doesNotContain("prebuilt/FinalClass.dex");
        }
    }

    /** When converting packaged libraries, we should not convert the ones on boot classpath. */
    @Test
    public void testPackagedLibs() throws IOException, TransformException, InterruptedException {
        File androidJar = temporaryFolder.newFile("android.jar");
        FileUtils.copyFile(
                TestResources.getFile(this.getClass(), "/testData/testing/classes.jar"),
                androidJar);

        JackPreDexTransform transform =
                new JackPreDexTransform(
                        defaultJackOptions(),
                        ImmutableList::of,
                        () -> buildToolInfo,
                        errorReporter,
                        javaProcessExecutor,
                        JackPreDexTransform.InputType.PACKAGED_LIBRARY,
                        emptyJackPluginsClassPath);

        File classesJar = temporaryFolder.newFile("classes.jar");
        FileUtils.copyFile(
                TestResources.getFile(this.getClass(), "/testData/testing/classes.jar"),
                classesJar);
        TransformInput otherJarInput =
                new SimpleJarTransformInput(SimpleJarInput.builder(classesJar).create());

        transform.transform(
                new TransformInvocationBuilder(context)
                        .addInputs(ImmutableList.of(otherJarInput))
                        .addOutputProvider(transformOutputProvider)
                        .build());

        assertThat(FileUtils.join(temporaryFolder.getRoot(), JACK_OUTPUTS.get(1))).doesNotExist();

        File outputJack = FileUtils.join(temporaryFolder.getRoot(), JACK_OUTPUTS.get(0));
        for (String property : generateJackProperties(API_LEVEL, false)) {
            assertThatZip(outputJack).containsFileWithMatch("jack.properties", property);
        }

        assertThatZip(outputJack).contains("jayce/NonFinalClass.jayce");
        assertThatZip(outputJack).contains("jayce/FinalClass.jayce");
        assertThatZip(outputJack).contains("prebuilt/NonFinalClass.dex");
        assertThatZip(outputJack).contains("prebuilt/FinalClass.dex");
    }

    /** If debuggable flag is true, it should generate sources, vars and lines debug info. */
    @Test
    public void testPackagedDebuggable()
            throws IOException, TransformException, InterruptedException {
        JackProcessOptions jackProcessOptions =
                JackProcessOptions.builder(defaultJackOptions()).setDebuggable(true).build();
        JackPreDexTransform transform =
                new JackPreDexTransform(
                        jackProcessOptions,
                        ImmutableList::of,
                        () -> buildToolInfo,
                        errorReporter,
                        javaProcessExecutor,
                        JackPreDexTransform.InputType.PACKAGED_LIBRARY,
                        emptyJackPluginsClassPath);

        File classesJar = temporaryFolder.newFile("classes.jar");
        FileUtils.copyFile(
                TestResources.getFile(this.getClass(), "/testData/testing/classes.jar"),
                classesJar);
        TransformInput otherJarInput =
                new SimpleJarTransformInput(SimpleJarInput.builder(classesJar).create());

        transform.transform(
                new TransformInvocationBuilder(context)
                        .addInputs(ImmutableList.of(otherJarInput))
                        .addOutputProvider(transformOutputProvider)
                        .build());

        File outputJack = FileUtils.join(temporaryFolder.getRoot(), JACK_OUTPUTS.get(0));

        assertThatZip(outputJack).containsFileWithMatch("jack.properties", "lib.emitter=jack");
        for (String property : generateJackProperties(API_LEVEL, true)) {
            assertThatZip(outputJack).containsFileWithMatch("jack.properties", property);
        }

        assertThatZip(outputJack).contains("jayce/NonFinalClass.jayce");
        assertThatZip(outputJack).contains("jayce/FinalClass.jayce");
        assertThatZip(outputJack).contains("prebuilt/NonFinalClass.dex");
        assertThatZip(outputJack).contains("prebuilt/FinalClass.dex");
    }

    /** Empty input should produce no output. */
    @Test
    public void testEmptyInputCreateNoOutput()
            throws TransformException, InterruptedException, IOException {
        JackPreDexTransform transform =
                new JackPreDexTransform(
                        defaultJackOptions(),
                        ImmutableList::of,
                        () -> buildToolInfo,
                        errorReporter,
                        javaProcessExecutor,
                        JackPreDexTransform.InputType.PACKAGED_LIBRARY,
                        emptyJackPluginsClassPath);

        TransformInput input = new SimpleJarTransformInput(ImmutableList.of());

        transform.transform(
                new TransformInvocationBuilder(context)
                        .addInputs(ImmutableList.of(input))
                        .addOutputProvider(transformOutputProvider)
                        .build());

        assertThat(FileUtils.join(temporaryFolder.getRoot(), JACK_OUTPUTS.get(0))).doesNotExist();
        assertThat(FileUtils.join(temporaryFolder.getRoot(), JACK_OUTPUTS.get(1))).doesNotExist();
    }

    /** Additional parameters should be passed to the compiler. */
    @Test
    public void testDebuggableAsAdditionalParametersApplied()
            throws IOException, TransformException, InterruptedException {
        JackProcessOptions options =
                JackProcessOptions.builder(defaultJackOptions())
                        .setAdditionalParameters(
                                ImmutableMap.of(
                                        "jack.dex.debug.source", "true",
                                        "jack.dex.debug.vars", "true",
                                        "jack.dex.debug.lines", "true"))
                        .build();
        JackPreDexTransform transform =
                new JackPreDexTransform(
                        options,
                        ImmutableList::of,
                        () -> buildToolInfo,
                        errorReporter,
                        javaProcessExecutor,
                        JackPreDexTransform.InputType.PACKAGED_LIBRARY,
                        emptyJackPluginsClassPath);

        File classesJar = temporaryFolder.newFile("classes.jar");
        FileUtils.copyFile(
                TestResources.getFile(this.getClass(), "/testData/testing/classes.jar"),
                classesJar);
        TransformInput classedJarInput =
                new SimpleJarTransformInput(SimpleJarInput.builder(classesJar).create());

        transform.transform(
                new TransformInvocationBuilder(context)
                        .addInputs(ImmutableList.of(classedJarInput))
                        .addOutputProvider(transformOutputProvider)
                        .build());

        File outputJack = FileUtils.join(temporaryFolder.getRoot(), JACK_OUTPUTS.get(0));

        assertThatZip(outputJack).containsFileWithMatch("jack.properties", "lib.emitter=jack");
        for (String property : generateJackProperties(API_LEVEL, true /* debuggable */)) {
            assertThatZip(outputJack).containsFileWithMatch("jack.properties", property);
        }

        assertThatZip(outputJack).contains("jayce/NonFinalClass.jayce");
        assertThatZip(outputJack).contains("jayce/FinalClass.jayce");
        assertThatZip(outputJack).contains("prebuilt/NonFinalClass.dex");
        assertThatZip(outputJack).contains("prebuilt/FinalClass.dex");
    }

    /** If native multidex, non minified, DEX files will be generated. */
    @Test
    public void testDexOutputGeneratedForMultidex()
            throws IOException, TransformException, InterruptedException {
        JackProcessOptions options =
                JackProcessOptions.builder(defaultJackOptions()).setGenerateDex(true).build();
        JackPreDexTransform transform =
                new JackPreDexTransform(
                        options,
                        ImmutableList::of,
                        () -> buildToolInfo,
                        errorReporter,
                        javaProcessExecutor,
                        JackPreDexTransform.InputType.PACKAGED_LIBRARY,
                        emptyJackPluginsClassPath);

        File classesJar = temporaryFolder.newFile("classes.jar");
        FileUtils.copyFile(
                TestResources.getFile(this.getClass(), "/testData/testing/classes.jar"),
                classesJar);
        TransformInput classesJarInput =
                new SimpleJarTransformInput(SimpleJarInput.builder(classesJar).create());

        transform.transform(
                new TransformInvocationBuilder(context)
                        .addInputs(ImmutableList.of(classesJarInput))
                        .addOutputProvider(transformOutputProvider)
                        .build());

        File outputJack = FileUtils.join(temporaryFolder.getRoot(), JACK_OUTPUTS.get(0));
        assertThat(outputJack).exists();
        // output dex file will be in a dir, with the same name like the parent dir and DEX suffix
        File outputDex =
                FileUtils.join(
                        temporaryFolder.getRoot(),
                        DEX_OUTPUTS.get(0),
                        DEX_OUTPUTS.get(0) + SdkConstants.DOT_DEX);
        assertThat(outputDex).exists();
        assertThatDex(outputDex).containsClass("LNonFinalClass;");
        assertThatDex(outputDex).containsClass("LFinalClass;");
    }

    /** Classpath libraries conversion should generate JACK only files, and no DEX files. */
    @Test
    public void testClasspathLibrariesHaveNoDexOutput()
            throws IOException, TransformException, InterruptedException {
        JackProcessOptions options =
                JackProcessOptions.builder(defaultJackOptions()).setGenerateDex(true).build();
        JackPreDexTransform transform =
                new JackPreDexTransform(
                        options,
                        ImmutableList::of,
                        () -> buildToolInfo,
                        errorReporter,
                        javaProcessExecutor,
                        JackPreDexTransform.InputType.CLASSPATH_LIBRARY,
                        emptyJackPluginsClassPath);

        File classesJar = temporaryFolder.newFile("classes.jar");
        FileUtils.copyFile(
                TestResources.getFile(this.getClass(), "/testData/testing/classes.jar"),
                classesJar);
        TransformInput classesJarInput =
                new SimpleJarTransformInput(SimpleJarInput.builder(classesJar).create());

        transform.transform(
                new TransformInvocationBuilder(context)
                        .addInputs(ImmutableList.of(classesJarInput))
                        .addOutputProvider(transformOutputProvider)
                        .build());

        File outputJack = FileUtils.join(temporaryFolder.getRoot(), JACK_OUTPUTS.get(0));
        assertThat(outputJack).exists();

        File outputDexDir = FileUtils.join(temporaryFolder.getRoot(), DEX_OUTPUTS.get(0));
        assertThat(outputDexDir).doesNotExist();
    }

    private static JackProcessOptions defaultJackOptions() {
        return JackProcessOptions.builder()
                .setMinSdkVersion(new DefaultApiVersion(API_LEVEL))
                .setDebuggable(false)
                .setRunInProcess(true)
                .build();
    }

    private static List<String> generateJackProperties(int apiLevel, boolean debuggable) {
        return ImmutableList.of(
                "lib.emitter=jack",
                "config.jack.dex.optimize=true",
                "config.jack.android.min-api-level=" + apiLevel,
                "config.jack.dex.debug.source=" + debuggable,
                "config.jack.dex.debug.lines=" + debuggable,
                "config.jack.dex.debug.vars=" + debuggable);
    }
}

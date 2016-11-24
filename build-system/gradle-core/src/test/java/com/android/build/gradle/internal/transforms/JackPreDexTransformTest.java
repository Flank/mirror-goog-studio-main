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
import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.dsl.CoreJackOptions;
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder;
import com.android.build.gradle.tasks.JackPreDexTransform;
import com.android.builder.core.DefaultApiVersion;
import com.android.builder.core.ErrorReporter;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.repository.Revision;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.testutils.TestResources;
import com.android.testutils.TestUtils;
import com.android.testutils.truth.MoreTruth;
import com.android.utils.FileUtils;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
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
    private static final int API_LEVEL = 24;

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Mock JavaProcessExecutor javaProcessExecutor;
    @Mock ErrorReporter errorReporter;
    @Mock Context context;
    @Mock TransformOutputProvider transformOutputProvider;
    @Mock CoreJackOptions coreJackOptions;

    private BuildToolInfo buildToolInfo;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(coreJackOptions.isJackInProcess()).thenReturn(true);

        AndroidSdkHandler handler = AndroidSdkHandler.getInstance(TestUtils.getSdk());
        buildToolInfo =
                BuildToolInfo.fromLocalPackage(
                        Verify.verifyNotNull(
                                handler.getPackageInRange(
                                        SdkConstants.FD_BUILD_TOOLS,
                                        Range.atLeast(new Revision(25, 0, 0)),
                                        new FakeProgressIndicator())));

        when(transformOutputProvider.getContentLocation(
                        Mockito.anyString(),
                        Mockito.anySetOf(QualifiedContent.ContentType.class),
                        Mockito.anySetOf(QualifiedContent.Scope.class),
                        Mockito.any(Format.class)))
                .thenReturn(
                        FileUtils.join(temporaryFolder.getRoot(), JACK_OUTPUTS.get(0)),
                        FileUtils.join(temporaryFolder.getRoot(), JACK_OUTPUTS.get(1)));
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
                customizableBuilder()
                        .bootClasspath(() -> ImmutableList.of(androidJar))
                        .forClasspathLibs()
                        .create();

        File otherJar = temporaryFolder.newFile("otherJar.jar");
        FileUtils.copyFile(TestResources.getFile("/testData/testing/classes.jar"), otherJar);
        TransformInput otherJarInput =
                new SimpleJarTransformInput(SimpleJarInput.builder(otherJar).create());

        transform.transform(
                new TransformInvocationBuilder(context)
                        .addInputs(ImmutableList.of(otherJarInput))
                        .addOutputProvider(transformOutputProvider)
                        .build());

        for (String jackOutputFile : JACK_OUTPUTS) {
            File outputJack = FileUtils.join(temporaryFolder.getRoot(), jackOutputFile);
            assertThat(outputJack).exists();

            MoreTruth.assertThatZip(outputJack)
                    .containsFileWithMatch("jack.properties", "lib.emitter=jill");
            MoreTruth.assertThatZip(outputJack).contains("jayce/NonFinalClass.jayce");
            MoreTruth.assertThatZip(outputJack).contains("jayce/FinalClass.jayce");
            MoreTruth.assertThatZip(outputJack).doesNotContain("prebuilt/NonFinalClass.dex");
            MoreTruth.assertThatZip(outputJack).doesNotContain("prebuilt/FinalClass.dex");
        }
    }

    /** When converting packaged libraries, we should not convert the ones on the bootclasspath. */
    @Test
    public void testPackagedLibs() throws IOException, TransformException, InterruptedException {
        File androidJar = temporaryFolder.newFile("android.jar");
        FileUtils.copyFile(
                TestResources.getFile(this.getClass(), "/testData/testing/classes.jar"),
                androidJar);

        JackPreDexTransform transform = customizableBuilder().forPackagedLibs().create();

        File otherJar = temporaryFolder.newFile("otherJar.jar");
        FileUtils.copyFile(
                TestResources.getFile(this.getClass(), "/testData/testing/classes.jar"), otherJar);
        TransformInput otherJarInput =
                new SimpleJarTransformInput(SimpleJarInput.builder(otherJar).create());

        transform.transform(
                new TransformInvocationBuilder(context)
                        .addInputs(ImmutableList.of(otherJarInput))
                        .addOutputProvider(transformOutputProvider)
                        .build());

        assertThat(FileUtils.join(temporaryFolder.getRoot(), JACK_OUTPUTS.get(1))).doesNotExist();

        File outputJack = FileUtils.join(temporaryFolder.getRoot(), JACK_OUTPUTS.get(0));
        for (String property : generateJackProperties(API_LEVEL, false)) {
            MoreTruth.assertThatZip(outputJack).containsFileWithMatch("jack.properties", property);
        }

        MoreTruth.assertThatZip(outputJack).contains("jayce/NonFinalClass.jayce");
        MoreTruth.assertThatZip(outputJack).contains("jayce/FinalClass.jayce");
        MoreTruth.assertThatZip(outputJack).contains("prebuilt/NonFinalClass.dex");
        MoreTruth.assertThatZip(outputJack).contains("prebuilt/FinalClass.dex");
    }

    /** If debuggable flag is true, it should generate sources, vars and lines debug info. */
    @Test
    public void testPackagedDebuggable()
            throws IOException, TransformException, InterruptedException {
        JackPreDexTransform transform =
                customizableBuilder().forPackagedLibs().debuggable(true).create();

        File otherJar = temporaryFolder.newFile("otherJar.jar");
        FileUtils.copyFile(
                TestResources.getFile(this.getClass(), "/testData/testing/classes.jar"), otherJar);
        TransformInput otherJarInput =
                new SimpleJarTransformInput(SimpleJarInput.builder(otherJar).create());

        transform.transform(
                new TransformInvocationBuilder(context)
                        .addInputs(ImmutableList.of(otherJarInput))
                        .addOutputProvider(transformOutputProvider)
                        .build());

        File outputJack = FileUtils.join(temporaryFolder.getRoot(), JACK_OUTPUTS.get(0));

        MoreTruth.assertThatZip(outputJack)
                .containsFileWithMatch("jack.properties", "lib.emitter=jack");
        for (String property : generateJackProperties(API_LEVEL, true)) {
            MoreTruth.assertThatZip(outputJack).containsFileWithMatch("jack.properties", property);
        }

        MoreTruth.assertThatZip(outputJack).contains("jayce/NonFinalClass.jayce");
        MoreTruth.assertThatZip(outputJack).contains("jayce/FinalClass.jayce");
        MoreTruth.assertThatZip(outputJack).contains("prebuilt/NonFinalClass.dex");
        MoreTruth.assertThatZip(outputJack).contains("prebuilt/FinalClass.dex");
    }

    /** Empty input should produce no output. */
    @Test
    public void testEmptyInputCreateNoOutput()
            throws TransformException, InterruptedException, IOException {
        JackPreDexTransform transform = customizableBuilder().forPackagedLibs().create();

        TransformInput input = new SimpleJarTransformInput(ImmutableList.of());

        transform.transform(
                new TransformInvocationBuilder(context)
                        .addInputs(ImmutableList.of(input))
                        .addOutputProvider(transformOutputProvider)
                        .build());

        assertThat(FileUtils.join(temporaryFolder.getRoot(), JACK_OUTPUTS.get(0))).doesNotExist();
        assertThat(FileUtils.join(temporaryFolder.getRoot(), JACK_OUTPUTS.get(1))).doesNotExist();
    }

    @Test
    public void testDebuggableAsAdditionalParametersApplied()
            throws IOException, TransformException, InterruptedException {
        when(coreJackOptions.getAdditionalParameters())
                .thenReturn(
                        ImmutableMap.of(
                                "jack.dex.debug.source", "true",
                                "jack.dex.debug.vars", "true",
                                "jack.dex.debug.lines", "true"));
        JackPreDexTransform transform = customizableBuilder().forPackagedLibs().create();

        File otherJar = temporaryFolder.newFile("otherJar.jar");
        FileUtils.copyFile(
                TestResources.getFile(this.getClass(), "/testData/testing/classes.jar"), otherJar);
        TransformInput otherJarInput =
                new SimpleJarTransformInput(SimpleJarInput.builder(otherJar).create());

        transform.transform(
                new TransformInvocationBuilder(context)
                        .addInputs(ImmutableList.of(otherJarInput))
                        .addOutputProvider(transformOutputProvider)
                        .build());

        File outputJack = FileUtils.join(temporaryFolder.getRoot(), JACK_OUTPUTS.get(0));

        MoreTruth.assertThatZip(outputJack)
                .containsFileWithMatch("jack.properties", "lib.emitter=jack");
        for (String property : generateJackProperties(API_LEVEL, true /* debuggable */)) {
            MoreTruth.assertThatZip(outputJack).containsFileWithMatch("jack.properties", property);
        }

        MoreTruth.assertThatZip(outputJack).contains("jayce/NonFinalClass.jayce");
        MoreTruth.assertThatZip(outputJack).contains("jayce/FinalClass.jayce");
        MoreTruth.assertThatZip(outputJack).contains("prebuilt/NonFinalClass.dex");
        MoreTruth.assertThatZip(outputJack).contains("prebuilt/FinalClass.dex");
    }

    private JackPreDexTransform.Builder customizableBuilder() {
        return JackPreDexTransform.builder()
                .errorReporter(errorReporter)
                .javaProcessExecutor(javaProcessExecutor)
                .buildToolInfo(() -> buildToolInfo)
                .coreJackOptions(coreJackOptions)
                .minApiVersion(new DefaultApiVersion(API_LEVEL));
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

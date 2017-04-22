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

package com.android.build.gradle.tasks;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.apkzlib.zfile.ApkCreatorFactory;
import com.android.build.VariantOutput;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.BuildOutputs;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.build.gradle.internal.scope.SplitScope;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.packaging.PackagerException;
import com.android.ide.common.build.ApkData;
import com.android.ide.common.signing.KeytoolException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.internal.impldep.com.google.common.base.Charsets;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Tests for {@link InstantRunResourcesApkBuilder} class */
public class InstantRunResourcesApkBuilderTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    InstantRunResourcesApkBuilder task;
    Project project;
    File outputFolder;

    @Mock FileCollection fileCollection;
    @Mock PackagingScope packagingScope;
    @Mock AndroidBuilder androidBuilder;
    @Mock SplitScope splitScope;
    @Mock CoreSigningConfig signingConfig;
    @Mock InstantRunBuildContext buildContext;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        File testDir = temporaryFolder.newFolder();
        project = ProjectBuilder.builder().withProjectDir(testDir).build();

        task = project.getTasks().create("test", InstantRunResourcesApkBuilder.class);

        when(packagingScope.getSplitScope()).thenReturn(splitScope);
        when(packagingScope.getFullVariantName()).thenReturn("testVariant");
        when(packagingScope.getSigningConfig()).thenReturn(signingConfig);
        when(packagingScope.getAndroidBuilder()).thenReturn(androidBuilder);
        when(packagingScope.getInstantRunBuildContext()).thenReturn(buildContext);

        File incrementalDir = temporaryFolder.newFolder("test-incremental");

        when(packagingScope.getIncrementalDir(eq("instant-run-resources")))
                .thenReturn(incrementalDir);
        outputFolder = temporaryFolder.newFolder("test-output-folder");
        when(packagingScope.getInstantRunResourceApkFolder()).thenReturn(outputFolder);
    }

    @After
    public void tearDown() throws IOException {
        task = null;
        project = null;
    }

    @Test
    public void testConfigAction() throws IOException {

        InstantRunResourcesApkBuilder.ConfigAction configAction =
                new InstantRunResourcesApkBuilder.ConfigAction(
                        TaskOutputHolder.TaskOutputType.PROCESSED_RES,
                        fileCollection,
                        packagingScope);

        configAction.execute(task);

        assertThat(task.getResInputType())
                .isEqualTo(TaskOutputHolder.TaskOutputType.PROCESSED_RES.name());
        assertThat(task.getResourcesFile()).isEqualTo(fileCollection);
        assertThat(task.getOutputDirectory()).isEqualTo(outputFolder);
        assertThat(task.getSigningConf()).isEqualTo(signingConfig);
        assertThat(task.getVariantName()).isEqualTo("testVariant");
    }

    @Test
    public void testNoSplitExecution() throws IOException, PackagerException, KeytoolException {

        InstantRunResourcesApkBuilder.ConfigAction configAction =
                new InstantRunResourcesApkBuilder.ConfigAction(
                        TaskOutputHolder.TaskOutputType.PROCESSED_RES,
                        fileCollection,
                        packagingScope);

        configAction.execute(task);

        FileTree fileTree = Mockito.mock(FileTree.class);
        when(fileTree.getFiles()).thenReturn(ImmutableSet.of());
        when(fileCollection.getAsFileTree()).thenReturn(fileTree);

        ArgumentCaptor<SplitScope.SplitOutputAction> splitActionCaptor =
                ArgumentCaptor.forClass(SplitScope.SplitOutputAction.class);

        // invoke per split action with null values.
        doAnswer(
                        invocation -> {
                            splitActionCaptor.getValue().processSplit(null, null);
                            return null;
                        })
                .when(splitScope)
                .parallelForEachOutput(
                        eq(ImmutableList.of()),
                        eq(TaskOutputHolder.TaskOutputType.PROCESSED_RES),
                        eq(TaskOutputHolder.TaskOutputType.INSTANT_RUN_PACKAGED_RESOURCES),
                        splitActionCaptor.capture());

        task.doFullTaskAction();

        verifyNoMoreInteractions(androidBuilder, buildContext);
    }

    @Test
    public void testAnotherSingleSplitExecution()
            throws KeytoolException, IOException, PackagerException {
        testExecution(1);
    }

    @Test
    public void testMultipleSplitExecution()
            throws KeytoolException, IOException, PackagerException {
        testExecution(3);
    }

    @SuppressWarnings("unchecked")
    private void testExecution(int splitNumber)
            throws IOException, PackagerException, KeytoolException {

        InstantRunResourcesApkBuilder.ConfigAction configAction =
                new InstantRunResourcesApkBuilder.ConfigAction(
                        TaskOutputHolder.TaskOutputType.PROCESSED_RES,
                        fileCollection,
                        packagingScope);

        configAction.execute(task);

        FileTree fileTree = Mockito.mock(FileTree.class);
        List<ApkData> apkDatas = new ArrayList<>();
        List<File> resourcesFiles = new ArrayList<>();
        ImmutableSetMultimap.Builder<VariantScope.OutputType, BuildOutput> resources =
                ImmutableSetMultimap.builder();

        for (int i = 0; i < splitNumber; i++) {
            // invoke per split action with one split.
            ApkData apkData = Mockito.mock(ApkData.class);
            apkDatas.add(apkData);
            when(apkData.getBaseName()).thenReturn("feature-" + i);
            when(apkData.getType()).thenReturn(VariantOutput.OutputType.SPLIT);

            File resourcesFile = temporaryFolder.newFile("fake-resources-" + i + ".apk");
            when(fileCollection.getAsFileTree()).thenReturn(fileTree);

            resourcesFiles.add(resourcesFile);
            resources.put(
                    TaskOutputHolder.TaskOutputType.PROCESSED_RES,
                    new BuildOutput(
                            TaskOutputHolder.TaskOutputType.PROCESSED_RES, apkData, resourcesFile));
        }

        File[] inputFiles = temporaryFolder.getRoot().listFiles();
        assertThat(inputFiles).isNotNull();
        when(fileTree.getFiles()).thenReturn(ImmutableSet.copyOf(inputFiles));

        Files.write(
                BuildOutputs.persist(
                        ImmutableList.of(TaskOutputHolder.TaskOutputType.PROCESSED_RES),
                        resources.build()),
                BuildOutputs.getMetadataFile(temporaryFolder.getRoot()),
                Charsets.UTF_8);

        ArgumentCaptor<SplitScope.SplitOutputAction> splitActionCaptor =
                ArgumentCaptor.forClass(SplitScope.SplitOutputAction.class);

        doAnswer(
                        invocation -> {
                            for (int i = 0; i < splitNumber; i++) {
                                splitActionCaptor
                                        .getValue()
                                        .processSplit(apkDatas.get(i), resourcesFiles.get(i));
                            }
                            return null;
                        })
                .when(splitScope)
                .parallelForEachOutput(
                        any(Collection.class),
                        eq(TaskOutputHolder.TaskOutputType.PROCESSED_RES),
                        eq(TaskOutputHolder.TaskOutputType.INSTANT_RUN_PACKAGED_RESOURCES),
                        splitActionCaptor.capture());

        task.doFullTaskAction();

        for (int i = 0; i < splitNumber; i++) {
            File expectedOutputFile =
                    new File(
                            outputFolder,
                            InstantRunResourcesApkBuilder.mangleApkName(apkDatas.get(i))
                                    + SdkConstants.DOT_ANDROID_PACKAGE);
            verify(androidBuilder)
                    .packageCodeSplitApk(
                            eq(resourcesFiles.get(i)),
                            eq(ImmutableSet.of()),
                            eq(signingConfig),
                            eq(expectedOutputFile),
                            any(File.class),
                            any(ApkCreatorFactory.class));

            verify(buildContext).addChangedFile(FileType.RESOURCES, expectedOutputFile);
        }
    }
}

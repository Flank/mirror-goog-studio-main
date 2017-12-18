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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.apkzlib.zfile.ApkCreatorFactory;
import com.android.build.VariantOutput;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.scope.BuildElementActionScheduler;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.packaging.PackagerException;
import com.android.ide.common.build.ApkData;
import com.android.ide.common.build.ApkInfo;
import com.android.ide.common.signing.KeytoolException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import kotlin.jvm.functions.Function2;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.testfixtures.ProjectBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Tests for {@link InstantRunResourcesApkBuilder} class */
public class InstantRunResourcesApkBuilderTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    InstantRunResourcesApkBuilder task;
    Project project;
    File outputFolder;
    File testDir;

    @Mock FileCollection fileCollection;
    @Mock FileTree fileTree;
    @Mock VariantScope variantScope;
    @Mock GradleVariantConfiguration variantConfiguration;
    @Mock GlobalScope globalScope;
    @Mock AndroidBuilder androidBuilder;
    @Mock CoreSigningConfig signingConfig;
    @Mock InstantRunBuildContext buildContext;

    public static class InstantRunResourcesApkBuilderForTest extends InstantRunResourcesApkBuilder {
        @Override
        protected BuildElements getResInputBuildArtifacts() {
            return new BuildElements(super.getResInputBuildArtifacts().getElements()) {
                @NotNull
                @Override
                public BuildElementActionScheduler transform(
                        @NotNull Function2<? super ApkInfo, ? super File, ? extends File> action) {
                    return new BuildElementActionScheduler.Synchronous(this, action);
                }
            };
        }
    }

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        testDir = temporaryFolder.newFolder();
        project = ProjectBuilder.builder().withProjectDir(testDir).build();

        task = project.getTasks().create("test", InstantRunResourcesApkBuilderForTest.class);

        when(variantScope.getFullVariantName()).thenReturn("testVariant");
        when(variantScope.getGlobalScope()).thenReturn(globalScope);
        when(variantScope.getVariantConfiguration()).thenReturn(variantConfiguration);
        when(variantConfiguration.getSigningConfig()).thenReturn(signingConfig);
        when(globalScope.getAndroidBuilder()).thenReturn(androidBuilder);
        when(variantScope.getInstantRunBuildContext()).thenReturn(buildContext);

        File incrementalDir = temporaryFolder.newFolder("test-incremental");

        when(variantScope.getIncrementalDir(eq(task.getName()))).thenReturn(incrementalDir);
        outputFolder = temporaryFolder.newFolder("test-output-folder");
        when(variantScope.getInstantRunResourceApkFolder()).thenReturn(outputFolder);
        when(fileCollection.getAsFileTree()).thenReturn(fileTree);
    }

    @After
    public void tearDown() {
        task = null;
        project = null;
    }

    @Test
    public void testConfigAction() {

        InstantRunResourcesApkBuilder.ConfigAction configAction =
                new InstantRunResourcesApkBuilder.ConfigAction(
                        TaskOutputHolder.TaskOutputType.PROCESSED_RES,
                        fileCollection,
                        variantScope);

        configAction.execute(task);

        assertThat(task.getResInputType())
                .isEqualTo(TaskOutputHolder.TaskOutputType.PROCESSED_RES.name());
        assertThat(task.getResourcesFile()).isEqualTo(fileCollection);
        assertThat(task.getOutputDirectory()).isEqualTo(outputFolder);
        assertThat(task.getSigningConf()).isEqualTo(signingConfig);
        assertThat(task.getVariantName()).isEqualTo("testVariant");
    }

    @Test
    public void testNoSplitExecution() {

        InstantRunResourcesApkBuilder.ConfigAction configAction =
                new InstantRunResourcesApkBuilder.ConfigAction(
                        TaskOutputHolder.TaskOutputType.PROCESSED_RES,
                        fileCollection,
                        variantScope);

        configAction.execute(task);

        FileTree fileTree = Mockito.mock(FileTree.class);
        when(fileTree.getFiles()).thenReturn(ImmutableSet.of());
        when(fileCollection.getAsFileTree()).thenReturn(fileTree);

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
                        variantScope);

        configAction.execute(task);

        List<ApkData> apkDatas = new ArrayList<>();
        List<File> resourcesFiles = new ArrayList<>();
        ImmutableList.Builder<BuildOutput> resources = ImmutableList.builder();

        for (int i = 0; i < splitNumber; i++) {
            // invoke per split action with one split.
            ApkData apkData = Mockito.mock(ApkData.class);
            apkDatas.add(apkData);
            when(apkData.getBaseName()).thenReturn("feature-" + i);
            when(apkData.getType()).thenReturn(VariantOutput.OutputType.SPLIT);

            File resourcesFile = temporaryFolder.newFile("fake-resources-" + i + ".apk");

            resourcesFiles.add(resourcesFile);
            resources.add(
                    new BuildOutput(
                            TaskOutputHolder.TaskOutputType.PROCESSED_RES, apkData, resourcesFile));
        }
        new BuildElements(resources.build()).save(temporaryFolder.getRoot());

        File[] inputFiles = temporaryFolder.getRoot().listFiles();
        assertThat(inputFiles).isNotNull();
        when(fileTree.getFiles()).thenReturn(ImmutableSet.copyOf(inputFiles));

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

            verify(buildContext).addChangedFile(FileType.SPLIT, expectedOutputFile);
        }
    }
}

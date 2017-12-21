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

package com.android.build.gradle.internal.ide;

import static com.android.build.gradle.internal.scope.TaskOutputHolder.AnchorOutputType.ALL_CLASSES;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.VariantManager;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.model.NativeLibraryFactory;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.publishing.VariantPublishingSpec;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.OutputFactory;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.TestVariantBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.gradle.api.file.FileCollection;
import org.gradle.internal.impldep.com.google.common.base.Charsets;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Tests for the {@link com.android.build.gradle.internal.ide.ModelBuilder} */
public class ModelBuilderTest {

    private static final String PROJECT = "project";

    @Mock GlobalScope globalScope;
    @Mock AndroidBuilder androidBuilder;
    @Mock VariantManager variantManager;
    @Mock TaskManager taskManager;
    @Mock AndroidConfig androidConfig;
    @Mock ExtraModelInfo extraModelInfo;
    @Mock NdkHandler ndkHandler;
    @Mock NativeLibraryFactory nativeLibraryFactory;

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    ModelBuilder modelBuilder;
    File apkLocation;

    @Before
    public void setUp() throws IOException {

        MockitoAnnotations.initMocks(this);
        apkLocation = temporaryFolder.newFolder("apk");

        modelBuilder =
                new ModelBuilder(
                        globalScope,
                        androidBuilder,
                        variantManager,
                        taskManager,
                        androidConfig,
                        extraModelInfo,
                        ndkHandler,
                        nativeLibraryFactory,
                        AndroidProject.PROJECT_TYPE_APP,
                        AndroidProject.GENERATION_ORIGINAL);
    }

    @Test
    public void testEmptyMinimalisticModel() {
        assertThat(modelBuilder.buildMinimalisticModel()).isNotNull();
        assertThat(modelBuilder.buildMinimalisticModel().getVariantsBuildOutput()).isEmpty();
    }

    @Test
    public void testSingleVariantNoOutputMinimalisticModel() {

        GradleVariantConfiguration variantConfiguration =
                Mockito.mock(GradleVariantConfiguration.class);
        when(variantConfiguration.getDirName()).thenReturn("variant/name");

        VariantScope variantScope =
                createVariantScope("variantName", "variant/name", variantConfiguration);
        createVariantData(variantScope, variantConfiguration);
        when(variantManager.getVariantScopes()).thenReturn(ImmutableList.of(variantScope));

        assertThat(modelBuilder.buildMinimalisticModel()).isNotNull();
        Collection<VariantBuildOutput> variantsBuildOutput =
                modelBuilder.buildMinimalisticModel().getVariantsBuildOutput();
        assertThat(variantsBuildOutput).hasSize(1);
        VariantBuildOutput variantBuildOutput =
                Iterators.getOnlyElement(variantsBuildOutput.iterator());
        assertThat(variantBuildOutput.getName()).isEqualTo("variantName");
        Collection<OutputFile> variantOutputs = variantBuildOutput.getOutputs();
        assertThat(variantOutputs).isNotNull();
        assertThat(variantOutputs).hasSize(0);
    }

    @Test
    public void testSingleVariantWithOutputMinimalisticModel() throws IOException {

        GradleVariantConfiguration variantConfiguration =
                Mockito.mock(GradleVariantConfiguration.class);
        when(variantConfiguration.getDirName()).thenReturn("variant/name");

        VariantScope variantScope =
                createVariantScope("variantName", "variant/name", variantConfiguration);
        createVariantData(variantScope, variantConfiguration);

        when(variantManager.getVariantScopes()).thenReturn(ImmutableList.of(variantScope));

        OutputScope outputScope = new OutputScope();
        File variantOutputFolder = new File(apkLocation, FileUtils.join("variant", "name"));
        File apkOutput = new File(variantOutputFolder, "main.apk");
        Files.createParentDirs(apkOutput);
        Files.write("some apk", apkOutput, Charsets.UTF_8);

        OutputFactory outputFactory = new OutputFactory(PROJECT, variantConfiguration, outputScope);
        outputScope.addOutputForSplit(
                TaskOutputHolder.TaskOutputType.APK, outputFactory.addMainApk(), apkOutput);
        outputScope.save(
                ImmutableList.of(TaskOutputHolder.TaskOutputType.APK), variantOutputFolder);

        ProjectBuildOutput projectBuildOutput = modelBuilder.buildMinimalisticModel();
        assertThat(projectBuildOutput).isNotNull();
        Collection<VariantBuildOutput> variantsBuildOutput =
                projectBuildOutput.getVariantsBuildOutput();
        assertThat(variantsBuildOutput).hasSize(1);

        VariantBuildOutput variantBuildOutput =
                Iterators.getOnlyElement(variantsBuildOutput.iterator());
        assertThat(variantBuildOutput.getName()).isEqualTo("variantName");
        Collection<OutputFile> variantOutputs = variantBuildOutput.getOutputs();
        assertThat(variantOutputs).isNotNull();
        assertThat(variantOutputs).hasSize(1);

        OutputFile buildOutput = Iterators.getOnlyElement(variantOutputs.iterator());
        assertThat(buildOutput.getOutputType()).isEqualTo("MAIN");
        assertThat(buildOutput.getFilters()).isEmpty();
        assertThat(buildOutput.getFilterTypes()).isEmpty();
        assertThat(buildOutput.getMainOutputFile()).isEqualTo(buildOutput);
        assertThat(buildOutput.getOutputFile().exists()).isTrue();
        assertThat(Files.readFirstLine(buildOutput.getOutputFile(), Charsets.UTF_8))
                .isEqualTo("some apk");
    }

    @Test
    public void testSingleVariantWithMultipleOutputMinimalisticModel() throws IOException {

        GradleVariantConfiguration variantConfiguration =
                Mockito.mock(GradleVariantConfiguration.class);
        when(variantConfiguration.getDirName()).thenReturn("variant/name");

        VariantScope variantScope =
                createVariantScope("variantName", "variant/name", variantConfiguration);
        createVariantData(variantScope, variantConfiguration);

        when(variantManager.getVariantScopes()).thenReturn(ImmutableList.of(variantScope));
        OutputScope outputScope = new OutputScope();
        OutputFactory outputFactory = new OutputFactory(PROJECT, variantConfiguration, outputScope);

        File variantOutputFolder = new File(apkLocation, FileUtils.join("variant", "name"));

        File apkOutput = createApk(variantOutputFolder, "main.apk");

        outputScope.addOutputForSplit(
                TaskOutputHolder.TaskOutputType.APK, outputFactory.addMainApk(), apkOutput);

        for (int i = 0; i < 5; i++) {
            apkOutput = createApk(variantOutputFolder, "split_" + i + ".apk");

            outputScope.addOutputForSplit(
                    TaskOutputHolder.TaskOutputType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT,
                    outputFactory.addConfigurationSplit(
                            VariantOutput.FilterType.DENSITY, "hdpi", apkOutput.getName()),
                    apkOutput);
        }
        outputScope.save(
                ImmutableList.of(
                        TaskOutputHolder.TaskOutputType.APK,
                        TaskOutputHolder.TaskOutputType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT),
                variantOutputFolder);

        ProjectBuildOutput projectBuildOutput = modelBuilder.buildMinimalisticModel();
        assertThat(projectBuildOutput).isNotNull();
        Collection<VariantBuildOutput> variantsBuildOutput =
                projectBuildOutput.getVariantsBuildOutput();
        assertThat(variantsBuildOutput).hasSize(1);

        VariantBuildOutput variantBuildOutput =
                Iterators.getOnlyElement(variantsBuildOutput.iterator());
        assertThat(variantBuildOutput.getName()).isEqualTo("variantName");
        Collection<OutputFile> variantOutputs = variantBuildOutput.getOutputs();
        assertThat(variantOutputs).isNotNull();
        assertThat(variantOutputs).hasSize(6);

        for (OutputFile buildOutput : variantOutputs) {
            assertThat(buildOutput.getOutputType()).isAnyOf("MAIN", "SPLIT");
            if (buildOutput.getOutputType().equals("MAIN")) {
                assertThat(buildOutput.getFilters()).isEmpty();
                assertThat(buildOutput.getFilterTypes()).isEmpty();
            } else {
                assertThat(buildOutput.getFilters()).hasSize(1);
                FilterData filterData =
                        Iterators.getOnlyElement(buildOutput.getFilters().iterator());
                assertThat(filterData.getFilterType()).isEqualTo("DENSITY");
                assertThat(filterData.getIdentifier()).isEqualTo("hdpi");
                assertThat(buildOutput.getFilterTypes()).containsExactly("DENSITY");
            }
            assertThat(buildOutput.getMainOutputFile()).isEqualTo(buildOutput);
            assertThat(buildOutput.getOutputFile().exists()).isTrue();
            assertThat(Files.readFirstLine(buildOutput.getOutputFile(), Charsets.UTF_8))
                    .isEqualTo("some apk");
        }
    }

    @Test
    public void testMultipleVariantWithOutputMinimalisticModel() throws IOException {

        List<String> expectedVariantNames = new ArrayList<>();
        ImmutableList.Builder<VariantScope> scopes = ImmutableList.builder();
        for (int i = 0; i < 5; i++) {
            GradleVariantConfiguration variantConfiguration =
                    Mockito.mock(GradleVariantConfiguration.class);
            when(variantConfiguration.getDirName()).thenReturn("variant/name" + i);

            String variantName = "variantName" + i;
            VariantScope variantScope =
                    createVariantScope(variantName, "variant/name" + i, variantConfiguration);
            expectedVariantNames.add(variantName);
            createVariantData(variantScope, variantConfiguration);

            OutputScope outputScope = new OutputScope();
            File variantOutputFolder = new File(apkLocation, FileUtils.join("variant", "name" + i));
            File apkOutput = new File(variantOutputFolder, "main.apk");
            Files.createParentDirs(apkOutput);
            Files.write("some apk", apkOutput, Charsets.UTF_8);

            OutputFactory outputFactory =
                    new OutputFactory(PROJECT, variantConfiguration, outputScope);
            outputScope.addOutputForSplit(
                    TaskOutputHolder.TaskOutputType.APK, outputFactory.addMainApk(), apkOutput);
            outputScope.save(
                    ImmutableList.of(TaskOutputHolder.TaskOutputType.APK), variantOutputFolder);
            scopes.add(variantScope);
        }

        when(variantManager.getVariantScopes()).thenReturn(scopes.build());

        ProjectBuildOutput projectBuildOutput = modelBuilder.buildMinimalisticModel();
        assertThat(projectBuildOutput).isNotNull();
        Collection<VariantBuildOutput> variantsBuildOutput =
                projectBuildOutput.getVariantsBuildOutput();
        assertThat(variantsBuildOutput).hasSize(5);

        for (VariantBuildOutput variantBuildOutput : variantsBuildOutput) {
            assertThat(variantBuildOutput.getName()).startsWith("variantName");
            expectedVariantNames.remove(variantBuildOutput.getName());
            Collection<OutputFile> variantOutputs = variantBuildOutput.getOutputs();
            assertThat(variantOutputs).isNotNull();
            assertThat(variantOutputs).hasSize(1);

            OutputFile buildOutput = Iterators.getOnlyElement(variantOutputs.iterator());
            assertThat(buildOutput.getOutputType()).isEqualTo("MAIN");
            assertThat(buildOutput.getFilters()).isEmpty();
            assertThat(buildOutput.getFilterTypes()).isEmpty();
            assertThat(buildOutput.getMainOutputFile()).isEqualTo(buildOutput);
            assertThat(buildOutput.getOutputFile().exists()).isTrue();
            assertThat(Files.readFirstLine(buildOutput.getOutputFile(), Charsets.UTF_8))
                    .isEqualTo("some apk");
        }
        assertThat(expectedVariantNames).isEmpty();
    }

    @Test
    public void testSingleVariantWithOutputWithSingleTestVariantMinimalisticModel()
            throws IOException {

        GradleVariantConfiguration variantConfiguration =
                Mockito.mock(GradleVariantConfiguration.class);
        when(variantConfiguration.getDirName()).thenReturn("variant/name");
        when(variantConfiguration.getType()).thenReturn(VariantType.LIBRARY);

        VariantScope variantScope =
                createVariantScope("variantName", "variant/name", variantConfiguration);
        BaseVariantData variantData = createVariantData(variantScope, variantConfiguration);

        GradleVariantConfiguration testVariantConfiguration =
                Mockito.mock(GradleVariantConfiguration.class);
        when(testVariantConfiguration.getDirName()).thenReturn("test/name");
        when(testVariantConfiguration.getType()).thenReturn(VariantType.UNIT_TEST);

        VariantScope testVariantScope =
                createVariantScope("testVariant", "test/name", testVariantConfiguration);
        BaseVariantData testVariantData =
                createVariantData(testVariantScope, testVariantConfiguration);
        when(testVariantData.getType()).thenReturn(VariantType.UNIT_TEST);
        when(testVariantScope.getTestedVariantData()).thenReturn(variantData);

        FileCollection outputCollection = Mockito.mock(FileCollection.class);
        when(outputCollection.getSingleFile()).thenReturn(temporaryFolder.getRoot());
        Iterator outputIterator = Mockito.mock(Iterator.class);
        when(outputIterator.next()).thenReturn(temporaryFolder.getRoot());
        when(outputCollection.iterator()).thenReturn(outputIterator);
        when(testVariantScope.getOutput(ArgumentMatchers.eq(ALL_CLASSES)))
                .thenReturn(outputCollection);

        when(variantManager.getVariantScopes())
                .thenReturn(ImmutableList.of(variantScope, testVariantScope));

        OutputScope outputScope = new OutputScope();
        File variantOutputFolder = new File(apkLocation, FileUtils.join("variant", "name"));
        File apkOutput = new File(variantOutputFolder, "main.apk");
        Files.createParentDirs(apkOutput);
        Files.write("some apk", apkOutput, Charsets.UTF_8);

        OutputFactory outputFactory = new OutputFactory(PROJECT, variantConfiguration, outputScope);
        outputScope.addOutputForSplit(
                TaskOutputHolder.TaskOutputType.APK, outputFactory.addMainApk(), apkOutput);
        outputScope.save(
                ImmutableList.of(TaskOutputHolder.TaskOutputType.APK), variantOutputFolder);

        ProjectBuildOutput projectBuildOutput = modelBuilder.buildMinimalisticModel();
        assertThat(projectBuildOutput).isNotNull();
        Collection<VariantBuildOutput> variantsBuildOutput =
                projectBuildOutput.getVariantsBuildOutput();
        assertThat(variantsBuildOutput).hasSize(1);

        VariantBuildOutput variantBuildOutput =
                Iterators.getOnlyElement(variantsBuildOutput.iterator());
        assertThat(variantBuildOutput.getName()).isEqualTo("variantName");
        Collection<OutputFile> variantOutputs = variantBuildOutput.getOutputs();
        assertThat(variantOutputs).isNotNull();
        assertThat(variantOutputs).hasSize(1);

        // check the test variant.
        Collection<TestVariantBuildOutput> testingVariants =
                variantBuildOutput.getTestingVariants();
        assertThat(testingVariants).hasSize(1);
        TestVariantBuildOutput testVariant = Iterators.getOnlyElement(testingVariants.iterator());
        assertThat(testVariant.getName()).isEqualTo("testVariant");
        assertThat(testVariant.getTestedVariantName()).isEqualTo("variantName");
        assertThat(testVariant.getOutputs()).hasSize(1);
    }

    private static BaseVariantData createVariantData(
            VariantScope variantScope, GradleVariantConfiguration variantConfiguration) {
        BaseVariantData variantData = Mockito.mock(BaseVariantData.class);
        when(variantData.getType()).thenReturn(VariantType.DEFAULT);
        when(variantData.getScope()).thenReturn(variantScope);
        when(variantData.getVariantConfiguration()).thenReturn(variantConfiguration);
        when(variantScope.getVariantData()).thenReturn(variantData);
        return variantData;
    }

    private VariantScope createVariantScope(
            String variantName, String dirName, GradleVariantConfiguration variantConfiguration) {
        VariantScope variantScope = Mockito.mock(VariantScope.class);
        when(variantScope.getFullVariantName()).thenReturn(variantName);
        when(variantScope.getGlobalScope()).thenReturn(globalScope);
        when(variantScope.getApkLocation()).thenReturn(new File(apkLocation, dirName));
        when(variantScope.getVariantConfiguration()).thenReturn(variantConfiguration);

        final VariantType type = variantConfiguration.getType();
        //noinspection ConstantConditions
        if (type != null) {
            when(variantScope.getPublishingSpec())
                    .thenReturn(VariantPublishingSpec.getVariantSpec(type));
        }

        return variantScope;
    }

    private static File createApk(File variantOutputFolder, String fileName) throws IOException {
        File apkOutput = new File(variantOutputFolder, fileName);
        Files.createParentDirs(apkOutput);
        Files.write("some apk", apkOutput, Charsets.UTF_8);
        return apkOutput;
    }
}

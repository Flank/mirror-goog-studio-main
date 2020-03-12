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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.android.AndroidProjectTypes;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.api.artifact.PublicArtifactType;
import com.android.build.api.component.ComponentIdentity;
import com.android.build.api.component.impl.ComponentIdentityImpl;
import com.android.build.api.variant.BuiltArtifacts;
import com.android.build.api.variant.FilterConfiguration;
import com.android.build.api.variant.VariantOutputConfiguration;
import com.android.build.api.variant.impl.BuiltArtifactImpl;
import com.android.build.api.variant.impl.BuiltArtifactsImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.VariantManager;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.errors.SyncIssueReporter;
import com.android.build.gradle.internal.errors.SyncIssueReporterImpl;
import com.android.build.gradle.internal.fixtures.FakeGradleDirectory;
import com.android.build.gradle.internal.fixtures.FakeGradleProvider;
import com.android.build.gradle.internal.fixtures.FakeLogger;
import com.android.build.gradle.internal.publishing.PublishingSpecs;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.OutputFactory;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.VariantInputModelBuilder;
import com.android.build.gradle.internal.variant.VariantModel;
import com.android.build.gradle.internal.variant.VariantModelImpl;
import com.android.build.gradle.internal.variant2.DslScopeImpl;
import com.android.build.gradle.internal.variant2.FakeDslScope;
import com.android.build.gradle.options.SyncOptions;
import com.android.builder.core.VariantType;
import com.android.builder.core.VariantTypeImpl;
import com.android.builder.errors.IssueReporter;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.TestVariantBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.impldep.com.google.common.base.Charsets;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Tests for the {@link com.android.build.gradle.internal.ide.ModelBuilder} */
public class ModelBuilderTest {

    private static final String PROJECT = "project";

    @Mock GlobalScope globalScope;
    @Mock Project project;
    @Mock Gradle gradle;
    @Mock VariantManager variantManager;
    @Mock TaskManager taskManager;
    @Mock BaseExtension extension;
    @Mock ExtraModelInfo extraModelInfo;
    @Mock BuildArtifactsHolder artifacts;

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    ModelBuilder modelBuilder;
    File apkLocation;
    SyncIssueReporter syncIssueReporter;

    @Before
    public void setUp() throws IOException {

        MockitoAnnotations.initMocks(this);

        when(globalScope.getProject()).thenReturn(project);

        when(project.getGradle()).thenReturn(gradle);
        when(project.getProjectDir()).thenReturn(new File(""));

        when(gradle.getRootProject()).thenReturn(project);
        when(gradle.getParent()).thenReturn(null);
        when(gradle.getIncludedBuilds()).thenReturn(ImmutableList.of());

        syncIssueReporter =
                new SyncIssueReporterImpl(SyncOptions.EvaluationMode.IDE, new FakeLogger());

        DslScopeImpl dslScope = FakeDslScope.createFakeDslScope(syncIssueReporter);
        when(globalScope.getDslScope()).thenReturn(dslScope);

        apkLocation = temporaryFolder.newFolder("apk");

        // Register a builder for the custom tooling model
        VariantModel variantModel =
                new VariantModelImpl(
                        new VariantInputModelBuilder(FakeDslScope.createFakeDslScope()).toModel(),
                        extension::getTestBuildType,
                        variantManager,
                        syncIssueReporter);

        modelBuilder =
                new ModelBuilder<>(
                        globalScope,
                        variantModel,
                        taskManager,
                        extension,
                        extraModelInfo,
                        syncIssueReporter,
                        AndroidProjectTypes.PROJECT_TYPE_APP);
    }

    @Test
    public void testEmptyMinimalisticModel() {
        assertThat(modelBuilder.buildMinimalisticModel()).isNotNull();
        assertThat(modelBuilder.buildMinimalisticModel().getVariantsBuildOutput()).isEmpty();
    }

    @Test
    public void testSingleVariantNoOutputMinimalisticModel() {

        VariantDslInfo variantDslInfo = Mockito.mock(VariantDslInfo.class);
        when(variantDslInfo.getDirName()).thenReturn("variant/name");
        when(variantDslInfo.getVariantType()).thenReturn(VariantTypeImpl.BASE_APK);

        VariantScope variantScope =
                createVariantScope("variantName", "variant/name", variantDslInfo);
        createVariantData(variantScope, variantDslInfo);
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

        VariantDslInfo variantDslInfo = Mockito.mock(VariantDslInfo.class);
        when(variantDslInfo.getDirName()).thenReturn("variant/name");
        when(variantDslInfo.getVariantType()).thenReturn(VariantTypeImpl.BASE_APK);

        VariantScope variantScope =
                createVariantScope("variantName", "variant/name", variantDslInfo);
        createVariantData(variantScope, variantDslInfo);

        when(variantManager.getVariantScopes()).thenReturn(ImmutableList.of(variantScope));

        File variantOutputFolder = new File(apkLocation, FileUtils.join("variant", "name"));
        File apkOutput = new File(variantOutputFolder, "main.apk");
        Files.createParentDirs(apkOutput);
        Files.asCharSink(apkOutput, Charsets.UTF_8).write("some apk");

        OutputFactory outputFactory = new OutputFactory(PROJECT, variantDslInfo);

        new BuiltArtifactsImpl(
                        BuiltArtifacts.METADATA_FILE_VERSION,
                        PublicArtifactType.APK.INSTANCE,
                        "com.android.test",
                        "debug",
                        ImmutableList.of(
                                new BuiltArtifactImpl(
                                        apkOutput.toPath(),
                                        ImmutableMap.of(),
                                        123,
                                        "version_name",
                                        true,
                                        VariantOutputConfiguration.OutputType.SINGLE,
                                        ImmutableList.of())))
                .save(new FakeGradleDirectory(variantOutputFolder));

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
    public void testNewCodeWithOldBuildElementsFileFormat() throws IOException {

        VariantDslInfo variantDslInfo = Mockito.mock(VariantDslInfo.class);
        when(variantDslInfo.getDirName()).thenReturn("variant/name");
        when(variantDslInfo.getVariantType()).thenReturn(VariantTypeImpl.BASE_APK);

        VariantScope variantScope =
                createVariantScope("variantName", "variant/name", variantDslInfo);
        createVariantData(variantScope, variantDslInfo);

        when(variantManager.getVariantScopes()).thenReturn(ImmutableList.of(variantScope));

        File variantOutputFolder = new File(apkLocation, FileUtils.join("variant", "name"));
        File apkOutput = new File(variantOutputFolder, "main.apk");
        Files.createParentDirs(apkOutput);
        Files.asCharSink(apkOutput, Charsets.UTF_8).write("some apk");

        OutputFactory outputFactory = new OutputFactory(PROJECT, variantDslInfo);

        new BuildElements(
                        BuildElements.METADATA_FILE_VERSION,
                        "com.android.test",
                        "debug",
                        ImmutableList.of(
                                new BuildOutput(
                                        PublicArtifactType.APK.INSTANCE,
                                        ApkData.of(
                                                VariantOutput.OutputType.MAIN,
                                                ImmutableList.of(),
                                                123),
                                        apkOutput)))
                .save(variantOutputFolder);

        ProjectBuildOutput projectBuildOutput = modelBuilder.buildMinimalisticModel();
    }

    @Test
    public void testSingleVariantWithMultipleOutputMinimalisticModel() throws IOException {

        VariantDslInfo variantDslInfo = Mockito.mock(VariantDslInfo.class);
        when(variantDslInfo.getDirName()).thenReturn("variant/name");
        when(variantDslInfo.getVariantType()).thenReturn(VariantTypeImpl.BASE_APK);

        VariantScope variantScope =
                createVariantScope("variantName", "variant/name", variantDslInfo);
        createVariantData(variantScope, variantDslInfo);

        when(variantManager.getVariantScopes()).thenReturn(ImmutableList.of(variantScope));

        OutputFactory outputFactory = new OutputFactory(PROJECT, variantDslInfo);

        File variantOutputFolder = new File(apkLocation, FileUtils.join("variant", "name"));

        ImmutableList.Builder<BuiltArtifactImpl> builtArtifactsBuilder = ImmutableList.builder();

        for (int i = 0; i < 5; i++) {
            File apkOutput = createApk(variantOutputFolder, "split_" + i + ".apk");

            builtArtifactsBuilder.add(
                    new BuiltArtifactImpl(
                            apkOutput.toPath(),
                            ImmutableMap.of(),
                            123,
                            "version_name",
                            true,
                            VariantOutputConfiguration.OutputType.ONE_OF_MANY,
                            ImmutableList.of(
                                    new FilterConfiguration(
                                            FilterConfiguration.FilterType.DENSITY, "hdpi"))));
        }

        new BuiltArtifactsImpl(
                        BuiltArtifacts.METADATA_FILE_VERSION,
                        PublicArtifactType.APK.INSTANCE,
                        "com.android.test",
                        "debug",
                        builtArtifactsBuilder.build())
                .save(new FakeGradleDirectory(variantOutputFolder));

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
        assertThat(variantOutputs).hasSize(5);

        for (OutputFile buildOutput : variantOutputs) {
            assertThat(buildOutput.getOutputType()).isEqualTo("FULL_SPLIT");
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
            VariantDslInfo variantDslInfo = Mockito.mock(VariantDslInfo.class);
            when(variantDslInfo.getDirName()).thenReturn("variant/name" + i);
            when(variantDslInfo.getVariantType()).thenReturn(VariantTypeImpl.BASE_APK);

            String variantName = "variantName" + i;
            VariantScope variantScope =
                    createVariantScope(variantName, "variant/name" + i, variantDslInfo);
            expectedVariantNames.add(variantName);
            createVariantData(variantScope, variantDslInfo);

            File variantOutputFolder = new File(apkLocation, FileUtils.join("variant", "name" + i));
            File apkOutput = new File(variantOutputFolder, "main.apk");
            Files.createParentDirs(apkOutput);
            Files.asCharSink(apkOutput, Charsets.UTF_8).write("some apk");

            OutputFactory outputFactory = new OutputFactory(PROJECT, variantDslInfo);
            new BuiltArtifactsImpl(
                            BuiltArtifacts.METADATA_FILE_VERSION,
                            PublicArtifactType.APK.INSTANCE,
                            "com.android.test",
                            "debug",
                            ImmutableList.of(
                                    new BuiltArtifactImpl(
                                            apkOutput.toPath(),
                                            ImmutableMap.of(),
                                            123,
                                            "version_name",
                                            true,
                                            VariantOutputConfiguration.OutputType.SINGLE,
                                            ImmutableList.of())))
                    .save(new FakeGradleDirectory(variantOutputFolder));

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

        VariantDslInfo variantDslInfo = Mockito.mock(VariantDslInfo.class);
        when(variantDslInfo.getDirName()).thenReturn("variant/name");
        when(variantDslInfo.getVariantType()).thenReturn(VariantTypeImpl.LIBRARY);

        VariantScope variantScope =
                createVariantScope("variantName", "variant/name", variantDslInfo);
        when(variantScope.getArtifacts()).thenReturn(artifacts);
        BaseVariantData variantData = createVariantData(variantScope, variantDslInfo);

        RegularFile regularFileMock = Mockito.mock(RegularFile.class);
        when(regularFileMock.getAsFile()).thenReturn(temporaryFolder.getRoot());
        when(artifacts.getFinalProduct(InternalArtifactType.AAR.INSTANCE))
                .thenReturn(new FakeGradleProvider<>(regularFileMock));

        VariantDslInfo testVariantConfiguration = Mockito.mock(VariantDslInfo.class);
        when(testVariantConfiguration.getDirName()).thenReturn("test/name");
        when(testVariantConfiguration.getVariantType()).thenReturn(VariantTypeImpl.UNIT_TEST);

        VariantScope testVariantScope =
                createVariantScope("testVariant", "test/name", testVariantConfiguration);
        BaseVariantData testVariantData =
                createVariantData(testVariantScope, testVariantConfiguration);
        when(testVariantData.getType()).thenReturn(VariantTypeImpl.UNIT_TEST);
        when(testVariantScope.getTestedVariantData()).thenReturn(variantData);

        when(testVariantScope.getArtifacts()).thenReturn(artifacts);
        FileCollection testBuildableArtifact = Mockito.mock(FileCollection.class);
        when(artifacts.getFinalProductAsFileCollection(any()))
                .thenReturn(new FakeGradleProvider(testBuildableArtifact));
        when(testBuildableArtifact.iterator())
                .thenReturn(ImmutableSet.of(temporaryFolder.getRoot()).iterator());

        when(variantManager.getVariantScopes())
                .thenReturn(ImmutableList.of(variantScope, testVariantScope));

        File variantOutputFolder = new File(apkLocation, FileUtils.join("variant", "name"));
        File apkOutput = new File(variantOutputFolder, "main.apk");
        Files.createParentDirs(apkOutput);
        Files.asCharSink(apkOutput, Charsets.UTF_8).write("some apk");

        OutputFactory outputFactory = new OutputFactory(PROJECT, variantDslInfo);
        new BuildElements(
                        BuildElements.METADATA_FILE_VERSION,
                        "com.android.test",
                        VariantTypeImpl.BASE_APK.toString(),
                        ImmutableList.of(
                                new BuildOutput(
                                        InternalArtifactType.APK.INSTANCE,
                                        outputFactory.addMainApk(),
                                        apkOutput)))
                .save(variantOutputFolder);

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

    @Test(expected = IllegalStateException.class)
    public void syncIssueModelLockdown() {
        // This should lock down the issue handler.
        modelBuilder.buildAll("com.android.builder.model.ProjectSyncIssues", project);
        // And then trying to report anything after fetching the sync issues model should throw.
        syncIssueReporter.reportWarning(IssueReporter.Type.GENERIC, "Should Fail");
    }

    private static BaseVariantData createVariantData(
            VariantScope variantScope, VariantDslInfo variantDslInfo) {
        BaseVariantData variantData = Mockito.mock(BaseVariantData.class);
        final VariantType type = variantDslInfo.getVariantType();
        when(variantData.getType()).thenReturn(type);
        when(variantData.getScope()).thenReturn(variantScope);
        when(variantData.getVariantDslInfo()).thenReturn(variantDslInfo);

        when(variantScope.getVariantData()).thenReturn(variantData);

        return variantData;
    }

    private VariantScope createVariantScope(
            String variantName, String dirName, VariantDslInfo variantDslInfo) {
        VariantScope variantScope = Mockito.mock(VariantScope.class);
        when(variantScope.getName()).thenReturn(variantName);
        when(variantScope.getGlobalScope()).thenReturn(globalScope);
        when(variantScope.getApkLocation()).thenReturn(new File(apkLocation, dirName));
        when(variantScope.getVariantDslInfo()).thenReturn(variantDslInfo);

        ComponentIdentity variantConfig =
                new ComponentIdentityImpl(variantName, "flavorName", "debug", ImmutableList.of());
        when(variantDslInfo.getComponentIdentity()).thenReturn(variantConfig);

        final VariantType type = variantDslInfo.getVariantType();
        when(variantScope.getType()).thenReturn(type);

        //noinspection ConstantConditions
        if (type != null) {
            when(variantScope.getPublishingSpec()).thenReturn(PublishingSpecs.getVariantSpec(type));
        }

        return variantScope;
    }

    private static File createApk(File variantOutputFolder, String fileName) throws IOException {
        File apkOutput = new File(variantOutputFolder, fileName);
        Files.createParentDirs(apkOutput);
        Files.asCharSink(apkOutput, Charsets.UTF_8).write("some apk");
        return apkOutput;
    }
}

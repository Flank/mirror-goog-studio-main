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
import static org.mockito.Mockito.when;

import com.android.AndroidProjectTypes;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.component.ComponentIdentity;
import com.android.build.api.component.impl.AndroidTestPropertiesImpl;
import com.android.build.api.component.impl.ComponentIdentityImpl;
import com.android.build.api.component.impl.ComponentPropertiesImpl;
import com.android.build.api.component.impl.TestComponentPropertiesImpl;
import com.android.build.api.component.impl.UnitTestPropertiesImpl;
import com.android.build.api.variant.DependenciesInfo;
import com.android.build.api.variant.impl.ApplicationVariantPropertiesImpl;
import com.android.build.api.variant.impl.VariantPropertiesImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.core.VariantSources;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.errors.SyncIssueReporter;
import com.android.build.gradle.internal.errors.SyncIssueReporterImpl;
import com.android.build.gradle.internal.fixtures.FakeLogger;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.publishing.PublishingSpecs;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.BuildFeatureValues;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.MutableTaskContainer;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.internal.services.FakeServices;
import com.android.build.gradle.internal.services.ProjectServices;
import com.android.build.gradle.internal.services.TaskCreationServices;
import com.android.build.gradle.internal.services.VariantPropertiesApiServices;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.VariantInputModelBuilder;
import com.android.build.gradle.internal.variant.VariantModel;
import com.android.build.gradle.internal.variant.VariantModelImpl;
import com.android.build.gradle.internal.variant.VariantPathHelper;
import com.android.build.gradle.options.SyncOptions;
import com.android.builder.core.VariantType;
import com.android.builder.errors.IssueReporter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.gradle.api.Project;
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
    @Mock BaseExtension extension;
    @Mock ExtraModelInfo extraModelInfo;
    @Mock BuildArtifactsHolder artifacts;

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private List<VariantPropertiesImpl> variantList = Lists.newArrayList();
    private List<TestComponentPropertiesImpl> testComponentList = Lists.newArrayList();

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

        ProjectServices projectServices = FakeServices.createProjectServices(syncIssueReporter);
        DslServices dslServices = FakeServices.createDslServices(projectServices);
        when(globalScope.getDslServices()).thenReturn(dslServices);

        apkLocation = temporaryFolder.newFolder("apk");

        // Register a builder for the custom tooling model
        VariantModel variantModel =
                new VariantModelImpl(
                        new VariantInputModelBuilder(FakeServices.createDslServices()).toModel(),
                        extension::getTestBuildType,
                        () -> variantList,
                        () -> testComponentList,
                        syncIssueReporter);

        modelBuilder =
                new ModelBuilder<>(
                        globalScope,
                        variantModel,
                        extension,
                        extraModelInfo,
                        syncIssueReporter,
                        AndroidProjectTypes.PROJECT_TYPE_APP);
    }

    @Test(expected = IllegalStateException.class)
    public void syncIssueModelLockdown() {
        // This should lock down the issue handler.
        modelBuilder.buildAll("com.android.builder.model.ProjectSyncIssues", project);
        // And then trying to report anything after fetching the sync issues model should throw.
        syncIssueReporter.reportWarning(IssueReporter.Type.GENERIC, "Should Fail");
    }

    private <ComponentT extends ComponentPropertiesImpl> ComponentT createComponentProperties(
            @NonNull String variantName,
            @NonNull String dirName,
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull Class<ComponentT> componentClass,
            @Nullable VariantPropertiesImpl testedVariant) {
        // prepare the objects required for the constructor
        final VariantType type = variantDslInfo.getVariantType();
        VariantPropertiesApiServices variantPropertiesApiServices =
                FakeServices.createVariantPropertiesApiServices();
        TaskCreationServices taskCreationServices = FakeServices.createTaskCreationServices();

        ComponentIdentity componentIdentity =
                new ComponentIdentityImpl(variantName, "flavorName", "debug", ImmutableList.of());

        when(variantDslInfo.getComponentIdentity()).thenReturn(componentIdentity);

        VariantDependencies variantDependencies = Mockito.mock(VariantDependencies.class);
        VariantSources variantSources = Mockito.mock(VariantSources.class);

        VariantPathHelper paths = Mockito.mock(VariantPathHelper.class);
        when(paths.getApkLocation()).thenReturn(new File(apkLocation, dirName));

        VariantScope variantScope = Mockito.mock(VariantScope.class);
        when(variantScope.getPublishingSpec()).thenReturn(PublishingSpecs.getVariantSpec(type));

        BaseVariantData variantData = Mockito.mock(BaseVariantData.class);
        when(variantData.getTaskContainer()).thenReturn(new MutableTaskContainer());

        if (componentClass.equals(UnitTestPropertiesImpl.class)
                || componentClass.equals(AndroidTestPropertiesImpl.class)) {
            assertThat(testedVariant).named("tested variant").isNotNull();
            ComponentT unitTestComponent =
                    variantPropertiesApiServices.newInstance(
                            componentClass,
                            componentIdentity,
                            Mockito.mock(BuildFeatureValues.class),
                            variantDslInfo,
                            variantDependencies,
                            variantSources,
                            paths,
                            artifacts,
                            variantScope,
                            variantData,
                            testedVariant,
                            Mockito.mock(TransformManager.class),
                            variantPropertiesApiServices,
                            taskCreationServices,
                            globalScope);

            testedVariant
                    .getTestComponents()
                    .put(variantDslInfo.getVariantType(), unitTestComponent);

            return unitTestComponent;
        }

        assertThat(testedVariant).named("tested variant").isNull();

        if (componentClass.equals(ApplicationVariantPropertiesImpl.class)) {
            DependenciesInfo dependenciesInfo = Mockito.mock(DependenciesInfo.class);

            return variantPropertiesApiServices.newInstance(
                    componentClass,
                    componentIdentity,
                    Mockito.mock(BuildFeatureValues.class),
                    variantDslInfo,
                    variantDependencies,
                    variantSources,
                    paths,
                    artifacts,
                    variantScope,
                    variantData,
                    dependenciesInfo,
                    Mockito.mock(TransformManager.class),
                    variantPropertiesApiServices,
                    taskCreationServices,
                    globalScope);
        }

        return variantPropertiesApiServices.newInstance(
                componentClass,
                componentIdentity,
                Mockito.mock(BuildFeatureValues.class),
                variantDslInfo,
                variantDependencies,
                variantSources,
                paths,
                artifacts,
                variantScope,
                variantData,
                Mockito.mock(TransformManager.class),
                variantPropertiesApiServices,
                taskCreationServices,
                globalScope);
    }

    private static File createApk(File variantOutputFolder, String fileName) throws IOException {
        File apkOutput = new File(variantOutputFolder, fileName);
        Files.createParentDirs(apkOutput);
        Files.asCharSink(apkOutput, Charsets.UTF_8).write("some apk");
        return apkOutput;
    }
}

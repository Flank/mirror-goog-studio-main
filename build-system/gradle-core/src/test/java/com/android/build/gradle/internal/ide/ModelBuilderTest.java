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

import static org.mockito.Mockito.when;

import com.android.AndroidProjectTypes;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.component.TestComponentCreationConfig;
import com.android.build.gradle.internal.component.VariantCreationConfig;
import com.android.build.gradle.internal.dsl.ApplicationBuildFeaturesImpl;
import com.android.build.gradle.internal.errors.SyncIssueReporter;
import com.android.build.gradle.internal.errors.SyncIssueReporterImpl;
import com.android.build.gradle.internal.fixtures.FakeLogger;
import com.android.build.gradle.internal.fixtures.ProjectFactory;
import com.android.build.gradle.internal.profile.AnalyticsConfiguratorService;
import com.android.build.gradle.internal.scope.BuildFeatureValuesImpl;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.internal.services.FakeServices;
import com.android.build.gradle.internal.services.ProjectServices;
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig;
import com.android.build.gradle.internal.variant.VariantInputModelBuilder;
import com.android.build.gradle.internal.variant.VariantModel;
import com.android.build.gradle.internal.variant.VariantModelImpl;
import com.android.build.gradle.options.SyncOptions;
import com.android.builder.core.ComponentTypeImpl;
import com.android.builder.errors.IssueReporter;
import com.android.builder.model.v2.ide.ProjectType;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.gradle.api.Project;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for the {@link com.android.build.gradle.internal.ide.ModelBuilder} */
public class ModelBuilderTest {

    @Mock BaseExtension extension;
    @Mock ExtraModelInfo extraModelInfo;
    @Mock GlobalTaskCreationConfig globalConfig;

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final List<VariantCreationConfig> variantList = Lists.newArrayList();
    private final List<TestComponentCreationConfig> testComponentList = Lists.newArrayList();

    Project project;
    ModelBuilder<?> modelBuilder;
    File apkLocation;
    SyncIssueReporter syncIssueReporter;

    @Before
    public void setUp() throws IOException {

        MockitoAnnotations.initMocks(this);

        project = ProjectFactory.getProject();

        syncIssueReporter =
                new SyncIssueReporterImpl(
                        SyncOptions.EvaluationMode.IDE,
                        SyncOptions.ErrorFormatMode.HUMAN_READABLE,
                        new FakeLogger());

        ProjectServices projectServices =
                FakeServices.createProjectServices(project, syncIssueReporter);
        DslServices dslServices = FakeServices.createDslServices(projectServices);

        when(globalConfig.getServices()).thenReturn(dslServices);

        new SyncIssueReporterImpl.GlobalSyncIssueService.RegistrationAction(
                        project,
                        SyncOptions.EvaluationMode.IDE,
                        SyncOptions.ErrorFormatMode.HUMAN_READABLE)
                .execute();
        new AnalyticsConfiguratorService.RegistrationAction(project).execute();

        apkLocation = temporaryFolder.newFolder("apk");

        // Register a builder for the custom tooling model
        VariantModel variantModel =
                new VariantModelImpl(
                        new VariantInputModelBuilder(
                                        ComponentTypeImpl.BASE_APK,
                                        FakeServices.createDslServices())
                                .toModel(),
                        extension::getTestBuildType,
                        () -> variantList,
                        () -> testComponentList,
                        () ->
                                new BuildFeatureValuesImpl(
                                        dslServices.newInstance(ApplicationBuildFeaturesImpl.class),
                                        dslServices.getProjectOptions(),
                                        null,
                                        null),
                        AndroidProjectTypes.PROJECT_TYPE_APP,
                        ProjectType.APPLICATION,
                        globalConfig);

        modelBuilder = new ModelBuilder<>(project, variantModel, extension, extraModelInfo);
    }

    @Test(expected = IllegalStateException.class)
    public void syncIssueModelLockdown() {
        // This should lock down the issue handler.
        modelBuilder.buildAll("com.android.builder.model.ProjectSyncIssues", project);
        // And then trying to report anything after fetching the sync issues model should throw.
        syncIssueReporter.reportWarning(IssueReporter.Type.GENERIC, "Should Fail");
    }
}

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

package com.android.build.gradle.tasks.factory;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.internal.fixtures.FakeGradleProperty;
import com.android.build.gradle.internal.fixtures.FakeObjectFactory;
import com.android.build.gradle.internal.fixtures.FakeProviderFactory;
import com.android.build.gradle.internal.profile.AnalyticsService;
import com.android.build.gradle.internal.profile.ProjectData;
import com.android.build.gradle.internal.profile.TaskMetadata;
import com.android.build.gradle.tasks.JavaCompileUtils;
import com.android.builder.profile.NameAnonymizer;
import com.android.builder.profile.NameAnonymizerSerializer;
import com.google.wireless.android.sdk.stats.AnnotationProcessorInfo;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Test for JavaCompileTest. */
public class JavaCompileTest {
    private static final String VARIANT_NAME = "variant";
    private static final String projectPath = ":app";
    private AnalyticsService analyticsService;

    @ClassRule public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() {
        analyticsService = createAnalyticsService();
    }

    @Test
    public void processAnalyticsEmptyList() throws IOException {
        File inputFile = temporaryFolder.newFile();
        Files.write(inputFile.toPath(), "[]".getBytes(StandardCharsets.UTF_8));

        Map<String, Boolean> annotationProcessors =
                JavaCompileUtils.readAnnotationProcessorsFromJsonFile(inputFile);
        JavaCompileUtils.recordAnnotationProcessorsForAnalytics(
                 annotationProcessors, projectPath, VARIANT_NAME, analyticsService);

        Map<String, Boolean> processors = getProcessors();
        assertThat(processors).isEmpty();
    }

    @Test
    public void processAnalyticsMultipleProcessors() throws IOException {
        File inputFile = temporaryFolder.newFile();
        Files.write(
                inputFile.toPath(),
                "{\"processor1\":false,\"processor2\":true}".getBytes(StandardCharsets.UTF_8));

        Map<String, Boolean> annotationProcessors =
                JavaCompileUtils.readAnnotationProcessorsFromJsonFile(inputFile);
        JavaCompileUtils.recordAnnotationProcessorsForAnalytics(
                annotationProcessors, projectPath, VARIANT_NAME, analyticsService);

        Map<String, Boolean> processors = getProcessors();
        assertThat(processors).containsEntry("processor1", false);
        assertThat(processors).containsEntry("processor2", true);
        assertThat(annotationProcessingIncremental()).isFalse();

        Files.write(
                inputFile.toPath(), "{\"processor1\":true,\"processor2\":true}".getBytes("utf-8"));
        annotationProcessors = JavaCompileUtils.readAnnotationProcessorsFromJsonFile(inputFile);
        JavaCompileUtils.recordAnnotationProcessorsForAnalytics(
                annotationProcessors, projectPath, VARIANT_NAME, analyticsService);
        assertThat(annotationProcessingIncremental()).isTrue();
    }

    private Map<String, Boolean> getProcessors() {
        GradleBuildVariant.Builder variant =
                analyticsService.getVariantBuilder(projectPath, VARIANT_NAME);

        assertThat(variant).isNotNull();
        List<AnnotationProcessorInfo> procs = variant.getAnnotationProcessorsList();
        assertThat(procs).isNotNull();

        return procs.stream()
                .collect(
                        Collectors.toMap(
                                AnnotationProcessorInfo::getSpec,
                                AnnotationProcessorInfo::getIsIncremental));
    }

    private Boolean annotationProcessingIncremental() {
        GradleBuildVariant.Builder variant =
                analyticsService.getVariantBuilder(projectPath, VARIANT_NAME);

        assertThat(variant).isNotNull();
        return variant.getIsAnnotationProcessingIncremental();
    }

    private AnalyticsService createAnalyticsService() {
        return new AnalyticsService() {
            @NotNull
            @Override
            @Inject
            public ProviderFactory getProvider() {
                return FakeProviderFactory.getFactory();
            }

            @Override
            @Inject
            public Params getParameters() {
                return new Params() {
                    @NotNull
                    @Override
                    public Property<String> getProfile() {
                        byte[] profile = GradleBuildProfile.newBuilder().build().toByteArray();
                        return new FakeGradleProperty(Base64.getEncoder().encodeToString(profile));
                    }

                    @NotNull
                    @Override
                    public Property<String> getAnonymizer() {
                        return new FakeGradleProperty(
                                new NameAnonymizerSerializer().toJson(new NameAnonymizer()));
                    }

                    @NotNull
                    @Override
                    public MapProperty<String, ProjectData> getProjects() {
                        Map<String, ProjectData> map = new HashMap<>();
                        ProjectData projectData =
                                new ProjectData(GradleBuildProject.newBuilder().setId(1L));
                        projectData
                                .getVariantBuilders()
                                .put(VARIANT_NAME, GradleBuildVariant.newBuilder());
                        map.put(projectPath, projectData);
                        return FakeObjectFactory.getFactory()
                                .mapProperty(String.class, ProjectData.class)
                                .value(map);
                    }

                    @NotNull
                    @Override
                    public Property<Boolean> getEnableProfileJson() {
                        return new FakeGradleProperty(true);
                    }

                    @NotNull
                    @Override
                    public Property<File> getProfileDir() {
                        return new FakeGradleProperty();
                    }

                    @NotNull
                    @Override
                    public MapProperty<String, TaskMetadata> getTaskMetadata() {
                        return FakeObjectFactory.getFactory()
                                .mapProperty(String.class, TaskMetadata.class);
                    }

                    @NotNull
                    @Override
                    public Property<String> getRootProjectPath() {
                        return new FakeGradleProperty("/path");
                    }
                };
            }
        };
    }
}

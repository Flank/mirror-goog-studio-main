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

import com.android.build.gradle.tasks.JavaCompileUtils;
import com.android.builder.profile.ProcessProfileWriter;

import com.google.wireless.android.sdk.stats.AnnotationProcessorInfo;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Test for JavaCompileTest. */
public class JavaCompileTest {
    private static final String VARIANT_NAME = "variant";
    private static final String projectPath = ":app";

    @ClassRule public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void processAnalyticsEmptyList() throws IOException {
        File inputFile = temporaryFolder.newFile();
        Files.write(inputFile.toPath(), "[]".getBytes(StandardCharsets.UTF_8));

        Map<String, Boolean> annotationProcessors =
                JavaCompileUtils.readAnnotationProcessorsFromJsonFile(inputFile);
        JavaCompileUtils.recordAnnotationProcessorsForAnalytics(
                annotationProcessors, projectPath, VARIANT_NAME);

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
                annotationProcessors, projectPath, VARIANT_NAME);

        Map<String, Boolean> processors = getProcessors();
        assertThat(processors).containsEntry("processor1", false);
        assertThat(processors).containsEntry("processor2", true);
        assertThat(annotationProcessingIncremental()).isFalse();

        Files.write(
                inputFile.toPath(), "{\"processor1\":true,\"processor2\":true}".getBytes("utf-8"));
        annotationProcessors = JavaCompileUtils.readAnnotationProcessorsFromJsonFile(inputFile);
        JavaCompileUtils.recordAnnotationProcessorsForAnalytics(
                annotationProcessors, projectPath, VARIANT_NAME);
        assertThat(annotationProcessingIncremental()).isTrue();
    }

    private static Map<String, Boolean> getProcessors() {
        GradleBuildVariant.Builder variant =
                ProcessProfileWriter.getOrCreateVariant(projectPath, VARIANT_NAME);

        assertThat(variant).isNotNull();
        List<AnnotationProcessorInfo> procs = variant.getAnnotationProcessorsList();
        assertThat(procs).isNotNull();

        return procs.stream()
                .collect(
                        Collectors.toMap(
                                AnnotationProcessorInfo::getSpec,
                                AnnotationProcessorInfo::getIsIncremental));
    }

    private static Boolean annotationProcessingIncremental() {
        GradleBuildVariant.Builder variant =
                ProcessProfileWriter.getOrCreateVariant(projectPath, VARIANT_NAME);

        assertThat(variant).isNotNull();
        return variant.getIsAnnotationProcessingIncremental();
    }
}

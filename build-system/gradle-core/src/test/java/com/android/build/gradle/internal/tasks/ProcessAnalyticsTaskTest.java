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

package com.android.build.gradle.internal.tasks;

import static com.google.common.truth.Truth.assertThat;

import com.android.builder.profile.ProcessProfileWriter;
import com.android.builder.profile.ProcessProfileWriterFactory;
import com.google.wireless.android.sdk.stats.AnnotationProcessorInfo;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Test for ProcessAnalyticsTask */
public class ProcessAnalyticsTaskTest {
    private static final String VARIANT_NAME = "variant";

    @ClassRule public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    Project project;

    @Before
    public void setUp() throws IOException {
        File testDir = temporaryFolder.newFolder();
        project = ProjectBuilder.builder().withProjectDir(testDir).build();
        ProcessProfileWriterFactory.initializeForTests();
    }

    @Test
    public void processAnalyticsEmptyList() throws IOException {
        ProcessAnalyticsTask task = project.getTasks().create("test", ProcessAnalyticsTask.class);

        File inputFile = temporaryFolder.newFile();
        Files.write(inputFile.toPath(), "[]".getBytes("utf-8"));
        task.init(VARIANT_NAME, project.files(inputFile));

        task.processAnalytics();

        Collection<String> processorNames = getProcessorList();
        assertThat(processorNames).isEmpty();
    }

    @Test
    public void processAnalyticsMultipleProcessors() throws IOException {
        ProcessAnalyticsTask task = project.getTasks().create("test", ProcessAnalyticsTask.class);

        File inputFile = temporaryFolder.newFile();
        Files.write(inputFile.toPath(), "[\"processor1\", \"processor2\"]".getBytes("utf-8"));
        task.init(VARIANT_NAME, project.files(inputFile));
        task.processAnalytics();
        Collection<String> processorNames = getProcessorList();
        assertThat(processorNames).containsExactly("processor1", "processor2");
    }

    private Collection<String> getProcessorList() {
        GradleBuildVariant.Builder variant =
                ProcessProfileWriter.getOrCreateVariant(project.getPath(), VARIANT_NAME);

        assertThat(variant).isNotNull();
        List<AnnotationProcessorInfo> procs = variant.getAnnotationProcessorsList();
        assertThat(procs).isNotNull();

        return procs.stream().map(AnnotationProcessorInfo::getSpec).collect(Collectors.toList());
    }
}

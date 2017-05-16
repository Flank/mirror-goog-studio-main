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

import static com.android.testutils.truth.MoreTruth.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.build.gradle.internal.dsl.AnnotationProcessorOptions;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JavaPreCompileTaskTest {
    @ClassRule public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static final String processorMetaInf =
            "META-INF/services/javax.annotation.processing.Processor";

    private static final String testProcessorName = "com.google.test.MyAnnotationProcessor";

    private static final String dependencyWithProcessorJar = "dependencyWithProcessor.jar";

    private static File nonJarFile;
    private static File jar;
    private static File jarWithAnnotationProcessor;
    private static File directory;
    private static File directoryWithAnnotationProcessor;

    private Project project;
    private Configuration processorConfiguration;
    private JavaPreCompileTask task;
    private File outputFile;

    @BeforeClass
    public static void classSetUp() throws IOException {
        directory = temporaryFolder.newFolder();
        directoryWithAnnotationProcessor = temporaryFolder.newFolder();
        File processorMetaInfFile = new File(directoryWithAnnotationProcessor, processorMetaInf);
        Files.createParentDirs(processorMetaInfFile);
        assertThat(processorMetaInfFile.createNewFile()).isTrue();

        nonJarFile = temporaryFolder.newFile("notJar.txt");
        Files.write("This is not a jar file", nonJarFile, Charsets.UTF_8);

        jar = temporaryFolder.newFile("dependency.jar");
        ZipOutputStream jarOut = new ZipOutputStream(new FileOutputStream(jar));
        jarOut.close();

        jarWithAnnotationProcessor = temporaryFolder.newFile(dependencyWithProcessorJar);
        try (ZipOutputStream out =
                new ZipOutputStream(new FileOutputStream(jarWithAnnotationProcessor))) {
            out.putNextEntry(new ZipEntry(processorMetaInf));
            out.write(testProcessorName.getBytes());
            out.closeEntry();
        }
    }

    @Before
    public void setUp() throws IOException {
        File testDir = temporaryFolder.newFolder();
        outputFile = temporaryFolder.newFile();
        project = ProjectBuilder.builder().withProjectDir(testDir).build();
        task = project.getTasks().create("test", JavaPreCompileTask.class);
        processorConfiguration = project.getConfigurations().create("annotationProcessor");
    }

    @Test
    public void checkSuccessForNormalJar() throws IOException {
        FileCollection compileClasspath = project.files(jar, nonJarFile, directory);
        task.init(
                outputFile,
                processorConfiguration,
                compileClasspath,
                new AnnotationProcessorOptions(),
                false);

        task.preCompile();

        assertThat(getProcessorNames()).isEmpty();
    }

    @Test
    public void checkSuccessWithAnnotationProcessor() throws IOException {
        project.getDependencies()
                .add(
                        "annotationProcessor",
                        project.files(
                                jarWithAnnotationProcessor, directoryWithAnnotationProcessor));
        FileCollection compileClasspath =
                project.files(jarWithAnnotationProcessor, directoryWithAnnotationProcessor);
        task.init(
                outputFile,
                processorConfiguration,
                compileClasspath,
                new AnnotationProcessorOptions(),
                false);

        task.preCompile();

        assertThat(getProcessorNames())
                .containsExactly(
                        jarWithAnnotationProcessor.getName(),
                        directoryWithAnnotationProcessor.getName());
    }

    @Test
    public void checkErrorIsThrownForAnnotationProcessor() throws IOException {
        FileCollection compileClasspath =
                project.files(jarWithAnnotationProcessor, directoryWithAnnotationProcessor);
        task.init(
                outputFile,
                processorConfiguration,
                compileClasspath,
                new AnnotationProcessorOptions(),
                false);
        try {
            task.preCompile();
            fail("Expected to fail");
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains(jarWithAnnotationProcessor.getName());
            assertThat(e.getMessage()).contains(directoryWithAnnotationProcessor.getName());
        }
    }

    @Test
    public void checkSettingIncludeClasspathFalseDisableError() throws IOException {
        FileCollection compileClasspath = project.files(jarWithAnnotationProcessor);
        AnnotationProcessorOptions options = new AnnotationProcessorOptions();
        options.setIncludeCompileClasspath(false);
        task.init(outputFile, processorConfiguration, compileClasspath, options, false);

        task.preCompile();

        assertThat(getProcessorNames()).isEmpty();
    }

    @Test
    public void checkSettingIncludeClasspathTrueDisableError() throws IOException {
        FileCollection compileClasspath = project.files(jarWithAnnotationProcessor);
        AnnotationProcessorOptions options = new AnnotationProcessorOptions();
        options.setIncludeCompileClasspath(true);
        task.init(outputFile, processorConfiguration, compileClasspath, options, false);

        task.preCompile();

        assertThat(getProcessorNames()).containsExactly(jarWithAnnotationProcessor.getName());
    }

    @Test
    public void checkProcessorConfigurationAddedForMetrics() throws IOException {
        AnnotationProcessorOptions options = new AnnotationProcessorOptions();
        project.getDependencies()
                .add("annotationProcessor", project.files(jarWithAnnotationProcessor));
        task.init(outputFile, processorConfiguration, project.files(), options, false);
        task.preCompile();

        assertThat(getProcessorNames()).containsExactly(jarWithAnnotationProcessor.getName());
    }

    @Test
    public void checkExplicitProcessorAddedForMetrics() throws IOException {
        FileCollection compileClasspath = project.files();
        AnnotationProcessorOptions options = new AnnotationProcessorOptions();
        options.getClassNames().add(testProcessorName);
        task.init(outputFile, processorConfiguration, compileClasspath, options, false);
        task.preCompile();

        assertThat(getProcessorNames()).containsExactly(testProcessorName);
    }

    @Test
    public void checkDataBindingAddedForMetrics() throws IOException {
        FileCollection compileClasspath = project.files();
        task.init(
                outputFile,
                processorConfiguration,
                compileClasspath,
                new AnnotationProcessorOptions(),
                true);
        task.preCompile();

        assertThat(getProcessorNames()).containsExactly(JavaPreCompileTask.DATA_BINDING_SPEC);
    }

    @Test
    public void checkAllProcessorsAddedForMetrics() throws IOException {
        FileCollection compileClasspath = project.files(jarWithAnnotationProcessor);
        AnnotationProcessorOptions options = new AnnotationProcessorOptions();
        options.setIncludeCompileClasspath(true); // Disable exception.
        options.getClassNames().add(testProcessorName);

        task.init(outputFile, processorConfiguration, compileClasspath, options, true);
        task.preCompile();

        assertThat(getProcessorNames())
                .containsExactly(
                        testProcessorName,
                        JavaPreCompileTask.DATA_BINDING_SPEC,
                        dependencyWithProcessorJar);
    }

    private List<String> getProcessorNames() throws IOException {
        Gson gson = new GsonBuilder().create();
        assertThat(outputFile).isFile();
        FileReader reader = new FileReader(outputFile);
        return gson.fromJson(reader, new TypeToken<List<String>>() {}.getType());
    }
}

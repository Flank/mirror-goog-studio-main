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
import static org.junit.Assert.fail;

import com.android.build.gradle.internal.dsl.AnnotationProcessorOptions;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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

/** Test for JavaPreCompileTaskTest */
public class JavaPreCompileTaskTest {
    @ClassRule public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static final String processorMetaInf =
            "META-INF/services/javax.annotation.processing.Processor";

    private static File out;
    private static File nonJarFile;
    private static File jar;
    private static File jarWithAnnotationProcessor;
    private static File directory;
    private static File directoryWithAnnotationProcessor;

    private Project project;
    Configuration processorConfiguration;
    private JavaPreCompileTask task;

    @BeforeClass
    public static void classSetUp() throws IOException {
        out = temporaryFolder.newFolder();
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

        jarWithAnnotationProcessor = temporaryFolder.newFile("dependencyWithProcessor.jar");
        try (ZipOutputStream out =
                new ZipOutputStream(new FileOutputStream(jarWithAnnotationProcessor))) {
            out.putNextEntry(new ZipEntry(processorMetaInf));
            out.closeEntry();
        }
    }

    @Before
    public void setUp() throws IOException {
        File testDir = temporaryFolder.newFolder();
        project = ProjectBuilder.builder().withProjectDir(testDir).build();
        task = project.getTasks().create("test", JavaPreCompileTask.class);
        processorConfiguration = project.getConfigurations().create("annotationProcessor");
    }

    @Test
    public void checkSuccessForNormalJar() throws IOException {
        FileCollection compileClasspath = project.files(jar, nonJarFile, directory);
        task.init(out, processorConfiguration, compileClasspath, new AnnotationProcessorOptions());
        task.preCompile();
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
        task.init(out, processorConfiguration, compileClasspath, new AnnotationProcessorOptions());
        task.preCompile();
    }

    @Test
    public void checkErrorIsThrownForAnnotationProcessor() throws IOException {
        FileCollection compileClasspath =
                project.files(jarWithAnnotationProcessor, directoryWithAnnotationProcessor);
        task.init(out, processorConfiguration, compileClasspath, new AnnotationProcessorOptions());
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
        task.init(out, processorConfiguration, compileClasspath, options);
        task.preCompile();
    }

    @Test
    public void checkSettingIncludeClasspathTrueDisableError() throws IOException {
        FileCollection compileClasspath = project.files(jarWithAnnotationProcessor);
        AnnotationProcessorOptions options = new AnnotationProcessorOptions();
        options.setIncludeCompileClasspath(true);
        task.init(out, processorConfiguration, compileClasspath, options);
        task.preCompile();
    }
}

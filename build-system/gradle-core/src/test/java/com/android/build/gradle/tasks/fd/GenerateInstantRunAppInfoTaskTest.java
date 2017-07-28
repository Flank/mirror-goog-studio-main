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

package com.android.build.gradle.tasks.fd;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import com.android.build.VariantOutput;
import com.android.build.gradle.internal.incremental.AsmUtils;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.BuildOutputs;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.ide.common.build.ApkInfo;
import com.android.testutils.truth.MoreTruth;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.apache.commons.io.Charsets;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

/** Test for {@link GenerateInstantRunAppInfoTask} class */
public class GenerateInstantRunAppInfoTaskTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Mock ApkInfo apkInfo;
    @Mock FileCollection fileCollection;
    @Mock FileTree fileTree;
    @Mock InstantRunBuildContext buildContext;
    @Mock ILogger logger;

    Project project;
    GenerateInstantRunAppInfoTask task;
    File testDir;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        testDir = temporaryFolder.newFolder();
        project = ProjectBuilder.builder().withProjectDir(testDir).build();
        task = project.getTasks().create("test", GenerateInstantRunAppInfoTask.class);
        task.setMergedManifests(fileCollection);
        when(fileCollection.getAsFileTree()).thenReturn(fileTree);
        File outputFile = temporaryFolder.newFile();
        task.setOutputFile(outputFile);
        task.setBuildContext(buildContext);
        when(buildContext.getSecretToken()).thenReturn(12345L);
        when(apkInfo.getType()).thenReturn(VariantOutput.OutputType.SPLIT);
        when(apkInfo.getVersionCode()).thenReturn(12);
    }

    @Test
    public void testGeneration() throws IOException {

        File androidManifest = temporaryFolder.newFile();
        Files.write(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "      package=\"com.example.generateappinfo\"\n"
                        + "      android:versionCode=\"1\"\n"
                        + "      android:versionName=\"1.0\"\n"
                        + "      split=\"lib_slice_1_apk\">\n"
                        + "</manifest>",
                androidManifest,
                Charsets.UTF_8);

        BuildOutput buildOutput =
                new BuildOutput(
                        TaskOutputHolder.TaskOutputType.INSTANT_RUN_MERGED_MANIFESTS,
                        apkInfo,
                        androidManifest);
        File buildOutputs = temporaryFolder.newFolder("buildOutputs");
        String json =
                BuildOutputs.persist(
                        testDir.toPath(),
                        ImmutableList.of(
                                TaskOutputHolder.TaskOutputType.INSTANT_RUN_MERGED_MANIFESTS),
                        ImmutableSetMultimap.of(
                                TaskOutputHolder.TaskOutputType.INSTANT_RUN_MERGED_MANIFESTS,
                                buildOutput));
        Files.write(json, BuildOutputs.getMetadataFile(buildOutputs), Charsets.UTF_8);
        when(fileTree.getFiles())
                .thenReturn(ImmutableSet.of(BuildOutputs.getMetadataFile(buildOutputs)));

        task.generateInfoTask();

        MoreTruth.assertThat(task.getOutputFile()).exists();
        AsmUtils.JarBasedClassReader reader =
                new AsmUtils.JarBasedClassReader(task.getOutputFile());
        ClassReader classReader =
                reader.loadClassBytes("com.android.tools.fd.runtime.AppInfo", logger);
        assertThat(classReader).isNotNull();
        assertThat(classReader.getClassName()).isEqualTo("com/android/tools/fd/runtime/AppInfo");

        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, 0);
        List<FieldNode> fieldNodes = (List<FieldNode>) classNode.fields;
        assertThat(fieldNodes).hasSize(2);
        assertThat(hasField(fieldNodes, "applicationId")).isTrue();
        assertThat(hasField(fieldNodes, "token")).isTrue();
    }

    @Test
    public void testNoBuildOutput() throws IOException {
        File buildOutputs = temporaryFolder.newFolder("buildOutputs");
        String json =
                BuildOutputs.persist(
                        temporaryFolder.getRoot().toPath(),
                        ImmutableList.of(TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS),
                        ImmutableSetMultimap.of());
        Files.write(json, BuildOutputs.getMetadataFile(buildOutputs), Charsets.UTF_8);
        when(fileTree.getFiles())
                .thenReturn(ImmutableSet.of(BuildOutputs.getMetadataFile(buildOutputs)));

        assertThat(task.getOutputFile().delete()).isTrue();

        try {
            task.generateInfoTask();
            fail("Expected exception not thrown.");
        } catch (RuntimeException expected) {
            assertThat(expected.getMessage()).contains("clean");
        }
        MoreTruth.assertThat(task.getOutputFile()).doesNotExist();
    }

    private static boolean hasField(List<FieldNode> fields, String fieldName) {
        for (FieldNode field : fields) {
            if (field.name.equals(fieldName)) {
                return true;
            }
        }
        return false;
    }
}

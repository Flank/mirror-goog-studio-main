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

package com.android.build.gradle.internal.transforms;

import static android.databinding.tool.DataBindingBuilder.BINDING_CLASS_LIST_SUFFIX;
import static android.databinding.tool.DataBindingBuilder.INCREMENTAL_BINDING_CLASSES_LIST_DIR;
import static android.databinding.tool.DataBindingBuilder.INCREMENTAL_BIN_AAR_DIR;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder;
import com.android.build.gradle.internal.tasks.databinding.DataBindingMergeGenClassLogTransform;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.gradle.api.logging.Logger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for the transform that grabs data binding related artifacts from dependencies and combines
 * them for the annotation processor.
 */
public class DataBindingMergeGenClassLogTransformTest {

    @Mock Context context;

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File expectedOutFolder;

    @Mock Logger logger;

    @Before
    public void setUpMock() throws IOException {
        MockitoAnnotations.initMocks(this);
        expectedOutFolder = temporaryFolder.newFolder();
    }

    @Test
    public void nonIncrementalFromFolder()
            throws IOException, TransformException, InterruptedException {
        String classList1 = "class_list_1" + BINDING_CLASS_LIST_SUFFIX;
        String classList2 = "class_list_2" + BINDING_CLASS_LIST_SUFFIX;
        TransformInvocation invocation =
                new TransformInvocationBuilder(context)
                        .addReferencedInputs(
                                ImmutableList.of(
                                        asDirectoryInput(
                                                createFolder(
                                                        "blah",
                                                        ImmutableList.of(classList1, classList2)))))
                        .setIncrementalMode(false)
                        .build();
        createAndInvoke(invocation);
        assertThat(collectOutputs(), is(ImmutableSet.of(classList1, classList2)));
    }

    @Test
    public void incrementalFolderWithChanges()
            throws TransformException, InterruptedException, IOException {
        String classList1 = "class_list_1" + BINDING_CLASS_LIST_SUFFIX;
        String classList2 = "class_list_2" + BINDING_CLASS_LIST_SUFFIX;
        String classList3 = "class_list_3" + BINDING_CLASS_LIST_SUFFIX;
        String classList4 = "class_list_4" + BINDING_CLASS_LIST_SUFFIX;
        String classList5 = "class_list_5" + BINDING_CLASS_LIST_SUFFIX;

        File classListInputDir = createFolder("blah", ImmutableList.of(classList1));
        createAndInvoke(
                new TransformInvocationBuilder(context)
                        .addReferencedInputs(ImmutableList.of(asDirectoryInput(classListInputDir)))
                        .setIncrementalMode(false)
                        .build());
        // now run another invocation w/ a new file in that folder
        File unwanted =
                createInFolder(INCREMENTAL_BIN_AAR_DIR, classListInputDir, "unnecessary.tada");
        File wanted =
                createInFolder(INCREMENTAL_BINDING_CLASSES_LIST_DIR, classListInputDir, classList2);
        createAndInvoke(
                new TransformInvocationBuilder(context)
                        .addReferencedInputs(
                                ImmutableList.of(
                                        asDirectoryInput(
                                                classListInputDir,
                                                ImmutableMap.of(
                                                        unwanted,
                                                        Status.ADDED,
                                                        wanted,
                                                        Status.ADDED))))
                        .setIncrementalMode(true)
                        .build());

        assertThat(collectOutputs(), is(ImmutableSet.of(classList1, classList2)));

        // change file contents. literally
        File classList1Updated =
                getFileInDataBindingFolder(
                        INCREMENTAL_BINDING_CLASSES_LIST_DIR, classListInputDir, classList1);
        FileUtils.writeStringToFile(classList1Updated, "updated-class-list");
        createAndInvoke(
                new TransformInvocationBuilder(context)
                        .addReferencedInputs(
                                ImmutableList.of(
                                        asDirectoryInput(
                                                classListInputDir,
                                                ImmutableMap.of(
                                                        classList1Updated, Status.CHANGED))))
                        .setIncrementalMode(true)
                        .build());

        assertThat(collectOutputs(), is(ImmutableSet.of(classList1, classList2)));
        File classListOutFile = findOutputFile(classList1Updated.getName());
        assertThat(FileUtils.readFileToString(classListOutFile), is("updated-class-list"));

        // now delete one file
        createAndInvoke(
                new TransformInvocationBuilder(context)
                        .addReferencedInputs(
                                ImmutableList.of(
                                        asDirectoryInput(
                                                classListInputDir,
                                                ImmutableMap.of(
                                                        classList1Updated, Status.REMOVED))))
                        .setIncrementalMode(true)
                        .build());
        assertThat(collectOutputs(), is(ImmutableSet.of(classList2)));

        // introduce another folder
        File classListInputDir2 = createFolder("foobar", ImmutableList.of("d.qq"));
        File classListInput2 =
                createInFolder(
                        INCREMENTAL_BINDING_CLASSES_LIST_DIR, classListInputDir2, classList3);
        createAndInvoke(
                new TransformInvocationBuilder(context)
                        .addReferencedInputs(
                                ImmutableList.of(
                                        asDirectoryInput(
                                                classListInput2,
                                                ImmutableMap.of(classListInput2, Status.ADDED))))
                        .setIncrementalMode(true)
                        .build());
        assertThat(collectOutputs(), is(ImmutableSet.of(classList2, classList3)));

        // more new input in both
        File newInInput3 =
                createInFolder(INCREMENTAL_BINDING_CLASSES_LIST_DIR, classListInputDir, classList4);
        File newInInput4 =
                createInFolder(
                        INCREMENTAL_BINDING_CLASSES_LIST_DIR, classListInputDir2, classList5);
        createAndInvoke(
                new TransformInvocationBuilder(context)
                        .addReferencedInputs(
                                ImmutableList.of(
                                        asDirectoryInput(
                                                classListInputDir,
                                                ImmutableMap.of(newInInput3, Status.ADDED)),
                                        asDirectoryInput(
                                                classListInput2,
                                                ImmutableMap.of(newInInput4, Status.ADDED))))
                        .setIncrementalMode(true)
                        .build());
        assertThat(
                collectOutputs(),
                is(ImmutableSet.of(classList2, classList3, classList4, classList5)));
    }

    private Set<String> collectOutputs() {
        return FileUtils.listFiles(expectedOutFolder, TrueFileFilter.TRUE, TrueFileFilter.TRUE)
                .stream()
                .map(File::getName)
                .collect(Collectors.toSet());
    }

    private File findOutputFile(String name) {
        return FileUtils.listFiles(expectedOutFolder, TrueFileFilter.TRUE, TrueFileFilter.TRUE)
                .stream()
                .filter((f) -> f.getName().equals(name))
                .limit(1)
                .findFirst()
                .get();
    }

    private void createAndInvoke(TransformInvocation invocation)
            throws TransformException, InterruptedException, IOException {
        new DataBindingMergeGenClassLogTransform(logger, expectedOutFolder).transform(invocation);
    }

    private File createFolder(String fileName, List<String> files) throws IOException {
        File folder = temporaryFolder.newFolder("classlist-" + fileName);
        for (String file : files) {
            FileUtils.touch(
                    getFileInDataBindingFolder(INCREMENTAL_BINDING_CLASSES_LIST_DIR, folder, file));
        }
        return folder;
    }

    private File createInFolder(String folderName, File inputDir, String filename)
            throws IOException {
        File file = getFileInDataBindingFolder(folderName, inputDir, filename);
        FileUtils.touch(file);
        return file;
    }

    @NonNull
    private static File getFileInDataBindingFolder(
            String directory, File inputDir, String filename) {
        File dataBindingFolder = new File(inputDir, directory);
        return new File(dataBindingFolder, filename);
    }

    private static TransformInput asDirectoryInput(@NonNull File folder) {
        Collection<File> files = FileUtils.listFiles(folder, TrueFileFilter.TRUE, null);
        Map<File, Status> changes = new HashMap<>();
        for (File file : files) {
            changes.put(file, Status.ADDED);
        }
        return asDirectoryInput(folder, changes);
    }

    private static TransformInput asDirectoryInput(
            @NonNull File folder, @Nullable Map<File, Status> changes) {
        return TransformTestHelper.directoryBuilder(folder)
                .putChangedFiles(changes == null ? Maps.newHashMap() : changes)
                .build();
    }
}

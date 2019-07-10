/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.pipeline;

import static com.android.utils.FileUtils.mkdirs;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.DefaultContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.SecondaryInput;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.testutils.truth.FileSubject;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

@SuppressWarnings("deprecation")
public class TransformTaskTest extends TaskTestUtils {

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    private FileCollection testFileFC;
    private FileCollection testFolderFC;

    @Before
    public void setup() throws IOException {
        testFileFC =
                project.files(temporaryFolder.newFile("generic_input_file"))
                        .builtBy("my dependency");
        testFolderFC =
                project.files(temporaryFolder.newFolder("generic_intput_folder"))
                        .builtBy("my dependency");
    }

    @Test
    public void nonIncWithJarInputInOriginalStream()
            throws IOException, TransformException, InterruptedException {
        // create a stream and add it to the pipeline
        OriginalStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentType(QualifiedContent.DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(testFileFC)
                        .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(QualifiedContent.DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with a non-incremental build.
        transformTask.transform(inputBuilder().build());

        // check that was passed to the transform.
        assertThat(t.isIncrementalInputs()).isFalse();
        assertThat(t.getReferencedInputs()).isEmpty();

        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).hasSize(1);
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).isEmpty();

        JarInput singleJarInput = Iterables.getOnlyElement(jarInputs);
        assertThat(singleJarInput.getFile())
                .isEqualTo(Iterables.getOnlyElement(projectClass.getFileCollection()));
        assertThat(singleJarInput.getContentTypes()).containsExactlyElementsIn(
                projectClass.getContentTypes());
        assertThat(singleJarInput.getScopes()).containsExactlyElementsIn(projectClass.getScopes());
        assertThat(singleJarInput.getStatus()).isSameAs(Status.NOTCHANGED);
    }

    @Test
    public void nonIncWithJarInputInIntermediateStream()
            throws IOException, TransformException, InterruptedException {
        // create a stream and add it to the pipeline
        File rootFolder = temporaryFolder.newFolder();

        IntermediateStream projectClass =
                IntermediateStream.builder(project, "", "my dependency")
                        .addContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .addScopes(Scope.PROJECT)
                        .setRootLocation(rootFolder)
                        .build();
        transformManager.addStream(projectClass);

        // use the output version of this stream to create some content.
        TransformOutputProvider output = projectClass.asOutput();
        File jarFile = output.getContentLocation("foo", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.JAR);
        mkdirs(jarFile.getParentFile());
        Files.write("foo", jarFile, Charsets.UTF_8);

        // we need to simulate a save from a previous transform to ensure the state of the stream
        // is correct
        projectClass.save();

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(QualifiedContent.DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with a non-incremental build.
        transformTask.transform(inputBuilder().build());

        // check that was passed to the transform.
        assertThat(t.isIncremental()).isFalse();
        assertThat(t.getReferencedInputs()).isEmpty();

        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).hasSize(1);
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).isEmpty();

        JarInput singleJarInput = Iterables.getOnlyElement(jarInputs);
        assertThat(singleJarInput.getFile()).isEqualTo(jarFile);
        assertThat(singleJarInput.getContentTypes()).containsExactlyElementsIn(
                projectClass.getContentTypes());
        assertThat(singleJarInput.getScopes()).containsExactlyElementsIn(projectClass.getScopes());
        assertThat(singleJarInput.getStatus()).isSameAs(Status.NOTCHANGED);
    }

    @Test
    public void nonIncWithReferencedJarInputInOriginalStream()
            throws IOException, TransformException, InterruptedException {
        // create a stream and add it to the pipeline
        OriginalStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentType(QualifiedContent.DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(testFileFC)
                        .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(QualifiedContent.DefaultContentType.CLASSES)
                .setReferencedScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with a non-incremental build.
        transformTask.transform(inputBuilder().build());

        // check that was passed to the transform.
        assertThat(t.isIncremental()).isFalse();
        assertThat(t.getInputs()).isEmpty();

        Collection<TransformInput> inputs = t.getReferencedInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).hasSize(1);
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).isEmpty();

        JarInput singleJarInput = Iterables.getOnlyElement(jarInputs);
        assertThat(singleJarInput.getFile())
                .isEqualTo(Iterables.getOnlyElement(projectClass.getFileCollection()));
        assertThat(singleJarInput.getContentTypes()).containsExactlyElementsIn(
                projectClass.getContentTypes());
        assertThat(singleJarInput.getScopes()).containsExactlyElementsIn(projectClass.getScopes());
        assertThat(singleJarInput.getStatus()).isSameAs(Status.NOTCHANGED);
    }

    @Test
    public void nonIncWithReferencedJarInputInIntermediateStream()
            throws IOException, TransformException, InterruptedException {
        // create a stream and add it to the pipeline
        File rootFolder = temporaryFolder.newFolder();

        IntermediateStream projectClass =
                IntermediateStream.builder(project, "", "my dependency")
                        .addContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .addScopes(Scope.PROJECT)
                        .setRootLocation(rootFolder)
                        .build();
        transformManager.addStream(projectClass);

        // use the output version of this stream to create some content.
        TransformOutputProvider output = projectClass.asOutput();
        File jarFile = output.getContentLocation("foo", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.JAR);
        mkdirs(jarFile.getParentFile());
        Files.write("foo", jarFile, Charsets.UTF_8);

        // we need to simulate a save from a previous transform to ensure the state of the stream
        // is correct
        projectClass.save();

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(QualifiedContent.DefaultContentType.CLASSES)
                .setReferencedScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with a non-incremental build.
        transformTask.transform(inputBuilder().build());

        // check that was passed to the transform.
        assertThat(t.isIncremental()).isFalse();
        assertThat(t.getInputs()).isEmpty();

        Collection<TransformInput> inputs = t.getReferencedInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).hasSize(1);
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).isEmpty();

        JarInput singleJarInput = Iterables.getOnlyElement(jarInputs);
        assertThat(singleJarInput.getFile()).isEqualTo(jarFile);
        assertThat(singleJarInput.getContentTypes()).containsExactlyElementsIn(
                projectClass.getContentTypes());
        assertThat(singleJarInput.getScopes()).containsExactlyElementsIn(projectClass.getScopes());
        assertThat(singleJarInput.getStatus()).isSameAs(Status.NOTCHANGED);
    }

    @Test
    public void nonIncWithFolderInputInOriginalStream()
            throws IOException, TransformException, InterruptedException {
        // create a stream and add it to the pipeline
        OriginalStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(testFolderFC)
                        .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with a non-incremental build.
        transformTask.transform(inputBuilder().build());

        // check that was passed to the transform.
        assertThat(t.isIncremental()).isFalse();
        assertThat(t.getReferencedInputs()).isEmpty();

        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).isEmpty();
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).hasSize(1);

        DirectoryInput singleDirectoryInput = Iterables.getOnlyElement(directoryInputs);
        assertThat(singleDirectoryInput.getFile())
                .isEqualTo(Iterables.getOnlyElement(projectClass.getFileCollection()));
        assertThat(singleDirectoryInput.getContentTypes()).containsExactlyElementsIn(
                projectClass.getContentTypes());
        assertThat(singleDirectoryInput.getScopes()).containsExactlyElementsIn(projectClass.getScopes());
        assertThat(singleDirectoryInput.getChangedFiles()).isEmpty();
    }

    @Test
    public void nonIncWithFolderInputInIntermediateStream()
            throws IOException, TransformException, InterruptedException {
        // create a stream and add it to the pipeline
        File rootFolder = temporaryFolder.newFolder();

        IntermediateStream projectClass =
                IntermediateStream.builder(project, "", "my dependency")
                        .addContentTypes(DefaultContentType.CLASSES)
                        .addScopes(Scope.PROJECT)
                        .setRootLocation(rootFolder)
                        .build();
        transformManager.addStream(projectClass);

        // use the output version of this stream to create some content.
        TransformOutputProvider output = projectClass.asOutput();
        File outputFolder = output.getContentLocation("foo", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.DIRECTORY);
        mkdirs(outputFolder);

        // we need to simulate a save from a previous transform to ensure the state of the stream
        // is correct
        projectClass.save();

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with a non-incremental build.
        transformTask.transform(inputBuilder().build());

        // check that was passed to the transform.
        assertThat(t.isIncremental()).isFalse();
        assertThat(t.getReferencedInputs()).isEmpty();

        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).isEmpty();
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).hasSize(1);

        DirectoryInput singleDirectoryInput = Iterables.getOnlyElement(directoryInputs);
        assertThat(singleDirectoryInput.getFile()).isEqualTo(outputFolder);
        assertThat(singleDirectoryInput.getContentTypes()).containsExactlyElementsIn(
                projectClass.getContentTypes());
        assertThat(singleDirectoryInput.getScopes()).containsExactlyElementsIn(projectClass.getScopes());
        assertThat(singleDirectoryInput.getChangedFiles()).isEmpty();
    }

    @Test
    public void nonIncWithReferencedFolderInputInOriginalStream()
            throws IOException, TransformException, InterruptedException {
        // create a stream and add it to the pipeline
        OriginalStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(testFolderFC)
                        .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setReferencedScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with a non-incremental build.
        transformTask.transform(inputBuilder().build());

        // check that was passed to the transform.
        assertThat(t.isIncremental()).isFalse();
        assertThat(t.getInputs()).isEmpty();

        Collection<TransformInput> inputs = t.getReferencedInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).isEmpty();
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).hasSize(1);

        DirectoryInput singleDirectoryInput = Iterables.getOnlyElement(directoryInputs);
        assertThat(singleDirectoryInput.getFile())
                .isEqualTo(Iterables.getOnlyElement(projectClass.getFileCollection()));
        assertThat(singleDirectoryInput.getContentTypes()).containsExactlyElementsIn(
                projectClass.getContentTypes());
        assertThat(singleDirectoryInput.getScopes()).containsExactlyElementsIn(projectClass.getScopes());
        assertThat(singleDirectoryInput.getChangedFiles()).isEmpty();
    }

    @Test
    public void nonIncWithReferencedFolderInputInIntermediateStream()
            throws IOException, TransformException, InterruptedException {
        // create a stream and add it to the pipeline
        File rootFolder = temporaryFolder.newFolder();

        IntermediateStream projectClass =
                IntermediateStream.builder(project, "", "my dependency")
                        .addContentTypes(DefaultContentType.CLASSES)
                        .addScopes(Scope.PROJECT)
                        .setRootLocation(rootFolder)
                        .build();
        transformManager.addStream(projectClass);

        // use the output version of this stream to create some content.
        TransformOutputProvider output = projectClass.asOutput();
        File outputFolder = output.getContentLocation("foo", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.DIRECTORY);
        mkdirs(outputFolder);

        // we need to simulate a save from a previous transform to ensure the state of the stream
        // is correct
        projectClass.save();

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setReferencedScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with a non-incremental build.
        transformTask.transform(inputBuilder().build());

        // check that was passed to the transform.
        assertThat(t.isIncremental()).isFalse();
        assertThat(t.getInputs()).isEmpty();

        Collection<TransformInput> inputs = t.getReferencedInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).isEmpty();
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).hasSize(1);

        DirectoryInput singleDirectoryInput = Iterables.getOnlyElement(directoryInputs);
        assertThat(singleDirectoryInput.getFile()).isEqualTo(outputFolder);
        assertThat(singleDirectoryInput.getContentTypes()).containsExactlyElementsIn(
                projectClass.getContentTypes());
        assertThat(singleDirectoryInput.getScopes()).containsExactlyElementsIn(projectClass.getScopes());
        assertThat(singleDirectoryInput.getChangedFiles()).isEmpty();
    }

    @Test
    public void incTaskWithNonIncTransformWithJarInputInOriginalStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File jarFile = temporaryFolder.newFile("input file");
        OriginalStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(project.files(jarFile).builtBy("my dependency"))
                        .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(jarFile)
                .build());

        // check that was passed to the transform. Should be non-incremental since the
        // transform isn't.
        assertThat(t.isIncrementalInputs()).isFalse();

        // and the jar input should be status NOTCHANGED
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).hasSize(1);

        JarInput singleJarInput = Iterables.getOnlyElement(jarInputs);
        assertThat(singleJarInput.getFile()).isEqualTo(jarFile);
        assertThat(singleJarInput.getStatus()).isSameAs(Status.NOTCHANGED);
    }

    @Test
    public void incTaskWithNonIncTransformWithJarInputInIntermediateStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = temporaryFolder.newFolder();

        IntermediateStream projectClass =
                IntermediateStream.builder(project, "", "my dependency")
                        .addContentTypes(DefaultContentType.CLASSES)
                        .addScopes(Scope.PROJECT)
                        .setRootLocation(rootFolder)
                        .build();
        transformManager.addStream(projectClass);

        // use the output version of this stream to create some content.
        TransformOutputProvider output = projectClass.asOutput();
        File jarFile = output.getContentLocation("foo", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.JAR);
        mkdirs(jarFile.getParentFile());
        Files.write("foo", jarFile, Charsets.UTF_8);

        // we need to simulate a save from a previous transform to ensure the state of the stream
        // is correct
        projectClass.save();

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(jarFile)
                .build());

        // check that was passed to the transform. Should be non-incremental since the
        // transform isn't.
        assertThat(t.isIncrementalInputs()).isFalse();

        // and the jar input should be status NOTCHANGED
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).hasSize(1);

        JarInput singleJarInput = Iterables.getOnlyElement(jarInputs);
        assertThat(singleJarInput.getFile()).isEqualTo(jarFile);
        assertThat(singleJarInput.getStatus()).isSameAs(Status.NOTCHANGED);
    }

    @Test
    public void incTaskWithNonIncTransformWithFolderInputInOriginalStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = temporaryFolder.newFolder("root folder");
        OriginalStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(project.files(rootFolder).builtBy("my dependency"))
                        .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data

        File addedFile = new File(rootFolder, "added");
        FileUtils.createFile(addedFile, "addedContent");
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(addedFile)
                .build());

        // check that was passed to the transform. Should be non-incremental since the
        // transform isn't.
        assertThat(t.isIncrementalInputs()).isFalse();

        // and the jar input should be status NOTCHANGED
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).hasSize(1);

        DirectoryInput singleDirectoryInput = Iterables.getOnlyElement(directoryInputs);
        assertThat(singleDirectoryInput.getFile()).isEqualTo(rootFolder);
        assertThat(singleDirectoryInput.getChangedFiles()).isEmpty();
    }

    @Test
    public void incTaskWithNonIncTransformWithFolderInputInIntermediateStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = temporaryFolder.newFolder();

        IntermediateStream projectClass =
                IntermediateStream.builder(project, "", "my dependency")
                        .addContentTypes(DefaultContentType.CLASSES)
                        .addScopes(Scope.PROJECT)
                        .setRootLocation(rootFolder)
                        .build();
        transformManager.addStream(projectClass);

        // use the output version of this stream to create some content.
        TransformOutputProvider output = projectClass.asOutput();
        File outputFolder = output.getContentLocation("foo", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.DIRECTORY);
        mkdirs(outputFolder);

        // we need to simulate a save from a previous transform to ensure the state of the stream
        // is correct
        projectClass.save();

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data

        File addedFile = new File(rootFolder, "added");
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(addedFile)
                .build());

        // check that was passed to the transform. Should be non-incremental since the
        // transform isn't.
        assertThat(t.isIncrementalInputs()).isFalse();

        // and the jar input should be status NOTCHANGED
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).hasSize(1);

        DirectoryInput singleDirectoryInput = Iterables.getOnlyElement(directoryInputs);
        assertThat(singleDirectoryInput.getFile()).isEqualTo(outputFolder);
        assertThat(singleDirectoryInput.getChangedFiles()).isEmpty();
    }

    @Test
    public void incrementalJarInputInOriginalStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        // Don't create deleted files. This is handled in a separate test.
        final File addedFile = temporaryFolder.newFile("jar file1");
        final File changedFile = temporaryFolder.newFile("jar file2");
        final ImmutableMap<File, Status> jarMap = ImmutableMap.of(
                addedFile, Status.ADDED,
                changedFile, Status.CHANGED);
        OriginalStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(
                                project.files(
                                                (Callable<Collection<File>>)
                                                        () -> {
                                                            // this should not contain the removed jar files.
                                                            return ImmutableList.of(
                                                                    addedFile, changedFile);
                                                        })
                                        .builtBy("my dependency"))
                        .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(addedFile)
                .modifiedFile(changedFile)
                .build());

        // check that was passed to the transform.
        assertThat(t.isIncrementalInputs()).isTrue();

        // and the jar input should be status ADDED
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).hasSize(jarMap.size());

        for (JarInput jarInput : jarInputs) {
            File file = jarInput.getFile();
            assertThat(file).isIn(jarMap.keySet());
            assertThat(jarInput.getStatus()).isSameAs(jarMap.get(file));
        }
    }

    @Test
    public void incrementalJarInputInIntermediateStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = temporaryFolder.newFolder();

        IntermediateStream projectClass =
                IntermediateStream.builder(project, "", "my dependency")
                        .addContentTypes(DefaultContentType.CLASSES)
                        .addScopes(Scope.PROJECT)
                        .setRootLocation(rootFolder)
                        .build();
        transformManager.addStream(projectClass);

        // use the output version of this stream to create some content.
        TransformOutputProvider output = projectClass.asOutput();
        File addedJar = output.getContentLocation("added", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.JAR);
        mkdirs(addedJar.getParentFile());
        Files.write("foo", addedJar, Charsets.UTF_8);
        File changedJar = output.getContentLocation("changed", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.JAR);
        mkdirs(changedJar.getParentFile());
        Files.write("foo", changedJar, Charsets.UTF_8);
        // no need to create a deleted jar. It's handled by a separate test.
        final ImmutableMap<File, Status> jarMap = ImmutableMap.of(
                addedJar, Status.ADDED,
                changedJar, Status.CHANGED);

        // we need to simulate a save from a previous transform to ensure the state of the stream
        // is correct
        projectClass.save();

        // create the transform
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(addedJar)
                .modifiedFile(changedJar)
                .build());

        // check that was passed to the transform.
        assertThat(t.isIncrementalInputs()).isTrue();

        // and the jar input should be status ADDED
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).hasSize(jarMap.size());

        for (JarInput jarInput : jarInputs) {
            File file = jarInput.getFile();
            assertThat(file).isIn(jarMap.keySet());
            assertThat(jarInput.getStatus()).isSameAs(jarMap.get(file));
        }
    }

    @Test
    public void incrementalComplexTypeJarInputInIntermediateStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = temporaryFolder.newFolder();

        IntermediateStream projectClass =
                IntermediateStream.builder(project, "", "my dependency")
                        .addContentTypes(DefaultContentType.CLASSES)
                        .addContentTypes(ExtendedContentType.CLASSES_ENHANCED)
                        .addScopes(Scope.PROJECT)
                        .setRootLocation(rootFolder)
                        .build();
        transformManager.addStream(projectClass);

        // use the output version of this stream to create some content.
        TransformOutputProvider output = projectClass.asOutput();
        File addedJar = output.getContentLocation("added",
                ImmutableSet.of(DefaultContentType.CLASSES),
                projectClass.getScopes(), Format.JAR);
        mkdirs(addedJar.getParentFile());
        Files.write("foo", addedJar, Charsets.UTF_8);
        File changedJar = output.getContentLocation("changed",
                ImmutableSet.of(DefaultContentType.CLASSES),
                projectClass.getScopes(), Format.JAR);
        mkdirs(changedJar.getParentFile());
        Files.write("foo", changedJar, Charsets.UTF_8);

        // create the other input changes.
        // use the output version of this stream to create some content.
        File enhancedAddedJar =
                output.getContentLocation(
                        "enhancedAdded",
                        ImmutableSet.of(ExtendedContentType.CLASSES_ENHANCED),
                        projectClass.getScopes(),
                        Format.JAR);
        mkdirs(enhancedAddedJar.getParentFile());
        Files.write("foo", enhancedAddedJar, Charsets.UTF_8);
        File enhancedChangedJar =
                output.getContentLocation(
                        "enhancedChanged",
                        ImmutableSet.of(ExtendedContentType.CLASSES_ENHANCED),
                        projectClass.getScopes(),
                        Format.JAR);
        mkdirs(enhancedChangedJar.getParentFile());
        Files.write("foo", enhancedChangedJar, Charsets.UTF_8);
        File enhancedRemovedJar =
                output.getContentLocation(
                        "enhancedRemoved",
                        ImmutableSet.of(ExtendedContentType.CLASSES_ENHANCED),
                        projectClass.getScopes(),
                        Format.JAR);

        // no need to create a deleted jar. It's handled by a separate test.
        final ImmutableMap<File, Status> jarMap = ImmutableMap.of(
                addedJar, Status.ADDED,
                changedJar, Status.CHANGED);

        // we need to simulate a save from a previous transform to ensure the state of the stream
        // is correct
        projectClass.save();

        // create the transforms
        TestTransform classesTransform = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transforms to the manager
        TaskProvider<TransformTask> classesTask =
                transformManager
                        .addTransform(taskFactory, scope, classesTransform)
                        .orElseThrow(mTransformTaskFailed);

        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(classesTask.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(2);

        // call the task with incremental data
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(addedJar)
                .addedFile(enhancedAddedJar)
                .modifiedFile(changedJar)
                .modifiedFile(enhancedChangedJar)
                .removedFile(enhancedRemovedJar)
                .build());

        // check that was passed to the transform.
        assertThat(classesTransform.isIncrementalInputs()).isTrue();

        // and the jar input should be status ADDED
        Collection<TransformInput> inputs = classesTransform.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).hasSize(jarMap.size());

        for (JarInput jarInput : jarInputs) {
            File file = jarInput.getFile();
            assertThat(file).isIn(jarMap.keySet());
            assertThat(jarInput.getStatus()).isSameAs(jarMap.get(file));
        }
    }

    @Test
    public void incrementalComplexScopeJarInputInIntermediateStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = temporaryFolder.newFolder();

        IntermediateStream projectClass =
                IntermediateStream.builder(project, "", "my dependency")
                        .addContentTypes(DefaultContentType.CLASSES)
                        .addScopes(Scope.PROJECT, Scope.SUB_PROJECTS)
                        .setRootLocation(rootFolder)
                        .build();
        transformManager.addStream(projectClass);

        // use the output version of this stream to create some content.
        TransformOutputProvider output = projectClass.asOutput();
        File addedJar = output.getContentLocation("added",
                ImmutableSet.of(DefaultContentType.CLASSES),
                ImmutableSet.of(Scope.PROJECT), Format.JAR);
        mkdirs(addedJar.getParentFile());
        Files.write("foo", addedJar, Charsets.UTF_8);
        File changedJar = output.getContentLocation("changed",
                ImmutableSet.of(DefaultContentType.CLASSES),
                ImmutableSet.of(Scope.PROJECT), Format.JAR);
        mkdirs(changedJar.getParentFile());
        Files.write("foo", changedJar, Charsets.UTF_8);

        // create the other input changes.
        // use the output version of this stream to create some content.
        File enhancedAddedJar =
                output.getContentLocation(
                        "enhancedAdded",
                        ImmutableSet.of(DefaultContentType.CLASSES),
                        ImmutableSet.of(Scope.SUB_PROJECTS),
                        Format.JAR);
        mkdirs(addedJar.getParentFile());
        Files.write("foo", addedJar, Charsets.UTF_8);
        File enhancedChangedJar =
                output.getContentLocation(
                        "enhancedChanged",
                        ImmutableSet.of(DefaultContentType.CLASSES),
                        ImmutableSet.of(Scope.SUB_PROJECTS),
                        Format.JAR);
        mkdirs(changedJar.getParentFile());
        Files.write("foo", changedJar, Charsets.UTF_8);
        File enhancedRemovedJar =
                output.getContentLocation(
                        "enhancedRemoved",
                        ImmutableSet.of(DefaultContentType.CLASSES),
                        ImmutableSet.of(Scope.SUB_PROJECTS),
                        Format.JAR);

        // no need to create a deleted jar. It's handled by a separate test.
        final ImmutableMap<File, Status> jarMap = ImmutableMap.of(
                addedJar, Status.ADDED,
                changedJar, Status.CHANGED);

        // we need to simulate a save from a previous transform to ensure the state of the stream
        // is correct
        projectClass.save();

        // create the transforms
        TestTransform classesTransform = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transforms to the manager
        TaskProvider<TransformTask> classesTask =
                transformManager
                        .addTransform(taskFactory, scope, classesTransform)
                        .orElseThrow(mTransformTaskFailed);

        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(classesTask.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(2);

        // call the task with incremental data
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(addedJar)
                .addedFile(enhancedAddedJar)
                .modifiedFile(changedJar)
                .modifiedFile(enhancedChangedJar)
                .removedFile(enhancedRemovedJar)
                .build());

        // check that was passed to the transform.
        assertThat(classesTransform.isIncrementalInputs()).isTrue();

        // and the jar input should be status ADDED
        Collection<TransformInput> inputs = classesTransform.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).hasSize(jarMap.size());

        for (JarInput jarInput : jarInputs) {
            File file = jarInput.getFile();
            assertThat(file).isIn(jarMap.keySet());
            assertThat(jarInput.getStatus()).isSameAs(jarMap.get(file));
        }
    }

    @Test
    public void incrementalFolderInputInOriginalStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = temporaryFolder.newFolder();
        OriginalStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(project.files(rootFolder).builtBy("my dependency"))
                        .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        File addedFile = new File(rootFolder, "added");
        File modifiedFile = new File(rootFolder, "modified");
        File removedFile = new File(rootFolder, "removed");
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(addedFile)
                .modifiedFile(modifiedFile)
                .removedFile(removedFile)
                .build());

        // check that was passed to the transform.
        assertThat(t.isIncrementalInputs()).isTrue();

        // don't test everything, the rest is tested in the tests above.
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).hasSize(1);

        DirectoryInput singleDirectoryInput = Iterables.getOnlyElement(directoryInputs);
        assertThat(singleDirectoryInput.getFile()).isEqualTo(rootFolder);

        Map<File, Status> changedFiles = singleDirectoryInput.getChangedFiles();
        assertThat(changedFiles).hasSize(3);
        assertThat(changedFiles).containsEntry(addedFile, Status.ADDED);
        assertThat(changedFiles).containsEntry(modifiedFile, Status.CHANGED);
        assertThat(changedFiles).containsEntry(removedFile, Status.REMOVED);
    }

    @Test
    public void incrementalFolderInputInIntermediateStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = temporaryFolder.newFolder();

        IntermediateStream projectClass =
                IntermediateStream.builder(project, "", "my dependency")
                        .addContentTypes(DefaultContentType.CLASSES)
                        .addScopes(Scope.PROJECT)
                        .setRootLocation(rootFolder)
                        .build();
        transformManager.addStream(projectClass);

        // use the output version of this stream to create some content.
        TransformOutputProvider output = projectClass.asOutput();
        File outputFolder = output.getContentLocation("foo", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.DIRECTORY);
        mkdirs(outputFolder);

        // we need to simulate a save from a previous transform to ensure the state of the stream
        // is correct
        projectClass.save();

        // create the transform
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        File addedFile = new File(outputFolder, "added");
        File modifiedFile = new File(outputFolder, "modified");
        File removedFile = new File(outputFolder, "removed");
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(addedFile)
                .modifiedFile(modifiedFile)
                .removedFile(removedFile)
                .build());

        // check that was passed to the transform.
        assertThat(t.isIncrementalInputs()).isTrue();

        // don't test everything, the rest is tested in the tests above.
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).hasSize(1);

        DirectoryInput singleDirectoryInput = Iterables.getOnlyElement(directoryInputs);
        assertThat(singleDirectoryInput.getFile()).isEqualTo(outputFolder);

        Map<File, Status> changedFiles = singleDirectoryInput.getChangedFiles();
        assertThat(changedFiles).hasSize(3);
        assertThat(changedFiles).containsEntry(addedFile, Status.ADDED);
        assertThat(changedFiles).containsEntry(modifiedFile, Status.CHANGED);
        assertThat(changedFiles).containsEntry(removedFile, Status.REMOVED);
    }

    @Test
    public void incrementalComplexTypesFolderInputInIntermediateStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = temporaryFolder.newFolder();

        // this represents the output of the "previous" transform.
        IntermediateStream projectClass =
                IntermediateStream.builder(project, "", "my dependency")
                        .addContentTypes(
                                DefaultContentType.CLASSES, ExtendedContentType.CLASSES_ENHANCED)
                        .addScopes(Scope.PROJECT)
                        .setRootLocation(rootFolder)
                        .build();
        transformManager.addStream(projectClass);

        // use the output version of this stream to create some content.
        // however, we split the output into 2 streams, one for each content type.
        TransformOutputProvider output = projectClass.asOutput();
        File classesOutput = output.getContentLocation("classes",
                ImmutableSet.of(DefaultContentType.CLASSES),
                projectClass.getScopes(), Format.DIRECTORY);
        mkdirs(classesOutput);

        // now create the other output folder.
        File enhancedClassesOutput = output.getContentLocation("enhanced",
                ImmutableSet.of(ExtendedContentType.CLASSES_ENHANCED),
                projectClass.getScopes(), Format.DIRECTORY);
        mkdirs(enhancedClassesOutput);

        // we need to simulate a save from a previous transform to ensure the state of the stream
        // is correct
        projectClass.save();

        // create the transform
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(2);

        // call the task with incremental data
        File addedFile = new File(classesOutput, "added");
        File modifiedFile = new File(classesOutput, "modified");
        File removedFile = new File(classesOutput, "removed");

        // now add some changes in the second output folder, it should not be part of the
        // incremental changes for this transform since it is not interested in that content type.
        File enhancedRemoved = new File(enhancedClassesOutput, "removed");
        File enhancedAdded = new File(enhancedClassesOutput, "added");
        File enhancedModified = new File(enhancedClassesOutput, "modified");

        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(addedFile)
                .addedFile(enhancedAdded)
                .modifiedFile(modifiedFile)
                .modifiedFile(enhancedModified)
                .removedFile(removedFile)
                .removedFile(enhancedRemoved)
                .build());

        // check that was passed to the transform.
        assertThat(t.isIncrementalInputs()).isTrue();

        // don't test everything, the rest is tested in the tests above.
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).hasSize(1);

        DirectoryInput singleDirectoryInput = Iterables.getOnlyElement(directoryInputs);
        assertThat(singleDirectoryInput.getFile()).isEqualTo(classesOutput);

        // none of the entries specified in the "enhanced" folder should be passed as events.
        Map<File, Status> changedFiles = singleDirectoryInput.getChangedFiles();
        assertThat(changedFiles).hasSize(3);
        assertThat(changedFiles).containsEntry(addedFile, Status.ADDED);
        assertThat(changedFiles).containsEntry(modifiedFile, Status.CHANGED);
        assertThat(changedFiles).containsEntry(removedFile, Status.REMOVED);
    }

    @Test
    public void incrementalComplexScopeFolderInputInIntermediateStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = temporaryFolder.newFolder();

        // this represents the output of the "previous" transform.
        IntermediateStream projectClass =
                IntermediateStream.builder(project, "", "my dependency")
                        .addContentTypes(DefaultContentType.CLASSES)
                        .addScopes(Scope.PROJECT)
                        .addScopes(Scope.SUB_PROJECTS)
                        .setRootLocation(rootFolder)
                        .build();
        transformManager.addStream(projectClass);

        // use the output version of this stream to create some content.
        // however, we split the output into 2 streams, one for each content type.
        TransformOutputProvider output = projectClass.asOutput();
        File classesOutput = output.getContentLocation("classes",
                ImmutableSet.of(DefaultContentType.CLASSES),
                ImmutableSet.of(Scope.PROJECT),
                Format.DIRECTORY);
        mkdirs(classesOutput);

        // now create the other output folder.
        File subProjectOutput = output.getContentLocation("enhanced",
                ImmutableSet.of(DefaultContentType.CLASSES),
                ImmutableSet.of(Scope.SUB_PROJECTS),
                Format.DIRECTORY);

        // we need to simulate a save from a previous transform to ensure the state of the stream
        // is correct
        projectClass.save();

        // create the transform
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(2);

        // call the task with incremental data
        File addedFile = new File(classesOutput, "added");
        File modifiedFile = new File(classesOutput, "modified");
        File removedFile = new File(classesOutput, "removed");

        // now add some changes in the second output folder, it should not be part of the
        // incremental changes for this transform since it is not interested in that content type.
        File subProjectRemoved = new File(subProjectOutput, "removed");

        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(addedFile)
                .modifiedFile(modifiedFile)
                .removedFile(removedFile)
                .removedFile(subProjectRemoved)
                .build());

        // check that was passed to the transform.
        assertThat(t.isIncrementalInputs()).isTrue();

        // don't test everything, the rest is tested in the tests above.
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).hasSize(1);

        DirectoryInput singleDirectoryInput = Iterables.getOnlyElement(directoryInputs);
        assertThat(singleDirectoryInput.getFile()).isEqualTo(classesOutput);

        // none of the entries specified in the "subProject" folder should be passed as events.
        Map<File, Status> changedFiles = singleDirectoryInput.getChangedFiles();
        assertThat(changedFiles).hasSize(3);
        assertThat(changedFiles).containsEntry(addedFile, Status.ADDED);
        assertThat(changedFiles).containsEntry(modifiedFile, Status.CHANGED);
        assertThat(changedFiles).containsEntry(removedFile, Status.REMOVED);
    }

    @Test
    public void deletedJarInputInOriginalStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File jarFile = temporaryFolder.newFile("jar file");
        OriginalStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(project.files(jarFile).builtBy("my dependency"))
                        .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // the deleted jar is unrelated to the stream definition above. It might have existed in
        // a previous version of the stream passed to the transform but it cannot be part of the
        // current stream definition since it's being deleted.
        File deletedJar = new File("deleted jar");
        transformTask.transform(inputBuilder()
                .incremental()
                .removedFile(deletedJar)
                .build());

        // in this case we cannot know what types/scopes the missing jar is associated with
        // so we expect non-incremental mode.
        assertThat(t.isIncrementalInputs()).isFalse();

        // don't test everything, the rest is tested in the tests above.
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).hasSize(1);
        JarInput jarInput = Iterables.getOnlyElement(jarInputs);
        assertThat(jarInput.getFile()).isEqualTo(jarFile);
        assertThat(jarInput.getStatus()).isSameAs(Status.NOTCHANGED);
    }

    @Test
    public void deletedJarInputInIntermediateStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = temporaryFolder.newFolder();

        IntermediateStream projectClass =
                IntermediateStream.builder(project, "", "my dependency")
                        .addContentTypes(DefaultContentType.CLASSES)
                        .addScopes(Scope.PROJECT)
                        .setRootLocation(rootFolder)
                        .build();
        transformManager.addStream(projectClass);

        // use the output version of this stream to create some content.
        TransformOutputProvider output = projectClass.asOutput();
        File jarFile = output.getContentLocation("foo", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.JAR);
        mkdirs(jarFile.getParentFile());
        // have to write content to create the file.
        Files.write("foo", jarFile, Charsets.UTF_8);
        // for this one just get the location. It won't be created, but we know the location
        // is correct.
        File deletedJarFile = output.getContentLocation("deleted", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.JAR);

        // we need to simulate a save from a previous transform to ensure the state of the stream
        // is correct
        projectClass.save();

        // create the transform
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        transformTask.transform(inputBuilder()
                .incremental()
                .removedFile(deletedJarFile)
                .build());

        // check that was passed to the transform.
        assertThat(t.isIncrementalInputs()).isTrue();

        // don't test everything, the rest is tested in the tests above.
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).hasSize(2);

        List<File> jarLocations = ImmutableList.of(jarFile, deletedJarFile);
        for (JarInput jarInput : jarInputs) {
            File file = jarInput.getFile();
            assertThat(file).isIn(jarLocations);

            if (file.equals(jarFile)) {
                assertThat(jarInput.getStatus()).isSameAs(Status.NOTCHANGED);
            } else {
                assertThat(jarInput.getStatus()).isSameAs(Status.REMOVED);
            }
        }
    }

    @Test
    public void deletedFolderInputInOriginalStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = temporaryFolder.newFolder();
        OriginalStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(project.files(rootFolder).builtBy("my dependency"))
                        .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        File deletedFolder = new File("deleted");
        File removedFile = new File(deletedFolder, "removed");
        transformTask.transform(inputBuilder()
                .incremental()
                .removedFile(removedFile)
                .build());

        // in this case we cannot know what types/scopes the missing file/folder is associated with
        // so we expect non-incremental mode.
        assertThat(t.isIncrementalInputs()).isFalse();

        // don't test everything, the rest is tested in the tests above.
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).hasSize(1);
        DirectoryInput directoryInput = Iterables.getOnlyElement(directoryInputs);
        assertThat(directoryInput.getFile()).isEqualTo(rootFolder);
        assertThat(directoryInput.getChangedFiles()).isEmpty();
    }

    @Test
    public void deletedFolderInputInIntermediateStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = temporaryFolder.newFolder();
        IntermediateStream projectClass =
                IntermediateStream.builder(project, "", "my dependency")
                        .addContentTypes(DefaultContentType.CLASSES)
                        .addScopes(Scope.PROJECT)
                        .setRootLocation(rootFolder)
                        .build();
        transformManager.addStream(projectClass);

        // use the output version of this stream to create some content.
        TransformOutputProvider output = projectClass.asOutput();
        File outputFolder = output.getContentLocation("foo", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.DIRECTORY);
        mkdirs(outputFolder);
        // for this one just get the location. It won't be created, but we know the location
        // is correct.
        File deletedOutputFolder = output.getContentLocation("foo2", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.DIRECTORY);

        // we need to simulate a save from a previous transform to ensure the state of the stream
        // is correct
        projectClass.save();

        // create the transform
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        File removedFile = new File(deletedOutputFolder, "removed");
        File removedFile2 = new File(deletedOutputFolder, "removed2");
        transformTask.transform(inputBuilder()
                .incremental()
                .removedFile(removedFile)
                .removedFile(removedFile2)
                .build());

        // check that was passed to the transform.
        assertThat(t.isIncrementalInputs()).isTrue();

        // don't test everything, the rest is tested in the tests above.
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).hasSize(2);

        List<File> folderLocations = ImmutableList.of(outputFolder, deletedOutputFolder);
        for (DirectoryInput directoryInput : directoryInputs) {
            File file = directoryInput.getFile();
            assertThat(file).isIn(folderLocations);
            Map<File, Status> changedFiles = directoryInput.getChangedFiles();

            if (file.equals(outputFolder)) {
                assertThat(changedFiles).isEmpty();
            } else {
                assertThat(changedFiles).hasSize(2);
                assertThat(changedFiles).containsEntry(removedFile, Status.REMOVED);
                assertThat(changedFiles).containsEntry(removedFile2, Status.REMOVED);
            }
        }
    }

    @Test
    public void incrementalTestComplexOriginalStreamOnly()
            throws TransformException, InterruptedException, IOException {
        // test with multiple scopes, both with multiple streams, and consumed and referenced scopes.

        File scope1Jar = temporaryFolder.newFile("jar file1");
        File scope3Jar = temporaryFolder.newFile("jar file2");
        File scope1RootFolder = temporaryFolder.newFolder("folder file1");
        File scope3RootFolder = temporaryFolder.newFolder("folder file2");

        OriginalStream scope1 =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(
                                project.files(scope1Jar, scope1RootFolder).builtBy("my dependency"))
                        .build();

        File scope2Root = temporaryFolder.newFolder();
        IntermediateStream scope2 =
                IntermediateStream.builder(project, "", "my dependency")
                        .addContentTypes(DefaultContentType.CLASSES)
                        .addScopes(Scope.PROJECT_LOCAL_DEPS)
                        .setRootLocation(scope2Root)
                        .build();

        // use the output version of this stream to create some content.
        // only these jars could be detected as deleted.
        TransformOutputProvider output2 = scope2.asOutput();
        File scope2RootFolder = output2.getContentLocation("foo", scope2.getContentTypes(),
                scope2.getScopes(), Format.DIRECTORY);
        mkdirs(scope2RootFolder);
        // for this one just get the location. It won't be created, but we know the location
        // is correct.
        File scope2Jar = output2.getContentLocation("foo2", scope2.getContentTypes(),
                scope2.getScopes(), Format.JAR);

        // we need to simulate a save from a previous transform to ensure the state of the stream
        // is correct
        scope2.save();

        OriginalStream scope3 =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.CLASSES)
                        .addScope(Scope.SUB_PROJECTS)
                        .setFileCollection(
                                project.files(scope3Jar, scope3RootFolder).builtBy("my dependency"))
                        .build();

        File scope4Root = temporaryFolder.newFolder();
        IntermediateStream scope4 =
                IntermediateStream.builder(project, "", "my dependency")
                        .addContentTypes(DefaultContentType.CLASSES)
                        .addScopes(Scope.EXTERNAL_LIBRARIES)
                        .setRootLocation(scope4Root)
                        .build();

        // use the output version of this stream to create some content.
        // only these jars could be detected as deleted.
        TransformOutputProvider output4 = scope4.asOutput();
        File scope4RootFolder = output4.getContentLocation("foo", scope4.getContentTypes(),
                scope4.getScopes(), Format.DIRECTORY);
        mkdirs(scope4RootFolder);
        // for this one just get the location. It won't be created, but we know the location
        // is correct.
        File scope4Jar = output4.getContentLocation("foo2", scope4.getContentTypes(),
                scope4.getScopes(), Format.JAR);

        // we need to simulate a save from a previous transform to ensure the state of the stream
        // is correct
        scope4.save();

        final ImmutableMap<File, Status> inputJarMap1 = ImmutableMap.of(
                scope1Jar, Status.ADDED,
                scope2Jar, Status.REMOVED);

        final ImmutableMap<File, Status> inputJarMap2 = ImmutableMap.of(
                scope3Jar, Status.ADDED,
                scope4Jar, Status.REMOVED);

        transformManager.addStream(scope1);
        transformManager.addStream(scope2);
        transformManager.addStream(scope3);
        transformManager.addStream(scope4);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT, Scope.PROJECT_LOCAL_DEPS)
                .setReferencedScopes(Scope.EXTERNAL_LIBRARIES, Scope.SUB_PROJECTS)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(3); // the new output and the 2 referenced ones.

        // call the task with incremental data
        File addedFile1 = new File(scope1RootFolder, "added");
        File removedFile2 = new File(scope2RootFolder, "removed");
        File addedFile3 = new File(scope3RootFolder, "added");
        File removedFile4 = new File(scope4RootFolder, "removed");
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(scope1Jar)
                .removedFile(scope2Jar)
                .addedFile(scope3Jar)
                .removedFile(scope4Jar)
                .addedFile(addedFile1)
                .addedFile(addedFile3)
                .removedFile(removedFile2)
                .removedFile(removedFile4)
                .build());

        // check that was passed to the transform.
        assertThat(t.isIncrementalInputs()).isTrue();

        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(2);
        // we don't care much about the separation of the 3 inputs, so we'll mix the jar
        // and folder inputs in single lists.
        List<JarInput> jarInputs = Lists.newArrayListWithCapacity(2);
        List<DirectoryInput> directoryInputs = Lists.newArrayListWithCapacity(2);
        for (TransformInput input : inputs) {
            jarInputs.addAll(input.getJarInputs());
            directoryInputs.addAll(input.getDirectoryInputs());
        }

        assertThat(jarInputs).hasSize(2);

        for (JarInput jarInput : jarInputs) {
            File file = jarInput.getFile();
            assertThat(file).isIn(inputJarMap1.keySet());
            assertThat(jarInput.getStatus()).isSameAs(inputJarMap1.get(file));
        }

        assertThat(directoryInputs).hasSize(2);

        for (DirectoryInput directoryInput : directoryInputs) {
            Map<File, Status> changedFiles = directoryInput.getChangedFiles();
            assertThat(changedFiles).hasSize(1);

            File file = directoryInput.getFile();
            assertThat(file).isAnyOf(scope1RootFolder, scope2RootFolder);

            if (file.equals(scope1RootFolder)) {
                assertThat(changedFiles).containsEntry(addedFile1, Status.ADDED);
            } else if (file.equals(scope2RootFolder)) {
                assertThat(changedFiles).containsEntry(removedFile2, Status.REMOVED);
            }
        }

        // now check on the referenced inputs.
        Collection<TransformInput> referencedInputs = t.getReferencedInputs();
        assertThat(referencedInputs).hasSize(2);
        // we don't care much about the separation of the 3 inputs, so we'll mix the jar
        // and folder inputs in single lists.
        jarInputs = Lists.newArrayListWithCapacity(2);
        directoryInputs = Lists.newArrayListWithCapacity(2);
        for (TransformInput input : referencedInputs) {
            jarInputs.addAll(input.getJarInputs());
            directoryInputs.addAll(input.getDirectoryInputs());
        }

        assertThat(jarInputs).hasSize(2);

        for (JarInput jarInput : jarInputs) {
            File file = jarInput.getFile();
            assertThat(file).isIn(inputJarMap2.keySet());
            assertThat(jarInput.getStatus()).isSameAs(inputJarMap2.get(file));
        }

        assertThat(directoryInputs).hasSize(2);

        for (DirectoryInput directoryInput : directoryInputs) {
            Map<File, Status> changedFiles = directoryInput.getChangedFiles();
            assertThat(changedFiles).hasSize(1);

            File file = directoryInput.getFile();
            assertThat(file).isAnyOf(scope3RootFolder, scope4RootFolder);

            if (file.equals(scope3RootFolder)) {
                assertThat(changedFiles).containsEntry(addedFile3, Status.ADDED);
            } else if (file.equals(scope4RootFolder)) {
                assertThat(changedFiles).containsEntry(removedFile4, Status.REMOVED);
            }
        }
    }

    @Test
    public void testSecondaryInputs() {
        // create a stream and add it to the pipeline
        OriginalStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentType(QualifiedContent.DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(project.files())
                        .build();
        transformManager.addStream(projectClass);

        File file1 = new File("file1");
        File file2 = new File("file2");
        File file3 = new File("file3");
        SecondaryFile secondaryFile1 = SecondaryFile.incremental(file2);
        SecondaryFile secondaryFile2 = SecondaryFile.incremental(project.files(file3));
        Transform transform = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .addSecondaryFile(file1)
                .addSecondaryInput(secondaryFile1)
                .addSecondaryInput(secondaryFile2)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, transform)
                        .orElseThrow(mTransformTaskFailed);

        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        assertThat(transformTask.getOldSecondaryInputs()).containsExactly(file1);
        assertThat(transformTask.getSecondaryFileInputs()).hasSize(2);
        Collection<File> flattenedFileList = transformTask.getSecondaryFileInputs().stream()
                .map(FileCollection::getFiles)
                .flatMap(Set::stream)
                .collect(Collectors.toList());
        assertThat(flattenedFileList).containsExactly(project.file(file2), project.file(file3));
    }

    @Test
    public void secondaryFileAddedWithJarInputInOriginalStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File jarFile = temporaryFolder.newFile();
        TransformStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(project.files(jarFile).builtBy("my dependency"))
                        .build();
        transformManager.addStream(projectClass);

        // create the transform, with a 2ndary file.
        File secondaryFile = temporaryFolder.newFile("secondary file");
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .addSecondaryFile(secondaryFile)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        // including a normal, in stream changed file
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(secondaryFile)
                .build());

        // check that was passed to the transform. Incremental should be off due
        // to secondary file
        assertThat(t.isIncrementalInputs()).isFalse();

        // Also check that the regular inputs are not marked as anything special
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).hasSize(1);

        JarInput singleJarInput = Iterables.getOnlyElement(jarInputs);
        assertThat(singleJarInput.getFile()).isEqualTo(jarFile);
        assertThat(singleJarInput.getStatus()).isSameAs(Status.NOTCHANGED);
    }

    @Test
    public void secondaryFileAddedWithFolderInputInOriginalStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = temporaryFolder.newFolder();
        TransformStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(project.files(rootFolder).builtBy("my dependency"))
                        .build();
        transformManager.addStream(projectClass);

        // create the transform, with a 2ndary file.
        File secondaryFile = FileUtils.join(project.getRootDir(), "secondary", "file");
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .addSecondaryFile(secondaryFile)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        // including a normal, in stream changed file
        File addedFile = new File(rootFolder, "added");
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(addedFile)
                .addedFile(secondaryFile)
                .build());

        // check that was passed to the transform. Incremental should be off due
        // to secondary file
        assertThat(t.isIncrementalInputs()).isFalse();

        // Also check that the regular inputs are not marked as anything special
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).hasSize(1);

        DirectoryInput singleDirectoryInput = Iterables.getOnlyElement(directoryInputs);
        assertThat(singleDirectoryInput.getFile()).isEqualTo(rootFolder);
        assertThat(singleDirectoryInput.getChangedFiles()).isEmpty();
    }

    @Test
    public void secondaryFileModified()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File jarFile = new File("jar file");
        TransformStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(project.files(jarFile).builtBy("my dependency"))
                        .build();
        transformManager.addStream(projectClass);

        // create the transform, with a 2ndary file.
        File secondaryFile = FileUtils.join(project.getRootDir(), "secondary", "file");
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .addSecondaryFile(secondaryFile)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        // including a normal, in stream changed file
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(jarFile)
                .modifiedFile(secondaryFile)
                .build());

        // check that was passed to the transform. Incremental should be off due
        // to secondary file
        assertThat(t.isIncrementalInputs()).isFalse();

        // checks on the inputs are done in the "secondary file added" tests
    }

    @Test
    public void secondaryFileModifiedWithIncrementalCapabilities()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File jarFile = new File("jar file");
        TransformStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(project.files(jarFile).builtBy("my dependency"))
                        .build();
        transformManager.addStream(projectClass);

        // create the transform, with a 2ndary file.
        File secondaryFile = FileUtils.join(project.getRootDir(), "secondary", "file");
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .addSecondaryInput(SecondaryFile.incremental(secondaryFile))
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data including a normal, in stream changed file
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(jarFile)
                .modifiedFile(secondaryFile)
                .build());

        // check that was passed to the transform. Incremental should be off due
        // to secondary file
        assertThat(t.isIncrementalInputs()).isTrue();

        // assert that the secondary file change event was provided.
        assertThat(t.getSecondaryInputs()).hasSize(1);
        SecondaryInput change = Iterables.getOnlyElement(t.getSecondaryInputs());
        assertThat(change.getStatus()).isEqualTo(Status.CHANGED);
        assertThat(change.getSecondaryInput().getFile()).isEqualTo(secondaryFile);

        // now delete the file.
        transformTask.transform(inputBuilder()
                .incremental()
                .removedFile(secondaryFile)
                .build());

        assertThat(t.isIncrementalInputs()).isTrue();
        assertThat(t.getSecondaryInputs()).hasSize(1);
        change = Iterables.getOnlyElement(t.getSecondaryInputs());
        assertThat(change.getStatus()).isEqualTo(Status.REMOVED);
        assertThat(change.getSecondaryInput().getFile()).isEqualTo(secondaryFile);

        // and add it back..
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(secondaryFile)
                .build());

        assertThat(t.isIncrementalInputs()).isTrue();
        assertThat(t.getSecondaryInputs()).hasSize(1);
        change = Iterables.getOnlyElement(t.getSecondaryInputs());
        assertThat(change.getStatus()).isEqualTo(Status.ADDED);
        assertThat(change.getSecondaryInput().getFile()).isEqualTo(secondaryFile);
    }

    @Test
    public void secondaryFileRemoved()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File jarFile = new File("jar file");
        TransformStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(project.files(jarFile).builtBy("my dependency"))
                        .build();
        transformManager.addStream(projectClass);

        // create the transform, with a 2ndary file.
        File secondaryFile = new File("secondary file");
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .addSecondaryFile(secondaryFile)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        // including a normal, in stream changed file
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(jarFile)
                .removedFile(secondaryFile)
                .build());

        // check that was passed to the transform. Incremental should be off due
        // to secondary file
        assertThat(t.isIncrementalInputs()).isFalse();

        // checks on the inputs are done in the "secondary file added" tests
    }

    @Test
    public void streamWithTooManyScopes()
            throws IOException, TransformException, InterruptedException {
        // create a stream and add it to the pipeline
        File rootFolder = temporaryFolder.newFolder();

        IntermediateStream stream =
                IntermediateStream.builder(project, "", "my dependency")
                        .addContentTypes(DefaultContentType.CLASSES)
                        .addScopes(Scope.PROJECT, Scope.EXTERNAL_LIBRARIES)
                        .setRootLocation(rootFolder)
                        .build();
        transformManager.addStream(stream);

        // use the output version of this stream to create some content.
        TransformOutputProvider output = stream.asOutput();
        File outputFolder = output.getContentLocation("foo", stream.getContentTypes(),
                stream.getScopes(), Format.DIRECTORY);
        mkdirs(outputFolder);

        // we need to simulate a save from a previous transform to ensure the state of the stream
        // is correct
        stream.save();

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(2);

        // expect an exception at runtime.
        exception.expect(RuntimeException.class);
        exception.expectMessage(
                String.format(
                        "Unexpected scopes found in folder '%s'. Required: PROJECT. Found: EXTERNAL_LIBRARIES, PROJECT",
                        rootFolder));
        transformTask.transform(inputBuilder().build());
    }

    @Test
    public void oldContentJsonWithExtraScopes()
            throws IOException, TransformException, InterruptedException {
        // create a stream and add it to the pipeline
        File rootFolder = temporaryFolder.newFolder();
        IntermediateStream stream =
                IntermediateStream.builder(project, "", "my dependency")
                        .addContentTypes(DefaultContentType.CLASSES)
                        .addScopes(Scope.PROJECT)
                        .setRootLocation(rootFolder)
                        .build();
        transformManager.addStream(stream);

        // create the transform
        TestTransform t =
                TestTransform.builder()
                        .setInputTypes(DefaultContentType.CLASSES)
                        .setScopes(Scope.PROJECT)
                        .setIncremental(false)
                        .build();

        // add the transform to the manager
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();

        // mimic an old __content__.json file with too many scopes from a previous build
        File transformOutputFolder = transformTask.getStreamOutputFolder();
        assertThat(transformOutputFolder).isNotNull();
        File subStreamFile = new File(transformOutputFolder, SubStream.FN_FOLDER_CONTENT);
        FileSubject.assertThat(subStreamFile).doesNotExist();
        String subStreamFileContents =
                ""
                        + "[{\"name\":\"foo\","
                        + "\"index\":0,"
                        + "\"scopes\":[\"EXTERNAL_LIBRARIES\",\"PROJECT\"],"
                        + "\"types\":[\"CLASSES\"],"
                        + "\"format\":\"DIRECTORY\","
                        + "\"present\":true}]";
        FileUtils.createFile(subStreamFile, subStreamFileContents);
        FileSubject.assertThat(subStreamFile).exists();
        Collection<SubStream> subStreams = SubStream.loadSubStreams(transformOutputFolder);
        assertThat(subStreams)
                .containsExactly(
                        new SubStream(
                                "foo",
                                0,
                                ImmutableSet.of(Scope.EXTERNAL_LIBRARIES, Scope.PROJECT),
                                ImmutableSet.of(DefaultContentType.CLASSES),
                                Format.DIRECTORY,
                                true));

        // run the transform. Afterwards, the old SubStream should be marked as not being present
        // in the __content__.json file.
        transformTask.transform(inputBuilder().build());
        FileSubject.assertThat(subStreamFile).exists();
        subStreams = SubStream.loadSubStreams(transformOutputFolder);
        assertThat(subStreams)
                .containsExactly(
                        new SubStream(
                                "foo",
                                0,
                                ImmutableSet.of(Scope.EXTERNAL_LIBRARIES, Scope.PROJECT),
                                ImmutableSet.of(DefaultContentType.CLASSES),
                                Format.DIRECTORY,
                                false));
    }

    static InputFileBuilder fileBuilder() {
        return new InputFileBuilder();
    }

    /**
     * Builder to create a mock of InputFileDetails.
     */
    static class InputFileBuilder {
        private boolean added = false;
        private boolean modified = false;
        private boolean removed = false;
        private File file = null;

        InputFileBuilder added() {
            this.added = true;
            return this;
        }

        InputFileBuilder modified() {
            this.modified = true;
            return this;
        }

        InputFileBuilder removed() {
            this.removed = true;
            return this;
        }

        InputFileBuilder setFile(File file) {
            this.file = file;
            return this;
        }

        InputFileDetails build() {
            assertTrue(added ^ modified ^ removed);
            assertNotNull(file);

            return new InputFileDetails() {

                @Override
                public boolean isAdded() {
                    return added;
                }

                @Override
                public boolean isModified() {
                    return modified;
                }

                @Override
                public boolean isRemoved() {
                    return removed;
                }

                @Override
                public File getFile() {
                    return file;
                }

                @Override
                public String toString() {
                    return MoreObjects.toStringHelper(this)
                            .add("file", getFile())
                            .add("added", isAdded())
                            .add("modified", isModified())
                            .add("removed", isRemoved())
                            .toString();
                }
            };
        }
    }

    static InputBuilder inputBuilder() {
        return new InputBuilder();
    }

    /**
     * Builder to create a mock of IncrementalTaskInputs
     */
    static class InputBuilder {
        private boolean incremental = false;
        private final List<InputFileDetails> files = Lists.newArrayList();

        InputBuilder incremental() {
            this.incremental = true;
            return this;
        }

        InputBuilder addedFile(@NonNull File file) {
            files.add(fileBuilder().added().setFile(file).build());
            return this;
        }

        InputBuilder modifiedFile(@NonNull File file) {
            files.add(fileBuilder().modified().setFile(file).build());
            return this;
        }

        InputBuilder removedFile(@NonNull File file) {
            files.add(fileBuilder().removed().setFile(file).build());
            return this;
        }

        IncrementalTaskInputs build() {
            return new IncrementalTaskInputs() {

                @Override
                public boolean isIncremental() {
                    return incremental;
                }

                @Override
                public void outOfDate(Action<? super InputFileDetails> action) {
                    for (InputFileDetails details : files) {
                        if (details.isAdded() || details.isModified()) {
                            action.execute(details);
                        }
                    }
                }

                @Override
                public void removed(Action<? super InputFileDetails> action) {
                    for (InputFileDetails details : files) {
                        if (details.isRemoved()) {
                            action.execute(details);
                        }
                    }
                }
            };
        }
    }
}

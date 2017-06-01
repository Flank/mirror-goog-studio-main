/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder;
import com.android.build.gradle.internal.tasks.databinding.DataBindingMergeArtifactsTransform;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
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
 * Tests for the transform that grabs data binding related artifacts from dependencies and
 * combines them for the annotation processor.
 */
public class DataBindingMergeArtifactsTransformTest {

    @Mock
    Context context;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File expectedOutFolder;

    @Mock
    Logger logger;

    @Before
    public void setUpMock() throws IOException {
        MockitoAnnotations.initMocks(this);
        expectedOutFolder = temporaryFolder.newFolder();
    }

    @Test
    public void nonIncrementalFromJar() throws IOException, TransformException,
            InterruptedException {
        TransformInvocation invocation = new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(
                        asJarInput(createJarFile("blah",
                                ImmutableList.of("a.class", "b.class", "c-br.bin", "d.class",
                                        "foo.bin")),
                                Status.ADDED)))
                .setIncrementalMode(false)
                .build();
        createAndInvoke(invocation);
        Set<String> outputs = collectOutputs();
        assertThat(outputs, is(ImmutableSet.of("c-br.bin")));
    }

    @Test
    public void nonIncrementalFromFolder() throws IOException, TransformException,
            InterruptedException {
        TransformInvocation invocation = new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(
                        asDirectoryInput(createFolder("blah",
                                ImmutableList.of("a.tada", "b.ff", "x-br.bin", "d.qq")))))
                .setIncrementalMode(false)
                .build();
        createAndInvoke(invocation);
        assertThat(collectOutputs(), is(ImmutableSet.of("x-br.bin")));
    }

    @Test
    public void nonIncrementalFromFolderAndJar() throws IOException, TransformException,
            InterruptedException {
        TransformInvocation invocation = new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(
                        asJarInput(createJarFile("blah-jar",
                                ImmutableList.of("a.class", "b.class", "c-layoutinfo.bin",
                                        "d.class")), Status.ADDED),
                        asDirectoryInput(createFolder("blah-folder",
                                ImmutableList.of("a.tada", "b.ff", "x-setter_store.bin", "d.qq")))))
                .setIncrementalMode(false)
                .build();
        createAndInvoke(invocation);
        assertThat(collectOutputs(), is(ImmutableSet.of("c-layoutinfo.bin", "x-setter_store.bin")));
    }

    @Test
    public void incrementalFolderWithChanges()
            throws TransformException, InterruptedException, IOException {
        File inputDir = createFolder("blah",
                ImmutableList.of("a.tada", "b.ff", "x-br.bin", "d.qq"));
        createAndInvoke(new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(asDirectoryInput(inputDir)))
                .setIncrementalMode(false)
                .build());
        // now run another invocation w/ a new file in that folder
        File unwanted = createInDataBindingFolder(inputDir, "unnecessary.tada");
        File wanted = createInDataBindingFolder(inputDir, "y-br.bin");
        createAndInvoke(new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(asDirectoryInput(inputDir,
                                ImmutableMap.of(unwanted, Status.ADDED, wanted, Status.ADDED))))
                .setIncrementalMode(true)
                .build());

        assertThat(collectOutputs(), is(ImmutableSet.of("x-br.bin", "y-br.bin")));

        // change file contents. literally
        File xBr = getFileInDataBindingFolder(inputDir, "x-br.bin");
        FileUtils.writeStringToFile(xBr, "updated-file");
        createAndInvoke(new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(
                        asDirectoryInput(inputDir, ImmutableMap.of(xBr, Status.CHANGED))
                )).setIncrementalMode(true)
                .build());

        assertThat(collectOutputs(), is(ImmutableSet.of("x-br.bin", "y-br.bin")));
        File outFile = findOutputFile(xBr.getName());
        assertThat(FileUtils.readFileToString(outFile), is("updated-file"));

        // now delete one file
        createAndInvoke(new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(
                        asDirectoryInput(inputDir, ImmutableMap.of(xBr, Status.REMOVED))))
                .setIncrementalMode(true)
                .build());
        assertThat(collectOutputs(), is(ImmutableSet.of("y-br.bin")));

        // introduce another folder
        File inputDir2 = createFolder("foobar", ImmutableList.of("d.qq"));
        File fileInInput2 = createInDataBindingFolder(inputDir2, "a-br.bin");
        createAndInvoke(new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(
                        asDirectoryInput(inputDir2, ImmutableMap.of(fileInInput2, Status.ADDED))))
                .setIncrementalMode(true)
                .build());
        assertThat(collectOutputs(), is(ImmutableSet.of("y-br.bin", "a-br.bin")));

        // change both
        File newInInput1 = createInDataBindingFolder(inputDir, "y2-br.bin");
        File newInInput2 = createInDataBindingFolder(inputDir2, "a2-br.bin");
        createAndInvoke(new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(
                        asDirectoryInput(inputDir, ImmutableMap.of(newInInput1, Status.ADDED)),
                        asDirectoryInput(inputDir2, ImmutableMap.of(newInInput2, Status.ADDED))))
                .setIncrementalMode(true)
                .build());
        assertThat(collectOutputs(), is(ImmutableSet.of("y-br.bin", "a-br.bin", "y2-br.bin",
                "a2-br.bin")));
    }

    @Test
    public void incrementalWithJarChanges()
            throws TransformException, InterruptedException, IOException {
        File jar1 = createJarFile("blah",
                ImmutableList.of("a.class", "b.class", "c-br.bin", "d.class"));
        createAndInvoke(new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(asJarInput(jar1, Status.ADDED)))
                .setIncrementalMode(false)
                .build());
        jar1.delete();
        // now run another invocation w/ changed jar
        jar1 = createJarFile("blah",
                ImmutableList.of("a.class", "b.class", "c-br.bin", "d-br.bin"));
        createAndInvoke(new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(asJarInput(jar1, Status.CHANGED)))
                .setIncrementalMode(true)
                .build());
        assertThat(collectOutputs(), is(ImmutableSet.of("c-br.bin", "d-br.bin")));
        // now introduce another jar
        File jar2 = createJarFile("blah-2jar", ImmutableList.of("abc.class", "t-br.bin"));
        createAndInvoke(new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(asJarInput(jar2, Status.ADDED)))
                .setIncrementalMode(true)
                .build());
        assertThat(collectOutputs(), is(ImmutableSet.of("c-br.bin", "d-br.bin", "t-br.bin")));

        // now remove the first jar
        createAndInvoke(new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(asJarInput(jar1, Status.REMOVED)))
                .setIncrementalMode(true)
                .build());
        assertThat(collectOutputs(), is(ImmutableSet.of("t-br.bin")));

        // add another
        File jar3 = createJarFile("tadada", ImmutableList.of("unused.class"));
        createAndInvoke(new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(asJarInput(jar3, Status.ADDED)))
                .setIncrementalMode(true)
                .build());
        assertThat(collectOutputs(), is(ImmutableSet.of("t-br.bin")));
        // modify both
        jar2.delete();
        jar3.delete();
        jar2 = createJarFile("blah-2jar", ImmutableList.of("abc.class", "x-br.bin"));
        jar3 = createJarFile("blah-3jar", ImmutableList.of("xx.class", "z-br.bin"));
        createAndInvoke(new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(
                        asJarInput(jar3, Status.ADDED), asJarInput(jar2, Status.ADDED)))
                .setIncrementalMode(true)
                .build());
        assertThat(collectOutputs(), is(ImmutableSet.of("x-br.bin", "z-br.bin")));
    }

    private Set<String> collectOutputs() {
        return FileUtils
                .listFiles(expectedOutFolder, TrueFileFilter.TRUE, TrueFileFilter.TRUE).stream()
                .map(File::getName).collect(Collectors.toSet());
    }

    private File findOutputFile(String name) {
        return FileUtils
                .listFiles(expectedOutFolder, TrueFileFilter.TRUE, TrueFileFilter.TRUE).stream()
                .filter((f) -> f.getName().equals(name)).limit(1).findFirst().get();
    }

    private void createAndInvoke(TransformInvocation invocation)
            throws TransformException, InterruptedException, IOException {
        new DataBindingMergeArtifactsTransform(logger, expectedOutFolder).transform(invocation);
    }

    private File createJarFile(String fileName, List<String> files) throws IOException {
        File jar = temporaryFolder.newFile(fileName + ".jar");
        try (JarOutputStream jarOutputStream = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(jar)))) {
            for (String entry : files) {
                jarOutputStream.putNextEntry(new ZipEntry(entry));
                jarOutputStream.closeEntry();
            }
        }
        return jar;
    }

    private File createFolder(String fileName, List<String> files) throws IOException {
        File folder = temporaryFolder.newFolder(fileName);
        for (String file : files) {
            FileUtils.touch(getFileInDataBindingFolder(folder, file));
        }
        return folder;
    }

    private File createInDataBindingFolder(File inputDir, String filename) throws IOException {
        File file = getFileInDataBindingFolder(inputDir, filename);
        FileUtils.touch(file);
        return file;
    }

    @NonNull
    private File getFileInDataBindingFolder(File inputDir, String filename) {
        File dataBindingFolder = new File(inputDir, DataBindingBuilder.INCREMENTAL_BIN_AAR_DIR);
        return new File(dataBindingFolder, filename);
    }

    private static TransformInput asJarInput(@NonNull File jarFile, @NonNull Status status) {
        return new TransformInput() {
            @NonNull
            @Override
            public Collection<JarInput> getJarInputs() {
                return ImmutableList.of(new JarInput() {
                    @NonNull
                    @Override
                    public Status getStatus() {
                        return status;
                    }

                    @NonNull
                    @Override
                    public String getName() {
                        return jarFile.getName();
                    }

                    @NonNull
                    @Override
                    public File getFile() {
                        return jarFile;
                    }

                    @NonNull
                    @Override
                    public Set<ContentType> getContentTypes() {
                        return ImmutableSet.of(DefaultContentType.CLASSES);
                    }

                    @NonNull
                    @Override
                    public Set<Scope> getScopes() {
                        return ImmutableSet.of(Scope.SUB_PROJECTS);
                    }
                });
            }

            @NonNull
            @Override
            public Collection<DirectoryInput> getDirectoryInputs() {
                return Collections.emptyList();
            }
        };
    }

    private static TransformInput asDirectoryInput(@NonNull File folder) {
        Collection<File> files = FileUtils.listFiles(folder, TrueFileFilter.TRUE, null);
        Map<File, Status> changes = new HashMap<>();
        for (File file : files) {
            changes.put(file, Status.ADDED);
        }
        return asDirectoryInput(folder, changes);
    }

    private static TransformInput asDirectoryInput(@NonNull File folder,
            @Nullable Map<File, Status> changes) {
        return new TransformInput() {

            @NonNull
            @Override
            public Collection<JarInput> getJarInputs() {
                return ImmutableList.of();
            }

            @NonNull
            @Override
            public Collection<DirectoryInput> getDirectoryInputs() {
                return ImmutableList.of(
                        new DirectoryInput() {
                            @NonNull
                            @Override
                            public Map<File, Status> getChangedFiles() {
                                return changes == null ? Maps.newHashMap() : changes;
                            }

                            @NonNull
                            @Override
                            public String getName() {
                                return folder.getName();
                            }

                            @NonNull
                            @Override
                            public File getFile() {
                                return folder;
                            }

                            @NonNull
                            @Override
                            public Set<ContentType> getContentTypes() {
                                return ImmutableSet.of();
                            }

                            @NonNull
                            @Override
                            public Set<Scope> getScopes() {
                                return ImmutableSet.of();
                            }
                        }
                );
            }
        };
    }
}

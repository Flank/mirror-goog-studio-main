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

package com.android.builder.internal.utils;

import static com.android.testutils.truth.MoreTruth.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.repository.Revision;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Test cases for {@link FileCache}. */
public class FileCacheTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @NonNull private File cacheDir;
    @NonNull private File outputDir;

    @Before
    public void setUp() throws IOException {
        cacheDir = temporaryFolder.newFolder();
        outputDir = temporaryFolder.newFolder();
    }

    @Test
    public void testCreateFile_SameInputSameOutput() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input")).build();
        File outputFile = new File(outputDir, "output");

        // First access to the cache, expect cache miss
        fileCache.createFile(outputFile, inputs, () -> {
            Files.write("Some text", outputFile, StandardCharsets.UTF_8);
            return null;
        });
        assertThat(fileCache.getHits()).isEqualTo(0);
        assertThat(fileCache.getMisses()).isEqualTo(1);
        assertThat(outputFile).hasContents("Some text");

        FileUtils.delete(outputFile);

        // Second access to the cache, expect cache hit
        fileCache.createFile(outputFile, inputs, () -> {
            fail("This statement should not be executed");
            Files.write("This text should not be written", outputFile, StandardCharsets.UTF_8);
            return null;
        });
        assertThat(fileCache.getHits()).isEqualTo(1);
        assertThat(fileCache.getMisses()).isEqualTo(1);
        assertThat(outputFile).hasContents("Some text");
    }

    @Test
    public void testCreateFile_SameInputDifferentOutputs() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input")).build();

        // First access to the cache
        File outputFile1 = new File(outputDir, "output1");
        fileCache.createFile(outputFile1, inputs, () -> {
            Files.write("Some text", outputFile1, StandardCharsets.UTF_8);
            return null;
        });

        // Second access to the cache, expect cache hit
        File outputFile2 = new File(outputDir, "output2");
        fileCache.createFile(outputFile2, inputs, () -> {
            fail("This statement should not be executed");
            Files.write("This text should not be written", outputFile2, StandardCharsets.UTF_8);
            return null;
        });
        assertThat(fileCache.getHits()).isEqualTo(1);
        assertThat(fileCache.getMisses()).isEqualTo(1);
        assertThat(outputFile1).hasContents("Some text");
        assertThat(outputFile2).hasContents("Some text");
    }

    @Test
    public void testCreateFile_DifferentInputsSameOutput() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        File outputFile = new File(outputDir, "output");

        // First access to the cache
        FileCache.Inputs inputs1 =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input1")).build();
        fileCache.createFile(outputFile, inputs1, () -> {
            Files.write("Some text", outputFile, StandardCharsets.UTF_8);
            return null;
        });

        // Second access to the cache, expect cache miss
        FileCache.Inputs inputs2 =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input2")).build();
        fileCache.createFile(outputFile, inputs2, () -> {
            Files.write("Some other text", outputFile, StandardCharsets.UTF_8);
            return null;
        });
        assertThat(fileCache.getHits()).isEqualTo(0);
        assertThat(fileCache.getMisses()).isEqualTo(2);
        assertThat(outputFile).hasContents("Some other text");
    }

    @Test
    public void testCreateFile_DifferentInputsDifferentOutputs() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);

        // First access to the cache
        FileCache.Inputs inputs1 =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input1")).build();
        File outputFile1 = new File(outputDir, "output1");
        fileCache.createFile(outputFile1, inputs1, () -> {
            Files.write("Some text", outputFile1, StandardCharsets.UTF_8);
            return null;
        });

        // Second access to the cache, expect cache miss
        FileCache.Inputs inputs2 =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input2")).build();
        File outputFile2 = new File(outputDir, "output2");
        fileCache.createFile(outputFile2, inputs2, () -> {
            Files.write("Some other text", outputFile2, StandardCharsets.UTF_8);
            return null;
        });
        assertThat(fileCache.getHits()).isEqualTo(0);
        assertThat(fileCache.getMisses()).isEqualTo(2);
        assertThat(outputFile1).hasContents("Some text");
        assertThat(outputFile2).hasContents("Some other text");
    }

    @Test
    public void testCreateFileInCacheIfAbsent_SameInput() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input")).build();

        // First access to the cache, expect cache miss
        File cachedFile = fileCache.createFileInCacheIfAbsent(inputs, (outputFile) -> {
            Files.write("Some text", outputFile, StandardCharsets.UTF_8);
        });
        assertThat(fileCache.getHits()).isEqualTo(0);
        assertThat(fileCache.getMisses()).isEqualTo(1);
        assertThat(cachedFile).hasContents("Some text");

        // Second access to the cache, expect cache hit
        File cachedFile2 = fileCache.createFileInCacheIfAbsent(inputs, (outputFile) -> {
            fail("This statement should not be executed");
            Files.write("This text should not be written", outputFile, StandardCharsets.UTF_8);
        });
        assertThat(fileCache.getHits()).isEqualTo(1);
        assertThat(fileCache.getMisses()).isEqualTo(1);
        assertThat(cachedFile2).isEqualTo(cachedFile);
        assertThat(cachedFile).hasContents("Some text");
    }

    @Test
    public void testCreateFileInCacheIfAbsent_DifferentInputs() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);

        // First access to the cache
        FileCache.Inputs inputs1 =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input1")).build();
        File cachedFile1 = fileCache.createFileInCacheIfAbsent(inputs1, (outputFile) -> {
            Files.write("Some text", outputFile, StandardCharsets.UTF_8);
        });

        // Second access to the cache, expect cache miss
        FileCache.Inputs inputs2 =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input2")).build();
        File cachedFile2 = fileCache.createFileInCacheIfAbsent(inputs2, (outputFile) -> {
            Files.write("Some other text", outputFile, StandardCharsets.UTF_8);
        });
        assertThat(fileCache.getHits()).isEqualTo(0);
        assertThat(fileCache.getMisses()).isEqualTo(2);
        assertThat(cachedFile2).isNotEqualTo(cachedFile1);
        assertThat(cachedFile1).hasContents("Some text");
        assertThat(cachedFile2).hasContents("Some other text");
    }

    @Test
    public void testCreateFile_OutputToDirectory() throws Exception {
        // Use same input different outputs
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input")).build();

        // First access to the cache
        File outputDir1 = new File(outputDir, "outputDir1");
        fileCache.createFile(outputDir1, inputs, () -> {
            FileUtils.mkdirs(outputDir1);
            Files.write(
                    "Some text", new File(outputDir1, "fileInOutputDir"), StandardCharsets.UTF_8);
            return null;
        });

        // Second access to the cache, expect cache hit
        File outputDir2 = new File(outputDir, "outputDir2");
        fileCache.createFile(outputDir2, inputs, () -> {
            fail("This statement should not be executed");
            FileUtils.mkdirs(outputDir2);
            Files.write(
                    "This text should not be written",
                    new File(outputDir2, "fileInOutputDir"), StandardCharsets.UTF_8);
            return null;
        });
        assertThat(fileCache.getHits()).isEqualTo(1);
        assertThat(fileCache.getMisses()).isEqualTo(1);
        assertThat(outputDir1.list()).hasLength(1);
        assertThat(outputDir2.list()).hasLength(1);
        assertThat(new File(outputDir1, "fileInOutputDir")).hasContents("Some text");
        assertThat(new File(outputDir2, "fileInOutputDir")).hasContents("Some text");
    }

    @Test
    public void testCreateFileInCacheIfAbsent_OutputToDirectory() throws Exception {
        // Use same input
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input")).build();

        // First access to the cache
        File cachedDir1 = fileCache.createFileInCacheIfAbsent(inputs, (outputDir) -> {
            FileUtils.mkdirs(outputDir);
            Files.write(
                    "Some text", new File(outputDir, "fileInOutputDir"), StandardCharsets.UTF_8);
        });

        // Second access to the cache, expect cache hit
        File cachedDir2 = fileCache.createFileInCacheIfAbsent(inputs, (outputDir) -> {
            fail("This statement should not be executed");
            FileUtils.mkdirs(outputDir);
            Files.write(
                    "This text should not be written",
                    new File(outputDir, "fileInOutputDir"), StandardCharsets.UTF_8);
        });
        assertThat(fileCache.getHits()).isEqualTo(1);
        assertThat(fileCache.getMisses()).isEqualTo(1);
        assertThat(cachedDir2).isEqualTo(cachedDir1);
        assertThat(cachedDir2.list()).hasLength(1);
        assertThat(new File(cachedDir2, "fileInOutputDir")).hasContents("Some text");
    }

    @Test
    public void testCreateFileThenCreateFileInCacheIfAbsent() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input")).build();

        File outputFile = new File(outputDir, "output");
        fileCache.createFile(outputFile, inputs, () -> {
            Files.write("Some text", outputFile, StandardCharsets.UTF_8);
            return null;
        });

        File cachedFile = fileCache.createFileInCacheIfAbsent(inputs, (aCachedFile) -> {
            fail("This statement should not be executed");
            Files.write("This text should not be written", aCachedFile, StandardCharsets.UTF_8);
        });

        assertThat(fileCache.getHits()).isEqualTo(1);
        assertThat(fileCache.getMisses()).isEqualTo(1);
        assertThat(outputFile).hasContents("Some text");
        assertThat(cachedFile).hasContents("Some text");
    }

    @Test
    public void testCreateFileInCacheIfAbsentThenCreateFile() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input")).build();

        File cachedFile = fileCache.createFileInCacheIfAbsent(inputs, (cachedOutputFile) -> {
            Files.write("Some text", cachedOutputFile, StandardCharsets.UTF_8);
        });

        File outputFile = new File(outputDir, "output");
        fileCache.createFile(outputFile, inputs, () -> {
            fail("This statement should not be executed");
            Files.write("This text should not be written", outputFile, StandardCharsets.UTF_8);
            return null;
        });

        assertThat(fileCache.getHits()).isEqualTo(1);
        assertThat(fileCache.getMisses()).isEqualTo(1);
        assertThat(outputFile).hasContents("Some text");
        assertThat(cachedFile).hasContents("Some text");
    }

    @Test
    public void testInvalidCacheDirectory() {
        // Use an invalid character in the directory name
        File cacheDirectory = new File("\0");
        try {
            FileCache.getInstanceWithSingleProcessLocking(cacheDirectory);
            fail("expected IOException");
        } catch (IOException exception) {
            // Expected
        }
    }

    @Test
    public void testCacheDirectoryDidNotExist_ExistsAfterCreateFile() throws Exception {
        FileUtils.delete(cacheDir);
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        assertThat(fileCache.getCacheDirectory()).doesNotExist();

        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input")).build();
        File outputFile = new File(outputDir, "output");
        fileCache.createFile(outputFile, inputs, () -> {
            Files.write("Some text", outputFile, StandardCharsets.UTF_8);
            return null;
        });
        assertThat(fileCache.getCacheDirectory()).exists();
    }

    @Test
    public void testCacheDirectoryDidNotExist_ExistsAfterCreateFileInCacheIfAbsent()
            throws Exception {
        FileUtils.delete(cacheDir);
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        assertThat(fileCache.getCacheDirectory()).doesNotExist();

        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input")).build();
        fileCache.createFileInCacheIfAbsent(inputs, (outputFile) -> {
            Files.write("Some text", outputFile, StandardCharsets.UTF_8);
        });
        assertThat(fileCache.getCacheDirectory()).exists();
    }

    @Test
    public void testSameCacheDirectory() throws IOException {
        FileCache fileCache1 = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache fileCache2 = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        assertThat(fileCache1).isSameAs(fileCache2);
    }

    @Test
    public void testInconsistentLockingScope() throws IOException {
        FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        try {
            FileCache.getInstanceWithInterProcessLocking(cacheDir);
            fail("expected IllegalStateException");
        } catch (IllegalStateException exception) {
            // Expected
        }
    }

    @Test
    public void testUnusualInput() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("")).build();

        File outputFile = new File(outputDir, "output");
        fileCache.createFile(outputFile, inputs, () -> {
            Files.write("Some text", outputFile, StandardCharsets.UTF_8);
            return null;
        });

        assertThat(fileCache.getHits()).isEqualTo(0);
        assertThat(fileCache.getMisses()).isEqualTo(1);
        assertThat(outputFile).hasContents("Some text");
    }

    @Test
    public void testInvalidOutputFile() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input")).build();

        File outputFile = new File(cacheDir, "output");
        try {
            fileCache.createFile(outputFile, inputs, () -> null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            assertThat(exception).hasMessage(
                    String.format(
                            "Output file/directory '%1$s' must not be located"
                                    + " in the cache directory '%2$s'",
                            outputFile.getAbsolutePath(),
                            fileCache.getCacheDirectory().getAbsolutePath()));
        }
        assertThat(fileCache.getCacheDirectory().list()).isEmpty();

        outputFile = cacheDir.getParentFile();
        try {
            fileCache.createFile(outputFile, inputs, () -> null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            assertThat(exception).hasMessage(
                    String.format(
                            "Output directory '%1$s' must not contain the cache directory '%2$s'",
                            outputFile.getAbsolutePath(),
                            fileCache.getCacheDirectory().getAbsolutePath()));
        }
        assertThat(fileCache.getCacheDirectory().list()).isEmpty();

        outputFile = cacheDir;
        try {
            fileCache.createFile(outputFile, inputs, () -> null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            assertThat(exception).hasMessage(
                    String.format(
                            "Output directory must not be the same as the cache directory '%1$s'",
                            fileCache.getCacheDirectory().getAbsolutePath()));
        }
        assertThat(fileCache.getCacheDirectory().list()).isEmpty();
    }

    @Test
    public void testOutputFileAlreadyExistsAndIsNotCreated() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input")).build();

        File outputDir1 = new File(outputDir, "dir1");
        File outputDir2 = new File(outputDir, "dir2");
        FileUtils.mkdirs(outputDir1);
        FileUtils.mkdirs(outputDir2);
        File fileInOutputDir1 = new File(outputDir1, "output1");
        File fileInOutputDir2 = new File(outputDir2, "output2");
        Files.touch(fileInOutputDir1);
        Files.touch(fileInOutputDir2);

        fileCache.createFile(fileInOutputDir1, inputs, () -> {
            // The cache should have deleted the existing output file (but not the parent directory)
            // before calling this callback
            assertThat(fileInOutputDir1).doesNotExist();
            assertThat(fileInOutputDir1.getParentFile()).exists();
            return null;
        });

        // Since the callback didn't create an output, if the cache is called again, it should
        // delete any existing output files (but not their parent directories)
        fileCache.createFile(fileInOutputDir2, inputs, () -> null);
        assertThat(fileInOutputDir2).doesNotExist();
        assertThat(fileInOutputDir2.getParentFile()).exists();
    }

    @Test
    public void testOutputFileDoesNotAlreadyExistAndIsCreated() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input")).build();

        File outputDir1 = new File(outputDir, "dir1");
        File outputDir2 = new File(outputDir, "dir2");
        File fileInOutputDir1 = new File(outputDir1, "output1");
        File fileInOutputDir2 = new File(outputDir2, "output2");

        fileCache.createFile(fileInOutputDir1, inputs, () -> {
            // The cache should have created the parent directory of the output file (but should not
            // have created the output file) before calling this callback
            assertThat(fileInOutputDir1).doesNotExist();
            assertThat(fileInOutputDir1.getParentFile()).exists();
            Files.touch(fileInOutputDir1);
            return null;
        });

        // Since the callback created an output, if the cache is called again, it should create new
        // output files (together with their parent directories)
        fileCache.createFile(fileInOutputDir2, inputs, () -> null);
        assertThat(fileInOutputDir2).exists();
    }

    @Test
    public void testCreateFile_FileCreatorIOException() throws IOException {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input")).build();
        File outputFile = new File(outputDir, "output");

        try {
            fileCache.createFile(outputFile, inputs, () -> {
                throw new IOException("Some I/O exception");
            });
            fail("expected ExecutionException");
        } catch (ExecutionException exception) {
            assertThat(Throwables.getRootCause(exception)).isInstanceOf(IOException.class);
            assertThat(Throwables.getRootCause(exception)).hasMessage("Some I/O exception");
        }
        assertThat(fileCache.getCacheDirectory().list()).isEmpty();
    }

    @Test
    public void testCreateFile_FileCreatorRuntimeException() throws IOException {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input")).build();
        File outputFile = new File(outputDir, "output");

        try {
            fileCache.createFile(outputFile, inputs, () -> {
                throw new RuntimeException("Some runtime exception");
            });
            fail("expected ExecutionException");
        } catch (ExecutionException exception) {
            assertThat(Throwables.getRootCause(exception)).isInstanceOf(RuntimeException.class);
            assertThat(Throwables.getRootCause(exception)).hasMessage("Some runtime exception");
        }
        assertThat(fileCache.getCacheDirectory().list()).isEmpty();
    }

    @Test
    public void testCreateFile_IOExceptionNotThrownByFileCreator() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input")).build();

        // Use an invalid character in the file name
        File outputFile = new File("\0");
        try {
            fileCache.createFile(outputFile, inputs, () -> null);
            fail("expected IOException");
        } catch (IOException exception) {
            // Expected
        }
        assertThat(fileCache.getCacheDirectory().list()).isEmpty();
    }

    @Test
    public void testCreateFileInCacheIfAbsent_FileCreatorIOException() throws IOException {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input")).build();

        try {
            fileCache.createFileInCacheIfAbsent(inputs, (outputFile) -> {
                throw new IOException("Some I/O exception");
            });
            fail("expected ExecutionException");
        } catch (ExecutionException exception) {
            assertThat(Throwables.getRootCause(exception)).isInstanceOf(IOException.class);
            assertThat(Throwables.getRootCause(exception)).hasMessage("Some I/O exception");
        }
        assertThat(fileCache.getCacheDirectory().list()).isEmpty();
    }

    @Test
    public void testCreateFileInCacheIfAbsent_FileCreatorRuntimeException() throws IOException {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input")).build();

        try {
            fileCache.createFileInCacheIfAbsent(inputs, (outputFile) -> {
                throw new RuntimeException("Some runtime exception");
            });
            fail("expected ExecutionException");
        } catch (ExecutionException exception) {
            assertThat(Throwables.getRootCause(exception)).isInstanceOf(RuntimeException.class);
            assertThat(Throwables.getRootCause(exception)).hasMessage("Some runtime exception");
        }
        assertThat(fileCache.getCacheDirectory().list()).isEmpty();
    }

    @Test
    public void testCreateFileInCacheIfAbsent_IOExceptionNotThrownByFileCreator()
            throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input")).build();

        try {
            fileCache.createFileInCacheIfAbsent(inputs, (outputFile) -> {
                // Delete the cache entry directory to so that the execution will crash later
                FileUtils.deletePath(outputFile.getParentFile());
            });
            fail("expected IOException");
        } catch (IOException exception) {
            assertThat(Throwables.getRootCause(exception))
                    .isInstanceOf(FileNotFoundException.class);
        }
        assertThat(fileCache.getCacheDirectory().list()).isEmpty();
    }

    @Test
    public void testGetCachedFile() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input")).build();

        File cachedFile = fileCache.getCachedFile(inputs);
        assertThat(FileUtils.isFileInDirectory(cachedFile, fileCache.getCacheDirectory())).isTrue();

        File outputFile = new File(outputDir, "output");
        fileCache.createFile(outputFile, inputs, () -> {
            Files.write("Some text", outputFile, StandardCharsets.UTF_8);
            return null;
        });
        assertThat(fileCache.getCacheDirectory().list()).hasLength(1);
        assertThat(cachedFile).hasContents("Some text");

        File cachedFile2 =
                fileCache.createFileInCacheIfAbsent(
                        inputs, (anOutputFile) -> assertThat(anOutputFile).isSameAs(cachedFile));
        assertThat(fileCache.getCacheDirectory().list()).hasLength(1);
        assertThat(cachedFile2).isEqualTo(cachedFile);
    }

    @Test
    public void testDeleteFileCache() throws IOException {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        assertThat(fileCache.getCacheDirectory()).exists();

        fileCache.delete();
        assertThat(fileCache.getCacheDirectory()).doesNotExist();
    }

    @Test
    public void testCreateFile_MultiThreads_SingleProcessLocking_SameInputDifferentOutputs()
            throws IOException {
        testCreateFile_MultiThreads_SameCacheSameInputDifferentOutputs(
                FileCache.getInstanceWithSingleProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testCreateFile_MultiThreads_SingleProcessLocking_DifferentInputsDifferentOutputs()
            throws IOException {
        testCreateFile_MultiThreads_SameCacheDifferentInputsDifferentOutputs(
                FileCache.getInstanceWithSingleProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testCreateFile_MultiThreads_SingleProcessLocking_DifferentCaches()
            throws IOException {
        testCreateFile_MultiThreads_DifferentCaches(
                FileCache.getInstanceWithSingleProcessLocking(temporaryFolder.newFolder()),
                FileCache.getInstanceWithSingleProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testCreateFile_MultiThreads_InterProcessLocking_SameInputDifferentOutputs()
            throws IOException {
        testCreateFile_MultiThreads_SameCacheSameInputDifferentOutputs(
                FileCache.getInstanceWithInterProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testCreateFile_MultiThreads_InterProcessLocking_DifferentInputsDifferentOutputs()
            throws IOException {
        testCreateFile_MultiThreads_SameCacheDifferentInputsDifferentOutputs(
                FileCache.getInstanceWithInterProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testCreateFile_MultiThreads_InterProcessLocking_DifferentCaches()
            throws IOException {
        testCreateFile_MultiThreads_DifferentCaches(
                FileCache.getInstanceWithInterProcessLocking(temporaryFolder.newFolder()),
                FileCache.getInstanceWithInterProcessLocking(temporaryFolder.newFolder()));
    }

    private void testCreateFile_MultiThreads_SameCacheSameInputDifferentOutputs(
            @NonNull FileCache fileCache) throws IOException {
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input")).build();
        File[] outputFiles = {new File(outputDir, "output1"), new File(outputDir, "output2")};
        String fileContent = "Some text";

        ConcurrencyTester<File, Void> tester = new ConcurrencyTester<>();
        prepareConcurrencyTestForCreateFile(
                tester,
                new FileCache[] {fileCache, fileCache},
                new FileCache.Inputs[] {inputs, inputs},
                outputFiles,
                new String[] {fileContent, fileContent});

        // Since we use the same input, we expect only one of the actions to be executed
        tester.assertThatOnlyOneActionIsExecuted();

        assertThat(fileCache.getHits()).isEqualTo(1);
        assertThat(fileCache.getMisses()).isEqualTo(1);
        assertThat(outputFiles[0]).hasContents(fileContent);
        assertThat(outputFiles[1]).hasContents(fileContent);
    }

    private void testCreateFile_MultiThreads_SameCacheDifferentInputsDifferentOutputs(
            @NonNull FileCache fileCache) throws IOException {
        FileCache.Inputs[] inputList = {
                new FileCache.Inputs.Builder().putFilePath("file1", new File("input1")).build(),
                new FileCache.Inputs.Builder().putFilePath("file2", new File("input2")).build(),
        };
        File[] outputFiles = {new File(outputDir, "output1"), new File(outputDir, "output2")};
        String[] fileContents = {"Foo text", "Bar text"};

        ConcurrencyTester<File, Void> tester = new ConcurrencyTester<>();
        prepareConcurrencyTestForCreateFile(
                tester,
                new FileCache[] {fileCache, fileCache},
                inputList,
                outputFiles,
                fileContents);

        // Since we use different inputs, the actions are allowed to run concurrently
        tester.assertThatActionsCanRunConcurrently();

        assertThat(fileCache.getHits()).isEqualTo(0);
        assertThat(fileCache.getMisses()).isEqualTo(2);
        assertThat(outputFiles[0]).hasContents(fileContents[0]);
        assertThat(outputFiles[1]).hasContents(fileContents[1]);
    }

    private void testCreateFile_MultiThreads_DifferentCaches(
            @NonNull FileCache fileCache1, @NonNull FileCache fileCache2) throws IOException {
        // Use same input different outputs, different caches
        FileCache[] fileCaches = {fileCache1, fileCache2};
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input")).build();
        File[] outputFiles = {new File(outputDir, "output1"), new File(outputDir, "output2")};
        String[] fileContents = {"Foo text", "Bar text"};

        ConcurrencyTester<File, Void> tester = new ConcurrencyTester<>();
        prepareConcurrencyTestForCreateFile(
                tester,
                fileCaches,
                new FileCache.Inputs[] {inputs, inputs},
                outputFiles,
                fileContents);

        // Since we use different caches, even though we use the same input, the actions are allowed
        // to run concurrently
        tester.assertThatActionsCanRunConcurrently();

        assertThat(fileCaches[0].getHits()).isEqualTo(0);
        assertThat(fileCaches[0].getMisses()).isEqualTo(1);
        assertThat(fileCaches[1].getHits()).isEqualTo(0);
        assertThat(fileCaches[1].getMisses()).isEqualTo(1);
        assertThat(outputFiles[0]).hasContents(fileContents[0]);
        assertThat(outputFiles[1]).hasContents(fileContents[1]);
    }

    /**
     * Performs a few steps common to the concurrency tests for
     * {@link FileCache#createFile(File, FileCache.Inputs, Callable)}.
     */
    private void prepareConcurrencyTestForCreateFile(
            @NonNull ConcurrencyTester<File, Void> tester,
            @NonNull FileCache[] fileCaches,
            @NonNull FileCache.Inputs[] inputsList,
            @NonNull File[] outputFiles,
            @NonNull String[] fileContents) {
        for (int i = 0; i < fileCaches.length; i++) {
            FileCache fileCache = fileCaches[i];
            FileCache.Inputs inputs = inputsList[i];
            File outputFile = outputFiles[i];
            String fileContent = fileContents[i];

            IOExceptionFunction<File, Void> actionUnderTest = (File file) -> {
                Files.write(fileContent, file, StandardCharsets.UTF_8);
                return null;
            };
            tester.addMethodInvocationFromNewThread(
                    (IOExceptionFunction<File, Void> anActionUnderTest) -> {
                        try {
                            fileCache.createFile(
                                    outputFile, inputs, () -> anActionUnderTest.apply(outputFile));
                        } catch (ExecutionException exception) {
                            throw new RuntimeException(exception);
                        }
                    },
                    actionUnderTest);
        }
    }

    @Test
    public void testCreateFileInCacheIfAbsent_MultiThreads_SingleProcessLocking_SameInput()
            throws IOException {
        testCreateFileInCacheIfAbsent_MultiThreads_SameCacheSameInput(
                FileCache.getInstanceWithSingleProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testCreateFileInCacheIfAbsent_MultiThreads_SingleProcessLocking_DifferentInputs()
            throws IOException {
        testCreateFileInCacheIfAbsent_MultiThreads_SameCacheDifferentInputs(
                FileCache.getInstanceWithSingleProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testCreateFileInCacheIfAbsent_MultiThreads_SingleProcessLocking_DifferentCaches()
            throws IOException {
        testCreateFileInCacheIfAbsent_MultiThreads_DifferentCaches(
                FileCache.getInstanceWithSingleProcessLocking(temporaryFolder.newFolder()),
                FileCache.getInstanceWithSingleProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testCreateFileInCacheIfAbsent_MultiThreads_InterProcessLocking_SameInput()
            throws IOException {
        testCreateFileInCacheIfAbsent_MultiThreads_SameCacheSameInput(
                FileCache.getInstanceWithInterProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testCreateFileInCacheIfAbsent_MultiThreads_InterProcessLocking_DifferentInputs()
            throws IOException {
        testCreateFileInCacheIfAbsent_MultiThreads_SameCacheDifferentInputs(
                FileCache.getInstanceWithInterProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testCreateFileInCacheIfAbsent_MultiThreads_InterProcessLocking_DifferentCaches()
            throws IOException {
        testCreateFileInCacheIfAbsent_MultiThreads_DifferentCaches(
                FileCache.getInstanceWithInterProcessLocking(temporaryFolder.newFolder()),
                FileCache.getInstanceWithInterProcessLocking(temporaryFolder.newFolder()));
    }

    private void testCreateFileInCacheIfAbsent_MultiThreads_SameCacheSameInput(
            @NonNull FileCache fileCache) throws IOException {
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input")).build();
        String fileContent = "Some text";
        File[] cachedFiles = new File[2];

        ConcurrencyTester<File, Void> tester = new ConcurrencyTester<>();
        prepareConcurrencyTestForCreateFileInCacheIfAbsent(
                tester,
                new FileCache[] {fileCache, fileCache},
                new FileCache.Inputs[] {inputs, inputs},
                new String[] {fileContent, fileContent},
                cachedFiles);

        // Since we use the same input, we expect only one of the actions to be executed
        tester.assertThatOnlyOneActionIsExecuted();

        assertThat(fileCache.getHits()).isEqualTo(1);
        assertThat(fileCache.getMisses()).isEqualTo(1);
        assertThat(cachedFiles[1]).isEqualTo(cachedFiles[0]);
        assertThat(cachedFiles[0]).hasContents(fileContent);
    }

    private void testCreateFileInCacheIfAbsent_MultiThreads_SameCacheDifferentInputs(
            @NonNull FileCache fileCache) throws IOException {
        FileCache.Inputs[] inputList = {
                new FileCache.Inputs.Builder().putFilePath("file1", new File("input1")).build(),
                new FileCache.Inputs.Builder().putFilePath("file2", new File("input2")).build(),
        };
        String[] fileContents = {"Foo text", "Bar text"};
        File[] cachedFiles = new File[2];

        ConcurrencyTester<File, Void> tester = new ConcurrencyTester<>();
        prepareConcurrencyTestForCreateFileInCacheIfAbsent(
                tester,
                new FileCache[] {fileCache, fileCache},
                inputList,
                fileContents,
                cachedFiles);

        // Since we use different inputs, the actions are allowed to run concurrently
        tester.assertThatActionsCanRunConcurrently();

        assertThat(fileCache.getHits()).isEqualTo(0);
        assertThat(fileCache.getMisses()).isEqualTo(2);
        assertThat(cachedFiles[1]).isNotEqualTo(cachedFiles[0]);
        assertThat(cachedFiles[0]).hasContents(fileContents[0]);
        assertThat(cachedFiles[1]).hasContents(fileContents[1]);
    }

    private void testCreateFileInCacheIfAbsent_MultiThreads_DifferentCaches(
            @NonNull FileCache fileCache1, @NonNull FileCache fileCache2) throws IOException {
        // Use same input, different caches
        FileCache[] fileCaches = {fileCache1, fileCache2};
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("input")).build();
        String[] fileContents = {"Foo text", "Bar text"};
        File[] cachedFiles = new File[2];

        ConcurrencyTester<File, Void> tester = new ConcurrencyTester<>();
        prepareConcurrencyTestForCreateFileInCacheIfAbsent(
                tester,
                fileCaches,
                new FileCache.Inputs[] {inputs, inputs},
                fileContents,
                cachedFiles);

        // Since we use different caches, even though we use the same input, the actions are allowed
        // to run concurrently
        tester.assertThatActionsCanRunConcurrently();

        assertThat(fileCaches[0].getHits()).isEqualTo(0);
        assertThat(fileCaches[0].getMisses()).isEqualTo(1);
        assertThat(fileCaches[1].getHits()).isEqualTo(0);
        assertThat(fileCaches[1].getMisses()).isEqualTo(1);
        assertThat(cachedFiles[1]).isNotEqualTo(cachedFiles[0]);
        assertThat(cachedFiles[0]).hasContents(fileContents[0]);
        assertThat(cachedFiles[1]).hasContents(fileContents[1]);
    }

    /**
     * Performs a few steps common to the concurrency tests for
     * {@link FileCache#createFileInCacheIfAbsent(FileCache.Inputs, ExceptionConsumer)}.
     */
    private void prepareConcurrencyTestForCreateFileInCacheIfAbsent(
            @NonNull ConcurrencyTester<File, Void> tester,
            @NonNull FileCache[] fileCaches,
            @NonNull FileCache.Inputs[] inputsList,
            @NonNull String[] fileContents,
            @NonNull File[] cachedFiles) {
        for (int i = 0; i < fileCaches.length; i++) {
            FileCache fileCache = fileCaches[i];
            FileCache.Inputs inputs = inputsList[i];
            String fileContent = fileContents[i];
            final int idx = i;

            IOExceptionFunction<File, Void> actionUnderTest = (File file) -> {
                Files.write(fileContent, file, StandardCharsets.UTF_8);
                return null;
            };
            tester.addMethodInvocationFromNewThread(
                    (IOExceptionFunction<File, Void> anActionUnderTest) -> {
                        try {
                            cachedFiles[idx] =
                                    fileCache.createFileInCacheIfAbsent(
                                            inputs,
                                            (outputFile) -> anActionUnderTest.apply(outputFile));
                        } catch (ExecutionException exception) {
                            throw new RuntimeException(exception);
                        }
                    },
                    actionUnderTest);
        }
    }

    @Test
    public void testDoLockedReturnValue() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        File accessedFile = new File(cacheDir, "foo");

        assertThat(fileCache.doLocked(accessedFile, FileCache.LockingType.SHARED, () -> true))
                .isTrue();
        assertThat(fileCache.doLocked(accessedFile, FileCache.LockingType.SHARED, () -> false))
                .isFalse();
    }

    @Test
    public void testDoLockedException() throws Exception {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);
        File accessedFile = new File(cacheDir, "foo");

        try {
            fileCache.doLocked(accessedFile, FileCache.LockingType.SHARED, () -> {
                throw new IOException("Some I/O exception");
            });
            fail("expected ExecutionException");
        } catch (ExecutionException exception) {
            assertThat(exception.getCause()).isInstanceOf(IOException.class);
            assertThat(exception.getCause()).hasMessage("Some I/O exception");
        }

        try {
            fileCache.doLocked(accessedFile, FileCache.LockingType.SHARED, () -> {
                throw new RuntimeException("Some runtime exception");
            });
            fail("expected ExecutionException");
        } catch (ExecutionException exception) {
            assertThat(exception.getCause()).isInstanceOf(RuntimeException.class);
            assertThat(exception.getCause()).hasMessage("Some runtime exception");
        }

        try {
            // Use an invalid character in the file name
            accessedFile = new File("\0");
            fileCache.doLocked(accessedFile, FileCache.LockingType.SHARED, () -> true);
            fail("expected IOException");
        } catch (IOException exception) {
            // Expected
        }
    }

    @Test
    public void testDoLocked_MultiThreads_SingleProcessLocking_SameLockFileMixedLocks()
            throws IOException {
        testDoLocked_MultiThreads_SameLockFileMixedLocks(
                FileCache.getInstanceWithSingleProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testDoLocked_MultiThreads_SingleProcessLocking_SameLockFileSharedLocks()
            throws IOException {
        testDoLocked_MultiThreads_SameLockFileSharedLocks(
                FileCache.getInstanceWithSingleProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testDoLocked_MultiThreads_SingleProcessLocking_DifferentLockFiles()
            throws IOException {
        testDoLocked_MultiThreads_DifferentLockFiles(
                FileCache.getInstanceWithSingleProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testDoLocked_MultiThreads_InterProcessLocking_SameLockFileMixedLocks()
            throws IOException {
        testDoLocked_MultiThreads_SameLockFileMixedLocks(
                FileCache.getInstanceWithInterProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testDoLocked_MultiThreads_InterProcessLocking_SameLockFileSharedLocks()
            throws IOException {
        testDoLocked_MultiThreads_SameLockFileSharedLocks(
                FileCache.getInstanceWithInterProcessLocking(temporaryFolder.newFolder()));
    }

    @Test
    public void testDoLocked_MultiThreads_InterProcessLocking_DifferentLockFiles()
            throws IOException {
        testDoLocked_MultiThreads_DifferentLockFiles(
                FileCache.getInstanceWithInterProcessLocking(temporaryFolder.newFolder()));
    }

    private void testDoLocked_MultiThreads_SameLockFileMixedLocks(@NonNull FileCache fileCache) {
        ConcurrencyTester<Void, Void> tester = new ConcurrencyTester<>();
        prepareConcurrencyTestForDoLocked(
                tester,
                fileCache,
                new String[] {"foo", "foo", "foobar".substring(0, 3)},
                new FileCache.LockingType[] {
                    FileCache.LockingType.SHARED,
                    FileCache.LockingType.EXCLUSIVE,
                    FileCache.LockingType.EXCLUSIVE
                });

        // Since we use mixed locks on the same lock file, the actions are not allowed to run
        // concurrently
        tester.assertThatActionsCannotRunConcurrently();
    }

    private void testDoLocked_MultiThreads_SameLockFileSharedLocks(@NonNull FileCache fileCache) {
        ConcurrencyTester<Void, Void> tester = new ConcurrencyTester<>();
        prepareConcurrencyTestForDoLocked(
                tester,
                fileCache,
                new String[] {"foo", "foo", "foobar".substring(0, 3)},
                new FileCache.LockingType[] {
                    FileCache.LockingType.SHARED,
                    FileCache.LockingType.SHARED,
                    FileCache.LockingType.SHARED
                });

        // Since we use shared locks on the same lock file, the actions are allowed to run
        // concurrently
        tester.assertThatActionsCanRunConcurrently();
    }

    private void testDoLocked_MultiThreads_DifferentLockFiles(@NonNull FileCache fileCache) {
        // Use mixed locks on different lock files
        ConcurrencyTester<Void, Void> tester = new ConcurrencyTester<>();
        prepareConcurrencyTestForDoLocked(
                tester,
                fileCache,
                new String[] {"foo1", "foo2", "foo3"},
                new FileCache.LockingType[] {
                    FileCache.LockingType.SHARED,
                    FileCache.LockingType.EXCLUSIVE,
                    FileCache.LockingType.EXCLUSIVE
                });

        // Since we use different lock files, even though we use mixed locks, the actions are
        // allowed to run concurrently
        tester.assertThatActionsCanRunConcurrently();
    }

    /**
     * Performs a few steps common to the concurrency tests for
     * {@link FileCache#doLocked(File, FileCache.LockingType, Callable)}.
     */
    private void prepareConcurrencyTestForDoLocked(
            @NonNull ConcurrencyTester<Void, Void> tester,
            @NonNull FileCache fileCache,
            @NonNull String[] filesToLock,
            @NonNull FileCache.LockingType[] lockingTypes) {
        IOExceptionFunction<Void, Void> actionUnderTest = (Void arg) -> {
            // Do some artificial work here
            assertThat(1).isEqualTo(1);
            return null;
        };
        for (int i = 0; i < filesToLock.length; i++) {
            String fileToLock = filesToLock[i];
            FileCache.LockingType lockingType = lockingTypes[i];

            tester.addMethodInvocationFromNewThread(
                    (IOExceptionFunction<Void, Void> anActionUnderTest) -> {
                        try {
                            fileCache.doLocked(
                                    new File(cacheDir, fileToLock),
                                    lockingType,
                                    () -> anActionUnderTest.apply(null));
                        } catch (ExecutionException exception) {
                            throw new RuntimeException(exception);
                        }
                    },
                    actionUnderTest);
        }
    }

    @Test
    public void testInputsGetKey() throws IOException {
        File inputDir = temporaryFolder.newFolder();

        // Test all types of input parameters
        File inputFile =
                new File(
                        "/Users/foo/Android/Sdk/extras/android/m2repository/com/android/support/"
                                + "support-annotations/23.3.0/support-annotations-23.3.0.jar");
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder()
                        .putFilePath("file", inputFile)
                        .putString(
                                "buildToolsRevision", Revision.parseRevision("23.0.3").toString())
                        .putBoolean("jumboMode", true)
                        .putBoolean("optimize", false)
                        .putBoolean("multiDex", true)
                        .putFilePath("classpath", new File("foo"))
                        .build();
        assertThat(inputs.toString())
                .isEqualTo(
                        Joiner.on(System.lineSeparator())
                                .join(
                                        "file=" + inputFile.getPath(),
                                        "buildToolsRevision=23.0.3",
                                        "jumboMode=true",
                                        "optimize=false",
                                        "multiDex=true",
                                        "classpath=foo"));
        // In the assertion below, we represent the expected hash code as the regular expression
        // \w{40} since the computed hash code in the short key might be different on different
        // platforms
        assertThat(inputs.getKey()).matches("\\w{40}");

        // Test relative input file path
        inputFile = new File("com.android.support/design/23.3.0/jars/classes.jar");
        inputs = new FileCache.Inputs.Builder().putFilePath("file", inputFile).build();
        assertThat(inputs.toString()).isEqualTo("file=" + inputFile.getPath());
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
            assertThat(inputs.getKey()).isEqualTo("4b30578b0f46117c61911c027a589b48b80bd3d1");
        } else {
            assertThat(inputs.getKey()).isEqualTo("25dcc2247956f01b9dbdca420eff87c96aaf2874");
        }

        // Test Windows-based input file path
        inputFile =
                new File(
                        "C:\\Users\\foo\\Android\\Sdk\\extras\\android\\m2repository\\"
                                + "com\\android\\support\\support-annotations\\23.3.0\\"
                                + "support-annotations-23.3.0.jar");
        inputs = new FileCache.Inputs.Builder().putFilePath("file", inputFile).build();
        assertThat(inputs.toString()).isEqualTo("file=" + inputFile.getPath());
        assertThat(inputs.getKey()).isEqualTo("d78d98a050e19057d83ad84ddebde43e2f8e67d7");

        // Test file hash
        inputFile = new File(inputDir, "input");
        Files.write("Some text", inputFile, StandardCharsets.UTF_8);
        inputs = new FileCache.Inputs.Builder().putFileHash("fileHash", inputFile).build();
        assertThat(inputs.toString())
                .isEqualTo("fileHash=02d92c580d4ede6c80a878bdd9f3142d8f757be8");
        assertThat(inputs.getKey()).isEqualTo("4f81fcad8e33bbf04da9d6b0401b03472e0289d6");

        // Test unusual file path
        inputFile = new File("foo`-=[]\\\\;',./~!@#$%^&*()_+{}|:\\\"<>?");
        inputs = new FileCache.Inputs.Builder().putFilePath("file", inputFile).build();
        assertThat(inputs.toString()).isEqualTo("file=" + inputFile.getPath());
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
            assertThat(inputs.getKey()).isEqualTo("c9a5f22f392f6186b18b66f70e59fa20583533dd");
        } else {
            assertThat(inputs.getKey()).isEqualTo("7f205499565a454d0186f34313e63281c7192a43");
        }

        // Test empty file path
        inputFile = new File("");
        inputs = new FileCache.Inputs.Builder().putFilePath("file", inputFile).build();
        assertThat(inputs.toString()).isEqualTo("file=");
        assertThat(inputs.getKey()).isEqualTo("3fe9ece2d6113d8db5c0c5576cc1378823d839ab");

        // Test empty file content
        inputFile = new File(inputDir, "input");
        Files.write("", inputFile, StandardCharsets.UTF_8);
        inputs = new FileCache.Inputs.Builder().putFileHash("fileHash", inputFile).build();
        assertThat(inputs.toString())
                .isEqualTo("fileHash=da39a3ee5e6b4b0d3255bfef95601890afd80709");
        assertThat(inputs.getKey()).isEqualTo("1c0664f09cb5710ca68017f6f95078fced34f2f5");

        // Test empty inputs
        try {
            new FileCache.Inputs.Builder().build();
            fail("expected IllegalStateException");
        } catch (IllegalStateException exception) {
            assertThat(exception).hasMessage("Inputs must not be empty.");
        }

        // Test duplicate parameters with the same name and type
        inputs =
                new FileCache.Inputs.Builder()
                        .putString("arg", "true")
                        .putString("arg", "false")
                        .build();
        assertThat(inputs.toString()).isEqualTo("arg=false");

        // Test duplicate parameters with the same name and different types
        inputs =
                new FileCache.Inputs.Builder()
                        .putString("arg", "true")
                        .putBoolean("arg", false)
                        .build();
        assertThat(inputs.toString()).isEqualTo("arg=false");

        // Test duplicate parameters interleaved with other parameters
        inputs =
                new FileCache.Inputs.Builder()
                        .putString("arg1", "true")
                        .putString("arg2", "true")
                        .putString("arg1", "false")
                        .build();
        assertThat(inputs.toString())
                .isEqualTo("arg1=false" + System.lineSeparator() + "arg2=true");

        // Test inputs with different sizes
        FileCache.Inputs inputs1 =
                new FileCache.Inputs.Builder()
                        .putBoolean("arg1", true)
                        .putBoolean("arg2", true)
                        .build();
        FileCache.Inputs inputs2 =
                new FileCache.Inputs.Builder()
                        .putBoolean("arg1", true)
                        .putBoolean("arg2", true)
                        .putBoolean("arg3", true)
                        .build();
        assertThat(inputs1.getKey()).isNotEqualTo(inputs2.getKey());

        // Test inputs with same size, different orders
        inputs1 =
                new FileCache.Inputs.Builder()
                        .putBoolean("arg1", true)
                        .putBoolean("arg2", true)
                        .build();
        inputs2 =
                new FileCache.Inputs.Builder()
                        .putBoolean("arg2", true)
                        .putBoolean("arg1", true)
                        .build();
        assertThat(inputs1.getKey()).isNotEqualTo(inputs2.getKey());

        // Test inputs with same size, same order, different values
        inputs1 =
                new FileCache.Inputs.Builder()
                        .putBoolean("arg1", true)
                        .putBoolean("arg2", true)
                        .build();
        inputs2 =
                new FileCache.Inputs.Builder()
                        .putBoolean("arg1", true)
                        .putBoolean("arg2", false)
                        .build();
        assertThat(inputs1.getKey()).isNotEqualTo(inputs2.getKey());

        // Test inputs with same size, same order, same values
        inputs1 =
                new FileCache.Inputs.Builder()
                        .putBoolean("arg1", true)
                        .putBoolean("arg2", true)
                        .build();
        inputs2 =
                new FileCache.Inputs.Builder()
                        .putBoolean("arg1", true)
                        .putBoolean("arg2", true)
                        .build();
        assertThat(inputs1.getKey()).isEqualTo(inputs2.getKey());

        // Test inputs with different file paths, same canonical path
        inputs1 =
                new FileCache.Inputs.Builder()
                        .putFilePath("file", inputDir.getParentFile())
                        .build();
        inputs2 =
                new FileCache.Inputs.Builder()
                        .putFilePath("file", new File(inputDir.getPath() + "/.."))
                        .build();
        assertThat(inputs1.getKey()).isNotEqualTo(inputs2.getKey());

        // Test inputs with same file hash
        File fooFile = new File(inputDir, "fooInput");
        Files.write("Foo text", fooFile, StandardCharsets.UTF_8);
        inputs1 = new FileCache.Inputs.Builder().putFileHash("fileHash", fooFile).build();
        inputs2 = new FileCache.Inputs.Builder().putFileHash("fileHash", fooFile).build();
        assertThat(inputs1.getKey()).isEqualTo(inputs2.getKey());

        // Test inputs with different file hashes, same file path
        File barFile = new File(inputDir, "barInput");
        Files.write("Bar text", barFile, StandardCharsets.UTF_8);
        inputs1 =
                new FileCache.Inputs.Builder()
                        .putFilePath("file", fooFile)
                        .putFileHash("fileHash", fooFile)
                        .build();
        inputs2 =
                new FileCache.Inputs.Builder()
                        .putFilePath("file", fooFile)
                        .putFileHash("fileHash", barFile)
                        .build();
        assertThat(inputs1.getKey()).isNotEqualTo(inputs2.getKey());
    }
}

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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.repository.Revision;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Test cases for {@link FileCache}. */
public class FileCacheTest {

    @Rule public TemporaryFolder cacheFolder = new TemporaryFolder();

    @Rule public TemporaryFolder testFolder = new TemporaryFolder();

    @Test
    public void testSameInputSameOutput() throws IOException {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheFolder.getRoot());

        File outputFile = testFolder.newFile();
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("foo")).build();
        String[] fileContents = {"Foo line", "Bar line"};

        fileCache.createFile(
                outputFile,
                inputs,
                (newFile) -> Files.write(fileContents[0], newFile, StandardCharsets.UTF_8));

        assertEquals(0, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        assertEquals(fileContents[0], Files.toString(outputFile, StandardCharsets.UTF_8));

        FileUtils.delete(outputFile);

        fileCache.createFile(
                outputFile,
                inputs,
                (newFile) -> Files.write(fileContents[1], newFile, StandardCharsets.UTF_8));

        assertEquals(1, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        assertEquals(fileContents[0], Files.toString(outputFile, StandardCharsets.UTF_8));
    }

    @Test
    public void testSameInputDifferentOutputs() throws IOException {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheFolder.getRoot());

        File[] outputFiles = {testFolder.newFile(), testFolder.newFile()};
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("foo")).build();
        String[] fileContents = {"Foo line", "Bar line"};

        for (int i = 0; i < 2; i++) {
            File outputFile = outputFiles[i];
            String fileContent = fileContents[i];

            fileCache.createFile(
                    outputFile,
                    inputs,
                    (newFile) -> Files.write(fileContent, newFile, StandardCharsets.UTF_8));
        }

        assertEquals(1, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        assertEquals(fileContents[0], Files.toString(outputFiles[1], StandardCharsets.UTF_8));
    }

    @Test
    public void testDifferentInputsSameOutput() throws IOException {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheFolder.getRoot());

        File outputFile = testFolder.newFile();
        FileCache.Inputs[] inputList = {
            new FileCache.Inputs.Builder().putFilePath("file1", new File("foo")).build(),
            new FileCache.Inputs.Builder().putFilePath("file2", new File("bar")).build(),
        };
        String[] fileContents = {"Foo line", "Bar line"};

        for (int i = 0; i < 2; i++) {
            FileCache.Inputs inputs = inputList[i];
            String fileContent = fileContents[i];

            fileCache.createFile(
                    outputFile,
                    inputs,
                    (newFile) -> Files.write(fileContent, newFile, StandardCharsets.UTF_8));
        }

        assertEquals(0, fileCache.getHits());
        assertEquals(2, fileCache.getMisses());
        assertEquals(fileContents[1], Files.toString(outputFile, StandardCharsets.UTF_8));
    }

    @Test
    public void testDifferentInputsDifferentOutputs() throws IOException {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheFolder.getRoot());

        File[] outputFiles = {testFolder.newFile(), testFolder.newFile()};
        FileCache.Inputs[] inputList = {
            new FileCache.Inputs.Builder().putFilePath("file1", new File("foo")).build(),
            new FileCache.Inputs.Builder().putFilePath("file2", new File("bar")).build(),
        };
        String[] fileContents = {"Foo line", "Bar line"};

        for (int i = 0; i < 2; i++) {
            File outputFile = outputFiles[i];
            FileCache.Inputs inputs = inputList[i];
            String fileContent = fileContents[i];

            fileCache.createFile(
                    outputFile,
                    inputs,
                    (newFile) -> Files.write(fileContent, newFile, StandardCharsets.UTF_8));
        }

        assertEquals(0, fileCache.getHits());
        assertEquals(2, fileCache.getMisses());
        assertEquals(fileContents[1], Files.toString(outputFiles[1], StandardCharsets.UTF_8));
    }

    @Test
    public void testOutputToDirectory() throws IOException {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheFolder.getRoot());

        // Test same input different outputs
        File[] outputDirs = {testFolder.newFolder(), testFolder.newFolder()};
        Files.touch(new File(outputDirs[0], "tmp0"));
        Files.touch(new File(outputDirs[1], "tmp1"));

        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("foo")).build();
        String[] fileNames = {"fooFile", "barFile"};
        String[] fileContents = {"Foo line", "Bar line"};

        for (int i = 0; i < 2; i++) {
            File outputDir = outputDirs[i];
            String fileName = fileNames[i];
            String fileContent = fileContents[i];

            fileCache.createFile(
                    outputDir,
                    inputs,
                    (newFolder) -> {
                        FileUtils.mkdirs(newFolder);
                        Files.write(
                                fileContent, new File(newFolder, fileName), StandardCharsets.UTF_8);
                    });
        }

        assertEquals(1, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        assertEquals(1, outputDirs[0].list().length);
        assertEquals(1, outputDirs[1].list().length);
        assertEquals(
                fileContents[0],
                Files.toString(new File(outputDirs[0], fileNames[0]), StandardCharsets.UTF_8));
        assertEquals(
                fileContents[0],
                Files.toString(new File(outputDirs[1], fileNames[0]), StandardCharsets.UTF_8));
    }

    @Test
    public void testInvalidCacheDirectory() {
        try {
            FileCache.getInstanceWithSingleProcessLocking(new File("\0"));
            fail("expected IOException");
        } catch (IOException exception) {
            // Expected
        }
    }

    @Test
    public void testCacheDirectoryDoesNotAlreadyExist() throws IOException {
        FileCache fileCache =
                FileCache.getInstanceWithSingleProcessLocking(
                        new File(cacheFolder.getRoot(), "foo"));
        assertFalse(fileCache.getCacheDirectory().exists());

        File outputFile = testFolder.newFile();
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("foo")).build();
        String fileContent = "Foo line";

        fileCache.createFile(
                outputFile,
                inputs,
                (newFile) -> Files.write(fileContent, newFile, StandardCharsets.UTF_8));
        assertTrue(fileCache.getCacheDirectory().exists());

        assertEquals(0, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        assertEquals(fileContent, Files.toString(outputFile, StandardCharsets.UTF_8));
    }

    @Test
    public void testSameCacheDirectory() throws IOException {
        FileCache fileCache1 = FileCache.getInstanceWithSingleProcessLocking(cacheFolder.getRoot());
        FileCache fileCache2 = FileCache.getInstanceWithSingleProcessLocking(cacheFolder.getRoot());
        assertThat(fileCache1).isSameAs(fileCache2);
    }

    @Test
    public void testInconsistentLockingScope() throws IOException {
        FileCache.getInstanceWithSingleProcessLocking(cacheFolder.getRoot());
        try {
            FileCache.getInstanceWithInterProcessLocking(cacheFolder.getRoot());
            fail("expected IllegalStateException");
        } catch (IllegalStateException exception) {
            // Expected
        }
    }

    @Test
    public void testUnusualInput() throws IOException {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheFolder.getRoot());

        File outputFile = testFolder.newFile();
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("")).build();
        String fileContent = "Foo line";

        fileCache.createFile(
                outputFile,
                inputs,
                (newFile) -> Files.write(fileContent, newFile, StandardCharsets.UTF_8));

        assertEquals(0, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        assertEquals(fileContent, Files.toString(outputFile, StandardCharsets.UTF_8));
    }

    @Test
    public void testFileProducerPreconditions() throws IOException {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheFolder.getRoot());

        // Test the case when the output file already exists
        File outputFile = testFolder.newFile();
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("foo")).build();
        fileCache.createFile(
                outputFile,
                inputs,
                (newFile) -> {
                    assertFalse(newFile.exists());
                    assertTrue(newFile.getParentFile().exists());
                });

        // Test the case when the output file does not already exist
        outputFile = new File(testFolder.getRoot(), "tmp1/tmp2");
        inputs = new FileCache.Inputs.Builder().putFilePath("file", new File("bar")).build();
        fileCache.createFile(
                outputFile,
                inputs,
                (newFile) -> {
                    assertFalse(newFile.exists());
                    assertTrue(newFile.getParentFile().exists());
                });
    }

    @Test
    public void testOutputFileNotCreated() throws IOException {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheFolder.getRoot());

        File outputFile = testFolder.newFile();
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("foo")).build();

        fileCache.createFile(outputFile, inputs, (newFile) -> {});

        assertFalse(outputFile.exists());
        assertEquals(0, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
    }

    @Test
    public void testOutputFileNotCreatedIfNoFileExtension() throws IOException {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheFolder.getRoot());

        File outputFile = testFolder.newFile("x.bar");
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("foo")).build();
        String fileContent = "Foo line";

        fileCache.createFile(
                outputFile,
                inputs,
                (newFile) -> {
                    if (Files.getFileExtension(newFile.getName()).equals("bar")) {
                        Files.write(fileContent, newFile, StandardCharsets.UTF_8);
                    }
                });

        assertEquals(0, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        assertEquals(fileContent, Files.toString(outputFile, StandardCharsets.UTF_8));
    }

    @Test
    public void testOutputFileNotCreatedDueToException() throws IOException {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheFolder.getRoot());

        File outputFile = testFolder.newFile();
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("foo")).build();

        try {
            fileCache.createFile(
                    outputFile,
                    inputs,
                    (newFile) -> {
                        throw new IllegalStateException("Some exception");
                    });
            fail("expected IllegalStateException");
        } catch (IllegalStateException exception) {
            assertThat(exception).hasMessage("Some exception");
            assertThat(cacheFolder.getRoot().list().length == 0);
        }

        assertEquals(0, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
    }

    @Test
    public void testDeleteFileCache() throws IOException {
        FileCache fileCache = FileCache.getInstanceWithSingleProcessLocking(cacheFolder.getRoot());
        fileCache.delete();
        assertFalse(cacheFolder.getRoot().exists());
    }

    @Test
    public void testMultiThreadsWithSingleProcessLocking() throws IOException {
        testMultiThreadsWithSameCacheSameInputDifferentOutputs(
                FileCache.getInstanceWithSingleProcessLocking(cacheFolder.newFolder()));
        testMultiThreadsWithSameCacheDifferentInputsDifferentOutputs(
                FileCache.getInstanceWithSingleProcessLocking(cacheFolder.newFolder()));
        testMultiThreadsWithDifferentCaches(
                FileCache.getInstanceWithSingleProcessLocking(cacheFolder.newFolder()),
                FileCache.getInstanceWithSingleProcessLocking(cacheFolder.newFolder()));
    }

    @Test
    public void testMultiThreadsWithInterProcessLocking() throws IOException {
        testMultiThreadsWithSameCacheSameInputDifferentOutputs(
                FileCache.getInstanceWithInterProcessLocking(cacheFolder.newFolder()));
        testMultiThreadsWithSameCacheDifferentInputsDifferentOutputs(
                FileCache.getInstanceWithInterProcessLocking(cacheFolder.newFolder()));
        testMultiThreadsWithDifferentCaches(
                FileCache.getInstanceWithInterProcessLocking(cacheFolder.newFolder()),
                FileCache.getInstanceWithInterProcessLocking(cacheFolder.newFolder()));
    }

    private void testMultiThreadsWithSameCacheSameInputDifferentOutputs(
            @NonNull FileCache fileCache) throws IOException {
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("foo")).build();
        File[] outputFiles = {testFolder.newFile(), testFolder.newFile()};
        String fileContent = "Foo line";

        ConcurrencyTester<File, Void> tester = new ConcurrencyTester<>();
        prepareConcurrencyTest(
                tester,
                new FileCache[] {fileCache, fileCache},
                new FileCache.Inputs[] {inputs, inputs},
                outputFiles,
                new String[] {fileContent, fileContent});

        // Since we use the same input, we expect only one of the actions to be executed
        tester.assertThatOnlyOneActionIsExecuted();

        assertEquals(1, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        assertEquals(fileContent, Files.toString(outputFiles[0], StandardCharsets.UTF_8));
    }

    private void testMultiThreadsWithSameCacheDifferentInputsDifferentOutputs(
            @NonNull FileCache fileCache) throws IOException {
        FileCache.Inputs[] inputList = {
                new FileCache.Inputs.Builder().putFilePath("file1", new File("foo")).build(),
                new FileCache.Inputs.Builder().putFilePath("file2", new File("bar")).build(),
        };
        File[] outputFiles = {testFolder.newFile(), testFolder.newFile()};
        String[] fileContents = {"Foo line", "Bar line"};

        ConcurrencyTester<File, Void> tester = new ConcurrencyTester<>();
        prepareConcurrencyTest(
                tester,
                new FileCache[] {fileCache, fileCache},
                inputList,
                outputFiles,
                fileContents);

        // Since we use different inputs, the actions are allowed to run concurrently
        tester.assertThatActionsCanRunConcurrently();

        assertEquals(0, fileCache.getHits());
        assertEquals(2, fileCache.getMisses());
        assertEquals(fileContents[0], Files.toString(outputFiles[0], StandardCharsets.UTF_8));
        assertEquals(fileContents[1], Files.toString(outputFiles[1], StandardCharsets.UTF_8));
    }

    private void testMultiThreadsWithDifferentCaches(
            @NonNull FileCache fileCache1, @NonNull FileCache fileCache2) throws IOException {
        // Test same input different outputs, different caches
        FileCache[] fileCaches = {fileCache1, fileCache2};
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().putFilePath("file", new File("foo")).build();
        File[] outputFiles = {testFolder.newFile(), testFolder.newFile()};
        String[] fileContents = {"Foo line", "Bar line"};

        ConcurrencyTester<File, Void> tester = new ConcurrencyTester<>();
        prepareConcurrencyTest(
                tester,
                fileCaches,
                new FileCache.Inputs[] {inputs, inputs},
                outputFiles,
                fileContents);

        // Since we use different caches, even though we use the same input, the actions are allowed
        // to run concurrently
        tester.assertThatActionsCanRunConcurrently();

        assertEquals(0, fileCaches[0].getHits());
        assertEquals(1, fileCaches[0].getMisses());
        assertEquals(0, fileCaches[1].getHits());
        assertEquals(1, fileCaches[1].getMisses());
        assertEquals(fileContents[0], Files.toString(outputFiles[0], StandardCharsets.UTF_8));
        assertEquals(fileContents[1], Files.toString(outputFiles[1], StandardCharsets.UTF_8));
    }

    /** Performs a few steps common to the concurrency tests for the cache. */
    private void prepareConcurrencyTest(
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
                        fileCache.createFile(outputFile, inputs, anActionUnderTest::apply);
                    },
                    actionUnderTest);
        }
    }

    @Test
    public void testDoLockedMultiThreadsWithSingleProcessLocking() throws IOException {
        testDoLockedMultiThreadsWithSameFileExclusiveLock(
                FileCache.getInstanceWithSingleProcessLocking(cacheFolder.newFolder()));
        testDoLockedMultiThreadsWithSameFileSharedLock(
                FileCache.getInstanceWithSingleProcessLocking(cacheFolder.newFolder()));
        testDoLockedMultiThreadsWithDifferentFiles(
                FileCache.getInstanceWithSingleProcessLocking(cacheFolder.newFolder()));
    }

    @Test
    public void testDoLockedMultiThreadsWithInterProcessLocking() throws IOException {
        testDoLockedMultiThreadsWithSameFileExclusiveLock(
                FileCache.getInstanceWithInterProcessLocking(cacheFolder.newFolder()));
        testDoLockedMultiThreadsWithSameFileSharedLock(
                FileCache.getInstanceWithInterProcessLocking(cacheFolder.newFolder()));
        testDoLockedMultiThreadsWithDifferentFiles(
                FileCache.getInstanceWithInterProcessLocking(cacheFolder.newFolder()));
    }

    private void testDoLockedMultiThreadsWithSameFileExclusiveLock(@NonNull FileCache fileCache) {
        ConcurrencyTester<Void, Void> tester = new ConcurrencyTester<>();
        prepareDoLockedConcurrencyTest(
                tester,
                fileCache,
                new String[] {"foo", "foo", "foobar".substring(0, 3)},
                new FileCache.LockingType[] {
                    FileCache.LockingType.EXCLUSIVE,
                    FileCache.LockingType.EXCLUSIVE,
                    FileCache.LockingType.EXCLUSIVE
                });

        // Since we access the same file and use exclusive locks, the actions are not allowed to
        // run concurrently
        tester.assertThatActionsCannotRunConcurrently();
    }

    private void testDoLockedMultiThreadsWithSameFileSharedLock(@NonNull FileCache fileCache) {
        ConcurrencyTester<Void, Void> tester = new ConcurrencyTester<>();
        prepareDoLockedConcurrencyTest(
                tester,
                fileCache,
                new String[] {"foo", "foo", "foobar".substring(0, 3)},
                new FileCache.LockingType[] {
                    FileCache.LockingType.SHARED,
                    FileCache.LockingType.SHARED,
                    FileCache.LockingType.SHARED
                });

        // Since we access the same file and use shared locks, the actions are allowed to run
        // concurrently
        tester.assertThatActionsCanRunConcurrently();
    }

    private void testDoLockedMultiThreadsWithDifferentFiles(@NonNull FileCache fileCache) {
        ConcurrencyTester<Void, Void> tester = new ConcurrencyTester<>();
        prepareDoLockedConcurrencyTest(
                tester,
                fileCache,
                new String[] {"foo1", "foo2", "foo3"},
                new FileCache.LockingType[] {
                    FileCache.LockingType.EXCLUSIVE,
                    FileCache.LockingType.EXCLUSIVE,
                    FileCache.LockingType.EXCLUSIVE
                });

        // Since we access different files, even though we use exclusive locks, the actions are
        // allowed to run concurrently
        tester.assertThatActionsCanRunConcurrently();
    }

    /** Performs a few steps common to the concurrency tests for doLocked(). */
    private void prepareDoLockedConcurrencyTest(
            @NonNull ConcurrencyTester<Void, Void> tester,
            @NonNull FileCache fileCache,
            @NonNull String[] fileNames,
            @NonNull FileCache.LockingType[] lockingTypes) {
        IOExceptionFunction<Void, Void> actionUnderTest = (Void arg) -> {
            // Do some artificial work here
            assertEquals(1, 1);
            return null;
        };
        for (int i = 0; i < fileNames.length; i++) {
            String fileName = fileNames[i];
            FileCache.LockingType lockingType = lockingTypes[i];

            tester.addMethodInvocationFromNewThread(
                    (IOExceptionFunction<Void, Void> anActionUnderTest) -> {
                        fileCache.doLocked(
                                new File(testFolder.getRoot(), fileName),
                                lockingType,
                                () -> anActionUnderTest.apply(null));
                    },
                    actionUnderTest);
        }
    }

    @Test
    public void testInputsGetKey() throws IOException {
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
        assertThat(inputs.getKey()).isEqualTo("25dcc2247956f01b9dbdca420eff87c96aaf2874");

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
        inputFile = testFolder.newFile();
        Files.write("Some content", inputFile, StandardCharsets.UTF_8);
        inputs = new FileCache.Inputs.Builder().putFileHash("fileHash", inputFile).build();
        assertThat(inputs.toString())
                .isEqualTo("fileHash=9f1a6ecf74e9f9b1ae52e8eb581d420e63e8453a");
        assertThat(inputs.getKey()).isEqualTo("e670eb8fa97fdb081904581445aa57af7fb5274a");

        // Test unusual file path
        inputFile = new File("foo`-=[]\\\\;',./~!@#$%^&*()_+{}|:\\\"<>?");
        inputs = new FileCache.Inputs.Builder().putFilePath("file", inputFile).build();
        assertThat(inputs.toString()).isEqualTo("file=" + inputFile.getPath());
        assertThat(inputs.getKey()).isEqualTo("7f205499565a454d0186f34313e63281c7192a43");

        // Test empty file path
        inputFile = new File("");
        inputs = new FileCache.Inputs.Builder().putFilePath("file", inputFile).build();
        assertThat(inputs.toString()).isEqualTo("file=");
        assertThat(inputs.getKey()).isEqualTo("3fe9ece2d6113d8db5c0c5576cc1378823d839ab");

        // Test empty file content
        inputFile = testFolder.newFile();
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
                        .putFilePath("file", testFolder.getRoot().getParentFile())
                        .build();
        inputs2 =
                new FileCache.Inputs.Builder()
                        .putFilePath("file", new File(testFolder.getRoot().getPath() + "/.."))
                        .build();
        assertThat(inputs1.getKey()).isNotEqualTo(inputs2.getKey());

        // Test inputs with same file hash
        File fooFile = testFolder.newFile();
        Files.write("Some foo content", fooFile, StandardCharsets.UTF_8);
        inputs1 = new FileCache.Inputs.Builder().putFileHash("fileHash", fooFile).build();
        inputs2 = new FileCache.Inputs.Builder().putFileHash("fileHash", fooFile).build();
        assertThat(inputs1.getKey()).isEqualTo(inputs2.getKey());

        // Test inputs with different file hashes, same file path
        File barFile = testFolder.newFile();
        Files.write("Some bar content", barFile, StandardCharsets.UTF_8);
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

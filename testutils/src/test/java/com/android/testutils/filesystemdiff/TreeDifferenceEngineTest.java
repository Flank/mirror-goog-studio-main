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

package com.android.testutils.filesystemdiff;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.utils.StdLogger;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class TreeDifferenceEngineTest {
    public static class FileSystem implements AutoCloseable {
        private Path mPath;
        private Boolean mSupportsSymbolicLinks;

        public FileSystem(Path root) {
            mPath = root;
        }

        public Path getPath() {
            return mPath;
        }

        public boolean supportsSymbolicLinks() {
            if (mSupportsSymbolicLinks == null) {
                mSupportsSymbolicLinks = supportsSymbolicLinkWorker();
            }
            return mSupportsSymbolicLinks;
        }

        private boolean supportsSymbolicLinkWorker() {
            createFile("myfile.txt");
            try {
                try {
                    Files.createSymbolicLink(mPath.resolve("test-link"),
                            Paths.get("myfile.txt"));
                } catch (FileSystemException e) {
                    return false;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                delete("test-link");
                return true;
            } finally {
                delete("myfile.txt");
            }
        }

        public Path createFile(String path) {
            try {
                Files.createDirectories(mPath.resolve(path).getParent());
                Path result = mPath.resolve(path);
                Files.createFile(result);
                return result;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public Path createFile(String path, String content) {
            try {
                Path result = createFile(path);
                try(PrintWriter writer = new PrintWriter(result.toString())) {
                    writer.write(content);
                }
                return result;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void createDirectory(String child) {
            try {
                Files.createDirectories(mPath.resolve(child).getParent());
                Files.createDirectory(mPath.resolve(child));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void createSymbolicLink(String path, String target) {
            try {
                Files.createDirectories(mPath.resolve(path).getParent());
                Files.createSymbolicLink(mPath.resolve(path), Paths.get(target));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void delete(String file) {
            try {
                Files.delete(mPath.resolve(file));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public boolean exists(String file) {
            return Files.exists(mPath.resolve(file));
        }

        @Override
        public void close() throws Exception {
            Files.walkFileTree(mPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    Files.delete(file);
                    return super.visitFile(file, attrs);
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                        throws IOException {
                    Files.delete(dir);
                    return super.postVisitDirectory(dir, exc);
                }
            });
        }
    }

    public static FileSystem createEmptyFileSystem() throws IOException {
        Path root = Files.createTempDirectory("filesystemtest-");
        return new FileSystem(root);
    }

    public static FileSystem createTestFileSystem() throws IOException {
        Path root = Files.createTempDirectory("filesystemtest-");
        FileSystem fs = new FileSystem(root);

        fs.createDirectory("child");
        fs.createDirectory("child/subchild1");
        fs.createFile("child/childfile");
        if (fs.supportsSymbolicLinks()) {
            fs.createSymbolicLink("child/file2", "childfile");
        } else {
            fs.createFile("child/file2");
        }
        fs.createDirectory("child/subchild2");
        fs.createDirectory("child2");
        fs.createFile("file");
        if (fs.supportsSymbolicLinks()) {
            fs.createSymbolicLink("file2", "file");
        } else {
            fs.createFile("file2");
        }

        return fs;
    }

    private static void scriptShouldMakeFileSystemsIdentical(
            FileSystem fs1, FileSystemEntry fs2, Script script) {
        // Both file systems should be identical after script execution
        script.execute(new StdLogger(StdLogger.Level.INFO));

        FileSystemEntry fs1After = TreeBuilder.buildFromFileSystem(fs1.mPath);
        Script newScript = TreeDifferenceEngine.computeEditScript(fs1After, fs2);
        assertEquals(0, newScript.getActions().size());
    }

    @Test
    public void comparingEmptyFileSystemsShouldHaveNoEdits() throws Exception {
        try (FileSystem fs1 = createEmptyFileSystem();
             FileSystem fs2 = createEmptyFileSystem()) {
            FileSystemEntry rootfs1 = TreeBuilder.buildFromFileSystem(fs1.getPath());
            FileSystemEntry rootfs2 = TreeBuilder.buildFromFileSystem(fs2.getPath());

            Script script = TreeDifferenceEngine.computeEditScript(rootfs1, rootfs2);

            assertEquals(0, script.getActions().size());

            scriptShouldMakeFileSystemsIdentical(fs1, rootfs2, script);
        }
    }

    @Test
    public void comparingIdenticalFileSystemsShouldHaveNoEdits() throws Exception {
        try (FileSystem fs1 = createTestFileSystem();
             FileSystem fs2 = createTestFileSystem()) {
            FileSystemEntry rootfs1 = TreeBuilder.buildFromFileSystem(fs1.getPath());
            FileSystemEntry rootfs2 = TreeBuilder.buildFromFileSystem(fs2.getPath());

            Script script = TreeDifferenceEngine.computeEditScript(rootfs1, rootfs2);

            assertEquals(0, script.getActions().size());

            scriptShouldMakeFileSystemsIdentical(fs1, rootfs2, script);
        }
    }

    @Test
    public void comparingEmptyToFullFileSystemsShouldHaveCreateEdits() throws Exception {
        try (FileSystem fs1 = createEmptyFileSystem();
             FileSystem fs2 = createTestFileSystem()) {
            FileSystemEntry rootfs1 = TreeBuilder.buildFromFileSystem(fs1.getPath());
            FileSystemEntry rootfs2 = TreeBuilder.buildFromFileSystem(fs2.getPath());

            Script script = TreeDifferenceEngine.computeEditScript(rootfs1, rootfs2);

            assertEquals(4, script.getActions().size());
            assertTrue(script.getActions().stream().allMatch(
                    a -> a instanceof CreateDirectoryAction
                            || a instanceof CreateFileAction
                            || a instanceof CreateSymbolicLinkAction));

            scriptShouldMakeFileSystemsIdentical(fs1, rootfs2, script);
        }
    }

    @Test
    public void comparingFullToEmptyFileSystemsShouldHaveDeleteEdits() throws Exception {
        try (FileSystem fs1 = createTestFileSystem();
             FileSystem fs2 = createEmptyFileSystem()) {
            FileSystemEntry rootfs1 = TreeBuilder.buildFromFileSystem(fs1.getPath());
            FileSystemEntry rootfs2 = TreeBuilder.buildFromFileSystem(fs2.getPath());

            Script script = TreeDifferenceEngine.computeEditScript(rootfs1, rootfs2);

            assertEquals(4, script.getActions().size());
            assertTrue(script.getActions().stream().allMatch(
                    a -> a instanceof DeleteDirectoryAction
                            || a instanceof DeleteFileAction
                            || a instanceof DeleteSymbolicLinkAction));

            scriptShouldMakeFileSystemsIdentical(fs1, rootfs2, script);
        }
    }

    @Test
    public void comparingSymbolicLinksWithDifferentTargetsShouldHaveEdits() throws Exception {
        try (FileSystem fs1 = createTestFileSystem();
             FileSystem fs2 = createTestFileSystem()) {
            assumeTrue(
                    "Platform must support symbolic links, or test must be run as Admin on Windows",
                    fs1.supportsSymbolicLinks());

            fs1.createSymbolicLink("foo", "child");
            fs2.createSymbolicLink("foo", "child2");

            FileSystemEntry rootfs1 = TreeBuilder.buildFromFileSystem(fs1.getPath());
            FileSystemEntry rootfs2 = TreeBuilder.buildFromFileSystem(fs2.getPath());

            Script script = TreeDifferenceEngine.computeEditScript(rootfs1, rootfs2);

            assertEquals(2, script.getActions().size());
            assertTrue(script.getActions().stream().anyMatch(
                    a -> a instanceof DeleteSymbolicLinkAction));
            assertTrue(script.getActions().stream().anyMatch(
                    a -> a instanceof CreateSymbolicLinkAction));

            scriptShouldMakeFileSystemsIdentical(fs1, rootfs2, script);
        }
    }

    @Test
    public void comparingFromLinkDefinitionsShouldFindEditsWork() throws Exception {
        try (FileSystem fs1 = createEmptyFileSystem();
             FileSystem fs2 = createEmptyFileSystem()) {
            assumeTrue(
                    "Platform must support symbolic links, or test must be run as Admin on Windows",
                    fs1.supportsSymbolicLinks());

            fs1.createSymbolicLink("dir1", fs1.getPath().toString());

            FileSystemEntry rootfs1 = TreeBuilder.buildFromFileSystem(fs1.getPath());

            List<SymbolicLinkDefinition> links = new ArrayList<>();
            links.add(new SymbolicLinkDefinition(fs2.getPath().resolve("dir1"), fs2.getPath()));
            FileSystemEntry rootfs2 = TreeBuilder.buildFromSymbolicLinkDefinitions(
                    fs2.getPath(), links);

            Script script = TreeDifferenceEngine.computeEditScript(rootfs1, rootfs2);

            assertEquals(2, script.getActions().size());
            assertTrue(script.getActions().stream().anyMatch(
                    a -> a instanceof DeleteSymbolicLinkAction));
            assertTrue(script.getActions().stream().anyMatch(
                    a -> a instanceof CreateSymbolicLinkAction));

            scriptShouldMakeFileSystemsIdentical(fs1, rootfs2, script);
        }
    }

    @Test
    public void comparingFromIdenticalLinkDefinitionsShouldFindNoEditsWork() throws Exception {
        try (FileSystem fs1 = createEmptyFileSystem();
             FileSystem fs2 = createEmptyFileSystem()) {
            assumeTrue(
                    "Platform must support symbolic links, or test must be run as Admin on Windows",
                    fs1.supportsSymbolicLinks());

            fs1.createSymbolicLink("dir1/dir2/dir3", fs1.getPath().toString());

            FileSystemEntry rootfs1 = TreeBuilder.buildFromFileSystem(fs1.getPath());

            List<SymbolicLinkDefinition> links = new ArrayList<>();
            links.add(new SymbolicLinkDefinition(fs2.getPath().resolve("dir1/dir2/dir3"), fs1.getPath()));
            FileSystemEntry rootfs2 = TreeBuilder.buildFromSymbolicLinkDefinitions(
                    fs2.getPath(), links);

            Script script = TreeDifferenceEngine.computeEditScript(rootfs1, rootfs2);

            assertEquals(0, script.getActions().size());

            scriptShouldMakeFileSystemsIdentical(fs1, rootfs2, script);
        }
    }
}

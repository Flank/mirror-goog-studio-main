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

package com.android.repository.io;

import static com.android.testutils.file.InMemoryFileSystems.createInMemoryFileSystem;
import static com.android.testutils.file.InMemoryFileSystems.getExistingFiles;
import static com.android.testutils.file.InMemoryFileSystems.getPlatformSpecificPath;
import static com.android.testutils.file.InMemoryFileSystems.recordExistingFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.MockFileOp;
import com.android.testutils.file.DelegatingFileSystemProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Assume;
import org.junit.Test;

/**
 * Tests for FileOpUtils
 */
public class FileOpUtilsTest {

    @Test
    public void makeRelativeNonWindows() throws Exception {
        Assume.assumeFalse(FileOpUtils.isWindows());
        assertEquals("dir3", FileOpUtils.makeRelativeImpl("/dir1/dir2", "/dir1/dir2/dir3", "/"));

        assertEquals(
                "../../../dir3",
                FileOpUtils.makeRelativeImpl("/dir1/dir2/dir4/dir5/dir6", "/dir1/dir2/dir3", "/"));

        assertEquals(
                "dir3/dir4/dir5/dir6",
                FileOpUtils.makeRelativeImpl("/dir1/dir2/", "/dir1/dir2/dir3/dir4/dir5/dir6", "/"));

        // case-sensitive on non-Windows.
        assertEquals(
                "../DIR2/dir3/DIR4/dir5/DIR6",
                FileOpUtils.makeRelativeImpl("/dir1/dir2/", "/dir1/DIR2/dir3/DIR4/dir5/DIR6", "/"));

        // same path: empty result.
        assertEquals("", FileOpUtils.makeRelativeImpl("/dir1/dir2/dir3", "/dir1/dir2/dir3", "/"));
    }

    @Test
    public void makeRelativeWindows() throws Exception {
        Assume.assumeTrue(FileOpUtils.isWindows());
        assertEquals(
                "..\\..\\..\\dir3",
                FileOpUtils.makeRelativeImpl(
                        "C:\\dir1\\dir2\\dir4\\dir5\\dir6", "C:\\dir1\\dir2\\dir3", "\\"));

        // not case-sensitive on Windows, results will be mixed.
        assertEquals(
                "dir3/DIR4/dir5/DIR6",
                FileOpUtils.makeRelativeImpl("/DIR1/dir2/", "/dir1/DIR2/dir3/DIR4/dir5/DIR6", "/"));

        // UNC path on Windows
        assertEquals(
                "..\\..\\..\\dir3",
                FileOpUtils.makeRelativeImpl(
                        "\\\\myserver.domain\\dir1\\dir2\\dir4\\dir5\\dir6",
                        "\\\\myserver.domain\\dir1\\dir2\\dir3",
                        "\\"));

        // different drive letters are not supported
        try {
            FileOpUtils.makeRelativeImpl(
                    "C:\\dir1\\dir2\\dir4\\dir5\\dir6", "D:\\dir1\\dir2\\dir3", "\\");
            fail("Expected: IOException. Actual: no exception.");
        } catch (IOException e) {
            assertEquals("makeRelative: incompatible drive letters", e.getMessage());
        }
    }

    @Test
    public void recursiveCopySuccess() throws Exception {
        MockFileOp fop = new MockFileOp();
        File s1 = new File("/root/src/a");
        File s2 = new File("/root/src/foo/a");
        File s3 = new File("/root/src/foo/b");
        File s4 = new File("/root/src/foo/bar/a");
        File s5 = new File("/root/src/baz/c");

        fop.recordExistingFile(s1.getPath(), "content1");
        fop.recordExistingFile(s2.getPath(), "content2");
        fop.recordExistingFile(s3.getPath(), "content3");
        fop.recordExistingFile(s4.getPath(), "content4");
        fop.recordExistingFile(s5.getPath(), "content5");

        FileOpUtils.recursiveCopy(new File("/root/src/"), new File("/root/dest"), fop,
                new FakeProgressIndicator());

        assertEquals("content1", new String(fop.getContent(new File("/root/dest/a"))));
        assertEquals("content2", new String(fop.getContent(new File("/root/dest/foo/a"))));
        assertEquals("content3", new String(fop.getContent(new File("/root/dest/foo/b"))));
        assertEquals("content4", new String(fop.getContent(new File("/root/dest/foo/bar/a"))));
        assertEquals("content5", new String(fop.getContent(new File("/root/dest/baz/c"))));

        // Also verify the sources are unchanged
        assertEquals("content1", new String(fop.getContent(s1)));
        assertEquals("content2", new String(fop.getContent(s2)));
        assertEquals("content3", new String(fop.getContent(s3)));
        assertEquals("content4", new String(fop.getContent(s4)));
        assertEquals("content5", new String(fop.getContent(s5)));

        // Finally verify that nothing else is created
        assertEquals(10, fop.getExistingFiles().size());
    }

    @Test
    public void recursiveCopyAlreadyExists() {
        MockFileOp fop = new MockFileOp();
        File s1 = new File("/root/src/a");
        File s2 = new File("/root/src/foo/a");

        File d1 = new File("/root/dest/b");

        fop.recordExistingFile(s1.getPath(), "content1");
        fop.recordExistingFile(s2.getPath(), "content2");
        fop.recordExistingFile(d1.getPath(), "content3");

        try {
            FileOpUtils.recursiveCopy(new File("/root/src/"), new File("/root/dest"), fop,
                    new FakeProgressIndicator());
            fail("Expected exception");
        } catch (IOException expected) {
        }

        // verify that nothing is changed
        assertEquals("content3", new String(fop.getContent(d1)));
        assertEquals("content1", new String(fop.getContent(s1)));
        assertEquals("content2", new String(fop.getContent(s2)));

        // Finally verify that nothing else is created
        assertEquals(3, fop.getExistingFiles().size());
    }

    @Test
    public void recursiveCopyMerge() throws Exception {
        MockFileOp fop = new MockFileOp();
        File s1 = new File("/root/src/a");
        File s2 = new File("/root/src/foo/a");
        File s3 = new File("/root/src/foo/b");

        File d1 = new File("/root/dest/b");
        File d2 = new File("/root/dest/foo/b");
        File d3 = new File("/root/dest/bar/b");

        fop.recordExistingFile(s1.getPath(), "content1");
        fop.recordExistingFile(s2.getPath(), "content2");
        fop.recordExistingFile(s3.getPath(), "content3");
        fop.recordExistingFile(d1.getPath(), "content4");
        fop.recordExistingFile(d2.getPath(), "content5");
        fop.recordExistingFile(d3.getPath(), "content6");

        FileOpUtils.recursiveCopy(new File("/root/src/"), new File("/root/dest"), true, fop,
                new FakeProgressIndicator());

        // Verify the existing dest files
        assertEquals("content4", new String(fop.getContent(d1)));
        assertEquals("content5", new String(fop.getContent(d2)));
        assertEquals("content6", new String(fop.getContent(d3)));

        // Verify the new dest files
        assertEquals("content1", new String(fop.getContent(new File("/root/dest/a"))));
        assertEquals("content2", new String(fop.getContent(new File("/root/dest/foo/a"))));

        // Finally verify that nothing else is created
        assertEquals(8, fop.getExistingFiles().size());
    }

    @Test
    public void recursiveCopyMergeFailed() {
        MockFileOp fop = new MockFileOp();
        File s1 = new File("/root/src/a");
        File s2 = new File("/root/src/foo/a");

        File d1 = new File("/root/dest/a/b");

        fop.recordExistingFile(s1.getPath(), "content1");
        fop.recordExistingFile(s2.getPath(), "content2");
        fop.recordExistingFile(d1.getPath(), "content3");

        try {
            FileOpUtils.recursiveCopy(new File("/root/src/"), new File("/root/dest"), true, fop,
                    new FakeProgressIndicator());
            fail();
        } catch (IOException expected) {
        }

        // Ensure nothing changed
        assertEquals("content1", new String(fop.getContent(s1)));
        assertEquals("content2", new String(fop.getContent(s2)));
        assertEquals("content3", new String(fop.getContent(d1)));

        // Finally verify that nothing else is created
        assertEquals(3, fop.getExistingFiles().size());
    }

    @Test
    public void safeRecursiveOverwriteSimpleMove() throws Exception {
        FileSystem fs = createInMemoryFileSystem();

        Path src = fs.getPath(getPlatformSpecificPath("/root/src"));
        recordExistingFile(src.resolve("a"), "content1");
        recordExistingFile(src.resolve("foo/a"), "content2");
        recordExistingFile(src.resolve("foo/b"), "content3");
        recordExistingFile(src.resolve("foo/bar/a"), "content4");
        recordExistingFile(src.resolve("baz/c"), "content5");

        Path dest = fs.getPath(getPlatformSpecificPath("/root/dest"));
        FileOpUtils.safeRecursiveOverwrite(src, dest, new FakeProgressIndicator());

        assertEquals("content1", new String(Files.readAllBytes(dest.resolve("a"))));
        assertEquals("content2", new String(Files.readAllBytes(dest.resolve("foo/a"))));
        assertEquals("content3", new String(Files.readAllBytes(dest.resolve("foo/b"))));
        assertEquals("content4", new String(Files.readAllBytes(dest.resolve("foo/bar/a"))));
        assertEquals("content5", new String(Files.readAllBytes(dest.resolve("baz/c"))));

        // Verify that the original files are gone
        assertEquals(5, getExistingFiles(fs).size());
    }

    @Test
    public void safeRecursiveOverwriteActuallyOverwrite() throws Exception {
        FileSystem fs = createInMemoryFileSystem();
        Path s1 = fs.getPath(getPlatformSpecificPath("/root/src/a"));
        Path s2 = fs.getPath(getPlatformSpecificPath("/root/src/foo/a"));
        Path s3 = fs.getPath(getPlatformSpecificPath("/root/src/foo/bar/a"));

        Path d1 = fs.getPath(getPlatformSpecificPath("/root/dest/a"));
        Path d2 = fs.getPath(getPlatformSpecificPath("/root/dest/foo/b"));

        recordExistingFile(s1, "content1");
        recordExistingFile(s2, "content2");
        recordExistingFile(s3, "content3");
        recordExistingFile(d1, "content4");
        recordExistingFile(d2, "content5");

        FileOpUtils.safeRecursiveOverwrite(
                s1.getParent(), d1.getParent(), new FakeProgressIndicator());

        Path s1Moved = fs.getPath(getPlatformSpecificPath("/root/dest/a"));
        Path s2Moved = fs.getPath(getPlatformSpecificPath("/root/dest/foo/a"));
        Path s3Moved = fs.getPath(getPlatformSpecificPath("/root/dest/foo/bar/a"));

        assertEquals("content1", new String(Files.readAllBytes(s1Moved)));
        assertEquals("content2", new String(Files.readAllBytes(s2Moved)));
        assertEquals("content3", new String(Files.readAllBytes(s3Moved)));

        // Verify that the original files are gone
        assertEquals(3, getExistingFiles(fs).size());
    }

    @Test
    public void safeRecursiveOverwriteCantMoveDest() throws Exception {
        Path[] destRef = new Path[1];
        final AtomicBoolean hitRename = new AtomicBoolean(false);
        FileSystem fs =
                new DelegatingFileSystemProvider(createInMemoryFileSystem()) {
                    @Override
                    public void move(
                            @NonNull Path source,
                            @NonNull Path target,
                            @NonNull CopyOption... options)
                            throws IOException {
                        if (source.equals(destRef[0])) {
                            hitRename.set(true);
                            throw new IOException("can't move");
                        }
                        super.move(source, target);
                    }
                }.getFileSystem();
        Path src = fs.getPath(getPlatformSpecificPath("/root/src"));
        Path s1 = src.resolve("a");
        Path s2 = src.resolve("foo/a");
        Path dest = fs.getPath(getPlatformSpecificPath("/root/dest"));
        Path d1 = dest.resolve("b");
        destRef[0] = dest;

        recordExistingFile(s1, "content1");
        recordExistingFile(s2, "content2");
        recordExistingFile(d1, "content3");

        FileOpUtils.safeRecursiveOverwrite(src, dest, new FakeProgressIndicator());

        // Make sure we tried and failed to move the dest.
        assertTrue(hitRename.get());

        // verify the files were moved
        assertEquals("content1", new String(Files.readAllBytes(dest.resolve("a"))));
        assertEquals("content2", new String(Files.readAllBytes(dest.resolve("foo/a"))));

        // Finally verify that nothing else is created
        assertEquals(2, getExistingFiles(fs).size());
    }

    @Test
    public void safeRecursiveOverwriteCantDeleteDest() throws Exception {
        Path[] d1Ref = new Path[1];

        FileSystem fs =
                new DelegatingFileSystemProvider(createInMemoryFileSystem()) {
                    @Override
                    public void move(
                            @NonNull Path source,
                            @NonNull Path target,
                            @NonNull CopyOption... options)
                            throws IOException {
                        if (source.equals(d1Ref[0].getParent())) {
                            throw new IOException("can't move");
                        } else {
                            super.move(source, target);
                        }
                    }

                    @Override
                    public void delete(@NonNull Path oldFile) throws IOException {
                        if (oldFile.equals(d1Ref[0].getParent())) {
                            throw new IOException("can't delete");
                        } else {
                            super.delete(oldFile);
                        }
                    }
                }.getFileSystem();
        Path s1 = fs.getPath(getPlatformSpecificPath("/root/src/a"));
        Path s2 = fs.getPath(getPlatformSpecificPath("/root/src/foo/a"));
        Path d1 = fs.getPath(getPlatformSpecificPath("/root/dest/b"));
        d1Ref[0] = d1;

        recordExistingFile(s1, "content1");
        recordExistingFile(s2, "content2");
        recordExistingFile(d1, "content3");

        try {
            FileOpUtils.safeRecursiveOverwrite(
                    s1.getParent(), d1.getParent(), new FakeProgressIndicator());
            fail("Expected exception");
        }
        catch (Exception expected) {}

        // Ensure nothing changed
        assertEquals("content1", new String(Files.readAllBytes(s1)));
        assertEquals("content2", new String(Files.readAllBytes(s2)));
        assertEquals("content3", new String(Files.readAllBytes(d1)));

        // Finally verify that nothing else is created
        assertEquals(3, getExistingFiles(fs).size());
    }

    @Test
    public void safeRecursiveOverwriteCantDeleteDestPartial() throws Exception {
        AtomicBoolean deletedSomething = new AtomicBoolean(false);
        FileSystem fs =
                new DelegatingFileSystemProvider(createInMemoryFileSystem()) {
                    @Override
                    public void move(
                            @NonNull Path source,
                            @NonNull Path target,
                            @NonNull CopyOption... options)
                            throws IOException {
                        if (source.toString().equals(getPlatformSpecificPath("/root/dest"))) {
                            throw new IOException("can't move");
                        } else {
                            super.move(source, target);
                        }
                    }

                    @Override
                    public void delete(@NonNull Path oldFile) throws IOException {
                        if (oldFile.toString().startsWith(getPlatformSpecificPath("/root/dest/"))) {
                            if (deletedSomething.compareAndSet(false, true)) {
                                super.delete(oldFile);
                            }
                            throw new IOException("can't delete");
                        }
                        super.delete(oldFile);
                    }
                }.getFileSystem();

        Path s1 = fs.getPath(getPlatformSpecificPath("/root/src/a"));
        Path s2 = fs.getPath(getPlatformSpecificPath("/root/src/foo/a"));
        Path d1 = fs.getPath(getPlatformSpecificPath("/root/dest/b"));
        Path d2 = fs.getPath(getPlatformSpecificPath("/root/dest/bar/b"));

        recordExistingFile(s1, "content1");
        recordExistingFile(s2, "content2");
        recordExistingFile(d1, "content3");
        recordExistingFile(d2, "content4");

        try {
            FileOpUtils.safeRecursiveOverwrite(
                    s1.getParent(), d1.getParent(), new FakeProgressIndicator());
            fail("Expected exception");
        }
        catch (IOException expected) {}

        assertTrue(deletedSomething.get());
        // Ensure nothing changed
        assertEquals("content1", new String(Files.readAllBytes(s1)));
        assertEquals("content2", new String(Files.readAllBytes(s2)));
        assertEquals("content3", new String(Files.readAllBytes(d1)));
        assertEquals("content4", new String(Files.readAllBytes(d2)));

        // Finally verify that nothing else is created
        assertEquals(4, getExistingFiles(fs).size());
    }

    @Test
    public void safeRecursiveOverwriteCantWrite() throws Exception {
        Path[] s1Ref = new Path[1];
        Path[] d1Ref = new Path[1];
        FileSystem fs =
                new DelegatingFileSystemProvider(createInMemoryFileSystem()) {
                    @Override
                    public void copy(
                            @NonNull Path source,
                            @NonNull Path target,
                            @NonNull CopyOption... options)
                            throws IOException {
                        if (source.equals(s1Ref[0]) && target.equals(d1Ref[0])) {
                            throw new IOException("failed to copy");
                        }
                        super.copy(source, target);
                    }

                    @Override
                    public void move(
                            @NonNull Path source,
                            @NonNull Path target,
                            @NonNull CopyOption... options)
                            throws IOException {
                        if (source.equals(s1Ref[0].getParent())
                                && target.equals(d1Ref[0].getParent())) {
                            throw new IOException("failed to move");
                        } else {
                            super.move(source, target);
                        }
                    }
                }.getFileSystem();

        Path s1 = fs.getPath(getPlatformSpecificPath("/root/src/a"));
        Path s2 = fs.getPath(getPlatformSpecificPath("/root/src/foo/a"));
        Path d1 = fs.getPath(getPlatformSpecificPath("/root/dest/a"));

        s1Ref[0] = s1;
        d1Ref[0] = d1;

        recordExistingFile(s1, "content1");
        recordExistingFile(s2, "content2");
        recordExistingFile(d1, "content3");

        try {
            FileOpUtils.safeRecursiveOverwrite(
                    s1.getParent(), d1.getParent(), new FakeProgressIndicator());
            fail("Expected exception");
        }
        catch (IOException expected) {}

        // Ensure nothing changed
        assertEquals("content1", new String(Files.readAllBytes(s1)));
        assertEquals("content2", new String(Files.readAllBytes(s2)));
        assertEquals("content3", new String(Files.readAllBytes(d1)));

        // Finally verify that nothing else is created
        assertEquals(3, getExistingFiles(fs).size());
    }

    @Test
    public void safeRecursiveOverwriteCantDeleteDestThenCantMoveBack() throws Exception {

        Path[] d1Ref = new Path[1];

        FileSystem fs =
                new DelegatingFileSystemProvider(createInMemoryFileSystem()) {
                    @Override
                    public void move(
                            @NonNull Path source,
                            @NonNull Path target,
                            @NonNull CopyOption... options)
                            throws IOException {
                        if (source.equals(d1Ref[0].getParent())) {
                            throw new IOException("can't move");
                        } else {
                            super.move(source, target);
                        }
                    }

                    @Override
                    public void delete(@NonNull Path path) throws IOException {
                        if (path.equals(d1Ref[0].getParent())) {
                            throw new IOException("can't delete");
                        } else {
                            super.delete(path);
                        }
                    }

                    @Override
                    public void copy(
                            @NonNull Path source,
                            @NonNull Path target,
                            @NonNull CopyOption... options)
                            throws IOException {
                        if (target.equals(d1Ref[0])) {
                            throw new IOException("failed to copy");
                        }
                        super.copy(source, target);
                    }
                }.getFileSystem();

        Path d1 = fs.getPath(getPlatformSpecificPath("/root/dest/b"));
        Path d2 = fs.getPath(getPlatformSpecificPath("/root/dest/foo/b"));
        Path s1 = fs.getPath(getPlatformSpecificPath("/root/src/a"));
        Path s2 = fs.getPath(getPlatformSpecificPath("/root/src/foo/a"));

        d1Ref[0] = d1;

        recordExistingFile(s1, "content1");
        recordExistingFile(s2, "content2");
        recordExistingFile(d1, "content3");
        recordExistingFile(d2, "content4");

        FakeProgressIndicator progress = new FakeProgressIndicator();
        try {
            FileOpUtils.safeRecursiveOverwrite(s1.getParent(), d1.getParent(), progress);
            fail("Expected exception");
        }
        catch (IOException expected) {}

        final String marker = "available at ";
        String message = progress.getWarnings().stream()
                .filter(in -> in.contains(marker))
                .findAny()
                .get();

        String backupPath = message
                .substring(message.indexOf(marker) + marker.length(), message.indexOf('\n'));

        // Ensure backup is correct
        assertEquals("content3", new String(Files.readAllBytes(fs.getPath(backupPath, "b"))));
        assertEquals("content4", new String(Files.readAllBytes(fs.getPath(backupPath, "foo/b"))));
    }

    @Test
    public void safeRecursiveOverwriteCantCopyThenCantRestore() throws Exception {

        Path[] d1Ref = new Path[1];

        FileSystem fs =
                new DelegatingFileSystemProvider(createInMemoryFileSystem()) {
                    @Override
                    public void move(
                            @NonNull Path source,
                            @NonNull Path target,
                            @NonNull CopyOption... options)
                            throws IOException {
                        if (target.equals(d1Ref[0].getParent())) {
                            throw new IOException("can't move");
                        } else {
                            super.move(source, target);
                        }
                    }

                    @Override
                    public void copy(
                            @NonNull Path source,
                            @NonNull Path target,
                            @NonNull CopyOption... options)
                            throws IOException {
                        if (target.equals(d1Ref[0])) {
                            throw new IOException("failed to copy");
                        }
                        super.copy(source, target);
                    }
                }.getFileSystem();

        Path dest = fs.getPath(getPlatformSpecificPath("/root/dest"));
        Path src = fs.getPath(getPlatformSpecificPath("/root/src"));
        Path d1 = dest.resolve("a");
        Path d2 = dest.resolve("foo/b");
        Path s1 = src.resolve("a");
        Path s2 = src.resolve("foo/a");

        d1Ref[0] = d1;

        recordExistingFile(s1, "content1");
        recordExistingFile(s2, "content2");
        recordExistingFile(d1, "content3");
        recordExistingFile(d2, "content4");

        FakeProgressIndicator progress = new FakeProgressIndicator();
        try {
            FileOpUtils.safeRecursiveOverwrite(src, dest, progress);
            fail("Expected exception");
        }
        catch (IOException expected) {}

        final String marker = "available at ";
        String message = progress.getWarnings().stream()
                .filter(in -> in.contains(marker))
                .findAny()
                .get();

        String backupPath = message
                .substring(message.indexOf(marker) + marker.length(), message.indexOf('\n'));

        // Ensure backup is correct
        assertEquals("content3", new String(Files.readAllBytes(fs.getPath(backupPath, "a"))));
        assertEquals("content4", new String(Files.readAllBytes(fs.getPath(backupPath, "foo/b"))));
    }
}

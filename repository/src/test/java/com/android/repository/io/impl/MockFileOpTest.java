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

package com.android.repository.io.impl;

import static org.junit.Assert.assertArrayEquals;

import com.android.repository.testframework.MockFileOp;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import junit.framework.TestCase;

/**
 * Unit-test for the {@link MockFileOp}, which is a mock of {@link FileOpImpl} that doesn't
 * touch the file system. Just testing the test.
 */
public class MockFileOpTest extends TestCase {

    private MockFileOp m;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        m = new MockFileOp();
    }

    private File createFile(String...segments) {
        File f = null;
        for (String segment : segments) {
            if (f == null) {
                f = new File(segment);
            } else {
                f = new File(f, segment);
            }
        }

        return f;
    }

    public void testIsFile() {
        File f1 = createFile("/dir1", "file1");
        assertFalse(m.isFile(f1));

        m.recordExistingFile("/dir1/file1");
        assertTrue(m.isFile(f1));

        assertEquals(
                ImmutableList.of(m.getPlatformSpecificPath("/dir1/file1")),
                Arrays.asList(m.getExistingFiles()));
    }

    public void testIsDirectory() {
        File d4 = createFile("/dir1", "dir2", "dir3", "dir4");
        File f7 = createFile("/dir1", "dir2", "dir6", "file7");
        assertFalse(m.isDirectory(d4));

        m.recordExistingFolder("/dir1/dir2/dir3/dir4");
        m.recordExistingFile("/dir1/dir2/dir6/file7");
        assertTrue(m.isDirectory(d4));
        assertFalse(m.isDirectory(f7));

        // any intermediate directory exists implicitly
        assertTrue(m.isDirectory(createFile("/")));
        assertTrue(m.isDirectory(createFile("/dir1")));
        assertTrue(m.isDirectory(createFile("/dir1", "dir2")));
        assertTrue(m.isDirectory(createFile("/dir1", "dir2", "dir3")));
        assertTrue(m.isDirectory(createFile("/dir1", "dir2", "dir6")));

        assertEquals(
                ImmutableList.of(
                                "/",
                                "/dir1",
                                "/dir1/dir2",
                                "/dir1/dir2/dir3",
                                "/dir1/dir2/dir3/dir4",
                                "/dir1/dir2/dir6")
                        .stream()
                        .map(m::getPlatformSpecificPath)
                        .collect(Collectors.toList()),
                Arrays.asList(m.getExistingFolders()));
    }

    public void testDelete() {
        m.recordExistingFolder("/dir1");
        m.recordExistingFile("/dir1/file1");
        m.recordExistingFile("/dir1/file2");

        assertEquals(
                ImmutableList.of(
                        m.getPlatformSpecificPath("/dir1/file1"),
                        m.getPlatformSpecificPath("/dir1/file2")),
                Arrays.asList(m.getExistingFiles()));

        assertTrue(m.delete(createFile("/dir1", "file1")));
        assertFalse(m.delete(createFile("/dir1", "file3")));
        assertFalse(m.delete(createFile("/dir2", "file2")));
        assertEquals(
                ImmutableList.of(m.getPlatformSpecificPath("/dir1/file2")),
                Arrays.asList(m.getExistingFiles()));

        // deleting a directory with files in it fails
        assertFalse(m.delete(createFile("/dir1")));
        // but it works if the directory is empty
        assertTrue(m.delete(createFile("/dir1", "file2")));
        assertTrue(m.delete(createFile("/dir1")));
    }

    public void testListFiles() {
        m.recordExistingFolder("/dir1");
        m.recordExistingFile("/dir1/file1");
        m.recordExistingFile("/dir1/file2");
        m.recordExistingFile("/dir1/dir2/file3");
        m.recordExistingFile("/dir4/file4");

        assertEquals(0, m.listFiles(createFile("/not_a_dir")).length);

        assertArrayEquals(
                new File[] {new File("/dir1/dir2/file3").getAbsoluteFile()},
                m.listFiles(createFile("/dir1", "dir2")));

        assertArrayEquals(
                new File[] {
                    new File("/dir1/dir2").getAbsoluteFile(),
                    new File("/dir1/file1").getAbsoluteFile(),
                    new File(m.getPlatformSpecificPath("/dir1/file2")).getAbsoluteFile()
                },
                m.listFiles(createFile("/dir1")));
    }

    public void testMkDirs() {
        assertArrayEquals(new String[] {m.getPlatformSpecificPath("/")}, m.getExistingFolders());

        assertTrue(m.mkdirs(createFile("/dir1")));
        assertArrayEquals(
                new String[] {m.getPlatformSpecificPath("/"), m.getPlatformSpecificPath("/dir1")},
                m.getExistingFolders());

        m.recordExistingFolder("/dir1");
        assertArrayEquals(
                new String[] {m.getPlatformSpecificPath("/"), m.getPlatformSpecificPath("/dir1")},
                m.getExistingFolders());

        assertTrue(m.mkdirs(createFile("/dir1/dir2/dir3")));
        assertEquals(
                ImmutableList.of("/", "/dir1", "/dir1/dir2", "/dir1/dir2/dir3").stream()
                        .map(m::getPlatformSpecificPath)
                        .collect(Collectors.toList()),
                Arrays.asList(m.getExistingFolders()));
    }

    public void testRenameTo() {
        m.recordExistingFile("/dir1/dir2/dir6/file7");
        m.recordExistingFolder("/dir1/dir2/dir3/dir4");

        assertArrayEquals(
                new String[] {m.getPlatformSpecificPath("/dir1/dir2/dir6/file7")},
                m.getExistingFiles());
        assertEquals(
                ImmutableList.of(
                                "/",
                                "/dir1",
                                "/dir1/dir2",
                                "/dir1/dir2/dir3",
                                "/dir1/dir2/dir3/dir4",
                                "/dir1/dir2/dir6")
                        .stream()
                        .map(m::getPlatformSpecificPath)
                        .collect(Collectors.toList()),
                Arrays.asList(m.getExistingFolders()));

        assertTrue(m.renameTo(createFile("/dir1", "dir2"), createFile("/dir1", "newDir2")));
        assertArrayEquals(
                new String[] {m.getPlatformSpecificPath("/dir1/newDir2/dir6/file7")},
                m.getExistingFiles());
        assertEquals(
                ImmutableList.of(
                                "/",
                                "/dir1",
                                "/dir1/newDir2",
                                "/dir1/newDir2/dir3",
                                "/dir1/newDir2/dir3/dir4",
                                "/dir1/newDir2/dir6")
                        .stream()
                        .map(m::getPlatformSpecificPath)
                        .collect(Collectors.toList()),
                Arrays.asList(m.getExistingFolders()));

        assertTrue(m.renameTo(
                createFile("/dir1", "newDir2", "dir6", "file7"),
                createFile("/dir1", "newDir2", "dir6", "newFile7")));
        assertTrue(m.renameTo(
                createFile("/dir1", "newDir2", "dir3", "dir4"),
                createFile("/dir1", "newDir2", "dir3", "newDir4")));
        assertArrayEquals(
                new String[] {m.getPlatformSpecificPath("/dir1/newDir2/dir6/newFile7")},
                m.getExistingFiles());
        assertEquals(
                ImmutableList.of(
                                "/",
                                "/dir1",
                                "/dir1/newDir2",
                                "/dir1/newDir2/dir3",
                                "/dir1/newDir2/dir3/newDir4",
                                "/dir1/newDir2/dir6")
                        .stream()
                        .map(m::getPlatformSpecificPath)
                        .collect(Collectors.toList()),
                Arrays.asList(m.getExistingFolders()));
    }

    public void testReadOnlyFile() throws Exception {
        File f1 = createFile("/root", "writable.txt");
        File f2 = createFile("/root", "readonly.txt");
        m.mkdirs(f1.getParentFile());
        m.createNewFile(f1);
        m.createNewFile(f2);
        m.setReadOnly(f2);

        assertTrue(m.canWrite(f1));
        assertFalse(m.canWrite(f2));
    }

    public void testToString() throws Exception {
        m.recordExistingFile("/root/blah", "foo");
        assertEquals("foo", m.readText(new File("/root/blah")));
        try {
            m.readText(new File("/root/bogus"));
            fail();
        }
        catch (Exception expected) {
            // nothing
        }
    }

    public void testListWithFilter() throws Exception {
        m.recordExistingFile("/root/foo/a.txt");
        m.recordExistingFile("/root/foo/b.csv");
        m.recordExistingFile("/root/foo/c.txt");
        m.recordExistingFile("/root/foo/d.txt/d.txtWasActuallyAFolder");
        m.recordExistingFile("/root/foofoo/blah");
        m.recordExistingFile("/root/foo/bar/baz.txt");
        String[] result = m.list(new File("/root/foo"), new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".txt");
            }
        });
        assertEquals(result.length, 3);
        List<String> resultList = Lists.newArrayList(result);
        assertTrue(resultList.contains("a.txt"));
        assertTrue(resultList.contains("c.txt"));
        assertTrue(resultList.contains("d.txt"));
    }
}

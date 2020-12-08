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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.repository.testframework.MockFileOp;
import com.android.testutils.OsType;
import com.android.testutils.file.InMemoryFileSystems;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * Unit-test for the {@link MockFileOp}, which is a mock of {@link FileOpImpl} that doesn't touch
 * the file system. Just testing the test.
 */
public class MockFileOpTest {

    private final MockFileOp m = new MockFileOp();

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

    @Test
    public void testIsFile() {
        File f1 = createFile("/dir1", "file1");
        assertFalse(m.isFile(f1));

        m.recordExistingFile("/dir1/file1");
        assertTrue(m.isFile(f1));

        assertExpectedFiles("/dir1/file1");
    }

    @Test
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

        assertExpectedFolders(
                "/dir1",
                "/dir1/dir2",
                "/dir1/dir2/dir3",
                "/dir1/dir2/dir3/dir4",
                "/dir1/dir2/dir6");
    }

    @Test
    public void testDelete() {
        m.recordExistingFolder("/dir1");
        m.recordExistingFile("/dir1/file1");
        m.recordExistingFile("/dir1/file2");

        assertExpectedFiles("/dir1/file1", "/dir1/file2");

        assertTrue(m.delete(createFile("/dir1", "file1")));
        assertFalse(m.delete(createFile("/dir1", "file3")));
        assertFalse(m.delete(createFile("/dir2", "file2")));
        assertExpectedFiles("/dir1/file2");

        // deleting a directory with files in it fails
        assertFalse(m.delete(createFile("/dir1")));
        // but it works if the directory is empty
        assertTrue(m.delete(createFile("/dir1", "file2")));
        assertTrue(m.delete(createFile("/dir1")));
    }

    @Test
    public void testListFiles() {
        m.recordExistingFolder("/dir1");
        m.recordExistingFile("/dir1/file1");
        m.recordExistingFile("/dir1/file2");
        m.recordExistingFile("/dir1/dir2/file3");
        m.recordExistingFile("/dir4/file4");

        assertEquals(0, m.listFiles(createFile("/not_a_dir")).length);

        assertArrayEquals(
                new File[] {
                    new File(InMemoryFileSystems.getPlatformSpecificPath("/dir1/dir2/file3"))
                },
                m.listFiles(createFile("/dir1", "dir2")));

        assertArrayEquals(
                new File[] {
                    new File(InMemoryFileSystems.getPlatformSpecificPath("/dir1/dir2"))
                            .getAbsoluteFile(),
                    new File(InMemoryFileSystems.getPlatformSpecificPath("/dir1/file1"))
                            .getAbsoluteFile(),
                    new File(InMemoryFileSystems.getPlatformSpecificPath("/dir1/file2"))
                            .getAbsoluteFile()
                },
                m.listFiles(createFile("/dir1")));
    }

    @Test
    public void testMkDirs() {
        assertExpectedFolders();

        assertTrue(m.mkdirs(createFile("/dir1")));
        assertExpectedFolders("/dir1");

        m.recordExistingFolder("/dir1");
        assertExpectedFolders("/dir1");

        assertTrue(m.mkdirs(createFile("/dir1/dir2/dir3")));
        assertExpectedFolders("/dir1", "/dir1/dir2", "/dir1/dir2/dir3");
    }

    @Test
    public void testRenameTo() {
        m.recordExistingFile("/dir1/dir2/dir6/file7");
        m.recordExistingFolder("/dir1/dir2/dir3/dir4");

        assertExpectedFiles("/dir1/dir2/dir6/file7");
        assertExpectedFolders(
                "/dir1",
                "/dir1/dir2",
                "/dir1/dir2/dir3",
                "/dir1/dir2/dir3/dir4",
                "/dir1/dir2/dir6");

        assertTrue(m.renameTo(createFile("/dir1", "dir2"), createFile("/dir1", "newDir2")));
        assertExpectedFiles("/dir1/newDir2/dir6/file7");

        assertExpectedFolders(
                "/dir1",
                "/dir1/newDir2",
                "/dir1/newDir2/dir3",
                "/dir1/newDir2/dir3/dir4",
                "/dir1/newDir2/dir6");

        assertTrue(m.renameTo(
                createFile("/dir1", "newDir2", "dir6", "file7"),
                createFile("/dir1", "newDir2", "dir6", "newFile7")));
        assertTrue(m.renameTo(
                createFile("/dir1", "newDir2", "dir3", "dir4"),
                createFile("/dir1", "newDir2", "dir3", "newDir4")));
        assertExpectedFiles("/dir1/newDir2/dir6/newFile7");
        assertExpectedFolders(
                "/dir1",
                "/dir1/newDir2",
                "/dir1/newDir2/dir3",
                "/dir1/newDir2/dir3/newDir4",
                "/dir1/newDir2/dir6");
    }

    @Test
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

    @Test
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

    @Test
    public void testListWithFilter() {
        m.recordExistingFile("/root/foo/a.txt");
        m.recordExistingFile("/root/foo/b.csv");
        m.recordExistingFile("/root/foo/c.txt");
        m.recordExistingFile("/root/foo/d.txt/d.txtWasActuallyAFolder");
        m.recordExistingFile("/root/foofoo/blah");
        m.recordExistingFile("/root/foo/bar/baz.txt");
        String[] result = m.list(new File("/root/foo"), (dir, name) -> name.endsWith(".txt"));
        assertEquals(result.length, 3);
        List<String> resultList = Lists.newArrayList(result);
        assertTrue(resultList.contains("a.txt"));
        assertTrue(resultList.contains("c.txt"));
        assertTrue(resultList.contains("d.txt"));
    }

    private void assertExpectedFiles(String... expected) {
        assertEqualsMaybeIgnoreCase(Arrays.asList(expected), m.getExistingFiles());
    }

    private void assertExpectedFolders(String... expected) {
        List<String> expectedList = new ArrayList<>(Arrays.asList(expected));
        expectedList.add(InMemoryFileSystems.getDefaultWorkingDirectory());
        assertEqualsMaybeIgnoreCase(expectedList, m.getExistingFolders());
    }

    private void assertEqualsMaybeIgnoreCase(
            @NonNull List<String> expected, @NonNull List<String> actual) {
        if (OsType.getHostOs() == OsType.WINDOWS || OsType.getHostOs() == OsType.DARWIN) {
            assertThat(actual.stream().map(String::toLowerCase).collect(Collectors.toList()))
                    .containsExactlyElementsIn(
                            expected.stream()
                                    .map(m::getPlatformSpecificPath)
                                    .map(String::toLowerCase)
                                    .collect(Collectors.toList()));
        } else {
            assertThat(actual).containsExactlyElementsIn(expected);
        }
    }
}

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
package com.android.repository.util;

import com.android.repository.io.FileUtilKt;
import com.android.testutils.file.InMemoryFileSystems;
import java.nio.file.Path;
import junit.framework.TestCase;

/**
 * Tests for {@link FileUtilKt}.
 */
public class FileUtilTest extends TestCase {

    public void testRecursiveSize() throws Exception {
        final String[][] theFiles = {
            {"file1.txt", "The contents of file1"},
            {"file_number_two.txt", "The contents of file number two"},
            {"aSubDirectory/file_three.txt", "A file in the first sub-directory"},
            {"aSubDirectory/subsub/file_4.txt", "A file in a sub-sub-directory"}
        };

        int expectedSize = 0;
        Path rootPath = InMemoryFileSystems.createInMemoryFileSystemAndFolder("aDirectory");
        for (final String[] fileInfo : theFiles) {
            InMemoryFileSystems.recordExistingFile(rootPath.resolve(fileInfo[0]), fileInfo[1]);
            expectedSize += fileInfo[1].length();
        }
        assertEquals(expectedSize, FileUtilKt.recursiveSize(rootPath));
    }
}

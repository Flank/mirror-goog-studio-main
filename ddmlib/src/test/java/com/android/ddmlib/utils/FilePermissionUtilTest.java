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
package com.android.ddmlib.utils;

import static org.junit.Assert.assertEquals;

import com.android.SdkConstants;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

/**
 * Unit tests for {@link FilePermissionUtil}
 */
public class FilePermissionUtilTest {

    private static final boolean isWindows =
            SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testGetFilePosixPermission_defaultPermission() {
        File fakeFile = new File("this-file-does-not-exist");
        assertEquals(0644, FilePermissionUtil.getFilePosixPermission(fakeFile));
    }

    @Test
    public void testGetFilePosixPermission() throws IOException {
        File testFile = folder.newFile("testFile");
        // ensure the test file permission is as expected: "rw-r--r--"
        testFile.setWritable(true, true);
        testFile.setReadable(true, false);
        testFile.setExecutable(false, false);
        // when on Windows, the file will be "rwx------" which is 700
        assertEquals(isWindows ? 0700 : 0644, FilePermissionUtil.getFilePosixPermission(testFile));
        // set executable for owner only
        testFile.setExecutable(true, true);
        assertEquals(isWindows ? 0700 : 0744, FilePermissionUtil.getFilePosixPermission(testFile));
    }
}

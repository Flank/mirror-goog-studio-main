/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.signflinger;

import com.android.testutils.TestUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.rules.TemporaryFolder;

/** Rule providing resource files and temporary output files to signflinger tests. */
public class Workspace extends TemporaryFolder {

    private static final String RESOURCE_PATH = "tools/base/signflinger/test/resources/";

    public File getResourceFile(String path) {
        String fullPath = RESOURCE_PATH + path;
        File prospect = new File(fullPath);
        if (prospect.exists()) {
            return prospect;
        }
        return TestUtils.resolveWorkspacePath(fullPath).toFile();
    }

    public Path getResourcePath(String path) {
        return getResourceFile(path).toPath();
    }

    public File getDummyAndroidManifest() {
        return getResourceFile("AndroidManifest.xml");
    }

    public File getTestOutputFile(String path) throws IOException {
        File directory = newFolder();
        Files.createDirectories(directory.toPath());
        return new File(directory, path);
    }

    public File createZip(long numFiles, int sizePerFile, String filename, File androidManifest)
            throws IOException {
        File dst = getTestOutputFile(filename);
        if (dst.exists()) {
            dst.delete();
        }

        long fileId = 0;
        Random random = new Random(1);
        try (FileOutputStream f = new FileOutputStream(dst);
                ZipOutputStream s = new ZipOutputStream(f)) {
            for (int i = 0; i < numFiles; i++) {
                long id = fileId++;
                String name = String.format("file%06d", id);
                ZipEntry entry = new ZipEntry(name);
                byte[] bytes = new byte[sizePerFile];
                random.nextBytes(bytes);
                s.putNextEntry(entry);
                s.write(bytes);
                s.closeEntry();
            }
            ZipEntry entry = new ZipEntry("AndroidManifest.xml");
            s.putNextEntry(entry);
            byte[] bytes = Files.readAllBytes(androidManifest.toPath());

            s.write(bytes);
            s.closeEntry();
        }
        return dst;
    }
}

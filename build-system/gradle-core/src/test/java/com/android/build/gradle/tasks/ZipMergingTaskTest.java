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

package com.android.build.gradle.tasks;

import com.android.testutils.truth.MoreTruth;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Test for ZipMergingTask. */
public class ZipMergingTaskTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void merge() throws IOException {
        File zip1 = temporaryFolder.newFile("file1.zip");
        File zip2 = temporaryFolder.newFile("file2.zip");

        createZip(zip1, "foo.txt", "foo");
        createZip(zip2, "bar.txt", "bar");

        File testDir = temporaryFolder.newFolder();
        Project project = ProjectBuilder.builder().withProjectDir(testDir).build();

        File output = temporaryFolder.newFile("output.zip");
        ZipMergingTask task = project.getTasks().create("test", ZipMergingTask.class);

        task.init(project.files(zip1, zip2), output);
        task.merge();

        MoreTruth.assertThat(output).exists();

        MoreTruth.assertThatZip(output).containsFileWithContent("foo.txt", "foo");
        MoreTruth.assertThatZip(output).containsFileWithContent("bar.txt", "bar");
    }

    private static void createZip(File file, String entry, String content) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file);
                BufferedOutputStream bos = new BufferedOutputStream(fos);
                ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry(entry));
            zos.write(content.getBytes());
            zos.closeEntry();
        }
    }
}

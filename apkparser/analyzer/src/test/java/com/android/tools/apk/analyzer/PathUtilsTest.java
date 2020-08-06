/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.apk.analyzer;

import static com.google.common.truth.Truth.assertThat;

import com.android.testutils.TestResources;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PathUtilsTest {
    @Test
    public void testLocalFileSystemPath() throws IOException {
        File tempFile = File.createTempFile("Foo", ".txt");
        Path path = tempFile.toPath();
        String separator = path.getFileSystem().getSeparator();

        assertThat(PathUtils.pathWithTrailingSeparator(path)).endsWith(".txt");
        assertThat(PathUtils.pathWithTrailingSeparator(path.getParent())).endsWith(separator);

        assertThat(PathUtils.fileNameWithTrailingSeparator(path)).endsWith(".txt");
        assertThat(PathUtils.fileNameWithTrailingSeparator(path.getParent())).endsWith(separator);
    }

    @Test
    public void testZipPath() throws IOException {
        Path archivePath = TestResources.getFile("/test.apk").toPath();
        try (ArchiveContext archiveContext = Archives.open(archivePath)) {
            Path path = archiveContext.getArchive().getContentRoot().resolve("res/anim/fade.xml");
            String separator = path.getFileSystem().getSeparator();

            assertThat(PathUtils.pathWithTrailingSeparator(path))
                    .isEqualTo(separator + "res" + separator + "anim" + separator + "fade.xml");

            assertThat(PathUtils.pathWithTrailingSeparator(path.getParent()))
                    .isEqualTo(separator + "res" + separator + "anim" + separator);

            assertThat(PathUtils.fileNameWithTrailingSeparator(path)).isEqualTo("fade.xml");

            assertThat(PathUtils.fileNameWithTrailingSeparator(path.getParent()))
                    .isEqualTo("anim" + separator);
        }
    }
}

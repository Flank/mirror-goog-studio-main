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

package com.android.tools.apk.analyzer;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.testutils.TestResources;
import com.google.common.primitives.Longs;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ArchiveTreeStructureTest {
    private Archive archive;
    private ArchiveNode root;

    @Before
    public void setup() throws IOException {
        archive = Archives.open(TestResources.getFile("/test.apk").toPath());
        root = ArchiveTreeStructure.create(archive);
    }

    @After
    public void tearDown() throws IOException {
        Set<Archive> archives = new HashSet<>();
        ArchiveTreeStream.postOrderStream(root)
                .forEach(node -> archives.add(node.getData().getArchive()));
        for (Archive ar : archives) {
            ar.close();
            if (ar != archive) {
                Files.deleteIfExists(ar.getPath());
                try {
                    Files.deleteIfExists(ar.getPath().getParent());
                } catch (DirectoryNotEmptyException ignored) {

                }
            }
        }
    }

    @Test
    public void create() throws IOException {
        String actual =
                dumpTree(
                        root,
                        n -> {
                            String path = n.getData().getPath().toString();
                            Archive archive = n.getData().getArchive();
                            ArchiveNode parentNode = n.getParent();
                            if (parentNode != null
                                    && parentNode.getData().getArchive() != archive) {
                                return path;
                            }
                            while ((parentNode = n.getParent()) != null) {
                                if (parentNode.getData().getArchive() != archive) {
                                    path = n.getData().getPath().toString() + path;
                                }
                                n = parentNode;
                                archive = n.getData().getArchive();
                            }
                            return path;
                        });
        String expected =
                "/\n"
                        + "/res/\n"
                        + "/res/anim/\n"
                        + "/res/anim/fade.xml\n"
                        + "/instant-run.zip\n"
                        + "/instant-run.zip/instant-run/\n"
                        + "/instant-run.zip/instant-run/classes1.dex\n"
                        + "/AndroidManifest.xml";
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void updateRawFileSizes() throws IOException {
        ArchiveTreeStructure.updateRawFileSizes(root, ApkSizeCalculator.getDefault());
        String actual =
                dumpTree(
                        root,
                        n -> {
                            ArchiveEntry entry = n.getData();
                            return String.format(
                                    Locale.US,
                                    "%1$-10d %2$s",
                                    entry.getRawFileSize(),
                                    entry.getPath());
                        });
        String expected =
                "19         /\n"
                        + "6          /res/\n"
                        + "6          /res/anim/\n"
                        + "6          /res/anim/fade.xml\n"
                        + "2          /instant-run.zip\n"
                        + "2          /instant-run/\n"
                        + "2          /instant-run/classes1.dex\n"
                        + "11         /AndroidManifest.xml";
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void updateDownloadFileSizes() throws IOException {
        ArchiveTreeStructure.updateDownloadFileSizes(root, ApkSizeCalculator.getDefault());

        String actual =
                dumpTree(
                        root,
                        n -> {
                            ArchiveEntry entry = n.getData();
                            return String.format(
                                    Locale.US,
                                    "%1$-10d %2$s",
                                    entry.getDownloadFileSize(),
                                    entry.getPath());
                        });
        String expected =
                "23         /\n"
                        + "8          /res/\n"
                        + "8          /res/anim/\n"
                        + "8          /res/anim/fade.xml\n"
                        + "4          /instant-run.zip\n"
                        + "4          /instant-run/\n"
                        + "4          /instant-run/classes1.dex\n"
                        + "11         /AndroidManifest.xml";
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void sort() throws IOException {
        ArchiveTreeStructure.updateRawFileSizes(root, ApkSizeCalculator.getDefault());
        ArchiveTreeStructure.sort(
                root,
                (o1, o2) ->
                        Longs.compare(
                                o2.getData().getRawFileSize(), o1.getData().getRawFileSize()));
        String actual =
                dumpTree(
                        root,
                        n -> {
                            ArchiveEntry entry = n.getData();
                            return String.format(
                                    Locale.US,
                                    "%1$-10d %2$s",
                                    entry.getRawFileSize(),
                                    entry.getPath());
                        });
        String expected =
                "19         /\n"
                        + "11         /AndroidManifest.xml\n"
                        + "6          /res/\n"
                        + "6          /res/anim/\n"
                        + "6          /res/anim/fade.xml\n"
                        + "2          /instant-run.zip\n"
                        + "2          /instant-run/\n"
                        + "2          /instant-run/classes1.dex";
        assertThat(actual).isEqualTo(expected);
    }

    private static String dumpTree(
            @NonNull ArchiveNode root, @NonNull Function<ArchiveNode, String> mapper) {
        return ArchiveTreeStream.preOrderStream(root).map(mapper).collect(Collectors.joining("\n"));
    }
}

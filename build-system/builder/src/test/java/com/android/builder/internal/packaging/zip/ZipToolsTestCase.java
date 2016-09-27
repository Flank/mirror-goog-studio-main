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

package com.android.builder.internal.packaging.zip;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ZipToolsTestCase {
    @Nullable
    private String mZipFile;

    @Nullable
    private List<String> mUnzipCommand;

    @Nullable
    private String mUnzipLineRegex;

    private int mUnzipRegexNameGroup;

    private int mUnzipRegexSizeGroup;

    private boolean mToolStoresDirectories;

    @Rule
    @NonNull
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    protected void configure(
            @NonNull String zipFile,
            @NonNull List<String> unzipCommand,
            @NonNull String unzipLineRegex,
            int nameGroup,
            int sizeGroup,
            boolean toolStoresDirectories) {
        mZipFile = zipFile;
        mUnzipCommand = unzipCommand;
        mUnzipLineRegex = unzipLineRegex;
        mUnzipRegexNameGroup = nameGroup;
        mUnzipRegexSizeGroup = sizeGroup;
        mToolStoresDirectories = toolStoresDirectories;
    }

    void configure(@NonNull String zipFile, boolean toolStoresDirectories) {
        configure(zipFile, ImmutableList.of("no command"), "no regexp", -1, -1, toolStoresDirectories);
    }

    private File cloneZipFile() throws Exception {
        File zfile = mTemporaryFolder.newFile("file.zip");
        Files.copy(ZipTestUtils.rsrcFile(mZipFile), zfile);
        return zfile;
    }

    private static void assertFileInZip(@NonNull ZFile zfile, @NonNull String name) throws Exception {
        StoredEntry root = zfile.get(name);
        assertNotNull(root);

        InputStream is = root.open();
        byte[] inZipData = ByteStreams.toByteArray(is);
        is.close();

        byte[] inFileData = Files.toByteArray(ZipTestUtils.rsrcFile(name));
        assertArrayEquals(inFileData, inZipData);
    }

    @Test
    public void zfileReadsZipFile() throws Exception {
        try (ZFile zf = new ZFile(cloneZipFile())) {
            if (mToolStoresDirectories) {
                assertEquals(6, zf.entries().size());
            } else {
                assertEquals(4, zf.entries().size());
            }

            assertFileInZip(zf, "root");
            assertFileInZip(zf, "images/lena.png");
            assertFileInZip(zf, "text-files/rfc2460.txt");
            assertFileInZip(zf, "text-files/wikipedia.html");
        }
    }

    @Test
    public void toolReadsZfFile() throws Exception {
        testReadZFile(false);
    }

    @Test
    public void toolReadsAlignedZfFile() throws Exception {
        testReadZFile(true);
    }

    private void testReadZFile(boolean align) throws Exception {
        String unzipcmd = mUnzipCommand.get(0);
        Assume.assumeTrue(new File(unzipcmd).canExecute());

        ZFileOptions options = new ZFileOptions();
        if (align) {
            options.setAlignmentRule(AlignmentRules.constant(500));
        }

        File zfile = new File (mTemporaryFolder.getRoot(), "zfile.zip");
        try (ZFile zf = new ZFile(zfile, options)) {
            zf.add("root", new FileInputStream(ZipTestUtils.rsrcFile("root")));
            zf.add("images/", new ByteArrayInputStream(new byte[0]));
            zf.add("images/lena.png", new FileInputStream(ZipTestUtils.rsrcFile("images/lena.png")));
            zf.add("text-files/", new ByteArrayInputStream(new byte[0]));
            zf.add("text-files/rfc2460.txt", new FileInputStream(
                    ZipTestUtils.rsrcFile("text-files/rfc2460.txt")));
            zf.add("text-files/wikipedia.html",
                    new FileInputStream(ZipTestUtils.rsrcFile("text-files/wikipedia.html")));
        }

        List<String> command = Lists.newArrayList(mUnzipCommand);
        command.add(zfile.getAbsolutePath());
        ProcessBuilder pb = new ProcessBuilder(command);
        Process proc = pb.start();
        InputStream is = proc.getInputStream();
        byte output[] = ByteStreams.toByteArray(is);
        String text = new String(output, Charsets.US_ASCII);
        String lines[] = text.split("\n");
        Map<String, Integer> sizes = Maps.newHashMap();
        for (String l : lines) {
            Matcher m = Pattern.compile(mUnzipLineRegex).matcher(l);
            if (m.matches()) {
                String sizeTxt = m.group(mUnzipRegexSizeGroup);
                int size = Integer.parseInt(sizeTxt);
                String name = m.group(mUnzipRegexNameGroup);
                sizes.put(name, size);
            }
        }

        assertEquals(6, sizes.size());

        /*
         * The "images" directory may show up as "images" or "images/".
         */
        String imagesKey = "images/";
        if (!sizes.containsKey(imagesKey)) {
            imagesKey = "images";
        }

        assertTrue(sizes.containsKey(imagesKey));
        assertEquals(0, sizes.get(imagesKey).intValue());

        assertSize(new String[] { "images/", "images" }, 0, sizes);
        assertSize(new String[] { "text-files/", "text-files"}, 0, sizes);
        assertSize(new String[] { "root" }, ZipTestUtils.rsrcFile("root").length(), sizes);
        assertSize(new String[] { "images/lena.png", "images\\lena.png" },
                ZipTestUtils.rsrcFile("images/lena.png").length(), sizes);
        assertSize(new String[] { "text-files/rfc2460.txt", "text-files\\rfc2460.txt" },
                ZipTestUtils.rsrcFile("text-files/rfc2460.txt").length(), sizes);
        assertSize(new String[] { "text-files/wikipedia.html", "text-files\\wikipedia.html" },
                ZipTestUtils.rsrcFile("text-files/wikipedia.html").length(), sizes);
    }

    private static void assertSize(String[] names, long size, Map<String, Integer> sizes) {
        for (String n : names) {
            if (sizes.containsKey(n)) {
                assertEquals((long) sizes.get(n), size);
                return;
            }
        }

        fail();
    }
}

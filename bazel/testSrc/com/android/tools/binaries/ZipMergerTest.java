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

package com.android.tools.binaries;

import static org.junit.Assert.assertEquals;

import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.Test;

public class ZipMergerTest {

    @Test
    public void testMergeTwoZips() throws Exception {
        File zip1 = createZipFile("zip1.zip",
                "place/here/b.txt", "BB",
                "place/here/c.txt", "CC");
        File zip2 = createZipFile("zip2.zip",
                "place/here/d.txt", "DD",
                "place/here/e.txt", "EE");

        File res = newFile("res.zip");
        ZipMerger.main(new String[]{
                "c",
                res.getAbsolutePath(),
                "prefix/=" + zip1.getAbsolutePath(),
                "prefix/=" + zip2.getAbsolutePath(),
        });

        assertZipEquals(res,
                "prefix/place/here/b.txt", "BB",
                "prefix/place/here/c.txt", "CC",
                "prefix/place/here/d.txt", "DD",
                "prefix/place/here/e.txt", "EE");
    }

    @Test
    public void testRecursiveMerge() throws Exception {
        File zip1 = createZipFile("zip1.zip",
                "place/here/b.txt", "BB",
                "place/here/c.txt", "CC",
                "place/here/d.zip!one.txt", "11",
                "place/here/e.zip!two.txt", "22");
        File zip2 = createZipFile("zip2.zip",
                "prefix/place/here/f.txt", "FF",
                "prefix/place/here/e.zip!three.txt", "33",
                "prefix/place/here/g.zip!four.txt", "44");

        File res = newFile("res.zip");
        ZipMerger.main(new String[]{
                "c",
                res.getAbsolutePath(),
                "prefix/=" + zip1.getAbsolutePath(),
                "+" + zip2.getAbsolutePath(),
        });

        assertZipEquals(res,
                "prefix/place/here/b.txt", "BB",
                "prefix/place/here/c.txt", "CC",
                "prefix/place/here/f.txt", "FF",
                "prefix/place/here/d.zip!one.txt", "11",
                "prefix/place/here/e.zip!two.txt", "22",
                "prefix/place/here/e.zip!three.txt", "33",
                "prefix/place/here/g.zip!four.txt", "44");
    }

    @Test
    public void testMergeTwoZipsWithOverlap() throws Exception {
        File zip1 = createZipFile("zip1.zip",
                "place/here/b.txt", "BB",
                "place/here/c.txt", "CC");
        File zip2 = createZipFile("zip2.zip",
                "place/here/d.txt", "DD",
                "place/here/c.txt", "OO");

        File res = newFile("res.zip");
        ZipMerger.main(new String[]{
                "c",
                res.getAbsolutePath(),
                "prefix/=" + zip1.getAbsolutePath(),
                "prefix/=+" + zip2.getAbsolutePath(),
        });

        assertZipEquals(res,
                "prefix/place/here/b.txt", "BB",
                "prefix/place/here/c.txt", "OO",
                "prefix/place/here/d.txt", "DD");
    }

    @Test
    public void testRecursiveMergeWithOverlap() throws Exception {
        File zip1 = createZipFile("zip1.zip",
                "place/here/b.txt", "BB",
                "place/here/c.txt", "CC",
                "place/here/d.zip!one.txt", "11",
                "place/here/e.zip!two.txt", "22");
        File zip2 = createZipFile("zip2.zip",
                "prefix/place/here/f.txt", "FF",
                "prefix/place/here/e.zip!two.txt", "OO",
                "prefix/place/here/e.zip!three.txt", "33",
                "prefix/place/here/g.zip!four.txt", "44");

        File res = newFile("res.zip");
        ZipMerger.main(new String[]{
                "c",
                res.getAbsolutePath(),
                "prefix/=" + zip1.getAbsolutePath(),
                "+" + zip2.getAbsolutePath(),
        });

        assertZipEquals(res,
                "prefix/place/here/b.txt", "BB",
                "prefix/place/here/c.txt", "CC",
                "prefix/place/here/f.txt", "FF",
                "prefix/place/here/d.zip!one.txt", "11",
                "prefix/place/here/e.zip!two.txt", "OO",
                "prefix/place/here/e.zip!three.txt", "33",
                "prefix/place/here/g.zip!four.txt", "44");
    }

    private void assertZipEquals(File zip, String... args) throws Exception {
        assertEquals(0, args.length % 2);
        Map<String, String> expected = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            expected.put(args[i], args[i + 1]);
        }
        assertEquals(expected, readZip(zip));
    }

    private Map<String, String> readZip(File zip) throws Exception {
        return readZip(new FileInputStream(zip));
    }

    private Map<String, String> readZip(InputStream zip) throws Exception {
        Map<String, String> ret = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(zip)) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                byte[] bytes = ByteStreams.toByteArray(zis);
                if (zipEntry.getName().endsWith(".zip")) {
                    Map<String, String> inner = readZip(new ByteArrayInputStream(bytes));
                    for (Map.Entry<String, String> innerEntry : inner.entrySet()) {
                        String k = zipEntry.getName() + "!" + innerEntry.getKey();
                        ret.put(k, innerEntry.getValue());
                    }
                } else {
                    ret.put(zipEntry.getName(), new String(bytes, StandardCharsets.UTF_8));
                }
                zipEntry = zis.getNextEntry();
            }
        }
        return ret;
    }

    private File createZipFile(String zip, String... args) throws Exception {
        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> contents = new ArrayList<>();
        for (int i = 0; i < args.length; i += 2) {
            names.add(args[i]);
            contents.add(args[i + 1]);
        }
        byte[] bytes = createZipFile(names, contents);
        File file = new File(zip);
        Files.write(file.toPath(), bytes);
        return file;
    }

    private byte[] createZipFile(ArrayList<String> names, ArrayList<String> contents) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zipOut = new ZipOutputStream(out)) {
            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i);
                int ix = name.indexOf('!');
                if (ix != -1) {
                    String prefix = name.substring(0, ix + 1);
                    ArrayList<String> innerNames = new ArrayList<>();
                    ArrayList<String> innerContents = new ArrayList<>();
                    i--;
                    while (i + 1 < names.size() && names.get(i + 1).startsWith(prefix)) {
                        i++;
                        innerNames.add(names.get(i).substring(ix + 1));
                        innerContents.add(contents.get(i));
                    }
                    zipOut.putNextEntry(new ZipEntry(name.substring(0, ix)));
                    byte[] bytes = createZipFile(innerNames, innerContents);
                    ByteStreams.copy(new ByteArrayInputStream(bytes), zipOut);
                } else {
                    zipOut.putNextEntry(new ZipEntry(name));
                    byte[] bytes = contents.get(i).getBytes(StandardCharsets.UTF_8);
                    ByteStreams.copy(new ByteArrayInputStream(bytes), zipOut);
                }
            }
        }
        return out.toByteArray();
    }

    private File newFile(String name) {
        String tmp = System.getenv("TEST_TMPDIR");
        return new File(tmp, name);
    }
}

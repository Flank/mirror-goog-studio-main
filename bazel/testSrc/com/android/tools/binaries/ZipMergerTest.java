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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.Test;

public class ZipMergerTest {

    @Test
    public void testAddToZip() throws Exception {
        File a = createFile("a.txt", "AA");
        File b = createFile("b.txt", "BB");
        File res = newFile("res.zip");
        ZipMerger.main(new String[]{
                "c",
                res.getAbsolutePath(),
                "place/here/a.txt=" + a.getAbsolutePath(),
                "place/here/b.txt=" + b.getAbsolutePath(),
        });

        assertZipEquals(res,
                "place/here/a.txt", "AA",
                "place/here/b.txt", "BB");
    }

    @Test
    public void testAddZipToZip() throws Exception {
        File a = createFile("a.txt", "AA");
        File zip = createZipFile("zip.zip",
                "place/here/b.txt", "BB",
                "place/here/c.txt", "CC");

        File res = newFile("res.zip");
        ZipMerger.main(new String[]{
                "c",
                res.getAbsolutePath(),
                "new/a.txt=" + a.getAbsolutePath(),
                "new/o.zip=" + zip.getAbsolutePath(),
        });

        assertZipEquals(res,
                "new/a.txt", "AA",
                "new/o.zip!place/here/b.txt", "BB",
                "new/o.zip!place/here/c.txt", "CC");
    }

    @Test
    public void testFlattenZip() throws Exception {
        File a = createFile("a.txt", "AA");
        File zip = createZipFile("zip.zip",
                "place/here/b.txt", "BB",
                "place/here/c.txt", "CC");

        File res = newFile("res.zip");
        ZipMerger.main(new String[]{
                "c",
                res.getAbsolutePath(),
                "new/a.txt=" + a.getAbsolutePath(),
                "new/path/=+" + zip.getAbsolutePath(),
        });

        assertZipEquals(res,
                "new/a.txt", "AA",
                "new/path/place/here/b.txt", "BB",
                "new/path/place/here/c.txt", "CC");
    }

    @Test
    public void testFlattenZipWithZip() throws Exception {
        File a = createFile("a.txt", "AA");
        File zip = createZipFile("zip.zip",
                "place/here/b.txt", "BB",
                "place/here/c.txt", "CC",
                "place/here/d.zip!one.txt", "11",
                "place/here/d.zip!two.txt", "22");

        File res = newFile("res.zip");
        ZipMerger.main(new String[]{
                "c",
                res.getAbsolutePath(),
                "new/a.txt=" + a.getAbsolutePath(),
                "new/path/=+" + zip.getAbsolutePath(),
        });

        assertZipEquals(res,
                "new/a.txt", "AA",
                "new/path/place/here/b.txt", "BB",
                "new/path/place/here/c.txt", "CC",
                "new/path/place/here/d.zip!one.txt", "11",
                "new/path/place/here/d.zip!two.txt", "22");
    }

    @Test
    public void testAddZipToZipWithOverride() throws Exception {
        File a = createFile("a.txt", "AA");
        File zip = createZipFile("zip.zip",
                "place/here/b.txt", "BB",
                "place/here/c.txt", "CC");
        File x = createFile("x.txt", "XX");

        File res = newFile("res.zip");
        ZipMerger.main(new String[]{
                "c",
                res.getAbsolutePath(),
                "new/a.txt=" + a.getAbsolutePath(),
                "new/o.zip=" + zip.getAbsolutePath(),
                "#new/o.zip!place/here/b.txt=" + x.getAbsolutePath(),
        });

        assertZipEquals(res,
                "new/a.txt", "AA",
                "new/o.zip!place/here/b.txt", "XX",
                "new/o.zip!place/here/c.txt", "CC");
    }

    @Test
    public void testFlattenZipWithZipAndOverride() throws Exception {
        File a = createFile("a.txt", "AA");
        File zip = createZipFile("zip.zip",
                "place/here/b.txt", "BB",
                "place/here/c.txt", "CC",
                "place/here/d.zip!one.txt", "11",
                "place/here/d.zip!two.txt", "22");

        File x = createFile("x.txt", "XX");
        File res = newFile("res.zip");
        ZipMerger.main(new String[]{
                "c",
                res.getAbsolutePath(),
                "new/a.txt=" + a.getAbsolutePath(),
                "new/path/=+" + zip.getAbsolutePath(),
                "#new/path/place/here/d.zip!one.txt=" + x.getAbsolutePath(),
        });

        assertZipEquals(res,
                "new/a.txt", "AA",
                "new/path/place/here/b.txt", "BB",
                "new/path/place/here/c.txt", "CC",
                "new/path/place/here/d.zip!one.txt", "XX",
                "new/path/place/here/d.zip!two.txt", "22");
    }

    @Test
    public void testAddZipToZipWithTwoOverrides() throws Exception {
        File a = createFile("a.txt", "AA");
        File zip1 = createZipFile("zip1.zip",
                "one/b.txt", "BB",
                "two/c.txt", "CC");
        File zip2 = createZipFile("zip2.zip",
                "three/d.txt", "DD");
        File x = createFile("x.txt", "XX");
        File y = createFile("y.txt", "YY");

        File res = newFile("res.zip");
        ZipMerger.main(new String[]{
                "c",
                res.getAbsolutePath(),
                "new/a.txt=" + a.getAbsolutePath(),
                "new/o.zip=" + zip1.getAbsolutePath(),
                "new/p.zip=" + zip2.getAbsolutePath(),
                "#new/o.zip!one/b.txt=" + x.getAbsolutePath(),
                "#new/p.zip!three/d.txt=" + y.getAbsolutePath(),
        });

        assertZipEquals(res,
                "new/a.txt", "AA",
                "new/o.zip!one/b.txt", "XX",
                "new/o.zip!two/c.txt", "CC",
                "new/p.zip!three/d.txt", "YY");
    }

    @Test
    public void testFlattenZipWithZipAndTwoOverridesSameFile() throws Exception {
        File a = createFile("a.txt", "AA");
        File zip = createZipFile("zip.zip",
                "place/here/b.txt", "BB",
                "place/here/c.txt", "CC",
                "place/here/d.zip!one.txt", "11",
                "place/here/d.zip!two.txt", "22");

        File x = createFile("x.txt", "XX");
        File y = createFile("y.txt", "YY");
        File res = newFile("res.zip");
        ZipMerger.main(new String[]{
                "c",
                res.getAbsolutePath(),
                "new/a.txt=" + a.getAbsolutePath(),
                "new/path/=+" + zip.getAbsolutePath(),
                "#new/path/place/here/d.zip!one.txt=" + x.getAbsolutePath(),
                "#new/path/place/here/d.zip!two.txt=" + y.getAbsolutePath(),
        });

        assertZipEquals(res,
                "new/a.txt", "AA",
                "new/path/place/here/b.txt", "BB",
                "new/path/place/here/c.txt", "CC",
                "new/path/place/here/d.zip!one.txt", "XX",
                "new/path/place/here/d.zip!two.txt", "YY");
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
                    while (i < names.size() && names.get(i).startsWith(prefix)) {
                        innerNames.add(names.get(i).substring(ix + 1));
                        innerContents.add(contents.get(i));
                        i++;
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

    private File createFile(String name, String content) throws Exception {
        File file = newFile(name);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        };
        return file;
    }
}

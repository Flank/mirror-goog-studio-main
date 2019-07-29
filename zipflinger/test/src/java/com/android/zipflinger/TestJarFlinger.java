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

package com.android.zipflinger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.Assert;
import org.junit.Test;

public class TestJarFlinger extends TestBase {

    @Test
    public void testBasicFilterClassesOnly() throws IOException {
        Path path = getTestPath("testBasicFilterIncludeClasses.zip");

        Predicate<String> predicate = archivePath -> archivePath.endsWith(".class");
        try (JarFlinger flinger = new JarFlinger(path, predicate)) {
            flinger.addJar(getPath("1-2-3files.zip"));
            flinger.addJar(getPath("fake.apk"));
        }

        Map<String, Entry> entries = ZipArchive.listEntries(path.toFile());
        for (String name : entries.keySet()) {
            Assert.assertTrue("Found !.class entry:" + name, predicate.test(name));
        }
    }

    @Test
    public void testBasicFilterExcludeClasses() throws IOException {
        Path path = getTestPath("testBasicFilterExcludeClasses.zip");

        Predicate<String> predicate = archivePath -> !archivePath.endsWith(".class");
        try (JarFlinger flinger = new JarFlinger(path, predicate)) {
            flinger.addJar(getPath("1-2-3files.zip"));
            flinger.addJar(getPath("fake.apk"));
        }

        Map<String, Entry> entries = ZipArchive.listEntries(path.toFile());
        for (String name : entries.keySet()) {
            Assert.assertTrue("Found .class entry:" + name, predicate.test(name));
        }
    }

    @Test
    public void testJarMerging() throws IOException {
        Path path = getTestPath("testJarMerging.zip");

        try (JarFlinger flinger = new JarFlinger(path)) {
            flinger.addJar(getPath("1-2-3files.zip"));
            flinger.addJar(getPath("fake.apk"));
        }
        verifyArchive(path.toFile());

        Map<String, Entry> entries = ZipArchive.listEntries(path.toFile());
        Assert.assertEquals("Archive should have seven entries", 7, entries.size());
    }

    @Test
    public void testFilesMerging() throws IOException {
        Path path = getTestPath("testFilesMerging.zip");

        try (JarFlinger flinger = new JarFlinger(path)) {
            flinger.addFile("file1", getPath("1-2-3files.zip"));
            flinger.addFile("file2", getPath("fake.apk"));
        }
        verifyArchive(path.toFile());

        Map<String, Entry> entries = ZipArchive.listEntries(path.toFile());
        Assert.assertEquals("Archive should have two entries", 2, entries.size());
        Assert.assertTrue("Archive should contain entry 'file1", entries.containsKey("file1"));
        Assert.assertTrue("Archive should contain entry 'file2", entries.containsKey("file2"));
    }
}

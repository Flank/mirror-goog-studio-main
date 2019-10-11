/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.deployer;

import static org.junit.Assert.assertEquals;

import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.DexClass;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import org.junit.Test;

public class CachedDexSplitterTest {

    private static final Apk apk = Apk.builder().build();

    private static final ApkEntry entryA = new ApkEntry("A", 0, apk);
    private static final DexClass classA = new DexClass("A", 16, new byte[0], entryA);

    private static final ApkEntry entryB = new ApkEntry("B", 1, apk);
    private static final DexClass classBV1 = new DexClass("B", 256, new byte[0], entryB);
    private static final DexClass classBV2 = new DexClass("B", 1024, new byte[0], entryB);

    @Test
    public void testCache() throws Exception {
        FakeApkFileDb fakeDb = new FakeApkFileDb();
        fakeDb.addClasses(ImmutableList.of(classBV1));

        FakeDexSplitter fakeSplitter = new FakeDexSplitter();
        fakeSplitter.add(entryA, classA);
        fakeSplitter.add(entryB, classBV2);

        CachedDexSplitter splitter = new CachedDexSplitter(fakeDb, fakeSplitter);

        // Test that a split with a cache miss works.
        Collection<DexClass> classes = splitter.split(entryA, null);
        assertEquals(Iterables.getOnlyElement(classes), classA);

        splitter.cache(classes);

        // Test that a split with a cache hit works
        classes = splitter.split(entryA, null);
        assertEquals(Iterables.getOnlyElement(classes), classA);

        // Test that the cache is preferred over the db
        classes = splitter.split(entryB, null);
        assertEquals(Iterables.getOnlyElement(classes), classBV1);

        splitter.cache(ImmutableList.of(classBV2));

        // Test that the cache gets updated appropriately
        classes = splitter.split(entryB, null);
        Iterator<DexClass> iter = classes.iterator();
        assertEquals(iter.next(), classBV1);
        assertEquals(iter.next(), classBV2);
    }

    private static class FakeApkFileDb implements ApkFileDatabase {
        private final ArrayListMultimap<ApkEntry, DexClass> classes;

        public FakeApkFileDb() {
            this.classes = ArrayListMultimap.create();
        }

        @Override
        public void addClasses(Collection<DexClass> allClasses) {
            allClasses.stream().forEach(dex -> classes.put(dex.dex, dex));
        }

        @Override
        public List<DexClass> getClasses(ApkEntry dex) {
            return classes.get(dex);
        }

        @Override
        public void close() {}

        @Override
        public List<DexClass> dump() {
            return null;
        }
    }

    private static class FakeDexSplitter implements DexSplitter {
        private final ArrayListMultimap<ApkEntry, DexClass> classes;

        public FakeDexSplitter() {
            this.classes = ArrayListMultimap.create();
        }

        public void add(ApkEntry entry, DexClass... dex) {
            classes.putAll(entry, ImmutableList.copyOf(dex));
        }

        @Override
        public List<DexClass> split(ApkEntry dex, Predicate<DexClass> keepCode) {
            return classes.get(dex);
        }
    }
}

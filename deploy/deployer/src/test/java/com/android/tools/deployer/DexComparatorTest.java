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

import static org.junit.Assert.*;

import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.DexClass;
import com.android.tools.deployer.model.FileDiff;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.Test;

public class DexComparatorTest {

    @Test
    public void testNoDiff() throws DeployerException {
        DexComparator comparator = new DexComparator();
        List<FileDiff> diffs = new ArrayList<>();
        List<DexClass> classes = comparator.compare(diffs, new FakeDexSplitter());
        assertTrue(classes.isEmpty());
    }

    @Test
    public void testNoChanges() throws DeployerException {
        DexComparator comparator = new DexComparator();
        List<FileDiff> diffs = new ArrayList<>();

        Apk oldApk = new Apk("base.apk", "abcd", null, ImmutableList.of(), null);
        ApkEntry oldFile = new ApkEntry("a.dex", 1, oldApk);

        // A similar apk that contains a dex with different checksum
        // but actually the same classes (different order for example)
        Apk newApk = new Apk("base.apk", "efgh", null, ImmutableList.of(), null);
        ApkEntry newFile = new ApkEntry("a.dex", 2, newApk);

        diffs.add(new FileDiff(oldFile, newFile, FileDiff.Status.MODIFIED));

        List<DexClass> classes = comparator.compare(diffs, new FakeDexSplitter());
        assertTrue(classes.isEmpty());
    }

    @Test
    public void testOneClassChange() throws DeployerException {
        DexComparator comparator = new DexComparator();
        List<FileDiff> diffs = new ArrayList<>();

        Apk oldApk = new Apk("base.apk", "abcd", null, ImmutableList.of(), null);
        ApkEntry oldFile = new ApkEntry("a.dex", 1, oldApk);

        Apk newApk = new Apk("base.apk", "efgh", null, ImmutableList.of(), null);
        ApkEntry newFile = new ApkEntry("a.dex", 3, newApk);

        diffs.add(new FileDiff(oldFile, newFile, FileDiff.Status.MODIFIED));

        List<DexClass> classes = comparator.compare(diffs, new FakeDexSplitter());
        assertEquals(1, classes.size());
        assertEquals(0x0012, classes.get(0).checksum);
        assertEquals("A", classes.get(0).name);
    }

    @Test
    public void testOneClassAdded() {
        DexComparator comparator = new DexComparator();
        List<FileDiff> diffs = new ArrayList<>();

        Apk oldApk = new Apk("base.apk", "abcd", null, ImmutableList.of(), null);
        ApkEntry oldFile = new ApkEntry("a.dex", 1, oldApk);

        Apk newApk = new Apk("base.apk", "efgh", null, ImmutableList.of(), null);
        ApkEntry newFile = new ApkEntry("a.dex", 4, newApk);

        diffs.add(new FileDiff(oldFile, newFile, FileDiff.Status.MODIFIED));

        try {
            comparator.compare(diffs, new FakeDexSplitter());
            fail();
        } catch (DeployerException e) {
            assertEquals(DeployerException.Error.CANNOT_SWAP_NEW_CLASS, e.getError());
        }
    }

    static class FakeDexSplitter implements DexSplitter {

        @Override
        public List<DexClass> split(ApkEntry dex, Predicate<DexClass> keepCode)
                throws DeployerException {
            ArrayList<DexClass> classes = new ArrayList<>();
            if (dex.checksum == 1) {
                classes.add(new DexClass("A", 0x0011, new byte[0], dex));
            } else if (dex.checksum == 2) {
                classes.add(new DexClass("A", 0x0011, new byte[0], dex));
            } else if (dex.checksum == 3) {
                classes.add(new DexClass("A", 0x0012, new byte[0], dex));
            } else if (dex.checksum == 4) {
                classes.add(new DexClass("A", 0x0011, new byte[0], dex));
                classes.add(new DexClass("B", 0x0013, new byte[0], dex));
            }
            for (int i = 0; i < classes.size(); i++) {
                DexClass cls = classes.get(i);
                if (keepCode == null || !keepCode.test(cls)) {
                    classes.set(i, new DexClass(cls.name, cls.checksum, null, cls.dex));
                }
            }
            return classes;
        }
    }
}

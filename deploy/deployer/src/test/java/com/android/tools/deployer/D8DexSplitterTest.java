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
package com.android.tools.deployer;

import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.DexClass;
import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.origin.Origin;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import org.junit.Assert;
import org.junit.Test;

/**
 * Mainly focus on unit testing split and checksum functions in the dex splitter.
 *
 * <p>Interaction with database and such will be left for {@link DexComparatorTest}.
 */
public class D8DexSplitterTest {

    /** Verify that we are using D8's .class crc checksum when available. */
    @Test
    public void testD8EncodedClassCrc() throws Exception {
        // Any random class would do but we are going to do this inception style.
        Class target = this.getClass();

        // We will keep class that matches .class file crc of the target.
        long keepCrc = crc(buildClass(target));

        ApkEntry input = new InMemoryDexFile(true, target);

        D8InMemoryDexSplitter splitter = new D8InMemoryDexSplitter();
        Collection<DexClass> result = splitter.split(input, d -> d.checksum == keepCrc);
        Assert.assertNotNull(findClass(result, target).code);
    }

    /** Do a typical split, cache and diff operation without encoded checksum. */
    @Test
    public void testSplitWithoutD8EncodeChecksum() {
        Class target = SimpleJavaClass.class;

        // Split it once, simulating a database cache on deploy
        ApkEntry input = new InMemoryDexFile(false, target);
        final Map<String, Long> crcs = new HashMap<>();

        D8InMemoryDexSplitter splitter = new D8InMemoryDexSplitter();
        Collection<DexClass> result =
                splitter.split(
                        input,
                        clz -> {
                            crcs.put(clz.name, clz.checksum);
                            return false;
                        });
        Assert.assertNull(findClass(result, target).code);

        // Throw in some random class, it should affect the outcome.
        new InMemoryDexFile(false, target, this.getClass());
        splitter = new D8InMemoryDexSplitter();
        result = splitter.split(input, clz -> crcs.get(target.getName()).equals(clz.checksum));
        Assert.assertNotNull(findClass(result, target).code);
    }

    /** Do a typical split, cache and diff operation with encoded checksum. */
    @Test
    public void testSplitWithD8EncodeChecksum() {
        Class target = SimpleJavaClass.class;

        // Split it once, simulating a database cache on deploy
        ApkEntry input = new InMemoryDexFile(true, target);
        final Map<String, Long> crcs = new HashMap<>();

        D8InMemoryDexSplitter splitter = new D8InMemoryDexSplitter();
        Collection<DexClass> result =
                splitter.split(
                        input,
                        clz -> {
                            crcs.put(clz.name, clz.checksum);
                            return false;
                        });
        Assert.assertNull(findClass(result, target).code);

        // Throw in some random class, it should affect the outcome.
        new InMemoryDexFile(true, target, this.getClass());
        splitter = new D8InMemoryDexSplitter();
        result = splitter.split(input, clz -> crcs.get(target.getName()).equals(clz.checksum));
        Assert.assertNotNull(findClass(result, target).code);
    }

    private static class D8InMemoryDexSplitter extends D8DexSplitter {
        @Override
        protected byte[] readDex(ApkEntry entry) {
            InMemoryDexFile dex =
                    (InMemoryDexFile) entry; // This test will not work with actual files.
            return buildDex(dex.encodedChecksum, dex.classes);
        }
    }

    private static class InMemoryDexFile extends ApkEntry {
        private Class[] classes;
        private boolean encodedChecksum;

        private InMemoryDexFile(boolean encodedChecksum, Class... classes) {
            super("classes.dex", 0, null);
            this.classes = classes;
            this.encodedChecksum = encodedChecksum;
        }
    }

    private static long crc(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }

    // Local dex class by simple name.
    private static DexClass findClass(Collection<DexClass> collection, Class c) {
        for (DexClass dexClass : collection) {
            if (dexClass.name.equals(c.getName())) {
                return dexClass;
            }
        }
        Assert.fail(c.getName() + " was not found in collection.");
        return null;
    }

    private static byte[] buildClass(Class clazz) throws IOException {
        return ByteStreams.toByteArray(clazz.getResourceAsStream(clazz.getSimpleName() + ".class"));
    }

    public static byte[] buildDex(boolean checksum, Class... classes) {
        DexBytesConsumer dexBytesConsumer = new DexBytesConsumer();

        try {
            D8Command.Builder builder =
                    D8Command.builder()
                            .setIncludeClassesChecksum(checksum)
                            .setProgramConsumer(dexBytesConsumer)
                            .setMinApiLevel(28)
                            .setDisableDesugaring(true);
            for (Class c : classes) {
                builder.addClassProgramData(buildClass(c), Origin.unknown());
            }
            D8.run(builder.build());
        } catch (Exception e) {
            Assert.fail(e.toString());
        }
        return dexBytesConsumer.dexData;
    }

    private static class DexBytesConsumer implements DexIndexedConsumer {
        private byte[] dexData = null;

        @Override
        public void accept(
                int fileIndex,
                ByteDataView data,
                Set<String> descriptors,
                DiagnosticsHandler handler) {
            Assert.assertEquals(0, fileIndex);
            Assert.assertNull(dexData);
            dexData = data.copyByteData();
        }

        @Override
        public void finished(DiagnosticsHandler handler) {
            Assert.assertNotNull(dexData);
        }
    }
}

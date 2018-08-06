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
package com.android.tools.deploy.swapper;

import com.android.tools.r8.*;
import com.android.tools.r8.origin.Origin;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Given two sets of dex archives, this class computes individual classes that are modified between
 * the two and extract them in a way that is suitable for code hot swapping.
 */
public class DexArchiveComparator {

    /** Name and dex data pair that represents a class that has been modified. */
    public static class Entry {
        public final String name;
        public final byte[] dex;

        public Entry(String name, byte[] dex) {
            this.name = name;
            this.dex = dex;
        }
    }

    /** A set of Entries to presents the difference between two archives. */
    public static class Result {
        public final List<Entry> changedClasses = new ArrayList<>();
        public final List<Entry> addedClasses = new ArrayList<>();
        // Deleted classes are ignored as it is irrelevant to hot swapping.
    }

    public Result compare(DexArchive oldApk, DexArchive newApk) throws Exception {
        Map<String, DexFile> oldDexFiles = oldApk.getDexFiles();
        Map<String, DexFile> newDexFiles = newApk.getDexFiles();

        List<String> changedDexFiles = new ArrayList<>(newDexFiles.size());

        if (oldDexFiles.size() != newDexFiles.size()) {
            throw new UnsupportedOperationException("new .dex added. Not supported.");
        }

        for (Map.Entry<String, DexFile> entry : newDexFiles.entrySet()) {
            String dexFileName = entry.getKey();
            DexFile newDexFile = entry.getValue();
            DexFile oldDexFile = oldDexFiles.get(dexFileName);
            assert oldDexFile != null : "Different dex file naming scheme between APK?";
            if (oldDexFile.differs(newDexFile)) {
                changedDexFiles.add(dexFileName);
            }
        }

        Map<String, Long> prevDexesChecksum = new HashMap<>();
        List<byte[]> newDexes = new ArrayList<>(changedDexFiles.size());

        for (String s : changedDexFiles) {
            prevDexesChecksum.putAll(oldDexFiles.get(s).getClasssesChecksum());
            newDexes.add(newDexFiles.get(s).getCode());
        }

        return compare(prevDexesChecksum, newDexes);
    }

    /**
     * The core comparision method that invokes D8. This algorithm will be improved but right now it
     * just split all the individual classes and perform a byte code comparison.
     */
    private Result compare(Map<String, Long> prevDexesChecksum, List<byte[]> newDexes) {
        Result result = new Result();
        NewDexConsumer newDexConsumer = new NewDexConsumer(prevDexesChecksum, result);

        for (byte[] newDex : newDexes) {
            try {
                D8Command.Builder newBuilder = D8Command.builder();
                newBuilder.addDexProgramData(newDex, Origin.unknown());
                newBuilder.setDisableDesugaring(true);
                newBuilder.setProgramConsumer(newDexConsumer);
                D8.run(newBuilder.build());
            } catch (CompilationFailedException e) {
                throw new RuntimeException(e);
            }
        }

        if (!newDexConsumer.done) {
            synchronized (this) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return null;
                }
            }
        }

        return result;
    }

    /**
     * Dex consumer that reads a set of new dex arhives and compare it to the results from {@link
     * PreviousDexConsumer}.
     */
    private class NewDexConsumer implements DexFilePerClassFileConsumer {
        private final Map<String, Long> prevDexesChecksum;
        private final Result result;
        private boolean done = false;

        private NewDexConsumer(Map<String, Long> prevDexesChecksum, Result result) {
            this.prevDexesChecksum = prevDexesChecksum;
            this.result = result;
        }

        @Override
        public synchronized void accept(
                String name,
                byte[] newDexData,
                Set<String> descriptors,
                DiagnosticsHandler handler) {
            Long oldDexChecksum = prevDexesChecksum.get(name);
            if (oldDexChecksum == null) {
                result.addedClasses.add(new Entry(typeNameToClassName(name), newDexData));
            } else {
                CRC32 crc = new CRC32();
                crc.update(newDexData);
                if (crc.getValue() != oldDexChecksum) {
                    result.changedClasses.add(new Entry(typeNameToClassName(name), newDexData));
                }
            }
        }

        @Override
        public void finished(DiagnosticsHandler handler) {
            synchronized (DexArchiveComparator.this) {
                done = true;
                DexArchiveComparator.this.notify();
            }
        }
    }

    private byte[] getFileFromApk(File apk, String name) throws IOException {
        ZipInputStream zis = new ZipInputStream(new FileInputStream(apk));
        for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
            if (!entry.getName().equals(name)) {
                continue;
            }

            byte[] buffer = new byte[1024];
            ByteArrayOutputStream dexContent = new ByteArrayOutputStream();

            int len;
            while ((len = zis.read(buffer)) > 0) {
                dexContent.write(buffer, 0, len);
            }
            zis.closeEntry();
            zis.close();
            return dexContent.toByteArray();
        }
        zis.closeEntry();
        zis.close();
        return null;
    }

    /** VM type names to the more readable class names. */
    private static String typeNameToClassName(String typeName) {
        assert typeName.startsWith("L");
        assert typeName.endsWith(";");
        return typeName.substring(1, typeName.length() - 1).replace('/', '.');
    }
}

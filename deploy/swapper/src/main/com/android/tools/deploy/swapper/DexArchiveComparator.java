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

    /** Compute differences between a set of paths in two APKs. */
    public Result compare(File oldApk, File newApk, String[] paths) throws Exception {
        int len = paths.length;
        List<byte[]> oldDexes = new ArrayList<>(len);
        List<byte[]> newDexes = new ArrayList<>(len);

        for (int i = 0; i < len; i++) {
            assert paths[i].endsWith(".dex");
            oldDexes.add(getFileFromApk(oldApk, paths[i]));
            newDexes.add(getFileFromApk(newApk, paths[i]));
        }

        return compare(oldDexes, newDexes);
    }

    /**
     * The core comparision method that invokes D8. This algorithm will be improved but right now it
     * just split all the individual classes and perform a byte code comparison.
     */
    private Result compare(List<byte[]> prevDexes, List<byte[]> newDexes) {
        Result result = new Result();
        PreviousDexConsumer previousDexConsumer = new PreviousDexConsumer();
        NewDexConsumer newDexConsumer = new NewDexConsumer(previousDexConsumer, result);

        for (byte[] prevDex : prevDexes) {
            try {
                D8Command.Builder prevBuilder = D8Command.builder();
                prevBuilder.addDexProgramData(prevDex, Origin.unknown());
                prevBuilder.setDisableDesugaring(true);
                prevBuilder.setProgramConsumer(previousDexConsumer);
                D8.run(prevBuilder.build());
            } catch (CompilationFailedException e) {
                throw new RuntimeException(e);
            }
        }

        if (!previousDexConsumer.done) {
            synchronized (this) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return null;
                }
            }
        }

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

    /** Dex Consumer that reads a set of old dex archives. */
    private class PreviousDexConsumer implements DexFilePerClassFileConsumer {
        private final Map<String, byte[]> dexMap = new LinkedHashMap<>();
        private boolean done = false;

        @Override
        public synchronized void accept(
                String s, byte[] bytes, Set<String> set, DiagnosticsHandler handler) {
            dexMap.put(s, bytes);
        }

        @Override
        public void finished(DiagnosticsHandler handler) {
            synchronized (DexArchiveComparator.this) {
                done = true;
                DexArchiveComparator.this.notify();
            }
        }
    }

    /**
     * Dex consumer that reads a set of new dex arhives and compare it to the results from {@link
     * PreviousDexConsumer}.
     */
    private class NewDexConsumer implements DexFilePerClassFileConsumer {
        private final PreviousDexConsumer previousDexConsumer;
        private final Result result;
        private boolean done = false;

        private NewDexConsumer(PreviousDexConsumer previousDexConsumer, Result result) {
            this.previousDexConsumer = previousDexConsumer;
            this.result = result;
        }

        @Override
        public synchronized void accept(
                String name,
                byte[] newDexData,
                Set<String> descriptors,
                DiagnosticsHandler handler) {
            byte[] oldDexData = previousDexConsumer.dexMap.get(name);
            if (oldDexData == null) {
                result.addedClasses.add(new Entry(typeNameToClassName(name), newDexData));
            } else {
                if (!Arrays.equals(oldDexData, newDexData)) {
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

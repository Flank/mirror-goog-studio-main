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

import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.DexClass;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexFilePerClassFileConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.origin.Origin;
import com.android.tools.tracer.Trace;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class D8DexSplitter implements DexSplitter {

    @Override
    public List<DexClass> split(ApkEntry dex, Predicate<DexClass> keepCode) {
        try (Trace ignored = Trace.begin("split " + dex.name)) {
            List<DexClass> classes = new ArrayList<>();
            DexConsumer consumer = new DexConsumer(dex, classes, keepCode);
            D8Command.Builder newBuilder = D8Command.builder();
            newBuilder.addDexProgramData(readDex(dex), Origin.unknown());
            newBuilder.setDisableDesugaring(true);
            newBuilder.setProgramConsumer(consumer);
            D8.run(newBuilder.build());
            consumer.join();
            return classes;
        } catch (InterruptedException | CompilationFailedException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] readDex(ApkEntry dex) {
        // TODO Check if opening the file several times matters
        try (ZipFile file = new ZipFile(dex.apk.path)) {
            ZipEntry entry = file.getEntry(dex.name);
            return ByteStreams.toByteArray(file.getInputStream(entry));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static class DexConsumer implements DexFilePerClassFileConsumer {
        private final List<DexClass> classes;
        private final CountDownLatch finished = new CountDownLatch(1);
        private final Predicate<DexClass> keepCode;
        private final ApkEntry dex;

        private DexConsumer(ApkEntry dex, List<DexClass> classes, Predicate<DexClass> keepCode) {
            this.dex = dex;
            this.classes = classes;
            this.keepCode = keepCode;
        }

        @Override
        public void finished(DiagnosticsHandler handler) {
            finished.countDown();
        }

        public void join() throws InterruptedException {
            finished.await();
        }

        @Override
        public synchronized void accept(
                String name, byte[] dexData, Set<String> descriptors, DiagnosticsHandler handler) {
            String className = typeNameToClassName(name);
            CRC32 crc = new CRC32();
            crc.update(dexData);
            long newChecksum = crc.getValue();
            DexClass clazz = new DexClass(className, newChecksum, null, dex);
            if (keepCode != null && keepCode.test(clazz)) {
                clazz = new DexClass(className, newChecksum, dexData, dex);
            }
            classes.add(clazz);
        }

        /** VM type names to the more readable class names. */
        private static String typeNameToClassName(String typeName) {
            assert typeName.startsWith("L");
            assert typeName.endsWith(";");
            return typeName.substring(1, typeName.length() - 1).replace('/', '.');
        }
    }
}

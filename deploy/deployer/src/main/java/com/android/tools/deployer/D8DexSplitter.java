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
import com.android.tools.r8.ByteDataView;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class D8DexSplitter implements DexSplitter {

    /** @param keepCode Needs to be threadsafe. */
    @Override
    public Collection<DexClass> split(ApkEntry dex, Predicate<DexClass> keepCode) {
        try (Trace ignored = Trace.begin("split " + dex.name)) {
            D8Command.Builder newBuilder = D8Command.builder();
            DexConsumer consumer = new DexConsumer(dex, keepCode);
            newBuilder.addDexProgramData(readDex(dex), Origin.unknown());
            newBuilder.setDexClassChecksumFilter(consumer::parseFilter);
            newBuilder.setProgramConsumer(consumer);
            D8.run(newBuilder.build());
            consumer.join();
            return consumer.classes.values();
        } catch (InterruptedException | CompilationFailedException e) {
            throw new RuntimeException(e);
        }
    }

    protected byte[] readDex(ApkEntry dex) {
        // TODO Check if opening the file several times matters
        try (ZipFile file = new ZipFile(dex.apk.path)) {
            ZipEntry entry = file.getEntry(dex.name);
            return ByteStreams.toByteArray(file.getInputStream(entry));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static class DexConsumer implements DexFilePerClassFileConsumer {
        private final Map<String, DexClass> classes = new HashMap<>();

        private final CountDownLatch finished = new CountDownLatch(1);
        private final Predicate<DexClass> keepCode;
        private final ApkEntry dex;

        private DexConsumer(ApkEntry dex, Predicate<DexClass> keepCode) {
            this.dex = dex;
            this.keepCode = keepCode;
        }

        @Override
        public void finished(DiagnosticsHandler handler) {
            finished.countDown();
        }

        public void join() throws InterruptedException {
            finished.await();
        }

        /**
         * Performs a filter of Java classes during parse time. If we can already decide if we are
         * not keeping the code of this class, we tell the compiler to skip parsing the rest of the
         * class body.
         */
        public boolean parseFilter(String classDescriptor, Long checksum) {
            DexClass c =
                    new DexClass(
                            typeNameToClassName(classDescriptor),
                            checksum == null ? 0 : checksum,
                            null,
                            dex);

            // D8 is free to use multiple thread to parse (although it mostly do it base on number of dex input). We can
            // potentially have multiple parsing calling us back.
            synchronized (this) {
                classes.put(classDescriptor, c);
            }
            if (keepCode != null) {
                return keepCode.test(c);
            } else {
                return false;
            }
        }

        @Override
        public synchronized void accept(
                String name,
                ByteDataView data,
                Set<String> descriptors,
                DiagnosticsHandler handler) {
            DexClass clazz = classes.get(name);
            String className = typeNameToClassName(name);

            // It is possible that some classes has no checksum information. They would not appear on the previous filter step.
            if (clazz == null) {
                CRC32 crc = new CRC32();
                crc.update(data.getBuffer(), data.getOffset(), data.getLength());
                long newChecksum = crc.getValue();
                clazz = new DexClass(className, newChecksum, null, dex);
                classes.put(name, clazz);
            }

            if (keepCode != null && keepCode.test(clazz)) {
                classes.put(
                        name, new DexClass(className, clazz.checksum, data.copyByteData(), dex));
            }
        }
    }

    /** VM type names to the more readable class names. */
    private static String typeNameToClassName(String typeName) {
        assert typeName.startsWith("L");
        assert typeName.endsWith(";");
        return typeName.substring(1, typeName.length() - 1).replace('/', '.');
    }
}

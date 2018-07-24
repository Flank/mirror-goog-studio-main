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

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexFilePerClassFileConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.origin.Origin;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * A representation of the files within a newly built APK that is on the host's disk. What's
 * important is that the content of the files should be readily available for dex code splitting.
 */
public final class OnHostDexFile extends DexFile {
    private final byte[] code;
    private Map<String, Long> classesChecksum;

    public OnHostDexFile(long checksum, String name, byte[] code) {
        super(checksum, name);
        this.code = code;
    }

    /**
     * The checksum map is only lazily computed since typical operation should not require all the
     * checksums.
     */
    private void fillChecksumIfNeeded() {
        if (classesChecksum != null) {
            return;
        }
        classesChecksum = new HashMap<>();

        try {
            ChecksumGatheringConsumer consumer = new ChecksumGatheringConsumer();
            D8Command.Builder newBuilder = D8Command.builder();
            newBuilder.addDexProgramData(code, Origin.unknown());
            newBuilder.setDisableDesugaring(true);
            newBuilder.setProgramConsumer(consumer);
            D8.run(newBuilder.build());

            if (!consumer.done) {
                synchronized (consumer) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        } catch (CompilationFailedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<String, Long> getClasssesChecksum() {
        fillChecksumIfNeeded();
        return classesChecksum;
    }

    @Override
    public byte[] getCode() {
        return code;
    }

    private class ChecksumGatheringConsumer implements DexFilePerClassFileConsumer {
        private boolean done = false;

        @Override
        public synchronized void finished(DiagnosticsHandler handler) {
            done = true;
            notify();
        }

        @Override
        public synchronized void accept(
                String name, byte[] dexData, Set<String> descriptors, DiagnosticsHandler handler) {
            // TODO(acleung): Change to a more robust checksum.
            CRC32 crc = new CRC32();
            crc.update(dexData);
            classesChecksum.put(name, crc.getValue());
        }
    }
}

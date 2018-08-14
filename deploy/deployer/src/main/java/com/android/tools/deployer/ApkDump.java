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

import com.google.devrel.gmscore.tools.apk.arsc.*;
import java.nio.ByteBuffer;
import java.util.*;

public class ApkDump {

    private final byte[] contentDirectory;
    private final byte[] signature;
    private final String name;

    // Cached lazy-initialized values
    private String digest = null;
    private HashMap<String, Long> crcs = null;

    /**
     * A class to manipulate an apk central directory and signature blokc dump files.
     *
     * <p>CD dump files contain the metadata of an apk located on a remote device. A cd dump file
     * contains only the Central Directory and the End of Central Directory.
     *
     * <p>Block dump files contain the V2/V3 Signature Block located between the APK payload and the
     * APK CD record.
     */
    public ApkDump(String name, byte[] contentDirectory, byte[] signature) {
        this.name = name;
        this.contentDirectory = contentDirectory;
        this.signature = signature;
    }

    public HashMap<String, Long> getCrcs() {
        if (crcs != null) {
            return crcs;
        }
        crcs = readCrcs();
        return crcs;
    }

    public String getDigest() {
        if (digest != null) {
            return digest;
        }
        digest = generateDigest();
        return digest;
    }

    // Generates a hash for a given APK. If there is a signature block, hash it. Otherwise, hash the Central Directory record.
    private String generateDigest() {
        byte[] data = signature != null ? signature : contentDirectory;
        ByteBuffer buffer = ByteBuffer.wrap(data);
        return ZipUtils.digest(buffer);
    }

    private HashMap<String, Long> readCrcs() {
        ByteBuffer buffer = ByteBuffer.wrap(contentDirectory);
        return ZipUtils.readCrcs(buffer);
    }
}

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

import com.google.common.io.BaseEncoding;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

public class ZipUtils {

    private static final int CENTRAL_DIRECTORY_FILE_HEADER_MAGIC = 0x02014b50;
    private static final int CENTRAL_DIRECTORY_FILE_HEADER_SIZE = 46;
    private static final String DIGEST_ALGORITHM = "SHA-1";

    public static HashMap<String, Long> readCrcs(byte[] buf) {
        ByteBuffer buffer = ByteBuffer.wrap(buf);
        return readCrcs(buffer);
    }

    public static HashMap<String, Long> readCrcs(ByteBuffer buf) {
        buf.order(ByteOrder.LITTLE_ENDIAN);
        HashMap<String, Long> crcs = new HashMap<>();
        while (buf.remaining() >= CENTRAL_DIRECTORY_FILE_HEADER_SIZE
                && buf.getInt() == CENTRAL_DIRECTORY_FILE_HEADER_MAGIC) {

            buf.position(buf.position() + 12);
            // short version = buf.getShort();
            // short versionNeeded = buf.getShort();
            // short flags = buf.getShort();
            // short compression = buf.getShort();
            // short modTime = buf.getShort();
            // short modDate = buf.getShort();

            long crc32 = buf.getInt() & 0xFFFFFFFFL;

            buf.position(buf.position() + 8);
            // int compressedSize = buf.getInt();
            // int decompressedSize = buf.getInt();
            short pathLength = buf.getShort();
            short extraLength = buf.getShort();
            short commentLength = buf.getShort();

            buf.position(buf.position() + 12);
            // buf.getShort();
            // buf.getShort();
            // buf.getInt();
            // buf.getInt();
            byte[] string = new byte[pathLength];
            buf.get(string);
            String name = new String(string, Charset.forName("UTF-8"));
            buf.position(buf.position() + extraLength + commentLength);
            crcs.put(name, crc32);
        }
        return crcs;
    }

    public static String digest(ByteBuffer buffer) {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new DeployerException("MessageDigest:" + DIGEST_ALGORITHM + " unavailable.", e);
        }
        // TODO: Parse the block and hash the top level signature instead of hashing the entire block.
        messageDigest.update(buffer);
        byte[] digestBytes = messageDigest.digest();
        return BaseEncoding.base16().lowerCase().encode(digestBytes);
    }
}

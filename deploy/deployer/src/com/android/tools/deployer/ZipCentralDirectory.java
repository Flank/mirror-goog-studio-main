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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.HashMap;

public class ZipCentralDirectory {

    private static final int CENTRAL_DIRECTORY_FILE_HEADER_MAGIC = 0x02014b50;
    private final File file;

    public ZipCentralDirectory(String path) {
        this(new File(path));
    }

    public ZipCentralDirectory(File f) {
        file = f;
    }

    public void getCrcs(HashMap<String, Long> crcs) {
        try {
            if (!file.exists()) {
                throw new DeployerException(
                        "CD dump file '" + file.getAbsolutePath() + "' does not exist.");
            }

            FileInputStream inFile = new FileInputStream(file);
            FileChannel inChannel = inFile.getChannel();
            ByteBuffer buf =
                    ByteBuffer.allocate((int) file.length()).order(ByteOrder.LITTLE_ENDIAN);

            int readResult = inChannel.read(buf);
            buf.flip();
            if (readResult == -1) {
                throw new DeployerException(
                        "Unable to read from cd dump:'" + file.getAbsolutePath() + "'");
            }

            while (buf.limit() - buf.position() > 4
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
                String name = new String(buf.array(), buf.position(), pathLength);
                buf.position(buf.position() + pathLength + extraLength + commentLength);
                crcs.put(name, crc32);
            }

            buf.clear();
            inFile.close();
        } catch (IOException e) {
            throw new DeployerException(
                    "Unable to get crcs for remote apk '" + file.getAbsolutePath() + "'.", e);
        }
    }
}

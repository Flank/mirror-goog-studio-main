/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.pixelprobe.decoder;

import com.android.tools.pixelprobe.util.Bytes;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Decodes JPEG streams. Accepts the "jpeg" and "jpg" format strings.
 */
final class JpegDecoder extends Decoder {
    private static final byte[] JPEG_HEADER = Bytes.fromHexString("ffd8");

    JpegDecoder() {
        super("jpg", "jpeg");
    }

    @Override
    public boolean accept(InputStream in) {
        try {
            // If the stream begins with the magic 2 byte marker we assume
            // it's a valid JPEG
            byte[] data = new byte[JPEG_HEADER.length];
            int read = in.read(data);
            if (read == JPEG_HEADER.length) {
                return Arrays.equals(data, JPEG_HEADER);
            }
        } catch (IOException e) {
            // Ignore
        }
        return false;
    }
}

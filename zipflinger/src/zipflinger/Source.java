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

package zipflinger;

import com.android.annotations.NonNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

abstract class Source {
    private final String name;
    private final byte[] nameBytes;
    private boolean aligned = false;

    protected long compressedSize;
    protected long uncompressedSize;
    protected int crc;
    protected short compressionFlag;

    protected Source(@NonNull String name) {
        this.name = name;
        nameBytes = name.getBytes(StandardCharsets.UTF_8);
    }

    @NonNull
    String getName() {
        return name;
    }

    @NonNull
    byte[] getNameBytes() {
        return nameBytes;
    }

    boolean isAligned() {
        return aligned;
    }

    public void align() {
        this.aligned = true;
    }

    int getCrc() {
        return crc;
    }

    long getCompressedSize() {
        return compressedSize;
    }

    long getUncompressedSize() {
        return uncompressedSize;
    }

    short getCompressionFlag() {
        return compressionFlag;
    }

    // Guaranteed to be called before writeTo
    abstract void prepare() throws IOException;

    abstract void writeTo(@NonNull ZipWriter writer) throws IOException;
}

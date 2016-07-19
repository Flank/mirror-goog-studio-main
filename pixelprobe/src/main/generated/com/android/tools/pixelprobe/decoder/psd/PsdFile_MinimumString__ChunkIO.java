package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class PsdFile_MinimumString__ChunkIO {
    static PsdFile.MinimumString read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.MinimumString minimumString = new PsdFile.MinimumString();
        stack.addFirst(minimumString);

        int size = 0;
        long byteCount = 0;

        minimumString.length = in.readInt() & 0xffffffffL;
        byteCount = Math.max(minimumString.length, 4);
        minimumString.value = ChunkUtils.readString(in, byteCount, Charset.forName("ISO-8859-1"));

        stack.removeFirst();
        return minimumString;
    }
}

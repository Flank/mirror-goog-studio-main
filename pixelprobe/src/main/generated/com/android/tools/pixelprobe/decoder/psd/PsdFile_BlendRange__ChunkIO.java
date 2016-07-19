package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdFile_BlendRange__ChunkIO {
    static PsdFile.BlendRange read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.BlendRange blendRange = new PsdFile.BlendRange();
        stack.addFirst(blendRange);

        int size = 0;
        long byteCount = 0;

        blendRange.srcBlackIn = (short) (in.readByte() & 0xff);
        blendRange.srcWhiteIn = (short) (in.readByte() & 0xff);
        blendRange.srcBlackOut = (short) (in.readByte() & 0xff);
        blendRange.srcWhiteOut = (short) (in.readByte() & 0xff);
        blendRange.dstBlackIn = (short) (in.readByte() & 0xff);
        blendRange.dstWhiteIn = (short) (in.readByte() & 0xff);
        blendRange.dstBlackOut = (short) (in.readByte() & 0xff);
        blendRange.dstWhiteOut = (short) (in.readByte() & 0xff);

        stack.removeFirst();
        return blendRange;
    }
}

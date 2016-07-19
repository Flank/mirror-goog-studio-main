package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdFile_DescriptorItem_Enumerated__ChunkIO {
    static PsdFile.DescriptorItem.Enumerated read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.DescriptorItem.Enumerated enumerated = new PsdFile.DescriptorItem.Enumerated();
        stack.addFirst(enumerated);

        int size = 0;
        long byteCount = 0;

        enumerated.type = PsdFile_MinimumString__ChunkIO.read(in, stack);
        enumerated.value = PsdFile_MinimumString__ChunkIO.read(in, stack);

        stack.removeFirst();
        return enumerated;
    }
}

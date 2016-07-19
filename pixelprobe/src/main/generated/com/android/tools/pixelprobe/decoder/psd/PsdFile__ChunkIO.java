package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdFile__ChunkIO {
    static PsdFile read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile psdFile = new PsdFile();
        stack.addFirst(psdFile);

        int size = 0;
        long byteCount = 0;

        psdFile.header = PsdFile_Header__ChunkIO.read(in, stack);
        psdFile.colorData = PsdFile_ColorData__ChunkIO.read(in, stack);
        psdFile.resources = PsdFile_ImageResources__ChunkIO.read(in, stack);
        psdFile.layersInfo = PsdFile_LayersInformation__ChunkIO.read(in, stack);
        psdFile.imageData = PsdFile_ImageData__ChunkIO.read(in, stack);

        stack.removeFirst();
        return psdFile;
    }
}

package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class PsdFile_LayerProperty__ChunkIO {
    static PsdFile.LayerProperty read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.LayerProperty layerProperty = new PsdFile.LayerProperty();
        stack.addFirst(layerProperty);

        int size = 0;
        long byteCount = 0;

        layerProperty.signature = ChunkUtils.readString(in, 4, Charset.forName("ISO-8859-1"));
        layerProperty.key = ChunkUtils.readString(in, 4, Charset.forName("ISO-8859-1"));
        layerProperty.length = in.readInt() & 0xffffffffL;
        byteCount = (layerProperty.length + 3) & ~3;
        in.pushRange(byteCount);
        if (layerProperty.key.equals("lmfx")) {
            layerProperty.data = PsdFile_LayerEffects__ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("lfx2")) {
            layerProperty.data = PsdFile_LayerEffects__ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("lsct")) {
            layerProperty.data = PsdFile_LayerSection__ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("luni")) {
            layerProperty.data = PsdFile_UnicodeString__ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("SoCo")) {
            layerProperty.data = PsdFile_SolidColorAdjustment__ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("iOpa")) {
            layerProperty.data = ChunkUtils.readByte(in, byteCount);
        } else if (layerProperty.key.equals("TySh")) {
            layerProperty.data = PsdFile_TypeToolObject__ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("vmsk")) {
            layerProperty.data = PsdFile_ShapeMask__ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("vsms")) {
            layerProperty.data = PsdFile_ShapeMask__ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("vscg")) {
            layerProperty.data = PsdFile_ShapeGraphics__ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("vstk")) {
            layerProperty.data = PsdFile_ShapeStroke__ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("Lr16")) {
            layerProperty.data = PsdFile_LayersList__ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("Lr32")) {
            layerProperty.data = PsdFile_LayersList__ChunkIO.read(in, stack);
        }
        in.popRange();

        stack.removeFirst();
        return layerProperty;
    }
}

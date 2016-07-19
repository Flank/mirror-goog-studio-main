package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;

final class PsdFile_RawLayer__ChunkIO {
    static PsdFile.RawLayer read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.RawLayer rawLayer = new PsdFile.RawLayer();
        stack.addFirst(rawLayer);

        int size = 0;
        long byteCount = 0;

        rawLayer.top = in.readInt();
        rawLayer.left = in.readInt();
        rawLayer.bottom = in.readInt();
        rawLayer.right = in.readInt();
        rawLayer.channels = in.readShort();
        rawLayer.channelsInfo = new ArrayList<PsdFile.ChannelInformation>();
        size = rawLayer.channels;
        PsdFile.ChannelInformation channelInformation;
        for (int i = 0; i < size; i++) {
            channelInformation = PsdFile_ChannelInformation__ChunkIO.read(in, stack);
            rawLayer.channelsInfo.add(channelInformation);
        }
        rawLayer.signature = ChunkUtils.readString(in, 4, Charset.forName("ISO-8859-1"));
        ChunkUtils.checkState(rawLayer.signature.equals("8BIM"),
                "Value read in signature does not match expected value");
        rawLayer.blendMode = ChunkUtils.readString(in, 4, Charset.forName("ISO-8859-1"));
        rawLayer.opacity = (short) (in.readByte() & 0xff);
        rawLayer.clipping = in.readByte();
        rawLayer.flags = in.readByte();
        /* rawLayer.filler */
        ChunkUtils.skip(in, 1);
        rawLayer.extraLength = in.readInt() & 0xffffffffL;
        byteCount = rawLayer.extraLength;
        in.pushRange(byteCount);
        rawLayer.extras = PsdFile_LayerExtras__ChunkIO.read(in, stack);
        in.popRange();

        stack.removeFirst();
        return rawLayer;
    }
}

package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class PsdFile_DescriptorItem_Reference_Item__ChunkIO {
    static PsdFile.DescriptorItem.Reference.Item read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.DescriptorItem.Reference.Item item = new PsdFile.DescriptorItem.Reference.Item();
        stack.addFirst(item);

        int size = 0;
        long byteCount = 0;

        item.type = ChunkUtils.readString(in, 4, Charset.forName("ISO-8859-1"));
        if (item.type.equals("Enmr")) {
            item.data = PsdFile_DescriptorItem_Enumerated__ChunkIO.read(in, stack);
        } else if (item.type.equals("Clss")) {
            item.data = PsdFile_DescriptorItem_ClassType__ChunkIO.read(in, stack);
        } else if (item.type.equals("Idnt")) {
            item.data = in.readInt();
        } else if (item.type.equals("indx")) {
            item.data = in.readInt();
        } else if (item.type.equals("name")) {
            item.data = PsdFile_UnicodeString__ChunkIO.read(in, stack);
        } else if (item.type.equals("prop")) {
            item.data = PsdFile_DescriptorItem_Property__ChunkIO.read(in, stack);
        } else if (item.type.equals("rele")) {
            item.data = in.readInt();
        }

        stack.removeFirst();
        return item;
    }
}

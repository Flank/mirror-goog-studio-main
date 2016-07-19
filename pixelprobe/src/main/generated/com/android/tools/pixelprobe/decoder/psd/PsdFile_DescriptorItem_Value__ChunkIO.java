package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class PsdFile_DescriptorItem_Value__ChunkIO {
    static PsdFile.DescriptorItem.Value read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.DescriptorItem.Value value = new PsdFile.DescriptorItem.Value();
        stack.addFirst(value);

        int size = 0;
        long byteCount = 0;

        value.type = ChunkUtils.readString(in, 4, Charset.forName("ISO-8859-1"));
        if (value.type.equals("alis")) {
            value.data = PsdFile_FixedString__ChunkIO.read(in, stack);
        } else if (value.type.equals("bool")) {
            value.data = in.readByte() != 0;
        } else if (value.type.equals("comp")) {
            value.data = in.readLong();
        } else if (value.type.equals("doub")) {
            value.data = in.readDouble();
        } else if (value.type.equals("enum")) {
            value.data = PsdFile_DescriptorItem_Enumerated__ChunkIO.read(in, stack);
        } else if (value.type.equals("GlbC")) {
            value.data = PsdFile_DescriptorItem_ClassType__ChunkIO.read(in, stack);
        } else if (value.type.equals("GlbO")) {
            value.data = PsdFile_Descriptor__ChunkIO.read(in, stack);
        } else if (value.type.equals("long")) {
            value.data = in.readInt();
        } else if (value.type.equals("obj" )) {
            value.data = PsdFile_DescriptorItem_Reference__ChunkIO.read(in, stack);
        } else if (value.type.equals("Objc")) {
            value.data = PsdFile_Descriptor__ChunkIO.read(in, stack);
        } else if (value.type.equals("TEXT")) {
            value.data = PsdFile_UnicodeString__ChunkIO.read(in, stack);
        } else if (value.type.equals("tdta")) {
            value.data = PsdFile_FixedByteArray__ChunkIO.read(in, stack);
        } else if (value.type.equals("type")) {
            value.data = PsdFile_DescriptorItem_ClassType__ChunkIO.read(in, stack);
        } else if (value.type.equals("UnFl")) {
            value.data = PsdFile_DescriptorItem_UnitFloat__ChunkIO.read(in, stack);
        } else if (value.type.equals("UntF")) {
            value.data = PsdFile_DescriptorItem_UnitDouble__ChunkIO.read(in, stack);
        } else if (value.type.equals("VlLs")) {
            value.data = PsdFile_DescriptorItem_ValueList__ChunkIO.read(in, stack);
        }

        stack.removeFirst();
        return value;
    }
}

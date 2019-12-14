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

package com.android.tools.agent.layoutinspector.testing;

import android.view.inspector.PropertyReader;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class EnumPropertyReader<E extends Enum<E>> {
    private final PropertyReader myReader;
    private final int myOffset;

    public EnumPropertyReader(@NonNull PropertyReader reader, int offset) {
        myReader = reader;
        myOffset = offset;
    }

    public void readBoolean(E e, boolean value) {
        myReader.readBoolean(indexOf(e), value);
    }

    public void readByte(E e, byte value) {
        myReader.readByte(indexOf(e), value);
    }

    public void readChar(E e, char value) {
        myReader.readChar(indexOf(e), value);
    }

    public void readDouble(E e, double value) {
        myReader.readDouble(indexOf(e), value);
    }

    public void readFloat(E e, float value) {
        myReader.readFloat(indexOf(e), value);
    }

    public void readInt(E e, int value) {
        myReader.readInt(indexOf(e), value);
    }

    public void readLong(E e, long value) {
        myReader.readLong(indexOf(e), value);
    }

    public void readShort(E e, short value) {
        myReader.readShort(indexOf(e), value);
    }

    public void readObject(E e, @Nullable Object value) {
        myReader.readObject(indexOf(e), value);
    }

    public void readColor(E e, int value) {
        myReader.readColor(indexOf(e), value);
    }

    public void readGravity(E e, int value) {
        myReader.readGravity(indexOf(e), value);
    }

    public void readIntEnum(E e, int value) {
        myReader.readIntEnum(indexOf(e), value);
    }

    public void readIntFlag(E e, int value) {
        myReader.readIntFlag(indexOf(e), value);
    }

    public void readResourceId(E e, int value) {
        myReader.readResourceId(indexOf(e), value);
    }

    private int indexOf(E e) {
        return myOffset + e.ordinal();
    }
}

/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.deploy.liveedit;

public class InvokeSpecialParent {
    int getHash() {
        return InvokeSpecialParent.class.hashCode();
    }

    int getArrayValue(int[] array, int index) {
        return array[index];
    }

    public boolean paramBoolean(boolean b) {
        return b;
    }

    public char paramChar(char c) {
        return c;
    }

    public byte paramByte(byte b) {
        return b;
    }

    public short paramShort(short s) {
        return s;
    }

    public int paramInt(int i) {
        return i;
    }

    public long paramLong(long l) {
        return l;
    }

    public float paramFloat(float f) {
        return f;
    }

    public double paramDouble(double d) {
        return d;
    }

    public Object paramObject(Object o) {
        return o;
    }

    public int[] paramArray(int[] i) {
        return i;
    }
}

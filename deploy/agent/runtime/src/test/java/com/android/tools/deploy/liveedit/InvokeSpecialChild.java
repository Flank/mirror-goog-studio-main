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

public class InvokeSpecialChild extends InvokeSpecialParent {

    @Override
    protected int getHash() {
        throw new IllegalStateException("This should never be called");
    }

    @Override
    int getArrayValue(int[] array, int index) {
        throw new IllegalStateException("This should never be called");
    }

    public int callSuperGetHash() {
        return super.getHash();
    }

    public int callgetArrayValue(int[] array, int index) {
        return super.getArrayValue(array, index);
    }

    @Override
    public boolean paramBoolean(boolean b) {
        throw new IllegalStateException("This should never be called");
    }

    public boolean callSuperParamBool(boolean b) {
        return super.paramBoolean(b);
    }

    @Override
    public char paramChar(char c) {
        throw new IllegalStateException("This should never be called");
    }

    public char callSuperParamChar(char c) {
        return super.paramChar(c);
    }

    @Override
    public byte paramByte(byte b) {
        throw new IllegalStateException("This should never be called");
    }

    public byte callSuperParamByte(byte b) {
        return super.paramByte(b);
    }

    @Override
    public short paramShort(short s) {
        throw new IllegalStateException("This should never be called");
    }

    public short callSuperParamShort(short s) {
        return super.paramShort(s);
    }

    @Override
    public int paramInt(int i) {
        throw new IllegalStateException("This should never be called");
    }

    public int callSuperParamInt(int i) {
        return super.paramInt(i);
    }

    @Override
    public long paramLong(long l) {
        throw new IllegalStateException("This should never be called");
    }

    public long callSuperParamLong(long l) {
        return super.paramLong(l);
    }

    @Override
    public float paramFloat(float f) {
        throw new IllegalStateException("This should never be called");
    }

    public float callSuperParamFloat(float f) {
        return super.paramFloat(f);
    }

    @Override
    public double paramDouble(double d) {
        throw new IllegalStateException("This should never be called");
    }

    public double callSuperParamDouble(double d) {
        return super.paramDouble(d);
    }

    @Override
    public Object paramObject(Object o) {
        throw new IllegalStateException("This should never be called");
    }

    public Object callSuperParamObject(Object b) {
        return super.paramObject(b);
    }

    @Override
    public int[] paramArray(int[] i) {
        throw new IllegalStateException("This should never be called");
    }

    public int[] callSuperParamArray(int[] i) {
        return super.paramArray(i);
    }
}

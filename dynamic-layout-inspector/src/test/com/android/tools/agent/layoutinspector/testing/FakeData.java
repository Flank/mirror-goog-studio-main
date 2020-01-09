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

public class FakeData {
    private byte myByteValue;
    private char myCharValue;
    private double myDoubleValue;
    private short myShortValue;
    private long myLongValue;

    public byte getByteValue() {
        return myByteValue;
    }

    public void setByteValue(byte byteValue) {
        myByteValue = byteValue;
    }

    public char getCharValue() {
        return myCharValue;
    }

    public void setCharValue(char charValue) {
        myCharValue = charValue;
    }

    public double getDoubleValue() {
        return myDoubleValue;
    }

    public void setDoubleValue(double doubleValue) {
        myDoubleValue = doubleValue;
    }

    public short getShortValue() {
        return myShortValue;
    }

    public void setShortValue(short shortValue) {
        myShortValue = shortValue;
    }

    public long getLongValue() {
        return myLongValue;
    }

    public void setLongValue(long longValue) {
        myLongValue = longValue;
    }
}

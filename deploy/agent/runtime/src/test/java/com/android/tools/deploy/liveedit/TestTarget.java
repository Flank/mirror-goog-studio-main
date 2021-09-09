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

import java.io.IOException;

class TestTarget {

    private static int getPrivateStaticInt() {
        return 2;
    }

    public static int getPublicStaticInt() {
        return 9;
    }

    private String field = "A_FIEID";

    private int six = 6;

    private int getSix() {
        return six;
    }

    private static int ten = 10;

    public int returnFive() {
        return 5;
    }

    private int memberInt = 0;
    private int staticInt = 0;

    public int returnSeventeen() {
        memberInt = 0;
        staticInt = 1;
        int[] x = new int[1];
        x[0] = ten;
        staticInt = memberInt = six + x.length + x[0] + staticInt;
        staticInt += this.hashCode();
        return memberInt + getPrivateStaticInt() + getPublicStaticInt() + memberInt + staticInt;
    }

    public String returnHappiness() {
        return "Happiness";
    }

    public String returnField() {
        return field;
    }

    public int returnPlusOne(int input) {
        return input + 1;
    }

    public byte returnByteFromArray() {
        byte[] array = new byte[2];
        array[1] = 0xA;
        return array[1];
    }

    public Object returnObjectFromArray() {
        Integer[] array = new Integer[2];
        array[1] = Integer.valueOf(666);
        return array[1];
    }

    public short returnShortFromArray() {
        short[] array = new short[2];
        array[1] = (short) 555;
        return array[1];
    }

    public char returnCharFromArray() {
        char[] array = new char[2];
        array[1] = (char) 555;
        return array[1];
    }

    public boolean returnBooleanFromArray() {
        boolean[] array = new boolean[2];
        array[1] = true;
        return array[1];
    }

    public int returnIntFromArray() {
        int[] array = new int[2];
        array[1] = 1234;
        return array[1];
    }

    public long returnLongFromArray() {
        long[] array = new long[2];
        array[1] = Integer.MAX_VALUE + 1L;
        return array[1];
    }

    public float returnFloatFromArray() {
        float[] array = new float[2];
        array[1] = 1.0f;
        return array[1];
    }

    public double returnDoubleFromArray() {
        double[] array = new double[2];
        array[1] = 1.0;
        return array[1];
    }

    Object instanceOfObject = new Object();
    public boolean isInstanceOf() {
        return instanceOfObject instanceof Object;
    }

    public int getPrivateField() {
        return getSix();
    }

    public Parent newParent() {
        return new Parent();
    }

    public Parent newParentWithParameter(int i) {
        return new Parent(i);
    }

    private long l1 = 1;
    private long l2 = 2;

    public long getLongFields() {
        return l1 + l2;
    }

    private float f1 = 1.0f;
    private float f2 = 2.0f;

    public float getFloatFields() {
        return f1 + f2;
    }

    private double d1 = 1.0;
    private double d2 = 2.0;

    public double getDoubleFields() {
        return d1 + d2;
    }

    private boolean z1 = true;
    private boolean z2 = true;

    public boolean getBooleanFields() {
        return z1 && z2;
    }

    private byte b1 = 1;
    private byte b2 = 1;

    public byte getByteFields() {
        return (byte) (b1 & b2);
    }

    private short s1 = 1;
    private short s2 = 1;

    public short getShortFields() {
        return (short) (s1 & s2);
    }

    private char c1 = 1;
    private char c2 = 1;

    public char getCharacterFields() {
        return (char) (c1 & c2);
    }

    private int i1 = 1;
    private int i2 = 1;

    public int getIntegerFields() {
        return i1 & i2;
    }

    public int callParentStaticPlusFive() {
        return Child.parentStaticPlusFive();
    }

    public void functionReturningVoid() {
        String x = "Hello";
        String y = " World";
        String helloWorld = x + y;
        return;
    }

    public int tryFinally() {
        try {
            return 1;
        } finally {
            return 2;
        }
    }

    public int tryCatch() {
        try {
            throw new IOException();
        } catch (IllegalStateException e) {
            return 2;
        } catch (RuntimeException e) {
            return 3;
        } catch (IOException e) {
            return 4;
        }
    }

    private boolean not(boolean x) {
        return !x;
    }

    public boolean invokeBooleanParamWithBool(boolean x) {
        boolean y = !x;
        boolean z = not(y);
        return z;
    }

    private static boolean staticNot(boolean b) {
        return !b;
    }

    public static boolean staticInvokeBooleanParamWithBool(boolean x) {
        boolean y = !x;
        boolean z = staticNot(y);
        return z;
    }

    public static int tableSwitch1to4(int value) {
        switch (value) {
            case 1:
                return 1;
            case 2:
                return 2;
            case 3:
                return 3;
            case 4:
                return 4;
            default:
                return -1;
        }
    }
}

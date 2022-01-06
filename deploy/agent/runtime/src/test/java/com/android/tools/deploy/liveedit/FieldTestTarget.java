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

class FieldTestTarget {
    public static int publicStaticIntField = 0;
    public static byte publicStaticByteField = 0;
    public static short publicStaticShortField = 0;
    public static long publicStaticLongField = 0;
    public static float publicStaticFloatField = 0;
    public static double publicStaticDoubleField = 0;
    public static boolean publicStaticBooleanField = false;
    public static char publicStaticCharField = 0;
    public static String publicStaticObjectField = "";

    public int publicIntField = 0;
    public byte publicByteField = 0;
    public short publicShortField = 0;
    public long publicLongField = 0;
    public float publicFloatField = 0;
    public double publicDoubleField = 0;
    public boolean publicBooleanField = false;
    public char publicCharField = 0;
    public String publicObjectField = "";

    private static int privateStaticIntField = 0;
    private static byte privateStaticByteField = 0;
    private static short privateStaticShortField = 0;
    private static long privateStaticLongField = 0;
    private static float privateStaticFloatField = 0;
    private static double privateStaticDoubleField = 0;
    private static boolean privateStaticBooleanField = false;
    private static char privateStaticCharField = 0;
    private static String privateStaticObjectField = "";

    private int privateIntField = 0;
    private byte privateByteField = 0;
    private short privateShortField = 0;
    private long privateLongField = 0;
    private float privateFloatField = 0;
    private double privateDoubleField = 0;
    private boolean privateBooleanField = false;
    private char privateCharField = 0;
    private String privateObjectField = "";

    // Integer fields

    private void setIntFields() {
        publicIntField = 111;
        privateIntField = 222;
    }

    private static void setStaticIntFields() {
        publicStaticIntField = 333;
        privateStaticIntField = 444;
    }

    public static int testIntFields() {
        FieldTestTarget instance = new FieldTestTarget();
        instance.setIntFields();
        setStaticIntFields();
        return instance.publicIntField
                + instance.privateIntField
                + publicStaticIntField
                + privateStaticIntField;
    }

    // Byte fields

    private void setByteFields() {
        publicByteField = 1;
        privateByteField = 2;
    }

    private static void setStaticByteFields() {
        publicStaticByteField = 3;
        privateStaticByteField = 4;
    }

    public static int testByteFields() {
        FieldTestTarget instance = new FieldTestTarget();
        instance.setByteFields();
        setStaticByteFields();
        return instance.publicByteField
                + instance.privateByteField
                + publicStaticByteField
                + privateStaticByteField;
    }

    // Short fields

    private void setShortFields() {
        publicShortField = 1;
        privateShortField = 2;
    }

    private static void setStaticShortFields() {
        publicStaticShortField = 3;
        privateStaticShortField = 4;
    }

    public static int testShortFields() {
        FieldTestTarget instance = new FieldTestTarget();
        instance.setShortFields();
        setStaticShortFields();
        return instance.publicShortField
                + instance.privateShortField
                + publicStaticShortField
                + privateStaticShortField;
    }

    // Long fields

    private void setLongFields() {
        publicLongField = 100000000L;
        privateLongField = 20000000L;
    }

    private static void setStaticLongFields() {
        publicStaticLongField = 30000000L;
        privateStaticLongField = 40000000L;
    }

    public static long testLongFields() {
        FieldTestTarget instance = new FieldTestTarget();
        instance.setLongFields();
        setStaticLongFields();
        return instance.publicLongField
                + instance.privateLongField
                + publicStaticLongField
                + privateStaticLongField;
    }

    // Float fields

    private void setFloatFields() {
        publicFloatField = 10.12f;
        privateFloatField = 20.15f;
    }

    private static void setStaticFloatFields() {
        publicStaticFloatField = 3.141f;
        privateStaticFloatField = 4.2f;
    }

    public static float testFloatFields() {
        FieldTestTarget instance = new FieldTestTarget();
        instance.setFloatFields();
        setStaticFloatFields();
        return instance.publicFloatField
                + instance.privateFloatField
                + publicStaticFloatField
                + privateStaticFloatField;
    }

    // Double fields

    private void setDoubleFields() {
        publicDoubleField = 100.12;
        privateDoubleField = 2.15;
    }

    private static void setStaticDoubleFields() {
        publicStaticDoubleField = 354.2;
        privateStaticDoubleField = 4124.1;
    }

    public static double testDoubleFields() {
        FieldTestTarget instance = new FieldTestTarget();
        instance.setDoubleFields();
        setStaticDoubleFields();
        return instance.publicDoubleField
                + instance.privateDoubleField
                + publicStaticDoubleField
                + privateStaticDoubleField;
    }

    // Boolean fields

    private void setBooleanFields() {
        publicBooleanField = true;
        privateBooleanField = true;
    }

    private static void setStaticBooleanFields() {
        publicStaticBooleanField = true;
        privateStaticBooleanField = true;
    }

    public static boolean testBooleanFields() {
        FieldTestTarget instance = new FieldTestTarget();
        instance.setBooleanFields();
        setStaticBooleanFields();
        return instance.publicBooleanField
                && instance.privateBooleanField
                && publicStaticBooleanField
                && privateStaticBooleanField;
    }

    // Char fields

    private void setCharFields() {
        publicCharField = 'a';
        privateCharField = 'b';
    }

    private static void setStaticCharFields() {
        publicStaticCharField = 'c';
        privateStaticCharField = 'd';
    }

    public static String testCharFields() {
        FieldTestTarget instance = new FieldTestTarget();
        instance.setCharFields();
        setStaticCharFields();
        return ""
                + instance.publicCharField
                + instance.privateCharField
                + publicStaticCharField
                + privateStaticCharField;
    }

    // Object fields

    private void setObjectFields() {
        publicObjectField = "hello";
        privateObjectField = "world";
    }

    private static void setStaticObjectFields() {
        publicStaticObjectField = "foo";
        privateStaticObjectField = "bar";
    }

    public static String testObjectFields() {
        FieldTestTarget instance = new FieldTestTarget();
        instance.setObjectFields();
        setStaticObjectFields();
        return instance.publicObjectField
                + instance.privateObjectField
                + publicStaticObjectField
                + privateStaticObjectField;
    }

    public static void setStaticBoolean() {
        boolean b = true;
        publicStaticBooleanField = b;
    }

    public static void setBoolean() {
        FieldTestTarget t = new FieldTestTarget();
        boolean b = true;
        t.publicBooleanField = b;
    }
}

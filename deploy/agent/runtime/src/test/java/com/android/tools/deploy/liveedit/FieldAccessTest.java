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

import static com.android.tools.deploy.liveedit.Utils.buildClass;

import org.junit.Assert;

public class FieldAccessTest {
    @org.junit.Test
    public void testIntFields() throws Exception {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "testIntFields", "()I")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(FieldTestTarget.testIntFields(), result);
    }

    @org.junit.Test
    public void testByteFields() throws Exception {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "testByteFields", "()I")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(FieldTestTarget.testByteFields(), result);
    }

    @org.junit.Test
    public void testShortFields() throws Exception {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "testShortFields", "()I")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(FieldTestTarget.testShortFields(), result);
    }

    @org.junit.Test
    public void testLongFields() throws Exception {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "testLongFields", "()J")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(FieldTestTarget.testLongFields(), result);
    }

    @org.junit.Test
    public void testFloatFields() throws Exception {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "testFloatFields", "()F")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(FieldTestTarget.testFloatFields(), result);
    }

    @org.junit.Test
    public void testDoubleFields() throws Exception {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "testDoubleFields", "()D")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(FieldTestTarget.testDoubleFields(), result);
    }

    @org.junit.Test
    public void testBooleanFields() throws Exception {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "testBooleanFields", "()Z")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(FieldTestTarget.testBooleanFields(), result);
    }

    @org.junit.Test
    public void testCharFields() throws Exception {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "testCharFields", "()Ljava/lang/String;")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(FieldTestTarget.testCharFields(), result);
    }

    @org.junit.Test
    public void testObjectFields() throws Exception {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "testObjectFields", "()Ljava/lang/String;")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(FieldTestTarget.testObjectFields(), result);
    }
}

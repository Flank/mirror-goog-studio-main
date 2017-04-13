/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.proguard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.text.ParseException;
import org.junit.Test;

public class ProguardUsageMapTest {
    @Test
    public void parseFileTest() throws IOException, ParseException {
        String seedsFile =
                "com.example.wkal.emptyapk.BuildConfig\n"
                        + "com.example.wkal.emptyapk.MyAbstractClas:\n"
                        + "    public abstract void abstractMethod(int,java.lang.String)\n"
                        + "    public abstract com.example.wkal.emptyapk.MyAbstractClas anotherAbstract(com.example.wkal.emptyapk.MyClass)\n"
                        + "    27:28:private void privateMethod()\n"
                        + "com.example.wkal.emptyapk.MyAbstractClas$1:\n"
                        + "    14:14:public void abstractMethod(int,java.lang.String)\n"
                        + "    18:18:public com.example.wkal.emptyapk.MyAbstractClas anotherAbstract(com.example.wkal.emptyapk.MyClass)\n"
                        + "com.example.wkal.emptyapk.MyClass:\n"
                        + "    private java.lang.String privateString\n"
                        + "com.example.wkal.emptyapk.MyClass$StaticClass\n"
                        + "com.example.wkal.emptyapk.MyClass$StaticClass$InnerClass\n"
                        + "com.example.wkal.emptyapk.R\n"
                        + "com.example.wkal.emptyapk.R$attr";

        StringReader reader = new StringReader(seedsFile);
        ProguardUsagesMap parser = ProguardUsagesMap.parse(reader);

        assertTrue(parser.hasClass("com.example.wkal.emptyapk.BuildConfig"));
        assertTrue(parser.hasClass("com.example.wkal.emptyapk.MyClass$StaticClass$InnerClass"));
        assertTrue(parser.hasClass("com.example.wkal.emptyapk.R"));
        assertFalse(parser.hasClass("someClass"));

        assertTrue(
                parser.hasMethod(
                        "com.example.wkal.emptyapk.MyAbstractClas",
                        "void abstractMethod(int,java.lang.String)"));
        assertTrue(
                parser.hasMethod(
                        "com.example.wkal.emptyapk.MyAbstractClas",
                        "com.example.wkal.emptyapk.MyAbstractClas anotherAbstract(com.example.wkal.emptyapk.MyClass)"));
        assertFalse(
                parser.hasMethod("com.example.wkal.emptyapk.MyAbstractClas", "void someMethod()"));
        assertFalse(parser.hasMethod("someClass", "void abstractMethod(int,java.lang.String)"));

        assertTrue(
                parser.hasField(
                        "com.example.wkal.emptyapk.MyClass", "java.lang.String privateString"));
        assertFalse(
                parser.hasField("com.example.wkal.emptyapk.MyClass", "java.lang.String someField"));
        assertFalse(parser.hasField("someClass", "java.lang.String privateString"));
    }
}

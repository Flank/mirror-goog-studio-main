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

public class ProguardSeedsMapTest {
    @Test
    public void parseFileTest() throws IOException, ParseException {
        String seedsFile =
                "android.support.annotation.Keep\n"
                        + "android.support.annotation.RestrictTo$Scope: android.support.annotation.RestrictTo$Scope[] values()\n"
                        + "android.support.annotation.RestrictTo$Scope: android.support.annotation.RestrictTo$Scope valueOf(java.lang.String)\n"
                        + "android.support.constraint.ConstraintLayout\n"
                        + "android.support.constraint.ConstraintLayout: ConstraintLayout(android.content.Context)\n"
                        + "android.support.constraint.ConstraintLayout: ConstraintLayout(android.content.Context,android.util.AttributeSet)\n"
                        + "android.support.constraint.ConstraintLayout: ConstraintLayout(android.content.Context,android.util.AttributeSet,int)\n"
                        + "android.support.constraint.R$id: int wrap\n"
                        + "android.support.constraint.R$styleable: int[] ConstraintLayout_Layout";

        StringReader reader = new StringReader(seedsFile);
        ProguardSeedsMap parser = ProguardSeedsMap.parse(reader);

        assertTrue(parser.hasClass("android.support.annotation.Keep"));
        assertTrue(parser.hasClass("android.support.constraint.ConstraintLayout"));
        assertFalse(parser.hasClass("someClass"));

        assertTrue(parser.hasMethod("android.support.annotation.RestrictTo$Scope", "values()"));
        assertTrue(
                parser.hasMethod(
                        "android.support.constraint.ConstraintLayout",
                        "ConstraintLayout(android.content.Context)"));
        assertFalse(parser.hasMethod("someClass", "ConstraintLayout(android.content.Context)"));

        assertTrue(parser.hasField("android.support.constraint.R$id", "wrap"));
        assertTrue(
                parser.hasField(
                        "android.support.constraint.R$styleable", "ConstraintLayout_Layout"));
        assertFalse(parser.hasField("android.support.constraint.R$styleable", "wrap"));
    }
}

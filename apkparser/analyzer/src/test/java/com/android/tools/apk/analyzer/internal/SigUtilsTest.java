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
package com.android.tools.apk.analyzer.internal;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;

public class SigUtilsTest {

    private ImmutableMap<String, String> testData =
            new ImmutableMap.Builder<String, String>()
                    .put("V", "void")
                    .put("Z", "boolean")
                    .put("B", "byte")
                    .put("C", "char")
                    .put("S", "short")
                    .put("I", "int")
                    .put("J", "long")
                    .put("F", "float")
                    .put("D", "double")
                    .put("[D", "double[]")
                    .put("[[I", "int[][]")
                    .put("Ljava/util/String;", "java.util.String")
                    .put("LTest;", "Test")
                    .put("[LTest;", "Test[]")
                    .put("[[LTest;", "Test[][]")
                    .put("[Ljava/util/String;", "java.util.String[]")
                    .put("[[Ljava/util/String;", "java.util.String[][]")
                    .build();

    @Test
    public void signatureToName() {
        testData.forEach((s, s2) -> assertEquals(s2, SigUtils.signatureToName(s)));
    }

    @Test
    public void typeToSignature() {
        testData.forEach((s, s2) -> assertEquals(s, SigUtils.typeToSignature(s2)));
    }
}

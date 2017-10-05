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

package com.android.build.gradle.internal;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import org.junit.Test;

/** Test cases for {@link CombinedInput}. */
public class CombinedInputTest {

    @Test
    public void test() {
        CombinedInput input = new CombinedInput();
        input.add("outputFile", new File("foo"));
        input.add("outputDirectory", new File("bar"));
        input.add("outputFile2", null);
        input.add("outputDirectory2", null);
        assertThat(input.toString())
                .isEqualTo(
                        "outputFile=foo\n"
                                + "outputDirectory=bar\n"
                                + "outputFile2=null\n"
                                + "outputDirectory2=null");

        CombinedInput newInput = new CombinedInput(input.toString());
        assertThat(newInput.toString())
                .isEqualTo(
                        "outputFile=foo\n"
                                + "outputDirectory=bar\n"
                                + "outputFile2=null\n"
                                + "outputDirectory2=null");
        newInput.add("newOutputFile", new File("baz"));
        assertThat(newInput.toString())
                .isEqualTo(
                        "outputFile=foo\n"
                                + "outputDirectory=bar\n"
                                + "outputFile2=null\n"
                                + "outputDirectory2=null\n"
                                + "newOutputFile=baz");
    }
}

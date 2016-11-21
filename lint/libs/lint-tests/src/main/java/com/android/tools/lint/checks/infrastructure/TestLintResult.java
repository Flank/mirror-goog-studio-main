/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.lint.checks.infrastructure;

import static org.junit.Assert.assertEquals;

import com.android.annotations.NonNull;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * The result of running a {@link TestLintTask}.
 *
 * <p><b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
public class TestLintResult {
    private final String output;
    private final Exception exception;

    TestLintResult(@NonNull String output) {
        this.output = output;
        this.exception = null;
    }

    TestLintResult(@NonNull Exception e) {
        this.output = null;
        this.exception = e;
    }

    /**
     * Checks that the lint result had the expected report format.
     *
     * @param expectedText the text to expect
     */
    public void expect(@NonNull String expectedText) {
        if (output == null && exception != null) {
            StringWriter writer = new StringWriter();
            exception.printStackTrace(new PrintWriter(writer));
            assertEquals(expectedText, writer.toString());
        } else {
            assertEquals(expectedText, output);
        }
    }

    /**
     * Checks that there were no errors or exceptions.
     */
    public void expectClean() {
        expect("No warnings.");
    }
}

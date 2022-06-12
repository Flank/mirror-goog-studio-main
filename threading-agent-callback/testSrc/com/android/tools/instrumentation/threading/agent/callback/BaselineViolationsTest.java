/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.instrumentation.threading.agent.callback;

import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class BaselineViolationsTest {

    static final String BASELINE_TEXT =
            "#comment\n"
                    + "   \n"
                    + "\n"
                    + "com.android.ClassA#methodOne\n"
                    + "com.android.ClassA#methodTwo\n";

    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Test
    public void methodInBaseline_isIgnored() {
        InputStream stream = new ByteArrayInputStream(BASELINE_TEXT.getBytes());
        BaselineViolations baseline = BaselineViolations.fromStream(stream);

        assertThat(baseline.isIgnored("com.android.ClassA#methodOne")).isTrue();
    }

    @Test
    public void methodNotInBaseline_isNotIgnored() {
        InputStream stream = new ByteArrayInputStream(BASELINE_TEXT.getBytes());
        BaselineViolations baseline = BaselineViolations.fromStream(stream);

        assertThat(baseline.isIgnored("com.android.ClassA#notListedMethod")).isFalse();
        assertThat(baseline.isIgnored("com.android.ClassA#method")).isFalse();
        assertThat(baseline.isIgnored("com.android.ClassA#methodOnePlus")).isFalse();
        assertThat(baseline.isIgnored("methodOne")).isFalse();
    }

    @Test
    public void commentsAndEmptySpace_areNotParsedFromStream() {
        InputStream stream = new ByteArrayInputStream(BASELINE_TEXT.getBytes());
        BaselineViolations baseline = BaselineViolations.fromStream(stream);

        assertThat(baseline.isIgnored("#comment")).isFalse();
        assertThat(baseline.isIgnored("")).isFalse();
    }
}

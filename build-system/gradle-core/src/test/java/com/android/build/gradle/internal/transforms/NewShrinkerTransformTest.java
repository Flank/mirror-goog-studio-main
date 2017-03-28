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

package com.android.build.gradle.internal.transforms;

import com.android.build.gradle.shrinker.DependencyType;
import com.android.build.gradle.shrinker.tracing.RealTracer;
import com.android.build.gradle.shrinker.tracing.Trace;
import com.android.build.gradle.shrinker.tracing.Tracer;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Truth;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import org.junit.Test;

public class NewShrinkerTransformTest {

    @Test
    public void whyAreYouKeepingExplanation() throws Exception {
        Tracer<String> tracer = new RealTracer<>(Collections.emptySet());
        Trace<String> trace =
                tracer.startTrace()
                        .with("test/Main.main:()V", DependencyType.REQUIRED_KEEP_RULES)
                        .with("test/Aaa", DependencyType.REQUIRED_CODE_REFERENCE)
                        .with("test/Bbb", DependencyType.REQUIRED_CLASS_STRUCTURE);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        NewShrinkerTransform.printWhyAreYouKeepingExplanation(
                ImmutableMap.of("test/Bbb", trace), new PrintStream(byteArrayOutputStream));

        String expected =
                "test/Bbb\n"
                        + "  REQUIRED_CLASS_STRUCTURE from test/Aaa\n"
                        + "  REQUIRED_CODE_REFERENCE from test/Main.main:()V\n"
                        + "  REQUIRED_KEEP_RULES from keep rules\n";
        Truth.assertThat(byteArrayOutputStream.toString())
                .isEqualTo(expected.replace("\n", System.lineSeparator()));
    }
}

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

package com.android.build.gradle.options;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.google.common.collect.ImmutableMap;
import groovy.util.Eval;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

public class ProjectOptionsTest {

    private static boolean parseBoolean(Object input) {
        return new ProjectOptions(ImmutableMap.of("android.injected.invoked.from.ide", input))
                .get(BooleanOption.IDE_INVOKED_FROM_IDE);
    }

    private static Integer parseInteger(@NonNull Object input) {
        return new ProjectOptions(ImmutableMap.of("android.injected.build.api", input))
                .get(IntegerOption.IDE_TARGET_API_LEVEL);
    }

    private static Object asGroovyString(@NonNull Object input) {
        Object output = Eval.x(input, "\"$x\"");
        assertThat(output).isNotInstanceOf(String.class);
        return output;
    }

    @Test
    public void booleanSanity() {
        assertThat(BooleanOption.IDE_INVOKED_FROM_IDE.getDefaultValue()).isFalse();

        assertThat(new ProjectOptions(ImmutableMap.of()).get(BooleanOption.IDE_INVOKED_FROM_IDE))
                .isFalse();

        assertThat(parseBoolean("true")).isTrue();
        assertThat(parseBoolean("false")).isFalse();
        assertThat(parseBoolean(asGroovyString("true"))).isTrue();
        assertThat(parseBoolean(asGroovyString("false"))).isFalse();
        assertThat(parseBoolean(true)).isTrue();
        assertThat(parseBoolean(false)).isFalse();
        assertThat(parseBoolean(1)).isTrue();
        assertThat(parseBoolean(0)).isFalse();
        try {
            //noinspection ResultOfObjectAllocationIgnored
            new ProjectOptions(ImmutableMap.of("android.injected.invoked.from.ide", new Object()));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage()).contains("android.injected.invoked.from.ide");
        }
    }

    @Test
    public void integerSanity() {
        assertThat(IntegerOption.IDE_TARGET_API_LEVEL.getDefaultValue()).isNull();
        assertThat(new ProjectOptions(ImmutableMap.of()).get(IntegerOption.IDE_TARGET_API_LEVEL))
                .isNull();

        assertThat(parseInteger("20")).isEqualTo(20);
        assertThat(parseInteger(21)).isEqualTo(21);
        //noinspection UnnecessaryBoxing
        assertThat(parseInteger(new Long(22))).isEqualTo(22);
        assertThat(parseInteger(asGroovyString(23))).isEqualTo(23);

        try {
            //noinspection ResultOfObjectAllocationIgnored
            new ProjectOptions(ImmutableMap.of("android.injected.build.api", new Object()));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage()).contains("android.injected.build.api");
        }
    }

    @Test
    public void stringSanity() {
        assertThat(StringOption.IDE_BUILD_TARGET_ABI.getDefaultValue()).isNull();
        assertThat(new ProjectOptions(ImmutableMap.of()).get(StringOption.IDE_BUILD_TARGET_ABI))
                .isNull();

        ProjectOptions options =
                new ProjectOptions(
                        ImmutableMap.of("android.injected.build.abi", asGroovyString("x86")));
        assertThat(options.get(StringOption.IDE_BUILD_TARGET_ABI)).isEqualTo("x86");
    }

    @Test
    public void argsSanity() {
        assertThat(new ProjectOptions(ImmutableMap.of()).getExtraInstrumentationTestRunnerArgs())
                .isEmpty();
        ProjectOptions options =
                new ProjectOptions(
                        ImmutableMap.of("android.testInstrumentationRunnerArguments.a", "b"));
        assertThat(options.getExtraInstrumentationTestRunnerArgs()).containsExactly("a", "b");
    }

    @Test
    public void ensureUniqueness() {
        List<String> optionsNames =
                Stream.of(
                                BooleanOption.values(),
                                OptionalBooleanOption.values(),
                                IntegerOption.values(),
                                StringOption.values())
                        .flatMap(Arrays::stream)
                        .map(option -> option.getPropertyName())
                        .collect(Collectors.toList());

        assertThat(optionsNames).containsNoDuplicates();
    }
}

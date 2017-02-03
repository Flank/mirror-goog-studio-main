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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.OptionalCompilationStep;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import org.gradle.api.Project;

/** Determines if various options, triggered from the command line or environment, are set. */
public final class ProjectOptions {

    public static final String PROPERTY_TEST_RUNNER_ARGS =
            "android.testInstrumentationRunnerArguments.";

    private final ImmutableMap<BooleanOption, Boolean> booleanOptions;
    private final ImmutableMap<OptionalBooleanOption, Boolean> optionalBooleanOptions;
    private final ImmutableMap<IntegerOption, Integer> integerOptions;
    private final ImmutableMap<StringOption, String> stringOptions;
    private final ImmutableMap<String, String> testRunnerArgs;

    public ProjectOptions(@NonNull ImmutableMap<String, Object> properties) {
        booleanOptions = readOptions(BooleanOption.values(), properties);
        optionalBooleanOptions = readOptions(OptionalBooleanOption.values(), properties);
        integerOptions = readOptions(IntegerOption.values(), properties);
        stringOptions = readOptions(StringOption.values(), properties);
        testRunnerArgs = readTestRunnerArgs(properties);
    }

    public ProjectOptions(@NonNull Project project) {
        this(copyProperties(project));
    }

    @NonNull
    private static ImmutableMap<String, Object> copyProperties(@NonNull Project project) {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        for (Map.Entry<String, ?> entry : project.getProperties().entrySet()) {
            Object value = entry.getValue();
            if (value != null) {
                builder.put(entry.getKey(), value);
            }
        }
        return builder.build();
    }

    @NonNull
    private static <OptionT extends Option<ValueT>, ValueT>
            ImmutableMap<OptionT, ValueT> readOptions(
                    @NonNull OptionT[] values, @NonNull Map<String, ?> properties) {
        Map<String, OptionT> optionLookup =
                Arrays.stream(values).collect(Collectors.toMap(Option::getPropertyName, v -> v));
        ImmutableMap.Builder<OptionT, ValueT> valuesBuilder = ImmutableMap.builder();
        for (Map.Entry<String, ?> property : properties.entrySet()) {
            OptionT option = optionLookup.get(property.getKey());
            if (option != null) {
                valuesBuilder.put(option, option.parse(property.getValue()));
            }
        }
        return valuesBuilder.build();
    }

    @NonNull
    private static ImmutableMap<String, String> readTestRunnerArgs(
            @NonNull Map<String, ?> properties) {
        ImmutableMap.Builder<String, String> testRunnerArgsBuilder = ImmutableMap.builder();
        for (Map.Entry<String, ?> entry : properties.entrySet()) {
            String name = entry.getKey();
            if (name.startsWith(PROPERTY_TEST_RUNNER_ARGS)) {
                String argName = name.substring(PROPERTY_TEST_RUNNER_ARGS.length());
                String argValue = entry.getValue().toString();
                testRunnerArgsBuilder.put(argName, argValue);
            }
        }
        return testRunnerArgsBuilder.build();
    }

    public boolean get(BooleanOption option) {
        return booleanOptions.getOrDefault(option, option.getDefaultValue());
    }

    @Nullable
    public Boolean get(OptionalBooleanOption option) {
        return optionalBooleanOptions.get(option);
    }

    @Nullable
    public Integer get(IntegerOption option) {
        return integerOptions.getOrDefault(option, option.getDefaultValue());
    }

    @Nullable
    public String get(StringOption option) {
        return stringOptions.getOrDefault(option, option.getDefaultValue());
    }

    @NonNull
    public Map<String, String> getExtraInstrumentationTestRunnerArgs() {
        return testRunnerArgs;
    }

    @NonNull
    public Set<OptionalCompilationStep> getOptionalCompilationSteps() {
        String values = get(StringOption.IDE_OPTIONAL_COMPILATION_STEPS);
        if (values != null) {
            List<OptionalCompilationStep> optionalCompilationSteps = new ArrayList<>();
            StringTokenizer st = new StringTokenizer(values, ",");
            while (st.hasMoreElements()) {
                optionalCompilationSteps.add(OptionalCompilationStep.valueOf(st.nextToken()));
            }
            return EnumSet.copyOf(optionalCompilationSteps);
        }
        return EnumSet.noneOf(OptionalCompilationStep.class);
    }
}

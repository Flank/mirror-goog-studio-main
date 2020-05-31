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
import com.android.annotations.concurrency.Immutable;
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
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

/** Determines if various options, triggered from the command line or environment, are set. */
@Immutable
public final class ProjectOptions {

    public static final String PROPERTY_TEST_RUNNER_ARGS =
            "android.testInstrumentationRunnerArguments.";

    private final ImmutableMap<ReplacedOption, String> replacedOptions;
    private final ImmutableMap<StringOption, String> stringOptions;
    private final ImmutableMap<String, String> testRunnerArgs;

    private final ProviderFactory providerFactory;
    private final ImmutableMap<BooleanOption, OptionValue<BooleanOption, Boolean>>
            booleanOptionValues;
    private final ImmutableMap<OptionalBooleanOption, OptionValue<OptionalBooleanOption, Boolean>>
            optionalBooleanOptionValues;
    private final ImmutableMap<IntegerOption, OptionValue<IntegerOption, Integer>>
            integerOptionValues;

    public ProjectOptions(
            @NonNull ImmutableMap<String, Object> properties,
            @NonNull ProviderFactory providerFactory) {
        replacedOptions = readOptions(ReplacedOption.values(), properties);
        stringOptions = readOptions(StringOption.values(), properties);
        testRunnerArgs = readTestRunnerArgs(properties);
        this.providerFactory = providerFactory;
        booleanOptionValues = createOptionValues(BooleanOption.values());
        optionalBooleanOptionValues = createOptionValues(OptionalBooleanOption.values());
        integerOptionValues = createOptionValues(IntegerOption.values());
    }

    /**
     * Constructor used to obtain Project Options from the project's properties.
     *
     * @param project the project containing the properties
     */
    public ProjectOptions(@NonNull Project project) {
        this(copyProperties(project), project.getProviders());
    }

    /**
     * Constructor used to obtain Project Options from the project's properties and modify them by
     * applying all the flags from the given map.
     *
     * @param project the project containing the properties
     * @param overwrites a map of flags overwriting project properties' values
     */
    public ProjectOptions(
            @NonNull Project project, @NonNull ImmutableMap<String, Object> overwrites) {
        this(copyAndModifyProperties(project, overwrites), project.getProviders());
    }

    @NonNull
    private static ImmutableMap<String, Object> copyProperties(@NonNull Project project) {
        return copyAndModifyProperties(project, ImmutableMap.of());
    }

    @NonNull
    private static ImmutableMap<String, Object> copyAndModifyProperties(
            @NonNull Project project, @NonNull ImmutableMap<String, Object> overwrites) {
        ImmutableMap.Builder<String, Object> optionsBuilder = ImmutableMap.builder();
        for (Map.Entry<String, ?> entry :
                project.getExtensions().getExtraProperties().getProperties().entrySet()) {
            Object value = entry.getValue();
            if (value != null && !overwrites.containsKey(entry.getKey())) {
                optionsBuilder.put(entry.getKey(), value);
            }
        }
        for (Map.Entry<String, ?> overwrite : overwrites.entrySet()) {
            optionsBuilder.put(overwrite.getKey(), overwrite.getValue());
        }
        return optionsBuilder.build();
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
                ValueT value = option.parse(property.getValue());
                valuesBuilder.put(option, value);
            }
        }
        return valuesBuilder.build();
    }

    @NonNull
    private <OptionT extends Option<ValueT>, ValueT>
            ImmutableMap<OptionT, OptionValue<OptionT, ValueT>> createOptionValues(
                    @NonNull OptionT[] options) {
        ImmutableMap.Builder<OptionT, OptionValue<OptionT, ValueT>> map = ImmutableMap.builder();
        for (OptionT option : options) {
            map.put(option, new OptionValue<>(option));
        }
        return map.build();
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
        return getValue(option);
    }

    /** Obtain the gradle property value immediately at configuration time. */
    public boolean getValue(@NonNull BooleanOption option) {
        Boolean value = booleanOptionValues.get(option).getValueForUseAtConfiguration();
        if (value != null) {
            return value;
        } else {
            return option.getDefaultValue();
        }
    }

    /** Returns a provider which has the gradle property value to be obtained at execution time. */
    @NonNull
    public Provider<Boolean> getValueProvider(@NonNull BooleanOption option) {
        return providerFactory.provider(
                () ->
                        booleanOptionValues
                                .get(option)
                                .getValueForUseAtExecution()
                                .getOrElse(option.getDefaultValue()));
    }

    /** Obtain the gradle property value immediately at configuration time. */
    @Nullable
    public Boolean getValue(@NonNull OptionalBooleanOption option) {
        return optionalBooleanOptionValues.get(option).getValueForUseAtConfiguration();
    }

    /** Returns a provider which has the gradle property value to be obtained at execution time. */
    @NonNull
    public Provider<Boolean> getValueProvider(@NonNull OptionalBooleanOption option) {
        return optionalBooleanOptionValues.get(option).getValueForUseAtExecution();
    }

    /** Obtain the gradle property value immediately at configuration time. */
    @Nullable
    public Integer getValue(@NonNull IntegerOption option) {
        Integer value = integerOptionValues.get(option).getValueForUseAtConfiguration();
        if (value != null) {
            return value;
        } else {
            return option.getDefaultValue();
        }
    }

    /** Returns a provider which has the gradle property value to be obtained at execution time. */
    @NonNull
    public Provider<Integer> getValueProvider(@NonNull IntegerOption option) {
        return providerFactory.provider(
                () -> {
                    Integer value =
                            integerOptionValues.get(option).getValueForUseAtExecution().getOrNull();
                    if (value != null) {
                        return value;
                    } else {
                        return option.getDefaultValue();
                    }
                });
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

    public <OptionT extends Option<ValueT>, ValueT>
            ImmutableMap<OptionT, ValueT> getExplicitlySetOptions(
                    ImmutableMap<OptionT, OptionValue<OptionT, ValueT>> optionValues) {
        ImmutableMap.Builder<OptionT, ValueT> mapBuilder = ImmutableMap.builder();
        for (Map.Entry<OptionT, OptionValue<OptionT, ValueT>> entry : optionValues.entrySet()) {
            ValueT value = entry.getValue().getValueForUseAtConfiguration();
            if (value != null) {
                mapBuilder.put(entry.getKey(), value);
            }
        }
        return mapBuilder.build();
    }

    public ImmutableMap<BooleanOption, Boolean> getExplicitlySetBooleanOptions() {
        return getExplicitlySetOptions(booleanOptionValues);
    }

    public ImmutableMap<OptionalBooleanOption, Boolean> getExplicitlySetOptionalBooleanOptions() {
        return getExplicitlySetOptions(optionalBooleanOptionValues);
    }

    public ImmutableMap<IntegerOption, Integer> getExplicitlySetIntegerOptions() {
        return getExplicitlySetOptions(integerOptionValues);
    }

    public ImmutableMap<StringOption, String> getExplicitlySetStringOptions() {
        return stringOptions;
    }

    public ImmutableMap<Option<?>, Object> getAllOptions() {
        return new ImmutableMap.Builder()
                .putAll(replacedOptions)
                .putAll(getExplicitlySetBooleanOptions())
                .putAll(getExplicitlySetOptionalBooleanOptions())
                .putAll(getExplicitlySetIntegerOptions())
                .putAll(stringOptions)
                .build();
    }

    private class OptionValue<OptionT extends Option<ValueT>, ValueT> {
        @Nullable private Provider<ValueT> valueForUseAtConfiguration;
        @Nullable private Provider<ValueT> valueForUseAtExecution;
        @NonNull private OptionT option;

        OptionValue(@NonNull OptionT option) {
            this.option = option;
        }

        @Nullable
        private ValueT getValueForUseAtConfiguration() {
            if (valueForUseAtConfiguration == null) {
                valueForUseAtConfiguration = setValueForUseAtConfiguration();
            }
            return valueForUseAtConfiguration.getOrNull();
        }

        @NonNull
        private Provider<ValueT> getValueForUseAtExecution() {
            if (valueForUseAtExecution == null) {
                valueForUseAtExecution = setValueForUseAtExecution();
            }
            return valueForUseAtExecution;
        }

        @NonNull
        private Provider<ValueT> setValueForUseAtConfiguration() {
            Provider<String> rawValue = providerFactory.gradleProperty(option.getPropertyName());
            return providerFactory.provider(
                    () -> {
                        String str = rawValue.forUseAtConfigurationTime().getOrNull();
                        if (str == null) {
                            return null;
                        }
                        return option.parse(str);
                    });
        }

        @NonNull
        private Provider<ValueT> setValueForUseAtExecution() {
            Provider<String> rawValue = providerFactory.gradleProperty(option.getPropertyName());
            return providerFactory.provider(
                    () -> {
                        String str = rawValue.getOrNull();
                        if (str == null) {
                            return null;
                        }
                        return option.parse(str);
                    });
        }
    }
}

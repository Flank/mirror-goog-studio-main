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

package com.android.flags;

import com.android.annotations.NonNull;

/**
 * A flag is a setting with an unique ID and some value. Flags are often used to gate features (e.g.
 * start with the feature disabled or enabled) or initialize a feature with some default value (e.g.
 * how much memory to initialize a system with, what mode a system should use by default).
 */
public final class Flag<T> {
    private static final ValueConverter<Boolean> BOOL_CONVERTER =
            new ValueConverter<Boolean>() {
                @NonNull
                @Override
                public String serialize(@NonNull Boolean value) {
                    return value.toString();
                }

                @NonNull
                @Override
                public Boolean deserialize(@NonNull String strValue) {
                    return Boolean.parseBoolean(strValue);
                }
            };
    private static final ValueConverter<Integer> INT_CONVERTER =
            new ValueConverter<Integer>() {
                @NonNull
                @Override
                public String serialize(@NonNull Integer value) {
                    return Integer.toString(value);
                }

                @NonNull
                @Override
                public Integer deserialize(@NonNull String strValue) {
                    return Integer.valueOf(strValue);
                }
            };
    private static final ValueConverter<String> PASSTHRU_CONVERTER =
            new ValueConverter<String>() {
                @NonNull
                @Override
                public String serialize(@NonNull String value) {
                    return value;
                }

                @NonNull
                @Override
                public String deserialize(@NonNull String strValue) {
                    return strValue;
                }
            };
    private final FlagGroup group;
    private final String name;
    private final String displayName;
    private final String description;
    private final ValueConverter<T> valueConverter;

    @NonNull private final String defaultValue;

    /** Use one of the {@code Flag#create} convenience methods to construct this class. */
    protected Flag(
            @NonNull FlagGroup group,
            @NonNull String name,
            @NonNull String displayName,
            @NonNull String description,
            @NonNull T defaultValue,
            @NonNull ValueConverter<T> valueConverter) {
        this.group = group;
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.valueConverter = valueConverter;
        this.defaultValue = valueConverter.serialize(defaultValue);

        Flag.verifyFlagIdFormat(getId());
        Flag.verifyDispayTextFormat(displayName);
        Flag.verifyDispayTextFormat(description);
        group.getFlags().verifyUniqueId(this);
    }

    /**
     * Verify that a flag's ID is correctly formatted, i.e. consisting of only lower-case letters,
     * numbers, and periods. Furthermore, the first character of an ID must be a letter and cannot
     * end with one.
     */
    public static void verifyFlagIdFormat(@NonNull String id) {
        if (!id.matches("[a-z][a-z0-9]*(\\.[a-z0-9]+)*")) {
            throw new IllegalArgumentException("Invalid id: " + id);
        }
    }

    /** Verify that display text is correctly formatted. */
    public static void verifyDispayTextFormat(@NonNull String name) {
        if (name.isEmpty() || name.charAt(0) == ' ' || name.charAt(name.length() - 1) == ' ') {
            throw new IllegalArgumentException("Invalid name: " + name);
        }
    }

    /**
     * Returns a unique ID for this flag. It will be composed of the group's name prefixed to this
     * flag's name.
     */
    @NonNull
    public String getId() {
        return group.getName() + "." + name;
    }

    @NonNull
    public static Flag<Boolean> create(
            @NonNull FlagGroup group,
            @NonNull String name,
            @NonNull String displayName,
            @NonNull String description,
            boolean defaultValue) {
        return new Flag<>(group, name, displayName, description, defaultValue, BOOL_CONVERTER);
    }

    @NonNull
    public static Flag<Integer> create(
            @NonNull FlagGroup group,
            @NonNull String name,
            @NonNull String displayName,
            @NonNull String description,
            int defaultValue) {
        return new Flag<>(group, name, displayName, description, defaultValue, INT_CONVERTER);
    }

    @NonNull
    public static Flag<String> create(
            @NonNull FlagGroup group,
            @NonNull String name,
            @NonNull String displayName,
            @NonNull String description,
            String defaultValue) {
        return new Flag<>(group, name, displayName, description, defaultValue, PASSTHRU_CONVERTER);
    }

    /** Returns the {@link FlagGroup} that this flag is part of. */
    public FlagGroup getGroup() {
        return group;
    }

    /** Returns a user-friendly display name for this flag. */
    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    /** Returns a user-friendly description for what feature this flag gates. */
    @NonNull
    public String getDescription() {
        return description;
    }

    /** Returns the value of this flag. */
    @NonNull
    public T get() {
        Flags flags = getGroup().getFlags();
        String strValue = flags.getOverriddenValue(this);
        if (strValue == null) {
            strValue = defaultValue;
        }

        return valueConverter.deserialize(strValue);
    }

    /**
     * Override the value of this flag at runtime.
     *
     * <p>This method does not modify this flag definition directly, but instead adds an entry into
     * its parent {@link Flags#getOverrides()} collection.
     */
    public void override(@NonNull T overrideValue) {
        getGroup().getFlags().getOverrides().put(this, valueConverter.serialize(overrideValue));
    }

    /** Clear any override previously set by {@link #override(Object)}. */
    public void clearOverride() {
        getGroup().getFlags().getOverrides().remove(this);
    }

    public boolean isOverridden() {
        return getGroup().getFlags().getOverrides().get(this) != null;
    }

    /**
     * Simple interface for converting a value to and from a String. This is useful as all flags are
     * really strings underneath, although it's convenient to expose, say, boolean flags to users
     * instead.
     */
    private interface ValueConverter<T> {
        @NonNull
        String serialize(@NonNull T value);

        @NonNull
        T deserialize(@NonNull String strValue);
    }
}

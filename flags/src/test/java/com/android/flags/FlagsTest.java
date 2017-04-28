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

import static com.google.common.truth.Truth.assertThat;

import com.android.flags.overrides.DefaultFlagOverrides;
import com.android.flags.overrides.PropertyOverrides;
import java.util.Properties;
import org.junit.Assert;
import org.junit.Test;

public class FlagsTest {
    @Test
    public void propertiesCanOverrideFlagValues() throws Exception {
        Properties properties = new Properties();
        properties.put("test.int", "123");
        properties.put("test.bool", "true");
        properties.put("test.str", "Property override");

        PropertyOverrides propertyOverrides = new PropertyOverrides(properties);
        Flags flags = new Flags(propertyOverrides);
        FlagGroup group = new FlagGroup(flags, "test", "Test Group");

        Flag<Integer> flagInt = Flag.create(group, "int", "Unused", "Unused", 10);
        Flag<Boolean> flagBool = Flag.create(group, "bool", "Unused", "Unused", false);
        Flag<String> flagStr = Flag.create(group, "str", "Unused", "Unused", "Default value");

        assertThat(flagInt.get()).isEqualTo(123);
        assertThat(flagBool.get()).isEqualTo(true);
        assertThat(flagStr.get()).isEqualTo("Property override");
    }

    @Test
    public void overrideFlagAndClearOverrideWorks() throws Exception {
        Flags flags = new Flags();

        FlagGroup group = new FlagGroup(flags, "test", "Test Group");

        Flag<Integer> flagInt = Flag.create(group, "int", "Unused", "Unused", 10);
        Flag<Boolean> flagBool = Flag.create(group, "bool", "Unused", "Unused", false);
        Flag<String> flagStr = Flag.create(group, "str", "Unused", "Unused", "Default value");

        flags.getOverrides().put(flagInt, "456");
        flags.getOverrides().put(flagBool, "true");
        flags.getOverrides().put(flagStr, "Manual override");

        assertThat(flagInt.get()).isEqualTo(456);
        assertThat(flagBool.get()).isEqualTo(true);
        assertThat(flagStr.get()).isEqualTo("Manual override");

        flags.getOverrides().remove(flagInt);
        flags.getOverrides().remove(flagBool);
        flags.getOverrides().remove(flagStr);

        assertThat(flagInt.get()).isEqualTo(10);
        assertThat(flagBool.get()).isEqualTo(false);
        assertThat(flagStr.get()).isEqualTo("Default value");
    }

    @Test
    public void mutableOverridesTakePrecedenceOverImmutableOverrides() throws Exception {
        Properties properties = new Properties();
        properties.put("test.str", "Property override");

        PropertyOverrides propertyOverrides = new PropertyOverrides(properties);
        Flags flags = new Flags(propertyOverrides);

        FlagGroup group = new FlagGroup(flags, "test", "Test Group");
        Flag<String> flagStr = Flag.create(group, "str", "Unused", "Unused", "Default value");

        flags.getOverrides().put(flagStr, "Manual override");
        assertThat(flagStr.get()).isEqualTo("Manual override");

        flags.getOverrides().remove(flagStr);
        assertThat(flagStr.get()).isEqualTo("Property override");
    }

    @Test
    public void canSpecifyCustomUserOveriddes() throws Exception {
        DefaultFlagOverrides customMutableOverrides = new DefaultFlagOverrides();
        Flags flags = new Flags(customMutableOverrides);
        FlagGroup group = new FlagGroup(flags, "test", "Test Group");
        Flag<String> flagStr = Flag.create(group, "str", "Unused", "Unused", "Default value");

        customMutableOverrides.put(flagStr, "Overridden value");

        assertThat(flagStr.get()).isEqualTo("Overridden value");

        customMutableOverrides.clear();

        assertThat(flagStr.get()).isEqualTo("Default value");
    }

    @Test
    public void flagsThrowsExceptionIfFlagsWithDuplicateIdsAreRegisetered() throws Exception {
        Flags flags = new Flags();
        FlagGroup group = new FlagGroup(flags, "test", "Test Group");
        Flag<String> flag1 = Flag.create(group, "str1", "Unused", "Unused", "Str 1");
        Flag<String> flag2 = Flag.create(group, "str2", "Unused", "Unused", "Str 2");

        try {
            // Oops. Copy/paste error...
            Flag<String> flag3 = Flag.create(group, "str2", "Unused", "Unused", "Str 3");
            Assert.fail();
        } catch (IllegalArgumentException ignored) {
        }
    }
}

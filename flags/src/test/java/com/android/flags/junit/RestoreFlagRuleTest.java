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
package com.android.flags.junit;

import static com.google.common.truth.Truth.assertThat;

import com.android.flags.Flag;
import com.android.flags.FlagGroup;
import com.android.flags.Flags;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

public class RestoreFlagRuleTest {

    private static final Flags myFlags = new Flags();
    private static final FlagGroup myFlagGroup = new FlagGroup(myFlags, "group", "dummy");
    private static final Flag<Boolean> myBoolFlag =
            Flag.create(myFlagGroup, "flag.bool", "dummy", "dummy", true);
    private static final Flag<Integer> myIntFlag =
            Flag.create(myFlagGroup, "flag.int", "dummy", "dummy", 42);
    private static final Flag<String> myStrFlag =
            Flag.create(myFlagGroup, "flag.str", "dummy", "dummy", "Hello");

    @Rule public RestoreFlagRule<Boolean> myRestoreBoolFlagRule = new RestoreFlagRule<>(myBoolFlag);
    @Rule public RestoreFlagRule<Integer> myRestoreIntFlagRule = new RestoreFlagRule<>(myIntFlag);
    @Rule public RestoreFlagRule<String> myRestoreStringFlagRule = new RestoreFlagRule<>(myStrFlag);

    @BeforeClass
    public static void setUpClass() {
        assertExpectedDefaults();
    }

    @AfterClass
    public static void tearDownClass() {
        assertExpectedDefaults();
    }

    private static void assertExpectedDefaults() {
        assertThat(myBoolFlag.get()).isTrue();
        assertThat(myIntFlag.get()).isEqualTo(42);
        assertThat(myStrFlag.get()).isEqualTo("Hello");

        assertThat(myBoolFlag.isOverridden()).isFalse();
        assertThat(myIntFlag.isOverridden()).isFalse();
        assertThat(myStrFlag.isOverridden()).isFalse();
    }

    @Before
    public void setUp() throws Exception {
        assertExpectedDefaults();
    }

    @Test
    public void overrideBoolFlag() {
        myBoolFlag.override(false);
    }

    @Test
    public void overrideIntFlag() {
        myIntFlag.override(9001);
    }

    @Test
    public void overrideStringFlag() {
        myStrFlag.override("Goodbye");
    }
}

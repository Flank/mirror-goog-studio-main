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

package com.android.build.gradle.integration.common.utils;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.ModelHelper.searchForSingleItemInList;
import static com.google.common.collect.ImmutableList.of;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ModelHelperTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void searchForSingleItemWithTriplets() throws Exception {
        assertThat(searchForSingleItemInList(of("a", "b", "c"), "a", i -> i)).hasValueEqualTo("a");
    }

    @Test
    public void searchForSingleItemWithSame3() throws Exception {
        thrown.expect(IllegalArgumentException.class);
        searchForSingleItemInList(of("a", "a", "a"), "a", i -> i);
    }

    @Test
    public void searchForSingleItemWithSame3outOf4() throws Exception {
        thrown.expect(IllegalArgumentException.class);
        searchForSingleItemInList(of("a", "a", "a", "b"), "a", i -> i);
    }

    @Test
    public void searchForSingleItemWithSame2() throws Exception {
        thrown.expect(IllegalArgumentException.class);
        searchForSingleItemInList(of("a", "a"), "a", i -> i);
    }

    @Test
    public void searchForSingleItemWithMissingValue() throws Exception {
        assertThat(searchForSingleItemInList(of("a", "b", "c"), "d", i -> i)).isAbsent();
    }
}
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

package com.android.tools.lint.detector.api;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.lint.detector.api.LintFix.ReplaceString;
import com.android.tools.lint.detector.api.LintFix.SetAttribute;
import com.google.common.collect.Maps;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import junit.framework.TestCase;

public class LintFixTest extends TestCase {
    public void test() {
        LintFix.FixMapBuilder builder = LintFix.create().map("foo", 3, new BigDecimal(50));
        builder.put("name1", "Name1");
        builder.put("name2", "Name2");
        builder.put(Maps.newHashMap());
        builder.put(new ArrayList<>());
        builder.put(null); // no-op
        LintFix.DataMap quickfixData = (LintFix.DataMap) builder.build();
        assertThat(quickfixData.get(Float.class)).isNull();
        assertThat(quickfixData.get(String.class)).isEqualTo("foo");
        assertThat(quickfixData.get(Integer.class)).isEqualTo(3);
        assertThat(quickfixData.get(BigDecimal.class)).isEqualTo(new BigDecimal(50));
        assertThat(quickfixData.get("name1")).isEqualTo("Name1");
        assertThat(quickfixData.get("name2")).isEqualTo("Name2");

        boolean foundString = false;
        boolean foundInteger = false;
        boolean foundBigDecimal = false;
        for (Object data : quickfixData) { // no order guarantee
            if (data.equals("foo")) {
                foundString = true;
            } else if (data.equals(3)) {
                foundInteger = true;
            } else if (data instanceof BigDecimal) {
                foundBigDecimal = true;
            }
        }
        assertThat(foundString).isTrue();
        assertThat(foundInteger).isTrue();
        assertThat(foundBigDecimal).isTrue();

        assertNotNull(LintFix.getData(quickfixData, BigDecimal.class));

        // Check key conversion to general interface
        assertNull(LintFix.getData(quickfixData, ArrayList.class));
        assertNotNull(LintFix.getData(quickfixData, List.class));
        assertNull(LintFix.getData(quickfixData, HashMap.class));
        assertNotNull(LintFix.getData(quickfixData, Map.class));
    }

    public void testClassInheritance() {
        //noinspection UnnecessaryBoxing
        LintFix.FixMapBuilder builder = LintFix.create().map(Integer.valueOf(5));
        LintFix.DataMap quickfixData = (LintFix.DataMap) builder.build();
        assertThat(quickfixData.get(Integer.class)).isEqualTo(5);
        // Looking up with a more general class:
        assertThat(quickfixData.get(Number.class)).isEqualTo(5);
    }

    public void testSetAttribute() {
        SetAttribute fixData = (SetAttribute) LintFix.create().set().namespace("namespace")
                .attribute("attribute").value("value").build();
        assertThat(fixData.namespace).isEqualTo("namespace");
        assertThat(fixData.attribute).isEqualTo("attribute");
        assertThat(fixData.value).isEqualTo("value");
    }

    public void testReplaceString() {
        ReplaceString fixData = (ReplaceString) LintFix.create().replace().text("old")
                .with("new").build();
        assertThat(fixData.oldString).isEqualTo("old");
        assertThat(fixData.replacement).isEqualTo("new");
        assertThat(fixData.oldPattern).isNull();

        fixData = (ReplaceString) LintFix.create().replace().pattern("(oldPattern)")
                .with("new").build();
        assertThat(fixData.oldPattern).isEqualTo("(oldPattern)");
        assertThat(fixData.replacement).isEqualTo("new");
        assertThat(fixData.oldString).isNull();

        fixData = (ReplaceString) LintFix.create().replace().pattern("oldPattern").with("new")
                .build();
        assertThat(fixData.oldPattern).isEqualTo("(oldPattern)");
        assertThat(fixData.replacement).isEqualTo("new");
        assertThat(fixData.oldString).isNull();
    }
}
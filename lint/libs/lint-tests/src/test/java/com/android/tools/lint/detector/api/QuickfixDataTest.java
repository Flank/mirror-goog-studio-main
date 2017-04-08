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

import com.android.tools.lint.detector.api.QuickfixData.ReplaceString;
import com.android.tools.lint.detector.api.QuickfixData.SetAttribute;
import java.math.BigDecimal;
import junit.framework.TestCase;

public class QuickfixDataTest extends TestCase {
    public void test() {
        QuickfixData quickfixData = QuickfixData.create("foo", 3, new BigDecimal(50));
        quickfixData.put("name1", "Name1");
        quickfixData.put("name2", "Name2");
        quickfixData.put(null); // no-op
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
    }

    public void testSetAttribute() {
        SetAttribute fixData = new SetAttribute("namespace",
                "attribute", "value");
        assertThat(fixData.namespace).isEqualTo("namespace");
        assertThat(fixData.attribute).isEqualTo("attribute");
        assertThat(fixData.value).isEqualTo("value");
    }

    public void testReplaceString() {
        ReplaceString fixData = new ReplaceString("old", null, "new");
        assertThat(fixData.oldString).isEqualTo("old");
        assertThat(fixData.replacement).isEqualTo("new");
        assertThat(fixData.oldPattern).isNull();

        fixData = new ReplaceString(null, "oldPattern", "new");
        assertThat(fixData.oldPattern).isEqualTo("oldPattern");
        assertThat(fixData.replacement).isEqualTo("new");
        assertThat(fixData.oldString).isNull();
    }
}
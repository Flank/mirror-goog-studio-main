/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.agent.layoutinspector.property;

import static com.google.common.truth.Truth.assertThat;

import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.tools.agent.layoutinspector.testing.CompanionSupplierRule;
import com.android.tools.agent.layoutinspector.testing.ViewBuilder;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

public class LayoutParamsTypeTreeTest {

    @Rule public CompanionSupplierRule supplier = new CompanionSupplierRule();

    @Test
    public void testLinearLayout() {
        TextView textView =
                ViewBuilder.create(TextView.class, LinearLayout.LayoutParams.class).build();
        LayoutParamsTypeTree tree = new LayoutParamsTypeTree();
        LayoutParamsType type = tree.typeOf(textView.getLayoutParams());
        List<String> propertyNames =
                type.getProperties()
                        .stream()
                        .map(PropertyType::getName)
                        .collect(Collectors.toList());
        assertThat(propertyNames)
                .containsExactly(
                        // From ViewGroup
                        "layout_width", "layout_height",
                        // From LinearLayout
                        "layout_gravity", "layout_marginBottom");
    }

    @Test
    public void testFrameLayout() {
        TextView textView =
                ViewBuilder.create(TextView.class, FrameLayout.LayoutParams.class).build();
        LayoutParamsTypeTree tree = new LayoutParamsTypeTree();
        LayoutParamsType type = tree.typeOf(textView.getLayoutParams());
        List<String> propertyNames =
                type.getProperties()
                        .stream()
                        .map(PropertyType::getName)
                        .collect(Collectors.toList());
        assertThat(propertyNames)
                .containsExactly(
                        // From ViewGroup
                        "layout_width", "layout_height");
    }
}

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

import android.widget.ImageView;
import android.widget.TextView;
import com.android.tools.agent.layoutinspector.testing.CompanionSupplierRule;
import com.android.tools.agent.layoutinspector.testing.ViewBuilder;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

public class ViewTypeTreeTest {

    @Rule public CompanionSupplierRule supplier = new CompanionSupplierRule();

    @Test
    public void testTextView() {
        TextView textView = ViewBuilder.create(TextView.class).build();
        ViewTypeTree tree = new ViewTypeTree();
        ViewType<TextView> type = tree.typeOf(textView);
        List<String> propertyNames =
                type.getProperties()
                        .stream()
                        .map(PropertyType::getName)
                        .collect(Collectors.toList());
        assertThat(propertyNames)
                .containsAtLeast(
                        // From View
                        "focused",
                        "scaleX",
                        "scrollX",
                        "transitionName",
                        "background",
                        "backgroundTint",
                        // From TextView
                        "text");
    }

    @Test
    public void testImageView() {
        ImageView imageView = ViewBuilder.create(ImageView.class).build();
        ViewTypeTree tree = new ViewTypeTree();
        ViewType<ImageView> type = tree.typeOf(imageView);
        List<String> propertyNames =
                type.getProperties()
                        .stream()
                        .map(PropertyType::getName)
                        .collect(Collectors.toList());
        assertThat(propertyNames)
                .containsAtLeast(
                        // From View
                        "focused",
                        "scaleX",
                        "scrollX",
                        "transitionName",
                        "background",
                        "backgroundTint");
        assertThat(propertyNames)
                .containsNoneOf(
                        // From TextView
                        "text", "textColor");
    }
}

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

import android.graphics.Color;
import android.widget.TextView;
import com.android.tools.agent.layoutinspector.testing.CompanionSupplierRule;
import com.android.tools.agent.layoutinspector.testing.StandardView;
import com.google.common.collect.ImmutableSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

public class ViewNodeTest {

    @Rule public CompanionSupplierRule supplier = new CompanionSupplierRule();

    @Test
    public void testValueMapping() {
        TextView textView = StandardView.createTextView();
        ViewTypeTree viewTree = new ViewTypeTree();
        LayoutParamsTypeTree layoutTree = new LayoutParamsTypeTree();
        ViewNode<TextView> node =
                new ViewNode<>(
                        viewTree.typeOf(textView), layoutTree.typeOf(textView.getLayoutParams()));
        node.readProperties(textView);

        Map<String, Property> viewProperties = asMap(node.getViewProperties());
        assertThat(viewProperties.get("focused").getValue()).isEqualTo(1);
        assertThat(viewProperties.get("byte").getValue()).isEqualTo((byte) 7);
        assertThat(viewProperties.get("char").getValue()).isEqualTo('g');
        assertThat(viewProperties.get("double").getValue()).isEqualTo(3.75);
        assertThat(viewProperties.get("scaleX").getValue()).isEqualTo(1.75f);
        assertThat(viewProperties.get("scrollX").getValue()).isEqualTo(10);
        assertThat(viewProperties.get("long").getValue()).isEqualTo(7000L);
        assertThat(viewProperties.get("short").getValue()).isEqualTo((short) 70);
        assertThat(viewProperties.get("transitionName").getValue()).isEqualTo("MyTransitionName");
        assertThat(viewProperties.get("backgroundTint").getValue()).isEqualTo(Color.BLUE);
        assertThat(viewProperties.get("background").getValue()).isEqualTo(Color.YELLOW);
        assertThat(viewProperties.get("outlineSpotShadowColor").getValue()).isEqualTo(Color.RED);
        assertThat(viewProperties.get("foregroundGravity").getValue())
                .isEqualTo(ImmutableSet.of("top", "fill_horizontal"));
        assertThat(viewProperties.get("visibility").getValue()).isEqualTo("invisible");
        assertThat(String.valueOf(viewProperties.get("labelFor").getValue()))
                .isEqualTo("resource:(@pck:id/other)");
        assertThat(viewProperties.get("scrollIndicators").getValue())
                .isEqualTo(ImmutableSet.of("left", "bottom"));
        // From TextView:
        assertThat(viewProperties.get("text").getValue()).isEqualTo("Hello World!");

        Map<String, Property> layoutProperties = asMap(node.getLayoutProperties());
        assertThat(layoutProperties.get("layout_width").getValue()).isEqualTo("match_parent");
        assertThat(layoutProperties.get("layout_height").getValue()).isEqualTo(400);
        // From LinearLayout:
        assertThat(layoutProperties.get("layout_marginBottom").getValue()).isEqualTo(10);
    }

    private static Map<String, Property> asMap(List<Property> properties) {
        Map<String, Property> map = new HashMap<>();
        properties.forEach(property -> map.put(property.getPropertyType().getName(), property));
        return map;
    }
}

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.tools.agent.layoutinspector.testing.CompanionSupplierRule;
import com.android.tools.agent.layoutinspector.testing.ResourcesBuilder;
import com.android.tools.agent.layoutinspector.testing.ViewBuilder;
import com.google.common.collect.ImmutableSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

public class ViewNodeTest {
    private static final int LAYOUT_ID = 1;
    private static final int STYLE_ID = 2;
    private static final int OTHER_ID = 3;

    @Rule public CompanionSupplierRule supplier = new CompanionSupplierRule();

    @Test
    public void testValueMapping() {
        Resources resources =
                ResourcesBuilder.create()
                        .add(LAYOUT_ID, "layout", "pck", "main_layout")
                        .add(STYLE_ID, "style", "android", "MyStyle")
                        .add(OTHER_ID, "id", "pck", "other")
                        .build();
        ColorDrawable yellow = mock(ColorDrawable.class);
        when(yellow.getColor()).thenReturn(Color.YELLOW);
        ColorStateList backgroundTint = mock(ColorStateList.class);
        when(backgroundTint.getDefaultColor()).thenReturn(Color.YELLOW);
        when(backgroundTint.getColorForState(any(), anyInt())).thenReturn(Color.BLUE);

        TextView textView =
                ViewBuilder.create(TextView.class, LinearLayout.LayoutParams.class)
                        .withFocused(true)
                        .withByte((byte) 7)
                        .withChar('g')
                        .withDouble(3.75)
                        .withScaleX(1.75f)
                        .withScrollX(10)
                        .withLong(7000L)
                        .withShort((short) 70)
                        .withTransitionName("MyTransitionName")
                        .withDrawableState(new int[] {android.R.attr.state_focused})
                        .withBackgroundTint(backgroundTint)
                        .withBackground(yellow)
                        .withOutlineSpotShadowColor(Color.RED)
                        .withForegroundGravity(Gravity.TOP | Gravity.LEFT | Gravity.RIGHT)
                        .withVisibility(View.INVISIBLE)
                        .withLabelFor(OTHER_ID)
                        .withScrollIndicators(
                                View.SCROLL_INDICATOR_LEFT | View.SCROLL_INDICATOR_BOTTOM)
                        .withLayoutWidth(ViewGroup.LayoutParams.MATCH_PARENT)
                        .withLayoutHeight(400)
                        .withResolutionStack(LAYOUT_ID, STYLE_ID, OTHER_ID)
                        .withResources(resources)
                        .build();

        when(textView.getText()).thenReturn("Hello World!");
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) textView.getLayoutParams();
        params.gravity = Gravity.END;
        params.bottomMargin = 10;

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
        assertThat(layoutProperties.get("layout_height").getValue()).isEqualTo("400");
        // From LinearLayout:
        assertThat(layoutProperties.get("layout_marginBottom").getValue()).isEqualTo(10);
    }

    private static Map<String, Property> asMap(List<Property> properties) {
        Map<String, Property> map = new HashMap<>();
        properties.forEach(property -> map.put(property.getPropertyType().getName(), property));
        return map;
    }
}

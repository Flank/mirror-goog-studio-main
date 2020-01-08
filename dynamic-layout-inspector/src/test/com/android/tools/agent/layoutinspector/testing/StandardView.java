/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.agent.layoutinspector.testing;

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

public class StandardView {
    private static final int LAYOUT_ID = 1;
    private static final int STYLE_ID = 2;
    private static final int OTHER_ID = 3;

    /** Creates a TextView instance for tests with LinearLayout LayoutParams. */
    public static TextView createTextView() {
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

        return textView;
    }
}

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.LocaleList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RootLinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.compose.ui.platform.AndroidComposeView;

public class StandardView {
    private static final int LAYOUT = 1;
    private static final int STYLE_ID = 2;
    private static final int OTHER_ID = 3;
    private static final int TEXT_VIEW_ID = 4;
    private static final int LINEAR_LAYOUT_ID = 5;
    private static final int THEME = 6;

    public static final String PACKAGE_NAME = "pck";

    /** Creates a TextView instance for tests with LinearLayout LayoutParams. */
    public static TextView createTextView() {
        Resources resources =
                ResourcesBuilder.create()
                        .add(LAYOUT, "layout", PACKAGE_NAME, "main_activity")
                        .add(STYLE_ID, "style", "android", "MyStyle")
                        .add(OTHER_ID, "id", PACKAGE_NAME, "other")
                        .add(TEXT_VIEW_ID, "id", PACKAGE_NAME, "textView1")
                        .build();
        ColorDrawable yellow = mock(ColorDrawable.class);
        when(yellow.getColor()).thenReturn(Color.YELLOW);
        ColorStateList backgroundTint = mock(ColorStateList.class);
        when(backgroundTint.getDefaultColor()).thenReturn(Color.YELLOW);
        when(backgroundTint.getColorForState(any(), anyInt())).thenReturn(Color.BLUE);

        TextView textView =
                ViewBuilder.create(TextView.class, LinearLayout.LayoutParams.class)
                        .withDrawId(11)
                        .withId(TEXT_VIEW_ID)
                        .withBounds(100, 200, 400, 30)
                        .withLayoutId(LAYOUT)
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
                        .withResolutionStack(LAYOUT, STYLE_ID, OTHER_ID)
                        .withResources(resources)
                        .withAttachedToWindow(true)
                        .build();

        when(textView.getText()).thenReturn("Hello World!");
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) textView.getLayoutParams();
        params.gravity = Gravity.END;
        params.bottomMargin = 10;

        return textView;
    }

    /** Creates a LinearLayout instance with a TextView as a child. */
    public static LinearLayout createLinearLayoutWithTextView() {
        return createLinearLayoutWithComponent(createTextView());
    }

    /** Creates a LinearLayout instance with a ComposeView as a child. */
    public static LinearLayout createLinearLayoutWithComposeView() {
        return createLinearLayoutWithComponent(
                ViewBuilder.create(AndroidComposeView.class, LinearLayout.LayoutParams.class)
                        .build());
    }

    /** Creates a root of a LinearLayout with the specified view as a child. */
    private static LinearLayout createLinearLayoutWithComponent(@NonNull View view) {
        Resources resources =
                ResourcesBuilder.create()
                        .add(LAYOUT, "layout", PACKAGE_NAME, "main_activity")
                        .add(LINEAR_LAYOUT_ID, "id", PACKAGE_NAME, "linearLayout1")
                        .add(THEME, "style", PACKAGE_NAME, "MyTheme")
                        .build();

        LinearLayout linearLayout =
                ViewBuilder.create(RootLinearLayout.class, WindowManager.LayoutParams.class)
                        .withDrawId(10)
                        .withId(LINEAR_LAYOUT_ID)
                        .withBounds(10, 50, 980, 2000)
                        .withLayoutId(LAYOUT)
                        .withLayoutWidth(ViewGroup.LayoutParams.MATCH_PARENT)
                        .withLayoutHeight(ViewGroup.LayoutParams.MATCH_PARENT)
                        .withResources(resources)
                        .withResolutionStack(LAYOUT, THEME)
                        .withAttachedToWindow(true)
                        .build();

        WindowManager.LayoutParams params =
                (WindowManager.LayoutParams) linearLayout.getLayoutParams();
        params.flags =
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_DIM_BEHIND;

        Context context = createContext();
        when(linearLayout.getChildCount()).thenReturn(1);
        when(linearLayout.getChildAt(eq(0))).thenReturn(view);
        when(linearLayout.getContext()).thenReturn(context);

        return linearLayout;
    }

    @NonNull
    private static Context createContext() {
        LocaleList locales = mock(LocaleList.class);
        when(locales.isEmpty()).thenReturn(true);
        Configuration config = mock(Configuration.class);
        when(config.getLocales()).thenReturn(locales);
        config.fontScale = 1.5f;
        config.mcc = 310; // US
        config.mnc = 4; // Verizon
        config.screenLayout =
                Configuration.SCREENLAYOUT_SIZE_LARGE
                        | Configuration.SCREENLAYOUT_LONG_NO
                        | Configuration.SCREENLAYOUT_LAYOUTDIR_RTL;
        config.colorMode =
                Configuration.COLOR_MODE_WIDE_COLOR_GAMUT_NO | Configuration.COLOR_MODE_HDR_YES;
        config.touchscreen = Configuration.TOUCHSCREEN_FINGER;
        config.keyboard = Configuration.KEYBOARD_QWERTY;
        config.keyboardHidden = Configuration.KEYBOARDHIDDEN_YES;
        config.hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_NO;
        config.navigation = Configuration.NAVIGATION_WHEEL;
        config.navigationHidden = Configuration.NAVIGATIONHIDDEN_YES;
        config.uiMode = Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_YES;
        config.smallestScreenWidthDp = Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED;
        config.densityDpi = 367;
        config.orientation = Configuration.ORIENTATION_PORTRAIT;
        config.screenWidthDp = 1080;
        config.screenHeightDp = 2280;
        Resources resources = mock(Resources.class);
        when(resources.getConfiguration()).thenReturn(config);
        Context context = mock(Context.class);
        when(context.getPackageName()).thenReturn(StandardView.PACKAGE_NAME);
        when(context.getResources()).thenReturn(resources);
        return context;
    }
}

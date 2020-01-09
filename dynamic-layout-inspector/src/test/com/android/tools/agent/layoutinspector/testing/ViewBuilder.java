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

package com.android.tools.agent.layoutinspector.testing;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;

/** Utility to create a mock {@link android.view.View} for testing. */
public class ViewBuilder<V extends View, L extends ViewGroup.LayoutParams> {
    private final V myView;
    private final L myLayoutParams;
    private final FakeData myFakeData;

    @NonNull
    public static <V extends View> ViewBuilder<V, ViewGroup.LayoutParams> create(
            @NonNull Class<V> viewClass) {
        return create(viewClass, ViewGroup.LayoutParams.class);
    }

    @NonNull
    public static <V extends View, L extends ViewGroup.LayoutParams> ViewBuilder<V, L> create(
            @NonNull Class<V> viewClass, @NonNull Class<L> layoutParams) {
        return new ViewBuilder<>(mock(viewClass), mock(layoutParams));
    }

    @NonNull
    public ViewBuilder<V, L> withDrawId(long drawId) {
        when(myView.getUniqueDrawingId()).thenReturn(drawId);
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withId(int id) {
        when(myView.getId()).thenReturn(id);
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withBounds(int x, int y, int width, int height) {
        when(myView.getLeft()).thenReturn(x);
        when(myView.getTop()).thenReturn(y);
        when(myView.getWidth()).thenReturn(width);
        when(myView.getHeight()).thenReturn(height);
        when(myView.getRight()).thenReturn(x + width);
        when(myView.getBottom()).thenReturn(y + height);
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withLayoutId(int layoutId) {
        when(myView.getSourceLayoutResId()).thenReturn(layoutId);
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withResolutionStack(int... stack) {
        when(myView.getAttributeResolutionStack(anyInt())).thenReturn(stack);
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withResources(@NonNull Resources resources) {
        when(myView.getResources()).thenReturn(resources);
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withFocused(boolean focused) {
        when(myView.isFocused()).thenReturn(focused);
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withByte(byte value) {
        myFakeData.setByteValue(value);
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withChar(char value) {
        myFakeData.setCharValue(value);
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withDouble(double value) {
        myFakeData.setDoubleValue(value);
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withScaleX(float value) {
        when(myView.getScaleX()).thenReturn(value);
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withScrollX(int value) {
        when(myView.getScrollX()).thenReturn(value);
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withLong(long value) {
        myFakeData.setLongValue(value);
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withShort(short value) {
        myFakeData.setShortValue(value);
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withTransitionName(@NonNull String value) {
        when(myView.getTransitionName()).thenReturn(value);
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withBackgroundTint(@NonNull ColorStateList value) {
        when(myView.getBackgroundTintList()).thenReturn(value);
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withBackground(@NonNull Drawable value) {
        when(myView.getBackground()).thenReturn(value);
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withStateListAnimator(@NonNull Drawable value) {
        when(myView.getBackground()).thenReturn(value);
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withOutlineSpotShadowColor(int color) {
        when(myView.getOutlineSpotShadowColor()).thenReturn(color);
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withForegroundGravity(int gravity) {
        when(myView.getForegroundGravity()).thenReturn(gravity);
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withVisibility(int visibility) {
        when(myView.getVisibility()).thenReturn(visibility);
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withLabelFor(int id) {
        when(myView.getLabelFor()).thenReturn(id);
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withScrollIndicators(int value) {
        when(myView.getScrollIndicators()).thenReturn(value);
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withDrawableState(int[] value) {
        when(myView.getDrawableState()).thenReturn(value);
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withLayoutWidth(int value) {
        myLayoutParams.width = value;
        return this;
    }

    @NonNull
    public ViewBuilder<V, L> withLayoutHeight(int value) {
        myLayoutParams.height = value;
        return this;
    }

    @NonNull
    public V build() {
        return myView;
    }

    private ViewBuilder(@NonNull V view, @NonNull L layoutParams) {
        myView = view;
        myLayoutParams = layoutParams;
        myFakeData = new FakeData();
        when(myView.getLayoutParams()).thenReturn(layoutParams);
        // Hack: This data is read in CompanionSupplierRule as if these attributes were set
        // in the View itself.
        when(myView.getTag()).thenReturn(myFakeData);
    }
}

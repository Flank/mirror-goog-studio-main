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

import static android.view.inspector.StaticInspectionCompanionProvider.register;

import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.InspectionCompanion;
import android.view.inspector.PropertyMapper;
import android.view.inspector.PropertyReader;
import android.view.inspector.StaticInspectionCompanionProvider;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.android.tools.agent.layoutinspector.property.IntFlagMapping;
import java.util.function.IntFunction;
import org.junit.rules.ExternalResource;

public class CompanionSupplierRule extends ExternalResource {

    @Override
    public void before() {
        register(View.class, new ViewInspectionCompanion());
        register(TextView.class, new TextViewInspectionCompanion());
        register(ViewGroup.LayoutParams.class, new ViewGroupLayoutParamsInspectionCompanion());
        register(LinearLayout.LayoutParams.class, new LinearLayoutParamsInspectionCompanion());
    }

    @Override
    public void after() {
        StaticInspectionCompanionProvider.cleanUp();
    }

    private static class ViewInspectionCompanion implements InspectionCompanion<View> {
        private IntFlagMapping myScrollIndicatorsMapping = new IntFlagMapping();
        private IntFunction<String> myVisibilityMapping =
                value -> {
                    switch (value) {
                        case View.VISIBLE:
                            return "visible";
                        case View.INVISIBLE:
                            return "invisible";
                        default:
                            return "gone";
                    }
                };

        private ViewInspectionCompanion() {
            myScrollIndicatorsMapping.add(0xffff_ffff, 0, "none");
            myScrollIndicatorsMapping.add(
                    View.SCROLL_INDICATOR_TOP, View.SCROLL_INDICATOR_TOP, "top");
            myScrollIndicatorsMapping.add(
                    View.SCROLL_INDICATOR_BOTTOM, View.SCROLL_INDICATOR_BOTTOM, "bottom");
            myScrollIndicatorsMapping.add(
                    View.SCROLL_INDICATOR_LEFT, View.SCROLL_INDICATOR_LEFT, "left");
            myScrollIndicatorsMapping.add(
                    View.SCROLL_INDICATOR_RIGHT, View.SCROLL_INDICATOR_RIGHT, "right");
            myScrollIndicatorsMapping.add(
                    View.SCROLL_INDICATOR_START, View.SCROLL_INDICATOR_START, "start");
            myScrollIndicatorsMapping.add(
                    View.SCROLL_INDICATOR_END, View.SCROLL_INDICATOR_END, "end");
        }

        enum Property {
            FOCUSED,
            BYTE,
            CHAR,
            DOUBLE,
            SCALE_X,
            SCROLL_X,
            LONG,
            SHORT,
            TRANSITION_NAME,
            BACKGROUND_TINT,
            BACKGROUND,
            STATE_LIST_ANIMATOR,
            ANIMATOR,
            INTERPOLATOR,
            OUTLINE_SPOT_SHADOW_COLOR,
            FOREGROUND_GRAVITY,
            VISIBILITY,
            LABEL_FOR,
            SCROLL_INDICATORS
        }

        @Override
        public void mapProperties(@NonNull PropertyMapper propertyMapper) {
            EnumPropertyMapper<Property> mapper = new EnumPropertyMapper<>(propertyMapper, 0);
            mapper.mapBoolean(Property.FOCUSED);
            mapper.mapByte(Property.BYTE);
            mapper.mapChar(Property.CHAR);
            mapper.mapDouble(Property.DOUBLE);
            mapper.mapFloat(Property.SCALE_X);
            mapper.mapInt(Property.SCROLL_X);
            mapper.mapLong(Property.LONG);
            mapper.mapShort(Property.SHORT);
            mapper.mapObject(Property.TRANSITION_NAME);
            mapper.mapObject(Property.BACKGROUND_TINT);
            mapper.mapObject(Property.BACKGROUND);
            mapper.mapObject(Property.STATE_LIST_ANIMATOR);
            mapper.mapObject(Property.ANIMATOR);
            mapper.mapObject(Property.INTERPOLATOR);
            mapper.mapColor(Property.OUTLINE_SPOT_SHADOW_COLOR);
            mapper.mapGravity(Property.FOREGROUND_GRAVITY);
            mapper.mapIntEnum(Property.VISIBILITY, myVisibilityMapping);
            mapper.mapResourceId(Property.LABEL_FOR);
            mapper.mapIntFlag(Property.SCROLL_INDICATORS, myScrollIndicatorsMapping);
        }

        @Override
        public void readProperties(@NonNull View view, @NonNull PropertyReader propertyReader) {
            FakeData data = (FakeData) view.getTag();
            EnumPropertyReader<Property> reader = new EnumPropertyReader<>(propertyReader, 0);
            reader.readBoolean(Property.FOCUSED, view.isFocused());
            reader.readByte(Property.BYTE, data.getByteValue());
            reader.readChar(Property.CHAR, data.getCharValue());
            reader.readDouble(Property.DOUBLE, data.getDoubleValue());
            reader.readFloat(Property.SCALE_X, view.getScaleX());
            reader.readInt(Property.SCROLL_X, view.getScrollX());
            reader.readLong(Property.LONG, data.getLongValue());
            reader.readShort(Property.SHORT, data.getShortValue());
            reader.readObject(Property.TRANSITION_NAME, view.getTransitionName());
            reader.readObject(Property.BACKGROUND_TINT, view.getBackgroundTintList());
            reader.readObject(Property.BACKGROUND, view.getBackground());
            reader.readObject(Property.STATE_LIST_ANIMATOR, view.getStateListAnimator());
            reader.readObject(Property.ANIMATOR, null);
            reader.readObject(Property.INTERPOLATOR, null);
            reader.readColor(Property.OUTLINE_SPOT_SHADOW_COLOR, view.getOutlineSpotShadowColor());
            reader.readGravity(Property.FOREGROUND_GRAVITY, view.getForegroundGravity());
            reader.readIntEnum(Property.VISIBILITY, view.getVisibility());
            reader.readResourceId(Property.LABEL_FOR, view.getLabelFor());
            reader.readIntFlag(Property.SCROLL_INDICATORS, view.getScrollIndicators());
        }
    }

    private static class TextViewInspectionCompanion implements InspectionCompanion<TextView> {
        private final int myOffset = ViewInspectionCompanion.Property.values().length;

        enum Property {
            TEXT,
        }

        @Override
        public void mapProperties(@NonNull PropertyMapper propertyMapper) {
            EnumPropertyMapper<Property> mapper =
                    new EnumPropertyMapper<>(propertyMapper, myOffset);
            mapper.mapObject(Property.TEXT);
        }

        @Override
        public void readProperties(
                @NonNull TextView textView, @NonNull PropertyReader propertyReader) {
            EnumPropertyReader<Property> reader =
                    new EnumPropertyReader<>(propertyReader, myOffset);
            reader.readObject(Property.TEXT, textView.getText());
        }
    }

    private static class ViewGroupLayoutParamsInspectionCompanion
            implements InspectionCompanion<ViewGroup.LayoutParams> {
        private IntFunction<String> mySizeMapping =
                value -> {
                    switch (value) {
                        case ViewGroup.LayoutParams.MATCH_PARENT:
                            return "match_parent";
                        case ViewGroup.LayoutParams.WRAP_CONTENT:
                            return "wrap_content";
                        default:
                            return Integer.toString(value);
                    }
                };

        enum Property {
            WIDTH,
            HEIGHT,
        }

        @Override
        public void mapProperties(@NonNull PropertyMapper propertyMapper) {
            EnumPropertyMapper<Property> mapper =
                    new EnumPropertyMapper<>(propertyMapper, 0, "layout_");
            mapper.mapIntEnum(Property.WIDTH, mySizeMapping);
            mapper.mapIntEnum(Property.HEIGHT, mySizeMapping);
        }

        @Override
        public void readProperties(
                @NonNull ViewGroup.LayoutParams layoutParams,
                @NonNull PropertyReader propertyReader) {
            EnumPropertyReader<Property> reader = new EnumPropertyReader<>(propertyReader, 0);
            reader.readIntEnum(Property.WIDTH, layoutParams.width);
            reader.readIntEnum(Property.HEIGHT, layoutParams.height);
        }
    }

    private static class LinearLayoutParamsInspectionCompanion
            implements InspectionCompanion<LinearLayout.LayoutParams> {
        private final int myOffset =
                ViewGroupLayoutParamsInspectionCompanion.Property.values().length;

        enum Property {
            MARGIN_BOTTOM,
            GRAVITY,
        }

        @Override
        public void mapProperties(@NonNull PropertyMapper propertyMapper) {
            EnumPropertyMapper<Property> mapper =
                    new EnumPropertyMapper<>(propertyMapper, myOffset, "layout_");
            mapper.mapInt(Property.MARGIN_BOTTOM);
            mapper.mapGravity(Property.GRAVITY);
        }

        @Override
        public void readProperties(
                @NonNull LinearLayout.LayoutParams layoutParams,
                @NonNull PropertyReader propertyReader) {
            EnumPropertyReader<Property> reader =
                    new EnumPropertyReader<>(propertyReader, myOffset);
            reader.readInt(Property.MARGIN_BOTTOM, layoutParams.bottomMargin);
            reader.readGravity(Property.GRAVITY, layoutParams.gravity);
        }
    }
}

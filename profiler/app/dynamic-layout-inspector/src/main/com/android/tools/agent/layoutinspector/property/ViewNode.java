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

import android.animation.StateListAnimator;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Interpolator;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.Animation;
import android.view.inspector.PropertyReader;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.tools.agent.layoutinspector.common.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/** Holds the properties (including their values) of a View. */
public class ViewNode<V extends View> {
    private ViewType<V> mType;
    private LayoutParamsType mLayoutParamsType;
    private List<Property> mViewProperties;
    private List<Property> mLayoutProperties;

    public ViewNode(@NonNull ViewType<V> type, @NonNull LayoutParamsType layoutParamsType) {
        mType = type;
        mLayoutParamsType = layoutParamsType;
        mViewProperties = new ArrayList<>();
        for (PropertyType propertyType : type.getProperties()) {
            mViewProperties.add(new Property(propertyType));
        }
        mLayoutProperties = new ArrayList<>();
        for (PropertyType propertyType : layoutParamsType.getProperties()) {
            mLayoutProperties.add(new Property(propertyType));
        }
    }

    @NonNull
    public ViewType<V> getType() {
        return mType;
    }

    @NonNull
    public List<Property> getViewProperties() {
        return mViewProperties;
    }

    @NonNull
    public List<Property> getLayoutProperties() {
        return mLayoutProperties;
    }

    public void readProperties(@NonNull V view) {
        PropertyReader viewReader = new SimplePropertyReader<>(view, mViewProperties, true);
        mType.readProperties(view, viewReader);
        PropertyReader layoutReader = new SimplePropertyReader<>(view, mLayoutProperties, false);
        mLayoutParamsType.readProperties(view.getLayoutParams(), layoutReader);
    }

    public Resource getLayoutResource(@NonNull V view) {
        return Resource.fromResourceId(view, view.getSourceLayoutResId());
    }

    private static class SimplePropertyReader<V extends View> implements PropertyReader {
        private final V mView;
        private final List<Property> mProperties;
        private final Map<Integer, Integer> mResourceMap;
        private final boolean mIsViewProperties;

        SimplePropertyReader(
                @NonNull V view, @NonNull List<Property> properties, boolean isViewProperties) {
            mView = view;
            mProperties = properties;
            mResourceMap = view.getAttributeSourceResourceMap();
            mIsViewProperties = isViewProperties;
        }

        @Override
        public void readBoolean(int id, boolean b) {
            readAny(id, b ? 1 : 0);
        }

        @Override
        public void readByte(int id, byte b) {
            readAny(id, (int) b);
        }

        @Override
        public void readChar(int id, char c) {
            readAny(id, (int) c);
        }

        @Override
        public void readDouble(int id, double d) {
            readAny(id, d);
        }

        @Override
        public void readFloat(int id, float f) {
            readAny(id, f);
        }

        @Override
        public void readInt(int id, int i) {
            readAny(id, i);
        }

        @Override
        public void readLong(int id, long l) {
            readAny(id, l);
        }

        @Override
        public void readShort(int id, short s) {
            readAny(id, (int) s);
        }

        @Override
        public void readObject(int id, @Nullable Object o) {
            if (o instanceof String) {
                readAny(id, o.toString().intern());
                mProperties.get(id).setType(ValueType.STRING);
            } else if (o instanceof ColorStateList) {
                ColorStateList cl = (ColorStateList) o;
                readAny(id, cl.getColorForState(mView.getDrawableState(), cl.getDefaultColor()));
                mProperties.get(id).setType(ValueType.COLOR);
            } else if (o instanceof ColorDrawable) {
                readAny(id, ((ColorDrawable) o).getColor());
                mProperties.get(id).setType(ValueType.COLOR);
            } else if (o instanceof Drawable) {
                readAny(id, o);
                mProperties.get(id).setType(ValueType.DRAWABLE);
            } else if (o instanceof Animation) {
                readAny(id, o);
                mProperties.get(id).setType(ValueType.ANIM);
            } else if (o instanceof StateListAnimator) {
                readAny(id, o);
                mProperties.get(id).setType(ValueType.ANIMATOR);
            } else if (o instanceof Interpolator) {
                readAny(id, o);
                mProperties.get(id).setType(ValueType.INTERPOLATOR);
            }
        }

        @Override
        public void readColor(int id, int color) {
            readAny(id, color);
        }

        @Override
        public void readColor(int id, long color) {
            readAny(id, color);
        }

        @Override
        public void readColor(int id, @NonNull Color color) {
            readAny(id, color);
        }

        @Override
        public void readGravity(int id, int value) {
            readIntFlag(id, value);
        }

        @Override
        public void readIntEnum(int id, int value) {
            Property property = mProperties.get(id);
            PropertyType type = property.getPropertyType();
            IntFunction<String> mapping = type.getEnumMapping();
            if (mapping != null) {
                readAny(id, mapping.apply(value));
            }
        }

        @Override
        public void readIntFlag(int id, int value) {
            Property property = mProperties.get(id);
            PropertyType type = property.getPropertyType();
            IntFunction<Set<String>> mapping = type.getFlagMapping();
            if (mapping != null) {
                readAny(id, mapping.apply(value));
            }
        }

        @Override
        public void readResourceId(int id, int value) {
            readAny(id, Resource.fromResourceId(mView, value));
        }

        private void readAny(int id, @Nullable Object value) {
            Property property = mProperties.get(id);
            PropertyType type = property.getPropertyType();
            property.setValue(value);
            if (mIsViewProperties) {
                property.setSource(getResourceValueOfAttribute(type.getAttributeId()));
                addResolutionStack(property.getResolutionStack(), type.getAttributeId());
            }
        }

        @Nullable
        private Resource getResourceValueOfAttribute(int attributeId) {
            Integer resourceId = mResourceMap.get(attributeId);
            return resourceId != null ? Resource.fromResourceId(mView, resourceId) : null;
        }

        private void addResolutionStack(@NonNull List<Resource> stack, int attributeId) {
            int[] ids = mView.getAttributeResolutionStack(attributeId);
            for (int id : ids) {
                stack.add(Resource.fromResourceId(mView, id));
            }
        }
    }
}

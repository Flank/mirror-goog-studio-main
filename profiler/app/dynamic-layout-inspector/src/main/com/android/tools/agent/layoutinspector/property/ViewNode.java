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

import android.graphics.Color;
import android.view.View;
import android.view.inspector.PropertyReader;
import com.android.tools.agent.layoutinspector.common.Resource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Holds the properties (including their values) of a View. */
public class ViewNode<V extends View> {
    private ViewType<V> mType;
    private List<Property> mProperties;

    public ViewNode(ViewType<V> type) {
        mType = type;
        mProperties = new ArrayList<>();
        for (PropertyType propertyType : type.getProperties()) {
            mProperties.add(new Property(propertyType));
        }
    }

    public ViewType<V> getType() {
        return mType;
    }

    public List<Property> getProperties() {
        return mProperties;
    }

    public void readProperties(V view) {
        PropertyReader reader = createPropertyReader(view);
        mType.readProperties(view, reader);
    }

    public Resource getLayoutResource(V view) {
        return Resource.fromResourceId(view, getSourceLayoutResId(view));
    }

    private SimplePropertyReader createPropertyReader(V view) {
        return new SimplePropertyReader(view);
    }

    private int getSourceLayoutResId(V view) {
        try {
            // TODO: Call this method directly when we compile against android-Q
            Method method = View.class.getDeclaredMethod("getSourceLayoutResId");
            Integer layoutId = (Integer) method.invoke(view);
            return layoutId != null ? layoutId : 0;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            return 0;
        }
    }

    private Map<Integer, Integer> getAttributeSourceResourceMap(V view) {
        try {
            // TODO: Call this method directly when we compile against android-Q
            Method method = View.class.getDeclaredMethod("getAttributeSourceResourceMap");
            //noinspection unchecked
            return (Map<Integer, Integer>) method.invoke(view);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            return Collections.emptyMap();
        }
    }

    private class SimplePropertyReader implements PropertyReader {
        private final V mView;
        private final Map<Integer, Integer> mResourceMap;

        private SimplePropertyReader(V view) {
            mView = view;
            mResourceMap = getAttributeSourceResourceMap(view);
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
        public void readObject(int id, Object o) {
            if (o instanceof String) {
                readAny(id, o.toString().intern());
                mProperties.get(id).setType(ValueType.STRING);
            } else {
                readAny(id, null);
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
        public void readColor(int id, Color color) {
            readAny(id, color);
        }

        @Override
        public void readGravity(int id, int value) {
            readAny(id, value);
        }

        @Override
        public void readIntEnum(int id, int value) {
            readAny(id, value);
        }

        @Override
        public void readIntFlag(int id, int value) {
            readAny(id, value);
        }

        @Override
        public void readResourceId(int id, int value) {
            readAny(id, Resource.fromResourceId(mView, value));
        }

        private void readAny(int id, Object value) {
            Property property = mProperties.get(id);
            PropertyType type = property.getPropertyType();
            property.setValue(value);
            property.setSource(getResourceValueOfAttribute(type.getAttributeId()));
        }

        private Resource getResourceValueOfAttribute(int attributeId) {
            Integer resourceId = mResourceMap.get(attributeId);
            return resourceId != null ? Resource.fromResourceId(mView, resourceId) : null;
        }
    }
}

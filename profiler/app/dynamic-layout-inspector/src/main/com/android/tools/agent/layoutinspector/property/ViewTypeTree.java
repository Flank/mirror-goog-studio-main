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

import android.view.View;
import android.view.inspector.*;
import java.util.*;
import java.util.Set;
import java.util.function.IntFunction;
/**
 * Holds a tree of {@link ViewType}s.
 *
 * <p>A caller can add a ViewType to the tree by calling {@link #nodeOf}. All super classes of this
 * View up to android.view.View will be included.
 */
public class ViewTypeTree {
    private InspectionCompanionProvider inspectionCompanionProvider =
            new StaticInspectionCompanionProvider();
    private Map<Class<? extends View>, ViewType<? extends View>> typeMap = new HashMap<>();

    public <V extends View> ViewNode<V> nodeOf(V view) {
        return typeOf(view).newNode();
    }

    @SuppressWarnings("unchecked")
    private <V extends View> ViewType<V> typeOf(V view) {
        return typeOf((Class<V>) view.getClass());
    }

    private <V extends View> ViewType<V> typeOf(Class<V> viewClass) {
        return innerTypeOf(viewClass);
    }

    private <V extends View> ViewType<V> innerTypeOf(Class<V> viewClass) {
        @SuppressWarnings("unchecked")
        ViewType<V> type = (ViewType<V>) typeMap.get(viewClass);
        if (type != null) {
            return type;
        }

        InspectionCompanion inspectionCompanion = loadInspectionCompanion(viewClass);
        @SuppressWarnings("unchecked")
        ViewType<? extends View> superType =
                !viewClass.getCanonicalName().equals("android.view.View")
                        ? innerTypeOf((Class<? extends View>) viewClass.getSuperclass())
                        : null;
        List<InspectionCompanion> companions = new ArrayList<>();
        if (superType != null) {
            companions.addAll(superType.getInspectionCompanions());
        }
        if (inspectionCompanion != null) {
            companions.add(inspectionCompanion);
        }

        List<PropertyType> properties = new ArrayList<>();
        String nodeName = viewClass.getSimpleName();
        if (superType != null) {
            properties.addAll(superType.getProperties());
        }
        if (inspectionCompanion != null) {
            PropertyTypeMapper mapper = new PropertyTypeMapper(properties);
            inspectionCompanion.mapProperties(mapper);
            properties = mapper.getProperties();
        }

        //noinspection unchecked
        type = new ViewType(nodeName, viewClass.getCanonicalName(), properties, companions);
        typeMap.put(viewClass, type);
        return type;
    }

    private <V extends View> InspectionCompanion<V> loadInspectionCompanion(Class<V> javaClass) {
        return inspectionCompanionProvider.provide(javaClass);
    }

    private class PropertyTypeMapper implements PropertyMapper {
        private List<PropertyType> mProperties;

        private PropertyTypeMapper(List<PropertyType> existingProperties) {
            mProperties = new ArrayList<>(existingProperties);
        }

        private List<PropertyType> getProperties() {
            return mProperties;
        }

        @Override
        public int mapBoolean(String name, int attributeId) {
            return map(name, attributeId, ValueType.BOOLEAN);
        }

        @Override
        public int mapByte(String name, int attributeId) {
            return map(name, attributeId, ValueType.BYTE);
        }

        @Override
        public int mapChar(String name, int attributeId) {
            return map(name, attributeId, ValueType.CHAR);
        }

        @Override
        public int mapDouble(String name, int attributeId) {
            return map(name, attributeId, ValueType.DOUBLE);
        }

        @Override
        public int mapFloat(String name, int attributeId) {
            return map(name, attributeId, ValueType.FLOAT);
        }

        @Override
        public int mapInt(String name, int attributeId) {
            return map(name, attributeId, ValueType.INT32);
        }

        @Override
        public int mapLong(String name, int attributeId) {
            return map(name, attributeId, ValueType.INT64);
        }

        @Override
        public int mapShort(String name, int attributeId) {
            return map(name, attributeId, ValueType.INT16);
        }

        @Override
        public int mapObject(String name, int attributeId) {
            return map(name, attributeId, ValueType.OBJECT);
        }

        @Override
        public int mapColor(String name, int attributeId) {
            return map(name, attributeId, ValueType.COLOR);
        }

        @Override
        public int mapGravity(String name, int attributeId) {
            return map(name, attributeId, ValueType.GRAVITY);
        }

        @Override
        public int mapIntEnum(String name, int attributeId, IntFunction<String> mapping) {
            return map(name, attributeId, ValueType.INT_ENUM);
        }

        @Override
        public int mapIntFlag(String name, int attributeId, IntFunction<Set<String>> mapping) {
            return map(name, attributeId, ValueType.INT_FLAG);
        }

        @Override
        public int mapResourceId(String name, int attributeId) {
            return map(name, attributeId, ValueType.RESOURCE);
        }

        private int map(String name, int attributeId, ValueType type) {
            // TODO: Handle duplicate property names as per spec
            int id = mProperties.size();
            mProperties.add(new PropertyType(name, attributeId, id, type));
            return id;
        }
    }
}

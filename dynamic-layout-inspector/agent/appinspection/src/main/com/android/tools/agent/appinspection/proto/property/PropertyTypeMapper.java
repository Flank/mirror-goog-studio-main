/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.agent.appinspection.proto.property;

import android.view.inspector.PropertyMapper;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Property;

/**
 * A {@link PropertyMapper} implementation that accepts property values and generates {@link
 * PropertyBuilder} instances for each.
 *
 * <p>These builders can then be used to generate {@link Property} proto instances, corresponding to
 * the properties read in from the Android framework.
 */
public final class PropertyTypeMapper implements PropertyMapper {

    private final IntFunction<Set<String>> gravityMapping = new GravityIntMapping();
    private final List<PropertyBuilder> mProperties;

    PropertyTypeMapper(@NonNull List<PropertyBuilder> existingProperties) {
        mProperties = new ArrayList<>(existingProperties);
    }

    @NonNull
    List<PropertyBuilder> getProperties() {
        return mProperties;
    }

    @Override
    public int mapBoolean(@NonNull String name, int attributeId) {
        return map(name, attributeId, Property.Type.BOOLEAN);
    }

    @Override
    public int mapByte(@NonNull String name, int attributeId) {
        return map(name, attributeId, Property.Type.BYTE);
    }

    @Override
    public int mapChar(@NonNull String name, int attributeId) {
        return map(name, attributeId, Property.Type.CHAR);
    }

    @Override
    public int mapDouble(@NonNull String name, int attributeId) {
        return map(name, attributeId, Property.Type.DOUBLE);
    }

    @Override
    public int mapFloat(@NonNull String name, int attributeId) {
        return map(name, attributeId, Property.Type.FLOAT);
    }

    @Override
    public int mapInt(@NonNull String name, int attributeId) {
        return map(name, attributeId, Property.Type.INT32);
    }

    @Override
    public int mapLong(@NonNull String name, int attributeId) {
        return map(name, attributeId, Property.Type.INT64);
    }

    @Override
    public int mapShort(@NonNull String name, int attributeId) {
        return map(name, attributeId, Property.Type.INT16);
    }

    @Override
    public int mapObject(@NonNull String name, int attributeId) {
        return map(name, attributeId, Property.Type.OBJECT);
    }

    @Override
    public int mapColor(@NonNull String name, int attributeId) {
        return map(name, attributeId, Property.Type.COLOR);
    }

    @Override
    public int mapGravity(@NonNull String name, int attributeId) {
        int id = map(name, attributeId, Property.Type.GRAVITY);
        mProperties.get(id).setFlagMapping(gravityMapping);
        return id;
    }

    @Override
    public int mapIntEnum(
            @NonNull String name, int attributeId, @NonNull IntFunction<String> mapping) {
        int id = map(name, attributeId, Property.Type.INT_ENUM);
        mProperties.get(id).setEnumMapping(mapping);
        return id;
    }

    @Override
    public int mapIntFlag(
            @NonNull String name, int attributeId, @NonNull IntFunction<Set<String>> mapping) {
        int id = map(name, attributeId, Property.Type.INT_FLAG);
        mProperties.get(id).setFlagMapping(mapping);
        return id;
    }

    @Override
    public int mapResourceId(@NonNull String name, int attributeId) {
        return map(name, attributeId, Property.Type.RESOURCE);
    }

    private int map(@NonNull String name, int attributeId, @NonNull Property.Type type) {
        // TODO: Handle duplicate property names as per spec
        int id = mProperties.size();
        PropertyMetadata metadata = new PropertyMetadata(name, attributeId, type);
        mProperties.add(new PropertyBuilder(metadata));
        return id;
    }
}

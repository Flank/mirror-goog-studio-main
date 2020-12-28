package com.android.tools.agent.layoutinspector.property;

import android.view.inspector.PropertyMapper;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;

class PropertyTypeMapper implements PropertyMapper {
    private IntFunction<Set<String>> gravityMapping = new GravityIntMapping();
    private List<PropertyType> mProperties;

    PropertyTypeMapper(@NonNull List<PropertyType> existingProperties) {
        mProperties = new ArrayList<>(existingProperties);
    }

    @NonNull
    List<PropertyType> getProperties() {
        return mProperties;
    }

    @Override
    public int mapBoolean(@NonNull String name, int attributeId) {
        return map(name, attributeId, ValueType.BOOLEAN);
    }

    @Override
    public int mapByte(@NonNull String name, int attributeId) {
        return map(name, attributeId, ValueType.BYTE);
    }

    @Override
    public int mapChar(@NonNull String name, int attributeId) {
        return map(name, attributeId, ValueType.CHAR);
    }

    @Override
    public int mapDouble(@NonNull String name, int attributeId) {
        return map(name, attributeId, ValueType.DOUBLE);
    }

    @Override
    public int mapFloat(@NonNull String name, int attributeId) {
        return map(name, attributeId, ValueType.FLOAT);
    }

    @Override
    public int mapInt(@NonNull String name, int attributeId) {
        return map(name, attributeId, ValueType.INT32);
    }

    @Override
    public int mapLong(@NonNull String name, int attributeId) {
        return map(name, attributeId, ValueType.INT64);
    }

    @Override
    public int mapShort(@NonNull String name, int attributeId) {
        return map(name, attributeId, ValueType.INT16);
    }

    @Override
    public int mapObject(@NonNull String name, int attributeId) {
        return map(name, attributeId, ValueType.OBJECT);
    }

    @Override
    public int mapColor(@NonNull String name, int attributeId) {
        return map(name, attributeId, ValueType.COLOR);
    }

    @Override
    public int mapGravity(@NonNull String name, int attributeId) {
        int id = map(name, attributeId, ValueType.GRAVITY);
        mProperties.get(id).setFlagMapping(gravityMapping);
        return id;
    }

    @Override
    public int mapIntEnum(
            @NonNull String name, int attributeId, @NonNull IntFunction<String> mapping) {
        int id = map(name, attributeId, ValueType.INT_ENUM);
        mProperties.get(id).setEnumMapping(mapping);
        return id;
    }

    @Override
    public int mapIntFlag(
            @NonNull String name, int attributeId, @NonNull IntFunction<Set<String>> mapping) {
        int id = map(name, attributeId, ValueType.INT_FLAG);
        mProperties.get(id).setFlagMapping(mapping);
        return id;
    }

    @Override
    public int mapResourceId(@NonNull String name, int attributeId) {
        return map(name, attributeId, ValueType.RESOURCE);
    }

    private int map(@NonNull String name, int attributeId, @NonNull ValueType type) {
        // TODO: Handle duplicate property names as per spec
        int id = mProperties.size();
        mProperties.add(new PropertyType(name, attributeId, id, type));
        return id;
    }
}

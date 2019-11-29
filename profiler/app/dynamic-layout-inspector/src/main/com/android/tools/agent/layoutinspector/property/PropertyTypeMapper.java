package com.android.tools.agent.layoutinspector.property;

import android.view.inspector.PropertyMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;

class PropertyTypeMapper implements PropertyMapper {
    private IntFunction<Set<String>> gravityMapping = new GravityIntMapping();
    private List<PropertyType> mProperties;

    PropertyTypeMapper(List<PropertyType> existingProperties) {
        mProperties = new ArrayList<>(existingProperties);
    }

    List<PropertyType> getProperties() {
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
        int id = map(name, attributeId, ValueType.GRAVITY);
        mProperties.get(id).setFlagMapping(gravityMapping);
        return id;
    }

    @Override
    public int mapIntEnum(String name, int attributeId, IntFunction<String> mapping) {
        int id = map(name, attributeId, ValueType.INT_ENUM);
        mProperties.get(id).setEnumMapping(mapping);
        return id;
    }

    @Override
    public int mapIntFlag(String name, int attributeId, IntFunction<Set<String>> mapping) {
        int id = map(name, attributeId, ValueType.INT_FLAG);
        mProperties.get(id).setFlagMapping(mapping);
        return id;
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

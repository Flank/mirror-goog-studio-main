package com.android.tools.agent.layoutinspector.property;

/** The property type as determined from inspection. */
public class PropertyType {
    private String mName;
    private int mAttributeId;
    private int mPropertyId;
    private ValueType mType;

    public PropertyType(String name, int attributeId, int propertyId, ValueType type) {
        mName = name;
        mAttributeId = attributeId;
        mPropertyId = propertyId;
        mType = type;
    }

    public String getName() {
        return mName;
    }

    public int getAttributeId() {
        return mAttributeId;
    }

    public int getPropertyId() {
        return mPropertyId;
    }

    public ValueType getType() {
        return mType;
    }
}

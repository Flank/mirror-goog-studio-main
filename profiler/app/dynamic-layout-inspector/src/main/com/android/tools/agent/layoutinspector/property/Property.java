package com.android.tools.agent.layoutinspector.property;

/** Representation of a Property including name, type, and value. */
public class Property {
    private PropertyType mType;
    private int mNameId;
    private Object mValue;
    private ValueType mValueType;
    private Resource mSource;

    public Property(PropertyType type) {
        mType = type;
        mValueType = type.getType();
    }

    public PropertyType getPropertyType() {
        return mType;
    }

    /**
     * The value of the property.
     *
     * <p>The type of the value depends on the value type which is stored separately. Certain values
     * are encoded e.g. a string is stored as an Integer which represent an id in the string table
     * that is generated along with the properties.
     */
    public Object getValue() {
        return mValue;
    }

    public ValueType getValueType() {
        return mValueType;
    }

    /** The name of the property represented as an id in a string table. */
    public int getNameId() {
        return mNameId;
    }

    public void setNameId(int nameId) {
        this.mNameId = nameId;
    }

    public void setType(ValueType type) {
        mValueType = type;
    }

    public void setValue(Object value) {
        mValue = value;
    }

    /**
     * The source location where the value was set.
     *
     * <p>This can be a layout file or from a style.
     */
    public Resource getSource() {
        return mSource;
    }

    public void setSource(Resource source) {
        mSource = source;
    }
}

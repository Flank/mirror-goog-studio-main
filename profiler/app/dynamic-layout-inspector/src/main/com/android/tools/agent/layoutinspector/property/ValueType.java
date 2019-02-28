package com.android.tools.agent.layoutinspector.property;

/**
 * The type of a property value.
 *
 * <p>This enum must be kept in sync with Property.Type in layout_inspector.proto.
 */
public enum ValueType {
    STRING,
    BOOLEAN,
    BYTE,
    CHAR,
    DOUBLE,
    FLOAT,
    INT16,
    INT32,
    INT64,
    OBJECT,
    COLOR,
    GRAVITY,
    INT_ENUM,
    INT_FLAG,
    RESOURCE,
}

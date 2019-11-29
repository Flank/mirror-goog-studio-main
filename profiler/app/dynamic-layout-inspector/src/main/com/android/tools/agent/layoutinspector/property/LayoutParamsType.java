package com.android.tools.agent.layoutinspector.property;

import android.view.ViewGroup.LayoutParams;
import android.view.inspector.InspectionCompanion;
import android.view.inspector.PropertyReader;
import java.util.List;

public class LayoutParamsType<L extends LayoutParams> {
    private String mNodeName;
    private String mJavaName;
    private List<PropertyType> mProperties;
    private List<InspectionCompanion> mInspectionCompanions;

    LayoutParamsType(
            String nodeName,
            String javaName,
            List<PropertyType> properties,
            List<InspectionCompanion> inspectionCompanions) {
        mNodeName = nodeName;
        mJavaName = javaName;
        mProperties = properties;
        mInspectionCompanions = inspectionCompanions;
    }

    public String getNodeName() {
        return mNodeName;
    }

    public String getJavaName() {
        return mJavaName;
    }

    List<InspectionCompanion> getInspectionCompanions() {
        return mInspectionCompanions;
    }

    List<PropertyType> getProperties() {
        return mProperties;
    }

    void readProperties(LayoutParams layoutParams, PropertyReader propertyReader) {
        for (InspectionCompanion companion : mInspectionCompanions) {
            //noinspection unchecked
            companion.readProperties(layoutParams, propertyReader);
        }
    }
}

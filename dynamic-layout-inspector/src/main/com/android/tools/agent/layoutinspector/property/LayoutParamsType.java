package com.android.tools.agent.layoutinspector.property;

import android.view.ViewGroup.LayoutParams;
import android.view.inspector.InspectionCompanion;
import android.view.inspector.PropertyReader;
import androidx.annotation.NonNull;
import java.util.List;

public class LayoutParamsType {
    private String mNodeName;
    private String mJavaName;
    private List<PropertyType> mProperties;
    private List<InspectionCompanion<LayoutParams>> mInspectionCompanions;

    LayoutParamsType(
            @NonNull String nodeName,
            @NonNull String javaName,
            @NonNull List<PropertyType> properties,
            @NonNull List<InspectionCompanion<LayoutParams>> inspectionCompanions) {
        mNodeName = nodeName;
        mJavaName = javaName;
        mProperties = properties;
        mInspectionCompanions = inspectionCompanions;
    }

    @NonNull
    @SuppressWarnings("unused")
    public String getNodeName() {
        return mNodeName;
    }

    @NonNull
    @SuppressWarnings("unused")
    public String getJavaName() {
        return mJavaName;
    }

    @NonNull
    List<InspectionCompanion<LayoutParams>> getInspectionCompanions() {
        return mInspectionCompanions;
    }

    @NonNull
    List<PropertyType> getProperties() {
        return mProperties;
    }

    void readProperties(
            @NonNull LayoutParams layoutParams, @NonNull PropertyReader propertyReader) {
        for (InspectionCompanion<LayoutParams> companion : mInspectionCompanions) {
            companion.readProperties(layoutParams, propertyReader);
        }
    }
}

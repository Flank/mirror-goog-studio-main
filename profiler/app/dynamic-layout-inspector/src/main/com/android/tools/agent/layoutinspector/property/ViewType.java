package com.android.tools.agent.layoutinspector.property;

import android.view.View;
import android.view.inspector.*;
import java.util.List;

/**
 * Holds information about a View.
 *
 * <p>The property types of a view is found via the {@link InspectionCompanion}s.
 */
public class ViewType<V extends View> {
    private String mNodeName;
    private String mJavaName;
    private List<PropertyType> mProperties;
    private List<InspectionCompanion> mInspectionCompanions;

    ViewType(
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

    ViewNode<V> newNode() {
        //noinspection unchecked
        return new ViewNode(this);
    }

    List<InspectionCompanion> getInspectionCompanions() {
        return mInspectionCompanions;
    }

    List<PropertyType> getProperties() {
        return mProperties;
    }

    void readProperties(V view, PropertyReader propertyReader) {
        for (InspectionCompanion companion : mInspectionCompanions) {
            //noinspection unchecked
            companion.readProperties(view, propertyReader);
        }
    }
}

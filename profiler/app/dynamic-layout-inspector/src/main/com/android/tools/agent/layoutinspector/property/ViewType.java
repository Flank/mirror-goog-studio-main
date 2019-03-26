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

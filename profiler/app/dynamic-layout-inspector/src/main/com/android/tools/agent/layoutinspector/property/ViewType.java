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
import android.view.inspector.InspectionCompanion;
import android.view.inspector.PropertyReader;
import androidx.annotation.NonNull;
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
    private List<InspectionCompanion<View>> mInspectionCompanions;

    ViewType(
            @NonNull String nodeName,
            @NonNull String javaName,
            @NonNull List<PropertyType> properties,
            @NonNull List<InspectionCompanion<View>> inspectionCompanions) {
        mNodeName = nodeName;
        mJavaName = javaName;
        mProperties = properties;
        mInspectionCompanions = inspectionCompanions;
    }

    @SuppressWarnings("unused")
    @NonNull
    public String getNodeName() {
        return mNodeName;
    }

    @SuppressWarnings("unused")
    @NonNull
    public String getJavaName() {
        return mJavaName;
    }

    @NonNull
    List<InspectionCompanion<View>> getInspectionCompanions() {
        return mInspectionCompanions;
    }

    @NonNull
    List<PropertyType> getProperties() {
        return mProperties;
    }

    void readProperties(@NonNull V view, @NonNull PropertyReader propertyReader) {
        for (InspectionCompanion<View> companion : mInspectionCompanions) {
            companion.readProperties(view, propertyReader);
        }
    }
}

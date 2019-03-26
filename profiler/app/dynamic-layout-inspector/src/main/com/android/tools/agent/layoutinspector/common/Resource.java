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

package com.android.tools.agent.layoutinspector.common;

import android.content.res.Resources;
import android.view.View;

/**
 * Holds a snapshot of a ResourceReference.
 *
 * <p>In Android a resource id is a simple integer. This class holds the namespace, type, and name
 * of such a resource id. e.g. "@android:id/textView2".
 */
public class Resource {
    private final String mType;
    private final String mNamespace;
    private final String mName;

    private Resource(String type, String namespace, String name) {
        mType = type;
        mNamespace = namespace;
        mName = name;
    }

    public static Resource fromResourceId(View view, int resourceId) {
        if (resourceId <= 0) {
            return null;
        }
        Resources resources = view.getResources();
        if (resources == null) {
            return null;
        }
        try {
            String type = resources.getResourceTypeName(resourceId);
            String namespace = resources.getResourcePackageName(resourceId);
            String name = resources.getResourceEntryName(resourceId);
            return new Resource(type.intern(), namespace.intern(), name.intern());
        } catch (Resources.NotFoundException ex) {
            return null;
        }
    }

    public String getType() {
        return mType;
    }

    public String getNamespace() {
        return mNamespace;
    }

    public String getName() {
        return mName;
    }

    @Override
    public String toString() {
        return String.format("resource:(@%s:%s/%s)", mNamespace, mType, mName);
    }

    public static String toString(Resource resource) {
        return resource != null ? resource.toString() : "";
    }
}

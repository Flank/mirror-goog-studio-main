/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.content.res;

import androidx.annotation.VisibleForTesting;
import java.util.Map;

public final class Resources {
    public static final class NotFoundException extends RuntimeException {}

    private final Map<Integer, String> mResourceNames;
    private final Configuration mConfiguration;

    /** @param resourceNames name format: "namespace.type/entry", e.g. "android.id/next_button" */
    @VisibleForTesting
    public Resources(Map<Integer, String> resourceNames) {
        mResourceNames = resourceNames;
        mConfiguration = new Configuration();
    }

    public String getResourceName(int resourceId) throws NotFoundException {
        if (!mResourceNames.containsKey(resourceId)) {
            throw new NotFoundException();
        }
        return mResourceNames.get(resourceId);
    }

    public String getResourceTypeName(int resourceId) throws NotFoundException {
        String name = getResourceName(resourceId);
        String typeName = name.substring(name.indexOf('.'));
        typeName = typeName.substring(0, name.indexOf('/'));
        return typeName;
    }

    public String getResourcePackageName(int resourceId) throws NotFoundException {
        String name = getResourceName(resourceId);
        return name.substring(0, name.indexOf('.'));
    }

    public String getResourceEntryName(int resourceId) throws NotFoundException {
        String name = getResourceName(resourceId);
        return name.substring(name.indexOf('/'));
    }

    public Configuration getConfiguration() {
        return mConfiguration;
    }
}

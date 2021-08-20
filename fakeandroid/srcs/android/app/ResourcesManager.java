/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.app;

import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.os.Build;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class ResourcesManager {
    private ArrayList<WeakReference<Resources>> mResourceReferences = new ArrayList<>();
    private Resources resources;

    // Exists on API 30 and below
    public void applyNewResourceDirsLocked(final ApplicationInfo appInfo, final String[] oldPaths) {
        if (Build.VERSION.SDK_INT > 30) {
            throw new RuntimeException("Method does not exist on API 31 and above");
        }
        mResourceReferences.clear();
        resources = new Resources();
        mResourceReferences.add(new WeakReference<>(resources));
    }

    // Exists on API 31 and above
    public void applyNewResourceDirs(final ApplicationInfo appInfo, final String[] oldPaths) {
        if (Build.VERSION.SDK_INT < 30) {
            throw new RuntimeException("Method does not exist on API 30 and below");
        }
        mResourceReferences.clear();
        resources = new Resources();
        mResourceReferences.add(new WeakReference<>(resources));
    }
}

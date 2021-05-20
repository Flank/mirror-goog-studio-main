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

package com.android.tools.deploy.instrument;

import static com.android.tools.deploy.instrument.ReflectionHelpers.*;

import android.content.res.Resources;
import android.content.res.loader.ResourcesLoader;
import android.content.res.loader.ResourcesProvider;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public final class ResourceOverlays {
    private static final String TAG = "studio.deploy";

    // The loader that holds all of the overlaid resources.
    private static ResourcesLoader resourcesLoader = null;

    public static void addResourceOverlays(Resources resources) throws Exception {
        updateLoader();
        resources.addLoaders(resourcesLoader);
    }

    public static void addResourceOverlays(Object resourcesManager) throws Exception {
        updateLoader();

        // Enumerate every Resources object that currently exists in the application. Add our
        // resources loader to each one.
        @SuppressWarnings("unchecked")
        ArrayList<WeakReference<Resources>> refs =
                (ArrayList<WeakReference<Resources>>)
                        getDeclaredField(resourcesManager, "mResourceReferences");
        for (WeakReference<Resources> ref : refs) {
            Resources resources = ref.get();
            if (resources == null) {
                continue;
            }
            resources.addLoaders(resourcesLoader);
        }
    }

    private static void updateLoader() throws Exception {
        if (resourcesLoader == null) {
            resourcesLoader = new ResourcesLoader();
        }

        Object activityThread = getActivityThread();
        Overlay overlay = new Overlay(getPackageName(activityThread));

        List<ResourcesProvider> providers = new ArrayList<>();
        for (File apkDir : overlay.getApkDirs()) {
            File resDir = new File(apkDir, "res");
            File arscFile = new File(apkDir, "resources.arsc");
            // Ensure there's actually resource content in the directory before creating a loader.
            // This is mostly important for when IWI run does not support resources but IWI swap
            // does.
            if (resDir.exists() || arscFile.exists()) {
                providers.add(ResourcesProvider.loadFromDirectory(apkDir.getAbsolutePath(), null));
            }
        }

        // This will update every AssetManager that currently references our loader.
        resourcesLoader.setProviders(providers);
    }

    private static Object getActivityThread() throws Exception {
        Class<?> clazz = Class.forName("android.app.ActivityThread");
        return call(clazz, "currentActivityThread");
    }

    private static String getPackageName(Object activityThread) throws Exception {
        return call(activityThread, "currentPackageName").toString();
    }
}

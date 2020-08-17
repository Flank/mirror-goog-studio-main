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
import java.io.File;
import java.lang.ref.Reference;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class ResourceOverlays {
    private static final String TAG = "studio.deploy";

    // ResourceLoaders are containers for supplying ResourcesProviders to Resources objects.
    // ResourcesLoaders are added to Resources objects to supply additional resources and assets or
    // modify the values of existing resources and assets.
    private static Class<?> resourcesLoaderClass =
            findClassOrNull("android.content.res.loader.ResourcesLoader");
    private static Class<?> resourcesProviderClass =
            findClassOrNull("android.content.res.loader.ResourcesProvider");
    private static Class<?> assetsProviderClass =
            findClassOrNull("android.content.res.loader.AssetsProvider");

    // The ResourcesLoader that holds all of the overlaid resources.
    private static Object resourcesLoader = null;

    public static void addResourceOverlays(Resources resources) throws Exception {
        updateLoader();
        Object[] loaderList = (Object[]) Array.newInstance(resourcesLoaderClass, 1);
        loaderList[0] = resourcesLoader;
        call(resources, "addLoaders", arg(loaderList));
    }

    public static void addResourceOverlays(Object resourcesManager) throws Exception {
        updateLoader();
        Object[] loaderList = (Object[]) Array.newInstance(resourcesLoaderClass, 1);
        loaderList[0] = resourcesLoader;

        // Enumerate every Resources object that currently exists in the application. Add our
        // resources loader to each one.
        Object resourcesRefs = getDeclaredField(resourcesManager, "mResourceReferences");
        for (Object ref : (Collection) resourcesRefs) {
            Object resources = call(ref, Reference.class, "get");
            if (resources == null) {
                continue;
            }
            call(resources, "addLoaders", arg(loaderList));
        }
    }

    private static void updateLoader() throws Exception {
        if (resourcesLoader == null) {
            resourcesLoader = resourcesLoaderClass.newInstance();
        }

        Object activityThread = getActivityThread();
        Overlay overlay = new Overlay(getPackageName(activityThread));

        List<Object> providers = new ArrayList<>();
        for (File apkDir : overlay.getApkDirs()) {
            Arg path = arg(apkDir.getAbsolutePath());
            Arg assets = arg(null, assetsProviderClass);
            providers.add(call(resourcesProviderClass, "loadFromDirectory", path, assets));
        }

        // This will update every AssetManager that currently references our loader.
        call(resourcesLoader, "setProviders", arg(providers, List.class));
    }

    private static Class<?> findClassOrNull(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Object getActivityThread() throws Exception {
        Class<?> clazz = Class.forName("android.app.ActivityThread");
        return call(clazz, "currentActivityThread");
    }

    private static String getPackageName(Object activityThread) throws Exception {
        return call(activityThread, "currentPackageName").toString();
    }
}

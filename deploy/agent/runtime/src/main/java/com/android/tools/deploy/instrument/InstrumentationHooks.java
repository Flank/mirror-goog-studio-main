/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

@SuppressWarnings("unused") // Used by native instrumentation code.
public final class InstrumentationHooks {
    // ApplicationThreadConstants.PACKAGE_REPLACED
    private static final int PACKAGE_REPLACED = 3;
    private static final String TAG = "studio.deploy";

    private static boolean mRestart;
    private static Object mActivityThread;
    private static boolean isPackageChanging;

    // Set of all previous installation locations of the package.
    private static final HashSet<Path> oldPackagePaths = new HashSet<>();

    // Current installation path of the running package. Written by
    // handleDispatchPackageBroadcastExit; read by handleFindResourceEntry.
    private static Path currentPackagePath;

    public static void setRestart(boolean restart) {
        mRestart = restart;
    }

    public static List<File> handleSplitDexPathExit(List<File> files) {
        try {
            Object activityThread = getActivityThread();

            String packageName = getPackageName(activityThread);
            Path packagePath = getPackagePath(activityThread);

            // If we've added native libraries, we need to remove them and
            // replace them with dex overlays. If we don't remove any files,
            // we need to additionally check if we need dex overlays.
            Overlay overlay = new Overlay(packageName);
            if (files.removeIf(prefixMatch(overlay.getOverlayRoot()))
                    || files.stream().anyMatch(prefixMatch(packagePath))) {
                files.addAll(0, overlay.getDexFiles());
            }

        } catch (Exception e) {
            Log.e(TAG, "Exception", e);
        }

        return files;
    }

    public static List<File> handleSplitPathsExit(List<File> files) {
        try {
            Object activityThread = getActivityThread();

            String packageName = getPackageName(activityThread);
            Path packagePath = getPackagePath(activityThread);

            // If any files in the list are in the package's installation directory,
            // include our overlay files in the list as well.
            if (files.stream().anyMatch(prefixMatch(packagePath))) {
                Overlay overlay = new Overlay(packageName);
                files.addAll(0, overlay.getNativeLibraryDirs());
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception", e);
        }

        return files;
    }

    private static Predicate<File> prefixMatch(Path prefix) {
        return file -> file.toPath().startsWith(prefix);
    }

    public static Resources addResourceOverlays(Resources resources) throws Exception {
        ResourceOverlays.addResourceOverlays(resources);
        return resources;
    }

    public static void addResourceOverlays(
            Object resourcesManager, ApplicationInfo appInfo, String[] oldPaths) throws Exception {
        ResourceOverlays.addResourceOverlays(resourcesManager);
    }

    // Instruments DexPathList$Element#findResource(). Checks to see if an Element object refers
    // to an old installation of this package, and modifies the element to point to the latest
    // installation path if so.
    public static void handleFindResourceEntry(Object element, String name) {
        try {
            File file = (File) getDeclaredField(element, "path");
            Path dir = file.getParentFile().toPath();

            // Need to ensure that a background thread isn't retrieving resources while a
            // --dont-kill installation is occurring.
            synchronized (oldPackagePaths) {
                // First check if this Element points to the current package path. If so, we don't
                // need to do anything. We check this first because currentPackagePath can end up in
                // the old paths set without the package actually having been moved.
                //
                // If currentPackagePath is null, no --dont-kill install has been triggered by Apply
                // Changes, so we don't need to do anything.
                if (currentPackagePath == null || currentPackagePath.equals(dir)) {
                    return;
                }

                // If the path pointed to by this Element is an old installation location, bring the
                // Element up to date and mark it as uninitialized. This will cause the classloader to
                // read the new path.
                if (oldPackagePaths.contains(dir)) {
                    File newFile = currentPackagePath.resolve(file.getName()).toFile();
                    setDeclaredField(element, "path", newFile);
                    setDeclaredField(element, "initialized", false);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Excepton", e);
        }
    }

    public static void handleDispatchPackageBroadcastEntry(
            Object activityThread, int cmd, String[] packages) {
        mActivityThread = activityThread;
        isPackageChanging = false;

        try {
            final String currentPackageName = getPackageName(activityThread);
            for (String pkg : packages) {
                if (pkg.equals(currentPackageName)) {
                    isPackageChanging = cmd == PACKAGE_REPLACED;
                    break;
                }
            }

            synchronized (oldPackagePaths) {
                oldPackagePaths.add(getPackagePath(mActivityThread));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in package installer patch", e);
        }
    }

    public static void handleDispatchPackageBroadcastExit() {
        if (!isPackageChanging) {
            return;
        }

        try {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                // Update the application and activity context objects to properly point to the new
                // LoadedApk that was created by the package update. We fix activity contexts even
                // if the activities wil be restarted, as those contexts may still be in use by app
                // code.
                Log.v(TAG, "Fixing application and activity contexts");
                Object newResourcesImpl = fixAppContext(mActivityThread);
                for (Object activity : getActivityClientRecords(mActivityThread)) {
                    fixActivityContext(activity, newResourcesImpl);
                }
            }

            if (mRestart) {
                updateApplicationInfo(mActivityThread);
            }

            synchronized (oldPackagePaths) {
                currentPackagePath = getPackagePath(mActivityThread);
            }
        } catch (Exception ex) {
            // The actual risks of the patch are unknown; although it seems to be safe, we're using some
            // defensive exception handling to prevent any application hard-crashes.
            Log.e(TAG, "Error in package installer patch", ex);
        } finally {
            mRestart = false;
            isPackageChanging = false;
        }
    }

    public static Object getActivityThread() throws Exception {
        Class<?> clazz = Class.forName("android.app.ActivityThread");
        return call(clazz, "currentActivityThread");
    }

    public static String getPackageName(Object activityThread) throws Exception {
        return call(activityThread, "currentPackageName").toString();
    }

    public static Path getPackagePath(Object activityThread) throws Exception {
        Object packageName = getPackageName(activityThread);
        Object loadedApk =
                call(activityThread, "peekPackageInfo", arg(packageName), arg(true, boolean.class));
        ApplicationInfo info = (ApplicationInfo) call(loadedApk, "getApplicationInfo");
        return Paths.get(info.sourceDir.substring(0, info.sourceDir.lastIndexOf("/")));
    }

    // ResourcesImpl fixAppContext(ActivityThread activityThread)
    public static native Object fixAppContext(Object activityThread);

    // Collection<ActivityClientRecord> getActivityClientRecords(ActivityThread activityThread)
    public static native Collection<? extends Object> getActivityClientRecords(
            Object activityThread);

    // void fixActivityContext(ActivityClientRecord activityRecord, ResourcesImpl newResourcesImpl)
    public static native void fixActivityContext(Object activityRecord, Object newResourcesImpl);

    // Wrapper around ActivityThread#handleUpdateApplicationInfo(ApplicationInfo)
    public static native void updateApplicationInfo(Object activityThread);

    public static native void logUnhandledException(Object thread, Throwable throwable);
}


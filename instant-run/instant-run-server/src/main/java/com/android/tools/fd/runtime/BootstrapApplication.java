/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.fd.runtime;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import android.app.ActivityManager;
import android.content.ComponentCallbacks;
import android.os.Process;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;

// This is based on the reflection parts of
//     com.google.devtools.build.android.incrementaldeployment.StubApplication,
// plus changes to compile on JDK 6.
//
// (The code to handle resource loading etc is different; see FileManager.)
//
// The original is
// https://cs.corp.google.com/codesearch/f/piper///depot/google3/third_party/bazel/src/tools/android/java/com/google/devtools/build/android/incrementaldeployment/StubApplication.java?cl=93287264
// Public (May 11 revision, ca96e11)
// https://github.com/google/bazel/blob/master/src/tools/android/java/com/google/devtools/build/android/incrementaldeployment/StubApplication.java

/**
 * A stub application that patches the class loader, then replaces itself with the real application
 * by applying a liberal amount of reflection on Android internals.
 * <p>
 * <p>This is, of course, terribly error-prone. Most of this code was tested with API versions
 * 8, 10, 14, 15, 16, 17, 18, 19 and 21 on the Android emulator, a Nexus 5 running Lollipop LRX22C
 * and a Samsung GT-I5800 running Froyo XWJPE. The exception is {@code monkeyPatchAssetManagers},
 * which only works on Kitkat and Lollipop.
 * <p>
 * <p>Note that due to a bug in Dalvik, this only works on Kitkat if ART is the Java runtime.
 * <p>
 * <p>Unfortunately, if this does not work, we don't have a fallback mechanism: as soon as we
 * build the APK with this class as the Application, we are committed to going through with it.
 * <p>
 * <p>This class should use as few other classes as possible before the class loader is patched
 * because any class loaded before it cannot be incrementally deployed.
 */
public class BootstrapApplication extends Application {
    public static final String LOG_TAG = "InstantRun";

    static {
        com.android.tools.fd.common.Log.logging =
                new com.android.tools.fd.common.Log.Logging() {
            @Override
            public void log(@NonNull Level level, @NonNull String string) {
                log(level, string, null /* throwable */);
            }

            @Override
            public boolean isLoggable(@NonNull Level level) {
                if (level == Level.SEVERE) {
                    return Log.isLoggable(LOG_TAG, Log.ERROR);
                } else if (level == Level.FINE) {
                    return Log.isLoggable(LOG_TAG, Log.VERBOSE);
                } else return Log.isLoggable(LOG_TAG, Log.INFO);
            }

            @Override
            public void log(@NonNull Level level, @NonNull String string,
                    @Nullable Throwable throwable) {
                if (level == Level.SEVERE) {
                    if (throwable == null) {
                        Log.e(LOG_TAG, string);
                    } else {
                        Log.e(LOG_TAG, string, throwable);
                    }
                } else if (level == Level.FINE) {
                    if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                        if (throwable == null) {
                            Log.v(LOG_TAG, string);
                        } else {
                            Log.v(LOG_TAG, string, throwable);
                        }
                    }
                } else if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                    if (throwable == null) {
                        Log.i(LOG_TAG, string);
                    } else {
                        Log.i(LOG_TAG, string, throwable);
                    }
                }
            }
        };
    }

    private String externalResourcePath;
    private Application realApplication;

    public BootstrapApplication() {
        // always log such that we can debug issues like http://b.android.com/215805
        Log.i(LOG_TAG, String.format(
                "Instant Run Runtime started. Android package is %s, real application class is %s.",
                AppInfo.applicationId, AppInfo.applicationClass));
    }

    private void createResources(long apkModified) {
        // Look for changes stashed in the inbox folder while the server was not running
        FileManager.checkInbox();

        File file = FileManager.getExternalResourceFile();
        externalResourcePath = file != null ? file.getPath() : null;

        if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
            Log.v(LOG_TAG, "Resource override is " + externalResourcePath);
        }

        if (file != null) {
            try {
                long resourceModified = file.lastModified();
                if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                    Log.v(LOG_TAG, "Resource patch last modified: " + resourceModified);
                    Log.v(LOG_TAG, "APK last modified: " + apkModified + " " +
                            (apkModified > resourceModified ? ">" : "<") + " resource patch");
                }

                if (apkModified == 0L || resourceModified <= apkModified) {
                    if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                        Log.v(LOG_TAG, "Ignoring resource file, older than APK");
                    }
                    externalResourcePath = null;
                }
            } catch (Throwable t) {
                Log.e(LOG_TAG, "Failed to check patch timestamps", t);
            }
        }
    }

    private static void setupClassLoaders(Context context, String codeCacheDir, long apkModified) {
        List<String> dexList = FileManager.getDexList(context, apkModified);

        // Make sure class loader finds these
        @SuppressWarnings("unused") Class<Server> server = Server.class;
        @SuppressWarnings("unused") Class<MonkeyPatcher> patcher = MonkeyPatcher.class;

        if (!dexList.isEmpty()) {
            if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                Log.v(LOG_TAG, "Bootstrapping class loader with dex list " + join('\n', dexList));
            }

            ClassLoader classLoader = BootstrapApplication.class.getClassLoader();
            String nativeLibraryPath;
            try {
                nativeLibraryPath = (String) classLoader.getClass().getMethod("getLdLibraryPath")
                                .invoke(classLoader);
                if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                    Log.v(LOG_TAG, "Native library path: " + nativeLibraryPath);
                }
            } catch (Throwable t) {
                Log.e(LOG_TAG, "Failed to determine native library path " + t.getMessage());
                nativeLibraryPath = FileManager.getNativeLibraryFolder().getPath();
            }
            IncrementalClassLoader.inject(
                    classLoader,
                    nativeLibraryPath,
                    codeCacheDir,
                    dexList);
        } else {
            Log.w(LOG_TAG, "No instant run dex files added to classpath");
        }
    }

    public static String join(char on, List<String> list) {
        StringBuilder stringBuilder = new StringBuilder();
        for (String item : list) {
            stringBuilder.append(item).append(on);
        }
        stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        return stringBuilder.toString();
    }

    private void createRealApplication() {
        if (AppInfo.applicationClass != null) {
            if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                Log.v(LOG_TAG, "About to create real application of class name = " +
                        AppInfo.applicationClass);
            }

            try {
                @SuppressWarnings("unchecked")
                Class<? extends Application> realClass =
                        (Class<? extends Application>) Class.forName(AppInfo.applicationClass);
                if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                    Log.v(LOG_TAG, "Created delegate app class successfully : " + realClass +
                            " with class loader " + realClass.getClassLoader());
                }
                Constructor<? extends Application> constructor = realClass.getConstructor();
                realApplication = constructor.newInstance();
                if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                    Log.v(LOG_TAG, "Created real app instance successfully :" + realApplication);
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        } else {
            realApplication = new Application();
        }
    }

    @Override
    protected void attachBaseContext(Context context) {
        // As of Marshmallow, we use APK splits and don't need to rely on
        // reflection to inject classes and resources for coldswap
        //noinspection PointlessBooleanExpression
        if (!AppInfo.usingApkSplits) {
            String apkFile = context.getApplicationInfo().sourceDir;
            long apkModified = apkFile != null ? new File(apkFile).lastModified() : 0L;
            createResources(apkModified);
            setupClassLoaders(context, context.getCacheDir().getPath(), apkModified);
        }

        createRealApplication();

        // This is called from ActivityThread#handleBindApplication() -> LoadedApk#makeApplication().
        // Application#mApplication is changed right after this call, so we cannot do the monkey
        // patching here. So just forward this method to the real Application instance.
        super.attachBaseContext(context);

        if (realApplication != null) {
            try {
                Method attachBaseContext =
                        ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
                attachBaseContext.setAccessible(true);
                attachBaseContext.invoke(realApplication, context);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Override
    public Context createPackageContext(String packageName, int flags)
            throws PackageManager.NameNotFoundException {
        Context c = realApplication.createPackageContext(packageName, flags);
        return c == null ? realApplication : c;
    }

    @Override
    public void registerComponentCallbacks(ComponentCallbacks callback) {
        realApplication.registerComponentCallbacks(callback);
    }

    @Override
    public void registerActivityLifecycleCallbacks(
            ActivityLifecycleCallbacks callback) {
        realApplication.registerActivityLifecycleCallbacks(callback);
    }

    @Override
    public void registerOnProvideAssistDataListener(
            OnProvideAssistDataListener callback) {
        realApplication.registerOnProvideAssistDataListener(callback);
    }

    @Override
    public void unregisterComponentCallbacks(ComponentCallbacks callback) {
        realApplication.unregisterComponentCallbacks(callback);
    }

    @Override
    public void unregisterActivityLifecycleCallbacks(
            ActivityLifecycleCallbacks callback) {
        realApplication.unregisterActivityLifecycleCallbacks(callback);
    }

    @Override
    public void unregisterOnProvideAssistDataListener(
            OnProvideAssistDataListener callback) {
        realApplication.unregisterOnProvideAssistDataListener(callback);
    }

    @Override
    public void onCreate() {
        // As of Marshmallow, we use APK splits and don't need to rely on
        // reflection to inject classes and resources for coldswap
        //noinspection PointlessBooleanExpression
        if (!AppInfo.usingApkSplits) {
            MonkeyPatcher.monkeyPatchApplication(
                    BootstrapApplication.this, BootstrapApplication.this,
                    realApplication, externalResourcePath);
            MonkeyPatcher.monkeyPatchExistingResources(BootstrapApplication.this,
                    externalResourcePath, null);
        } else {
            // We still need to set the application instance in the LoadedApk etc
            // such that getApplication() returns the new application
            MonkeyPatcher.monkeyPatchApplication(
                    BootstrapApplication.this, BootstrapApplication.this,
                    realApplication, null);
        }
        super.onCreate();

        // Start server, unless we're in a multiprocess scenario and this isn't the
        // primary process
        if (AppInfo.applicationId != null) {
            try {
                boolean foundPackage = false;
                int pid = Process.myPid();
                ActivityManager manager = (ActivityManager) getSystemService(
                        Context.ACTIVITY_SERVICE);
                List<RunningAppProcessInfo> processes = manager.getRunningAppProcesses();

                boolean startServer;
                if (processes != null && processes.size() > 1) {
                    // Multiple processes: look at each, and if the process name matches
                    // the package name (for the current pid), it's the main process.
                    startServer = false;
                    for (RunningAppProcessInfo processInfo : processes) {
                        if (AppInfo.applicationId.equals(processInfo.processName)) {
                            foundPackage = true;
                            if (processInfo.pid == pid) {
                                startServer = true;
                                break;
                            }
                        }
                    }
                    if (!startServer && !foundPackage) {
                        // Safety check: If for some reason we didn't even find the main package,
                        // start the server anyway. This safeguards against apps doing strange
                        // things with the process name.
                        startServer = true;
                        if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                            Log.v(LOG_TAG, "Multiprocess but didn't find process with package: "
                                    + "starting server anyway");
                        }
                    }
                } else {
                    // If there is only one process, start the server.
                    startServer = true;
                }

                if (startServer) {
                    Server.create(AppInfo.applicationId, BootstrapApplication.this);
                }
            } catch (Throwable t) {
                if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                    Log.v(LOG_TAG, "Failed during multi process check", t);
                }
                Server.create(AppInfo.applicationId, BootstrapApplication.this);
            }
        }

        if (realApplication != null) {
            realApplication.onCreate();
        }
    }
}

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

package com.android.tools.agent.app.inspection;

import static com.android.tools.agent.app.inspection.InspectorContext.CrashListener;
import static com.android.tools.agent.app.inspection.NativeTransport.*;

import androidx.inspection.ArtTooling;
import androidx.inspection.ArtTooling.EntryHook;
import androidx.inspection.ArtTooling.ExitHook;
import com.android.tools.agent.app.inspection.version.VersionChecker;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** This service controls all app inspectors */
// Suppress Convert2Lambda: Lambdas may incur penalty hit on older Android devices
// Suppress unused: Methods invoked via jni
// Suppress rawtypes: Service doesn't care about specific types, works with Objects
@SuppressWarnings({"Convert2Lambda", "unused", "rawtypes"})
public class AppInspectionService {
    private static AppInspectionService sInstance;

    public static AppInspectionService instance() {
        if (sInstance == null) {
            sInstance = createAppInspectionService();
        }
        return sInstance;
    }

    // will be passed to jni method to call methods on the instance
    @SuppressWarnings("FieldCanBeLocal")
    // currently AppInspectionService is singleton and it is never destroyed, so we don't clean this reference.
    private final long mNativePtr;

    private final Map<String, InspectorBridge> mInspectorBridges = new ConcurrentHashMap<>();

    private final Map<String, List<HookInfo<ExitHook>>> mExitTransforms = new ConcurrentHashMap<>();
    private final Map<String, List<HookInfo<EntryHook>>> mEntryTransforms =
            new ConcurrentHashMap<>();

    private static final String INSPECTOR_ID_MISSING_ERROR =
            "Argument inspectorId must not be null";

    // TODO: b/159250979
    // Special work around to support overloads and exit hooks
    // currently our labels (keys in mExitTransforms)
    // lose important information about parameters of methods.
    // This set stores full information about instrumented methods
    private final Set<String> mExitTransformsFullLabels =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private CrashListener mCrashListener =
            new CrashListener() {
                @Override
                public void onInspectorCrashed(String inspectorId, String message) {
                    sendCrashEvent(inspectorId, message);
                    doDispose(inspectorId);
                }
            };

    /**
     * Construct an instance referencing some native (JVMTI) resources.
     *
     * <p>A user shouldn't call this directly - instead, call {@link #instance()}, which delegates
     * work to JNI which ultimately calls this constructor.
     */
    AppInspectionService(long nativePtr) {
        mNativePtr = nativePtr;
    }

    /**
     * Creates and launches an inspector on device.
     *
     * <p>This will respond with error when an inspector with the same ID already exists, when the
     * dex cannot be located, and when an exception is encountered while loading classes.
     *
     * @param inspectorId the unique id of the inspector being launched
     * @param dexPath the path to the .dex file of the inspector
     * @param projectName the name of the studio project that is trying to launch the inspector
     * @param versionFileName the full name of the version file located in the APK's META-INF. Null
     *     if inspector is not targeting any particular library (ex: DB inspector).
     * @param minVersion the minimum version of the library this inspector is compatible with. Null
     *     if inspector is not targeting any particular library (ex: DB inspector).
     * @param force if true, create the inspector even if one is already running
     * @param commandId unique id of this command in the context of app inspection service
     */
    public void createInspector(
            String inspectorId,
            String dexPath,
            String versionFileName,
            String minVersion,
            String projectName,
            boolean force,
            int commandId) {
        if (inspectorId == null) {
            sendCreateInspectorResponseError(commandId, INSPECTOR_ID_MISSING_ERROR);
            return;
        }
        if (mInspectorBridges.containsKey(inspectorId)) {
            if (!force) {
                String alreadyLaunchedProjectName = mInspectorBridges.get(inspectorId).getProject();
                sendCreateInspectorResponseError(
                        commandId,
                        "Inspector with the given id "
                                + inspectorId
                                + " already exists. It was launched by project: "
                                + alreadyLaunchedProjectName
                                + "\n\nThis could happen if you launched the same inspector from two different projects at the same time, or if a previous run of the current project crashed unexpectedly and didn't shut down properly.");
                return;
            }

            doDispose(inspectorId);
        }

        VersionTargetInfo versionTarget = null;
        if (minVersion != null && versionFileName != null) {
            versionTarget = new VersionTargetInfo(versionFileName, minVersion);
        }
        if (!doCheckVersion(commandId, versionTarget)) {
            return;
        }

        if (!new File(dexPath).exists()) {
            sendCreateInspectorResponseError(
                    commandId, "Failed to find a file with path: " + dexPath);
            return;
        }

        InspectorBridge bridge = InspectorBridge.create(inspectorId, projectName, mCrashListener);
        mInspectorBridges.put(inspectorId, bridge);
        bridge.initializeInspector(
                dexPath,
                mNativePtr,
                (error) -> {
                    if (error != null) {
                        mInspectorBridges.remove(inspectorId);
                        sendCreateInspectorResponseError(commandId, error);
                    } else {
                        sendCreateInspectorResponseSuccess(commandId);
                    }
                });
    }

    public void disposeInspector(String inspectorId, int commandId) {
        if (inspectorId == null) {
            sendDisposeInspectorResponseError(commandId, INSPECTOR_ID_MISSING_ERROR);
            return;
        }
        if (!mInspectorBridges.containsKey(inspectorId)) {
            sendDisposeInspectorResponseError(
                    commandId, "Inspector with id " + inspectorId + " wasn't previously created");
            return;
        }
        doDispose(inspectorId);
        sendDisposeInspectorResponseSuccess(commandId);
    }

    public void sendCommand(String inspectorId, int commandId, byte[] rawCommand) {
        if (inspectorId == null) {
            sendRawResponseError(commandId, INSPECTOR_ID_MISSING_ERROR);
            return;
        }
        InspectorBridge bridge = mInspectorBridges.get(inspectorId);
        if (bridge == null) {
            sendRawResponseError(
                    commandId, "Inspector with id " + inspectorId + " wasn't previously created");
            return;
        }
        bridge.sendCommand(commandId, rawCommand);
    }

    public void cancelCommand(int cancelledCommandId) {
        // broadcast cancellation to every inspector even if only one of handled this command
        for (InspectorBridge bridge : mInspectorBridges.values()) {
            bridge.cancelCommand(cancelledCommandId);
        }
    }

    private void doDispose(String inspectorId) {
        removeHooks(inspectorId, mEntryTransforms);
        removeHooks(inspectorId, mExitTransforms);
        InspectorBridge inspector = mInspectorBridges.remove(inspectorId);
        if (inspector != null) {
            inspector.disposeInspector();
        }
    }

    /**
     * Checks whether the inspector we are trying to create is compatible with the library.
     *
     * <p>This will compare the provided minVersion with the version string located in the version
     * file in the APK's META-INF directory.
     *
     * <p>In the case the provided version targeting information is null, return true because the
     * inspector is targeting the Android framework.
     *
     * <p>Note, this method will send the appropriate response to the command if the check failed in
     * any way. In other words, callers don't need to send a response if this method returns false.
     *
     * @param commandId the id of the command
     * @param versionTarget contains the version compatibility and location of the library. Null if
     *     inspector does not target any particular library.
     * @return true if check passed. false if check failed for any reason.
     */
    private boolean doCheckVersion(int commandId, VersionTargetInfo versionTarget) {
        if (versionTarget == null) {
            return true;
        }
        VersionChecker.Result versionResult =
                VersionChecker.checkVersion(
                        versionTarget.versionFileName, versionTarget.minVersion);
        if (versionResult.status == VersionChecker.Result.Status.INCOMPATIBLE) {
            sendCreateInspectorResponseVersionIncompatible(commandId, versionResult.message);
            return false;
        } else if (versionResult.status == VersionChecker.Result.Status.NOT_FOUND) {
            sendCreateInspectorResponseLibraryMissing(commandId, versionResult.message);
            return false;
        } else if (versionResult.status == VersionChecker.Result.Status.ERROR) {
            sendCreateInspectorResponseError(commandId, versionResult.message);
            return false;
        }
        return true;
    }

    private static String createLabel(Class origin, String method) {
        if (method.indexOf('(') == -1) {
            return "";
        }
        return origin.getCanonicalName() + method.substring(0, method.indexOf('('));
    }

    private static String createFullLabel(Class origin, String method) {
        return origin.getCanonicalName() + method;
    }

    public static void addEntryHook(
            String inspectorId, Class origin, String method, EntryHook hook) {
        List<HookInfo<EntryHook>> hooks =
                sInstance.mEntryTransforms.computeIfAbsent(
                        createLabel(origin, method),
                        new Function<String, List<HookInfo<EntryHook>>>() {

                            @Override
                            public List<HookInfo<EntryHook>> apply(String key) {
                                nativeRegisterEntryHook(sInstance.mNativePtr, origin, method);
                                return new CopyOnWriteArrayList<>();
                            }
                        });
        hooks.add(new HookInfo<>(inspectorId, hook));
    }

    public static void addExitHook(
            String inspectorId, Class origin, String method, ArtTooling.ExitHook<?> hook) {
        if (sInstance.mExitTransformsFullLabels.add(createFullLabel(origin, method))) {
            nativeRegisterExitHook(sInstance.mNativePtr, origin, method);
        }

        List<HookInfo<ExitHook>> hooks =
                sInstance.mExitTransforms.computeIfAbsent(
                        createLabel(origin, method),
                        new Function<String, List<HookInfo<ExitHook>>>() {

                            @Override
                            public List<HookInfo<ExitHook>> apply(String key) {
                                return new CopyOnWriteArrayList<>();
                            }
                        });
        hooks.add(new HookInfo<>(inspectorId, hook));
    }

    private static <T> T onExitInternal(T returnObject) {
        Error error = new Error();
        error.fillInStackTrace();
        StackTraceElement[] stackTrace = error.getStackTrace();
        if (stackTrace.length < 3) {
            return returnObject;
        }
        StackTraceElement element = stackTrace[2];
        String label = element.getClassName() + element.getMethodName();

        AppInspectionService instance = AppInspectionService.instance();
        List<HookInfo<ExitHook>> hooks = instance.mExitTransforms.get(label);
        if (hooks == null) {
            return returnObject;
        }

        // TODO: b/159250979, we currently do this deduplication, because
        // hooks for different methods end up at the same place
        // to avoid calling the same hook twice we add them to a set
        Set<ExitHook> calledHooks = new HashSet<>();
        for (HookInfo<ExitHook> info : hooks) {
            //noinspection unchecked
            if (calledHooks.add(info.hook)) {
                returnObject = (T) info.hook.onExit(returnObject);
            }
        }
        return returnObject;
    }

    public static Object onExit(Object returnObject) {
        return onExitInternal(returnObject);
    }

    public static void onExit() {
        onExitInternal(null);
    }

    public static boolean onExit(boolean result) {
        return onExitInternal(result);
    }

    public static byte onExit(byte result) {
        return onExitInternal(result);
    }

    public static char onExit(char result) {
        return onExitInternal(result);
    }

    public static short onExit(short result) {
        return onExitInternal(result);
    }

    public static int onExit(int result) {
        return onExitInternal(result);
    }

    public static float onExit(float result) {
        return onExitInternal(result);
    }

    public static long onExit(long result) {
        return onExitInternal(result);
    }

    public static double onExit(double result) {
        return onExitInternal(result);
    }

    /**
     * Receives an array where the first parameter is the "this" reference and all remaining
     * arguments are the function's parameters.
     *
     * <p>For example, the function {@code Client#sendMessage(Receiver r, String message)} will
     * receive the array: [this, r, message]
     */
    public static void onEntry(Object[] thisAndParams) {
        assert (thisAndParams.length >= 1); // Should always at least contain "this"
        Error error = new Error();
        error.fillInStackTrace();
        StackTraceElement[] stackTrace = error.getStackTrace();
        if (stackTrace.length < 2) {
            return;
        }
        StackTraceElement element = stackTrace[1];
        String label = element.getClassName() + element.getMethodName();
        List<HookInfo<EntryHook>> hooks =
                AppInspectionService.instance().mEntryTransforms.get(label);

        if (hooks == null) {
            return;
        }

        Object thisObject = thisAndParams[0];
        List<Object> params = Collections.emptyList();
        if (thisAndParams.length > 1) {
            params = Arrays.asList(Arrays.copyOfRange(thisAndParams, 1, thisAndParams.length));
        }

        for (HookInfo<EntryHook> info : hooks) {
            info.hook.onEntry(thisObject, params);
        }
    }

    private static final class HookInfo<T> {
        private final String inspectorId;
        private final T hook;

        HookInfo(String inspectorId, T hook) {
            this.inspectorId = inspectorId;
            this.hook = hook;
        }
    }

    private static void removeHooks(
            String inspectorId, Map<String, ? extends List<? extends HookInfo<?>>> hooks) {
        for (List<? extends HookInfo<?>> list : hooks.values()) {
            for (HookInfo<?> info : list) {
                if (info.inspectorId.equals(inspectorId)) {
                    list.remove(info);
                }
            }
        }
    }

    private static native AppInspectionService createAppInspectionService();

    private static native void nativeRegisterEntryHook(
            long servicePtr, Class<?> originClass, String originMethod);

    private static native void nativeRegisterExitHook(
            long servicePtr, Class<?> originClass, String originMethod);

    private static class VersionTargetInfo {
        String versionFileName;
        String minVersion;

        public VersionTargetInfo(String versionFileName, String minVersion) {
            this.versionFileName = versionFileName;
            this.minVersion = minVersion;
        }
    }
}

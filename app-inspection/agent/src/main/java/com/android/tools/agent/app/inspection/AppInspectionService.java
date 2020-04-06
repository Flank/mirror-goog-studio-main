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

import static com.android.tools.agent.app.inspection.NativeTransport.sendCrashEvent;
import static com.android.tools.agent.app.inspection.NativeTransport.sendServiceResponseError;
import static com.android.tools.agent.app.inspection.NativeTransport.sendServiceResponseSuccess;

import android.util.Pair;
import androidx.inspection.Inspector;
import androidx.inspection.InspectorEnvironment;
import androidx.inspection.InspectorFactory;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/** This service controls all app inspectors */
@SuppressWarnings("unused") // invoked via jni
public class AppInspectionService {
    private static final String MAIN_THREAD_NAME = "main";

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

    private final Map<String, InspectorContext> mInspectorContexts =
            new HashMap<String, InspectorContext>();

    // TODO: save inspector and clean up transformation when inspectors are gone
    Map<String, InspectorEnvironment.ExitHook> mExitTransforms =
            new HashMap<String, InspectorEnvironment.ExitHook>();
    Map<String, InspectorEnvironment.EntryHook> mEntryTransforms =
            new HashMap<String, InspectorEnvironment.EntryHook>();

    // it keeps reference only to pending commands.
    private ConcurrentHashMap<Integer, CommandCallbackImpl> mIdToCommandCallback =
            new ConcurrentHashMap<Integer, CommandCallbackImpl>();

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
     * @param commandId unique id of this command in the context of app inspection service
     */
    @SuppressWarnings("unused") // invoked via jni
    public void createInspector(
            String inspectorId, String dexPath, String projectName, int commandId) {
        if (failNull("inspectorId", inspectorId, commandId)) {
            return;
        }
        if (mInspectorContexts.containsKey(inspectorId)) {
            String alreadyLaunchedProjectName = mInspectorContexts.get(inspectorId).getProject();
            sendServiceResponseError(
                    commandId,
                    "Inspector with the given id "
                            + inspectorId
                            + " already exists. It was launched by project: "
                            + alreadyLaunchedProjectName);
            return;
        }
        ClassLoader mainClassLoader = mainThreadClassLoader();
        if (mainClassLoader == null) {
            sendServiceResponseError(commandId, "Failed to find a main thread");
            return;
        }
        if (!new File(dexPath).exists()) {
            sendServiceResponseError(commandId, "Failed to find a file with path: " + dexPath);
            return;
        }

        String optimizedDir = System.getProperty("java.io.tmpdir");

        try {
            ClassLoader classLoader =
                    new DexClassLoader(dexPath, optimizedDir, null, mainClassLoader);
            ServiceLoader<InspectorFactory> loader =
                    ServiceLoader.load(InspectorFactory.class, classLoader);
            Iterator<InspectorFactory> iterator = loader.iterator();
            Inspector inspector = null;
            while (iterator.hasNext()) {
                InspectorFactory inspectorFactory = iterator.next();
                if (inspectorId.equals(inspectorFactory.getInspectorId())) {
                    ConnectionImpl connection = new ConnectionImpl(inspectorId);
                    InspectorEnvironment environment = new InspectorEnvironmentImpl(mNativePtr);
                    inspector = inspectorFactory.createInspector(connection, environment);
                    mInspectorContexts.put(
                            inspectorId, new InspectorContext(inspector, projectName));
                    break;
                }
            }
            if (inspector == null) {
                sendServiceResponseError(
                        commandId, "Failed to find InspectorFactory with id " + inspectorId);
                return;
            }
            sendServiceResponseSuccess(commandId);
        } catch (Throwable e) {
            e.printStackTrace();
            sendServiceResponseError(
                    commandId, "Failed during instantiating inspector with id " + inspectorId);
        }
    }

    @SuppressWarnings("unused") // invoked via jni
    public void disposeInspector(String inspectorId, int commandId) {
        if (failNull("inspectorId", inspectorId, commandId)) {
            return;
        }
        if (!mInspectorContexts.containsKey(inspectorId)) {
            sendServiceResponseError(
                    commandId, "Inspector with id " + inspectorId + " wasn't previously created");
            return;
        }
        doDispose(inspectorId);
        sendServiceResponseSuccess(commandId);
    }

    @SuppressWarnings("unused") // invoked via jni
    public void sendCommand(String inspectorId, int commandId, byte[] rawCommand) {
        if (failNull("inspectorId", inspectorId, commandId)) {
            return;
        }
        Inspector inspector = mInspectorContexts.get(inspectorId).getInspector();
        if (inspector == null) {
            sendServiceResponseError(
                    commandId, "Inspector with id " + inspectorId + " wasn't previously created");
            return;
        }
        try {
            CommandCallbackImpl callback = new CommandCallbackImpl(commandId);
            mIdToCommandCallback.put(commandId, callback);
            inspector.onReceiveCommand(rawCommand, callback);
        } catch (Throwable t) {
            t.printStackTrace();
            sendCrashEvent(
                    inspectorId,
                    "Inspector "
                            + inspectorId
                            + " crashed during sendCommand due to: "
                            + t.getMessage());
            doDispose(inspectorId);
        }
    }

    @SuppressWarnings("unused") // invoked via jni
    public void cancelCommand(int cancelledCommandId) {
        CommandCallbackImpl callback = mIdToCommandCallback.get(cancelledCommandId);
        if (callback != null) {
            callback.cancelCommand();
        }
    }

    private void doDispose(String inspectorId) {
        Inspector inspector = mInspectorContexts.remove(inspectorId).getInspector();
        if (inspector != null) {
            try {
                inspector.onDispose();
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean failNull(String name, Object value, int commandId) {
        boolean result = value == null;
        if (result) {
            sendServiceResponseError(commandId, "Argument " + name + " must not be null");
        }
        return result;
    }

    enum Status {
        PENDING,
        REPLIED,
        CANCELLED
    }

    class CommandCallbackImpl implements Inspector.CommandCallback {
        private final Object mLock = new Object();
        private volatile Status mStatus = Status.PENDING;
        private final int mCommandId;
        private final List<Pair<Executor, Runnable>> mCancellationListeners =
                new ArrayList<Pair<Executor, Runnable>>();

        CommandCallbackImpl(int commandId) {
            mCommandId = commandId;
        }

        @Override
        public void reply(byte[] bytes) {
            synchronized (mLock) {
                if (mStatus == Status.PENDING) {
                    mStatus = Status.REPLIED;
                    mIdToCommandCallback.remove(mCommandId);
                    NativeTransport.sendRawResponseSuccess(mCommandId, bytes, bytes.length);
                }
            }
        }

        @Override
        public void addCancellationListener(Executor executor, Runnable runnable) {
            synchronized (mLock) {
                if (mStatus == Status.CANCELLED) {
                    executor.execute(runnable);
                } else {
                    mCancellationListeners.add(new Pair<Executor, Runnable>(executor, runnable));
                }
            }
        }

        void cancelCommand() {
            List<Pair<Executor, Runnable>> listeners = null;
            synchronized (mLock) {
                if (mStatus == Status.PENDING) {
                    mStatus = Status.CANCELLED;
                    mIdToCommandCallback.remove(mCommandId);
                    listeners = new ArrayList<Pair<Executor, Runnable>>(mCancellationListeners);
                }
            }
            if (listeners != null) {
                for (Pair<Executor, Runnable> p : listeners) {
                    p.first.execute(p.second);
                }
            }
        }
    }

    /**
     * Iterates through threads presented in the app and looks for a thread with name "main". It can
     * return {@code null} in case if thread with a name "main" is missing.
     */
    private static ClassLoader mainThreadClassLoader() {
        ThreadGroup group = Thread.currentThread().getThreadGroup();

        while (group.getParent() != null) {
            group = group.getParent();
        }

        Thread[] threads = new Thread[100];
        group.enumerate(threads);
        for (Thread thread : threads) {
            if (thread != null && thread.getName().equals(MAIN_THREAD_NAME)) {
                return thread.getContextClassLoader();
            }
        }
        return null;
    }

    private static String createLabel(Class origin, String method) {
        if (method.indexOf('(') == -1) {
            return "";
        }
        return origin.getCanonicalName() + method.substring(0, method.indexOf('('));
    }

    public static void addEntryHook(
            Class origin, String method, InspectorEnvironment.EntryHook hook) {
        sInstance.mEntryTransforms.put(createLabel(origin, method), hook);
    }

    public static void addExitHook(
            Class origin, String method, InspectorEnvironment.ExitHook<?> hook) {
        sInstance.mExitTransforms.put(createLabel(origin, method), hook);
    }

    private static Object onExitInternal(Object returnObject) {
        Error error = new Error();
        error.fillInStackTrace();
        StackTraceElement[] stackTrace = error.getStackTrace();
        if (stackTrace.length < 3) {
            return returnObject;
        }
        StackTraceElement element = stackTrace[2];
        String label = element.getClassName() + element.getMethodName();

        AppInspectionService instance = AppInspectionService.instance();
        InspectorEnvironment.ExitHook hook = instance.mExitTransforms.get(label);
        if (hook != null) {
            hook.onExit(returnObject);
        }
        return returnObject;
    }

    public static Object onExit(Object returnObject) {
        return onExitInternal(returnObject);
    }

    public static void onExit() {
        onExitInternal(null);
    }

    public static int onExit(int result) {
        return ((Integer) onExitInternal(result)).intValue();
    }

    public static boolean onExit(boolean result) {
        return ((Boolean) onExitInternal(result)).booleanValue();
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
        InspectorEnvironment.EntryHook hook =
                AppInspectionService.instance().mEntryTransforms.get(label);
        if (hook != null) {
            Object thisObject = thisAndParams[0];
            List<Object> params = Collections.emptyList();
            if (thisAndParams.length > 1) {
                params = Arrays.asList(Arrays.copyOfRange(thisAndParams, 1, thisAndParams.length));
            }
            hook.onEntry(thisObject, params);
        }
    }

    private static native AppInspectionService createAppInspectionService();
}

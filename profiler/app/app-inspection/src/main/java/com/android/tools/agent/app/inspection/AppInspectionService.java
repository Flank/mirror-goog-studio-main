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

import static com.android.tools.agent.app.inspection.Responses.replyCrash;
import static com.android.tools.agent.app.inspection.Responses.replyError;
import static com.android.tools.agent.app.inspection.Responses.replySuccess;

import androidx.inspection.Inspector;
import androidx.inspection.InspectorEnvironment;
import androidx.inspection.InspectorFactory;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;

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
    private Map<String, Inspector> mInspectors = new HashMap<String, Inspector>();

    /**
     * Construct an instance referencing some native (JVMTI) resources.
     *
     * <p>A user shouldn't call this directly - instead, call {@link #instance()}, which delegates
     * work to JNI which ultimately calls this constructor.
     */
    AppInspectionService(long nativePtr) {
        mNativePtr = nativePtr;
    }

    @SuppressWarnings("unused") // invoked via jni
    public void createInspector(String inspectorId, String dexPath, int commandId) {
        if (failNull("inspectorId", inspectorId, commandId)) {
            return;
        }
        if (mInspectors.containsKey(inspectorId)) {
            replyError(commandId, "Inspector with the given id " + inspectorId + " already exists");
            return;
        }
        ClassLoader mainClassLoader = mainThreadClassLoader();
        if (mainClassLoader == null) {
            replyError(commandId, "Failed to find a main thread");
            return;
        }
        if (!new File(dexPath).exists()) {
            replyError(commandId, "Failed to find a file with path: " + dexPath);
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
                    mInspectors.put(inspectorId, inspector);
                    break;
                }
            }
            if (inspector == null) {
                replyError(commandId, "Failed to find InspectorFactory with id " + inspectorId);
                return;
            }
            replySuccess(commandId);
        } catch (Throwable e) {
            e.printStackTrace();
            replyError(commandId, "Failed during instantiating inspector with id " + inspectorId);
        }
    }

    @SuppressWarnings("unused") // invoked via jni
    public void disposeInspector(String inspectorId, int commandId) {
        if (failNull("inspectorId", inspectorId, commandId)) {
            return;
        }
        if (!mInspectors.containsKey(inspectorId)) {
            replyError(
                    commandId, "Inspector with id " + inspectorId + " wasn't previously created");
            return;
        }
        doDispose(inspectorId);
        replySuccess(commandId);
    }

    @SuppressWarnings("unused") // invoked via jni
    public void sendCommand(String inspectorId, int commandId, byte[] rawCommand) {
        if (failNull("inspectorId", inspectorId, commandId)) {
            return;
        }
        Inspector inspector = mInspectors.get(inspectorId);
        if (inspector == null) {
            replyError(
                    commandId, "Inspector with id " + inspectorId + " wasn't previously created");
        }
        try {
            inspector.onReceiveCommand(rawCommand, new CommandCallbackImpl(inspectorId, commandId));
        } catch (Throwable t) {
            t.printStackTrace();
            replyCrash(
                    commandId,
                    inspectorId,
                    "Inspector "
                            + inspectorId
                            + " crashed during sendCommand due to "
                            + t.getMessage());
            doDispose(inspectorId);
        }
    }

    private void doDispose(String inspectorId) {
        Inspector inspector = mInspectors.remove(inspectorId);
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
            replyError(commandId, "Argument " + name + " must not be null");
        }
        return result;
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

    private static native AppInspectionService createAppInspectionService();

    /// ==================================== EVERYTHING below should be called only when app_inspection_experimental=true
    private ExperimentalCapabilities mExperimental = new ExperimentalCapabilities();

    static class ExperimentalCapabilities {
        // TODO: save inspector and clean up transformation when inspectors are gone
        Map<String, InspectorEnvironment.ExitHook> mExitTransforms =
                new HashMap<String, InspectorEnvironment.ExitHook>();
        Map<String, InspectorEnvironment.EntryHook> mEntryTransforms =
                new HashMap<String, InspectorEnvironment.EntryHook>();

        private static String createLabel(Class origin, String method) {
            if (method.indexOf('(') == -1) {
                return "";
            }
            return origin.getCanonicalName() + method.substring(0, method.indexOf('('));
        }

        static void addEntryHook(Class origin, String method, InspectorEnvironment.EntryHook hook) {
            sInstance.mExperimental.mEntryTransforms.put(createLabel(origin, method), hook);
        }

        static void addExitHook(
                Class origin, String method, InspectorEnvironment.ExitHook<?> hook) {
            sInstance.mExperimental.mExitTransforms.put(createLabel(origin, method), hook);
        }

        public static void onExit(Object returnObject) {
            Error error = new Error();
            error.fillInStackTrace();
            StackTraceElement[] stackTrace = error.getStackTrace();
            if (stackTrace.length < 2) {
                return;
            }
            StackTraceElement element = stackTrace[1];
            String label = element.getClassName() + element.getMethodName();

            AppInspectionService instance = AppInspectionService.instance();
            InspectorEnvironment.ExitHook hook = instance.mExperimental.mExitTransforms.get(label);
            if (hook != null) {
                hook.onExit(returnObject);
            } else {
                System.out.println("!!! Dropped on the floor");
            }
        }

        public static void onEntry(Object thisObject) {
            Error error = new Error();
            error.fillInStackTrace();
            StackTraceElement[] stackTrace = error.getStackTrace();
            if (stackTrace.length < 2) {
                return;
            }
            StackTraceElement element = stackTrace[1];
            String label = element.getClassName() + element.getMethodName();
            InspectorEnvironment.EntryHook hook =
                    AppInspectionService.instance().mExperimental.mEntryTransforms.get(label);
            System.out.println("!!!!! entry worked yo yo you! " + label + " " + hook);
            if (hook != null) {
                hook.onEntry(thisObject, Collections.emptyList());
            } else {
                System.out.println("!!! Dropped on the floor exit");
            }
        }
    }
}

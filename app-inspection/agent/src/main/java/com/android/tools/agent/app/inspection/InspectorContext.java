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

package com.android.tools.agent.app.inspection;

import android.util.Pair;
import androidx.annotation.NonNull;
import androidx.inspection.Inspector;
import androidx.inspection.InspectorEnvironment;
import androidx.inspection.InspectorExecutors;
import androidx.inspection.InspectorFactory;
import dalvik.system.DexClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

final class InspectorContext {
    private static final String MAIN_THREAD_NAME = "main";

    private final String mInspectorId;
    private Inspector mInspector;
    /**
     * The name of the project this inspector was launched with, which can be used to show error
     * messages to the user in case multiple projects try to launch the same inspector.
     */
    private final String mProjectName;

    // it keeps reference only to pending commands.
    private final ConcurrentHashMap<Integer, CommandCallbackImpl> mIdToCommandCallback =
            new ConcurrentHashMap<>();

    private final InspectorExecutors mExecutors;

    InspectorContext(
            @NonNull String inspectorId,
            @NonNull String project,
            @NonNull InspectorExecutors executors) {
        mInspectorId = inspectorId;
        mProjectName = project;
        mExecutors = executors;
    }

    public String getProject() {
        return mProjectName;
    }

    // TODO: shouldn't know nativePtr
    @SuppressWarnings("rawtypes")
    public String initializeInspector(String dexPath, long nativePtr) {
        ClassLoader mainClassLoader = mainThreadClassLoader();
        if (mainClassLoader == null) {
            return "Failed to find a main thread";
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
                if (mInspectorId.equals(inspectorFactory.getInspectorId())) {
                    ConnectionImpl connection = new ConnectionImpl(mInspectorId);
                    InspectorEnvironment environment =
                            new InspectorEnvironmentImpl(nativePtr, mInspectorId, mExecutors);
                    inspector = inspectorFactory.createInspector(connection, environment);
                    break;
                }
            }
            if (inspector == null) {
                return "Failed to find InspectorFactory with id " + mInspectorId;
            }
            mInspector = inspector;
            return null;
        } catch (Throwable e) {
            e.printStackTrace();
            return "Failed during instantiating inspector with id " + mInspectorId;
        }
    }

    public void sendCommand(int commandId, byte[] rawCommand) {
        CommandCallbackImpl callback = new CommandCallbackImpl(commandId);
        mIdToCommandCallback.put(commandId, callback);
        mInspector.onReceiveCommand(rawCommand, callback);
    }

    public void cancelCommand(int cancelledCommandId) {
        CommandCallbackImpl callback = mIdToCommandCallback.get(cancelledCommandId);
        if (callback != null) {
            callback.cancelCommand();
        }
    }

    public void disposeInspector() {
        if (mInspector == null) {
            return;
        }
        try {
            mInspector.onDispose();
        } catch (Throwable ignored) {
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

    public interface CrashListener {
        void onInspectorCrashed(String inspectorId, String message);
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
        private final List<Pair<Executor, Runnable>> mCancellationListeners = new ArrayList<>();

        CommandCallbackImpl(int commandId) {
            mCommandId = commandId;
        }

        @Override
        public void reply(@NonNull byte[] bytes) {
            synchronized (mLock) {
                if (mStatus == Status.PENDING) {
                    mStatus = Status.REPLIED;
                    mIdToCommandCallback.remove(mCommandId);
                    NativeTransport.sendRawResponseSuccess(mCommandId, bytes, bytes.length);
                }
            }
        }

        @Override
        public void addCancellationListener(
                @NonNull Executor executor, @NonNull Runnable runnable) {
            synchronized (mLock) {
                if (mStatus == Status.CANCELLED) {
                    executor.execute(runnable);
                } else {
                    mCancellationListeners.add(new Pair<>(executor, runnable));
                }
            }
        }

        void cancelCommand() {
            List<Pair<Executor, Runnable>> listeners = null;
            synchronized (mLock) {
                if (mStatus == Status.PENDING) {
                    mStatus = Status.CANCELLED;
                    mIdToCommandCallback.remove(mCommandId);
                    listeners = new ArrayList<>(mCancellationListeners);
                }
            }
            if (listeners != null) {
                for (Pair<Executor, Runnable> p : listeners) {
                    p.first.execute(p.second);
                }
            }
        }
    }
}

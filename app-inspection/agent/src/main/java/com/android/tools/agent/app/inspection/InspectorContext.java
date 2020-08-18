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
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

final class InspectorContext {
    private final String mInspectorId;
    private Inspector mInspector;
    /**
     * The name of the project this inspector was launched with, which can be used to show error
     * messages to the user in case multiple projects try to launch the same inspector.
     */
    private final String mProjectName;

    // it keeps reference only to pending commands.
    private final ConcurrentHashMap<Integer, WeakReference<CommandCallbackImpl>>
            mIdToCommandCallback = new ConcurrentHashMap<>();

    private final InspectorExecutors mExecutors;

    private final Consumer<Throwable> mCrashConsumer;

    InspectorContext(
            @NonNull String inspectorId,
            @NonNull String project,
            @NonNull InspectorExecutors executors,
            Consumer<Throwable> crashConsumer) {
        mInspectorId = inspectorId;
        mProjectName = project;
        mExecutors = executors;
        mCrashConsumer = crashConsumer;
    }

    public String getProject() {
        return mProjectName;
    }

    // TODO: shouldn't know nativePtr
    @SuppressWarnings("rawtypes")
    public String initializeInspector(String dexPath, long nativePtr) {
        ClassLoader mainClassLoader = ClassLoaderUtils.mainThreadClassLoader();
        if (mainClassLoader == null) {
            return "Failed to find a main thread";
        }
        String optimizedDir = ClassLoaderUtils.optimizedDirectory;

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
        mIdToCommandCallback.put(commandId, new WeakReference<>(callback));
        mInspector.onReceiveCommand(rawCommand, callback);
    }

    public void cancelCommand(int cancelledCommandId) {
        WeakReference<CommandCallbackImpl> reference = mIdToCommandCallback.get(cancelledCommandId);
        if (reference != null) {
            CommandCallbackImpl callback = reference.get();
            if (callback != null) {
                callback.cancelCommand();
            }
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

        @Override
        protected void finalize() {
            if (mStatus == Status.PENDING) {
                // Don't actually throw the exception, or that will cancel the finalize process
                // Instead, just inform the consumer, who will handle sending off the crash event
                // and disposing the current inspector.
                mCrashConsumer.accept(
                        new Exception(
                                "CommandCallback#reply for command with ID "
                                        + mCommandId
                                        + " was never called"));
            }
        }
    }
}

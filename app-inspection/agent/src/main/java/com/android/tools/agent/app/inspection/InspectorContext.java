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

import android.os.Build;
import android.util.Pair;
import androidx.annotation.NonNull;
import androidx.inspection.Inspector;
import androidx.inspection.InspectorEnvironment;
import androidx.inspection.InspectorExecutors;
import androidx.inspection.InspectorFactory;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

final class InspectorContext {
    /**
     * A reasonable size for a chunk (in bytes), to be used by {@link NativeTransport#sendPayload}.
     */
    private static final int CHUNK_SIZE = 50 * 1024;

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

    // Created classloaders are cached for process lifetime here.
    // Theoretically, starting with Android N classes can be unloaded and
    // their respective classloaders can be gc-ed.
    // However, in practice it is hard to guarantee that a reference to class isn't cached somewhere
    // keeping classloader around. Additionally, there is no guarantee when gc is called, allowing
    // DexClassLoader to be alive much longer then necessary. Having two DexClassloaders created
    // from the same jars is a problem, because they start fighting over resources:
    // second Classloader will fail to loadLibrary(), because
    // underlying resource isn't closed by first classloader (b/187342510)
    // To avoid this issue and to avoid multiple copies of same DexClassloader in the memory
    // they are explicitly cached here.
    private static final ConcurrentHashMap<String, ClassLoader> sCachedClassLoaders =
            new ConcurrentHashMap<>();

    // TODO: shouldn't know nativePtr
    @SuppressWarnings("rawtypes")
    public String initializeInspector(String dexPath, long nativePtr) {
        ClassLoader mainClassLoader = ClassLoaderUtils.mainThreadClassLoader();
        if (mainClassLoader == null) {
            return "Failed to find a main thread";
        }
        try {
            ClassLoader classLoader =
                    sCachedClassLoaders.computeIfAbsent(dexPath, s -> createClassloader(dexPath));
            ServiceLoader<InspectorFactory> loader =
                    ServiceLoader.load(InspectorFactory.class, classLoader);
            Iterator<InspectorFactory> iterator = loader.iterator();
            Inspector inspector = null;
            while (iterator.hasNext()) {
                InspectorFactory inspectorFactory = iterator.next();
                if (mInspectorId.equals(inspectorFactory.getInspectorId())) {
                    ConnectionImpl connection = new ConnectionImpl(mInspectorId, CHUNK_SIZE);
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

    private static ClassLoader createClassloader(String dexPath) {
        ClassLoader mainClassLoader = ClassLoaderUtils.mainThreadClassLoader();
        String optimizedDir = ClassLoaderUtils.optimizedDirectory;
        try {
            String nativePath = prepareNativeLibraries(dexPath, Build.SUPPORTED_ABIS[0]);
            return new DexClassLoader(dexPath, optimizedDir, nativePath, mainClassLoader);
        } catch (IOException e) {
            // can't recover from this IOException so promote it to runtime exception
            throw new RuntimeException("Failed to create classloader", e);
        }
    }

    // Theoretically there is no need in this, because classloader should find libraries right in
    // our jar with "path_to_apk!/lib/<host>", but unfortunately it doesn't seem to work
    private static String prepareNativeLibraries(String dexPath, String abi) throws IOException {
        JarFile jarFile = new JarFile(dexPath);
        ZipEntry lib = jarFile.getEntry("lib/");
        if (lib == null || !lib.isDirectory()) {
            return null;
        }
        File dexFile = new File(dexPath);
        String tmpDir = System.getProperty("java.io.tmpdir");
        Path tmpPath = Paths.get(tmpDir);
        File workingDir = new File(tmpPath.toFile(), dexFile.getName() + "_unpacked_lib");
        if (!workingDir.exists() && !workingDir.mkdir()) {
            throw new IOException("Failed to create working dir: " + workingDir);
        }
        Enumeration<JarEntry> entries = jarFile.entries();
        String targetFolder = "lib/" + abi + "/";
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.getName().startsWith(targetFolder)
                    && !entry.isDirectory()
                    && entry.getName().endsWith(".so")) {
                String name = entry.getName().substring(entry.getName().lastIndexOf('/') + 1);
                File file = new File(workingDir, name);
                Files.copy(
                        jarFile.getInputStream(entry),
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return workingDir.getAbsolutePath();
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
        for (WeakReference<CommandCallbackImpl> reference : mIdToCommandCallback.values()) {
            CommandCallbackImpl callback = reference.get();
            if (callback != null) {
                callback.dispose();
            }
        }
    }

    public interface CrashListener {
        void onInspectorCrashed(String inspectorId, String message);
    }

    enum Status {
        PENDING,
        REPLIED,
        CANCELLED,
        DISPOSED,
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
                    if (bytes.length <= CHUNK_SIZE) {
                        NativeTransport.sendRawResponseData(mCommandId, bytes, bytes.length);
                    } else {
                        long payloadId =
                                NativeTransport.sendPayload(bytes, bytes.length, CHUNK_SIZE);
                        NativeTransport.sendRawResponsePayload(mCommandId, payloadId);
                    }
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

        void dispose() {
            synchronized (mLock) {
                mStatus = Status.DISPOSED;
                mIdToCommandCallback.remove(mCommandId);
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

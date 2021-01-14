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

import com.android.tools.deployer.Sites;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public class LiveLiteralSupport {
    public static final String LIVE_LITERAL_KT = "androidx.compose.runtime.internal.LiveLiteralKt";

    // This map contains all LiveLiteral$FooBarKt ->
    //   Updated Literal Field -> Update Literal value mapping.
    //
    // The first instrumented helper to get loaded into the class loader will populate this
    // map by reading the file pointed by the mapping file.
    private static Map<String, Map<String, Object>> initMap;

    // The application's package name used to locate the .overlay directory.
    // The first literal update request on set this to non-null. Otherwise,
    // the first start-up agent will set the value on application restart.
    private static String applicationId = null;

    // TODO: Find a way to not have a user visible thread hang around after
    //       updates are done.
    // A single helper background thread responsible for writing the initMap to disk.
    // This thread make sure that:
    //  1) The application does not have any strict mode violation. (IE it is not writing
    //     to disk on the main thread.
    //  2) The write is thread safe. (By the single threaded nature of this pool)
    private static final ExecutorService POOL =
            Executors.newFixedThreadPool(
                    1,
                    new ThreadFactory() {
                        @Override
                        public Thread newThread(Runnable runnable) {
                            return new Thread(
                                    new ThreadGroup("Live Literal Support"),
                                    runnable,
                                    "I/O Thread");
                        }
                    });

    // Time step when a last live literal update occurred.
    // Should the writing thread just written to disk recently, it would sleep for a short time.
    // This make sure that the writer thread is not thrashing the system by continuously writing
    // to disk, a possibility if the user is typing around the speed of how fast intellij
    // trigger a literal update (around 50ms per keystroke constantly).
    private static final AtomicLong LAST_UPDATE_REQUEST_MS = new AtomicLong(-1);

    // The writer thread make sure there is at least 500ms gap between writes, perventing
    // it from rapdily writing to disk on fast keystrokes.
    private static final int WRITE_INTERVAL_MS = 500;

    /**
     * For performance reasons, the runtime will start checking for literal update request until
     * isLiveLiteralsEnabled is set to true.
     *
     * <p>This will only be called by the main thread so there is no need for synchronization.
     *
     * @param liveLiteralKtClass androidx.compose.runtime.internal.LiveLiteralKt needs to be passed
     *     in from a JVMTI class search because this class will be loaded in the boot classloader
     *     and will not be able to find the application classes.
     * @param targetApplicationId The package name of the application.
     */
    public static void enable(Class<?> liveLiteralKtClass, String targetApplicationId) {
        applicationId = targetApplicationId;
        try {
            Method enableMethod = liveLiteralKtClass.getMethod("enableLiveLiterals");
            enableMethod.invoke(liveLiteralKtClass);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Similar to enable() but for the application start-up to initialize the mapping file.
     *
     * <p>Note that we don't read the mapping here as it will only be done on demend when the first
     * literal help get loaded into the classloader.
     *
     * <p>This will only be called by the main thread so there is no need for sychronization.
     */
    public static void enableStartup(String targetApplicationId) {
        applicationId = targetApplicationId;
    }

    /**
     * This is the function that a literal helper is instrumented to call after clinit is completed.
     *
     * <p>It will determine the caller of this function and check if there literal mapping has an
     * entry for the caller. If so, the caller's fields will be remapping to updated values.
     */
    public static void reinit() {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        // Search for the caller of this method.
        int frame = 0;
        while (!stackTraceElements[frame]
                .getClassName()
                .equals(LiveLiteralSupport.class.getName())) {
            frame++;
        }
        StackTraceElement callerFrame = stackTraceElements[frame + 1];
        String helperClassName = callerFrame.getClassName();
        try {
            Class<?> helperClass =
                    Thread.currentThread().getContextClassLoader().loadClass(helperClassName);
            restore(helperClass);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    // Accessing the initMap will require a lock.
    private static synchronized void restore(Class<?> helper) {
        loadFromDiskIfNeeded();
        String name = helper.getCanonicalName();
        Map<String, Object> fields = initMap.get(name);
        if (fields == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            try {
                Field field = helper.getDeclaredField(key);
                field.setAccessible(true);
                field.set(helper, value);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    // Accessing the initMap will require a lock.
    public static synchronized void add(String helper, String key, Object value) {
        loadFromDiskIfNeeded();
        Map<String, Object> fields = initMap.get(helper);
        if (fields == null) {
            fields = new HashMap<>();
            initMap.put(helper, fields);
        }
        fields.put(key, value);
        POOL.submit(() -> saveToDisk());
    }

    // Accessing the initMap will require a lock.
    private static synchronized void saveToDisk() {
        while (System.currentTimeMillis() - LAST_UPDATE_REQUEST_MS.get() < WRITE_INTERVAL_MS) {
            // If it looks like the app is super busy with updating literals, take a short nap
            try {
                Thread.sleep(WRITE_INTERVAL_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        store(initMap);
        LAST_UPDATE_REQUEST_MS.set(System.currentTimeMillis());
    }

    // Accessing the initMap will require a lock.
    private static synchronized void loadFromDiskIfNeeded() {
        if (initMap != null) {
            return;
        }
        initMap = new HashMap<>();
        String mappingFile = getMappingFileLocation();
        try (ObjectInputStream in =
                new ObjectInputStream(Files.newInputStream(Paths.get(mappingFile)))) {
            initMap.putAll((HashMap) in.readObject());
        } catch (FileNotFoundException e) {
            // Just use the empty map created.
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException c) {
            // HashMap? Hopefully not.....
            c.printStackTrace();
        }
    }
    // Accessing the initMap will require a lock.
    private static synchronized void store(Map<String, Map<String, Object>> literals) {
        try {
            Path dir = Paths.get(Sites.appLiveLiteral(applicationId));
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        Path mappingFile = Paths.get(getMappingFileLocation());
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(mappingFile))) {
            out.writeObject(literals);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String getMappingFileLocation() {
        return Sites.appLiveLiteral(applicationId) + "ll.mapping";
    }
}

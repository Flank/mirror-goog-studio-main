/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.profiler.support.network.okhttp;

import com.android.tools.profiler.support.util.StudioLog;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.util.List;

@SuppressWarnings("unused") // This class and all methods referenced via instrumentation.
public final class OkHttp2Wrapper {
    private static final String DEX_PATH = getDexPath();
    private static final String OKHTTP2_INTERCEPTOR_CLASS_NAME =
            "com.android.tools.profiler.agent.okhttp.OkHttp2Interceptor";

    private static ThreadLocal<ClassLoader> myOkHttp2ClassLoader = new ThreadLocal<ClassLoader>();

    /**
     * Adds okhttp2 Interceptor during {@code OkHttpClient} construction.
     *
     * <p>This is the entry-point for compile-time BCI.
     */
    @SuppressWarnings("unchecked")
    public static void addInterceptorToClient(Object client) {
        try {
            setOkHttpClassLoader(client);
            Class<?> interceptorClass =
                    myOkHttp2ClassLoader.get().loadClass(OKHTTP2_INTERCEPTOR_CLASS_NAME);
            interceptorClass.getMethod("addToClient", Object.class).invoke(null, client);
        } catch (Exception ex) {
            StudioLog.e(
                    "Could not add an OkHttp2 profiler interceptor during OkHttpClient construction",
                    ex);
        }
    }

    /**
     * Gets OkHttp class loader and store as thread local variable during runtime (JVMTI) BCI. Other
     * hook methods could use this if the method arguments do not provide.
     */
    @SuppressWarnings("unchecked")
    public static void setOkHttpClassLoader(Object okHttpObject) {
        try {
            if (myOkHttp2ClassLoader.get() == null) {
                ClassLoader classLoader = okHttpObject.getClass().getClassLoader();
                try {
                    if (DEX_PATH != null) {
                        // Uses tmp directory for app writing optimized dex file. Though documentation
                        // of DexClassLoader recommend context.getCodeCacheDir, context instance is unavailable.
                        String optimizedDir = System.getProperty("java.io.tmpdir");
                        classLoader = new DexClassLoader(DEX_PATH, optimizedDir, null, classLoader);
                    }

                } catch (Exception ignored) {
                }
                myOkHttp2ClassLoader.set(classLoader);
            }
        } catch (Exception ex) {
            StudioLog.e("Could not set up OkHttp2 class loader", ex);
        }
    }

    /**
     * Modifies a list of okhttp2 {@code List<Interceptor>}s in place, adding our own profiler
     * interceptor into it if not already present, and then returning the list again (so that this
     * method can be inserted into the middle of instrumented code).
     *
     * <p>In okhttp2 (unlike okhttp3), {@code networkInterceptors()} returns direct access to an
     * {@code OkHttpClient}s list of interceptors and uses that as the API for a user to add more,
     * e.g.
     *
     * <pre>
     *   OkHttpClient client = ...;
     *   client.networkInterceptors().add(customInterceptor);
     * </pre>
     *
     * Therefore, we have to modify the list in place, whenever it is first accessed (either by the
     * user to add their own interceptor, or by OkHttp internally to iterate through all
     * interceptors). If we created a copy of the list and returned that instead (a common
     * instrumentation pattern), then the user's interceptor would never actually get added into the
     * underlying list.
     *
     * <p>This is the entry-point for runtime (JVMTI) BCI. It dynamically loads the {@code
     * Interceptor} class from a dex build of OkHttp2.
     */
    @SuppressWarnings("unchecked")
    public static List insertInterceptor(List interceptors) {
        try {
            if (myOkHttp2ClassLoader.get() != null) {
                Class<?> interceptorClass =
                        myOkHttp2ClassLoader.get().loadClass(OKHTTP2_INTERCEPTOR_CLASS_NAME);

                for (Object interceptor : interceptors) {
                    if (interceptor.getClass() == interceptorClass) {
                        // We can abort early if we're already added
                        return interceptors;
                    }
                }

                interceptors.add(0, interceptorClass.newInstance());
            }
        } catch (Exception ex) {
            StudioLog.e("Could not insert an OkHttp2 profiler interceptor", ex);
        }
        return interceptors;
    }

    private static String getDexPath() {
        // TODO: Remove using perfd path for dex.
        String dexPath = "/data/local/tmp/perfd/perfa_okhttp.dex";
        if (new File(dexPath).exists()) {
            return dexPath;
        }
        String runfilesDir = System.getProperty("user.dir");
        dexPath = runfilesDir + "/tools/base/profiler/app/perfa_okhttp.dex";
        if (runfilesDir != null && !runfilesDir.isEmpty() && new File(dexPath).exists()) {
            return dexPath;
        }
        return null;
    }
}

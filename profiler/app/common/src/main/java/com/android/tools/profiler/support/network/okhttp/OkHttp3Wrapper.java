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
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused") // This class and all methods referenced via instrumentation.
public final class OkHttp3Wrapper {
    private static final String DEX_PATH = getDexPath();
    private static final String OKHTTP3_INTERCEPTOR_CLASS_NAME =
            "com.android.tools.profiler.agent.okhttp.OkHttp3Interceptor";

    private static ThreadLocal<ClassLoader> myOkHttp3ClassLoader = new ThreadLocal<ClassLoader>();

    /**
     * Adds okhttp3 Interceptor to an {@code OkHttpClient.Builder} as soon as it is constructed.
     *
     * <p>This is the intended entry-point for compile-time BCI.
     */
    public static void addInterceptorToBuilder(Object builder) {
        try {
            setOkHttpClassLoader(builder);
            Class<?> interceptorClass =
                    myOkHttp3ClassLoader.get().loadClass(OKHTTP3_INTERCEPTOR_CLASS_NAME);
            interceptorClass.getMethod("addToBuilder", Object.class).invoke(null, builder);
        } catch (Exception ex) {
            StudioLog.e(
                    "Could not add an OkHttp3 profiler interceptor during OkHttpClient construction",
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
            if (myOkHttp3ClassLoader.get() == null) {
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
                myOkHttp3ClassLoader.set(classLoader);
            }
        } catch (Exception ex) {
            StudioLog.e("Could not set up OkHttp2 class loader", ex);
        }
    }

    /**
     * Adds an okhttp3 Interceptor to a {@code List<Interceptor>}, returning a copy of the list with
     * the interceptor added at the beginning of the list.
     *
     * <p>This is the entry-point for runtime (JVMTI) BCI. It dynamically loads Interceptor class
     * from dex file which uses compileOnly dependency on OkHttp.
     */
    @SuppressWarnings("unchecked")
    public static List insertInterceptor(List interceptors) {
        ArrayList list = new ArrayList(interceptors);
        try {
            if (myOkHttp3ClassLoader.get() != null) {
                Class<?> interceptorClass =
                        myOkHttp3ClassLoader.get().loadClass(OKHTTP3_INTERCEPTOR_CLASS_NAME);
                list.add(0, interceptorClass.newInstance());
            }
        } catch (Exception ex) {
            StudioLog.e("Could not insert an OkHttp3 profiler interceptor", ex);
        }
        return list;
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

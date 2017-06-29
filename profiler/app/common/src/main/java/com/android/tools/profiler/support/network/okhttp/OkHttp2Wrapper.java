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

import com.android.tools.profiler.support.network.okhttp.reflection.okhttp2.OkHttpClient$;
import com.android.tools.profiler.support.util.StudioLog;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused") // This class and all methods referenced via reflection
public final class OkHttp2Wrapper {
    /**
     * Adds okhttp2 Interceptor during {@code OkHttpClient} construction.
     *
     * <p>This is the entry-point for compile-time BCI.
     */
    @SuppressWarnings("unchecked")
    public static void addInterceptorToClient(Object client) {
        try {
            OkHttp2ClassLoader.setClassLoader(client.getClass().getClassLoader());
            new OkHttpClient$(client).networkInterceptors().add(OkHttp2Interceptor.create().obj);
        } catch (Exception ex) {
            StudioLog.e(
                    "Could not add an OkHttp2 profiler interceptor during OkHttpClient construction",
                    ex);
        }
    }

    /**
     * Adds an okhttp2 Interceptor to a {@code List<Interceptor>}, returning a copy of the list with
     * the interceptor added at the beginning of the list.
     *
     * <p>This is the entry-point for runtime (JVMTI) BCI.
     */
    @SuppressWarnings("unchecked")
    public static List insertInterceptor(List interceptors) {
        ArrayList list = new ArrayList(interceptors);
        try {
            // TODO: Find a way to solidate okhttp class loader.
            list.add(0, OkHttp2Interceptor.create().obj);
        } catch (ClassNotFoundException ex) {
            StudioLog.e("Could not insert an OkHttp2 profiler interceptor", ex);
        }
        return list;
    }
}

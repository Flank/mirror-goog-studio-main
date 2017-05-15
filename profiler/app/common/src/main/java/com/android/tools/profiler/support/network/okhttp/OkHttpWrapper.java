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

import android.util.Log;
import com.android.tools.profiler.support.ProfilerService;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;

@SuppressWarnings("unused") // Referenced via reflection in OkHttpAdapter
public final class OkHttpWrapper {

    /**
     * Adds okhttp3 Interceptor to an {@code OkHttpClient.Builder} if it does not contain one.
     *
     * <p>Reflection code equals to {@code builder.addNetworkInterceptor(proxy);}
     */
    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static void addOkHttp3Interceptor(Object builder) {
        Class<?> interceptorClass;
        try {
            interceptorClass =
                    Class.forName(
                            "okhttp3.Interceptor", false, builder.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            Log.e(ProfilerService.STUDIO_PROFILER, "Failed to find Interceptor class");
            return;
        }
        Object proxy =
                Proxy.newProxyInstance(
                        interceptorClass.getClassLoader(),
                        new Class[] {interceptorClass},
                        new OkHttp3InterceptorHandler());
        try {
            builder.getClass()
                    .getDeclaredMethod("addNetworkInterceptor", interceptorClass)
                    .invoke(builder, proxy);
        } catch (NoSuchMethodException ignored) {
            Log.e(ProfilerService.STUDIO_PROFILER, "Failed to find addNetworkInterceptor method");
        } catch (IllegalAccessException ignored) {
            Log.e(ProfilerService.STUDIO_PROFILER, "Failed to access addNetworkInterceptor method");
        } catch (InvocationTargetException ignored) {
            Log.e(ProfilerService.STUDIO_PROFILER, "Failed to invoke addNetworkInterceptor method");
        }
    }

    /**
     * Adds okhttp2 Interceptor during {@code OkHttpClient} construction.
     *
     * <p>Reflection code equals to {@code networkInterceptors().add(proxy);}
     */
    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static void addOkHttp2Interceptor(Object client) {
        Class<?> interceptorClass;
        try {
            interceptorClass =
                    Class.forName(
                            "com.squareup.okhttp.Interceptor",
                            false,
                            client.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            Log.e(ProfilerService.STUDIO_PROFILER, "Failed to find Interceptor class");
            return;
        }
        Object proxy =
                Proxy.newProxyInstance(
                        interceptorClass.getClassLoader(),
                        new Class[] {interceptorClass},
                        new OkHttp2InterceptorHandler());
        try {
            List list =
                    (List)
                            client.getClass()
                                    .getDeclaredMethod("networkInterceptors")
                                    .invoke(client);
            assert list.isEmpty()
                    : String.format("Unexpected network interceptor list of size %d", list.size());
            list.add(interceptorClass.cast(proxy));
        } catch (NoSuchMethodException ignored) {
            Log.e(ProfilerService.STUDIO_PROFILER, "Failed to find networkInterceptors method");
        } catch (IllegalAccessException ignored) {
            Log.e(ProfilerService.STUDIO_PROFILER, "Failed to access networkInterceptors method");
        } catch (InvocationTargetException ignored) {
            Log.e(ProfilerService.STUDIO_PROFILER, "Failed to invoke networkInterceptors method");
        }
    }
}

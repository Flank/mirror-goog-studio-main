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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused") // This class and all methods referenced via reflection
public final class OkHttpWrapper {

    /**
     * Adds okhttp3 Interceptor to an {@code OkHttpClient.Builder} if it does not contain one.
     *
     * <p>Reflection code equals to {@code builder.addNetworkInterceptor(interceptor);}
     */
    public static void addOkHttp3Interceptor(Object builder) {
        Object interceptor = newOkHttp3Interceptor();
        if (interceptor == null) {
            return;
        }

        try {
            Class<?> interceptorClass = ((Proxy) interceptor).getClass().getInterfaces()[0];
            builder.getClass()
                    .getDeclaredMethod("addNetworkInterceptor", interceptorClass)
                    .invoke(builder, interceptor);
        } catch (NoSuchMethodException ignored) {
        } catch (IllegalAccessException ignored) {
        } catch (InvocationTargetException ignored) {
        }
    }

    /**
     * Adds an okhttp3 Interceptor to a {@code List<Interceptor>}, returning a copy of the list with
     * the interceptor added (or the original list if our interceptor was already in the list).
     */
    public static List appendOkHttp3Interceptor(List interceptors) {
        for (Object interceptor : interceptors) {
            if (Proxy.isProxyClass(interceptor.getClass())) {
                // We already added ourselves, so abort early
                return interceptors;
            }
        }

        ArrayList list = new ArrayList(interceptors);
        Object interceptor = newOkHttp3Interceptor();
        if (interceptor != null) {
            list.add(interceptor);
        }
        return list;
    }

    /**
     * Adds okhttp2 Interceptor during {@code OkHttpClient} construction.
     *
     * <p>Reflection code equals to {@code networkInterceptors().add(interceptor);}
     */
    public static void addOkHttp2Interceptor(Object client) {
        Object interceptor = newOkHttp2Interceptor();
        if (interceptor == null) {
            return;
        }

        try {
            List list =
                    (List)
                            client.getClass()
                                    .getDeclaredMethod("networkInterceptors")
                                    .invoke(client);
            assert list.isEmpty()
                    : String.format("Unexpected network interceptor list of size %d", list.size());
            list.add(interceptor);
        } catch (NoSuchMethodException ignored) {
        } catch (IllegalAccessException ignored) {
        } catch (InvocationTargetException ignored) {
        }
    }

    /**
     * Adds an okhttp2 Interceptor to a {@code List<Interceptor>}, returning a copy of the list with
     * the interceptor added (or the original list if our interceptor was already in the list).
     */
    public static List appendOkHttp2Interceptor(List interceptors) {
        for (Object interceptor : interceptors) {
            if (Proxy.isProxyClass(interceptor.getClass())) {
                // We already added ourselves, so abort early
                return interceptors;
            }
        }

        ArrayList list = new ArrayList(interceptors);
        Object interceptor = newOkHttp2Interceptor();
        if (interceptor != null) {
            list.add(interceptor);
        }
        return list;
    }

    private static Object newOkHttp3Interceptor() {
        Class<?> interceptorClass;
        try {
            interceptorClass =
                    Class.forName(
                            "okhttp3.Interceptor",
                            false,
                            Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
        return Proxy.newProxyInstance(
                interceptorClass.getClassLoader(),
                new Class[] {interceptorClass},
                new OkHttp3InterceptorHandler());
    }

    private static Object newOkHttp2Interceptor() {
        Class<?> interceptorClass;
        try {
            interceptorClass =
                    Class.forName(
                            "com.squareup.okhttp.Interceptor",
                            false,
                            Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
        return Proxy.newProxyInstance(
                interceptorClass.getClassLoader(),
                new Class[] {interceptorClass},
                new OkHttp2InterceptorHandler());
    }
}

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

import com.android.tools.profiler.support.network.okhttp.reflection.okhttp3.OkHttpClient$;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused") // This class and all methods referenced via reflection
public final class OkHttp3Wrapper {

    /**
     * Adds okhttp3 Interceptor to an {@code OkHttpClient.Builder}.
     *
     * <p>This is the intended entry-point for compile-time BCI.
     */
    public static void addInterceptorToBuilder(Object builder) {
        try {
            new OkHttpClient$.Builder$(builder).addNetworkInterceptor(OkHttp3Interceptor.create());
        } catch (NoSuchMethodException ignored) {
        } catch (InvocationTargetException ignored) {
        } catch (IllegalAccessException ignored) {
        } catch (ClassNotFoundException ignored) {
        }
    }

    /**
     * Adds an okhttp3 Interceptor to a {@code List<Interceptor>}, returning a copy of the list with
     * the interceptor added.
     *
     * <p>This is the entry-point for runtime (JVMTI) BCI.
     */
    @SuppressWarnings("unchecked")
    public static List appendInterceptor(List interceptors) {
        ArrayList list = new ArrayList(interceptors);
        try {
            list.add(OkHttp3Interceptor.create().obj);
        } catch (ClassNotFoundException ignored) {
        }
        return list;
    }
}

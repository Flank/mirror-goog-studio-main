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

package com.android.tools.profiler.support.network.okhttp.reflection.okhttp3;

import com.android.tools.profiler.support.network.okhttp.OkHttp3ClassLoader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public final class Interceptor$ {
    public final Object obj;

    public Interceptor$(Object interceptor) {
        this.obj = interceptor;
    }

    public static Class getTargetClass() throws ClassNotFoundException {
        return OkHttp3ClassLoader.loadClass("okhttp3.Interceptor");
    }

    public static final class Chain$ {
        public final Object obj;

        public Chain$(Object chain) {
            this.obj = chain;
        }

        public Request$ request()
                throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
            return new Request$(obj.getClass().getMethod("request").invoke(obj));
        }

        public Response$ proceed(Request$ request)
                throws IOException, NoSuchMethodException, InvocationTargetException,
                        IllegalAccessException, ClassNotFoundException {
            return new Response$(
                    obj.getClass()
                            .getMethod("proceed", Request$.getTargetClass())
                            .invoke(obj, request.obj));
        }
    }
}

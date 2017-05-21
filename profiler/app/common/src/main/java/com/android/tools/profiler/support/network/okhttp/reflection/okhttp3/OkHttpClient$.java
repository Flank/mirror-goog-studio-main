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

import java.lang.reflect.InvocationTargetException;

public class OkHttpClient$ {
    public final Object obj;

    private OkHttpClient$(Object client) {
        this.obj = client;
    }

    public static final class Builder$ {
        public final Object obj;

        public Builder$(Object builder) {
            this.obj = builder;
        }

        public Builder$ addNetworkInterceptor(Interceptor$ interceptor)
                throws NoSuchMethodException, InvocationTargetException, IllegalAccessException,
                        ClassNotFoundException {
            obj.getClass()
                    .getMethod("addNetworkInterceptor", Interceptor$.getTargetClass())
                    .invoke(obj, interceptor.obj);
            return this;
        }
    }
}

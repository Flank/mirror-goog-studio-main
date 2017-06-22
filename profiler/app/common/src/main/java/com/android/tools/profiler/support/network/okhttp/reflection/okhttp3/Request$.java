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
import java.lang.reflect.InvocationTargetException;

public class Request$ {
    public final Object obj;

    public Request$(Object request) {
        this.obj = request;
    }

    public static Class getTargetClass() throws ClassNotFoundException {
        return OkHttp3ClassLoader.loadClass("okhttp3.Request");
    }

    public HttpUrl$ url()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return new HttpUrl$(obj.getClass().getDeclaredMethod("url").invoke(obj));
    }

    public String method()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return (String) obj.getClass().getDeclaredMethod("method").invoke(obj);
    }

    public Headers$ headers()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return new Headers$(obj.getClass().getDeclaredMethod("headers").invoke(obj));
    }
}

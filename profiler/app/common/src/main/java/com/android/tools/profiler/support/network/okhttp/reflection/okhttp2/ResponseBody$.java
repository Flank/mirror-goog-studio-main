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

package com.android.tools.profiler.support.network.okhttp.reflection.okhttp2;

import com.android.tools.profiler.support.network.okhttp.reflection.okio.BufferedSource$;
import java.lang.reflect.InvocationTargetException;

public class ResponseBody$ {
    public final Object obj;

    public ResponseBody$(Object responseBody) {
        this.obj = responseBody;
    }

    public static Class getTargetClass() throws ClassNotFoundException {
        return Thread.currentThread()
                .getContextClassLoader()
                .loadClass("com.squareup.okhttp.ResponseBody");
    }

    public static ResponseBody$ create(
            MediaType$ mediaType, long contentLength, BufferedSource$ source)
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
                    IllegalAccessException {
        Class<?> bodyClass = ResponseBody$.getTargetClass();
        Object body =
                bodyClass
                        .getMethod(
                                "create",
                                MediaType$.getTargetClass(),
                                Long.TYPE,
                                BufferedSource$.getTargetClass())
                        .invoke(null, mediaType.obj, contentLength, source.obj);
        return new ResponseBody$(body);
    }

    public MediaType$ contentType()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return new MediaType$(obj.getClass().getDeclaredMethod("contentType").invoke(obj));
    }

    public long contentLength()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return (Long) obj.getClass().getDeclaredMethod("contentLength").invoke(obj);
    }

    public BufferedSource$ source()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return new BufferedSource$(obj.getClass().getDeclaredMethod("source").invoke(obj));
    }
}

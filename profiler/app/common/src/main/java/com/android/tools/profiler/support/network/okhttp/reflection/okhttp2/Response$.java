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

import java.lang.reflect.InvocationTargetException;

public class Response$ {
    public final Object obj;

    public Response$(Object response) {
        this.obj = response;
    }

    public Headers$ headers()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return new Headers$(obj.getClass().getDeclaredMethod("headers").invoke(obj));
    }

    public int code()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return (Integer) obj.getClass().getDeclaredMethod("code").invoke(obj);
    }

    public ResponseBody$ body()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return new ResponseBody$(obj.getClass().getDeclaredMethod("body").invoke(obj));
    }

    public Builder$ newBuilder()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return new Builder$(obj.getClass().getDeclaredMethod("newBuilder").invoke(obj));
    }

    public static class Builder$ {
        public final Object obj;

        public Builder$(Object builder) {
            this.obj = builder;
        }

        public Builder$ body(ResponseBody$ responseBody)
                throws NoSuchMethodException, InvocationTargetException, IllegalAccessException,
                        ClassNotFoundException {
            obj.getClass()
                    .getDeclaredMethod("body", ResponseBody$.getTargetClass())
                    .invoke(obj, responseBody.obj);
            return this;
        }

        public Response$ build()
                throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
            return new Response$(obj.getClass().getDeclaredMethod("build").invoke(obj));
        }
    }
}

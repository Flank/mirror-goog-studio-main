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

public class MediaType$ {
    public final Object obj;

    public MediaType$(Object mediaType) {
        this.obj = mediaType;
    }

    public static Class getTargetClass() throws ClassNotFoundException {
        return Thread.currentThread().getContextClassLoader().loadClass("okhttp3.MediaType");
    }
}

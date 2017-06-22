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

package com.android.tools.profiler.support.network.okhttp.reflection.okio;

import com.android.tools.profiler.support.network.okhttp.OkioClassLoader;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

public class Okio$ {
    public static Source$ source(InputStream inputStream)
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
                    IllegalAccessException {
        Class<?> okioClass = getTargetClass();
        Object source = okioClass.getMethod("source", InputStream.class).invoke(null, inputStream);
        return new Source$(source);
    }

    public static BufferedSource$ buffer(Source$ source)
            throws NoSuchMethodException, ClassNotFoundException, InvocationTargetException,
                    IllegalAccessException {
        Class<?> okioClass = getTargetClass();
        Class<?> sourceClass = Source$.getTargetClass();
        Object buffer = okioClass.getMethod("buffer", sourceClass).invoke(null, source.obj);
        return new BufferedSource$(buffer);
    }

    public static Class getTargetClass() throws ClassNotFoundException {
        return OkioClassLoader.loadClass("okio.Okio");
    }
}

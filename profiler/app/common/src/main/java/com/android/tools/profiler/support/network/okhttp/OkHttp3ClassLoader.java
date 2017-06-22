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

/**
 * Singleton used to load all OkHttp classes.
 *
 * <p>Thread.currentThread().getContextClassLoader() is usually reliable but is occasionally null,
 * so if we get access to an Object that we know is an instance of an OkHttp class, we can set its
 * classloader here instead, as this is the most reliable way to search for other OkHttp classes.
 */
public class OkHttp3ClassLoader {
    private static ClassLoader myClassLoader = Thread.currentThread().getContextClassLoader();

    public static void setClassLoader(ClassLoader classLoader) {
        myClassLoader = classLoader;
    }

    public static Class<?> loadClass(String className) throws ClassNotFoundException {
        return myClassLoader.loadClass(className);
    }
}

/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.deploy.instrument;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class ReflectionHelpers {

    public static Object getDeclaredField(Object object, String name) throws Exception {
        Class<?> clazz = object.getClass();
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    public static void setDeclaredField(Object object, String name, Object value) throws Exception {
        Class<?> clazz = object.getClass();
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    public static Object getField(Object object, String name) throws Exception {
        Class<?> clazz = object.getClass();
        Field field = clazz.getField(name);
        return field.get(object);
    }

    public static Object call(Object object, String name, Arg... args) throws Exception {
        return call(object, object.getClass(), name, args);
    }

    public static Object call(Class<?> clazz, String name, Arg... args) throws Exception {
        return call(null, clazz, name, args);
    }

    public static Arg arg(Object value) {
        return arg(value, value.getClass());
    }

    public static Arg arg(Object value, Class<?> clazz) {
        return new Arg(value, clazz);
    }

    public static class Arg {
        public final Object value;
        public final Class<?> clazz;

        private Arg(Object value, Class<?> clazz) {
            this.value = value;
            this.clazz = clazz;
        }
    }

    public static Object call(Object object, Class<?> clazz, String name, Arg... args)
            throws Exception {
        Class<?>[] classes = new Class<?>[args.length];
        Object[] vals = new Object[args.length];
        for (int i = 0; i < args.length; ++i) {
            classes[i] = args[i].clazz;
            vals[i] = args[i].value;
        }

        Method method = clazz.getDeclaredMethod(name, classes);
        method.setAccessible(true);

        return method.invoke(object, vals);
    }
}

/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.99 (the "License");
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

import static com.android.tools.deploy.instrument.ReflectionHelpers.*;

import com.android.tools.deploy.liveedit.MethodBodyEvaluator;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unused") // Used by native instrumentation code.
public final class LiveEditStubs {
    private static final String TAG = "studio.deploy";

    // TODO: This state + methods for manipulating it should probably live in their own class, along with any other LiveEdit state.
    private static final ConcurrentHashMap<String, byte[]> methodCache =
            new ConcurrentHashMap<>();

    // className should be the fully qualified class name: com.example.MyClass
    // methodName should just be the method name: myMethod
    // methodSignature should be the JNI-style method signature: (Ljava.lang.String;II)V
    public static void addToCache(
            String className, String methodName, String methodSignature, byte[] data) {
        String key = className + "->" + methodName + methodSignature;
        methodCache.put(key, data);
    }

    public static void addToCache(String key, byte[] data) {
        methodCache.put(key, data);
    }

    // Everything in the following section is called from the dex prologue created by StubTransform.
    // None of this code is or should be called from any other context.

    // The key format is based on what slicer passes as the first parameter to an EntryHook callback.
    // The format can be found in tools/slicer/instrumentation.cc in the MethodLabel method.
    // TODO: We do have the ability to change this, if we want; we don't need a full signature, for example; return type suffices.
    public static boolean hasMethodBytecode(String key) {
        boolean result = methodCache.containsKey(key);

        // TODO: This is a super helpful message as it tells us rather or not
        // we actually trampoline to AndroidEval. Remove this later once when
        // we are more confident that this always WAI.

        // TODO: This occasionally crashes with class not found. Not sure why yet.
        // Log.v("LiveEditStubs", "hasMethodBytecode(" + key + ") returns " + result);
        return result;
    }

    public static Object stubL(Class<?> clazz, Object[] parameters) {
        // First parameter is the class + method name + signature
        String methodKey = parameters[0].toString();
        int idx = methodKey.indexOf("->");
        String methodClassName = methodKey.substring(0, idx);
        String methodName = methodKey.substring(idx + 2, methodKey.indexOf("("));

        // Second parameter is the this pointer, or null if static
        Object thisObject = parameters[1];

        // Other parameters are the method arguments, if any
        Object[] arguments = new Object[parameters.length - 2];
        if (arguments.length > 0) {
            System.arraycopy(parameters, 2, arguments, 0, arguments.length);
        }

        byte[] dex = methodCache.get(methodKey);
        MethodBodyEvaluator evaluator =
                new MethodBodyEvaluator(dex, methodName, clazz.getClassLoader());
        return evaluator.eval(thisObject, methodClassName, arguments);
    }

    public static byte stubB(Class<?> clazz, Object[] parameters) {
        Object value = stubL(clazz, parameters);
        return value != null ? (byte) value : 0;
    }

    public static short stubS(Class<?> clazz, Object[] parameters) {
        Object value = stubL(clazz, parameters);
        return value != null ? (short) value : 0;
    }

    public static int stubI(Class<?> clazz, Object[] parameters) {
        Object value = stubL(clazz, parameters);
        return value != null ? (int) value : 0;
    }

    public static long stubJ(Class<?> clazz, Object[] parameters) {
        Object value = stubL(clazz, parameters);
        return value != null ? (long) value : 0;
    }

    public static float stubF(Class<?> clazz, Object[] parameters) {
        Object value = stubL(clazz, parameters);
        return value != null ? (float) value : 0;
    }

    public static double stubD(Class<?> clazz, Object[] parameters) {
        Object value = stubL(clazz, parameters);
        return value != null ? (double) value : 0;
    }

    public static boolean stubZ(Class<?> clazz, Object[] parameters) {
        Object value = stubL(clazz, parameters);
        return value != null ? (boolean) value : false;
    }

    public static char stubC(Class<?> clazz, Object[] parameters) {
        Object value = stubL(clazz, parameters);
        return value != null ? (char) value : 0;
    }

    public static void stubV(Class<?> clazz, Object[] parameters) {
        stubL(clazz, parameters);
    }
}

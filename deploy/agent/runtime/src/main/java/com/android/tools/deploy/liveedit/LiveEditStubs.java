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

package com.android.tools.deploy.liveedit;

import static com.android.tools.deploy.instrument.ReflectionHelpers.*;

import com.android.deploy.asm.ClassReader;
import java.util.HashSet;

@SuppressWarnings("unused") // Used by native instrumentation code.
public final class LiveEditStubs {
    private static final String TAG = "studio.deploy";

    // Currently set to true as a workaround to handle the changes the Compose compiler makes to the
    // signatures of functions that accept or return @Composable lambdas.
    private static final boolean INTERPRET_ALL = true;

    // Context object that holds all of LiveEdit's global state. Initialized by the first LiveEdit.
    private static LiveEditContext context = null;

    // Set of method keys used to determine if a given method should redirect to the interpreter.
    // TODO: This should live in LiveEditClass.
    private static final HashSet<String> interpretedMethods = new HashSet<>();

    // TODO: Figure out if we need to support multiple class loaders.
    public static void init(ClassLoader loader) {
        if (context == null) {
            context = new LiveEditContext(loader);
            Log.setLogger(new AndroidLogger());
        }
    }

    public static void addToCache(String key, byte[] bytecode) {
        String className = key.substring(0, key.indexOf("->"));
        context.addClass(className, bytecode);
        interpretedMethods.add(key);
    }

    public static void addProxiedClass(byte[] bytecode) {
        // TODO: This should be done on the host and needs to include the constructor descriptor.
        ClassReader reader = new ClassReader(bytecode);
        String className = reader.getClassName();
        String[] interfaces = reader.getInterfaces();
        context.addProxyClass(className, bytecode, interfaces);
    }

    // Everything in the following section is called from the dex prologue created by StubTransform.
    // None of this code is or should be called from any other context.

    // The key format is based on what slicer passes as the first parameter to an EntryHook
    // callback.
    // The format can be found in tools/slicer/instrumentation.cc in the MethodLabel method.
    // TODO: We need to centralize which LiveEdit component "owns" this key format.
    public static boolean shouldInterpretMethod(String key) {
        String internalKey = key.replace('.', '/');
        LiveEditClass clazz = context.getClass(internalKey.substring(0, internalKey.indexOf("->")));
        return INTERPRET_ALL
                || (clazz != null && clazz.isProxyClass())
                || interpretedMethods.contains(internalKey);
    }

    // TODO: We don't need to pass the class here; remove once the prologue is updated.
    public static Object stubL(Class<?> clazz, Object[] parameters) {
        // First parameter is the class + method name + signature
        String methodKey = parameters[0].toString().replace('.', '/');
        int idx = methodKey.indexOf("->");
        String methodClassName = methodKey.substring(0, idx);
        String methodName = methodKey.substring(idx + 2);

        // Second parameter is the this pointer, or null if static
        Object thisObject = parameters[1];

        // Other parameters are the method arguments, if any
        Object[] arguments = new Object[parameters.length - 2];
        if (arguments.length > 0) {
            System.arraycopy(parameters, 2, arguments, 0, arguments.length);
        }

        return context.getClass(methodClassName).invokeMethod(methodName, thisObject, arguments);
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

    private static class AndroidLogger implements Log.Logger {
        public void v(String tag, String message) {
            android.util.Log.v(tag, message);
        }
    }
}

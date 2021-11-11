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

import static java.lang.reflect.Proxy.newProxyInstance;

import java.util.HashMap;

class LiveEditClass {
    // The context this class is defined in.
    private final LiveEditContext context;

    private final String className;
    private final byte[] bytecode;

    private final boolean isProxyClass;
    private final String[] proxyInterfaces;

    // Whether or not <clinit> has been interpreted and run for this class.
    private boolean isInitialized;
    private final HashMap<String, Object> staticFields;

    public LiveEditClass(LiveEditContext context, String className, byte[] bytecode) {
        this(context, className, bytecode, false, null);
    }

    public LiveEditClass(
            LiveEditContext context, String className, byte[] bytecode, String[] proxyInterfaces) {
        this(context, className, bytecode, true, proxyInterfaces);
    }

    private LiveEditClass(
            LiveEditContext context,
            String className,
            byte bytecode[],
            boolean isProxyClass,
            String[] proxyInterfaces) {
        this.context = context;
        this.className = className;
        this.bytecode = bytecode;
        this.isProxyClass = isProxyClass;
        this.proxyInterfaces = proxyInterfaces;
        this.isInitialized = false;
        this.staticFields = new HashMap<>();
    }

    public boolean isProxyClass() {
        return isProxyClass;
    }

    public Object invokeMethod(String methodDescriptor, Object thisObject, Object[] arguments) {
        MethodBodyEvaluator evaluator =
                new MethodBodyEvaluator(context, bytecode, methodDescriptor);
        return evaluator.eval(thisObject, className, arguments);
    }

    // Returns a new proxy instance of the class that implements all the proxied class' interfaces.
    // Can only be called if the LiveEditClass instance is a proxiable class.
    public Object getProxy() {
        if (!isProxyClass) {
            throw new LiveEditException(
                    "Cannot create a proxy object for a non-proxy LiveEdit class");
        }
        Class<?>[] interfaceClasses = new Class<?>[proxyInterfaces.length + 1];
        interfaceClasses[0] = ProxyClass.class;
        try {
            for (int i = 1; i < interfaceClasses.length; ++i) {
                interfaceClasses[i] =
                        Class.forName(
                                proxyInterfaces[i - 1].replace('/', '.'),
                                true,
                                context.getClassLoader());
            }
        } catch (ClassNotFoundException cnfe) {
            throw new LiveEditException("Could not find interface class for proxy", cnfe);
        }

        ProxyClassHandler handler = new ProxyClassHandler(context, className);
        return newProxyInstance(context.getClassLoader(), interfaceClasses, handler);
    }

    public synchronized Object getStaticField(String fieldName) {
        ensureClinit();
        return staticFields.get(fieldName);
    }

    public synchronized void setStaticField(String fieldName, Object value) {
        ensureClinit();
        staticFields.put(fieldName, value);
    }

    private synchronized void ensureClinit() {
        if (!isProxyClass) {
            throw new LiveEditException("Cannot invoke <clinit> for non-proxy LiveEdit class");
        }
        if (!isInitialized) {
            isInitialized = true; // Must set this first to prevent infinite loops.
            invokeMethod("<clinit>()V", null, new Object[0]);
        }
    }
}

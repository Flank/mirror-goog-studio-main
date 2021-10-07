/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.deploy.liveedit;

import com.android.deploy.asm.Type;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;

final class ProxyClassHandler implements InvocationHandler {
    private final LiveEditContext context;
    private final String className;
    private final HashMap<String, Object> fields;

    ProxyClassHandler(LiveEditContext context, String className) {
        this.context = context;
        this.className = className;
        this.fields = new HashMap<>();
    }

    void setField(String name, Object value) {
        fields.put(name, value);
    }

    Object getField(String name) {
        return fields.get(name);
    }

    Object invokeMethod(Object instance, String name, String signature, Object[] args) {
        String descriptor = name + signature;
        return context.getClass(className).invokeMethod(descriptor, instance, args);
    }

    /**
     * This proxy method is invoked whenever a function literal instance that has "leaked" outside
     * of the context of the interpreter is invoked. It allows methods that have not been
     * instrumented to correctly call the proxy class as if it existed in the VM.
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Use this handler as the source for Object based methods, since using the proxy object
        // itself results in an infinite loop of this method being called.
        if (method.getDeclaringClass().equals(Object.class)) {
            return method.invoke(this, args);
        }

        // It's safe to pass the proxy object here, since it's serving as a 'this' pointer, not
        // having the specified method reflectively invoked on it.
        return invokeMethod(proxy, method.getName(), Type.getMethodDescriptor(method), args);
    }
}

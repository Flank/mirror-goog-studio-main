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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

// Handler class bound to an instance of a proxy class, responsible for resolving field access and
// method invocations. Holds the instance fields of the proxy object.
//
// Must be public; accessed cross-classloader from LiveEditSuspendLambda and
// LiveEditRestrictedSuspendLambda.
public final class ProxyClassHandler implements InvocationHandler {
    private final LiveEditClass clazz;
    private final HashMap<String, Object> fields;

    // Instance of the superclass for handling super method and field access.
    private Object superInstance;

    ProxyClassHandler(LiveEditClass clazz, Map<String, Object> defaultFieldValues) {
        this.clazz = clazz;
        this.fields = new HashMap<>();
        fields.putAll(defaultFieldValues);
    }

    void initSuperClass(String superInternalName, Object[] args, Object proxy) {
        try {
            Class<?> factory =
                    Class.forName(
                            "com.android.tools.deploy.liveedit.LambdaFactory",
                            true,
                            clazz.getClassLoader());
            superInstance =
                    factory.getDeclaredMethod("create", String.class, args.getClass(), Object.class)
                            .invoke(null, superInternalName, args, proxy);
        } catch (Exception e) {
            throw new LiveEditException("Could not instantiate superclass", e);
        }
    }

    void setField(String name, Object value) {
        if (fields.containsKey(name)) {
            fields.put(name, value);
            return;
        }
        Field superField = clazz.getSuperField(name);
        if (superField != null) {
            try {
                superField.set(superInstance, value);
                return;
            } catch (Exception e) {
                throw new LiveEditException("Could not access field", e);
            }
        }
        throw new LiveEditException("No such field '" + name + "' found in class");
    }

    Object getField(String name) {
        if (fields.containsKey(name)) {
            return fields.get(name);
        }
        Field superField = clazz.getSuperField(name);
        if (superField != null) {
            try {
                return superField.get(superInstance);
            } catch (Exception e) {
                throw new LiveEditException("Could not access field", e);
            }
        }
        throw new LiveEditException("No such field '" + name + "' found in class");
    }

    // Must be public; invoked cross-classloader from LiveEditSuspendLambda and
    // LiveEditRestrictedSuspendLambda.
    public Object invokeMethod(Object instance, String name, String desc, Object[] args) {
        if (clazz.declaresMethod(name, desc)) {
            return clazz.invokeDeclaredMethod(name, desc, instance, args);
        }
        Method superMethod = clazz.getSuperMethod(name, desc);
        if (superMethod != null) {
            try {
                return superMethod.invoke(superInstance, args);
            } catch (Exception e) {
                throw new LiveEditException("Could not invoke method '" + name + desc + "'", e);
            }
        }
        throw new LiveEditException(
                "No such method '" + name + desc + "' found in " + clazz.getClassInternalName());
    }

    boolean isInstanceOf(Type type) {
        return clazz.isInstanceOf(type);
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

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

import com.android.annotations.NonNull;
import com.android.deploy.asm.Type;
import com.android.tools.deploy.interpreter.FieldDescription;
import com.android.tools.deploy.interpreter.MethodDescription;
import com.android.tools.deploy.interpreter.ObjectValue;
import com.android.tools.deploy.interpreter.Value;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

class ProxyClassEval extends BackPorterEval {

    private final LiveEditContext context;

    public ProxyClassEval(LiveEditContext context) {
        super(context.getClassLoader());
        this.context = context;
    }

    @NonNull
    @Override
    public Value getField(@NonNull Value target, FieldDescription field) {
        if (target.obj() instanceof ProxyClass) {
            Log.v("live.deploy.lambda", "getField: " + field);
            ProxyClassHandler handler =
                    (ProxyClassHandler) Proxy.getInvocationHandler(target.obj());
            return makeValue(handler.getField(field.getName()), Type.getType(field.getDesc()));
        }

        return super.getField(target, field);
    }

    @Override
    public void setField(@NonNull Value target, FieldDescription field, Value value) {
        if (target.obj() instanceof ProxyClass) {
            Log.v("live.deploy.lambda", "setField: " + field);
            ProxyClassHandler handler =
                    (ProxyClassHandler) Proxy.getInvocationHandler(target.obj());
            handler.setField(field.getName(), value.obj());
        } else {
            super.setField(target, field, value);
        }
    }

    @NonNull
    @Override
    public Value getStaticField(FieldDescription field) {
        LiveEditClass clazz = context.getClass(field.getOwnerInternalName());
        if (isProxyClass(clazz)) {
            Log.v("live.deploy.lambda", "getStaticField: " + field);
            Object value = clazz.getStaticField(field.getName());
            Log.v("live.deploy.lambda", "\tTHE TYPE IS " + value);
            return makeValue(value, Type.getType(field.getDesc()));
        }

        return super.getStaticField(field);
    }

    @Override
    public void setStaticField(FieldDescription field, @NonNull Value value) {
        LiveEditClass clazz = context.getClass(field.getOwnerInternalName());
        if (isProxyClass(clazz)) {
            Log.v("live.deploy.lambda", "setStaticField: " + field);
            clazz.setStaticField(field.getName(), value.obj());
            Log.v("live.deploy.lambda", "\tTHE TYPE IS " + value.obj());
        } else {
            super.setStaticField(field, value);
        }
    }

    @NonNull
    @Override
    public Value invokeSpecial(
            @NonNull Value target, MethodDescription method, @NonNull List<? extends Value> args) {
        final String methodName = method.getName();
        final String methodDesc = method.getDesc();

        if (method.isConstructor()) {
            // We're calling a super() constructor, which LiveEdit doesn't support yet.
            if (target.obj() instanceof ProxyClass) {
                return Value.VOID_VALUE;
            }
            // If we're calling the constructor of a proxied class, set up a new proxy instance.
            LiveEditClass clazz = context.getClass(method.getOwnerInternalName());
            if (isProxyClass(clazz)) {
                Log.v("live.deploy.lambda", "invokeSpecial: " + method);

                ObjectValue objTarget = (ObjectValue) target;
                Object proxy = clazz.getProxy();
                invokeProxy(proxy, "<init>", methodDesc, args);
                objTarget.setValue(proxy);
                return Value.VOID_VALUE;
            }
        } else if (target.obj() instanceof ProxyClass) {
            Log.v("live.deploy.lambda", "invokeSpecial: " + method);
            Object result = invokeProxy(target.obj(), methodName, methodDesc, args);
            return makeValue(result, Type.getReturnType(methodDesc));
        }

        return super.invokeSpecial(target, method, args);
    }

    @NonNull
    @Override
    public Value invokeInterface(
            @NonNull Value target, MethodDescription method, @NonNull List<? extends Value> args) {
        final String methodName = method.getName();
        final String methodDesc = method.getDesc();

        if (target.obj() instanceof ProxyClass) {
            Log.v("live.deploy.lambda", "invokeInterface: " + method);
            Object result = invokeProxy(target.obj(), methodName, methodDesc, args);
            return makeValue(result, Type.getReturnType(methodDesc));
        }

        return super.invokeInterface(target, method, args);
    }

    @NonNull
    @Override
    public Value invokeMethod(
            @NonNull Value target, MethodDescription method, @NonNull List<? extends Value> args) {
        final String methodName = method.getName();
        final String methodDesc = method.getDesc();

        if (target.obj() instanceof ProxyClass) {
            Log.v("live.deploy.lambda", "invokeMethod: " + method);
            Object result = invokeProxy(target.obj(), methodName, methodDesc, args);
            return makeValue(result, Type.getReturnType(methodDesc));
        }

        return super.invokeMethod(target, method, args);
    }

    @NonNull
    @Override
    public Value invokeStaticMethod(MethodDescription method, @NonNull List<? extends Value> args) {
        final String internalName = method.getOwnerInternalName();
        final String methodName = method.getName();
        final String methodDesc = method.getDesc();

        LiveEditClass clazz = context.getClass(internalName);
        if (clazz == null) {
            return super.invokeStaticMethod(method, args);
        }

        Type[] parameterType = Type.getArgumentTypes(methodDesc);
        try {
            // If the method is a synthetic static added by Compose compiler, we must interpret it.
            // To detect these methods, we check if a given static method exists in the original,
            // and that it wasn't added by the user via a LiveEdit operation.
            Method originalMethod = methodLookup(internalName, methodName, parameterType);
            boolean isLiveEdited = clazz.hasLiveEditedMethod(methodName, methodDesc);
            if (originalMethod == null && !isLiveEdited) {
                Log.v("live.deploy.lambda", "invokeStaticMethod: " + method);

                Object[] argValues = new Object[args.size()];
                for (int i = 0; i < argValues.length; i++) {
                    argValues[i] = args.get(i).obj();
                }

                Object result = clazz.invokeMethod(methodName, methodDesc, null, argValues);
                return makeValue(result, Type.getReturnType(methodDesc));
            }
        } catch (ClassNotFoundException cnfe) {
            // Ignore; let AndroidEval handle it.
        }

        return super.invokeStaticMethod(method, args);
    }

    private static boolean isProxyClass(LiveEditClass clazz) {
        return clazz != null && clazz.isProxyClass();
    }

    private static Object invokeProxy(
            Object proxy, String methodName, String methodDesc, List<? extends Value> args) {
        ProxyClassHandler handler = (ProxyClassHandler) Proxy.getInvocationHandler(proxy);

        Object[] argValues = new Object[args.size()];
        for (int i = 0; i < argValues.length; i++) {
            argValues[i] = args.get(i).obj();
        }

        return handler.invokeMethod(proxy, methodName, methodDesc, argValues);
    }
}

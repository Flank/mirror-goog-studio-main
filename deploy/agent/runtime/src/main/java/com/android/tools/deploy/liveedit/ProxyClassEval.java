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
            Log.v("live.deploy.lambda", "\tfield_type=" + value.getClass());
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
            Log.v("live.deploy.lambda", "\tfield_type=" + value.obj().getClass());
        } else {
            super.setStaticField(field, value);
        }
    }

    @NonNull
    @Override
    public Value invokeSpecial(
            @NonNull Value target,
            MethodDescription method,
            @NonNull List<? extends Value> argsValues) {
        final String ownerInternalName = method.getOwnerInternalName();
        final String methodName = method.getName();
        final String methodDesc = method.getDesc();

        if (method.isConstructor()) {
            // We're calling a super() constructor.
            if (target.obj() instanceof ProxyClass) {
                Log.v("live.deploy.lambda", "invokeSpecial(targetProxy): " + method);

                Type[] parameterType = Type.getArgumentTypes(methodDesc);
                Object[] args = new Object[argsValues.size()];
                for (int i = 0; i < args.length; i++) {
                    args[i] = argsValues.get(i).obj(parameterType[i]);
                }

                ProxyClassHandler handler =
                        (ProxyClassHandler) Proxy.getInvocationHandler(target.obj());
                handler.initSuperClass(ownerInternalName, args, target.obj());

                return Value.VOID_VALUE;
            }
            // If we're calling the constructor of a proxied class, set up a new proxy instance.
            LiveEditClass clazz = context.getClass(ownerInternalName);
            if (isProxyClass(clazz)) {
                Log.v("live.deploy.lambda", "invokeSpecial(isProxy): " + method);

                ObjectValue objTarget = (ObjectValue) target;
                Object proxy = clazz.getProxy();
                invokeProxy(proxy, "<init>", methodDesc, argsValues);
                objTarget.setValue(proxy);
                return Value.VOID_VALUE;
            }
        } else if (target.obj() instanceof ProxyClass) {
            Log.v("live.deploy.lambda", "invokeSpecial(targetProxy): " + method);
            Object result = invokeProxy(target.obj(), methodName, methodDesc, argsValues);
            return makeValue(result, Type.getReturnType(methodDesc));
        }

        return super.invokeSpecial(target, method, argsValues);
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

        // We interpret *all* static methods of proxy classes.
        if (clazz.isProxyClass()) {
            Log.v("live.deploy.lambda", "invokeStaticMethod: " + method);
            Object result =
                    clazz.invokeDeclaredMethod(
                            methodName, methodDesc, null, valueToObj(args, methodDesc));
            return makeValue(result, Type.getReturnType(methodDesc));
        }

        try {
            // If the method is a synthetic static added by Compose compiler, we must interpret it.
            // To detect these methods, we check if a given static method exists in the original,
            // and that it wasn't added by the user via a LiveEdit operation.
            Type[] parameterTypes = Type.getArgumentTypes(methodDesc);
            Type returnType = Type.getReturnType(methodDesc);
            Method originalMethod =
                    methodLookup(internalName, methodName, parameterTypes, returnType);
            boolean isLiveEdited = clazz.hasLiveEditedMethod(methodName, methodDesc);
            if (originalMethod == null && !isLiveEdited) {
                Log.v("live.deploy.lambda", "invokeStaticMethod: " + method);
                Object result =
                        clazz.invokeDeclaredMethod(
                                methodName, methodDesc, null, valueToObj(args, methodDesc));
                return makeValue(result, Type.getReturnType(methodDesc));
            }
        } catch (ClassNotFoundException cnfe) {
            // Ignore; let AndroidEval handle it.
        }

        return super.invokeStaticMethod(method, args);
    }

    @Override
    public boolean isInstanceOf(@NonNull Value target, @NonNull Type type) {
        if (target.obj() instanceof ProxyClass) {
            ProxyClassHandler handler =
                    (ProxyClassHandler) Proxy.getInvocationHandler(target.obj());
            return handler.isInstanceOf(type);
        }
        return super.isInstanceOf(target, type);
    }

    private static boolean isProxyClass(LiveEditClass clazz) {
        return clazz != null && clazz.isProxyClass();
    }

    private static Object invokeProxy(
            Object proxy, String methodName, String methodDesc, List<? extends Value> args) {
        ProxyClassHandler handler = (ProxyClassHandler) Proxy.getInvocationHandler(proxy);
        return handler.invokeMethod(proxy, methodName, methodDesc, valueToObj(args, methodDesc));
    }

    private static Object[] valueToObj(List<? extends Value> args, String methodDesc) {
        Type[] argTypes = Type.getArgumentTypes(methodDesc);
        Object[] argValues = new Object[args.size()];
        for (int i = 0; i < argValues.length; i++) {
            argValues[i] = args.get(i).obj(argTypes[i]);
        }
        return argValues;
    }
}

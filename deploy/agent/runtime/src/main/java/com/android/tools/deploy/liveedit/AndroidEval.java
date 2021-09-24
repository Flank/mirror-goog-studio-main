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
import com.android.tools.deploy.interpreter.DoubleValue;
import com.android.tools.deploy.interpreter.Eval;
import com.android.tools.deploy.interpreter.FieldDescription;
import com.android.tools.deploy.interpreter.FloatValue;
import com.android.tools.deploy.interpreter.IntValue;
import com.android.tools.deploy.interpreter.LongValue;
import com.android.tools.deploy.interpreter.MethodDescription;
import com.android.tools.deploy.interpreter.ObjectValue;
import com.android.tools.deploy.interpreter.Value;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

class AndroidEval implements Eval {

    private final ClassLoader classloader;

    public AndroidEval(ClassLoader classloader) {
        this.classloader = classloader;
    }

    @NonNull
    @Override
    public Value getArrayElement(Value array, @NonNull Value index) {
        try {
            Type elementType = array.getAsmType().getElementType();
            switch (elementType.getSort()) {
                case Type.BOOLEAN:
                    boolean b = Array.getBoolean(array.obj(), index.getInt());
                    return new IntValue(b ? 1 : 0, Type.BOOLEAN_TYPE);
                case Type.CHAR:
                    return new IntValue(Array.getChar(array.obj(), index.getInt()), Type.CHAR_TYPE);
                case Type.BYTE:
                    return new IntValue(Array.getByte(array.obj(), index.getInt()), Type.BYTE_TYPE);
                case Type.SHORT:
                    return new IntValue(
                            Array.getShort(array.obj(), index.getInt()), Type.SHORT_TYPE);
                case Type.INT:
                    return new IntValue(Array.getInt(array.obj(), index.getInt()), Type.INT_TYPE);
                case Type.FLOAT:
                    return new FloatValue(Array.getFloat(array.obj(), index.getInt()));
                case Type.LONG:
                    return new LongValue(Array.getLong(array.obj(), index.getInt()));
                case Type.DOUBLE:
                    return new DoubleValue(Array.getDouble(array.obj(), index.getInt()));
                case Type.OBJECT:
                    return new ObjectValue(Array.get(array.obj(), index.getInt()), elementType);
                default:
                    String msg =
                            String.format(
                                    "getArrayElement undefined for type '%s' ",
                                    elementType.getClassName());
                    throw new ClassNotFoundException(msg);
            }
        } catch (ClassNotFoundException e) {
            handleThrowable(e);
        }
        throw new IllegalStateException();
    }

    @NonNull
    @Override
    public Value getArrayLength(@NonNull Value array) {
        return new IntValue(Array.getLength(array.obj()), Type.INT_TYPE);
    }

    @NonNull
    @Override
    public Value getField(@NonNull Value value, FieldDescription description) {
        Object owner = value.obj();
        String name = description.getName();
        String type = description.getDesc();
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return makeValue(field.get(owner), Type.getType(type));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            handleThrowable(e);
        }
        throw new IllegalStateException();
    }

    @NonNull
    @Override
    public Value getStaticField(FieldDescription description) {
        String owner = description.getOwnerInternalName().replace('/', '.');
        String name = description.getName();
        String type = description.getDesc();
        try {
            Field field = forName(owner).getDeclaredField(name);
            field.setAccessible(true);
            return makeValue(field.get(owner), Type.getType(type));
        } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
            handleThrowable(e);
        }
        throw new IllegalStateException();
    }

    @NonNull
    @Override
    public Value invokeMethod(
            @NonNull Value target,
            MethodDescription methodDesc,
            @NonNull List<? extends Value> args,
            boolean invokeSpecial) {
        String owner = methodDesc.getOwnerInternalName();
        String name = methodDesc.getName();
        String description = methodDesc.getDesc();
        Type[] parameterType = Type.getArgumentTypes(description);
        Class<?>[] parameterClass = new Class[parameterType.length];
        try {
            for (int i = 0; i < parameterClass.length; i++) {
                parameterClass[i] = typeToClass(parameterType[i]);
            }

            Object[] argValues = new Object[args.size()];
            for (int i = 0; i < argValues.length; i++) {
                argValues[i] = args.get(i).obj(parameterType[i]);
            }

            // This is a constructor call.
            if (invokeSpecial && "<init>".equals(name)) {
                ObjectValue objTarget = (ObjectValue) target;
                if (objTarget.getValue() != null) {
                    // This is a call to super.<init> which we currently not handle.
                    throw new IllegalStateException("Unable to do super.<init>");
                }
                Class klass = typeToClass(objTarget.getAsmType());
                Constructor constructor = klass.getDeclaredConstructor(parameterClass);
                constructor.setAccessible(true);
                Object obj = constructor.newInstance(argValues);
                objTarget.setValue(obj);
                return new ObjectValue(obj, objTarget.getAsmType());
            }

            // We use invokevirtual for everything else which is inaccurate for private methods
            // and super methods invocations.
            Method method = methodLookup(owner, name, parameterClass);
            if (method == null) {
                // Unlikely since we know that the class compiles.
                throw new IllegalStateException("Cannot find " + name + " in " + owner);
            }

            method.setAccessible(true);
            Object result = method.invoke(target.obj(), argValues);
            return makeValue(result, Type.getReturnType(method));
        } catch (Throwable t) {
            handleThrowable(t);
        }
        throw new IllegalStateException();
    }

    @NonNull
    @Override
    public Value invokeStaticMethod(
            MethodDescription description, @NonNull List<? extends Value> args) {
        String owner = description.getOwnerInternalName();
        String methodName = description.getName();
        String signature = description.getDesc();
        Type[] parameterType = Type.getArgumentTypes(signature);
        Class<?>[] parameterClass = new Class[parameterType.length];
        try {
            for (int i = 0; i < parameterClass.length; i++) {
                parameterClass[i] = typeToClass(parameterType[i]);
            }

            Object[] argValues = new Object[args.size()];
            for (int i = 0; i < argValues.length; i++) {
                argValues[i] = args.get(i).obj(parameterType[i]);
            }

            Method method = methodLookup(owner, methodName, parameterClass);
            if (method == null) {
                // Unlikely since we know that the class compiles.
                throw new IllegalStateException(
                        "Cannot find static " + methodName + " in " + owner);
            }

            method.setAccessible(true);
            Object result = method.invoke(null, argValues);
            return makeValue(result, Type.getReturnType(method));
        } catch (Exception e) {
            handleThrowable(e);
        }
        throw new IllegalStateException();
    }

    @Override
    public boolean isInstanceOf(@NonNull Value target, @NonNull Type type) {
        try {
            Class<?> c = typeToClass(type);
            return c.isInstance(target.obj());
        } catch (ClassNotFoundException e) {
            handleThrowable(e);
        }
        throw new IllegalStateException();
    }

    @NonNull
    @Override
    public Value loadClass(@NonNull Type type) {
        try {
            Class<?> c = typeToClass(type);
            return new ObjectValue(c, Type.getObjectType("java/lang/Class"));
        } catch (ClassNotFoundException e) {
            // TODO: This needs to surface to the user.
            handleThrowable(e);
        }
        throw new IllegalStateException();
    }

    @NonNull
    @Override
    public Value loadString(@NonNull String s) {
        return new ObjectValue(s, Type.getObjectType("java/lang/String"));
    }

    @NonNull
    @Override
    public Value newArray(Type type, int length) {
        try {
            Class<?> elementClass = typeToClass(type.getElementType());
            return makeValue(Array.newInstance(elementClass, length), type);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    @Override
    public Value newInstance(@NonNull Type type) {
        return new ObjectValue(null, type);
    }

    @NonNull
    @Override
    public Value newMultiDimensionalArray(Type type, List<Integer> dimensions) {
        try {
            Class<?> elementClass = typeToClass(type.getElementType());
            return makeValue(
                    Array.newInstance(elementClass, dimensions.stream().mapToInt(e -> e).toArray()),
                    type);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setArrayElement(Value array, Value index, @NonNull Value newValue) {
        try {
            Type elementType = array.getAsmType().getElementType();
            Object arrayObject = array.obj();
            int arrayIndex = index.getInt();

            switch (elementType.getSort()) {
                case Type.INT:
                    Array.setInt(arrayObject, arrayIndex, newValue.getInt());
                    break;
                case Type.BYTE:
                    Array.setByte(arrayObject, arrayIndex, (byte) newValue.getInt());
                    break;
                case Type.OBJECT:
                    Array.set(arrayObject, arrayIndex, ((ObjectValue) newValue).getValue());
                    break;
                case Type.SHORT:
                    Array.setShort(arrayObject, arrayIndex, (short) newValue.getInt());
                    break;
                case Type.CHAR:
                    Array.setChar(arrayObject, arrayIndex, (char) newValue.getInt());
                    break;
                case Type.BOOLEAN:
                    Array.setBoolean(arrayObject, arrayIndex, newValue.getBoolean());
                    break;
                case Type.LONG:
                    Array.setLong(arrayObject, arrayIndex, newValue.getLong());
                    break;
                case Type.FLOAT:
                    Array.setFloat(arrayObject, arrayIndex, newValue.getFloat());
                    break;
                case Type.DOUBLE:
                    Array.setDouble(arrayObject, arrayIndex, newValue.getDouble());
                    break;
                default:
                    String msg =
                            String.format(
                                    "setArrayElement undefined for type '%s' ",
                                    newValue.getAsmType());
                    throw new ClassNotFoundException(msg);
            }
            return;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        throw new IllegalStateException();
    }

    @Override
    public void setField(@NonNull Value owner, FieldDescription description, Value value) {
        String ownerClass = description.getOwnerInternalName().replace('/', '.');
        String name = description.getName();
        try {
            Field field = Class.forName(ownerClass).getDeclaredField(name);
            field.setAccessible(true);
            if (description.getDesc().equals("I")) {
                field.setInt(owner.obj(), ((IntValue) value).getValue());
            } else if (description.getDesc().equals("Z")) {
                field.setBoolean(owner.obj(), ((IntValue) value).getValue() == 1);
            } else if (description.getDesc().equals("B")) {
                field.setByte(owner.obj(), ((IntValue) value).getValue().byteValue());
            } else if (description.getDesc().equals("C")) {
                field.setChar(owner.obj(), (char) ((IntValue) value).getValue().intValue());
            } else if (description.getDesc().equals("S")) {
                field.setShort(owner.obj(), ((IntValue) value).getValue().shortValue());
            } else if (description.getDesc().equals("D")) {
                field.setDouble(owner.obj(), ((DoubleValue) value).getValue());
            } else if (description.getDesc().equals("F")) {
                field.setFloat(owner.obj(), ((FloatValue) value).getValue());
            } else if (description.getDesc().equals("J")) {
                field.setLong(owner.obj(), ((LongValue) value).getValue());
            } else {
                field.set(owner.obj(), value.obj());
            }
            return;
        } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
            handleThrowable(e);
        }
        throw new IllegalStateException();
    }

    @Override
    public void setStaticField(FieldDescription description, @NonNull Value value) {
        String ownerClassName = description.getOwnerInternalName().replace('/', '.');
        String name = description.getName();
        try {
            Class<?> ownerClass = forName(ownerClassName);
            Field field = ownerClass.getDeclaredField(name);
            field.setAccessible(true);
            if (description.getDesc().equals("I")) {
                field.setInt(ownerClass, ((IntValue) value).getValue());
            } else if (description.getDesc().equals("Z")) {
                field.setBoolean(ownerClass, ((IntValue) value).getValue() == 1);
            } else if (description.getDesc().equals("B")) {
                field.setByte(ownerClass, ((IntValue) value).getValue().byteValue());
            } else if (description.getDesc().equals("C")) {
                field.setChar(ownerClass, (char) ((IntValue) value).getValue().intValue());
            } else if (description.getDesc().equals("S")) {
                field.setShort(ownerClass, ((IntValue) value).getValue().shortValue());
            } else if (description.getDesc().equals("D")) {
                field.setDouble(ownerClass, ((DoubleValue) value).getValue());
            } else if (description.getDesc().equals("F")) {
                field.setFloat(ownerClass, ((FloatValue) value).getValue());
            } else if (description.getDesc().equals("J")) {
                field.setLong(ownerClass, ((LongValue) value).getValue());
            } else {
                field.set(ownerClass, value.obj());
            }
            return;
        } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
            handleThrowable(e);
        }
        throw new IllegalStateException();
    }

    public static Value makeValue(Object v, Type type) {
        switch (type.getSort()) {
            case Type.INT:
                return new IntValue((Integer) v, type);
            case Type.BOOLEAN:
                return new IntValue((Boolean) v ? 1 : 0, type);
            case Type.BYTE:
                return new IntValue(((Byte) v).intValue(), type);
            case Type.SHORT:
                return new IntValue(((Short) v).intValue(), type);
            case Type.CHAR:
                return new IntValue((Character) v, type);
            case Type.LONG:
                return new LongValue((Long) v);
            case Type.FLOAT:
                return new FloatValue((Float) v);
            case Type.DOUBLE:
                return new DoubleValue((Double) v);
            case Type.VOID:
                return new IntValue(0, type);
            default:
                return new ObjectValue(v, type);
        }
    }

    Class<?> forName(String className) throws ClassNotFoundException {
        return Class.forName(className.replace('/', '.'), true, classloader);
    }

    public Class<?> typeToClass(Type type) throws ClassNotFoundException {
        switch (type.getSort()) {
            case Type.INT:
                return int.class;
            case Type.BOOLEAN:
                return boolean.class;
            case Type.BYTE:
                return byte.class;
            case Type.SHORT:
                return short.class;
            case Type.CHAR:
                return char.class;
            case Type.LONG:
                return long.class;
            case Type.FLOAT:
                return float.class;
            case Type.DOUBLE:
                return double.class;
            case Type.VOID:
                return void.class;
            case Type.ARRAY:
                return forName(type.getDescriptor());
            default:
                return forName(type.getClassName());
        }
    }

    private Method methodLookup(String className, String methodName, Class[] parameterClass)
            throws ClassNotFoundException {
        Method method = null;
        Class curClass = forName(className.replace('/', '.'));
        while (curClass != null) {
            try {
                method = curClass.getDeclaredMethod(methodName, parameterClass);
                break;
            } catch (NoSuchMethodException e) {
                curClass = curClass.getSuperclass();
            }
        }
        return method;
    }

    /**
     * A placeholder to all the exceptions that needs to be dealt with.
     *
     * <p>Before shipping an MVP, this method should be removed and all exceptions be properly
     * handled by either logging or displaying to the user.
     */
    private static void handleThrowable(Throwable t) {
        throw new RuntimeException(t);
    }
}

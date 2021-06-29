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

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.jetbrains.eval4j.AbstractValue;
import org.jetbrains.eval4j.DoubleValue;
import org.jetbrains.eval4j.Eval;
import org.jetbrains.eval4j.FieldDescription;
import org.jetbrains.eval4j.FloatValue;
import org.jetbrains.eval4j.IntValue;
import org.jetbrains.eval4j.LongValue;
import org.jetbrains.eval4j.MethodDescription;
import org.jetbrains.eval4j.ObjectValue;
import org.jetbrains.eval4j.Value;
import org.jetbrains.eval4j.ValuesKt;
import org.jetbrains.org.objectweb.asm.Type;

class AndroidEval implements Eval {

    private final ClassLoader classloader;

    public AndroidEval(ClassLoader classloader) {
        this.classloader = classloader;
    }

    @Override
    public Value getArrayElement(Value array, Value index) {
        try {
            Type elementType = array.getAsmType().getElementType();
            switch (elementType.getSort()) {
                case Type.BOOLEAN:
                    boolean b = Array.getBoolean(valueToObject(array), ValuesKt.getInt(index));
                    return new IntValue(b ? 1 : 0, Type.BOOLEAN_TYPE);
                case Type.CHAR:
                    return new IntValue(
                            Array.getChar(valueToObject(array), ValuesKt.getInt(index)),
                            Type.CHAR_TYPE);
                case Type.BYTE:
                    return new IntValue(
                            Array.getByte(valueToObject(array), ValuesKt.getInt(index)),
                            Type.BYTE_TYPE);
                case Type.SHORT:
                    return new IntValue(
                            Array.getShort(valueToObject(array), ValuesKt.getInt(index)),
                            Type.SHORT_TYPE);
                case Type.INT:
                    return new IntValue(
                            Array.getInt(valueToObject(array), ValuesKt.getInt(index)),
                            Type.INT_TYPE);
                case Type.FLOAT:
                    return new FloatValue(
                            Array.getFloat(valueToObject(array), ValuesKt.getInt(index)));
                case Type.LONG:
                    return new LongValue(
                            Array.getLong(valueToObject(array), ValuesKt.getInt(index)));
                case Type.DOUBLE:
                    return new DoubleValue(
                            Array.getDouble(valueToObject(array), ValuesKt.getInt(index)));
                case Type.OBJECT:
                    return new ObjectValue(
                            Array.get(valueToObject(array), ValuesKt.getInt(index)), elementType);
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

    @Override
    public Value getArrayLength(Value array) {
        return new IntValue(Array.getLength(valueToObject(array)), Type.INT_TYPE);
    }

    @Override
    public Value getField(Value value, FieldDescription description) {
        Object owner = valueToObject(value);
        String name = description.getName();
        String type = description.getDesc();
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Value result = makeValue(field.get(owner), Type.getType(type));
            return result;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            handleThrowable(e);
        }
        throw new IllegalStateException();
    }

    @Override
    public Value getStaticField(FieldDescription description) {
        String owner = description.getOwnerInternalName().replace('/', '.');
        String name = description.getName();
        String type = description.getDesc();
        try {
            Field field = forName(owner).getDeclaredField(name);
            field.setAccessible(true);
            Value result = makeValue(field.get(owner), Type.getType(type));
            return result;
        } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
            handleThrowable(e);
        }
        throw new IllegalStateException();
    }

    // Temporary HACK since eval4j's AbstractValue field "value" is private.
    // Either patch eval4j, or use our own interpreter.
    private void setObjectValue(ObjectValue target, Object value)
            throws NoSuchFieldException, IllegalAccessException {
        Field f = AbstractValue.class.getDeclaredField("value");
        f.setAccessible(true);
        f.set(target, value);
    }

    @Override
    public Value invokeMethod(
            Value target,
            MethodDescription methodDesc,
            List<? extends Value> args,
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
                Object obj =
                        constructor.newInstance(
                                args.stream().map(AndroidEval::valueToObject).toArray());
                setObjectValue(objTarget, obj);
                return new ObjectValue(obj, objTarget.getAsmType());
            }

            // We use invokevirtual for everything else which is inaccurate for private methods
            // and super methods invocations.
            Method method =
                    forName(owner.replace('/', '.')).getDeclaredMethod(name, parameterClass);
            method.setAccessible(true);
            Object result =
                    method.invoke(
                            valueToObject(target),
                            args.stream().map(AndroidEval::valueToObject).toArray());
            return makeValue(result, Type.getReturnType(method));
        } catch (Throwable t) {
            handleThrowable(t);
        }
        throw new IllegalStateException();
    }

    @Override
    public Value invokeStaticMethod(MethodDescription description, List<? extends Value> list) {
        String owner = description.getOwnerInternalName();
        String methodName = description.getName();
        String signature = description.getDesc();
        Type[] parameterType = Type.getArgumentTypes(signature);
        Class<?>[] parameterClass = new Class[parameterType.length];
        try {
            for (int i = 0; i < parameterClass.length; i++) {
                parameterClass[i] = typeToClass(parameterType[i]);
            }
            Method method =
                    Class.forName(owner.replace('/', '.'))
                            .getDeclaredMethod(methodName, parameterClass);
            method.setAccessible(true);
            Object result =
                    method.invoke(null, list.stream().map(AndroidEval::valueToObject).toArray());
            return makeValue(result, Type.getReturnType(method));
        } catch (Exception e) {
            handleThrowable(e);
        }
        throw new IllegalStateException();
    }

    @Override
    public boolean isInstanceOf(Value target, Type type) {
        try {
            Class<?> c = typeToClass(type);
            return c.isInstance(valueToObject(target));
        } catch (ClassNotFoundException e) {
            handleThrowable(e);
        }
        throw new IllegalStateException();
    }

    @Override
    public Value loadClass(Type type) {
        try {
            Class<?> c = typeToClass(type);
            return new ObjectValue(c, Type.getObjectType("java/lang/Class"));
        } catch (ClassNotFoundException e) {
            // TODO: This needs to surface to the user.
            handleThrowable(e);
        }
        throw new IllegalStateException();
    }

    @Override
    public Value loadString(String s) {
        return new ObjectValue(s, Type.getObjectType("java/lang/String"));
    }

    @Override
    public Value newArray(Type type, int length) {
        try {
            Class<?> elementClass = typeToClass(type.getElementType());
            return makeValue(Array.newInstance(elementClass, length), type);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Value newInstance(Type type) {
        return new ObjectValue(null, type);
    }

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
    public void setArrayElement(Value array, Value index, Value newValue) {
        try {
            Type elementType = array.getAsmType().getElementType();
            Object arrayObject = valueToObject(array);
            int arrayIndex = ValuesKt.getInt(index);

            switch (elementType.getSort()) {
                case Type.INT:
                    Array.setInt(arrayObject, arrayIndex, ValuesKt.getInt(newValue));
                    break;
                case Type.BYTE:
                    Array.setByte(arrayObject, arrayIndex, (byte) ValuesKt.getInt(newValue));
                    break;
                case Type.OBJECT:
                    Array.set(arrayObject, arrayIndex, ((ObjectValue) newValue).getValue());
                    break;
                case Type.SHORT:
                    Array.setShort(arrayObject, arrayIndex, (short) ValuesKt.getInt(newValue));
                    break;
                case Type.CHAR:
                    Array.setChar(arrayObject, arrayIndex, (char) ValuesKt.getInt(newValue));
                    break;
                case Type.BOOLEAN:
                    Array.setBoolean(arrayObject, arrayIndex, ValuesKt.getBoolean(newValue));
                    break;
                case Type.LONG:
                    Array.setLong(arrayObject, arrayIndex, ValuesKt.getLong(newValue));
                    break;
                case Type.FLOAT:
                    Array.setFloat(arrayObject, arrayIndex, ValuesKt.getFloat(newValue));
                    break;
                case Type.DOUBLE:
                    Array.setDouble(arrayObject, arrayIndex, ValuesKt.getDouble(newValue));
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
    public void setField(Value owner, FieldDescription description, Value value) {
        String ownerClass = description.getOwnerInternalName().replace('/', '.');
        String name = description.getName();
        try {
            Field field = Class.forName(ownerClass).getDeclaredField(name);
            field.setAccessible(true);
            if (description.getDesc().equals("I")) {
                field.setInt(valueToObject(owner), ((IntValue) value).getValue());
            } else if (description.getDesc().equals("Z")) {
                field.setBoolean(
                        valueToObject(owner), ((IntValue) value).getValue() == 1 ? true : false);
            } else if (description.getDesc().equals("B")) {
                field.setByte(valueToObject(owner), ((IntValue) value).getValue().byteValue());
            } else if (description.getDesc().equals("C")) {
                field.setChar(
                        valueToObject(owner), (char) ((IntValue) value).getValue().intValue());
            } else if (description.getDesc().equals("S")) {
                field.setShort(valueToObject(owner), ((IntValue) value).getValue().shortValue());
            } else if (description.getDesc().equals("D")) {
                field.setDouble(valueToObject(owner), ((DoubleValue) value).getValue());
            } else if (description.getDesc().equals("F")) {
                field.setFloat(valueToObject(owner), ((FloatValue) value).getValue());
            } else if (description.getDesc().equals("J")) {
                field.setLong(valueToObject(owner), ((LongValue) value).getValue());
            } else {
                field.set(valueToObject(owner), valueToObject(value));
            }
            return;
        } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
            handleThrowable(e);
        }
        throw new IllegalStateException();
    }

    @Override
    public void setStaticField(FieldDescription description, Value value) {
        String ownerClassName = description.getOwnerInternalName().replace('/', '.');
        String name = description.getName();
        try {
            Class<?> ownerClass = forName(ownerClassName);
            Field field = ownerClass.getDeclaredField(name);
            field.setAccessible(true);
            if (description.getDesc().equals("I")) {
                field.setInt(ownerClass, ((IntValue) value).getValue());
            } else if (description.getDesc().equals("Z")) {
                field.setBoolean(ownerClass, ((IntValue) value).getValue() == 1 ? true : false);
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
                field.set(ownerClass, valueToObject(value));
            }
            return;
        } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
            handleThrowable(e);
        }
        throw new IllegalStateException();
    }

    public static Object valueToObject(Value v) {
        return (ValuesKt.obj(v, v.getAsmType()));
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

    Class<?> forName(Type type) throws ClassNotFoundException {
        return forName(type.getClassName());
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
            default:
                return forName(type);
        }
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

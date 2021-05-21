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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
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

    @Override
    public Value getArrayElement(Value array, Value index) {
        try {
            Type elementType = array.getAsmType().getElementType();
            Class element = typeToClass(elementType);
            if (element.isPrimitive()) {
                if (elementType == Type.BOOLEAN_TYPE) {
                    return new IntValue(
                            Array.getBoolean(valueToObject(array), ValuesKt.getInt(index)) ? 1 : 0,
                            Type.BOOLEAN_TYPE);
                } else if (elementType == Type.CHAR_TYPE) {
                    return new IntValue(
                            Array.getChar(valueToObject(array), ValuesKt.getInt(index)),
                            Type.CHAR_TYPE);
                } else if (elementType == Type.BYTE_TYPE) {
                    return new IntValue(
                            Array.getByte(valueToObject(array), ValuesKt.getInt(index)),
                            Type.BYTE_TYPE);
                } else if (elementType == Type.SHORT_TYPE) {
                    return new IntValue(
                            Array.getShort(valueToObject(array), ValuesKt.getInt(index)),
                            Type.SHORT_TYPE);
                } else if (elementType == Type.INT_TYPE) {
                    return new IntValue(
                            Array.getInt(valueToObject(array), ValuesKt.getInt(index)),
                            Type.INT_TYPE);
                } else if (elementType == Type.FLOAT_TYPE) {
                    return new FloatValue(
                            Array.getFloat(valueToObject(array), ValuesKt.getInt(index)));
                } else if (elementType == Type.LONG_TYPE) {
                    return new FloatValue(
                            Array.getLong(valueToObject(array), ValuesKt.getInt(index)));
                } else if (elementType == Type.DOUBLE_TYPE) {
                    return new DoubleValue(
                            Array.getDouble(valueToObject(array), ValuesKt.getInt(index)));
                } else {
                    return new ObjectValue(
                            Array.get(valueToObject(array), ValuesKt.getInt(index)), elementType);
                }
            }
        } catch (ClassNotFoundException e) {
            handleException(e);
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
            boolean accessible = field.isAccessible();
            field.setAccessible(true);
            Value result = objectToValueWithUnboxing(field.get(owner), Type.getType(type));
            field.setAccessible(accessible);
            return result;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            handleException(e);
        }
        throw new IllegalStateException();
    }

    @Override
    public Value getStaticField(FieldDescription description) {
        String owner = description.getOwnerInternalName().replace('/', '.');
        String name = description.getName();
        String type = description.getDesc();
        try {
            Field field = Class.forName(owner).getDeclaredField(name);
            boolean accessible = field.isAccessible();
            field.setAccessible(true);
            Value result = objectToValueWithUnboxing(field.get(owner), Type.getType(type));
            field.setAccessible(accessible);
            return result;
        } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
            handleException(e);
        }
        throw new IllegalStateException();
    }

    @Override
    public Value invokeMethod(
            Value target,
            MethodDescription description,
            List<? extends Value> list,
            boolean invokeSpecial) {
        String owner = description.getOwnerInternalName();
        String methodName = description.getName();
        String signature = description.getDesc();
        Type[] parameterType = Type.getArgumentTypes(signature);
        Class<?>[] parameterClass = new Class[parameterType.length];
        try {
            for (int i = 0; i < parameterClass.length; i++) {
                parameterClass[i] =
                        Class.forName(parameterType[i].getClassName().replace('/', '.'));
            }
            Method method =
                    Class.forName(owner.replace('/', '.'))
                            .getDeclaredMethod(methodName, parameterClass);
            method.setAccessible(true);
            Object result =
                    method.invoke(
                            valueToObject(target),
                            list.stream().map(AndroidEval::valueToObject).toArray());
            return objectToValueWithUnboxing(result, Type.getReturnType(method));
        } catch (Exception e) {
            handleException(e);
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
                parameterClass[i] =
                        Class.forName(parameterType[i].getClassName().replace('/', '.'));
            }
            Method method =
                    Class.forName(owner.replace('/', '.'))
                            .getDeclaredMethod(methodName, parameterClass);
            method.setAccessible(true);
            Object result =
                    method.invoke(null, list.stream().map(AndroidEval::valueToObject).toArray());
            return objectToValueWithUnboxing(result, Type.getReturnType(method));
        } catch (Exception e) {
            handleException(e);
        }
        throw new IllegalStateException();
    }

    @Override
    public boolean isInstanceOf(Value target, Type type) {
        try {
            Class<?> c = typeToClass(type);
            c.isInstance(valueToObject(target));
        } catch (ClassNotFoundException e) {
            handleException(e);
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
            handleException(e);
        }
        throw new IllegalStateException();
    }

    @Override
    public Value loadString(String s) {
        return new ObjectValue(s, Type.getObjectType("java/lang/String"));
    }

    @Override
    public Value newArray(Type type, int i) {
        try {
            Class<?> elementClass = typeToClass(type.getElementType());
            return objectToValueWithUnboxing(Array.newInstance(elementClass, i), type);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Value newInstance(Type type) {
        String owner = type.getClassName().replace('/', '.');
        try {
            return objectToValueWithUnboxing(Class.forName(owner).newInstance(), type);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Value newMultiDimensionalArray(Type type, List<Integer> dimensions) {
        try {
            Class<?> elementClass = typeToClass(type.getElementType());
            return objectToValueWithUnboxing(
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
            Class<?> element = typeToClass(elementType);
            if (element.isPrimitive()) {
                if (elementType == Type.INT_TYPE) {
                    Array.setInt(
                            valueToObject(array),
                            ValuesKt.getInt(index),
                            ValuesKt.getInt(newValue));
                    return;
                }
            }
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
            boolean accessible = field.isAccessible();
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
            field.setAccessible(accessible);
            return;
        } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
            handleException(e);
        }
        throw new IllegalStateException();
    }

    @Override
    public void setStaticField(FieldDescription description, Value value) {
        String ownerClassName = description.getOwnerInternalName().replace('/', '.');
        String name = description.getName();
        try {
            Class<?> ownerClass = Class.forName(ownerClassName);
            Field field = ownerClass.getDeclaredField(name);
            boolean accessible = field.isAccessible();
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
            field.setAccessible(accessible);
            return;
        } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
            handleException(e);
        }
        throw new IllegalStateException();
    }

    public static Object valueToObject(Value v) {
        return (ValuesKt.obj(v, v.getAsmType()));
    }

    public static Value objectToValueWithUnboxing(Object v, Type type) {
        if (type == Type.INT_TYPE) {
            return new IntValue((Integer) v, Type.INT_TYPE);
        } else {
            return new ObjectValue(v, type);
        }
    }

    public static Class<?> typeToClass(Type type) throws ClassNotFoundException {
        if (type == Type.INT_TYPE) {
            return int.class;
        } else {
            return Class.forName(type.getClassName().replace('/', '.'));
        }
    }

    /**
     * A placeholder to all the exceptions that needs to be dealt with.
     *
     * <p>Before shipping an MVP, this method should be removed and all exceptions be properly
     * handled by either logging or displaying to the user.
     */
    private static void handleException(Exception e) {
        throw new RuntimeException(e);
    }
}

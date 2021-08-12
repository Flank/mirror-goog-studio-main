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

package com.android.tools.deploy.interpreter;

import com.android.annotations.NonNull;
import org.jetbrains.org.objectweb.asm.Type;

public abstract class Value implements org.jetbrains.org.objectweb.asm.tree.analysis.Value {

    protected Type asmType;
    protected boolean valid;

    protected Value(@NonNull Type asmType, boolean valid) {
        this.asmType = asmType;
        this.valid = valid;
    }

    @Override
    public int getSize() {
        return asmType.getSize();
    }

    @NonNull
    @Override
    public abstract String toString();

    @NonNull
    public Type getAsmType() {
        return asmType;
    }

    public boolean isValid() {
        return valid;
    }

    public int getInt() {
        IntValue v = (IntValue) this;
        return v.value;
    }

    public long getLong() {
        LongValue v = (LongValue) this;
        return v.value;
    }

    public float getFloat() {
        FloatValue v = (FloatValue) this;
        return v.value;
    }

    public double getDouble() {
        DoubleValue v = (DoubleValue) this;
        return v.value;
    }

    public boolean getBoolean() {
        return getInt() == 1;
    }

    public Object obj() {
        return obj(asmType);
    }

    public Object obj(Type expectedType) {
        if (expectedType == Type.BOOLEAN_TYPE) return getBoolean();
        if (expectedType == Type.SHORT_TYPE) return (short) getInt();
        if (expectedType == Type.BYTE_TYPE) return (byte) getInt();
        if (expectedType == Type.CHAR_TYPE) return (char) getInt();
        return ((AbstractValue) this).value;
    }

    public static Value NOT_A_VALUE =
            new Value(Type.getObjectType("<invalid>"), false) {
                @Override
                public String toString() {
                    return "NOT_A_VALUE";
                }

                @Override
                public int getSize() {
                    return 1;
                }
            };

    public static Value VOID_VALUE =
            new Value(Type.VOID_TYPE, false) {
                @Override
                public String toString() {
                    return "VOID_VALUE";
                }
            };
}

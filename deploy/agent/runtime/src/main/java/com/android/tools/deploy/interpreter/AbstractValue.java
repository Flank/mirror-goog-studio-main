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
import com.android.annotations.Nullable;
import java.util.Objects;
import org.jetbrains.org.objectweb.asm.Type;

public class AbstractValue<T> extends Value {

    protected T value;

    protected AbstractValue(@Nullable T value, @NonNull Type asmType) {
        super(asmType, true);
        this.value = value;
    }

    @Override
    public String toString() {
        return value + ": " + asmType;
    }

    @NonNull
    public T getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof AbstractValue)) {
            return false;
        }

        AbstractValue v = (AbstractValue) other;
        return Objects.equals(value, v.value) && Objects.equals(asmType, v.asmType);
    }

    public int hashCode() {
        return value.hashCode() + 17 * asmType.hashCode();
    }
}

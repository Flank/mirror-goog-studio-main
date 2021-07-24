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
import java.util.List;
import org.jetbrains.org.objectweb.asm.Type;

public interface Eval {

    @NonNull
    Value loadClass(@NonNull Type classType);

    @NonNull
    Value loadString(@NonNull String str);

    @NonNull
    Value newInstance(@NonNull Type classType);

    boolean isInstanceOf(@NonNull Value value, @NonNull Type targetType);

    @NonNull
    Value newArray(@NonNull Type arrayType, int size);

    @NonNull
    Value newMultiDimensionalArray(@NonNull Type arrayType, @NonNull List<Integer> dimensionSizes);

    @NonNull
    Value getArrayLength(@NonNull Value array);

    @NonNull
    Value getArrayElement(@NonNull Value array, @NonNull Value index);

    void setArrayElement(@NonNull Value array, @NonNull Value index, @NonNull Value newValue);

    @NonNull
    Value getStaticField(@NonNull FieldDescription fieldDesc);

    void setStaticField(@NonNull FieldDescription fieldDesc, @NonNull Value newValue);

    @NonNull
    Value invokeStaticMethod(
            @NonNull MethodDescription methodDesc, @NonNull List<? extends Value> arguments);

    @NonNull
    Value getField(@NonNull Value instance, @NonNull FieldDescription fieldDesc);

    void setField(@NonNull Value instance, FieldDescription fieldDesc, Value newValue);

    @NonNull
    Value invokeMethod(
            @NonNull Value instance,
            @NonNull MethodDescription methodDesc,
            @NonNull List<? extends Value> arguments,
            boolean invokeSpecial);
}

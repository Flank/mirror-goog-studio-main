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
import com.android.tools.deploy.interpreter.Eval;
import com.android.tools.deploy.interpreter.FieldDescription;
import com.android.tools.deploy.interpreter.MethodDescription;
import com.android.tools.deploy.interpreter.Value;
import java.util.List;

class LoggingEval implements Eval {
    private Eval receiver;

    public LoggingEval(Eval receiver) {
        this.receiver = receiver;
    }

    @NonNull
    @Override
    public Value newArray(Type type, int length) {
        Log.v("live.deploy", "newArray: " + type + "[" + length + "]");
        return receiver.newArray(type, length);
    }

    @NonNull
    @Override
    public Value newMultiDimensionalArray(Type type, List<Integer> dimensions) {
        return receiver.newMultiDimensionalArray(type, dimensions);
    }

    @NonNull
    @Override
    public Value getArrayElement(Value array, @NonNull Value index) {
        Log.v("live.deploy", "getArrayElement: " + index);
        return receiver.getArrayElement(array, index);
    }

    @Override
    public void setArrayElement(Value array, Value index, @NonNull Value newValue) {
        Log.v("live.deploy", "setArrayElement: " + index);
        receiver.setArrayElement(array, index, newValue);
    }

    @NonNull
    @Override
    public Value getArrayLength(@NonNull Value array) {
        Log.v("live.deploy", "getArrayLength");
        return receiver.getArrayLength(array);
    }

    @NonNull
    @Override
    public Value getField(@NonNull Value value, FieldDescription description) {
        Log.v("live.deploy", "getField: " + description);
        return receiver.getField(value, description);
    }

    @NonNull
    @Override
    public Value getStaticField(FieldDescription description) {
        Log.v("live.deploy", "getStaticField: " + description);
        return receiver.getStaticField(description);
    }

    @Override
    public void setField(@NonNull Value owner, FieldDescription description, Value value) {
        Log.v("live.deploy", "setField: " + description);
        receiver.setField(owner, description, value);
    }

    @Override
    public void setStaticField(FieldDescription description, @NonNull Value value) {
        Log.v("live.deploy", "setStaticField: " + description);
        receiver.setStaticField(description, value);
    }

    @NonNull
    @Override
    public Value invokeMethod(
            @NonNull Value target,
            MethodDescription methodDesc,
            @NonNull List<? extends Value> args) {
        Log.v("live.deploy", "invokeMethod: " + methodDesc);
        return receiver.invokeMethod(target, methodDesc, args);
    }

    @NonNull
    @Override
    public Value invokeStaticMethod(
            MethodDescription description, @NonNull List<? extends Value> args) {
        Log.v("live.deploy", "invokeStaticMethod: " + description);
        return receiver.invokeStaticMethod(description, args);
    }

    @NonNull
    @Override
    public Value invokeSpecial(
            @NonNull Value target,
            MethodDescription methodDesc,
            @NonNull List<? extends Value> args) {
        Log.v("live.deploy", "invokeSpecial: " + methodDesc);
        return receiver.invokeSpecial(target, methodDesc, args);
    }

    @Override
    public boolean isInstanceOf(@NonNull Value target, @NonNull Type type) {
        return receiver.isInstanceOf(target, type);
    }

    @NonNull
    @Override
    public Value loadClass(@NonNull Type type) {
        return receiver.loadClass(type);
    }

    @NonNull
    @Override
    public Value loadString(@NonNull String s) {
        return receiver.loadString(s);
    }

    @NonNull
    @Override
    public Value newInstance(@NonNull Type type) {
        return receiver.newInstance(type);
    }

    @Override
    public void monitorEnter(@NonNull Value value) {
        receiver.monitorEnter(value);
    }

    @Override
    public void monitorExit(@NonNull Value value) {
        receiver.monitorExit(value);
    }
}

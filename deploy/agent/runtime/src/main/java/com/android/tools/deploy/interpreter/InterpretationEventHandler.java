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
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode;
import org.jetbrains.org.objectweb.asm.tree.analysis.Frame;

public interface InterpretationEventHandler {

    // If a non-null value is returned, interpreter loop is terminated and that value is used as a
    // result
    @Nullable
    InterpreterResult instructionProcessed(@NonNull AbstractInsnNode inst);

    @Nullable
    InterpreterResult exceptionThrown(
            @NonNull Frame<Value> currentState,
            @NonNull AbstractInsnNode currentIns,
            @NonNull Value exception);

    @Nullable
    InterpreterResult exceptionCaught(
            @NonNull Frame<Value> currentState,
            @NonNull AbstractInsnNode currentIns,
            @NonNull Value exception);

    public InterpretationEventHandler NONE =
            new InterpretationEventHandler() {
                @Nullable
                @Override
                public InterpreterResult instructionProcessed(@NonNull AbstractInsnNode inst) {
                    return null;
                }

                @Nullable
                @Override
                public InterpreterResult exceptionThrown(
                        @NonNull Frame<Value> currentState,
                        @NonNull AbstractInsnNode currentIns,
                        @NonNull Value exception) {
                    return null;
                }

                @Nullable
                @Override
                public InterpreterResult exceptionCaught(
                        @NonNull Frame<Value> currentState,
                        @NonNull AbstractInsnNode currentIns,
                        @NonNull Value exception) {
                    return null;
                }
            };
}

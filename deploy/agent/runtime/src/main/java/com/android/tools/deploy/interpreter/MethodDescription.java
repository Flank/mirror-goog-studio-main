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
import com.android.deploy.asm.tree.MethodInsnNode;

public class MethodDescription {
    private final String ownerInternalName;
    private final String name;
    private final String desc;

    public static final MethodDescription EMPTY = new MethodDescription("", "", "");

    public MethodDescription(
            @NonNull String ownerInternalName, @NonNull String name, @NonNull String desc) {
        this.ownerInternalName = ownerInternalName;
        this.name = name;
        this.desc = desc;
    }

    public MethodDescription(@NonNull MethodInsnNode insn) {
        this(insn.owner, insn.name, insn.desc);
    }

    @NonNull
    public String getOwnerInternalName() {
        return ownerInternalName;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getDesc() {
        return desc;
    }

    public boolean isConstructor() {
        return name.equals("<init>");
    }

    // Checks if the method is a Kotlin-generated accessor method for handling a private lambda
    // capture. This is likely not the most precise test, but it suffices for now.
    public boolean isSynthAccessor() {
        return name.startsWith("access$");
    }

    public String toString() {
        String d = String.format("%s.%s%s", ownerInternalName, name, desc);
        return d;
    }
}

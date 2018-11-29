/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.tracer.agent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.GeneratorAdapter;

class TraceApiMethodVisitor extends GeneratorAdapter implements Opcodes {

    private final String name;
    private final String desc;
    private static final Set<String> API =
            new HashSet<>(Arrays.asList("begin", "end", "flush", "start", "addVmArgs"));

    public TraceApiMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
        super(Opcodes.ASM5, mv, access, name, desc);
        this.name = name;
        this.desc = desc;
    }

    @Override
    public void visitCode() {
        super.visitCode();
        if (API.contains(name)) {
            redirect(name, desc);
        }
    }

    private void redirect(String method, String desc) {
        loadArgs();
        desc = desc.replaceAll("\\).*", ")V");
        visitMethodInsn(INVOKESTATIC, "com/android/tools/tracer/agent/Tracer", method, desc, false);
    }
}

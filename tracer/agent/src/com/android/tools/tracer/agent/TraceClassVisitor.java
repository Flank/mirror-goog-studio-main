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

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class TraceClassVisitor extends ClassVisitor {
    private final String className;
    private final TraceProfile profile;

    public TraceClassVisitor(ClassWriter writer, String className, TraceProfile profile) {
        super(Opcodes.ASM5, writer);
        this.className = className;
        this.profile = profile;
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        if (className.equals("com/android/tools/tracer/Trace")) {
            return new TraceApiMethodVisitor(mv, access, name, desc);
        } else {
            return new TraceMethodVisitor(mv, className, access, name, desc, profile);
        }
    }
}

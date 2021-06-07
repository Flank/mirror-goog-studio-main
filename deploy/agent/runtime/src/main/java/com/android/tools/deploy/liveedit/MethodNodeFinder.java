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

import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.commons.TryCatchBlockSorter;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;

/** This class searches the .class bytecode for a methodNode. */
class MethodNodeFinder extends ClassVisitor {
    private MethodNode target = null;
    private final String targetName;

    public static MethodNode findIn(byte[] classData, String name) {
        return new MethodNodeFinder(classData, name).getTarget();
    }

    private MethodNodeFinder(byte[] classData, String name) {
        super(Opcodes.ASM6);
        targetName = name;
        ClassReader reader = new ClassReader(classData);
        reader.accept(this, 0);
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String desc, String signature, String[] exceptions) {
        if (!name.equals(targetName)) {
            return super.visitMethod(access, name, desc, signature, exceptions);
        }
        target = new TryCatchBlockSorter(null, access, name, desc, signature, exceptions);
        return target;
    }

    public MethodNode getTarget() {
        return target;
    }
}

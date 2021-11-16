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

import com.android.deploy.asm.ClassReader;
import com.android.deploy.asm.ClassVisitor;
import com.android.deploy.asm.MethodVisitor;
import com.android.deploy.asm.Opcodes;
import com.android.deploy.asm.commons.TryCatchBlockSorter;
import com.android.deploy.asm.tree.MethodNode;
import java.util.ArrayList;
import java.util.List;

/** This class searches the .class bytecode for a methodNode. */
class MethodNodeFinder extends ClassVisitor {
    private MethodNode target = null;
    private String filename = null;
    private final String methodName;
    private final String methodDesc;
    private String ownerInternalName;
    private final List<String> visited = new ArrayList<>();

    MethodNodeFinder(byte[] classData, String methodName, String methodDesc) {
        super(Opcodes.ASM6);
        this.methodName = methodName;
        this.methodDesc = methodDesc;
        ClassReader reader = new ClassReader(classData);
        reader.accept(this, 0);
    }

    @Override
    public void visit(
            final int version,
            final int access,
            final String name,
            final String signature,
            final String superName,
            final String[] interfaces) {
        this.ownerInternalName = name;
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String desc, String signature, String[] exceptions) {
        visited.add(name + desc);

        if (!methodName.equals(name) || !methodDesc.equals(desc)) {
            return null;
        }
        target = new TryCatchBlockSorter(null, access, name, desc, signature, exceptions);
        return target;
    }

    @Override
    public void visitSource(String source, String debug) {
        this.filename = source;
    }

    public String getName() {
        return methodName;
    }

    public MethodNode getTarget() {
        return target;
    }

    public String getFilename() {
        return filename;
    }

    public String getOwnerInternalName() {
        return ownerInternalName;
    }

    List<String> getVisited() {
        return visited;
    }
}

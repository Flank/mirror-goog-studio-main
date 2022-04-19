/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.deploy.asm.FieldVisitor;
import com.android.deploy.asm.MethodVisitor;
import com.android.deploy.asm.Opcodes;
import com.android.deploy.asm.commons.TryCatchBlockSorter;
import com.android.deploy.asm.tree.MethodNode;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

// A class parsed from JVM bytecode. Contains a map of method descriptor to method nodes that is
// used by LiveEditClass to
// look up invoked methods to be interpreted at runtime.
class Interpretable extends ClassVisitor {
    private String filename;
    private String superName;
    private String[] interfaces;
    private String internalName;

    private final Map<String, MethodNode> declaredMethods;
    private final Map<String, Object> defaultFieldValues;

    Interpretable(byte[] classData) {
        super(Opcodes.ASM6);

        declaredMethods = new HashMap<>();
        defaultFieldValues = new HashMap<>();

        if (classData.length > 0) {
            ClassReader reader = new ClassReader(classData);
            reader.accept(this, 0);
        }
    }

    @Override
    public void visit(
            final int version,
            final int access,
            final String name,
            final String signature,
            final String superName,
            final String[] interfaces) {
        this.internalName = name;
        this.superName = superName;
        this.interfaces = interfaces;
    }

    @Override
    public FieldVisitor visitField(
            int access, String name, String desc, String signature, Object value) {
        if ((access & Opcodes.ACC_STATIC) == 0) {
            defaultFieldValues.put(name, defaultValue(desc));
        }
        return null;
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String desc, String signature, String[] exceptions) {
        // We use descriptor here (return type + param types) because synthetic methods can be
        // overloaded on return type.
        // Also, signature here is NOT a method signature, but a generic signature, and can be null
        // for non-generic methods.
        MethodNode node = new TryCatchBlockSorter(null, access, name, desc, signature, exceptions);
        declaredMethods.put(name + desc, node);
        return node;
    }

    @Override
    public void visitSource(String source, String debug) {
        this.filename = source;
    }

    public String getFilename() {
        return filename;
    }

    public String getInternalName() {
        return internalName;
    }

    public String getSuperName() {
        return superName;
    }

    public String[] getInterfaces() {
        return interfaces;
    }

    public MethodNode getMethod(String name, String desc) {
        return declaredMethods.get(name + desc);
    }

    public Collection<MethodNode> getMethods() {
        return declaredMethods.values();
    }

    public Map<String, Object> getDefaultFieldValues() {
        return defaultFieldValues;
    }

    // Returns the default value for a variable of the given type descriptor.
    private static Object defaultValue(String desc) {
        switch (desc) {
            case "Z":
                return false;
            case "B":
            case "S":
            case "I":
            case "J":
            case "F":
            case "D":
            case "C":
                return 0;
            default:
                // All reference types (prefixed with 'L') fall through to here.
                return null;
        }
    }
}

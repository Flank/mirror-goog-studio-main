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
import com.android.deploy.asm.Opcodes;

public class ClassNameFinder extends ClassVisitor {

    private String name;

    public ClassNameFinder(byte[] classData) {
        super(Opcodes.ASM6);
        ClassReader reader = new ClassReader(classData);
        reader.accept(this, 0);
    }

    @Override
    public void visit(
            int version,
            int access,
            String name,
            String signature,
            String superName,
            String[] interfaces) {
        this.name = name;
    }

    public String getInternalName() {
        if (name == null) {
            throw new IllegalStateException("Unable to find internalName");
        }
        return name;
    }
}

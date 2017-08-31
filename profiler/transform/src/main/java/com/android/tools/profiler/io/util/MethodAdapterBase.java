/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.profiler.io.util;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public abstract class MethodAdapterBase extends MethodVisitor implements Opcodes {

    public MethodAdapterBase(MethodVisitor mv) {
        super(ASM5, mv);
    }

    protected abstract String getWrapperClass();

    protected boolean isConstructor(int opcode, String name) {
        return opcode == INVOKESPECIAL && name.equals("<init>");
    }

    /**
     * Checks if there is a constructor that matches the invoked constructor and invokes the
     * wrapped one instead.
     *
     * @param owner the class of the object to be constructed
     * @param desc the description of the constructor
     * @param itf boolean to indicate if it's an interface
     * @param className the name of the class to be checked against the owner
     * @param constructorWrapper the name of the wrapped constructor method
     * @param descList the list of the methods descriptions
     * @return true if a matching constructor is found and wrapped
     */
    protected boolean findAndInvokeMatchingConstructorWrapper(String owner, String desc,
        boolean itf,
        String className, String constructorWrapper, String[] descList) {
        if (!owner.equals(className)) {
            return false;
        }
        for (String desc1 : descList) {
            if (desc1.equals(desc)) {
                // The description here is converted from a method returning void to
                // a method returning className object.
                // ex: (Ljava/io/File;)V -> (Ljava/io/File;)Ljava/io/FileInputStream;
                String wrappedDesc = desc.substring(0, desc.length() - 1) + "L" + className + ";";
                invoke(constructorWrapper, wrappedDesc, itf);
                super.visitInsn(SWAP);
                super.visitInsn(POP);
                super.visitInsn(SWAP);
                super.visitInsn(POP);
                return true;
            }
        }
        return false;
    }

    /**
     * Invokes a static method on our wrapper class.
     */
    protected void invoke(String method, String desc, boolean itf) {
        super.visitMethodInsn(INVOKESTATIC, getWrapperClass(), method, desc, itf);
    }
}

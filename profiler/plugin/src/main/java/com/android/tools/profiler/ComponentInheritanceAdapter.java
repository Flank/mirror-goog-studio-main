/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.profiler;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public class ComponentInheritanceAdapter extends ClassVisitor implements Opcodes {
  public ComponentInheritanceAdapter(ClassVisitor cv) {
    super(ASM5, cv);
  }

  /**
   * Record inheritance of current visiting class.
   * java.lang.Object don't have inheritance (superName equals null)
   */
  @Override
  public void visit(
      int version,
      int access,
      String name,
      String signature,
      String superName,
      String[] interfaces) {
    if (superName != null) {
      ComponentInheritanceUtils.recordDirectInheritance(name, superName);
    }
    super.visit(version, access, name, signature, superName, interfaces);
  }
}

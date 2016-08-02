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

import org.objectweb.asm.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class ProfilerTransform extends ClassTransform {
    public ProfilerTransform() {
        super("profiler", new String[] { "com.android.tools" + File.separator + "studio-profiler-lib" });
    }

    @Override
    protected void transform(InputStream in, OutputStream out, boolean instrumentUserClassOnly) throws IOException {
        if (instrumentUserClassOnly) {
            /**
             * Flag to automatically compute the maximum stack size and the maximum number of local
             * variables of methods.
             * If this flag is set, then the arguments of the visitMaxs method
             * of the MethodVisitor returned by the visitMethod method will be ignored,
             * and computed automatically from the signature and the bytecode of each method.
             */
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            ClassVisitor visitor = writer;
            visitor = new InitializerAdapter(visitor);
            visitor = new UserClassAdapter(visitor);
            ClassReader cr = new ClassReader(in);
            cr.accept(visitor, 0);
            out.write(writer.toByteArray());
        } else {
            /**
             * Flag to do nothing on computing the maximum stack size and the maximum number of local
             * variables of methods
             * When class transformer is traversing the system class, if we re-compute those fields
             * using ASM, it may cause java.lang.Verify exception in the run time.
             */
            ClassWriter writer = new ClassWriter(0);
            ClassVisitor visitor = writer;
            visitor = new InitializerAdapter(visitor);
            visitor = new NetworkingAdapter(visitor);
            visitor = new EventAdapter(visitor);
            /**
             * TODO: Remove the following line when the user class' instrumentation method stabilized
             */
            //visitor = new FragmentAdapter(visitor);
            visitor = new EnergyAdapter(visitor);
            visitor = new ComponentInheritanceAdapter(visitor);
            ClassReader cr = new ClassReader(in);
            cr.accept(visitor, 0);
            ComponentInheritanceUtils.buildInheritance();
            out.write(writer.toByteArray());
        }
    }
}


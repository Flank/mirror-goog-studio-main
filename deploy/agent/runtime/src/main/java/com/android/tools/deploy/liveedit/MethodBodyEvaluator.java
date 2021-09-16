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

import com.android.deploy.asm.Opcodes;
import com.android.deploy.asm.Type;
import com.android.deploy.asm.tree.MethodNode;
import com.android.deploy.asm.tree.analysis.Frame;
import com.android.tools.deploy.interpreter.ByteCodeInterpreter;
import com.android.tools.deploy.interpreter.InterpretationEventHandler;
import com.android.tools.deploy.interpreter.InterpreterResult;
import com.android.tools.deploy.interpreter.ObjectValue;
import com.android.tools.deploy.interpreter.Value;
import com.android.tools.deploy.interpreter.ValueReturned;
import org.jetbrains.eval4j.InterpreterLoopKt;

/** Evaluate a method body with Eval4j for the Android environment. */
public class MethodBodyEvaluator {

    private final MethodNode target;
    private final ClassLoader classLoader;
    private static final boolean USE_JFLINGER = true;

    // TODO: We should always use the app's classloader. This method is here for
    // our unit tests. We should consider removing this after we refactor the tests.
    public MethodBodyEvaluator(byte[] classData, String targetMethod) {
        this(classData, targetMethod, new byte[0][], MethodBodyEvaluator.class.getClassLoader());
    }

    public MethodBodyEvaluator(
            byte[] classData, String targetMethod, ClassLoader parentClassLoader) {
        this(classData, targetMethod, new byte[0][], parentClassLoader);
    }

    public MethodBodyEvaluator(
            byte[] classData,
            String targetMethod,
            byte[][] supportClasses,
            ClassLoader parentClassLoader) {
        MethodNodeFinder finder = new MethodNodeFinder(classData, targetMethod);
        this.target = finder.getTarget();
        if (target == null) {
            String msg = String.format("Cannot find target '%s' in:\n", targetMethod);
            for (String method : finder.getVisited()) {
                msg += "  -> " + method + "\n";
            }
            throw new IllegalStateException(msg);
        }
        if (supportClasses.length == 0) {
            this.classLoader = parentClassLoader;
        } else {
            this.classLoader = new LiveEditClassLoader(supportClasses, parentClassLoader);
        }
    }

    public Object evalStatic(Object[] arguments) {
        return eval(null, null, arguments);
    }

    public Object eval(Object thisObject, String objectType, Object[] arguments) {
        Frame<Value> init = new Frame<>(target.maxLocals, target.maxStack);
        int localIndex = 0;
        boolean isStatic = (target.access & Opcodes.ACC_STATIC) != 0;
        if (!isStatic) {
            init.setLocal(
                    localIndex++, new ObjectValue(thisObject, Type.getObjectType(objectType)));
        }

        Type[] argTypes = Type.getArgumentTypes(target.desc);
        for (int i = 0, len = argTypes.length; i < len; i++) {
            init.setLocal(localIndex++, AndroidEval.makeValue(arguments[i], argTypes[i]));
        }

        AndroidEval evaluator = new AndroidEval(classLoader);
        InterpreterResult result;
        if (USE_JFLINGER) {
            result =
                    ByteCodeInterpreter.interpreterLoop(
                            target, init, evaluator, InterpretationEventHandler.NONE);
        } else {
            result =
                    InterpreterLoopKt.interpreterLoop(
                            target, init, evaluator, InterpretationEventHandler.NONE);
        }
        if (result instanceof ValueReturned) {
            Value value = ((ValueReturned) result).getResult();
            return value.obj();
        }

        // TODO: Handle Exceptions
        return null;
    }
}

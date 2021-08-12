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

import static org.jetbrains.eval4j.InterpreterLoopKt.interpreterLoop;

import com.android.tools.deploy.interpreter.ObjectValue;
import com.android.tools.deploy.interpreter.Value;
import org.jetbrains.eval4j.InterpretationEventHandler;
import org.jetbrains.eval4j.InterpreterResult;
import org.jetbrains.eval4j.ValueReturned;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;
import org.jetbrains.org.objectweb.asm.tree.analysis.Frame;

/** Evaluate a method body with Eval4j for the Android environment. */
public class MethodBodyEvaluator {

    // Required but not used yet.
    private static class NoOpInterpretationEventHandler implements InterpretationEventHandler {
        @Override
        public InterpreterResult exceptionCaught(
                Frame<Value> frame, AbstractInsnNode node, Value value) {
            return null;
        }

        @Override
        public InterpreterResult exceptionThrown(
                Frame<Value> frame, AbstractInsnNode node, Value value) {
            return null;
        }

        @Override
        public InterpreterResult instructionProcessed(AbstractInsnNode node) {
            return null;
        }
    }

    private final MethodNode target;
    private final ClassLoader classLoader;

    public MethodBodyEvaluator(byte[] classData, String targetMethod) {
        this(classData, targetMethod, new byte[0][]);
    }

    public MethodBodyEvaluator(byte[] classData, String targetMethod, byte[][] supportClasses) {
        this.target = MethodNodeFinder.findIn(classData, targetMethod);
        if (target == null) {
            String msg = String.format("Cannot find target '%s'", targetMethod);
            throw new IllegalStateException(msg);
        }
        this.classLoader = new LiveEditClassLoader(supportClasses);
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
        InterpreterResult result =
                interpreterLoop(target, init, evaluator, new NoOpInterpretationEventHandler());
        if (result instanceof ValueReturned) {
            Value value = ((ValueReturned) result).getResult();
            return AndroidEval.valueToObject(value);
        }

        // TODO: Handle Exceptions
        return null;
    }
}

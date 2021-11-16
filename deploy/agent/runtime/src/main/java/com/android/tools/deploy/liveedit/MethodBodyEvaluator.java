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
import com.android.tools.deploy.interpreter.Eval;
import com.android.tools.deploy.interpreter.InterpretedMethod;
import com.android.tools.deploy.interpreter.InterpreterResult;
import com.android.tools.deploy.interpreter.ObjectValue;
import com.android.tools.deploy.interpreter.Throw;
import com.android.tools.deploy.interpreter.Value;
import com.android.tools.deploy.interpreter.ValueReturned;

public class MethodBodyEvaluator {
    private static final boolean DEBUG_EVAL = true;

    private final LiveEditContext context;
    private final InterpretedMethod method;

    // TODO: We should always use the app's classloader. This method is here for
    // our unit tests. We should consider removing this after we refactor the tests.
    public MethodBodyEvaluator(byte[] classData, String methodName, String methodDesc) {
        this(
                new LiveEditContext(MethodBodyEvaluator.class.getClassLoader()),
                classData,
                methodName,
                methodDesc);
    }

    public MethodBodyEvaluator(
            LiveEditContext context, byte[] classData, String methodName, String methodDesc) {
        MethodNodeFinder finder = new MethodNodeFinder(classData, methodName, methodDesc);
        this.context = context;
        this.method =
                new InterpretedMethod(
                        finder.getTarget(),
                        finder.getFilename(),
                        finder.getName(),
                        finder.getOwnerInternalName());
        if (method.getTarget() == null) {
            String msg = String.format("Cannot find target '%s' in:\n", methodName + methodDesc);
            for (String method : finder.getVisited()) {
                msg += "  -> " + method + "\n";
            }
            throw new IllegalStateException(msg);
        }
    }

    public Object evalStatic(Object[] arguments) {
        return eval(null, null, arguments);
    }

    public Object eval(Object thisObject, String objectType, Object[] arguments) {
        MethodNode target = method.getTarget();
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

        Eval evaluator = new ProxyClassEval(context);
        if (DEBUG_EVAL) {
            evaluator = new LoggingEval(evaluator);
        }

        InterpreterResult result = ByteCodeInterpreter.interpreterLoop(method, init, evaluator);
        if (result instanceof ValueReturned) {
            Value value = ((ValueReturned) result).getResult();
            return value.obj();
        }

        if (result instanceof ByteCodeInterpreter.ExceptionThrown) {
            ByteCodeInterpreter.ExceptionThrown exceptionResult =
                    (ByteCodeInterpreter.ExceptionThrown) result;
            Throwable t = (Throwable) exceptionResult.getException().getValue();
            Throw.sneaky(t);
        }
        return null;
    }
}

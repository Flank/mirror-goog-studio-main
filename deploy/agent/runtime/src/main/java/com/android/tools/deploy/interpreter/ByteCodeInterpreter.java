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
package com.android.tools.deploy.interpreter;

import static com.android.deploy.asm.Opcodes.ARETURN;
import static com.android.deploy.asm.Opcodes.ATHROW;
import static com.android.deploy.asm.Opcodes.DRETURN;
import static com.android.deploy.asm.Opcodes.FRETURN;
import static com.android.deploy.asm.Opcodes.GOTO;
import static com.android.deploy.asm.Opcodes.IRETURN;
import static com.android.deploy.asm.Opcodes.LRETURN;
import static com.android.deploy.asm.Opcodes.RET;
import static com.android.deploy.asm.Opcodes.RETURN;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.deploy.asm.Type;
import com.android.deploy.asm.tree.AbstractInsnNode;
import com.android.deploy.asm.tree.InsnList;
import com.android.deploy.asm.tree.JumpInsnNode;
import com.android.deploy.asm.tree.LineNumberNode;
import com.android.deploy.asm.tree.MethodNode;
import com.android.deploy.asm.tree.TryCatchBlockNode;
import com.android.deploy.asm.tree.VarInsnNode;
import com.android.deploy.asm.tree.analysis.Frame;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ByteCodeInterpreter {

    private AbstractInsnNode currentInsn;
    private final Eval eval;
    private final InterpretedMethod im;
    private final OpcodeInterpreter interpreter;
    private final Frame<Value> frame;
    private final List<TryCatchBlockNode>[] handlers;

    // If the interpreter calls a method, it will declare it here. This field is used for stacktrace
    // cleaning.
    private MethodDescription exitPoint = MethodDescription.EMPTY;

    private ByteCodeInterpreter(
            @NonNull InterpretedMethod im, @NonNull Frame<Value> initialState, @NonNull Eval eval) {
        this.eval = eval;
        this.im = im;

        AbstractInsnNode firstInsn = im.getTarget().instructions.getFirst();
        if (firstInsn == null) {
            throw new IllegalArgumentException("Empty method");
        }

        currentInsn = firstInsn;

        interpreter = new OpcodeInterpreter(eval, this);
        frame = new Frame<>(initialState);
        handlers = computeHandlers(im.getTarget());
    }

    void goTo(@Nullable AbstractInsnNode nextInsn) {
        if (nextInsn == null) {
            throw new IllegalArgumentException("Instruction flow ended with no RETURN");
        }
        currentInsn = nextInsn;
    }

    @NonNull
    public static InterpreterResult interpreterLoop(
            @NonNull InterpretedMethod method,
            @NonNull Frame<Value> initialState,
            @NonNull Eval eval) {
        ByteCodeInterpreter bci = new ByteCodeInterpreter(method, initialState, eval);

        try {
            Houdini.startFrame(bci);
            return bci.doInterpret();
        } finally {
            Houdini.endFrame();
        }
    }

    @NonNull
    InterpreterResult doInterpret() {
        try {
            loop:
            while (true) {
                switch (currentInsn.getType()) {
                    case AbstractInsnNode.LABEL:
                    case AbstractInsnNode.FRAME:
                    case AbstractInsnNode.LINE:
                        // Do nothing, go to next instruction
                        goTo(currentInsn.getNext());
                        continue loop;
                    default:
                }

                int insnOpcode = currentInsn.getOpcode();
                switch (insnOpcode) {
                    case GOTO:
                        goTo(((JumpInsnNode) currentInsn).label);
                        continue loop;
                    case RET:
                        VarInsnNode varNode = (VarInsnNode) currentInsn;
                        com.android.deploy.asm.tree.analysis.Value address =
                                frame.getLocal(varNode.var);
                        goTo(((LabelValue) address).value);
                        continue loop;

                    case IRETURN:
                    case LRETURN:
                    case FRETURN:
                    case DRETURN:
                    case ARETURN:
                        return computeReturn(insnOpcode);
                    case RETURN:
                        return new ValueReturned(Value.VOID_VALUE);
                    case ATHROW:
                        ObjectValue exceptionValue = (ObjectValue) getStackTop(frame);

                        // Regardless if the exception is caught in this method or not, we must
                        // clean it.
                        Throwable cleaned = clear((Throwable) exceptionValue.getValue());
                        exceptionValue.setValue(cleaned);

                        if (exceptionCaught(exceptionValue)) {
                            continue loop;
                        }
                        return new ExceptionThrown(
                                exceptionValue, ExceptionKind.FROM_EVALUATED_CODE);

                    default:
                        // Remaining cases are handler by the interpreter (frame.execute).
                        break;
                }

                try {
                    frame.execute(currentInsn, interpreter);
                } catch (InterpreterException e) {
                    if (e.getCause() == null) {
                        throw new NullPointerException("Exception without cause");
                    }
                    Throwable exception = e.getCause();
                    ObjectValue exceptionValue =
                            new ObjectValue(exception, Type.getType(Exception.class));
                    if (exceptionFromEvalCaught(exception, exceptionValue)) {
                        continue loop;
                    }

                    ExceptionKind exceptionType;
                    if (e instanceof BrokenCode) {
                        exceptionType = ExceptionKind.BROKEN_CODE;
                    } else {
                        exceptionType = ExceptionKind.FROM_EVALUATOR;
                    }
                    return new ExceptionThrown(exceptionValue, exceptionType);
                } catch (Exception e) {
                    Throw.sneaky(clear(e));
                }
                goTo(currentInsn.getNext());
            }
        } catch (ResultException e) {
            return e.result;
        }
    }

    public StackTraceElement createStackTraceElement() {
        int line = getLineNumber();
        String ownerName = im.getOwnerName();
        String name = im.getName();
        String filename = im.getFilename();
        return new StackTraceElement(ownerName, name, filename, line);
    }

    private Throwable clear(Throwable t) {
        if (!Config.getInstance().debugEnabled()) {
            t = Houdini.clean(t);
        }
        return t;
    }

    private int getLineNumber() {
        AbstractInsnNode cursor = currentInsn;
        while (cursor.getPrevious() != null) {
            AbstractInsnNode node = cursor.getPrevious();
            if (node instanceof LineNumberNode) {
                LineNumberNode lineNumberNode = (LineNumberNode) node;
                return lineNumberNode.line;
            }
            cursor = cursor.getPrevious();
        }
        return -1;
    }

    private InterpreterResult computeReturn(int insnOpcode) {
        Value value = getStackTop(frame);
        Type expectedType = Type.getReturnType(im.getTarget().desc);
        if (expectedType.getSort() == Type.OBJECT || expectedType.getSort() == Type.ARRAY) {
            Value coerced = value;
            if (!value.equals(Value.NULL_VALUE) && value.asmType.equals(expectedType)) {
                coerced = new ObjectValue(value.obj(), expectedType);
            }
            return new ValueReturned(coerced);
        }
        if (value.asmType != expectedType) {
            assert insnOpcode == IRETURN
                    : String.format("Only ints should be coerced: %d", insnOpcode);

            Value coerced;
            switch (expectedType.getSort()) {
                case Type.BOOLEAN:
                    coerced = IntValue.fromBool(value.getBoolean());
                    break;
                case Type.BYTE:
                    coerced = IntValue.fromByte((byte) value.getInt());
                    break;
                case Type.SHORT:
                    coerced = IntValue.fromShort((short) value.getInt());
                    break;
                case Type.CHAR:
                    coerced = IntValue.fromChar((char) value.getInt());
                    break;
                case Type.INT:
                    coerced = new IntValue(value.getInt());
                    break;
                default:
                    throw new UnsupportedByteCodeException(
                            "Should not be coerced: " + expectedType);
            }
            return new ValueReturned(coerced);
        }
        return new ValueReturned(value);
    }

    @NonNull
    private static Value getStackTop(@NonNull Frame<Value> frame) throws BrokenCode {
        Value v = frame.getStack(frame.getStackSize() - 1);
        if (v == null) {
            Exception e = new IllegalArgumentException("Couldn't get top stack value");
            throw new BrokenCode(e);
        }
        return v;
    }

    @NonNull
    private static List<TryCatchBlockNode>[] computeHandlers(@NonNull MethodNode m) {
        InsnList insn = m.instructions;
        List[] handlers = new List[insn.size()];
        for (TryCatchBlockNode tcb : m.tryCatchBlocks) {
            int begin = insn.indexOf(tcb.start);
            int end = insn.indexOf(tcb.end);
            for (int j = begin; j < end; ++j) {
                List<TryCatchBlockNode> insnHandlers = handlers[j];
                if (insnHandlers == null) {
                    insnHandlers = new ArrayList<>();
                    handlers[j] = insnHandlers;
                }
                insnHandlers.add(tcb);
            }
        }
        return handlers;
    }

    boolean exceptionCaught(
            @NonNull Value exceptionValue, @NonNull Function<Type, Boolean> instanceOf) {
        List<TryCatchBlockNode> catchBlocks =
                handlers[im.getTarget().instructions.indexOf(currentInsn)];
        if (catchBlocks == null) {
            catchBlocks = new ArrayList<>();
        }
        for (TryCatchBlockNode catcher : catchBlocks) {
            String exceptionTypeInternalName = catcher.type;
            if (exceptionTypeInternalName != null) {
                Type exceptionType = Type.getObjectType(exceptionTypeInternalName);
                if (instanceOf.apply(exceptionType)) {
                    frame.clearStack();
                    frame.push(exceptionValue);
                    goTo(catcher.handler);
                    return true;
                }
            }
        }
        return false;
    }

    boolean exceptionCaught(@NonNull Value exceptionValue) {
        return exceptionCaught(
                exceptionValue,
                (exceptionType) -> eval.isInstanceOf(exceptionValue, exceptionType));
    }

    boolean exceptionFromEvalCaught(@NonNull Throwable exception, @NonNull Value exceptionValue) {
        return exceptionCaught(
                exceptionValue,
                (exceptionType) -> {
                    try {
                        Class exceptionClass = Exception.class;
                        Class clazz =
                                Class.forName(
                                        exceptionType.getInternalName().replace('/', '.'),
                                        true,
                                        exceptionClass.getClassLoader());
                        return clazz.isAssignableFrom(exceptionClass);
                    } catch (ClassNotFoundException e) {
                        // If the class is not available in this VM, it can not be a superclass of
                        // an exception thrown in it
                        return false;
                    }
                });
    }

    void setExitPoint(MethodDescription method) {
        this.exitPoint = method;
    }

    public MethodDescription getExitPoint() {
        return exitPoint;
    }

    public static class ExceptionThrown implements InterpreterResult {

        private final ObjectValue exception;
        private final ExceptionKind kind;

        private ExceptionThrown(@NonNull ObjectValue exception, @NonNull ExceptionKind kind) {
            this.exception = exception;
            this.kind = kind;
        }

        @NonNull
        public ObjectValue getException() {
            return exception;
        }

        @Override
        public String toString() {
            return "Thrown $exception:" + kind;
        }
    };

    enum ExceptionKind {
        FROM_EVALUATED_CODE,
        FROM_EVALUATOR,
        BROKEN_CODE
    }

    static class BrokenCode extends InterpreterException {
        BrokenCode(@NonNull Throwable cause) {
            super(cause);
        }
    }

    static class UnsupportedByteCodeException extends RuntimeException {
        UnsupportedByteCodeException(@NonNull String message) {
            super(message);
        }
    }

    static class ResultException extends RuntimeException {
        private final InterpreterResult result;

        private ResultException(@NonNull InterpreterResult result) {
            this.result = result;
        }

        public InterpreterResult getResult() {
            return result;
        }
    }

    static class ThrownFromEvaluatedCodeException extends RuntimeException {
        private final ObjectValue exception;

        private ThrownFromEvaluatedCodeException(@NonNull ObjectValue exception) {
            this.exception = exception;
        }

        public ObjectValue getException() {
            return exception;
        }

        public String toString() {
            return "Thrown from evaluated code: " + exception;
        }
    }
}

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

import java.util.ArrayDeque;
import java.util.LinkedList;
import java.util.List;

class Houdini {

    private static final String STUB_CLASS = "com.android.tools.deploy.liveedit.LiveEditStubs";
    private static final String STUB_METHOD_PREFIX = "stub";

    // The FIFO stack where all interpreters are stored. These provide both the exit point and
    // the StackTraceElement to replace a stacktrace block of interpreter elements.
    private static final ThreadLocal<ArrayDeque<ByteCodeInterpreter>> frames = new ThreadLocal<>();

    static void startFrame(ByteCodeInterpreter interpreter) {
        ensureStack();
        frames.get().push(interpreter);
    }

    static void endFrame() {
        ensureStack();
        if (frames.get().isEmpty()) {
            return;
        }
        frames.get().pop();
    }

    private static void ensureStack() {
        if (frames.get() == null) {
            frames.set(new ArrayDeque<>());
        }
    }

    public static Throwable clean(Throwable t) {
        if (!HoudiniConfiguration.ENABLED) {
            return t;
        }
        ensureStack();
        if (frames.get().size() == 0) {
            return t;
        }

        // Iterate over the stack in its intended LIFO order
        ArrayDeque<ByteCodeInterpreter> stack = frames.get();
        for (ByteCodeInterpreter interpreter : stack) {
            t = cleanOneFrame(t, interpreter);
        }
        return t;
    }

    private static Throwable cleanOneFrame(Throwable t, ByteCodeInterpreter interpreter) {
        // 1. Go down until we find LiveEditStubs.doStub.
        // 2. From there, go up to find the exit point (if there is one).
        // 3. Replace this block with correct method stubbed.
        int start = 0;
        StackTraceElement[] elements = t.getStackTrace();
        while (start < elements.length && !isStub(elements[start])) {
            start++;
        }

        if (start == elements.length || !isStub(elements[start])) {
            return t;
        }

        // Because of the way we stub, the original callsite is encomparsed in the stacktrace block
        // to be replaced.
        start++;

        int end = start;
        // Now move up and find the exit point.
        MethodDescription exitPoint = interpreter.getExitPoint();
        do {
            end--;
        } while (end >= 0 && !isExitPoint(elements[end], exitPoint));

        // We have everything we need now.
        // 1/ "end" points to the first element in the stackTrace that
        //    does not belong to the stack block to remove.
        // 2/ "start" point to the first element in the stackTrace that
        //    does belong to the stack block to remove
        // +-----------+ 0
        // | App stack | <- end
        // | LE  stack |
        // | LE  stack |
        // | LE  ...   |
        // | LE  stack |
        // | App stack | <- start
        // | App stack | n - 1
        // +-----------+ n

        List<StackTraceElement> newStack = new LinkedList<>();

        // Insert everything BEFORE the stack block to replace
        int cursor = 0;
        for (; cursor <= end; cursor++) {
            newStack.add(elements[cursor]);
        }

        // Skip the stack block to replace
        for (; cursor < elements.length; cursor++) {
            if (start >= cursor && cursor > end) {
                continue;
            }
            break;
        }

        // Insert stack block replacement
        newStack.add(interpreter.createStackTraceElement());

        // Add everything else
        for (; cursor < elements.length; cursor++) {
            newStack.add(elements[cursor]);
        }

        StackTraceElement[] stack = newStack.toArray(new StackTraceElement[] {});
        t.setStackTrace(stack);
        return t;
    }

    private static boolean isExitPoint(StackTraceElement element, MethodDescription method) {
        String internalName = element.getClassName().replace(".", "/");
        String methodName = element.getMethodName();
        return internalName.equals(method.getOwnerInternalName())
                && methodName.equals(method.getName());
    }

    private static boolean isStub(StackTraceElement element) {
        return element.getClassName().equals(STUB_CLASS)
                && element.getMethodName().startsWith(STUB_METHOD_PREFIX);
    }

    // Only used for debug
    public static void dumpStack() {
        ensureStack();
        System.out.println("------HOUDINI STACK-----=" + frames.get().size());
        for (ByteCodeInterpreter interpreter : frames.get()) {
            System.out.println(interpreter.getExitPoint());
        }
        System.out.println("------HOUDINI STACK----*");
    }
}

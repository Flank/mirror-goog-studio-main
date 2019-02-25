package com.android.tools.checker.agent;

import com.android.annotations.NonNull;
import java.util.function.Consumer;
import java.util.function.Function;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class InstrumentClassVisitor extends ClassVisitor {
    private final Function<String, String> methodAspects;
    private final String className;
    private final Consumer<String> notFoundCallback;

    InstrumentClassVisitor(
            @NonNull ClassVisitor classVisitor,
            @NonNull String className,
            @NonNull Function<String, String> methodAspects,
            @NonNull Consumer<String> notFoundCallback) {
        super(Opcodes.ASM5, classVisitor);

        this.className = className;
        this.methodAspects = methodAspects;
        this.notFoundCallback = notFoundCallback;
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

        return new InterceptVisitor(
                mv, access, name, desc, className, methodAspects, notFoundCallback);
    }
}

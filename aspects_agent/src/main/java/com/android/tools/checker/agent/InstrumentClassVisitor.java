package com.android.tools.checker.agent;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class InstrumentClassVisitor extends ClassVisitor {
    private static final Logger LOGGER = Logger.getLogger(Transform.class.getName());

    private final Function<String, String> methodAspects;
    private final String className;
    private final Consumer<String> notFoundCallback;

    InstrumentClassVisitor(
            ClassVisitor classVisitor,
            String className,
            Function<String, String> methodAspects,
            Consumer<String> notFoundCallback) {
        super(Opcodes.ASM5, classVisitor);

        this.className = className;
        this.methodAspects = methodAspects;
        this.notFoundCallback = notFoundCallback;
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

        String className = this.className.replace('/', '.') + ".";
        String key = className + name + desc;
        String aspect = methodAspects.apply(key);

        if (aspect == null) {
            // Try with the method key without descriptor (no parameters or return type)
            String simplifiedKey = className + name;
            aspect = methodAspects.apply(simplifiedKey);
        }

        if (aspect == null) {
            LOGGER.fine("Aspect NOT found " + key);
            notFoundCallback.accept(key);
            return mv;
        }

        LOGGER.info("Aspect found " + key);

        try {
            return InterceptVisitor.getInterceptVisitor(mv, access, name, desc, aspect);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}

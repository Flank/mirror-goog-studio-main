package com.android.tools.checker.agent;

import com.android.annotations.NonNull;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class InstrumentClassVisitor extends ClassVisitor {
    private final Function<String, String> methodAspects;
    private final Set<Type> classAnnotations = new HashSet<>();
    private final String className;
    private final Consumer<String> notFoundCallback;
    private final AnnotationConflictsManager annotationConflictsManager;

    InstrumentClassVisitor(
            @NonNull ClassVisitor classVisitor,
            @NonNull String className,
            @NonNull Function<String, String> methodAspects,
            @NonNull Consumer<String> notFoundCallback,
            @NonNull AnnotationConflictsManager annotationConflictsManager) {
        super(Opcodes.ASM7, classVisitor);

        this.className = className;
        this.methodAspects = methodAspects;
        this.notFoundCallback = notFoundCallback;
        this.annotationConflictsManager = annotationConflictsManager;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        classAnnotations.add(Type.getType(desc));
        return super.visitAnnotation(desc, visible);
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

        return new InterceptVisitor(
                mv,
                access,
                name,
                desc,
                annotationConflictsManager,
                classAnnotations,
                className,
                methodAspects,
                notFoundCallback);
    }
}

package com.android.tools.checker.agent;

import com.android.annotations.NonNull;
import java.util.HashSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

public class InterceptVisitor extends AdviceAdapter {
    private static final Logger LOGGER = Logger.getLogger(InterceptVisitor.class.getName());
    private final Function<String, String> aspects;
    private final String className;
    private final String name;
    private final String desc;
    private final Consumer<String> notFoundCallback;
    private final HashSet<Type> visitedAnnotations = new HashSet<>();

    /**
     * Creates a new {@link InterceptVisitor}. The intercept visitor will analyze the method an
     * instrument it if an aspect is found for it.
     *
     * @param mv The {@link MethodVisitor} to which this adapter delegates calls.
     * @param access The method access flags.
     * @param name The method name.
     * @param desc The method type descriptor.
     * @param className The class name of the method container.
     * @param aspects {@link Function} to locate aspects.
     * @param notFoundCallback {@link Consumer} to report aspects that this class can not find.
     */
    public InterceptVisitor(
            @NonNull MethodVisitor mv,
            int access,
            @NonNull String name,
            @NonNull String desc,
            @NonNull String className,
            @NonNull Function<String, String> aspects,
            @NonNull Consumer<String> notFoundCallback) {
        super(Opcodes.ASM5, mv, access, name, desc);

        this.name = name;
        this.desc = desc;
        this.className = className;
        this.aspects = aspects;
        this.notFoundCallback = notFoundCallback;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        visitedAnnotations.add(Type.getType(desc));
        return super.visitAnnotation(desc, visible);
    }

    /** Generates a static call to the given aspect. */
    private void generateStaticCall(@NonNull String aspect) {
        int sharpIndex = aspect.indexOf('#');
        try {
            Type staticClassType = Type.getType(Class.forName(aspect.substring(0, sharpIndex)));
            Method staticClassMethod =
                    Method.getMethod(String.format("void %s()", aspect.substring(sharpIndex + 1)));

            invokeStatic(staticClassType, staticClassMethod);
        } catch (ClassNotFoundException e) {
            LOGGER.warning("Unable to instrument " + className);
        }
    }

    @Override
    protected void onMethodEnter() {
        // Check if there is an aspect defined for this method. If there is, we add the static
        // call at the beginning of the method.
        // Aspects can also be added by adding annotations. Multiple aspects can be added via
        // annotations.
        String className = this.className.replace('/', '.') + ".";
        String key = className + name + desc;
        String methodAspect = aspects.apply(key);

        if (methodAspect == null) {
            // Try with the method key without descriptor (no parameters or return type)
            String simplifiedKey = className + name;
            methodAspect = aspects.apply(simplifiedKey);
        }

        if (methodAspect == null) {
            notFoundCallback.accept(key);
        } else {
            generateStaticCall(methodAspect);
            LOGGER.fine("Method Aspect found " + key);
        }

        // If the method has any annotations, check if there are any aspects defined.
        if (!visitedAnnotations.isEmpty()) {
            visitedAnnotations
                    .stream()
                    .map(annotationType -> "@" + annotationType.getClassName())
                    .forEach(
                            annotationName -> {
                                String annotationAspect = aspects.apply(annotationName);
                                if (annotationAspect != null) {
                                    generateStaticCall(annotationAspect);
                                    LOGGER.fine("Annotation Aspect found " + annotationName);
                                } else {
                                    notFoundCallback.accept(annotationName);
                                }
                            });
        }
    }
}

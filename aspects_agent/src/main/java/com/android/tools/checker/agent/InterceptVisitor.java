package com.android.tools.checker.agent;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

public class InterceptVisitor extends AdviceAdapter {
    private final Type classType;
    private final Method method;

    private InterceptVisitor(
            MethodVisitor mv,
            int access,
            String name,
            String desc,
            Type staticClassType,
            Method staticClassMethod) {
        super(Opcodes.ASM5, mv, access, name, desc);

        classType = staticClassType;
        method = staticClassMethod;
    }

    static InterceptVisitor getInterceptVisitor(
            MethodVisitor mv, int access, String name, String desc, String staticCall)
            throws ClassNotFoundException {
        int sharpIndex = staticCall.indexOf('#');
        Type staticClassType = Type.getType(Class.forName(staticCall.substring(0, sharpIndex)));
        Method staticClassMethod =
                Method.getMethod(String.format("void %s()", staticCall.substring(sharpIndex + 1)));

        return new InterceptVisitor(mv, access, name, desc, staticClassType, staticClassMethod);
    }

    @Override
    protected void onMethodEnter() {
        invokeStatic(classType, method);
    }
}

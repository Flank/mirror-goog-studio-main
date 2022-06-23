/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.instrumentation.threading.agent;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.android.tools.instrumentation.threading.agent.callback.ThreadingCheckerHook;
import com.android.tools.instrumentation.threading.agent.callback.ThreadingCheckerTrampoline;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

public class AgentTest {

    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Mock private ThreadingCheckerHook mockThreadingCheckerHook;

    @Before
    public void setUp() {
        ThreadingCheckerTrampoline.installHook(mockThreadingCheckerHook);
    }

    @Test
    public void testUiMethodAnnotation()
            throws IOException, IllegalAccessException, InstantiationException,
                    NoSuchMethodException, InvocationTargetException {

        Class<?> transformedClass = loadAndTransform(SampleClasses.ClassWithAnnotatedMethods.class);
        Object instance = transformedClass.getDeclaredConstructor().newInstance();
        callMethod(transformedClass, instance, "uiMethod1", false);

        verify(mockThreadingCheckerHook).verifyOnUiThread();
    }

    @Test
    public void testWorkerThreadMethodAnnotation()
            throws IOException, IllegalAccessException, InstantiationException,
                    NoSuchMethodException, InvocationTargetException {

        Class<?> transformedClass = loadAndTransform(SampleClasses.ClassWithAnnotatedMethods.class);
        Object instance = transformedClass.getDeclaredConstructor().newInstance();
        callMethod(transformedClass, instance, "workerMethod1", false);

        verify(mockThreadingCheckerHook).verifyOnWorkerThread();
    }

    @Test
    public void testAnyThreadMethodAnnotation_currentlyDoesNotGetInstrumented()
            throws IOException, IllegalAccessException, InstantiationException,
                    NoSuchMethodException, InvocationTargetException {

        Class<?> transformedClass = loadAndTransform(SampleClasses.ClassWithAnnotatedMethods.class);
        Object instance = transformedClass.getDeclaredConstructor().newInstance();
        callMethod(transformedClass, instance, "anyThreadMethod1", false);

        verifyNoInteractions(mockThreadingCheckerHook);
    }

    @Test
    public void testSlowThreadMethodAnnotation_currentlyDoesNotGetInstrumented()
            throws IOException, IllegalAccessException, InstantiationException,
                    NoSuchMethodException, InvocationTargetException {

        Class<?> transformedClass = loadAndTransform(SampleClasses.ClassWithAnnotatedMethods.class);
        Object instance = transformedClass.getDeclaredConstructor().newInstance();
        callMethod(transformedClass, instance, "slowThreadMethod1", false);

        verifyNoInteractions(mockThreadingCheckerHook);
    }

    @Test
    public void testThreadingAnnotationOnPrivateMethod()
            throws IOException, IllegalAccessException, InstantiationException,
                    NoSuchMethodException, InvocationTargetException {

        Class<?> transformedClass = loadAndTransform(SampleClasses.ClassWithAnnotatedMethods.class);
        Object instance = transformedClass.getDeclaredConstructor().newInstance();
        callMethod(transformedClass, instance, "privateUiMethod1", true);

        verify(mockThreadingCheckerHook).verifyOnUiThread();
    }

    @Test
    public void testMethodWithNoAnnotations()
            throws IOException, IllegalAccessException, InstantiationException,
                    NoSuchMethodException, InvocationTargetException {

        Class<?> transformedClass = loadAndTransform(SampleClasses.ClassWithAnnotatedMethods.class);
        Object instance = transformedClass.getDeclaredConstructor().newInstance();
        callMethod(transformedClass, instance, "nonAnnotatedMethod1", false);

        verifyNoInteractions(mockThreadingCheckerHook);
    }

    @Test
    public void testConstructorAnnotation()
            throws IOException, IllegalAccessException, InstantiationException,
                    NoSuchMethodException, InvocationTargetException {

        Class<?> transformedClass =
                loadAndTransform(SampleClasses.ClassWithAnnotatedConstructor.class);

        Object instance = transformedClass.getDeclaredConstructor().newInstance();

        verify(mockThreadingCheckerHook).verifyOnUiThread();
    }

    @Test
    public void testClassAnnotationAppliesToUnannotatedMethod()
            throws IOException, IllegalAccessException, InstantiationException,
                    NoSuchMethodException, InvocationTargetException {

        Class<?> transformedClass =
                loadAndTransform(SampleClasses.ClassWithUiThreadAnnotation.class);

        Object instance = transformedClass.getDeclaredConstructor().newInstance();
        callMethod(transformedClass, instance, "nonAnnotatedMethod1", false);

        // One invocation is the result of a constructor call.
        verify(mockThreadingCheckerHook, times(2)).verifyOnUiThread();
    }

    @Test
    public void testClassAnnotationWithMatchingMethodAnnotation()
            throws IOException, IllegalAccessException, InstantiationException,
                    NoSuchMethodException, InvocationTargetException {

        Class<?> transformedClass =
                loadAndTransform(SampleClasses.ClassWithUiThreadAnnotation.class);

        Object instance = transformedClass.getDeclaredConstructor().newInstance();
        callMethod(transformedClass, instance, "uiMethod1", false);

        // One invocation is the result of a constructor call.
        verify(mockThreadingCheckerHook, times(2)).verifyOnUiThread();
    }

    @Test
    public void testClassAnnotationIsIgnoredInFavorOfMethodAnnotation()
            throws IOException, IllegalAccessException, InstantiationException,
                    NoSuchMethodException, InvocationTargetException {

        Class<?> transformedClass =
                loadAndTransform(SampleClasses.ClassWithUiThreadAnnotation.class);

        Object instance = transformedClass.getDeclaredConstructor().newInstance();
        callMethod(transformedClass, instance, "anyThreadMethod1", false);

        // One invocation is the result of a constructor call.
        verify(mockThreadingCheckerHook, times(1)).verifyOnUiThread();
    }

    @Test
    public void testMultipleThreadingAnnotationsOnAMethod()
            throws IOException, IllegalAccessException, InstantiationException,
                    NoSuchMethodException, InvocationTargetException {

        Class<?> transformedClass = loadAndTransform(SampleClasses.ClassWithAnnotatedMethods.class);
        Object instance = transformedClass.getDeclaredConstructor().newInstance();
        callMethod(transformedClass, instance, "workerAndUiMethod1", false);

        verify(mockThreadingCheckerHook).verifyOnUiThread();
    }

    @Test
    public void testClassAnnotation_doesNotExtendToLambda()
            throws IOException, IllegalAccessException, InstantiationException,
                    NoSuchMethodException, InvocationTargetException {

        Class<?> transformedClass =
                loadAndTransform(SampleClasses.ClassWithUiThreadAnnotation.class);

        Object instance = transformedClass.getDeclaredConstructor().newInstance();
        callMethod(transformedClass, instance, "executeWithLambda", false);

        // One invocation is the result of a constructor call.
        // Verify that there are no additional invocations from a call to a lambda method (note that
        // the compiler generates a method called "lambda$executeWithLambda$0" that's executed
        // as part of this test)
        verify(mockThreadingCheckerHook, times(1)).verifyOnUiThread();
    }

    @Test
    public void testClassAnnotation_doesNotExtendToSyntheticAccessors()
            throws IOException, IllegalAccessException, InstantiationException,
                    NoSuchMethodException, InvocationTargetException {

        Class<?> transformedParentClass =
                loadAndTransformMultiple(
                                SampleClasses.AnnotatedClassWithInnerClass.class,
                                SampleClasses.AnnotatedClassWithInnerClass.InnerClass.class)
                        .get(0);
        Object parentClassInstance = transformedParentClass.getDeclaredConstructor().newInstance();

        callMethod(
                transformedParentClass,
                parentClassInstance,
                "createAndCallInnerClassMethod",
                false);

        // Inner class method that increments/decrements parent class's  field does it through
        // synthetic
        // methods like "SampleClasses$AnnotatedClassWithInnerClass#access$004". This test verifies
        // that these methods do not use threading annotations from the containing class.
        // One and only UiThread check is the result of invoking a constructor.
        verify(mockThreadingCheckerHook, times(1)).verifyOnUiThread();
    }

    private static Class<?> loadAndTransform(Class<?> clazz) throws IOException {
        return loadAndTransformMultiple(clazz).get(0);
    }

    /**
     * Transforms provided classes using the {@link Transformer} and names them as
     * OriginalName_Transformed.
     */
    private static ArrayList<Class<?>> loadAndTransformMultiple(Class<?>... classes)
            throws IOException {
        TestClassLoader classLoader = new TestClassLoader(AgentTest.class.getClassLoader());
        Transformer transformer = new Transformer(AnnotationMappings.create());

        String newNameSuffix = "_Transformed";
        Map<String, String> nameMappings = new HashMap<>();
        for (Class<?> clazz : classes) {
            String internalClassName = clazz.getName().replace('.', '/');
            nameMappings.put(internalClassName, internalClassName + newNameSuffix);
        }

        ArrayList<Class<?>> transformedClasses = new ArrayList<>();
        for (Class<?> clazz : classes) {
            String internalClassName = clazz.getName().replace('.', '/');
            byte[] classInput =
                    ByteStreams.toByteArray(
                            Objects.requireNonNull(
                                    classLoader.getResourceAsStream(internalClassName + ".class")));

            byte[] newClass =
                    transformer.transform(classLoader, clazz.getName(), null, null, classInput);

            newClass = renameClassNames(newClass, nameMappings);
            Class<?> newClassDef =
                    classLoader.defineClass(clazz.getName() + newNameSuffix, newClass);
            transformedClasses.add(newClassDef);
        }

        return transformedClasses;
    }

    private static byte[] renameClassNames(byte[] input, Map<String, String> nameMappings) {
        try {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            ClassVisitor visitor = new ClassRemapper(writer, new SimpleRemapper(nameMappings));
            ClassReader reader = new ClassReader(input);
            reader.accept(visitor, ClassReader.EXPAND_FRAMES);
            return writer.toByteArray();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    static void callMethod(
            Class<?> clazz, Object instance, String methodName, boolean isPrivateMethod)
            throws NoSuchMethodException, IllegalAccessException {
        Method method = clazz.getDeclaredMethod(methodName);
        if (isPrivateMethod) {
            method.setAccessible(true);
        }
        try {
            method.invoke(instance);
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            if (!(t instanceof RuntimeException)) {
                throw new RuntimeException(t);
            } else {
                throw (RuntimeException) t;
            }
        }
    }

    // We need to create a new class loader to make a protected defineClass method into a public
    // method.
    private static class TestClassLoader extends ClassLoader {

        TestClassLoader(ClassLoader parent) {
            super(parent);
        }

        public Class<?> defineClass(String name, byte[] buffer) {
            return defineClass(name, buffer, 0, buffer.length);
        }
    }
}

/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.testutils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runner.manipulation.Sorter;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

public class JarTestSuiteRunner extends Suite {

    /** Putatively temporary mechanism to avoid running certain classes. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface ExcludeClasses {
        Class<?>[] value();
    }

    public JarTestSuiteRunner(Class<?> suiteClass, RunnerBuilder builder) throws InitializationError, ClassNotFoundException, IOException {
        super(builder, suiteClass, getTestClasses(suiteClass));
        final String seed = System.getProperty("test.seed");
        if (seed != null) {
            randomizeTestOrder(Long.parseLong(seed));
        }
    }

    private void randomizeTestOrder(long seed) {
        Map<Description, Integer> values = new HashMap<>();
        Random random = new Random(seed);
        assign(getDescription(), random, values);
        super.sort(new Sorter(Comparator.comparingInt(values::get)));
    }

    private static void assign(
            Description description, Random random, Map<Description, Integer> values) {
        values.put(description, random.nextInt());
        for (Description child : description.getChildren()) {
            assign(child, random, values);
        }
    }

    private static Class<?>[] getTestClasses(Class<?> suiteClass) throws ClassNotFoundException, IOException {
        List<Class<?>> testClasses = new ArrayList<>();
        final Set<String> excludeClassNames = new HashSet<>();
        String name = System.getProperty("test.suite.jar");
        if (name != null) {
            final ClassLoader loader = JarTestSuite.class.getClassLoader();
            if (loader instanceof URLClassLoader) {
                for (URL url : ((URLClassLoader) loader).getURLs()) {
                    if (url.getPath().endsWith(name)) {
                        testClasses.addAll(getTestClasses(url, loader));
                    }
                }
            }
            excludeClassNames.addAll(classNamesToExclude(suiteClass, testClasses));
        }
        return testClasses.stream().filter(c -> !excludeClassNames.contains(c.getCanonicalName())).toArray(Class<?>[]::new);
    }

    /** Putatively temporary mechanism to avoid running certain classes. */
    private static Set<String> classNamesToExclude(Class<?> suiteClass, List<Class<?>> testClasses) {
        Set<String> testClassNames = testClasses.stream().map(Class::getCanonicalName).collect(Collectors.toSet());
        Set<String> excludeClassNames = new HashSet<>();
        ExcludeClasses annotation = suiteClass.getAnnotation(ExcludeClasses.class);
        if (annotation != null) {
            for (Class<?> classToExclude : annotation.value()) {
                String className = classToExclude.getCanonicalName();
                if (!excludeClassNames.add(className)) {
                    throw new RuntimeException(String.format(
                      "on %s, %s value duplicated: %s", suiteClass.getSimpleName(), ExcludeClasses.class.getSimpleName(), className));
                }
                if (!testClassNames.contains(className)) {
                    throw new RuntimeException(String.format(
                      "on %s, %s value not found: %s", suiteClass.getSimpleName(), ExcludeClasses.class.getSimpleName(), className));
                }
            }
        }
        return excludeClassNames;
    }

    private static List<Class<?>> getTestClasses(URL url, ClassLoader loader) throws ClassNotFoundException, IOException {
        List<Class<?>> testClasses = new ArrayList<>();
        File file = new File(url.getFile());
        if (file.exists()) {
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
                ZipEntry ze;
                while ((ze = zis.getNextEntry()) != null) {
                    if (ze.getName().endsWith(".class")) {
                        String className = ze.getName().replaceAll("/", ".").replaceAll(".class$", "");
                        Class<?> aClass = loader.loadClass(className);
                        if (seemsLikeJUnit3(aClass) || seemsLikeJUnit4(aClass)) {
                            testClasses.add(aClass);
                        }
                    }
                }
            } catch (ZipException e) {
                System.err.println("Error while opening jar " + file.getName() + " : " + e.getMessage());
            }
        }
        return testClasses;
    }

    private static boolean seemsLikeJUnit3(Class<?> aClass) {
        return (TestCase.class.isAssignableFrom(aClass) || TestSuite.class.isAssignableFrom(aClass))
               && !Modifier.isAbstract(aClass.getModifiers());
    }

    private static boolean seemsLikeJUnit4(Class<?> aClass) {
        Predicate<Method> hasTestAnnotation = method -> method.isAnnotationPresent(Test.class);
        return aClass.isAnnotationPresent(RunWith.class)
                || Arrays.stream(aClass.getMethods()).anyMatch(hasTestAnnotation);
    }
}

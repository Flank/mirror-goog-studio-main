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
package com.android.testutils;

import static com.google.common.base.MoreObjects.firstNonNull;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Defines how to extract tests from class path jars or any particular given jar. Once built, tests
 * can be extracted using {@link #scanTestClasses}.
 *
 * <p>This lets test Suite's filter and run a subset of tests.
 */
public class TestGroup {
    private static final String JAVA_CLASS_PATH = "java.class.path";

    private final ClassLoader classLoader;
    private final boolean includeJUnit3;
    private final Set<String> classNamesToExclude;

    /** Returns a new TestGroup builder. */
    public static Builder builder() {
        return new Builder();
    }

    private TestGroup(Builder builder) {
        this.classLoader = builder.classLoader;
        this.includeJUnit3 = builder.includeJUnit3;
        this.classNamesToExclude = firstNonNull(builder.classNamesToExclude, ImmutableSet.of());
    }

    /**
     * Returns test classes from the classpath, only looking at jars ending with jarSuffix.
     */
    public List<Class<?>> scanTestClasses(String jarSuffix)
            throws IOException, ClassNotFoundException {
        return excludeClasses(scanClassPath(jarSuffix));
    }

    /** Returns test classes found in testJar. */
    public List<Class<?>> scanTestClasses(Path testJar)
            throws IOException, ClassNotFoundException {
        return excludeClasses(scanTestJar(testJar.toString()));
    }

    private List<Class<?>> excludeClasses(List<Class<?>> classes) {
        Set<String> testClassNames = classes.stream()
                .map(Class::getCanonicalName)
                .collect(Collectors.toSet());
        if (!testClassNames.containsAll(classNamesToExclude)) {
            classNamesToExclude.removeAll(testClassNames);
            throw new RuntimeException(String.format(
                    "Classes excluded but not found: %s", classNamesToExclude
            ));
        }
        List<Class<?>> filteredClasses = classes.stream()
                .filter(c -> !classNamesToExclude.contains(c.getCanonicalName()))
                .collect(Collectors.toList());
        return filteredClasses;
    }

    private List<Class<?>> scanTestJar(String jarPath) throws IOException, ClassNotFoundException {
        return loadClasses(jarPath, classLoader)
                .stream()
                .filter(c -> seemsLikeJUnit4(c) || includeJUnit3 && seemsLikeJUnit3(c))
                .collect(Collectors.toList());
    }

    private List<Class<?>> scanClassPath(String classpathJarSuffix)
            throws IOException, ClassNotFoundException {
        ArrayList<Class<?>> testClasses = new ArrayList<>();
        String classpathJarSuffixLowerCase = classpathJarSuffix.toLowerCase(Locale.US);

        Queue<String> paths =
                new ArrayDeque<>(
                        Arrays.asList(
                                System.getProperty(JAVA_CLASS_PATH).split(File.pathSeparator)));
        while (!paths.isEmpty()) {
            String path = paths.remove();
            // Lower case in order to avoid issues on Windows/Mac.
            String pathLowerCase = path.toLowerCase(Locale.US);
            if (pathLowerCase.endsWith(classpathJarSuffixLowerCase)) {
                testClasses.addAll(scanTestJar(path));
            }
            addManifestClassPath(path, paths);
        }
        return testClasses;
    }

    private static void addManifestClassPath(String jarPath, Queue<String> existingPaths)
            throws IOException {
        if (jarPath.endsWith(".jar")) {
            File file = new File(jarPath);
            try (ZipFile zipFile = new ZipFile(file)) {
                ZipEntry entry = zipFile.getEntry("META-INF/MANIFEST.MF");
                if (entry != null) {
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        Manifest manifest = new Manifest(is);
                        Attributes attributes = manifest.getMainAttributes();
                        String cp = attributes.getValue("Class-Path");
                        if (cp != null) {
                            String[] paths = cp.split(" ");
                            for (String path : paths) {
                                File absoluteFile = new File(path);
                                if (absoluteFile.exists()) {
                                    existingPaths.add(path);
                                } else {
                                    File relFile = new File(file.getParentFile(), path);
                                    if (relFile.exists()) {
                                        existingPaths.add(relFile.getAbsolutePath());
                                    } else {
                                        System.err.println(
                                                "Cannot find class-path jar: "
                                                        + path
                                                        + " referenced from "
                                                        + file.getName());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Returns a list of JUnit test classes contained in the JAR file. */
    private static List<Class<?>> loadClasses(String jar, ClassLoader loader)
            throws ClassNotFoundException, IOException {
        List<Class<?>> testClasses = new ArrayList<>();
        File file = new File(jar);
        if (file.exists()) {
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
                ZipEntry ze;
                while ((ze = zis.getNextEntry()) != null) {
                    if (ze.getName().endsWith(".class")) {
                        String className =
                                ze.getName().replaceAll("/", ".").replaceAll(".class$", "");
                        Class<?> aClass = loader.loadClass(className);
                        testClasses.add(aClass);
                    }
                }
            } catch (ZipException e) {
                System.err.println(
                        "Error while opening jar " + file.getName() + " : " + e.getMessage());
            }
        }
        return testClasses;
    }

    private static boolean seemsLikeJUnit3(Class<?> aClass) {
        boolean junit3 =
                (TestCase.class.isAssignableFrom(aClass)
                        || TestSuite.class.isAssignableFrom(aClass))
                        && !Modifier.isAbstract(aClass.getModifiers());
        if (!junit3) {
            return false;
        }
        StringBuilder failures = new StringBuilder();
        // Assert no @Ignore annotations used.
        for (Method method : aClass.getMethods()) {
            if (method.getAnnotation(Ignore.class) != null) {
                failures.append(
                        String.format(
                                "@Ignore is used on method '%s$2' on JUnit3 test class '%s$1'\n",
                                aClass.getName(), method.getName()));
            }
            if (method.getAnnotation(Test.class) != null) {
                failures.append(
                        String.format(
                                "@Test is used on method '%s$2' on JUnit3 test class '%s$1'\n",
                                aClass.getName(), method.getName()));
            }
        }
        if (failures.length() > 0) {
            throw new RuntimeException(failures.toString());
        }
        return true;
    }

    private static boolean seemsLikeJUnit4(Class<?> aClass) {
        Predicate<Method> hasTestAnnotation = method -> method.isAnnotationPresent(Test.class);
        return (aClass.isAnnotationPresent(RunWith.class)
                || Arrays.stream(aClass.getMethods()).anyMatch(hasTestAnnotation))
                && !Modifier.isAbstract(aClass.getModifiers());
    }

    /** A TestGroup builder. */
    public static class Builder {
        ClassLoader classLoader;
        boolean includeJUnit3;
        Set<String> classNamesToExclude;

        private Builder() {
            this.classLoader = Thread.currentThread().getContextClassLoader();
        }

        /** Sets the class loader for loading test classes. */
        public Builder setClassLoader(ClassLoader classLoader) {
            this.classLoader = Preconditions.checkNotNull(classLoader);
            return this;
        }

        /** Includes JUnit3 test classes in the TestGroup. */
        public Builder includeJUnit3() {
            this.includeJUnit3 = true;
            return this;
        }

        /** Excludes the class names from being present in the TestGroup. */
        public Builder excludeClassNames(Set<String> classNamesToExclude) {
            this.classNamesToExclude = classNamesToExclude;
            return this;
        }

        /** Returns a new TestGroup. */
        public TestGroup build() {
            return new TestGroup(this);
        }
    }
}

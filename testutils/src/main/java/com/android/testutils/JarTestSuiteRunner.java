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

import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.base.Stopwatch;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Sorter;
import org.junit.runner.notification.RunNotifier;
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

    private static final String JAVA_CLASS_PATH = "java.class.path";

    private static final ILogger logger = new StdLogger(StdLogger.Level.INFO);

    private final boolean isBazelIntegrationTestsSuite;

    public JarTestSuiteRunner(Class<?> suiteClass, RunnerBuilder builder) throws InitializationError, ClassNotFoundException, IOException {
        super(new DelegatingRunnerBuilder(builder), suiteClass, getTestClasses(suiteClass));
        isBazelIntegrationTestsSuite =
                suiteClass
                        .getName()
                        .equals("com.android.build.gradle.integration.BazelIntegrationTestsSuite");
        final String seed = System.getProperty("test.seed");
        if (seed != null) {
            randomizeTestOrder(Long.parseLong(seed));
        }
        useAbsoluteForClasspath();
    }

    @Override
    protected void runChild(Runner runner, RunNotifier notifier) {
        // Logs the test class that will be invoked, temporarily added to investigate tests timing
        // out issue b/78568459
        if (isBazelIntegrationTestsSuite) {
            Stopwatch stopwatch = Stopwatch.createStarted();
            logger.info("Running " + describeChild(runner).getClassName());
            super.runChild(runner, notifier);
            logger.info(
                    describeChild(runner).getClassName() + " finished running in %d secs",
                    stopwatch.stop().elapsed(TimeUnit.SECONDS));
        } else {
            super.runChild(runner, notifier);
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

    /**
     * Rewrite java.class.path system property to use absolute paths. This is to work around the
     * limitation in Gradle 4.9-rc-1 that prevents relative paths in the classpath.
     */
    private static void useAbsoluteForClasspath() {
        Object javaClassPath = System.getProperties().get(JAVA_CLASS_PATH);
        if (javaClassPath instanceof String) {
            String classPath = (String) javaClassPath;
            String[] paths = classPath.split(File.pathSeparator);

            Path workspace = TestUtils.getWorkspaceRoot().toPath();
            String absolutePaths =
                    Arrays.stream(paths)
                            .map(workspace::resolve)
                            .map(Path::toString)
                            .collect(Collectors.joining(File.pathSeparator));

            System.setProperty(JAVA_CLASS_PATH, absolutePaths);
        }
    }

    private static Class<?>[] getTestClasses(Class<?> suiteClass) throws ClassNotFoundException, IOException {
        List<Class<?>> testClasses = new ArrayList<>();
        final Set<String> excludeClassNames = new HashSet<>();
        String name = System.getProperty("test.suite.jar");
        if (name != null) {
            final ClassLoader loader = JarTestSuite.class.getClassLoader();
            if (loader instanceof URLClassLoader) {
                Queue<URL> urls = new ArrayDeque<>();
                urls.addAll(Arrays.asList(((URLClassLoader)loader).getURLs()));
                while (!urls.isEmpty()) {
                    URL url = urls.remove();
                    if (url.getPath().endsWith(name)) {
                        testClasses.addAll(getTestClasses(url, loader));
                    }
                    addManifestClassPath(url, urls);
                }
            }
            excludeClassNames.addAll(classNamesToExclude(suiteClass, testClasses));
        }
        return testClasses.stream().filter(c -> !excludeClassNames.contains(c.getCanonicalName())).toArray(Class<?>[]::new);
    }

    private static void addManifestClassPath(URL jarUrl, Queue<URL> urls) throws IOException {
        if (jarUrl.getPath().endsWith(".jar")) {
            File file = new File(jarUrl.getFile());
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
                                try {
                                    URL url = new URL(path);
                                    urls.add(url);
                                } catch (MalformedURLException e) {
                                    File relFile = new File(file.getParentFile(), path);
                                    if (relFile.exists()) {
                                        urls.add(relFile.toURI().toURL());
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
                        if (seemsLikeJUnit4(aClass) || seemsLikeJUnit3(aClass)) {
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
        return aClass.isAnnotationPresent(RunWith.class)
                || Arrays.stream(aClass.getMethods()).anyMatch(hasTestAnnotation);
    }
}

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
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Sorter;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    public JarTestSuiteRunner(Class<?> suiteClass, RunnerBuilder builder)
            throws InitializationError, ClassNotFoundException, IOException {
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
            TestExecutionTimeLogger.log();
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

            Path workspace = TestUtils.getWorkspaceRoot();
            String absolutePaths =
                    Arrays.stream(paths)
                            .map(workspace::resolve)
                            .map(Path::toString)
                            .collect(Collectors.joining(File.pathSeparator));

            System.setProperty(JAVA_CLASS_PATH, absolutePaths);
        }
    }

    private static Class<?>[] getTestClasses(Class<?> suiteClass)
            throws ClassNotFoundException, IOException {
        String jarSuffix = System.getProperty("test.suite.jar");
        if (jarSuffix == null) {
            throw new RuntimeException(
                    "Must set test.suite.jar to the name of the jar containing JUnit tests");
        }

        long start = System.currentTimeMillis();
        TestGroup testGroup = TestGroup.builder()
                .setClassLoader(suiteClass.getClassLoader())
                .includeJUnit3()
                .excludeClassNames(classNamesToExclude(suiteClass))
                .build();
        List<Class<?>> testClasses = testGroup.scanTestClasses(jarSuffix);
        System.out.printf("Found %d tests in %dms%n", testClasses.size(), (System.currentTimeMillis() - start));
        if (testClasses.isEmpty()) {
            throw new RuntimeException("No tests found in class path using suffix: " + jarSuffix);
        }
        return testClasses.toArray(new Class<?>[0]);
    }


    /** Putatively temporary mechanism to avoid running certain classes. */
    private static Set<String> classNamesToExclude(
            Class<?> suiteClass) {
        Set<String> excludeClassNames = new HashSet<>();
        ExcludeClasses annotation = suiteClass.getAnnotation(ExcludeClasses.class);
        if (annotation != null) {
            for (Class<?> classToExclude : annotation.value()) {
                String className = classToExclude.getCanonicalName();
                if (!excludeClassNames.add(className)) {
                    throw new RuntimeException(
                            String.format(
                                    "on %s, %s value duplicated: %s",
                                    suiteClass.getSimpleName(),
                                    ExcludeClasses.class.getSimpleName(),
                                    className));
                }
            }
        }
        return excludeClassNames;
    }
}

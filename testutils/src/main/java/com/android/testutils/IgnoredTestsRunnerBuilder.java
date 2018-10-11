/*
 * Copyright (C) 2018 The Android Open Source Project
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

import junit.framework.TestCase;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.internal.builders.AllDefaultPossibilitiesBuilder;
import org.junit.internal.builders.AnnotatedBuilder;
import org.junit.internal.builders.IgnoredBuilder;
import org.junit.internal.builders.IgnoredClassRunner;
import org.junit.internal.builders.JUnit3Builder;
import org.junit.internal.builders.JUnit4Builder;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.JUnit4;
import org.junit.runners.Suite;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.TestClass;

/**
 * This class builds {@link Runner Runners} that run only tests annotated with {@link Ignore}. For
 * JUnit3 tests, this is limited to class-level granularity: applying {@code @Ignore} to a JUnit3
 * test method has no effect. Tests annotated with {@link RunWith} are run only if they are {@link
 * Suite Suites} or are {@code @RunWith(JUnit4.class)}, as in general it's impossible to know how a
 * custom runner will behave. Most notably, this means {@link Parameterized} tests are not run
 * (yet).
 *
 * <p>{@link AllDefaultPossibilitiesBuilder} builds a {@code Runner} by querying the 5 sub-builders
 * obtained from calling {@code ignoredBuilder()}, {@code annotatedBuilder()}, {@code
 * suiteMethodBuilder()}, {@code junit3Builder()}, and {@code junit4Builder()} in turn until one of
 * them returns a non-null runner. Here we override some of these methods to return {@link
 * org.junit.runners.model.RunnerBuilder RunnerBuilders} that create runners that invert the sense
 * of {@code Ignore} annotations.
 */
public class IgnoredTestsRunnerBuilder extends AllDefaultPossibilitiesBuilder {

    IgnoredTestsRunnerBuilder() {
        super(true);
    }

    @Override
    protected IgnoredBuilder ignoredBuilder() {
        return new IgnoredBuilder() {
            @Override
            public Runner runnerForClass(Class<?> testClass) {
                return null; // we need to run ignored tests, so let everything fall through to the later builders
            }
        };
    }

    @Override
    protected AnnotatedBuilder annotatedBuilder() {
        return new AnnotatedBuilder(this) {
            @Override
            public Runner runnerForClass(Class<?> testClass) throws Exception {
                RunWith annotation = testClass.getAnnotation(RunWith.class);
                if (annotation == null) {
                    return null;
                } else if (annotation.value().equals(JUnit4.class)) {
                    try {
                        return junit4Builder().runnerForClass(testClass);
                    } catch (Throwable t) {
                        // JUnit4Builder.runnerForClass throws Throwable, while AnnotatedBuilder throws Exception
                        throw new RuntimeException(t);
                    }
                } else if (Suite.class.isAssignableFrom(testClass)) {
                    return super.runnerForClass(testClass); // don't ignore suites
                } else {
                    return new IgnoredClassRunner(testClass); // ignore tests with custom runners
                }
            }
        };
    }

    @Override
    protected JUnit3Builder junit3Builder() {
        return new JUnit3Builder() {
            @Override
            public Runner runnerForClass(Class<?> testClass) throws Throwable {
                if (testClass.getAnnotation(Ignore.class) != null) {
                    // the default JUnit3 runner will run it regardless of the @Ignore annotation
                    return super.runnerForClass(testClass);
                } else if (TestCase.class.isAssignableFrom(testClass)) {
                    // returning null here would cause us to fall through to the JUnit4Builder, which throws NoTestsRemainException on JUnit3 classes
                    return new IgnoredClassRunner(testClass); // ignore what normally would be run
                } else {
                    return null;
                }
            }
        };
    }

    @Override
    protected JUnit4Builder junit4Builder() {
        return new JUnit4Builder() {
            @Override
            public Runner runnerForClass(Class<?> testClass) throws Throwable {
                boolean classIgnored = testClass.getAnnotation(Ignore.class) != null;
                // if anything in the class is ignored, create a new BlockJUnit4ClassRunner to run those tests
                if (classIgnored
                        || new TestClass(testClass)
                                .getAnnotatedMethods(Test.class)
                                .stream()
                                .anyMatch(method -> method.getAnnotation(Ignore.class) != null)) {
                    return new BlockJUnit4ClassRunner(testClass) {
                        @Override
                        public boolean isIgnored(FrameworkMethod method) {
                            return !(classIgnored || super.isIgnored(method));
                        }
                    };
                }
                return new IgnoredClassRunner(testClass); // don't run what normally would be run
            }
        };
    }
}

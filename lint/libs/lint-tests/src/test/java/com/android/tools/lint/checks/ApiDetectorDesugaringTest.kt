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

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Desugaring
import com.android.tools.lint.detector.api.Detector

@Suppress("PrivatePropertyName")
class ApiDetectorDesugaringTest : AbstractCheckTest() {
    fun testTryWithResources() {
        // No desugaring
        val expected =
            """
            src/main/java/test/pkg/MultiCatch.java:10: Error: Multi-catch with these reflection exceptions requires API level 19 (current min is 1) because they get compiled to the common but new super type ReflectiveOperationException. As a workaround either create individual catch statements, or catch Exception. [NewApi]
                    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/main/java/test/pkg/TryWithResources.java:9: Error: Try-with-resources requires API level 19 (current min is 1) [NewApi]
                    try (BufferedReader br = new BufferedReader(new FileReader(path))) {
                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        lint().files(
            manifest().minSdk(1),
            tryWithResources,
            multiCatch,
            gradleVersion231
        ).run().expect(expected)
    }

    fun testTryWithResourcesOkDueToCompileSdk() {
        lint().files(
            manifest().minSdk(19),
            tryWithResources,
            multiCatch,
            gradleVersion231
        ).run().expectClean()
    }

    fun testTryWithResourcesOkDueToDesugar() {
        lint().files(
            manifest().minSdk(19),
            tryWithResources,
            multiCatch,
            gradleVersion24_language18
        ).run().expectClean()
    }

    fun testTryWithResourcesOutsideAndroid() {
        lint().files(
            manifest().minSdk(1),
            tryWithResources,
            multiCatch,
            gradle("apply plugin: 'java'\n")
        ).run().expectClean()
    }

    fun testTryWithResourcesOldGradlePlugin() {
        val expected =
            """
            src/main/java/test/pkg/TryWithResources.java:9: Error: Try-with-resources requires API level 19 (current min is 1) [NewApi]
                    try (BufferedReader br = new BufferedReader(new FileReader(path))) {
                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            manifest().minSdk(1),
            gradleVersion231,
            tryWithResources
        ).run().expect(expected)
    }

    fun testTryWithResourcesNewPluginLanguage17() {
        val expected =
            """
            src/main/java/test/pkg/TryWithResources.java:9: Error: Try-with-resources requires API level 19 (current min is 1) [NewApi]
                    try (BufferedReader br = new BufferedReader(new FileReader(path))) {
                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            manifest().minSdk(1),
            gradleVersion24_language17,
            tryWithResources
        ).run().expect(expected)
    }

    fun testTryWithResourcesDesugar() {
        lint().files(
            manifest().minSdk(1),
            gradleVersion24_language18,
            tryWithResources
        ).run().expectClean()
    }

    fun testDesugarMethods() {
        // Desugar inlines Objects.requireNonNull(foo) so don't flag this if using Desugar
        // Ditto for Throwable.addSuppressed.

        lint().files(
            java(
                """
                package test.pkg;

                import java.util.Objects;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class DesugarTest {
                    public void testRequireNull(Object foo) {
                        Objects.requireNonNull(foo); // Desugared, should not generate warning
                        Objects.requireNonNull(foo, "message"); // Should generate API warning
                    }

                    public void addThrowable(Throwable t1, Throwable t2) {
                        t1.addSuppressed(t2); // Desugared, should not generate warning
                    }
                }
                """
            ).indented(),
            gradleVersion24_language18
        ).run().expect(
            """
            src/main/java/test/pkg/DesugarTest.java:9: Error: Call requires API level 19 (current min is 1): java.util.Objects#requireNonNull [NewApi]
                    Objects.requireNonNull(foo, "message"); // Should generate API warning
                            ~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testDefaultMethodsDesugar() {
        // Default methods require minSdkVersion=N

        lint().files(
            manifest().minSdk(15),
            java(
                "src/test/pkg/InterfaceMethodTest.java",
                """
                package test.pkg;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public interface InterfaceMethodTest {
                    void someMethod();
                    default void method2() {
                        System.out.println("test");
                    }
                    static void method3() {
                        System.out.println("test");
                    }
                }"""
            ).indented(),
            gradleVersion24_language18
        ).run().expectClean()
    }

    fun testDesugarCompare() {
        lint().files(
            manifest().minSdk(1),
            java(
                """
                package test.pkg;

                // Desugar rewrites these
                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class CompareTest {
                    public void testLong(long value1, long value2) {
                        int result3 = Long.compare(value1, value2);
                    }

                    public int testFloat(float value1, float value2) {
                        return Float.compare(value1, value2); // OK
                    }

                    public int testBoolean(boolean value1, boolean value2) {
                        return Boolean.compare(value1, value2);
                    }

                    public int testDouble(double value1, double value2) {
                        return Double.compare(value1, value2); // OK
                    }

                    public int testByte(byte value1, byte value2) {
                        return Byte.compare(value1, value2);
                    }

                    public int testChar(char value1, char value2) {
                        return Character.compare(value1, value2);
                    }

                    public int testInt(int value1, int value2) {
                        return Integer.compare(value1, value2);
                    }

                    public int testShort(short value1, short value2) {
                        return Short.compare(value1, value2);
                    }
                }
                """
            ).indented(),
            gradleVersion24_language18
        ).run().expectClean()
    }

    fun testDesugarJava8LibsKotlin() {
        lint().files(
            manifest().minSdk(1),
            kotlin(
                """
                @file:Suppress("unused", "UNUSED_VARIABLE")

                package test.pkg

                import java.time.Duration
                import java.util.*
                import java.util.function.Consumer
                import java.util.function.Function

                class Test {
                    fun time(duration: java.time.Duration) {
                        val negative = duration.isNegative
                        val duration2 = Duration.ofMillis(1000L)
                   }

                    fun streams(list: ArrayList<String>) {
                        list.stream().forEach { it -> Consumer<String> { println(it) }  }
                    }

                    fun functions(func: Function<String, String>) {
                        func.apply("hello")
                    }


                    // Type use annotations
                    @Target(AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER)
                    annotation class MyTypeUse
                }
                """
            ).indented()
        ).desugaring(Desugaring.FULL).run().expectClean()
    }

    fun testDesugarJava8LibsJavaAndroid() {
        lint().files(
            manifest().minSdk(1),
            java(
                """
                package test.pkg;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Repeatable;
                import java.lang.annotation.Target;
                import java.util.ArrayList;
                import java.util.Arrays;
                import java.util.Iterator;
                import java.util.Optional;
                import java.util.stream.BaseStream;
                import java.util.stream.Stream;

                @SuppressWarnings({"unused", "SimplifyStreamApiCallChains", "OptionalGetWithoutIsPresent", "OptionalUsedAsFieldOrParameterType", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class Test {

                    public void utils(java.util.Collection<String> collection) {
                        collection.removeIf(s -> s.length() > 5);
                    }

                    public void streams(ArrayList<String> list, String[] array) {
                        list.stream().forEach(s -> System.out.println(s.length()));
                        Stream<String> stream = Arrays.stream(array);
                    }

                    public void otherUtils(Optional<String> optional, Iterator<String> iterator,
                                           java.util.concurrent.atomic.AtomicInteger integer) {
                        double max = java.lang.Double.max(5, 6);
                        int exact = java.lang.Math.toIntExact(5L);
                        String got = optional.get();
                        iterator.forEachRemaining(s -> System.out.println(s.length()));
                        integer.addAndGet(5);
                    }

                    public void bannedMembers(java.util.Collection collection, java.util.stream.BaseStream<String> base) {
                        Stream stream = collection.parallelStream();
                        BaseStream parallel = base.parallel();
                    }

                    // Type use annotations

                    @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
                    @interface MyInner {
                    }

                    // Repeatable annotations

                    public @interface Schedules {
                        Schedule[] value();
                    }

                    @Repeatable(Schedules.class)
                    public @interface Schedule {
                        String dayOfMonth() default "first";
                        String dayOfWeek() default "Mon";
                        int hour() default 12;
                    }
                }
                """
            ).indented()
        ).desugaring(Desugaring.FULL).run().expect(
            """
            src/test/pkg/Test.java:35: Error: Call requires API level 24 (current min is 1): java.util.Collection#parallelStream [NewApi]
                    Stream stream = collection.parallelStream();
                                               ~~~~~~~~~~~~~~
            src/test/pkg/Test.java:36: Error: Call requires API level 24 (current min is 1): java.util.stream.BaseStream#parallel [NewApi]
                    BaseStream parallel = base.parallel();
                                               ~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }

    fun testDesugarJava8LibsJavaLib() {
        val lib = project(
            java(
                """
                package test.pkg.lib;

                import java.util.ArrayList;
                import java.util.function.IntBinaryOperator;

                @SuppressWarnings({"unused", "SimplifyStreamApiCallChains", "OptionalGetWithoutIsPresent", "OptionalUsedAsFieldOrParameterType", "Convert2MethodRef", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class Test {
                    public @interface Something {
                        javax.lang.model.type.TypeKind value();
                    }

                    @Something(javax.lang.model.type.TypeKind.PACKAGE)
                    public void usingTypeKind() {
                    }

                    public void otherUtils(java.util.concurrent.atomic.AtomicInteger integer, IntBinaryOperator operator) {
                        new ArrayList<String>().stream().forEach(s -> System.out.println(s.length()));
                        integer.addAndGet(5);
                        integer.accumulateAndGet(5, operator);
                    }
                }
                """
            ).indented(),
            // Make sure it's treated as a plain library
            gradle(
                """
                apply plugin: 'java'
                """
            ).indented()
        )

        val main = project(
            manifest().minSdk(1)
        ).dependsOn(lib)

        lint().projects(lib, main).desugaring(Desugaring.FULL).run().expectClean()
    }

    private val gradleVersion24_language18 = gradle(
        """
        buildscript {
            repositories {
                jcenter()
            }
            dependencies {
                classpath 'com.android.tools.build:gradle:2.4.0-alpha8'
            }
        }
        android {
            compileOptions {
                sourceCompatibility JavaVersion.VERSION_1_8
                targetCompatibility JavaVersion.VERSION_1_8
            }
        }"""
    ).indented()

    private val gradleVersion24_language17 = gradle(
        """
        buildscript {
            repositories {
                jcenter()
            }
            dependencies {
                classpath 'com.android.tools.build:gradle:2.4.0-alpha8'
            }
        }
        android {
            compileOptions {
                sourceCompatibility JavaVersion.VERSION_1_7
                targetCompatibility JavaVersion.VERSION_1_7
            }
        }"""
    ).indented()

    private val gradleVersion231 = gradle(
        """
        buildscript {
            repositories {
                jcenter()
            }
            dependencies {
                classpath 'com.android.tools.build:gradle:2.3.1'
            }
        }"""
    ).indented()

    private val tryWithResources = java(
        """
        package test.pkg;

        import java.io.BufferedReader;
        import java.io.FileReader;
        import java.io.IOException;
        @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
        public class TryWithResources {
            public String testTryWithResources(String path) throws IOException {
                try (BufferedReader br = new BufferedReader(new FileReader(path))) {
                    return br.readLine();
                }
            }
        }
        """
    ).indented()

    private val multiCatch = java(
        """
        package test.pkg;

        import java.lang.reflect.InvocationTargetException;

        @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic", "TryWithIdenticalCatches"})
        public class MultiCatch {
            public void testMultiCatch() {
                try {
                    Class.forName("java.lang.Integer").getMethod("toString").invoke(null);
                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
        """
    ).indented()

    override fun getDetector(): Detector = ApiDetector()
}

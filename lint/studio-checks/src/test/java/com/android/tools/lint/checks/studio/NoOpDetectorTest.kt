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

@file:Suppress("SpellCheckingInspection")

package com.android.tools.lint.checks.studio

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import org.junit.Test

class NoOpDetectorTest {

    @Test
    fun testProblems() {
        studioLint()
            .files(
                kotlin(
                    """
                    package test.pkg
                    class Foo {
                        var a: Int = 1
                        var b: Int = 5
                    }

                    fun test(foo: Foo, bar: Bar, buffer: java.nio.ByteBuffer, node: org.w3c.dom.Node, opt: java.util.Optional<String>) {
                        val x = foo.b // OK 1
                        foo.b // WARN 1
                        bar.bar // WARN 2
                        bar.text // WARN 3
                        bar.getText() // WARN 4
                        bar.getFoo() // WARN 5
                        bar.getFoo2("") // OK 2
                        bar.getFoo3() // OK 3
                        bar.getFoo4() // OK 4
                        bar.getFoo5() // WARN 6
                        //noinspection ResultOfMethodCallIgnored
                        bar.getFoo5() // OK 5
                        bar.getBar() // WARN 7
                        "".toString() // WARN 8
                        java.io.File("").path // WARN 9
                        java.io.File("").getPath() // WARN 10
                        buffer.getShort() // OK 6
                        buffer.short // OK 7
                        synchronized (node.getOwnerDocument()) { // OK 8
                        }
                        bar.getPreferredSize() // OK 9
                        bar.computeRange() // WARN 11
                        bar.computeRange2() //OK 10
                        bar.getFoo5().not() // OK 11
                        if (foo.a > foo.b) foo.a else foo.b // WARN 12 a and b
                        val max = if (foo.a > foo.b) foo.a else foo.b // OK 12
                        // In the future, consider including this but now used for lots of side effect methods
                        // like future computations etc
                        opt.get()

                        // In an earlier version we included
                        //   methodName == "build" && method.containingClass?.name?.contains("Builder") == true
                        // as well -- but this had a number of false positives; build in many usages have side
                        // effects - they don't just return the result.
                        Gradle().build() // OK 13
                        MyBuilder().build() // consider flagging
                    }
                    class Gradle { fun build(): String = "done" }
                    class MyBuilder { fun build(): String = "done"}
                """
                ).indented(),
                java(
                    """
                    package test.pkg;
                    public class Bar {
                        public String getBar() { return "hello"; }
                        public String getText() { return getBar(); }
                        public String getFoo() { return field; }
                        public String getFoo2(String s) { return field; }
                        public void getFoo3() { field = "world"; }
                        public int getFoo4() { return field2++; }
                        public boolean getFoo5() { return !field3; }
                        public boolean getFoo6() { return !field3; }
                        private String field;
                        private int field2 = 0;
                        private boolean field3 = false;
                        public int getPreferredSize() { return null; }
                        @org.jetbrains.annotations.Contract(pure=true)
                        public int computeRange() { return 0; }
                        @org.jetbrains.annotations.Contract(pure=false)
                        public int computeRange2() { return 0; }
                    }
                    """
                ).indented(),
                kotlin(
                    """
                    package com.android.tools.idea.imports
                    class MavenClassRegistryManager {
                      companion object {
                        private var foo: Int = 10
                        @JvmStatic
                        fun getInstance(): Any = foo
                      }
                    }
                    class AutoRefresherForMavenClassRegistry {
                      override fun runActivity() {
                        // Start refresher
                        MavenClassRegistryManager.getInstance()
                      }
                    }
                    """
                ).indented(),
                kotlin(
                    """
                    package com.android.tools.lint.checks.infrastructure
                    import org.junit.Test
                    class GradleModelMockerTest {
                        @Test(expected = AssertionError::class)
                        fun testFailOnUnexpected() {
                            val mocker = Mocker()
                            mocker.getLintModule()
                        }
                    }
                    class Mocker {
                        private val module: String = "test"
                        fun getLintModule(): String {
                            return module
                        }
                    }
                    """
                ).indented(),
                java(
                    """
                    package com.android.annotations;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    public @interface VisibleForTesting {
                        enum Visibility { PROTECTED, PACKAGE, PRIVATE }
                        Visibility visibility() default Visibility.PRIVATE;
                    }
                    """
                ).indented(),
                java(
                    """
                package org.junit;
                public @interface Test {
                    Class<? extends Throwable> expected() default None.class;
                    long timeout() default 0L;
                }
                """
                ).indented(),
                java(
                    """
                    package org.jetbrains.annotations;
                    import java.lang.annotation.*;
                    public @interface Contract {
                      String value() default "";
                      boolean pure() default false;
                    }
                    """
                ).indented()
            )
            .issues(NoOpDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/Foo.kt:9: Warning: This reference is unused: b [NoOp]
                    foo.b // WARN 1
                        ~
                src/test/pkg/Foo.kt:10: Warning: This reference is unused: bar [NoOp]
                    bar.bar // WARN 2
                        ~~~
                src/test/pkg/Foo.kt:11: Warning: This reference is unused: text [NoOp]
                    bar.text // WARN 3
                        ~~~~
                src/test/pkg/Foo.kt:12: Warning: This call result is unused: getText [NoOp]
                    bar.getText() // WARN 4
                    ~~~~~~~~~~~~~
                src/test/pkg/Foo.kt:13: Warning: This call result is unused: getFoo [NoOp]
                    bar.getFoo() // WARN 5
                    ~~~~~~~~~~~~
                src/test/pkg/Foo.kt:17: Warning: This call result is unused: getFoo5 [NoOp]
                    bar.getFoo5() // WARN 6
                    ~~~~~~~~~~~~~
                src/test/pkg/Foo.kt:20: Warning: This call result is unused: getBar [NoOp]
                    bar.getBar() // WARN 7
                    ~~~~~~~~~~~~
                src/test/pkg/Foo.kt:21: Warning: This call result is unused: toString [NoOp]
                    "".toString() // WARN 8
                    ~~~~~~~~~~~~~
                src/test/pkg/Foo.kt:22: Warning: This reference is unused: path [NoOp]
                    java.io.File("").path // WARN 9
                                     ~~~~
                src/test/pkg/Foo.kt:23: Warning: This call result is unused: getPath [NoOp]
                    java.io.File("").getPath() // WARN 10
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/Foo.kt:29: Warning: This call result is unused: computeRange [NoOp]
                    bar.computeRange() // WARN 11
                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/Foo.kt:32: Warning: This reference is unused: a [NoOp]
                    if (foo.a > foo.b) foo.a else foo.b // WARN 12 a and b
                                           ~
                src/test/pkg/Foo.kt:32: Warning: This reference is unused: b [NoOp]
                    if (foo.a > foo.b) foo.a else foo.b // WARN 12 a and b
                                                      ~
                0 errors, 13 warnings
                """
            )
    }
}

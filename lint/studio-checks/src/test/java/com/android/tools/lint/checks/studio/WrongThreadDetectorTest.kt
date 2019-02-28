/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.lint.checks.studio

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import org.junit.Test

class WrongThreadDetectorTest {

    @Test
    fun testIncompatibleMethods() {
        studioLint()
            .files(
                java(
                    """
                    package test.pkg;
                    import com.android.annotations.concurrency.AnyThread;
                    import com.android.annotations.concurrency.Slow;
                    import com.android.annotations.concurrency.UiThread;

                    public class Test {
                        @Slow
                        public void slowMethod() {
                            uiThread(); // WARN
                            UiThreadClass.method(); // WARN
                        }

                        @UiThread
                        public void uiThread() {
                            slowMethod(); // WARN
                        }

                        @Slow
                        public void okSlow() {
                            slowMethod(); // OK
                            anyThread(); // OK
                        }

                        @UiThread
                        public void okUiThread() {
                            uiThread(); // OK
                            anyThread(); // OK
                            UiThreadClass.method(); // OK
                        }

                        @AnyThread
                        public void anyThread() {
                            uiThread(); // WARN
                            slowMethod(); // WARN
                        }
                    }
                """
                ).indented(),
                java(
                    """
                    package test.pkg;
                    import com.android.annotations.concurrency.UiThread;

                    @UiThread
                    public class UiThreadClass {
                        public void method() {
                        }
                    }
                """
                ).indented(),
                java(
                    """
                    package com.android.annotations.concurrency;

                    import java.lang.annotation.Documented;
                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.lang.annotation.Target;

                    @Documented
                    @Retention(RetentionPolicy.RUNTIME)
                    @Target(ElementType.METHOD)
                    public @interface Slow {}
                """
                ).indented(),
                java(
                    """
                    package com.android.annotations.concurrency;

                    import java.lang.annotation.Documented;
                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.lang.annotation.Target;

                    @Documented
                    @Retention(RetentionPolicy.RUNTIME)
                    @Target(ElementType.METHOD)
                    public @interface UiThread {}
                """
                ).indented(),
                java(
                    """
                    package com.android.annotations.concurrency;

                    import java.lang.annotation.Documented;
                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.lang.annotation.Target;

                    @Documented
                    @Retention(RetentionPolicy.RUNTIME)
                    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
                    public @interface AnyThread {}
                """
                ).indented()
            )
            .issues(WrongThreadDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/Test.java:9: Error: Method uiThread must be called from the UI thread, currently inferred thread is worker thread [WrongThread]
                        uiThread(); // WARN
                        ~~~~~~~~~~
                src/test/pkg/Test.java:10: Error: Method method must be called from the UI thread, currently inferred thread is worker thread [WrongThread]
                        UiThreadClass.method(); // WARN
                        ~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/Test.java:15: Error: Method slowMethod must be called from the worker thread, currently inferred thread is UI thread [WrongThread]
                        slowMethod(); // WARN
                        ~~~~~~~~~~~~
                src/test/pkg/Test.java:33: Error: Method uiThread must be called from the UI thread, currently inferred thread is any thread [WrongThread]
                        uiThread(); // WARN
                        ~~~~~~~~~~
                src/test/pkg/Test.java:34: Error: Method slowMethod must be called from the worker thread, currently inferred thread is any thread [WrongThread]
                        slowMethod(); // WARN
                        ~~~~~~~~~~~~
                5 errors, 0 warnings
                """
            )
    }

    @Test
    fun testImplicit() {
        studioLint()
            .files(
                java(
                    """
                    package test.pkg;
                    import com.intellij.openapi.application.Application;
                    import com.android.annotations.concurrency.Slow;
                    import com.android.annotations.concurrency.UiThread;
                    import com.intellij.openapi.actionSystem.AnAction;
                    import com.intellij.openapi.actionSystem.AnActionEvent;

                    public class Test {
                        @Slow
                        public void slowMethod() { }

                        private void fastMethod() { }

                        public void test() {
                            new AnAction() {
                                @UiThread
                                public void update(@NotNull AnActionEvent e) {
                                    fastMethod(); // OK
                                    slowMethod(); // WARN
                                }
                            };
                            new Application().invokeLater(() -> {
                                fastMethod(); // OK
                                slowMethod(); // WARN
                                fastMethod(); // OK
                            });
                            new Application().runOnPooledThread(() -> {
                                slowMethod(); // OK
                            });
                            new Application().invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    fastMethod(); // OK
                                    slowMethod(); // WARN
                                }
                            });
                            new Application().runOnPooledThread(new Runnable() {
                                @Override
                                public void run() {
                                    slowMethod(); // OK
                                }
                            });
                        }
                    }
                """
                ).indented(),
                java(
                    """
                    // Stub until test infrastructure passes the right class path for non-Android
                    // modules.
                    package com.intellij.openapi.application;
                    import com.android.annotations.concurrency.UiThread;
                    public class Application {
                        public void invokeLater(@UiThread Runnable run) { run.run(); }
                        /** Fake method to check that it does no trigger the warnings */
                        public void runOnPooledThread(Runnable run) { run.run(); }
                    }
                """
                ).indented(),
                java(
                    """
                    package com.android.annotations.concurrency;

                    import java.lang.annotation.Documented;
                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.lang.annotation.Target;

                    @Documented
                    @Retention(RetentionPolicy.RUNTIME)
                    @Target(ElementType.METHOD)
                    public @interface Slow {}
                """
                ).indented(),
                java(
                    """
                    package com.android.annotations.concurrency;

                    import java.lang.annotation.Documented;
                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.lang.annotation.Target;

                    @Documented
                    @Retention(RetentionPolicy.RUNTIME)
                    @Target(ElementType.METHOD)
                    public @interface UiThread {}
                """
                ).indented(),
                java(
                    """
                    package com.intellij.openapi.actionSystem;

                    public class AnActionEvent {}
                """
                ).indented(),
                java(
                    """
                    package com.intellij.openapi.actionSystem;

                    public class AnAction {
                        @UiThread
                        public void update(@NotNull AnActionEvent e) {
                        }
                    }
                """
                )
            )
            .issues(WrongThreadDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/Test.java:19: Error: Method slowMethod must be called from the worker thread, currently inferred thread is UI thread [WrongThread]
                                slowMethod(); // WARN
                                ~~~~~~~~~~~~
                src/test/pkg/Test.java:24: Error: Method slowMethod must be called from the worker thread, currently inferred thread is UI thread [WrongThread]
                            slowMethod(); // WARN
                            ~~~~~~~~~~~~
                src/test/pkg/Test.java:34: Error: Method slowMethod must be called from the worker thread, currently inferred thread is UI thread [WrongThread]
                                slowMethod(); // WARN
                                ~~~~~~~~~~~~
                3 errors, 0 warnings
                """
            )
    }
}

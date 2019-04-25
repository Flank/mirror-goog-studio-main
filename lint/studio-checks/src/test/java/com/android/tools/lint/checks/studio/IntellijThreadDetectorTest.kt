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

import com.android.tools.lint.checks.infrastructure.TestFiles.jar
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import org.junit.Test

@Suppress("ClassNameDiffersFromFileName") // For language injections.
class IntellijThreadDetectorTest {

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
                    import com.android.annotations.concurrency.WorkerThread;

                    public class Test {
                        @Slow
                        public void slowMethod() {
                            uiThread(); // WARN
                            UiThreadClass.method(); // WARN
                        }

                        @UiThread
                        public void uiThread() {
                            slowMethod(); // WARN
                            workerThread(); // WARN
                        }

                        @Slow
                        public void okSlow() {
                            slowMethod(); // OK
                            anyThread(); // OK
                            workerThread(); // OK
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
                            workerThread(); // WARN
                        }

                        @WorkerThread
                        public void workerThread() {
                            uiThread(); // WARN
                        }

                        @WorkerThread
                        public void okWorkerThread() {
                            workerThread(); // OK
                            slowMethod(); // OK
                            anyThread(); // OK
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
                    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE})
                    public @interface WorkerThread {}
                """
                ).indented()
            )
            .issues(IntellijThreadDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/Test.java:10: Error: Method uiThread must run on the UI thread, yet the currently inferred thread is a worker thread [WrongThread]
                        uiThread(); // WARN
                        ~~~~~~~~~~
                src/test/pkg/Test.java:11: Error: Method method must run on the UI thread, yet the currently inferred thread is a worker thread [WrongThread]
                        UiThreadClass.method(); // WARN
                        ~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/Test.java:16: Error: Method slowMethod is slow and thus should run on a worker thread, yet the currently inferred thread is the UI thread [WrongThread]
                        slowMethod(); // WARN
                        ~~~~~~~~~~~~
                src/test/pkg/Test.java:17: Error: Method workerThread is intended to run on a worker thread, yet the currently inferred thread is the UI thread [WrongThread]
                        workerThread(); // WARN
                        ~~~~~~~~~~~~~~
                src/test/pkg/Test.java:36: Error: Method uiThread must run on the UI thread, yet the currently inferred thread is any thread [WrongThread]
                        uiThread(); // WARN
                        ~~~~~~~~~~
                src/test/pkg/Test.java:37: Error: Method slowMethod is slow and thus should run on a worker thread, yet the currently inferred thread is any thread [WrongThread]
                        slowMethod(); // WARN
                        ~~~~~~~~~~~~
                src/test/pkg/Test.java:38: Error: Method workerThread is intended to run on a worker thread, yet the currently inferred thread is any thread [WrongThread]
                        workerThread(); // WARN
                        ~~~~~~~~~~~~~~
                src/test/pkg/Test.java:43: Error: Method uiThread must run on the UI thread, yet the currently inferred thread is a worker thread [WrongThread]
                        uiThread(); // WARN
                        ~~~~~~~~~~
                8 errors, 0 warnings
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
                    import com.android.annotations.concurrency.WorkerThread;
                    import com.intellij.openapi.actionSystem.AnAction;
                    import com.intellij.openapi.actionSystem.AnActionEvent;

                    @SuppressWarnings("Convert2Lambda")
                    public class Test {
                        @Slow
                        public boolean slowMethod() { }

                        private void fastMethod() { }

                        @UiThread
                        private void uiMethod() { }

                        @WorkerThread
                        private void workerMethod() { }

                        public void test() {
                            new AnAction() {
                                @UiThread
                                public void update(@NotNull AnActionEvent e) {
                                    fastMethod(); // OK
                                    slowMethod(); // WARN1
                                    uiMethod(); // OK
                                    workerMethod(); // WARN2
                                }
                            };
                            new Application().invokeLater(() -> {
                                fastMethod(); // OK
                                slowMethod(); // WARN3
                                uiMethod(); // OK
                                workerMethod(); // WARN4
                            });
                            new Application().runOnPooledThread(() -> {
                                slowMethod(); // OK
                                uiMethod(); // WARN5
                                workerMethod(); // OK
                            });
                            new Application().invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    fastMethod(); // OK
                                    slowMethod(); // WARN6
                                    uiMethod(); // OK
                                    workerMethod(); // WARN7
                                }
                            });
                            new Application().runOnPooledThread(new Runnable() {
                                @Override
                                public void run() {
                                    slowMethod(); // OK
                                    uiMethod(); // WARN8
                                    workerMethod(); // OK
                                }
                            });
                            new Application().externallyAnnotated(new Runnable() {
                                @Override
                                public void run() {
                                    slowMethod(); // WARN9
                                    slowMethod(); // WARN10
                                    uiMethod(); // OK
                                    workerMethod(); // WARN11
                                }
                            });
                        }

                        @Slow
                        public void test2() {
                            fastMethod(); // OK
                            slowMethod(); // OK
                            uiMethod(); // WARN12

                            new Application().invokeLater(() -> {
                                fastMethod(); // OK
                                slowMethod(); // WARN13
                                uiMethod(); // OK
                                workerMethod(); // WARN14
                            });

                            new Application().invokeLater(this::fastMethod); // OK
                            new Application().invokeLater(this::uiMethod); // OK
                            new Application().invokeLater(this::slowMethod); // WARN15

                            new Application().runOnPooledThread(this::uiMethod); // WARN16
                            new Application().runOnPooledThread(this::workerMethod); // OK
                            new Application().runOnPooledThread(this::slowMethod); // OK
                            new Application().runOnPooledThread(this::fastMethod); // OK

                            new Application().runWriteAction(() -> { // WARN17
                                fastMethod(); // OK
                                slowMethod(); // WARN18
                                workerMethod(); // WARN19
                                uiMethod(); // OK
                            });
                        }

                        @UiThread
                        public void test3() {
                            fastMethod(); // OK
                            slowMethod(); // WARN20
                            uiMethod(); // OK
                            workerMethod(); // WARN21

                            new Application().runWriteAction(() -> {
                                fastMethod(); // OK
                                slowMethod(); // WARN22
                                workerMethod(); // WARN23
                                uiMethod(); // OK
                            });

                            new Application().runWriteAction(this::fastMethod); // OK
                            new Application().runWriteAction(this::uiMethod); // OK
                            new Application().runWriteAction(this::slowMethod); // WARN24
                            new Application().runWriteAction(this::workerMethod); // WARN25
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
                    import com.android.annotations.concurrency.WorkerThread;
                    import org.jetbrains.annotations.NotNull;

                    @SuppressWarnings("ALL")
                    public class Application {
                        public void invokeLater(@NotNull @UiThread Runnable run) { run.run(); }

                        public void runOnPooledThread(@NotNull @WorkerThread Runnable run) { run.run(); }

                        public void externallyAnnotated(@NotNull Runnable run) { run.run(); }

                        @UiThread
                        public void runWriteAction(@NotNull @UiThread Runnable run) { run.run(); }
                    }
                """
                ).indented(),
                java(
                    """
                    package org.jetbrains.annotations;

                    @Documented
                    @Retention(RetentionPolicy.CLASS)
                    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
                    public @interface NotNull {}
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
                    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE})
                    public @interface WorkerThread {}
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
                ),
                jar(
                    "annotations.zip",
                    xml(
                        "com/intellij/openapi/application/annotations.xml",
                        """
                        <root>
                            <item name='com.intellij.openapi.application.Application void externallyAnnotated(java.lang.Runnable) 0'>
                                <annotation name='com.android.annotations.concurrency.UiThread' />
                            </item>
                        </root>
                        """
                    ).indented()
                )
            )
            .issues(IntellijThreadDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/Test.java:27: Error: Method slowMethod is slow and thus should run on a worker thread, yet the currently inferred thread is the UI thread [WrongThread]
                                slowMethod(); // WARN1
                                ~~~~~~~~~~~~
                src/test/pkg/Test.java:29: Error: Method workerMethod is intended to run on a worker thread, yet the currently inferred thread is the UI thread [WrongThread]
                                workerMethod(); // WARN2
                                ~~~~~~~~~~~~~~
                src/test/pkg/Test.java:34: Error: Method slowMethod is slow and thus should run on a worker thread, yet the currently inferred thread is the UI thread [WrongThread]
                            slowMethod(); // WARN3
                            ~~~~~~~~~~~~
                src/test/pkg/Test.java:36: Error: Method workerMethod is intended to run on a worker thread, yet the currently inferred thread is the UI thread [WrongThread]
                            workerMethod(); // WARN4
                            ~~~~~~~~~~~~~~
                src/test/pkg/Test.java:40: Error: Method uiMethod must run on the UI thread, yet the currently inferred thread is a worker thread [WrongThread]
                            uiMethod(); // WARN5
                            ~~~~~~~~~~
                src/test/pkg/Test.java:47: Error: Method slowMethod is slow and thus should run on a worker thread, yet the currently inferred thread is the UI thread [WrongThread]
                                slowMethod(); // WARN6
                                ~~~~~~~~~~~~
                src/test/pkg/Test.java:49: Error: Method workerMethod is intended to run on a worker thread, yet the currently inferred thread is the UI thread [WrongThread]
                                workerMethod(); // WARN7
                                ~~~~~~~~~~~~~~
                src/test/pkg/Test.java:56: Error: Method uiMethod must run on the UI thread, yet the currently inferred thread is a worker thread [WrongThread]
                                uiMethod(); // WARN8
                                ~~~~~~~~~~
                src/test/pkg/Test.java:63: Error: Method slowMethod is slow and thus should run on a worker thread, yet the currently inferred thread is the UI thread [WrongThread]
                                slowMethod(); // WARN9
                                ~~~~~~~~~~~~
                src/test/pkg/Test.java:64: Error: Method slowMethod is slow and thus should run on a worker thread, yet the currently inferred thread is the UI thread [WrongThread]
                                slowMethod(); // WARN10
                                ~~~~~~~~~~~~
                src/test/pkg/Test.java:66: Error: Method workerMethod is intended to run on a worker thread, yet the currently inferred thread is the UI thread [WrongThread]
                                workerMethod(); // WARN11
                                ~~~~~~~~~~~~~~
                src/test/pkg/Test.java:75: Error: Method uiMethod must run on the UI thread, yet the currently inferred thread is a worker thread [WrongThread]
                        uiMethod(); // WARN12
                        ~~~~~~~~~~
                src/test/pkg/Test.java:79: Error: Method slowMethod is slow and thus should run on a worker thread, yet the currently inferred thread is the UI thread [WrongThread]
                            slowMethod(); // WARN13
                            ~~~~~~~~~~~~
                src/test/pkg/Test.java:81: Error: Method workerMethod is intended to run on a worker thread, yet the currently inferred thread is the UI thread [WrongThread]
                            workerMethod(); // WARN14
                            ~~~~~~~~~~~~~~
                src/test/pkg/Test.java:86: Error: Method slowMethod is slow and thus should run on a worker thread, yet the currently inferred thread is the UI thread [WrongThread]
                        new Application().invokeLater(this::slowMethod); // WARN15
                                                      ~~~~~~~~~~~~~~~~
                src/test/pkg/Test.java:88: Error: Method uiMethod must run on the UI thread, yet the currently inferred thread is a worker thread [WrongThread]
                        new Application().runOnPooledThread(this::uiMethod); // WARN16
                                                            ~~~~~~~~~~~~~~
                src/test/pkg/Test.java:93: Error: Method runWriteAction must run on the UI thread, yet the currently inferred thread is a worker thread [WrongThread]
                        new Application().runWriteAction(() -> { // WARN17
                        ^
                src/test/pkg/Test.java:95: Error: Method slowMethod is slow and thus should run on a worker thread, yet the currently inferred thread is the UI thread [WrongThread]
                            slowMethod(); // WARN18
                            ~~~~~~~~~~~~
                src/test/pkg/Test.java:96: Error: Method workerMethod is intended to run on a worker thread, yet the currently inferred thread is the UI thread [WrongThread]
                            workerMethod(); // WARN19
                            ~~~~~~~~~~~~~~
                src/test/pkg/Test.java:104: Error: Method slowMethod is slow and thus should run on a worker thread, yet the currently inferred thread is the UI thread [WrongThread]
                        slowMethod(); // WARN20
                        ~~~~~~~~~~~~
                src/test/pkg/Test.java:106: Error: Method workerMethod is intended to run on a worker thread, yet the currently inferred thread is the UI thread [WrongThread]
                        workerMethod(); // WARN21
                        ~~~~~~~~~~~~~~
                src/test/pkg/Test.java:110: Error: Method slowMethod is slow and thus should run on a worker thread, yet the currently inferred thread is the UI thread [WrongThread]
                            slowMethod(); // WARN22
                            ~~~~~~~~~~~~
                src/test/pkg/Test.java:111: Error: Method workerMethod is intended to run on a worker thread, yet the currently inferred thread is the UI thread [WrongThread]
                            workerMethod(); // WARN23
                            ~~~~~~~~~~~~~~
                src/test/pkg/Test.java:117: Error: Method slowMethod is slow and thus should run on a worker thread, yet the currently inferred thread is the UI thread [WrongThread]
                        new Application().runWriteAction(this::slowMethod); // WARN24
                                                         ~~~~~~~~~~~~~~~~
                src/test/pkg/Test.java:118: Error: Method workerMethod is intended to run on a worker thread, yet the currently inferred thread is the UI thread [WrongThread]
                        new Application().runWriteAction(this::workerMethod); // WARN25
                                                         ~~~~~~~~~~~~~~~~~~
                25 errors, 0 warnings
                """
            )
    }
}

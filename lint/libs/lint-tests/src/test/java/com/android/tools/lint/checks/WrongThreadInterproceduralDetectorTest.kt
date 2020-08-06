/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.lint.detector.api.Detector

class WrongThreadInterproceduralDetectorTest : AbstractCheckTest() {
    fun testThreadingFromJava() {
        val expected =
            """
            src/test/pkg/Runnable.java:14: Error: Interprocedural thread annotation violation (UiThread to WorkerThread):
            Test#uiThreadStatic -> Test#unannotatedStatic -> Test#workerThreadStatic [WrongThreadInterprocedural]
              @UiThread static void uiThreadStatic() { unannotatedStatic(); }
                                                       ~~~~~~~~~~~~~~~~~~~
            src/test/pkg/Runnable.java:18: Error: Interprocedural thread annotation violation (UiThread to WorkerThread):
            Test#uiThread -> Test#unannotated -> Test#workerThread [WrongThreadInterprocedural]
              @UiThread void uiThread() { unannotated(); }
                                          ~~~~~~~~~~~~~
            src/test/pkg/Runnable.java:18: Error: Interprocedural thread annotation violation (UiThread to WorkerThread):
            Test#uiThread -> Test#unannotated -> Test#workerThread [WrongThreadInterprocedural]
              @UiThread void uiThread() { unannotated(); }
                                          ~~~~~~~~~~~~~
            src/test/pkg/Runnable.java:25: Error: Interprocedural thread annotation violation (WorkerThread to UiThread):
            Test#callRunIt -> Test#runIt -> Test#callRunIt#lambda -> Test#runUi [WrongThreadInterprocedural]
                runIt(() -> runUi());
                ~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/Runnable.java:39: Error: Interprocedural thread annotation violation (UiThread to WorkerThread):
            A#run -> Test#b [WrongThreadInterprocedural]
                public void run(Runnable r) { r.run(); }
                                                ~~~~~
            src/test/pkg/Runnable.java:44: Error: Interprocedural thread annotation violation (WorkerThread to UiThread):
            B#run -> Test#a [WrongThreadInterprocedural]
                public void run(Runnable r) { r.run(); }
                                                ~~~~~
            src/test/pkg/Runnable.java:71: Error: Interprocedural thread annotation violation (UiThread to WorkerThread):
            Test#callInvokeLater#lambda -> Test#c [WrongThreadInterprocedural]
                invokeLater(() -> c());
                                  ~~~
            src/test/pkg/Runnable.java:76: Error: Interprocedural thread annotation violation (WorkerThread to UiThread):
            Test#callInvokeInBackground#lambda -> Test#d [WrongThreadInterprocedural]
                invokeInBackground(() -> d());
                                         ~~~
            8 errors, 0 warnings
            """

        lint().files(
            java(
                """
                    package test.pkg;

                    import android.support.annotation.UiThread;
                    import android.support.annotation.WorkerThread;

                    @SuppressWarnings({"UnnecessaryInterfaceModifier", "ClassNameDiffersFromFileName"})
                    @FunctionalInterface
                    public interface Runnable {
                      public abstract void run();
                    }

                    @SuppressWarnings({"Convert2MethodRef", "MethodMayBeStatic", "override", "ClassNameDiffersFromFileName", "InnerClassMayBeStatic"})
                    class Test {
                      @UiThread static void uiThreadStatic() { unannotatedStatic(); }
                      static void unannotatedStatic() { workerThreadStatic(); }
                      @WorkerThread static void workerThreadStatic() {}

                      @UiThread void uiThread() { unannotated(); }
                      void unannotated() { workerThread(); }
                      @WorkerThread void workerThread() {}

                      @UiThread void runUi() {}
                      void runIt(Runnable r) { r.run(); }
                      @WorkerThread void callRunIt() {
                        runIt(() -> runUi());
                      }

                      public static void main(String[] args) {
                        Test instance = new Test();
                        instance.uiThread();
                      }

                      interface It {
                        void run(Runnable r);
                      }

                      class A implements It {
                        @UiThread
                        public void run(Runnable r) { r.run(); }
                      }

                      class B implements It {
                        @WorkerThread
                        public void run(Runnable r) { r.run(); }
                      }

                      @UiThread
                      void a() {}

                      @WorkerThread
                      void b() {}

                      void runWithIt(It it, Runnable r) { it.run(r); }

                      void f() {
                        runWithIt(new A(), this::b);
                        runWithIt(new B(), this::a);
                      }

                      public static void invokeLater(@UiThread Runnable runnable) { /* place on queue to invoke on UiThread */ }

                      public static void invokeInBackground(@WorkerThread Runnable runnable) { /* place on queue to invoke on background thread */ }

                      @WorkerThread
                      void c() {}

                      @UiThread
                      void d() {}

                      void callInvokeLater() {
                        invokeLater(() -> c());
                        invokeLater(() -> d()); // Ok.
                      }

                      void callInvokeInBackground() {
                        invokeInBackground(() -> d());
                        invokeInBackground(() -> c()); // Ok.
                      }
                    }
                    """
            ).indented(),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        )
            .allowSystemErrors(true)
            .allowDuplicates()
            .run()
            .expect(expected)
    }

    fun testThreadingFromKotlin() {
        val expected =
            """
            src/test/pkg/Test.kt:9: Error: Interprocedural thread annotation violation (UiThread to WorkerThread):
            Test#uiThread -> Test#unannotated -> Test#workerThread [WrongThreadInterprocedural]
              @UiThread fun uiThread() { unannotated() }
                                         ~~~~~~~~~~~~~
            src/test/pkg/Test.kt:9: Error: Interprocedural thread annotation violation (UiThread to WorkerThread):
            Test#uiThread -> Test#unannotated -> Test#workerThread [WrongThreadInterprocedural]
              @UiThread fun uiThread() { unannotated() }
                                         ~~~~~~~~~~~~~
            src/test/pkg/Test.kt:15: Error: Interprocedural thread annotation violation (WorkerThread to UiThread):
            Test#callRunIt -> Test#runIt -> Test#callRunIt#lambda -> Test#runUi [WrongThreadInterprocedural]
              @WorkerThread fun callRunIt() { runIt({ runUi() }) }
                                              ~~~~~~~~~~~~~~~~~~
            src/test/pkg/Test.kt:22: Error: Interprocedural thread annotation violation (UiThread to WorkerThread):
            A#run -> Test#b [WrongThreadInterprocedural]
                override fun run(r: () -> Unit) { r() }
                                                  ~~~
            src/test/pkg/Test.kt:27: Error: Interprocedural thread annotation violation (WorkerThread to UiThread):
            B#run -> Test#a [WrongThreadInterprocedural]
                override fun run(r: () -> Unit) { r() }
                                                  ~~~
            src/test/pkg/Test.kt:50: Error: Interprocedural thread annotation violation (UiThread to WorkerThread):
            Test#callInvokeLater#lambda -> Test#c [WrongThreadInterprocedural]
                invokeLater({ c() })
                              ~~~
            src/test/pkg/Test.kt:55: Error: Interprocedural thread annotation violation (WorkerThread to UiThread):
            Test#callInvokeInBackground#lambda -> Test#d [WrongThreadInterprocedural]
                invokeInBackground({ d() })
                                     ~~~
            src/test/pkg/Test.kt:60: Error: Interprocedural thread annotation violation (UiThread to WorkerThread):
            Test#uiThreadStatic -> Test#unannotatedStatic -> Test#workerThreadStatic [WrongThreadInterprocedural]
                @UiThread fun uiThreadStatic() { unannotatedStatic() }
                                                 ~~~~~~~~~~~~~~~~~~~
            8 errors, 0 warnings
            """
        lint().files(
            kotlin(
                """
                    package test.pkg

                    import android.support.annotation.UiThread
                    import android.support.annotation.WorkerThread

                    @Suppress("MemberVisibilityCanBePrivate")
                    class Test {

                      @UiThread fun uiThread() { unannotated() }
                      fun unannotated() { workerThread() }
                      @WorkerThread fun workerThread() {}

                      @UiThread fun runUi() {}
                      fun runIt(r: () -> Unit) { r() }
                      @WorkerThread fun callRunIt() { runIt({ runUi() }) }


                      interface It { fun run(r: () -> Unit) }

                      inner class A : It {
                        @UiThread
                        override fun run(r: () -> Unit) { r() }
                      }

                      inner class B : It {
                        @WorkerThread
                        override fun run(r: () -> Unit) { r() }
                      }

                      @UiThread
                      fun a() {}

                      @WorkerThread
                      fun b() {}

                      fun runWithIt(it: It, r: () -> Unit) { it.run(r) }

                      fun f() {
                        runWithIt(A(), this::b)
                        runWithIt(B(), this::a)
                      }

                      @WorkerThread
                      fun c() {}

                      @UiThread
                      fun d() {}

                      fun callInvokeLater() {
                        invokeLater({ c() })
                        invokeLater({ d() }) // Ok.
                      }

                      fun callInvokeInBackground() {
                        invokeInBackground({ d() })
                        invokeInBackground({ c() }) // Ok.
                      }

                      companion object {
                        @UiThread fun uiThreadStatic() { unannotatedStatic() }

                        fun unannotatedStatic() { workerThreadStatic() }

                        @WorkerThread fun workerThreadStatic() {}

                        @JvmStatic fun main(args: Array<String>) {
                          val instance = Test()
                          instance.uiThread()
                        }

                        fun invokeLater(@UiThread runnable: () -> Unit) { /* place on queue to invoke on UiThread */ }

                        fun invokeInBackground(@WorkerThread runnable: () -> Unit) { /* place on queue to invoke on background thread */ }
                      }
                    }
                    """
            ).indented(),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        )
            .allowSystemErrors(true)
            .run()
            .expect(expected)
    }

    override fun getDetector(): Detector {
        return WrongThreadInterproceduralDetector()
    }
}

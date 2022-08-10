/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.instrumentation.threading.agent;

import com.android.annotations.concurrency.AnyThread;
import com.android.annotations.concurrency.Slow;
import com.android.annotations.concurrency.UiThread;
import com.android.annotations.concurrency.WorkerThread;

public class SampleClasses {

    /** Sample annotated class with a few method-level annotations. */
    public static class ClassWithAnnotatedMethods {

        @AnyThread
        public void anyThreadMethod1() {
            // Do nothing
        }

        @Slow
        public void slowThreadMethod1() {
            // Do nothing
        }

        @UiThread
        public void uiMethod1() {
            // Do nothing
        }

        @WorkerThread
        public void workerMethod1() {
            // Do nothing
        }

        @UiThread
        private void privateUiMethod1() {
            // Do nothing
        }

        // Note that in practice we would never have both of these annotations present at the same
        // time.
        @UiThread
        @WorkerThread
        public void workerAndUiMethod1() {
            // Do nothing
        }

        public void nonAnnotatedMethod1() {
            // Do nothing
        }
    }

    public static class ClassWithAnnotatedConstructor {

        @UiThread
        public ClassWithAnnotatedConstructor() {
            // Do nothing
        }
    }

    @UiThread
    public static class ClassWithUiThreadAnnotation {

        public void nonAnnotatedMethod1() {
            // Do nothing
        }

        @UiThread
        public void uiMethod1() {
            // Do nothing
        }

        @WorkerThread
        public void workerMethod1() {
            // Do nothing
        }

        @AnyThread
        public void anyThreadMethod1() {
            // Do nothing
        }

        @Slow
        public void slowThreadMethod1() {
            // Do nothing
        }

        @Slow
        public void executeWithLambda() throws Exception {
            Runnable doSomething =
                    () -> {
                        /* Do nothing */
                    };
            Thread t = new Thread(doSomething);
            t.start();
            t.join();
        }
    }

    @UiThread
    public static class AnnotatedClassWithInnerClass {
        private int a = 5;

        public class InnerClass {
            public void method1() {
                ++a;
                a++;
                --a;
                a--;
            }

            public void method2() {}
        }
    }
}

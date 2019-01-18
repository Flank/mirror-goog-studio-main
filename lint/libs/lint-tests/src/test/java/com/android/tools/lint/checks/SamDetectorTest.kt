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

import com.android.tools.lint.detector.api.Detector

class SamDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return SamDetector()
    }

    fun testStashingImplicitInstances() {
        lint().files(
            kotlin(
                """
                @file:Suppress("RedundantSamConstructor", "MoveLambdaOutsideParentheses")

                package test.pkg

                fun test(handler: MyHandler, list: List<MyInterface>) {
                    handler.handle(MyInterface { println("hello") }) // OK
                    handler.handle({ println("hello") }) // OK
                    handler.stash(MyInterface { println("hello") }, list) // OK
                    handler.stash({ println("hello") }, list) // OK
                    handler.store({ println("hello") }) // OK
                    handler.compareIdentity1({ println("hello") }) // OK
                    handler.compareIdentity2({ println("hello") }) // OK
                    handler.compareEquals1({ println("hello") }) // OK
                    handler.compareEquals2({ println("hello") }) // OK

                    val lambda = { println("hello") }
                    handler.stash(lambda, list) // WARN
                    handler.store(lambda) // WARN
                    handler.compareIdentity1(lambda) // WARN
                    handler.compareIdentity2(lambda) // WARN
                    handler.compareEquals1(lambda) // OK
                    handler.compareEquals2(lambda) // OK

                    @Suppress("CanBeVal", "JoinDeclarationAndAssignment")
                    var lambda2: () -> Unit
                    lambda2 = { println("hello") }
                    handler.stash(lambda2, list) // WARN
                }

                fun viewpost(view: android.view.View) {
                    view.postDelayed({ println ("Hello") }, 50)
                }
                """
            ),
            java(
                """
                package test.pkg;

                import java.util.List;

                public class JavaTest {
                    public void test(MyHandler handler, List<MyInterface> list) {
                        handler.handle(() -> System.out.println("hello")); // OK
                        handler.stash(() -> System.out.println("hello"), list); // OK
                    }
                }
                """
            ),
            java(
                """
                package test.pkg;

                public interface MyInterface {
                    void act();
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import java.util.List;

                public class MyHandler {
                    public void handle(MyInterface actor) {
                        actor.act();
                        System.out.println(actor);
                        MyInterface copy = actor;
                        System.out.println(copy);
                    }

                    public void stash(MyInterface actor, List<MyInterface> actors) {
                        actors.add(actor);
                    }

                    public void store(MyInterface actor) {
                        last = actor;
                    }

                    private MyInterface last;

                    public void compareIdentity1(MyInterface actor) {
                        if (actor == last) {
                            System.out.println("last");
                        }
                    }

                    public void compareIdentity2(MyInterface actor) {
                        if (actor != last) {
                            System.out.println("not last");
                        }
                    }

                    public void compareEquals1(MyInterface actor) {
                        if (actor.equals(last)) {
                            System.out.println("last");
                        }
                    }

                    public void compareEquals2(MyInterface actor) {
                        if (last.equals(actor)) {
                            System.out.println("last");
                        }
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/test.kt:18: Warning: Implicit new MyInterface instance being passed to method which ends up checking instance equality; this can lead to subtle bugs [ImplicitSamInstance]
                                handler.stash(lambda, list) // WARN
                                              ~~~~~~
            src/test/pkg/test.kt:19: Warning: Implicit new MyInterface instance being passed to method which ends up checking instance equality; this can lead to subtle bugs [ImplicitSamInstance]
                                handler.store(lambda) // WARN
                                              ~~~~~~
            src/test/pkg/test.kt:20: Warning: Implicit new MyInterface instance being passed to method which ends up checking instance equality; this can lead to subtle bugs [ImplicitSamInstance]
                                handler.compareIdentity1(lambda) // WARN
                                                         ~~~~~~
            src/test/pkg/test.kt:21: Warning: Implicit new MyInterface instance being passed to method which ends up checking instance equality; this can lead to subtle bugs [ImplicitSamInstance]
                                handler.compareIdentity2(lambda) // WARN
                                                         ~~~~~~
            src/test/pkg/test.kt:28: Warning: Implicit new MyInterface instance being passed to method which ends up checking instance equality; this can lead to subtle bugs [ImplicitSamInstance]
                                handler.stash(lambda2, list) // WARN
                                              ~~~~~~~
            0 errors, 5 warnings
            """
        ).expectFixDiffs(
            """
            Fix for src/test/pkg/test.kt line 18: Explicitly create MyInterface instance:
            @@ -17 +17
            -                     val lambda = { println("hello") }
            +                     val lambda = MyInterface { println("hello") }
            Fix for src/test/pkg/test.kt line 19: Explicitly create MyInterface instance:
            @@ -17 +17
            -                     val lambda = { println("hello") }
            +                     val lambda = MyInterface { println("hello") }
            Fix for src/test/pkg/test.kt line 20: Explicitly create MyInterface instance:
            @@ -17 +17
            -                     val lambda = { println("hello") }
            +                     val lambda = MyInterface { println("hello") }
            Fix for src/test/pkg/test.kt line 21: Explicitly create MyInterface instance:
            @@ -17 +17
            -                     val lambda = { println("hello") }
            +                     val lambda = MyInterface { println("hello") }
            Fix for src/test/pkg/test.kt line 28: Explicitly create MyInterface instance:
            @@ -27 +27
            -                     lambda2 = { println("hello") }
            +                     lambda2 = MyInterface { println("hello") }
            """
        )
    }

    fun testHandler() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.os.Handler
                import android.widget.TextView

                fun callback() {}

                fun test(handler: Handler, view: TextView) {
                    handler.post(::callback)
                    handler.removeCallbacks(::callback)
                    view.post(::callback)
                    view.removeCallbacks { callback() } // OK
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/test.kt:9: Warning: Implicit new Runnable instance being passed to method which ends up checking instance equality; this can lead to subtle bugs [ImplicitSamInstance]
                handler.post(::callback)
                             ~~~~~~~~~~
            src/test/pkg/test.kt:10: Warning: Implicit new Runnable instance being passed to method which ends up checking instance equality; this can lead to subtle bugs [ImplicitSamInstance]
                handler.removeCallbacks(::callback)
                                        ~~~~~~~~~~
            src/test/pkg/test.kt:11: Warning: Implicit new Runnable instance being passed to method which ends up checking instance equality; this can lead to subtle bugs [ImplicitSamInstance]
                view.post(::callback)
                          ~~~~~~~~~~
            0 errors, 3 warnings
            """
        ).expectFixDiffs("")
    }
}

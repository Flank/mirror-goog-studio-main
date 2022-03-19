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

class WorkManagerDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector = WorkManagerDetector()

    fun testJava() {
        lint().files(
            java(
                """
                package test.pkg;

                import java.util.ArrayList;
                import java.util.Arrays;
                import java.util.List;

                import androidx.work.WorkManager;
                import androidx.work.WorkContinuation;
                import androidx.work.OneTimeWorkRequest;import java.util.Arrays;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "unused"})
                public abstract class WorkManagerTest {
                    void someWork(OneTimeWorkRequest workRequest1, OneTimeWorkRequest workRequest2) {
                        WorkManager workManager = WorkManager.getInstance();
                        WorkContinuation cont = workManager.beginWith(workRequest1, workRequest2); // ERROR
                        doSomeOtherStuff();
                        // cont needs to be enqueued before it goes out of scope
                    }

                    void someWork2(OneTimeWorkRequest workRequest1, OneTimeWorkRequest workRequest2) {
                        WorkManager workManager = WorkManager.getInstance();
                        workManager.beginWith(workRequest1, workRequest2); // ERROR
                        doSomeOtherStuff();
                    }

                    void someWork3(OneTimeWorkRequest workRequest1, OneTimeWorkRequest workRequest2) {
                        WorkManager workManager = WorkManager.getInstance();
                        WorkContinuation cont = workManager.beginWith(workRequest1, workRequest2); // OK
                        doSomeOtherStuff();
                        cont.enqueue();
                    }

                    void someWork4(OneTimeWorkRequest workRequest1, OneTimeWorkRequest workRequest2) {
                        WorkManager workManager = WorkManager.getInstance();
                        WorkContinuation cont = workManager.beginWith(workRequest1, workRequest2).enqueue(); // OK
                        doSomeOtherStuff();
                        cont.enqueue();
                    }

                    void someHarderWork(
                            OneTimeWorkRequest workRequest1, OneTimeWorkRequest workRequest2,
                            OneTimeWorkRequest workRequest3, OneTimeWorkRequest workRequest4,
                            OneTimeWorkRequest workRequest5) {
                        WorkManager workManager = WorkManager.getInstance();
                        WorkContinuation cont1 = workManager.beginWith(workRequest1, workRequest2); // OK
                        WorkContinuation cont2 = cont1.then(workRequest3).then(workRequest4); // ERROR
                        WorkContinuation cont3 = cont1.then(workRequest5); // ERROR
                        // cont2 and cont3 need to be enqueued before they go out of scope; cont1 does not
                    }

                    void someEvenHarderWork(
                            OneTimeWorkRequest workRequest1, OneTimeWorkRequest workRequest2,
                            OneTimeWorkRequest workRequest3, OneTimeWorkRequest workRequest4,
                            OneTimeWorkRequest workRequest5, OneTimeWorkRequest workRequest6) {
                        WorkManager workManager = WorkManager.getInstance();
                        WorkContinuation cont1 = workManager.beginWith(workRequest1, workRequest2); // OK
                        WorkContinuation cont2 = cont1.then(workRequest3).then(workRequest4); // OK
                        WorkContinuation cont3 = cont1.then(workRequest5); // OK
                        WorkContinuation cont4 = WorkContinuation.combine(workRequest6, cont2, cont3); // ERROR
                        // Only cont4 needs to be enqueued
                    }

                    void someEvenHarderWorkDoneProperly(
                            OneTimeWorkRequest workRequest1, OneTimeWorkRequest workRequest2,
                            OneTimeWorkRequest workRequest3, OneTimeWorkRequest workRequest4,
                            OneTimeWorkRequest workRequest5, OneTimeWorkRequest workRequest6) {
                        WorkManager workManager = WorkManager.getInstance();
                        WorkContinuation cont1 = workManager.beginWith(workRequest1, workRequest2); // OK
                        WorkContinuation cont2 = cont1.then(workRequest3).then(workRequest4); // OK
                        WorkContinuation cont3 = cont1.then(workRequest5); // OK
                        WorkContinuation cont4 = WorkContinuation.combine(workRequest6, cont2, cont3); // OK
                        cont4.enqueue();
                    }

                    void someEvenHarderWorkWithLists1(
                            OneTimeWorkRequest workRequest1, OneTimeWorkRequest workRequest2,
                            OneTimeWorkRequest workRequest3, OneTimeWorkRequest workRequest4,
                            OneTimeWorkRequest workRequest5, OneTimeWorkRequest workRequest6) {
                        WorkManager workManager = WorkManager.getInstance();
                        WorkContinuation cont1 = workManager.beginWith(workRequest1, workRequest2); // OK
                        WorkContinuation cont2 = cont1.then(workRequest3).then(workRequest4); // OK
                        WorkContinuation cont3 = cont1.then(workRequest5); // OK
                        List<WorkContinuation> continuations = new ArrayList<>();
                        continuations.add(cont2);
                        continuations.add(cont3);
                        WorkContinuation cont4 = WorkContinuation.combine(workRequest6, continuations); // OK
                        cont4.enqueue();
                    }

                    void someEvenHarderWorkWithLists2(
                            OneTimeWorkRequest workRequest1, OneTimeWorkRequest workRequest2,
                            OneTimeWorkRequest workRequest3, OneTimeWorkRequest workRequest4,
                            OneTimeWorkRequest workRequest5, OneTimeWorkRequest workRequest6) {
                        WorkManager workManager = WorkManager.getInstance();
                        WorkContinuation cont1 = workManager.beginWith(workRequest1, workRequest2); // OK
                        WorkContinuation cont2 = cont1.then(workRequest3).then(workRequest4); // OK
                        WorkContinuation cont3 = cont1.then(workRequest5); // OK
                        List<WorkContinuation> continuations = Arrays.asList(cont2, cont3);
                        WorkContinuation cont4 = WorkContinuation.combine(workRequest6, continuations); // OK
                        cont4.enqueue();
                    }

                    WorkContinuation someWorkThatIsReturned(
                            OneTimeWorkRequest workRequest1,
                            OneTimeWorkRequest workRequest2) {
                        WorkManager workManager = WorkManager.getInstance();
                        WorkContinuation cont1 = workManager.beginWith(workRequest1, workRequest2); // OK
                        return cont1;
                    }

                    private void doSomeOtherStuff() {
                    }
                }
                """
            ).indented(),
            *workManagerStubs
        ).run().expect(
            """
            src/test/pkg/WorkManagerTest.java:15: Warning: WorkContinuation cont not enqueued: did you forget to call enqueue()? [EnqueueWork]
                    WorkContinuation cont = workManager.beginWith(workRequest1, workRequest2); // ERROR
                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/WorkManagerTest.java:22: Warning: WorkContinuation not enqueued: did you forget to call enqueue()? [EnqueueWork]
                    workManager.beginWith(workRequest1, workRequest2); // ERROR
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/WorkManagerTest.java:46: Warning: WorkContinuation cont2 not enqueued: did you forget to call enqueue()? [EnqueueWork]
                    WorkContinuation cont2 = cont1.then(workRequest3).then(workRequest4); // ERROR
                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/WorkManagerTest.java:47: Warning: WorkContinuation cont3 not enqueued: did you forget to call enqueue()? [EnqueueWork]
                    WorkContinuation cont3 = cont1.then(workRequest5); // ERROR
                                             ~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/WorkManagerTest.java:59: Warning: WorkContinuation cont4 not enqueued: did you forget to call enqueue()? [EnqueueWork]
                    WorkContinuation cont4 = WorkContinuation.combine(workRequest6, cont2, cont3); // ERROR
                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 5 warnings
            """
        )
    }

    fun testKotlin() {
        lint().files(
            kotlin(
                """
                @file:Suppress("UNUSED_VARIABLE", "unused")

                package test.pkg

                import androidx.work.WorkManager
                import androidx.work.WorkContinuation
                import androidx.work.OneTimeWorkRequest

                abstract class WorkManagerTest {
                    fun someWork(workRequest1: OneTimeWorkRequest, workRequest2: OneTimeWorkRequest) {
                        val workManager = WorkManager.getInstance()
                        val cont = workManager.beginWith(workRequest1, workRequest2) // ERROR
                        doSomeOtherStuff()
                        // cont needs to be enqueued before it goes out of scope
                    }

                    fun someWork2(workRequest1: OneTimeWorkRequest, workRequest2: OneTimeWorkRequest) {
                        val workManager = WorkManager.getInstance()
                        val cont = workManager.beginWith(workRequest1, workRequest2) // OK
                        doSomeOtherStuff()
                        cont.enqueue()
                    }

                    fun someHarderWork(
                            workRequest1: OneTimeWorkRequest, workRequest2: OneTimeWorkRequest,
                            workRequest3: OneTimeWorkRequest, workRequest4: OneTimeWorkRequest,
                            workRequest5: OneTimeWorkRequest) {
                        val workManager = WorkManager.getInstance()
                        val cont1 = workManager.beginWith(workRequest1, workRequest2) // OK
                        val cont2 = cont1.then(workRequest3).then(workRequest4) // ERROR
                        val cont3 = cont1.then(workRequest5) // ERROR
                        // cont2 and cont3 need to be enqueued before they go out of scope; cont1 does not
                    }

                    fun someEvenHarderWork(
                            workRequest1: OneTimeWorkRequest, workRequest2: OneTimeWorkRequest,
                            workRequest3: OneTimeWorkRequest, workRequest4: OneTimeWorkRequest,
                            workRequest5: OneTimeWorkRequest, workRequest6: OneTimeWorkRequest) {
                        val workManager = WorkManager.getInstance()
                        val cont1 = workManager.beginWith(workRequest1, workRequest2) // OK
                        val cont2 = cont1.then(workRequest3).then(workRequest4) // OK
                        val cont3 = cont1.then(workRequest5) // OK
                        val cont4 = WorkContinuation.combine(workRequest6, cont2, cont3) // ERROR
                        // Only cont4 needs to be enqueued
                    }

                    fun someEvenHarderWorkDoneProperly(
                            workRequest1: OneTimeWorkRequest, workRequest2: OneTimeWorkRequest,
                            workRequest3: OneTimeWorkRequest, workRequest4: OneTimeWorkRequest,
                            workRequest5: OneTimeWorkRequest, workRequest6: OneTimeWorkRequest) {
                        val workManager = WorkManager.getInstance()
                        val cont1 = workManager.beginWith(workRequest1, workRequest2) // OK
                        val cont2 = cont1.then(workRequest3).then(workRequest4) // OK
                        val cont3 = cont1.then(workRequest5) // OK
                        val cont4 = WorkContinuation.combine(workRequest6, cont2, cont3) // OK
                        cont4.enqueue()
                    }

                    fun someEvenHarderWorkWithLists1(
                            workRequest1: OneTimeWorkRequest, workRequest2: OneTimeWorkRequest,
                            workRequest3: OneTimeWorkRequest, workRequest4: OneTimeWorkRequest,
                            workRequest5: OneTimeWorkRequest, workRequest6: OneTimeWorkRequest) {
                        val workManager = WorkManager.getInstance()
                        val cont1 = workManager.beginWith(workRequest1, workRequest2) // OK
                        val cont2 = cont1.then(workRequest3).then(workRequest4) // OK
                        val cont3 = cont1.then(workRequest5) // OK
                        val continuations = mutableListOf<WorkContinuation>()
                        continuations.add(cont2)
                        continuations.add(cont3)
                        val cont4 = WorkContinuation.combine(workRequest6, continuations) // OK
                        cont4.enqueue()
                    }

                    fun someEvenHarderWorkWithLists2(
                            workRequest1: OneTimeWorkRequest, workRequest2: OneTimeWorkRequest,
                            workRequest3: OneTimeWorkRequest, workRequest4: OneTimeWorkRequest,
                            workRequest5: OneTimeWorkRequest, workRequest6: OneTimeWorkRequest) {
                        val workManager = WorkManager.getInstance()
                        val cont1 = workManager.beginWith(workRequest1, workRequest2) // OK
                        val cont2 = cont1.then(workRequest3).then(workRequest4) // OK
                        val cont3 = cont1.then(workRequest5) // OK
                        val continuations = listOf(cont2, cont3)
                        val cont4 = WorkContinuation.combine(workRequest6, continuations) // OK
                        cont4.enqueue()
                    }

                    fun someWorkThatIsReturned(
                        workRequest1: OneTimeWorkRequest, workRequest2: OneTimeWorkRequest
                    ): WorkContinuation {
                        val workManager = WorkManager.getInstance()
                        val cont1 = workManager.beginWith(workRequest1, workRequest2) // OK
                        return cont1
                    }

                    private fun doSomeOtherStuff() {}
                }
                """
            ).indented(),
            *workManagerStubs
        ).run().expect(
            """
            src/test/pkg/WorkManagerTest.kt:12: Warning: WorkContinuation cont not enqueued: did you forget to call enqueue()? [EnqueueWork]
                    val cont = workManager.beginWith(workRequest1, workRequest2) // ERROR
                               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/WorkManagerTest.kt:30: Warning: WorkContinuation cont2 not enqueued: did you forget to call enqueue()? [EnqueueWork]
                    val cont2 = cont1.then(workRequest3).then(workRequest4) // ERROR
                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/WorkManagerTest.kt:31: Warning: WorkContinuation cont3 not enqueued: did you forget to call enqueue()? [EnqueueWork]
                    val cont3 = cont1.then(workRequest5) // ERROR
                                ~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/WorkManagerTest.kt:43: Warning: WorkContinuation cont4 not enqueued: did you forget to call enqueue()? [EnqueueWork]
                    val cont4 = WorkContinuation.combine(workRequest6, cont2, cont3) // ERROR
                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 4 warnings
            """
        )
    }

    fun testEnqueueSync() {
        // Regression test for https://issuetracker.google.com/113167619
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.work.ExistingWorkPolicy
                import androidx.work.OneTimeWorkRequest
                import androidx.work.WorkManager

                class WorkTest {
                    fun test1(manager: WorkManager, workRequest1: OneTimeWorkRequest) {
                        manager.beginUniqueWork(
                                "name",
                                ExistingWorkPolicy.KEEP,
                                workRequest1).synchronous().enqueueSync()
                    }
                }
                """
            ).indented(),
            *workManagerStubs
        ).run().expectClean()
    }

    fun testUnresolvable() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.work.OneTimeWorkRequest
                import androidx.work.WorkContinuation
                import androidx.work.WorkManager

                lateinit var workManager: WorkManager
                lateinit var unrelated: WorkContinuation
                lateinit var workRequest: OneTimeWorkRequest

                fun test1a() {
                    // Don't flag a enqueue call if there's an unresolvable reference *and* we have
                    // a enqueue call (even if we're not certain which instance it's on)
                    val job = workManager.beginWith(workRequest).unknown() // OK
                    unrelated.enqueue()
                }

                fun test1b() {
                    // Like 1a, but using assignment instead of declaration initialization
                    val job: WorkContinuation
                    job = workManager.beginWith(workRequest).unknown() // OK
                    unrelated.enqueue()
                }

                fun test2() {
                    // If there's an unresolvable reference, DO complain if there's *no* enqueue call anywhere
                    workManager.beginWith(workRequest) // ERROR 1
                }

                fun test3a() {
                    // If the unresolved call has nothing to do
                    // with updating the toast instance (e.g. is not associated with an
                    // assignment and has no further chained calls) don't revert to
                    // non-instance checking
                    val job = workManager.beginWith(workRequest) // ERROR 2
                    job.unknown()
                    unrelated.enqueue()
                }

                fun test3b() {
                    // Like 3a, but here we're making calls on the result of the
                    // unresolved call; those could be cleanup calls, so in that case
                    // we're not confident enough
                    val job = workManager.beginWith(workRequest) // OK
                    job.unknown().something()
                    unrelated.enqueue()
                }

                //private fun WorkContinuation.unknown(): WorkContinuation = error("not yet implemented")
                //private fun WorkContinuation.something(): Unit = error("not yet implemented")
                """
            ).indented(),
            *workManagerStubs
        ).run().expect(
            """
            src/test/pkg/test.kt:27: Warning: WorkContinuation not enqueued: did you forget to call enqueue()? [EnqueueWork]
                workManager.beginWith(workRequest) // ERROR 1
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/test.kt:35: Warning: WorkContinuation job not enqueued: did you forget to call enqueue()? [EnqueueWork]
                val job = workManager.beginWith(workRequest) // ERROR 2
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        )
    }

    private val workManagerStubs = arrayOf(
        // WorkManager stubs
        java(
            """
            package androidx.work;
            @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
            public abstract class WorkManager {
                public static WorkManager getInstance() { return null; }
                public WorkContinuation beginWith(OneTimeWorkRequest...work) { return null; }
                public final WorkContinuation beginUniqueWork(
                        String uniqueWorkName,
                        ExistingWorkPolicy existingWorkPolicy,
                        OneTimeWorkRequest... work) {
                    return null;
                }

            }
            """
        ).indented(),
        java(
            """
            package androidx.work;
            import java.util.List;

            @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
            public abstract class WorkContinuation {
                public final WorkContinuation then(OneTimeWorkRequest... work) {
                    return null;
                }
                public abstract WorkContinuation then(List<OneTimeWorkRequest> work);

                public static WorkContinuation combine(WorkContinuation... continuations) {
                    return null;
                }
                public static WorkContinuation combine(
                        OneTimeWorkRequest work,
                        WorkContinuation... continuations) {
                    return nul;
                }
                public static WorkContinuation combine(
                        OneTimeWorkRequest work,
                        List<WorkContinuation> continuations) {
                    return nul;
                }

                public abstract void enqueue();

                public abstract SynchronousWorkContinuation synchronous();
            }
            """
        ).indented(),
        java(
            """
            package androidx.work;
            @SuppressWarnings("ClassNameDiffersFromFileName")
            public class OneTimeWorkRequest {
            }
            """
        ).indented(),
        java(
            """
            package androidx.work;
            @SuppressWarnings("ClassNameDiffersFromFileName")
            public enum ExistingWorkPolicy {
                REPLACE,
                KEEP,
                APPEND
            }
            """
        ).indented(),
        java(
            """
            package androidx.work;
            @SuppressWarnings("ClassNameDiffersFromFileName")
            public interface SynchronousWorkContinuation {
                void enqueueSync();
            }
            """
        )
    )
}

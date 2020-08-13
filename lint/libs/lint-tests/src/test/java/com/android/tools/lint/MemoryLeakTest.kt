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

@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE") // accessing sun.tools.attach APIs
package com.android.tools.lint

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.BuiltinIssueRegistry
import com.android.tools.lint.checks.GradleDetectorTest
import com.android.tools.lint.checks.PrivateResourceDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.gradle
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl
import com.sun.tools.attach.VirtualMachine
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.junit.Assert.assertTrue
import org.junit.Test
import sun.tools.attach.HotSpotVirtualMachine
import java.lang.management.ManagementFactory
import java.util.Scanner

class MemoryLeakTest {

    private fun countLiveInstancesOf(clz: String): Int {
        val pid = ManagementFactory.getRuntimeMXBean().name.substringBefore('@')
        val vm = VirtualMachine.attach(pid) as HotSpotVirtualMachine
        val heap = vm.heapHisto("-live")

        var res = 0
        heap.bufferedReader().forEachLine { line ->
            Scanner(line).use { s ->
                try {
                    // Format: idx #instances #bytes className
                    s.next()
                    val count = s.nextInt()
                    s.next()
                    val currClass = s.next()

                    if (currClass == clz) {
                        res = count
                        return@forEachLine
                    }
                } catch (e: NoSuchElementException) {
                    // Skip this line.
                }
            }
        }

        return res
    }

    private fun lint(): TestLintTask {
        val task = TestLintTask()
        GradleDetectorTest.initializeNetworkMocksAndCaches(task)
        task.sdkHome(TestUtils.getSdk())
        return task
    }

    private fun doAnalysis() {
        lint().files(

            gradle(
                """
                apply plugin: 'com.android.application'

                android {
                    compileSdkVersion 29
                    defaultConfig {
                        applicationId "com.gharrma.sampleapp"
                        minSdkVersion 19
                        targetSdkVersion 29
                        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
                    }
                }

                dependencies {
                    // Higher version to accidentally pick up recent 1.1 version from local cache
                    implementation 'androidx.appcompat:appcompat:5.0.2'
                }
            """
            ).indented(),

            kotlin(
                """
                package com.gharrma.sampleapp

                import androidx.appcompat.app.AppCompatActivity
                import android.os.Bundle

                class MainActivity : AppCompatActivity() {

                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        setContentView(R.layout.activity_main)
                        Utils.foo()
                    }

                    companion object {
                        fun bar() {
                            println("bar")
                        }
                    }
                }
            """
            ).indented(),

            java(
                """
                package com.gharrma.sampleapp;

                public class Utils {

                    public static void foo() {
                        System.out.println("foo");
                        MainActivity.Companion.bar();
                    }
                }
            """
            ).indented(),

            xml(
                "src/main/res/layout/activity_main.xml",
                """
                <androidx.constraintlayout.widget.ConstraintLayout
                        xmlns:android="http://schemas.android.com/apk/res/android"
                        xmlns:tools="http://schemas.android.com/tools"
                        xmlns:app="http://schemas.android.com/apk/res-auto"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        tools:context=".MainActivity">

                    <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Hello World!"
                            app:layout_constraintBottom_toBottomOf="parent"
                            app:layout_constraintLeft_toLeftOf="parent"
                            app:layout_constraintRight_toRightOf="parent"
                            app:layout_constraintTop_toTopOf="parent"/>

                </androidx.constraintlayout.widget.ConstraintLayout>
            """
            ).indented(),
            java(
                """
                // Stub to prevent MissingClass errors
                package androidx.constraintlayout.widget;
                public abstract class ConstraintLayout extends android.view.ViewGroup {
                    public ConstraintLayout() { super(null); }
                }
                """
            ).indented()
        )
            // Needed to allow PrivateResourceDetector to run.
            .modifyGradleMocks(PrivateResourceDetectorTest.mockModifier)

            // Needed to allow GradleDetector to run.
            .networkData(
                "http://search.maven.org/solrsearch/select" +
                    "?q=g:%22androidx.appcompat%22+AND+a:%22appcompat%22&core=gav&wt=json",
                "" // Response doesn't matter for this test.
            )

            .issues(*BuiltinIssueRegistry().issues.toTypedArray())
            .run()
            .expectClean()
    }

    @Test
    fun testForMemoryLeak() {
        // Regression test for
        // 119833503: Memory leak due to Lint's custom class loader when running with Gradle
        //
        // This test cannot guarantee that there are no memory leaks, but it gives us a
        // chance at catching some automatically by running Lint and searching
        // for surviving instances of LintCoreProjectEnvironment, PsiWhiteSpaceImpl, etc.
        //
        // A more thorough test would run Lint multiple times on a large and
        // complex project using Gradle (with a daemon) in order to get better
        // code coverage (and thus a better chance at catching a leak).
        doAnalysis()

        assertTrue(
            "Utility function `countLiveInstancesOf()` appears to be broken.",
            countLiveInstancesOf(Object::class.java.name) > 0
        )

        assertTrue(
            "Detected Lint memory leak; KotlinCoreEnvironment is reachable",
            countLiveInstancesOf(KotlinCoreEnvironment::class.java.name) == 0
        )

        assertTrue(
            "Detected Lint memory leak; PsiWhiteSpaceImpl is reachable",
            countLiveInstancesOf(PsiWhiteSpaceImpl::class.java.name) == 0
        )
    }
}

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
package com.android.tools.lint.checks

import com.android.SdkConstants.DOT_JAR
import com.android.testutils.TestUtils
import com.android.tools.lint.checks.LintDetectorDetector.Companion.CHECK_URL
import com.android.tools.lint.checks.LintDetectorDetector.Companion.DOLLAR_STRINGS
import com.android.tools.lint.checks.LintDetectorDetector.Companion.EXISTING_LINT_CONSTANTS
import com.android.tools.lint.checks.LintDetectorDetector.Companion.ID
import com.android.tools.lint.checks.LintDetectorDetector.Companion.PSI_COMPARE
import com.android.tools.lint.checks.LintDetectorDetector.Companion.TEXT_FORMAT
import com.android.tools.lint.checks.LintDetectorDetector.Companion.TRIM_INDENT
import com.android.tools.lint.checks.LintDetectorDetector.Companion.UNEXPECTED_DOMAIN
import com.android.tools.lint.checks.LintDetectorDetector.Companion.USE_KOTLIN
import com.android.tools.lint.checks.LintDetectorDetector.Companion.USE_UAST
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.LibraryReferenceTestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.getLintClassPath
import com.android.tools.lint.checks.infrastructure.TestFiles.gradle
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestFiles.source
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test
import java.io.File

class LintDetectorDetectorTest {
    private val issues = arrayOf(
        ID,
        USE_UAST,
        PSI_COMPARE,
        TRIM_INDENT,
        USE_KOTLIN,
        CHECK_URL,
        TEXT_FORMAT,
        EXISTING_LINT_CONSTANTS,
        UNEXPECTED_DOMAIN,
        DOLLAR_STRINGS
    )

    @Test
    fun testProblems() {
        lint()
            .files(
                java(
                    """
                    /* Copyright (C) 2020 The Android Open Source Project */
                    package test.pkg;
                    import com.intellij.psi.PsiClass;
                    import com.intellij.psi.PsiCallExpression;
                    import com.intellij.psi.PsiExpression;
                    import com.intellij.psi.PsiField;
                    import com.intellij.psi.PsiMethod;
                    import com.intellij.psi.util.PsiTreeUtil;
                    import com.android.tools.lint.detector.api.Detector;
                    import org.jetbrains.uast.UFile;
                    import org.jetbrains.uast.UMethod;
                    import org.jetbrains.uast.UField;
                    import com.android.tools.lint.detector.api.Category;
                    import com.android.tools.lint.detector.api.Detector;
                    import com.android.tools.lint.detector.api.Implementation;
                    import com.android.tools.lint.detector.api.Issue;
                    import com.android.tools.lint.detector.api.JavaContext;
                    import com.android.tools.lint.detector.api.Scope;
                    import com.android.tools.lint.detector.api.Severity;
                    import org.jetbrains.uast.UCallExpression;
                    import java.util.EnumSet;

                    @SuppressWarnings({"MethodMayBeStatic", "ClassNameDiffersFromFileName", "StatementWithEmptyBody", "deprecation"})
                    public class MyJavaLintDetector extends Detector {
                        public static final Issue ISSUE =
                                Issue.create(
                                        "com.android.namespaced.lint.check.FooDetector",
                                        "Wrong use of <LinearLayout>",
                                        "As described in "
                                            + "https://code.google.com/p/android/issues/detail?id=65351 blah blah blah.",
                                        Category.A11Y,
                                        3,
                                        Severity.WARNING,
                                        new Implementation(MyJavaLintDetector.class, EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)))
                                        .addMoreInfo("file://explanation.doc")
                                        .addMoreInfo("http://my.personal.blogger.com/aboutme.htm")
                                        .addMoreInfo("mailto:lint@example.com");
                        public void testGetBody(PsiMethod method) {
                            method.getBody(); // ERROR - must use UAST
                        }
                        public void testGetBody(UMethod method) {
                            method.getBody(); // ERROR - must use UAST
                        }
                        public void testGetContainingClass(UMethod method, UField field) {
                            method.getContainingClass(); // ERROR - must use UAST
                            field.getContainingClass(); // ERROR - must use UAST
                        }
                        public void testGetContainingClass(PsiMethod method, PsiField field) {
                            method.getContainingClass(); // OK - legitimate uses after resolve
                            field.getContainingClass(); // OK - legitimate uses after resolve
                        }
                        public void testEquals(PsiCallExpression element1, PsiExpression element2) {
                            if (element1.equals(element2)) { }
                            if (element2.equals(element1)) { }
                            if (element1 == element2) { }
                            if (element1 != element2) { }
                        }
                        public void testGetInitializer(PsiField field) {
                            field.getInitializer(); // ERROR - must use UAST
                        }
                        public void testParents(PsiField field, UMethod method) {
                            PsiElement parent = field.getParent(); // OK
                            PsiElement parent = method.getParent(); // ERROR
                            PsiTreeUtil.getParentOfType(field, PsiClass.class); // OK
                            PsiTreeUtil.getParentOfType(method, PsiClass.class); // ERROR
                        }

                        public void testReport(JavaContext context, UCallExpression node) {
                            context.report(ISSUE, node, context.getLocation(node),
                                "Wrong use of LinearLayout.");
                            context.report(ISSUE, node, context.getLocation(node),
                                "First problem. Second problem.");
                            context.report(ISSUE, node, context.getLocation(node),
                                "This is teh typo");
                            String message = "Welcome to Andriod";
                            context.report(ISSUE, node, context.getLocation(node), message);
                        }
                    }
                """
                ).indented(),
                kotlin(
                    """
                    /* Copyright (C) 2020 The Android Open Source Project */
                    package test.pkg
                    import com.intellij.psi.PsiCallExpression
                    import com.intellij.psi.PsiExpression
                    import com.intellij.psi.PsiField
                    import com.intellij.psi.PsiMethod
                    import com.intellij.psi.util.PsiTreeUtil
                    import com.android.tools.lint.detector.api.Category
                    import com.android.tools.lint.detector.api.Detector
                    import com.android.tools.lint.detector.api.Implementation
                    import com.android.tools.lint.detector.api.Issue
                    import com.android.tools.lint.detector.api.JavaContext
                    import com.android.tools.lint.detector.api.Scope
                    import com.android.tools.lint.detector.api.Severity
                    import org.jetbrains.uast.UCallExpression

                    class MyKotlinLintDetector : Detector() {
                        fun testGetBody(method: PsiMethod) {
                            val body = method.body // ERROR - must use UAST
                        }
                        @Suppress("ReplaceCallWithBinaryOperator","ControlFlowWithEmptyBody")
                        fun testEquals(element1: PsiCallExpression, element2: PsiExpression) {
                            if (element1.equals(element2)) { }
                            if (element2.equals(element1)) { }
                            if (element1 == element2) { }
                            if (element1 === element2) { }
                            if (element1 != element2) { }
                            if (element1 !== element2) { }
                            if (element1 == null) { } // OK
                            if (element1 === null) { } // OK
                            if (element1 != null) { } // OK
                            if (element1 !== null) { } // OK
                        }
                        @Suppress("UsePropertyAccessSyntax")
                        fun testGetInitializer(field: PsiField) {
                            field.getInitializer() // ERROR - must use UAST
                            field.initializer // ERROR - must use UAST
                        }
                        fun testParents(field: PsiField) {
                            val parent = field.parent
                            val method = PsiTreeUtil.getParentOfType(field, PsiMethod::class.java)
                        }

                        fun testReport(context: JavaContext, node: UCallExpression) {
                            context.report(ISSUE, node, context.getLocation(node),
                                 ""${'"'}
                                        |Instead you should call foo().bar().baz() here.
                                        |""${'"'}.trimIndent())
                        }

                        companion object {
                            private val IMPLEMENTATION =
                                Implementation(
                                    MyKotlinLintDetector::class.java,
                                    Scope.JAVA_FILE_SCOPE
                                )

                            val ISSUE =
                                Issue.create(
                                    id = "badlyCapitalized id",
                                    briefDescription = "checks MyLintDetector",
                                    explanation = ""${'"'}
                                        Some description here.
                                        Here's a call: foo.bar.baz(args).
                                        ""${'"'}.trimIndent(),
                                    category = Category.INTEROPERABILITY_KOTLIN,
                                    moreInfo = "https://code.google.com/p/android/issues/detail?id=65351", // OBSOLETE
                                    priority = 4,
                                    severity = Severity.WARNING,
                                    implementation = IMPLEMENTATION
                                )
                                .addMoreInfo("https://issuetracker.google.com/issues/3733548") // ERROR - missing digit
                                .addMoreInfo("https://issuetracker.google.com/issues/373354878") // OK - including digit
                                .addMoreInfo("http://issuetracker.google.com/issues/37335487") // ERROR - http instead of https
                                .addMoreInfo("https://b.corp.google.com/issues/139153781") // ERROR - don't point to buganizer with internal link
                        }
                    }
                    """
                ).indented(),
                kotlin(
                    """
                    package test.pkg
                    import com.android.tools.lint.client.api.IssueRegistry
                    class MyIssueRegistry : IssueRegistry() {
                        override val issues = listOf(
                            MyJavaLintDetector.ISSUE,
                            MyKotlinLintDetector.Companion.ISSUE
                        )
                    }
                    """
                ).indented(),
                kotlin(
                    """
                        package test.pkg
                        import com.android.tools.lint.checks.infrastructure.LintDetectorTest
                        import com.android.tools.lint.detector.api.Detector

                        class MyKotlinLintDetectorTest : LintDetectorTest() {
                            override fun getDetector(): Detector {
                                return MyKotlinLintDetector()
                            }

                            fun testBasic() {
                                val expected = ""${'"'}
                                    src/test/pkg/AlarmTest.java:9: Warning: Value will be forced up to 5000 as of Android 5.1; don't rely on this to be exact [ShortAlarm]
                                            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, 50, 10, null); // ERROR
                                                                                                     ~~
                                    0 errors, 1 warnings
                                    ""${'"'}

                                lint().files(
                                    kotlin(
                                        ""${'"'}
                                        fun test() {
                                            println("Value=${"$"}{"$"}")
                                        }
                                        ""${'"'}
                                    ),
                                    java(
                                        "src/test/pkg/AlarmTest.java",
                                        ""${'"'}
                                            package test.pkg;

                                            import android.app.AlarmManager;
                                            @SuppressWarnings("ClassNameDiffersFromFileName")
                                            public class AlarmTest {
                                                public void test(AlarmManager alarmManager) {
                                                    alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, 5000, 60000, null); // OK
                                                    alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, 6000, 70000, null); // OK
                                                    alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, 50, 10, null); // ERROR
                                                    alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, 5000,  // ERROR
                                                            OtherClass.MY_INTERVAL, null);                          // ERROR
                                                }

                                                private static class OtherClass {
                                                    public static final long MY_INTERVAL = 1000L;
                                                }
                                            }
                                            ""${'"'}.trimIndent()
                                    )
                                ).run().expect(expected)
                            }
                        }
                    """
                ).indented(),
                *getLintClassPath()
            )
            .issues(
                *issues
            )
            .allowMissingSdk()
            .run()
            .expect(
                """
                src/test/pkg/MyJavaLintDetector.java:30: Error: Don't point to old http://b.android.com links; should be using https://issuetracker.google.com instead [LintImplBadUrl]
                                        + "https://code.google.com/p/android/issues/detail?id=65351 blah blah blah.",
                                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MyJavaLintDetector.java:35: Error: Unexpected protocol file in file://explanation.doc [LintImplBadUrl]
                                    .addMoreInfo("file://explanation.doc")
                                                  ~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MyKotlinLintDetector.kt:67: Error: Don't point to old http://b.android.com links; should be using https://issuetracker.google.com instead [LintImplBadUrl]
                                moreInfo = "https://code.google.com/p/android/issues/detail?id=65351", // OBSOLETE
                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MyKotlinLintDetector.kt:72: Error: Suspicious issue tracker length; expected a 9 digit issue id, but was 7 [LintImplBadUrl]
                            .addMoreInfo("https://issuetracker.google.com/issues/3733548") // ERROR - missing digit
                                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MyKotlinLintDetector.kt:60: Error: Lint issue IDs should use capitalized camel case, such as MyIssueId [LintImplIdFormat]
                                id = "badlyCapitalized id",
                                      ~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MyJavaLintDetector.java:70: Warning: "LinearLayout" looks like a code reference; surround with backtics in string to display as symbol, e.g. `LinearLayout` [LintImplTextFormat]
                            "Wrong use of LinearLayout.");
                                          ~~~~~~~~~~~~
                src/test/pkg/MyJavaLintDetector.java:70: Warning: Single sentence error messages should not end with a period [LintImplTextFormat]
                            "Wrong use of LinearLayout.");
                             ~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MyJavaLintDetector.java:74: Warning: "teh" is a common misspelling; did you mean "the" ? [LintImplTextFormat]
                            "This is teh typo");
                                     ~~~
                src/test/pkg/MyJavaLintDetector.java:76: Warning: "Andriod" is a common misspelling; did you mean "Android" ? [LintImplTextFormat]
                        context.report(ISSUE, node, context.getLocation(node), message);
                                                                               ~~~~~~~
                src/test/pkg/MyKotlinLintDetector.kt:47: Warning: "foo().bar().baz()" looks like a call; surround with backtics in string to display as symbol, e.g. `foo().bar().baz()` [LintImplTextFormat]
                                    |Instead you should call foo().bar().baz() here.
                                                             ~~~~~~~~~~~~~~~~~
                src/test/pkg/MyKotlinLintDetector.kt:61: Warning: The issue summary should be capitalized [LintImplTextFormat]
                                briefDescription = "checks MyLintDetector",
                                                    ~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MyKotlinLintDetector.kt:64: Warning: "foo.bar.baz(args)" looks like a call; surround with backtics in string to display as symbol, e.g. `foo.bar.baz(args)` [LintImplTextFormat]
                                    Here's a call: foo.bar.baz(args).
                                                   ~~~~~~~~~~~~~~~~~
                src/test/pkg/MyJavaLintDetector.java:36: Error: Unexpected URL host my.personal.blogger.com; for the builtin Android Lint checks make sure to use an authoritative link (http://my.personal.blogger.com/aboutme.htm) [LintImplUnexpectedDomain]
                                    .addMoreInfo("http://my.personal.blogger.com/aboutme.htm")
                                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MyKotlinLintDetector.kt:74: Error: Use https, not http, for more info links (http://issuetracker.google.com/issues/37335487) [LintImplUnexpectedDomain]
                            .addMoreInfo("http://issuetracker.google.com/issues/37335487") // ERROR - http instead of https
                                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MyKotlinLintDetector.kt:75: Error: Don't use internal Google links (https://b.corp.google.com/issues/139153781) [LintImplUnexpectedDomain]
                            .addMoreInfo("https://b.corp.google.com/issues/139153781") // ERROR - don't point to buganizer with internal link
                                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MyJavaLintDetector.java:34: Warning: Use Scope.JAVA_AND_RESOURCE_FILES instead [LintImplUseExistingConstants]
                                    new Implementation(MyJavaLintDetector.class, EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)))
                                                                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MyKotlinLintDetectorTest.kt:22: Error: In unit tests, use the fullwidth dollar sign, ＄, instead of ＄, to avoid having to use cumbersome escapes. Lint will treat a ＄ as a ＄. [LintImplDollarEscapes]
                                    println("Value=＄{"＄"}")
                                                   ~~~~~~
                src/test/pkg/MyJavaLintDetector.java:53: Error: Don't compare PsiElements with equals, use isEquivalentTo(PsiElement) instead [LintImplPsiEquals]
                        if (element1.equals(element2)) { }
                            ~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MyJavaLintDetector.java:54: Error: Don't compare PsiElements with equals, use isEquivalentTo(PsiElement) instead [LintImplPsiEquals]
                        if (element2.equals(element1)) { }
                            ~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MyKotlinLintDetector.kt:23: Error: Don't compare PsiElements with equals, use isEquivalentTo(PsiElement) instead [LintImplPsiEquals]
                        if (element1.equals(element2)) { }
                            ~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MyKotlinLintDetector.kt:24: Error: Don't compare PsiElements with equals, use isEquivalentTo(PsiElement) instead [LintImplPsiEquals]
                        if (element2.equals(element1)) { }
                            ~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MyKotlinLintDetector.kt:25: Error: Don't compare PsiElements with equals, use isEquivalentTo(PsiElement) instead [LintImplPsiEquals]
                        if (element1 == element2) { }
                            ~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MyKotlinLintDetector.kt:27: Error: Don't compare PsiElements with equals, use isEquivalentTo(PsiElement) instead [LintImplPsiEquals]
                        if (element1 != element2) { }
                            ~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MyKotlinLintDetector.kt:65: Error: No need to call .trimIndent() in issue registration strings; they are already trimmed by indent by lint when displaying to users [LintImplTrimIndent]
                                    ""${'"'}.trimIndent(),
                                        ~~~~~~~~~~~~
                src/test/pkg/MyKotlinLintDetectorTest.kt:46: Error: No need to call .trimIndent() in issue registration strings; they are already trimmed by indent by lint when displaying to users. Instead, call .indented() on the surrounding java() test file construction [LintImplTrimIndent]
                                    ""${'"'}.trimIndent()
                                        ~~~~~~~~~~~~
                src/test/pkg/MyJavaLintDetector.java:24: Warning: New lint checks should be implemented in Kotlin to take advantage of a lot of Kotlin-specific mechanisms in the Lint API [LintImplUseKotlin]
                public class MyJavaLintDetector extends Detector {
                             ~~~~~~~~~~~~~~~~~~
                src/test/pkg/MyJavaLintDetector.java:39: Error: Don't call PsiMethod#getBody(); you must use UAST instead. If you don't have a UMethod call UastFacade.getMethodBody(method) [LintImplUseUast]
                        method.getBody(); // ERROR - must use UAST
                        ~~~~~~~~~~~~~~~~
                src/test/pkg/MyJavaLintDetector.java:45: Error: Don't call PsiMember#getContainingClass(); you should use UAST instead and call getContainingUClass() [LintImplUseUast]
                        method.getContainingClass(); // ERROR - must use UAST
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MyJavaLintDetector.java:46: Error: Don't call PsiMember#getContainingClass(); you should use UAST instead and call getContainingUClass() [LintImplUseUast]
                        field.getContainingClass(); // ERROR - must use UAST
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MyJavaLintDetector.java:59: Error: Don't call PsiField#getInitializer(); you must use UAST instead. If you don't have a UField call UastFacade.getInitializerBody(field) [LintImplUseUast]
                        field.getInitializer(); // ERROR - must use UAST
                        ~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MyJavaLintDetector.java:63: Error: Don't call PsiElement#getParent(); you should use UAST instead and call getUastParent() [LintImplUseUast]
                        PsiElement parent = method.getParent(); // ERROR
                                            ~~~~~~~~~~~~~~~~~~
                src/test/pkg/MyJavaLintDetector.java:65: Error: Don't call PsiTreeUtil#getParentOfType(); you should use UAST instead and call UElement.parentOfType [LintImplUseUast]
                        PsiTreeUtil.getParentOfType(method, PsiClass.class); // ERROR
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MyKotlinLintDetector.kt:19: Error: Don't call PsiMethod#getBody(); you must use UAST instead. If you don't have a UMethod call UastFacade.getMethodBody(method) [LintImplUseUast]
                        val body = method.body // ERROR - must use UAST
                                   ~~~~~~~~~~~
                src/test/pkg/MyKotlinLintDetector.kt:36: Error: Don't call PsiField#getInitializer(); you must use UAST instead. If you don't have a UField call UastFacade.getInitializerBody(field) [LintImplUseUast]
                        field.getInitializer() // ERROR - must use UAST
                        ~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MyKotlinLintDetector.kt:37: Error: Don't call PsiField#getInitializer(); you must use UAST instead. If you don't have a UField call UastFacade.getInitializerBody(field) [LintImplUseUast]
                        field.initializer // ERROR - must use UAST
                        ~~~~~~~~~~~~~~~~~
                26 errors, 9 warnings
                """
            )
            .expectFixDiffs(
                """
                Fix for src/test/pkg/MyJavaLintDetector.java line 70: Surround with backtics:
                @@ -70 +70
                -             "Wrong use of LinearLayout.");
                +             "Wrong use of `LinearLayout`.");
                Fix for src/test/pkg/MyJavaLintDetector.java line 70: Remove period:
                @@ -70 +70
                -             "Wrong use of LinearLayout.");
                +             "Wrong use of LinearLayout");
                Fix for src/test/pkg/MyJavaLintDetector.java line 74: Replace with "the":
                @@ -74 +74
                -             "This is teh typo");
                +             "This is the typo");
                Fix for src/test/pkg/MyKotlinLintDetector.kt line 47: Surround with backtics:
                @@ -47 +47
                -                     |Instead you should call foo().bar().baz() here.
                +                     |Instead you should call `foo().bar().baz()` here.
                Fix for src/test/pkg/MyKotlinLintDetector.kt line 64: Surround with backtics:
                @@ -64 +64
                -                     Here's a call: foo.bar.baz(args).
                +                     Here's a call: `foo.bar.baz(args)`.
                Fix for src/test/pkg/MyJavaLintDetector.java line 34: Replace with Scope.JAVA_AND_RESOURCE_FILES:
                @@ -34 +34
                -                     new Implementation(MyJavaLintDetector.class, EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)))
                +                     new Implementation(MyJavaLintDetector.class, Scope.JAVA_AND_RESOURCE_FILES))
                Fix for src/test/pkg/MyKotlinLintDetectorTest.kt line 22: Replace with ＄:
                @@ -22 +22
                -                     println("Value=＄{"＄"}")
                +                     println("Value=＄")
                Fix for src/test/pkg/MyKotlinLintDetector.kt line 65: Delete:
                @@ -65 +65
                -                     ""${'"'}.trimIndent(),
                +                     ""${'"'}.,
                """
            )
    }

    @Test
    fun testOnSources() {
        // Attempt to run lint on its own source code, if we can find it. It will already
        // be run on the lint source code as part of our continuous build with the bazel
        // wrappers, so this is just for convenience when developing the rules
        val root = TestUtils.getWorkspaceRoot()
        val srcFiles =
            getTestSources(root, "tools/base/lint/libs/lint-checks/src/main/java") +
                getTestSources(root, "tools/base/lint/studio-checks/src/main/java")
        if (srcFiles.isEmpty()) {
            // This test doesn't work in Bazel; we don't ship all the source files of lint
            // as a dependency. Note however than in Bazel we actually run the lint checks
            // directly on the sources via StudioIssueRegistry and the bazel integration of it.
            return
        }

        val libs = mutableListOf<File>()
        val classPath: String = System.getProperty("java.class.path")
        for (path in classPath.split(':')) {
            val file = File(path)
            val name = file.name
            if (name.endsWith(DOT_JAR)) {
                libs.add(file)
            } else if (!file.path.endsWith("android.sdktools.base.lint.checks-base") &&
                !file.path.endsWith("android.sdktools.base.lint.studio-checks")
            ) {
                libs.add(file)
            }
        }

        // Symlink to all the jars on the classpath and insert a src/ link
        lint()
            .issues(*(issues.filter { it != PSI_COMPARE }.toTypedArray()))
            .files(
                gradle("// placeholder"), // such that it's seen as a project by lint
                *srcFiles.toTypedArray(),
                *libs.mapIndexed { index, file ->
                    // Include unique index at the end to prevent conflicts
                    LibraryReferenceTestFile("libs/${file.name}_$index", file)
                }.toTypedArray()
            )
            .checkUInjectionHost(false)
            .allowMissingSdk()
            .run()
            .expectClean()
    }

    private fun getTestSources(root: File, relative: String): List<TestFile> {
        val src = File(root, relative)
        // In Bazel these source files don't exist
        if (!src.isDirectory) {
            return emptyList()
        }
        val srcPath = src.path
        return src.walkTopDown().mapNotNull {
            if (it.isFile) {
                val target = "src/main/java/" + it.path.substring(srcPath.length + 1)
                val contents = it.readText()
                source(target, contents)
            } else {
                null
            }
        }.toList()
    }
}

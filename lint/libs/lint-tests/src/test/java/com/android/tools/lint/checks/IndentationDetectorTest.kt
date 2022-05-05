/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.TestLintClient
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.client.api.LintClient.Companion.CLIENT_STUDIO
import com.android.tools.lint.detector.api.Detector
import java.io.File

class IndentationDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return IndentationDetector()
    }

    override fun lint(): TestLintTask {
        // The body removal test mode does not apply here; we're deliberately
        // testing for brace-blocks
        return super.lint().skipTestModes(TestMode.BODY_REMOVAL, TestMode.IF_TO_WHEN)
    }

    fun testDocumentationExample() {
        @Suppress("UseWithIndex", "ControlFlowWithEmptyBody", "SuspiciousIndentAfterControlStatement")
        lint().files(
            java(
                """
                class Java {
                  public void test(Object context) {
                    if (context == null)
                    System.out.println("test"); // WARN 1
                    if (context == null)
                        System.out.println("test"); // OK
                        System.out.println("test"); // WARN 2
                  }
                }
                """
            ).indented(),
            kotlin(
                "src/Kotlin.kt",
                """
                fun String.getLineAndColumn(offset: Int): Pair<Int,Int> {
                    var line = 1
                    var column = 1
                    for (i in 0 until offset) {
                        column++
                        if (this[i] == '\n')
                            column = 0
                            line++ // WARN 3
                    }
                    return Pair(line, column)
                }

                fun getFooter1(price: Int) {
                    var s = "The price is: " // missing +
                        price.toString() + // WARN 4
                        "."
                }

                fun getFooter2(price: Int) {
                    var s = ""
                    if (price > 100) {
                        s += "The price " // missing +
                            price.toString() + // WARN 5
                            " is high."
                    }
                }

                fun getFooter3(price: Int) {
                    var s = ""
                    if (price < 1000) {
                        s = "The price was " // missing +
                          price.toString() + // WARN 6
                          "."
                    }
                }

                fun loops1() {
                    var x = 0
                    var y = 0
                    for (i in 0 until 100)
                        x++
                        y++ // WARN 7
                }

                fun loops2() {
                    while (x > 0)
                        x--
                        y++ // WARN 8
                }

                fun expectedIndent1(x: Int) {
                   if (x > 10)
                       println("hello") // OK
                   if (x < 10)
                   println("hello")     // WARN 9
                }

                fun expectedIndent2(x: Int) {
                   if (x < 10)
                       println("hello")
                   else
                   println("hello")     // WARN 10
                }

                fun String.getLineAndColumn2(offset: Int): Pair<Int,Int> {
                    var line = 1
                    var column = 1
                    for (i in 0 until offset) {
                        column++
                        if (this[i] != '\n') {
                        } else
                            column = 0
                            line++ // WARN 11
                    }
                    return Pair(line, column)
                }
                """
            ).indented()
        ).skipTestModes(TestMode.PARENTHESIZED, TestMode.WHITESPACE).run().expect(
            """
            src/Java.java:4: Error: Suspicious indentation: This is conditionally executed; expected it to be indented [SuspiciousIndentation]
                System.out.println("test"); // WARN 1
                ~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/Java.java:3: Previous statement here
                if (context == null)
                ~~~~~~~~~~~~~~~~~~~~
            src/Java.java:7: Error: Suspicious indentation: This is indented but is not nested under the previous expression (if (context == null)...) [SuspiciousIndentation]
                    System.out.println("test"); // WARN 2
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/Java.java:5: Previous statement here
                if (context == null)
                ~~~~~~~~~~~~~~~~~~~~
            src/Kotlin.kt:8: Error: Suspicious indentation: This is indented but is not nested under the previous expression (if (this[i] == '\n')...) [SuspiciousIndentation]
                        line++ // WARN 3
                        ~~~~~~
                src/Kotlin.kt:6: Previous statement here
                    if (this[i] == '\n')
                    ~~~~~~~~~~~~~~~~~~~~
            src/Kotlin.kt:15: Error: Suspicious indentation: This is indented but is not continuing the previous expression (var s = "The price i...) [SuspiciousIndentation]
                    price.toString() + // WARN 4
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/Kotlin.kt:14: Previous statement here
                var s = "The price is: " // missing +
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/Kotlin.kt:23: Error: Suspicious indentation: This is indented but is not continuing the previous expression (s += "The price...) [SuspiciousIndentation]
                        price.toString() + // WARN 5
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/Kotlin.kt:22: Previous statement here
                    s += "The price " // missing +
                    ~~~~~~~~~~~~~~~~~
            src/Kotlin.kt:32: Error: Suspicious indentation: This is indented but is not continuing the previous expression (s = "The price was...) [SuspiciousIndentation]
                      price.toString() + // WARN 6
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/Kotlin.kt:31: Previous statement here
                    s = "The price was " // missing +
                    ~~~~~~~~~~~~~~~~~~~~
            src/Kotlin.kt:42: Error: Suspicious indentation: This is indented but is not nested under the previous expression (for (i in 0 until 10...) [SuspiciousIndentation]
                    y++ // WARN 7
                    ~~~
                src/Kotlin.kt:40: Previous statement here
                for (i in 0 until 100)
                ~~~~~~~~~~~~~~~~~~~~~~
            src/Kotlin.kt:48: Error: Suspicious indentation: This is indented but is not nested under the previous expression (while (x > 0)       ...) [SuspiciousIndentation]
                    y++ // WARN 8
                    ~~~
                src/Kotlin.kt:46: Previous statement here
                while (x > 0)
                ~~~~~~~~~~~~~
            src/Kotlin.kt:55: Error: Suspicious indentation: This is conditionally executed; expected it to be indented [SuspiciousIndentation]
               println("hello")     // WARN 9
               ~~~~~~~~~~~~~~~~
                src/Kotlin.kt:54: Previous statement here
               if (x < 10)
               ~~~~~~~~~~~
            src/Kotlin.kt:62: Error: Suspicious indentation: This is conditionally executed; expected it to be indented [SuspiciousIndentation]
               println("hello")     // WARN 10
               ~~~~~~~~~~~~~~~~
                src/Kotlin.kt:59: Previous statement here
               if (x < 10)
               ~~~~~~~~~~~
            src/Kotlin.kt:73: Error: Suspicious indentation: This is indented but is not nested under the previous expression (if (this[i] != '\n')...) [SuspiciousIndentation]
                        line++ // WARN 11
                        ~~~~~~
                src/Kotlin.kt:70: Previous statement here
                    if (this[i] != '\n') {
                    ~~~~~~~~~~~~~~~~~~~~~~
            11 errors, 0 warnings
            """
        )
    }

    fun testWarnMixedIndentation() {
        @Suppress("UnusedAssignment", "ConstantConditions")
        lint().files(
            java(
                "" +
                    "class Java {\n" +
                    "    int x;\n" +
                    "        int y;\n" +
                    "    public void test() {\n" +
                    // We don't flag adjacent statements that probably don't
                    // matter
                    "        int x = 0;\n" +
                    "        int y = 0;\n" +
                    "        x = 0;\n" +
                    "    \t   y = 1;\n" +
                    "    }\n" +
                    "}"
            ).indented()
        ).run().expect(
            """
            src/Java.java:8: Warning: The indentation string here is different from on the previous line (" " vs \t) [SuspiciousIndentation]
                    y = 1;
                ~~~~
                src/Java.java:7: Previous line indentation here
                    x = 0;
                ~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testNoFalsePositives() {
        @Suppress(
            "ResultOfMethodCallIgnored", "UnusedAssignment", "RedundantIfStatement", "ConstantConditions",
            "SuspiciousIndentAfterControlStatement"
        )
        lint().files(
            kotlin(
                """
                fun String.getLineAndColumn(offset: Int): Pair<Int,Int> {
                    var line = 1
                    var column = 1
                    for (i in 0 until offset) {
                        column++
                        if (this[i] == '\n') {
                            column = 0
                        }
                            line++
                        if (this[i] == '\n')
                            column = 0
                         line++ // indented but only by 1 space
                    }
                    return Pair(line, column)
                }

                fun getFooter(price: Int) {
                    println("The price is: ") // missing +
                        price.toString() +
                        "."

                    println("The price is: ") +
                        price.toString() // missing +
                        "."
                }
                """
            ).indented(),
            kotlin(
                "src/test.kt",
                "" +
                    "  @Test\n" +
                    "  fun failToParseDuplicates() {\n" +
                    "    val input = \"\"\"\n" +
                    "    <attr name=\"foo\">\n" +
                    "        <enum name=\"bar\" value=\"0\"/>\n" +
                    "        <enum name=\"bar\" value=\"1\"/>\n" +
                    "    </attr>\n" +
                    "    \"\"\".trimIndent()\n" +
                    "\n" +
                    "      val mockLogger = BlameLoggerTest.MockLogger()\n" +
                    "      assertThat(testParse(input, mockLogger = mockLogger)).isFalse()\n" +
                    "      assertThat(mockLogger.errors).hasSize(1)\n" +
                    "      val errorMsg = mockLogger.errors.single().first\n" +
                    "\n" +
                    "      assertThat(errorMsg).contains(\n" +
                    "          \"test.xml.rewritten:7:1: Duplicate symbol 'id/bar' defined here:\")\n" +
                    "      assertThat(errorMsg).contains(\n" +
                    "          \"test.xml.rewritten:7:1:  and here:\")\n" +
                    "      assertThat(errorMsg)\n" +
                    "          .contains(\"test.xml.rewritten:6:1\")\n" +
                    "  }"
            ).indented(),
            kotlin(
                "" +
                    "fun manifestStrings(activityClass: String, isNewModule: Boolean, generateActivityTitle: Boolean): String {\n" +
                    "  val innerBlock = renderIf(!isNewModule && generateActivityTitle) {\n" +
                    "    \"\"\"<string name=\"title_\${activityToLayout(activityClass)}\">\$activityClass</string>\"\"\"\n" +
                    "  }\n" +
                    "\n" +
                    "    return \"\"\"\n" +
                    "<resources>\n" +
                    "    \$innerBlock\n" +
                    "</resources>\n" +
                    "\"\"\"\n" +
                    "}"
            ).indented(),
            java(
                """
                package test.pkg;
                public class JavaTest {
                    public void test(int x) {
                        if (x > 100) {
                            System.out.println("> 100");
                        }
                            System.out.println("Done.");
                    }

                    public boolean connect(ConstraintAnchor toAnchor) {
                        if (toAnchor == null) {
                            mTarget = null;
                            mMargin = 0;
                            mGoneMargin = UNSET_GONE_MARGIN;
                                  return true;
                        }
                    }

                    public void test() {
                        if (c == '>') {
                            styles.put(offset + 1, STYLE_PLAIN_TEXT);
                            state = STATE_TEXT;
                        } else //noinspection StatementWithEmptyBody
                        if (c == '/') {
                            // we expect an '>' next to close the tag
                        } else if (!Character.isWhitespace(c)) {
                            styles.put(offset, STYLE_ATTRIBUTE);
                        }
                    }

                    public static Comment getPreviousComment(@NonNull Node element) {
                        Node node = element;
                      do {
                            node = node.getPreviousSibling();
                            if (node instanceof Comment) {
                                return (Comment)node;
                            }
                        }
                        while (node instanceof Text && CharMatcher.whitespace().matchesAllOf(node.getNodeValue()));
                        return null;
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                // From tools/idea/plugins/kotlin/idea/tests/test/org/jetbrains/kotlin/idea/perf/UltraLightChecker.kt
                fun checkByJavaFile(testDataPath: String, lightClasses: List<KtLightClass>) {
                    val expectedTextFile = getJavaFileForTest(testDataPath)
                    val renderedResult = renderLightClasses(testDataPath, lightClasses)
                        KotlinTestUtils.assertEqualsToFile(expectedTextFile, renderedResult)
                }
                """
            ).indented(),
            kotlin(
                """
                // From tools/idea/plugins/gradle/java/testSources/execution/test/GradleJavaTestEventsIntegrationTest.kt
              private fun `call task for specific test overrides existing filters`() {
                val settings: Integer = createSettings {
                  putUserData(GradleConstants.RUN_TASK_AS_TEST, true)
                  withArguments("--tests","my.otherpack.*")
                }

                  GradleTaskManager().executeTasks(createId(),
                                                   listOf(":cleanTest", ":test"),
                                                   projectPath,
                                                   settings,
                                                   null,
                                                   testListener)
              }
                """
            ).indented(),
            java(
                """
                // From tools/idea/platform/util-ex/src/org/jetbrains/mvstore/MVStore.java
                class MVStore {
                  int nonLeafPageSplitSize;
                  int leafPageSplitSize;
                  Object chunkIdToToC;
                  Object nonLeafPageCache;
                  public void test() {
                        nonLeafPageCache = null;
                        leafPageCache = null;
                      chunkIdToToC = null;
                        nonLeafPageSplitSize = Long.MAX_VALUE;
                        leafPageSplitSize = Long.MAX_VALUE;

                  }
                }
                """
            ).indented(),
            java(
                """
                // From tools/idea/jps/jps-builders/gen/org/jetbrains/jps/api/CmdlineRemoteProto.java
                import java.util.concurrent.Future;
                class CmdlineRemoteProto {
                    /**
                     * <code>optional .org.jetbrains.jpsservice.Message.Failure failure = 5;</code>
                     */
                     Object failure_;
                     private int bitField0_;
                    private void setFailure(org.jetbrains.jps.api.CmdlineRemoteProto.Message.Failure value) {
                      value.getClass();
                  failure_ = value;
                      bitField0_ |= 0x00000010;
                      }
              }
              """
            ).indented(),
            java(
                """
                // From tools/idea/java/debugger/impl/src/com/intellij/debugger/memory/ui/InstancesView.java
                import java.util.concurrent.Future;
                class InstancesView {
                      private volatile Future<?> myFilteringTaskFuture;
                      private void test() {
                        synchronized (myFilteringTaskLock) {
                          List<JavaReferenceInfo> finalInstances = instances;
                          ApplicationManager.getApplication().runReadAction(() -> {
                            myFilteringTask =
                              new FilteringTask(myClassName, myDebugProcess, myFilterConditionEditor.getExpression(), new MyValuesList(finalInstances),
                                                new MyFilteringCallback(evaluationContext));

                              myFilteringTaskFuture = ApplicationManager.getApplication().executeOnPooledThread(myFilteringTask);
                          });
                        }
                  }
                }
                """
            ).indented(),
            java(
                """
                // From CidrGoogleOutputToGeneralTestEventsConverter.java
                public class CidrGoogleOutputToGeneralTestEventsConverter {
                  private void ensureOpen(@NotNull GeneralTestEventsProcessor processor,
                                          @NotNull CidrGoogleTestLinker linker,
                                          boolean runState) {
                    final String[] pathPartsAsNodeIds = ArrayUtil.toStringArray(CidrTestScopeElementImpl.splitPath(linker.getPath()));
                    int partsCount = pathPartsAsNodeIds.length;
                    String orderNamePart = null;
                    if (partsCount > 0
                        && ORDER_NAME.equals(splitByFirst(pathPartsAsNodeIds[partsCount - 1], KIND_SEPARATOR).first)) {
                      orderNamePart = pathPartsAsNodeIds[partsCount - 1];
                        --partsCount;
                    }
                  }
                }
              """
            ).indented(),
            java(
                """
                // From StandardConversionSequence.java
                class StandardConversionSequence {
                  boolean isPointerConversionToBool(@NotNull OCResolveContext context) {
                    if (OCIntType.isBool(getToType(1), context) &&
                        (getFromType() instanceof OCPointerType ||
                         First == ICK_Array_To_Pointer || First == ICK_Function_To_Pointer))
                    return true;

                    return false;
                  }
                }
                """
            ).indented()
        ).skipTestModes(TestMode.PARENTHESIZED).run().expectClean()
    }

    fun testCommentedOut() {
        @Suppress("SuspiciousIndentAfterControlStatement")
        lint().files(
            java(
                """
                class Test {
                  public void test() {
                   // Prevent updates while the list shows one of the state messages
                    if (mBluetoothAdapter.getState() != BluetoothAdapter.STATE_ON) return;
                    //if (mFilter.matches(cachedDevice.getDevice())) {
                       createDevicePreference(cachedDevice);
                    //}
                    //
                  }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testBrokenTestModeOffsets() {
        // Multi-file classes create a single UAST file which points into separate
        // source files (see UFile.acceptSameFile); this would trip up the source test
        // mode transformers. Here's a test case which threw exceptions in that scenario
        // before it was fixed.
        lint().files(
            kotlin(
                "src/file1.kt",
                """
                @file:JvmMultifileClass
                @file:JvmName("FlowKt")
                package test.pkg

                fun test1(b: Boolean) {
                    if (true)
                        println("test")
                }
                """
            ).indented(),
            kotlin(
                "src/file2.kt",
                """
                @file:JvmMultifileClass
                @file:JvmName("FlowKt")
                package test.pkg

                fun test2(b: Boolean) {
                    // (true)
                    println("test")
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testMultiPart() {
        // This is a carefully constructed test case to reproduce a bug similar to the one observed in
        // https://issuetracker.google.com/215741972
        // Basically, we create a single class ("Test") from two separate .kt files. In test2, we have
        // an if statement followed by a second statement; the indentation checker looks to see if that
        // second statement (which is not nested) is also indented, which would be an error. However,
        // because UAST constructs a single AST file for these two functions, if we just visit the
        // functions, we might actually be looking at source PSI elements in a different file than the
        // one we intended to analyze. And in this case, when we visit file1 (and use the source code
        // for file1), we'll also come across file2, and we'll get to the if statement there. When we
        // get to the statement following the if statement, and we look up its offsets, if we looked at
        // the right file, we'd see that it's not indented. However, because the sources we consult
        // is actually for file 1, we'll look at the offsets there, so we'll look at the indentation of
        // the corresponding region in file1, and we'll see an indent and report it as an error, even
        // though those sources are totally unrelated (and the indentation is fine because in that case
        // we've placed braces around the statements).
        //
        // The fix here is actually in UElementVisitor; we should only be visiting the AST parts
        // related to the current source file.
        lint().files(
            kotlin(
                "src/file1.kt",
                """
                @file:JvmMultifileClass
                @file:JvmName("Test")
                package test.pkg

                fun test1(b: Boolean) {
                    if (true) { // 1
                        println("test1a")
                        println("test1b")
                    }
                }
                """
            ).indented(),
            kotlin(
                "src/file2.kt",
                """
                @file:JvmMultifileClass
                @file:JvmName("Test")
                package test.pkg

                fun test2(b: Boolean) {
                    if (true) // file 2
                        println("test2a")
                    println("test2b")
                }
                """
            ).indented()
        ).run().expectClean()
    }

    @Suppress("SuspiciousIndentAfterControlStatement")
    fun testIdeHandling() {
        val clientFactory = { TestLintClient(CLIENT_STUDIO) }

        val testFile = java(
            """
                class Java {
                  public void test(Object context) {
                    if (context == null)
                    System.out.println("test"); // WARN
                  }
                }
                """
        ).indented()

        // Option off, IDE, incremental/on the fly mode: no warnings
        lint().files(testFile)
            .clientFactory(clientFactory)
            .incremental()
            .configureOption(IndentationDetector.ALWAYS_RUN_OPTION, false)
            .run().expectClean()

        val warnings = """
                src/Java.java:4: Error: Suspicious indentation: This is conditionally executed; expected it to be indented [SuspiciousIndentation]
                    System.out.println("test"); // WARN
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
                    src/Java.java:3: Previous statement here
                    if (context == null)
                    ~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """

        // Option off, IDE, batch mode: warn
        lint().files(testFile)
            .clientFactory(clientFactory)
            .configureOption(IndentationDetector.ALWAYS_RUN_OPTION, false)
            .run().expect(warnings)

        // Option on, IDE, incremental/on the fly mode: warn
        lint().files(testFile)
            .incremental()
            .clientFactory(clientFactory)
            .configureOption(IndentationDetector.ALWAYS_RUN_OPTION, true)
            .run().expect(warnings)

        // Option off, IDE, incremental/on the fly mode but with the file not having been edited: warn
        lint().files(testFile)
            .clientFactory {
                object : com.android.tools.lint.checks.infrastructure.TestLintClient(CLIENT_STUDIO) {
                    override fun isEdited(file: File, returnIfUnknown: Boolean, savedSinceMsAgo: Long): Boolean {
                        return false
                    }
                }
            }
            .configureOption(IndentationDetector.ALWAYS_RUN_OPTION, false)
            .run().expect(warnings)
    }
}

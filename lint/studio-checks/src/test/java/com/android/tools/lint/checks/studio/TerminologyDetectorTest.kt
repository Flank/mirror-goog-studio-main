/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.lint.checks.infrastructure.TestFiles.source
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Locale

class TerminologyDetectorTest {
    @Test
    fun testProblems() {
        // <unicode>
        val w1 = "\u0077\u0068\u0069\u0074\u0065\u004c\u0069\u0073\u0074\u0046\u0069\u0065\u006c\u0064"
        val w2 = "\u0077\u0068\u0069\u0074\u0065\u006c\u0069\u0073\u0074"
        val w3 = "\u0067\u0065\u0074\u0057\u0068\u0069\u0074\u0065\u004c\u0069\u0073\u0074"
        val w4 = "\u0077\u0068\u0069\u0074\u0065-\u006c\u0069\u0073\u0074\u0065\u0064"
        val w5 = "\u0077\u0068\u0069\u0074\u0065\u006c\u0069\u0073\u0074"
        val w6 = "\u0077\u0068\u0069\u0074\u0065\u006c\u0069\u0073\u0074\u0065\u0064"
        val w7 = "\u0067\u0065\u0074\u0057\u0068\u0069\u0074\u0065\u004c\u0069\u0073\u00742"
        val w8 = "\u0077\u0068\u0069\u0074\u0065\u004c\u0069\u0073\u0074"
        val w9 = "\u0077\u0068\u0069\u0074\u0065-\u006c\u0069\u0073\u0074"
        val w10 = "\u0057\u0068\u0069\u0074\u0065\u004c\u0069\u0073\u0074"
        val w11 = "\u0063\u0068\u0061\u0072\u0061\u0063\u0074\u0065\u0072\u0073\u0057\u0068\u0069\u0074\u0065\u006c\u0069\u0073\u0074"
        val w12 = "\u0057\u0068\u0069\u0074\u0065\u006c\u0069\u0073\u0074"
        // </unicode>

        studioLint()
            .files(
                java(
                    """
                    package test.pkg;

                    @SuppressWarnings({
                        "MethodMayBeStatic",
                        "ClassNameDiffersFromFileName",
                        "StringOperationCanBeSimplified"
                    })
                    public class Test {
                        public int $w1 = 0;
                        // This is the $w2 variable
                        public int $w3() { return 0; }
                        /** This is $w4 */
                        public String $w5() {
                            return "This is $w6";
                        }
                        @SuppressWarnings("WrongTerminology")
                        public int $w7() {
                            // Random $w2 comment
                            return 0;
                        }
                   }
                   """
                ).indented(),
                kotlin(
                    "test/test.kt",
                    """
                      // Random $w4 comment
                      /** This is $w4 */
                      fun test() {
                            // Random $w2 comment
                            val $w11: String = ""
                      }
                    """
                ).indented(),
                source("src/main/resources/cts/${w5}_devices.json", "something"),
                source("src/main/resources/something", "device $w5:")
            )
            .issues(TerminologyDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/Test.java:9: Error: Avoid using "$w8"; consider something like "include"; see https://developers.google.com/style/word-list [WrongTerminology]
                     public int $w1 = 0;
                                ~~~~~~~~~
                src/test/pkg/Test.java:10: Error: Avoid using "$w2"; consider something like "include"; see https://developers.google.com/style/word-list [WrongTerminology]
                     // This is the $w2 variable
                                    ~~~~~~~~~
                src/test/pkg/Test.java:11: Error: Avoid using "$w10" in "$w3"; consider something like "include"; see https://developers.google.com/style/word-list [WrongTerminology]
                     public int $w3() { return 0; }
                                   ~~~~~~~~~
                src/test/pkg/Test.java:12: Error: Avoid using "$w9"; consider something like "include"; see https://developers.google.com/style/word-list [WrongTerminology]
                     /** This is $w4 */
                                 ~~~~~~~~~~
                src/test/pkg/Test.java:13: Error: Avoid using "$w5"; consider something like "include"; see https://developers.google.com/style/word-list [WrongTerminology]
                     public String $w5() {
                                   ~~~~~~~~~
                src/test/pkg/Test.java:14: Error: Avoid using "$w5"; consider something like "include"; see https://developers.google.com/style/word-list [WrongTerminology]
                         return "This is $w6";
                                         ~~~~~~~~~
                src/test/pkg/Test.java:18: Error: Avoid using "$w2"; consider something like "include"; see https://developers.google.com/style/word-list [WrongTerminology]
                         // Random $w2 comment
                                   ~~~~~~~~~
                src/main/resources/something:1: Error: Avoid using "$w5"; consider something like "include"; see https://developers.google.com/style/word-list [WrongTerminology]
                device $w5:
                       ~~~~~~~~~
                test/test.kt:1: Error: Avoid using "$w9"; consider something like "include"; see https://developers.google.com/style/word-list [WrongTerminology]
                // Random $w4 comment
                          ~~~~~~~~~~
                test/test.kt:2: Error: Avoid using "$w9"; consider something like "include"; see https://developers.google.com/style/word-list [WrongTerminology]
                /** This is $w4 */
                            ~~~~~~~~~~
                test/test.kt:4: Error: Avoid using "$w5"; consider something like "include"; see https://developers.google.com/style/word-list [WrongTerminology]
                      // Random $w5 comment
                                ~~~~~~~~~
                test/test.kt:5: Error: Avoid using "$w12" in "$w11"; consider something like "include"; see https://developers.google.com/style/word-list [WrongTerminology]
                      val $w11: String = ""
                                    ~~~~~~~~~
                src/main/resources/cts/${w5}_devices.json: Error: Avoid using "$w5" in filename; consider something like "include"; see https://developers.google.com/style/word-list [WrongTerminology]
                13 errors, 0 warnings
                """
            )
    }

    // To update the replacement list, add @Test to this method and run it. It will emit the
    // new code to insert into TerminologyDetector#checkCommentStateMachine, or if you have
    // set $ADT_SOURCE_TREE to point to your repo, it will update the source file directly.
    @Suppress("ConstantConditionIf", "unused")
    fun createStateMachine() {
        data class Recommendation(
            val replace: String,
            val with: String,
            val words: Boolean = true,
            val alias1: String? = null,
            val alias2: String? = null,
            val alias3: String? = null
        ) {
            fun getNames(): List<String> = listOfNotNull(replace, alias1, alias2, alias3)
        }

        // <unicode>
        val recommendations = listOf(
            Recommendation(
                replace = "\u0062\u006c\u0061\u0063\u006b\u006c\u0069\u0073\u0074",
                alias1 = "\u0062\u006c\u0061\u0063\u006b-\u006c\u0069\u0073\u0074",
                with = "\u0065\u0078\u0063\u006c\u0075\u0064\u0065",
                words = false
            ),
            Recommendation(
                replace = "\u0077\u0068\u0069\u0074\u0065\u006c\u0069\u0073\u0074",
                alias1 = "\u0077\u0068\u0069\u0074\u0065-\u006c\u0069\u0073\u0074",
                with = "\u0069\u006e\u0063\u006c\u0075\u0064\u0065",
                words = false
            ),
            Recommendation(
                replace = "\u0067\u0072\u0061\u006e\u0064\u0066\u0061\u0074\u0068\u0065\u0072\u0065\u0064",
                with = "\u0062\u0061\u0073\u0065\u006c\u0069\u006e\u0065"
            ),
            Recommendation(
                replace = "\u0073\u006c\u0061\u0076\u0065",
                with = "\u0073\u0065\u0063\u006f\u006e\u0064\u0061\u0072\u0079",
                words = true
            ),
            Recommendation(
                replace = "\u0066\u0075\u0063\u006b",
                with = "?",
                words = false
            )
            /*
            // Consider:
            Recommendation(
                replace = "\u0064\u0075\u006d\u006d\u0079",
                with = "\u0070\u006c\u0061\u0063\u0065\u0068\u006f\u006c\u0064\u0065\u0072",
                words = true
            )
            */
        )
        // </unicode>

        // If you're trying to figure out how this works, try turning on readable state names:
        val readableStateNames = false
        val stateVariables = false

        val stringWriter = StringWriter()
        val printer = PrintWriter(stringWriter)
        val names = recommendations.map { it.getNames() }.flatten()
            .sortedWith(compareBy({ it.length }, { it })).toSet()

        val replacements: MutableMap<String, String> = mutableMapOf()
        recommendations.forEach { recommendation ->
            for (name in recommendation.getNames()) {
                replacements[name] = recommendation.with
            }
        }

        val recommendationsMap = mutableMapOf<String, Recommendation>()
        recommendations.forEach { recommendation ->
            for (name in recommendation.getNames()) {
                recommendationsMap[name] = recommendation
            }
        }

        fun String.mustEscapeIdentifier(): Boolean {
            return isNotEmpty() && (!this[0].isJavaIdentifierStart() || this.any { !it.isJavaIdentifierPart() })
        }

        val prefixSet: MutableSet<String> = HashSet()
        prefixSet.add("") // init sat
        for (state in names) {
            for (i in 1 until state.length + 1) {
                val prefix = state.substring(0, i).toLowerCase(Locale.ROOT)
                prefixSet.add(prefix)
            }
        }
        val prefixes = prefixSet.sortedBy { it.length }.toList()

        // Map from prefix to state name
        var stateNameNumber = 0
        val stateMap: MutableMap<String, String> = HashMap()
        for (prefix in prefixes) {
            stateMap[prefix] = if (prefix.isEmpty()) {
                when {
                    readableStateNames -> "STATE_INIT"
                    stateVariables -> "init"
                    else -> (++stateNameNumber).toString()
                }
            } else {
                when {
                    readableStateNames -> {
                        val escape = prefix.mustEscapeIdentifier()
                        val surround = if (escape) "`" else ""
                        surround + "STATE_" + prefix.toUpperCase(Locale.ROOT) + surround
                    }
                    stateVariables -> "state${++stateNameNumber}"
                    else -> (++stateNameNumber).toString()
                }
            }
        }

        printer.println(
            """        // <editor-fold defaultstate="collapsed" desc="Generated state machine">
        // @formatter:off"""
        )
        var stateNumber = 0
        if (stateVariables || readableStateNames) {
            for (prefix in prefixes) {
                if (names.contains(prefix)) {
                    // No state for the end state
                    continue
                }
                val state = stateMap[prefix]
                printer.print("        ")
                if (readableStateNames) {
                    printer.print("@Suppress(\"LocalVariableName\") ")
                }
                printer.print("val $state = ${stateNumber++}")
                printer.println()
            }
        }
        printer.println(
            """        var state = ${stateMap[""]}
        var begin = 0
        var i = 0
        while (i < source.length) {
            val c = source[i++]
            when (state) {"""
        )

        for (statePrefix in prefixes) {
            if (names.contains(statePrefix)) {
                continue
            }
            val stateName = stateMap[statePrefix]
            printer.println("                $stateName -> {")
            if (statePrefix.isEmpty()) {
                printer.println("                    begin = i - 1")
            }
            printer.println("                    state = when (c) {")
            for (prefix in prefixes) {
                if (prefix.length == statePrefix.length + 1 && prefix.startsWith(statePrefix)) {
                    val c1 = prefix[statePrefix.length].toLowerCase()
                    val c2 = c1.toUpperCase()
                    printer.print("                        ")
                    printer.print("'$c1'")
                    if (c2 != c1) {
                        printer.print(", '$c2'")
                    }
                    printer.print(" -> ")
                    if (names.contains(prefix)) {
                        val recommendation = recommendationsMap[prefix]!!
                        val replacement = recommendation.with
                        val words = recommendation.words
                        val from = if (words) "begin" else "i - " + prefix.length
                        printer.println(
                            """{
                            report(context, element, source, $from, i, "$replacement", $words)
                            ${stateMap[""]}
                        }"""
                        )
                    } else {
                        printer.println(stateMap[prefix])
                    }
                }
            }

            if (statePrefix.isEmpty()) {
                printer.println(
                    """                        else -> ${stateMap[""]}
                    }
                }"""
                )
            } else {
                var next = ""
                for (j in 1 until statePrefix.length) {
                    val sub = statePrefix.substring(j)
                    if (prefixes.contains(sub)) {
                        next = sub
                        break
                    }
                }
                printer.println(
                    """                        else -> { i--; ${stateMap[next] } }
                    }
                }"""
                )
            }
        }
        printer.print(
            """            }
        }
        // @formatter:on
        // </editor-fold>"""
        )

        val generated = stringWriter.toString()

        if (replace(
            path = "tools/base/lint/studio-checks/src/main/java/com/android/tools/lint/checks/studio/TerminologyDetector.kt",
            startMarker = "// <editor-fold",
            endMarker = "// </editor-fold>",
            replacementFunction = { generated.trim() }
        )
        ) {
            return
        }

        println("Generated code; insert into TerminologyDetector, or set \$ADT_SOURCE_TREE to have it written directly:\n")
        println(generated)
    }

    // Add @Test here and run (with $ADT_SOURCE_TREE set) to encode <\u0075nicode> ranges with
    // unicode escape
    @Suppress("unused")
    fun unicodeify() {
        // Inserts unicode in the string regions between the <\u0075nicode> markers
        replace(
            path = "tools/base/lint/studio-checks/src/test/java/com/android/tools/lint/checks/studio/TerminologyDetectorTest.kt",
            startMarker = "<\u0075nicode>",
            endMarker = "</\u0075nicode>",
            replacementFunction = { source ->
                val sb = StringBuilder()
                var escaped = false
                var string = false
                for (c in source) {
                    if (c == '\\') {
                        escaped = !escaped
                        sb.append(c)
                    } else if (c == '"') {
                        // This is simplistic but even works for raw strings because there's
                        // an odd number of double quotes
                        string = !string
                        sb.append(c)
                    } else if (string && !escaped && c.isLetter()) {
                        sb.append("\\u")
                        sb.append(Character.forDigit(c.toInt() shr 12, 16))
                        sb.append(Character.forDigit(c.toInt() shr 8 and 0x0f, 16))
                        sb.append(Character.forDigit(c.toInt() shr 4 and 0x0f, 16))
                        sb.append(Character.forDigit(c.toInt() and 0x0f, 16))
                    } else {
                        sb.append(c)
                    }
                }
                sb.toString()
            }
        )
    }

    // Add @Test here and run (with $ADT_SOURCE_TREE set) to decode <\u0075nicode> ranges with
    // unicode escapes
    @Suppress("unused")
    fun unidecodeify() {
        // Inserts unicode in the string regions between the <\u0075nicode> markers
        replace(
            path = "tools/base/lint/studio-checks/src/test/java/com/android/tools/lint/checks/studio/TerminologyDetectorTest.kt",
            startMarker = "<\u0075nicode>",
            endMarker = "</\u0075nicode>",
            replacementFunction = { source ->
                val sb = StringBuilder()
                var escaped = false
                var i = 0
                while (i < source.length) {
                    val c = source[i]
                    if (c == '\\' && source[i + 1] == 'u') {
                        val unicode = source.substring(i + 2, i + 6)
                        val hex: Int = unicode.toInt(16)
                        sb.append(hex.toChar())
                        i += 6
                        continue
                    } else if (c == '\\') {
                        escaped = !escaped
                        sb.append(c)
                    } else {
                        sb.append(c)
                    }
                    i++
                }
                sb.toString()
            }
        )
    }

    /** Replaces source ranges in the source tree */
    @Suppress("SameParameterValue")
    private fun replace(
        path: String,
        startMarker: String,
        endMarker: String,
        replacementFunction: (String) -> String
    ): Boolean {
        // Set $ADT_SOURCE_TREE to point to your git repository root; if done, then
        // this will replace the updated source into the source file in place
        val root = System.getenv("ADT_SOURCE_TREE")
        if (root != null) {
            val sourceFile = if (File(path).isAbsolute) File(path) else File(root, path)
            return if (sourceFile.exists()) {
                var source = sourceFile.readText()
                var index = source.length
                while (true) {
                    val begin = source.lastIndexOf(startMarker, index)
                    var end = source.lastIndexOf(endMarker, index)
                    if (begin == -1 || end == -1 || end <= begin) {
                        break
                    }
                    end += endMarker.length
                    val range = source.substring(begin, end)
                    val replacement = replacementFunction(range)
                    source = source.substring(0, begin) + replacement + source.substring(end)
                    index = begin - 1
                }
                sourceFile.writeText(source)
                println("Updated source code directly in $sourceFile")
                true
            } else {
                fail("\$ADT_SOURCE_TREE was set but $sourceFile does not exist")
                false
            }
        }
        return false
    }
}

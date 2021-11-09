/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.ninja

import com.android.build.gradle.internal.cxx.RandomInstanceGenerator
import com.android.build.gradle.internal.cxx.ninja.NinjaBuildToken.DoublePipe
import com.android.build.gradle.internal.cxx.ninja.NinjaBuildToken.EOF
import com.android.build.gradle.internal.cxx.ninja.NinjaBuildToken.EOL
import com.android.build.gradle.internal.cxx.ninja.NinjaBuildToken.Indent
import com.android.build.gradle.internal.cxx.ninja.NinjaBuildToken.Pipe
import com.android.build.gradle.internal.cxx.ninja.NinjaBuildToken.Text
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.StringReader

class StreamNinjaBuildTokensTest {

    private fun check(input : String, expected : String? = null) {
        val sb = StringBuilder()
        StringReader(input).streamNinjaBuildTokens {
            when (it) {
                is EOL -> sb.append("[EOL]")
                is EOF -> sb.append("[EOF]")
                is Indent -> sb.append("[Indent]")
                is Text -> sb.append("[${it.text}]")
                is Pipe -> sb.append("[Pipe]")
                is DoublePipe -> sb.append("[DoublePipe]")
                else -> error("$it")
            }
        }
        if (expected != null) {
            assertThat(sb.toString())
                .named(input)
                .isEqualTo("$expected[EOF]")
        }
    }

    @Test
    fun `build with spaces in file names`() {
        check("build a$ b|c$ d:ru$ le e$ f|g$ h||i$ j", "[build][a$ b][Pipe][c$ d][:][ru$ le][e$ f][Pipe][g$ h][DoublePipe][i$ j][EOL]")
    }

    @Test
    fun dots() {
        check("foo.dots \$bar.dots \${bar.dots}\n", "[foo.dots][\$bar.dots][\${bar.dots}][EOL]")
    }

    @Test
    fun `two lines`() {
        check("line1\nline2", "[line1][EOL][line2][EOL]")
    }

    @Test
    fun `simple build`() {
        check("""
            # Comment
            output.txt : CREATE input.txt
        """.trimIndent(),
        "[output.txt][:][CREATE][input.txt][EOL]")
    }

    @Test
    fun `no space after colon`() {
        check("""
            # Comment
            output.txt :CREATE input.txt
        """.trimIndent(),
            "[output.txt][:][CREATE][input.txt][EOL]")
    }

    @Test
    fun `no space before colon`() {
        check("""
            # Comment
            output.txt: CREATE input.txt
        """.trimIndent(),
            "[output.txt][:][CREATE][input.txt][EOL]")
    }

    @Test
    fun `rule and property`() {
        check("""
            rule my_rule
                command = my_command
        """.trimIndent(),
            "[rule][my_rule][EOL][Indent][command][=][my_command][EOL]")
    }

    @Test
    fun `build with pipe`() {
        check("""
            # Comment
            output.txt:CREATE | input1.txt input2.txt
        """.trimIndent(),
            "[output.txt][:][CREATE][Pipe][input1.txt][input2.txt][EOL]")
    }

    @Test
    fun `empty rule with comment`() {
        check("rule my_rule # comment",
            "[rule][my_rule][EOL]")
    }

    @Test
    fun `build with double pipe`() {
        check("||", "[DoublePipe][EOL]")
        check("|| ", "[DoublePipe][EOL]")
        check("||\n", "[DoublePipe][EOL]")
        check("||\$a", "[DoublePipe][\$a][EOL]")
        check("""
            # Comment
            output.txt:CREATE || input1.txt input2.txt
        """.trimIndent(),
            "[output.txt][:][CREATE][DoublePipe][input1.txt][input2.txt][EOL]")
    }

    @Test
    fun `indented comment after rule`() {
        check("rule cat\n" +
                "  #command = a",
        "[rule][cat][EOL][Indent][EOL]")
    }

    @Test
    fun `build with variable input`() {
        check("build \$x: foo y\n", "[build][\$x][:][foo][y][EOL]")
    }

    @Test
    fun `build with no inputs`() {
        check("build cat : Rule", "[build][cat][:][Rule][EOL]")
    }

    @Test
    fun `empty build`() {
        check("build:", "[build][:][EOL]")
    }

    @Test
    fun `line continuation`() {
        check("rule link\n" +
                "  command = foo bar $\n" +
                "    baz\n" +
                "\n" +
                "build a: link c $\n" +
                " d e f\n",
        "[rule][link][EOL][Indent][command][=][foo bar baz][EOL][build][a][:][link][c][d][e][f][EOL]")
    }

    @Test
    fun `property with colon`() {
        check("""
            build a: b
              COMMAND = C:\x\y\z\cmake.exe
              restat = 1""".trimIndent(),
        "[build][a][:][b][EOL][Indent][COMMAND][=][C:\\x\\y\\z\\cmake.exe][EOL][Indent][restat][=][1][EOL]")
    }

    @Test
    fun `property with pipe`() {
        check("""
            build a: b
              COMMAND = x|b
              restat = 1""".trimIndent(),
            "[build][a][:][b][EOL][Indent][COMMAND][=][x|b][EOL][Indent][restat][=][1][EOL]")
    }

    @Test
    fun `property with double pipe`() {
        check("""
            build a: b
              COMMAND = x||b
              restat = 1""".trimIndent(),
            "[build][a][:][b][EOL][Indent][COMMAND][=][x||b][EOL][Indent][restat][=][1][EOL]")
    }

    @Test
    fun `property with hash (not comment)`() {
        check("""
            build a: b
              COMMAND = x # not a comment
              restat = 1""".trimIndent(),
            "[build][a][:][b][EOL][Indent][COMMAND][=][x # not a comment][EOL][Indent][restat][=][1][EOL]")
    }

    @Test
    fun `comment no comment`() {
        check("# this is a comment\n" +
                "foo = not # a comment\n",
        "[foo][=][not # a comment][EOL]")
    }

    @Test
    fun `variable use in rule assignment`() {
        check("rule e1\n" +
                "  command = e2 \$in > \$out\n\n\n",
            "[rule][e1][EOL][Indent][command][=][e2 \$in > \$out][EOL]")
    }

    /**
     * https://ninja-build.org/manual.html
     * "Whitespace at the beginning of a line after a line continuation is also stripped."
     */
    @Test
    fun `whitespace after line continuation`() {
        check("""
            # Comment
            Non-$
              comment $
                indented
        """.trimIndent(),
            "[Non-comment][indented][EOL]")
    }

    @Test
    fun rules() {
        check("rule cat\n" +
                "  command = cat \$in > \$out\n" +
                "\n" +
                "rule date\n" +
                "  command = date > \$out\n" +
                "\n" +
                "build result: cat in_1.cc in-2.O\n",
            "[rule][cat][EOL][Indent][command][=][cat \$in > \$out][EOL][rule][date][EOL][Indent][command][=][date > \$out][EOL][build][result][:][cat][in_1.cc][in-2.O][EOL]")
    }

    @Test
    fun `fuzz repro with escaped space`() {
        check("abc =\$ lmn def", "[abc][=][$ lmn def][EOL]")
    }

    @Test
    fun `colon inside build file`() {
        check("build a: b C\$:/abc", "[build][a][:][b][C$:/abc][EOL]")
        check("a = ::1::", "[a][=][::1::][EOL]")
    }

    @Test
    fun `fuzz repro with`() {
        check("""
            build e1: e2 a/b.c
            # utility
            build x/y.z: e3
        """.trimIndent(),
        "[build][e1][:][e2][a/b.c][EOL][build][x/y.z][:][e3][EOL]")
    }

    @Test
    fun `single variable`() {
        check("\$a", "[\$a][EOL]")
        check("\${a}", "[\${a}][EOL]")
        check("|\$a", "[Pipe][\$a][EOL]")
        check("||\$a", "[DoublePipe][\$a][EOL]")
        check("  \$a", "[Indent][\$a][EOL]")
        check("  \${a}", "[Indent][\${a}][EOL]")
    }

    @Test
    fun `fuzz cases`() {
        check("subninja -G C_TEST_WAS_RUN1", "[subninja][-G][C_TEST_WAS_RUN1][EOL]")
        check("  #")
        check("|#")
        check("||#")
        check(":")
        check("|")
        check("  :")
        check("  |")
        check("|=")
        check("|:")
        check("'deps --?'-W C_TEST_WAS_RUN = --GC_TEST_WAS_RUN =-helpC_TEST_WAS_RUN =-W " +
                "C_TEST_WAS_RUN= --?-DC_TEST_WAS_RUN =-help prop = x--W C_TEST_WAS_RUN = " +
                "-help C_TEST_WAS_RUN =/-?  C_TEST_WAS_RUN=^& --W C_TEST_WAS_RUN =-D  " +
                "C_TEST_WAS_RUN = --helpC_TEST_WAS_RUN=libnative-lib.so --G C_TEST_WAS_RUN=--?  " +
                "C_TEST_WAS_RUN= --helpC_TEST_WAS_RUN =HELP--H|-B '--G --W\"--help  " +
                "C_TEST_WAS_RUN=_libnative-lib.so-W C_TEST_WAS_RUN= -W C_TEST_WAS_RUN " +
                "=--help C_TEST_WAS_RUN= -GC_TEST_WAS_RUN= --GC_TEST_WAS_RUN = " +
                "-helpC_TEST_WAS_RUN =--D-G  C_TEST_WAS_RUN = --W C_TEST_WAS_RUN= " +
                "-W C_TEST_WAS_RUN = -?-DC_TEST_WAS_RUN = --?0--H C_TEST_WAS_RUN =rules.ninja'")
    }

    @Test
    fun fuzz() {
        RandomInstanceGenerator().strings(10000).forEach { text ->
            try {
                StringReader(text).streamNinjaBuildTokens { }
            } catch (e : Throwable) {
                println("\'$text\'")
                throw e
            }
        }
    }
}

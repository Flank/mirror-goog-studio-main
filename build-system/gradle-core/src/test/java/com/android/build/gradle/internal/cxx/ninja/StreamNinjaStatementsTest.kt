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
import com.android.build.gradle.internal.cxx.logging.LoggingMessage
import com.android.build.gradle.internal.cxx.logging.ThreadLoggingEnvironment
import com.android.build.gradle.internal.cxx.ninja.NinjaStatement.Assignment
import com.android.build.gradle.internal.cxx.ninja.NinjaStatement.BuildDef
import com.android.build.gradle.internal.cxx.ninja.NinjaStatement.Default
import com.android.build.gradle.internal.cxx.ninja.NinjaStatement.Include
import com.android.build.gradle.internal.cxx.ninja.NinjaStatement.PoolDef
import com.android.build.gradle.internal.cxx.ninja.NinjaStatement.RuleDef
import com.android.build.gradle.internal.cxx.ninja.NinjaStatement.SubNinja
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.Reader
import java.io.StringReader

class StreamNinjaStatementsTest {
    @Test
    fun `fuzz failures`() {
        parseNinjaExpectError(
            """
                abc =
                  def
            """.trimIndent())
        parseNinjaExpectError("subninja -G C_TEST_WAS_RUN1")
        parseNinja("--HC_TEST_WAS_RUN =")
        parseNinjaExpectError("build:")
        parseNinja("pool --Dbar^&")
        parseNinjaExpectError("build")
        parseNinja("pool")
        parseNinja("rule")
        parseNinjaExpectError("[")
        parseNinja("|")
        parseNinjaExpectError("||")
        parseNinjaExpectError("build|")
    }

    @Test
    fun empty() {
        parseNinja("")
    }

    @Test
    fun `empty build statement`() {
        parseNinjaExpectError("build")
        parseNinjaExpectError("build:")
    }

    @Test
    fun implicitExplicit() {
        val ninja = parseNinja("build a | b : RULE c | d || e")
        assertThat(writeNinjaToString(ninja)).isEqualTo("build a | b : RULE c | d || e")
    }

    @Test
    fun rules() {
        val ninja = parseNinja(
            "rule cat\n" +
                    "  command = cat \$in > \$out\n" +
                    "\n" +
                    "rule date\n" +
                    "  command = date > \$out\n" +
                    "\n" +
                    "build result: cat in_1.cc in-2.O\n")
        assertThat(writeNinjaToString(ninja))
            .isEqualTo("""
            rule cat
              command = cat ${'$'}in > ${'$'}out
            rule date
              command = date > ${'$'}out
            build result : cat in_1.cc in-2.O""".trimIndent())
        assertThat((ninja.tops[1] as RuleDef).properties["command"])
            .isEqualTo("date > \$out")
    }

    @Test
    fun `two rules`() {
        val ninja = parseNinja("""
                rule cat
                  command = cat
                rule dog
                  command = dog
            """.trimIndent())

        assertThat(writeNinjaToString(ninja))
            .isEqualTo("""
            rule cat
              command = cat
            rule dog
              command = dog""".trimIndent())

    }

    @Test
    fun ruleAttributes() {
        parseNinja("rule cat\n" +
                    "  command = a\n" +
                    "  depfile = a\n" +
                    "  deps = a\n" +
                    "  description = a\n" +
                    "  generator = a\n" +
                    "  restat = a\n" +
                    "  rspfile = a\n" +
                    "  rspfile_content = a\n")
    }

    @Test
    fun indentedComments() {
        val ninja = parseNinja("rule cat\n" +
                    "  command = a\n" +
                    "  depfile = a\n" +
                    "  # Deps comment\n" +
                    "  deps = a\n" +
                    "  description = a\n" +
                    "  generator = a\n" +
                    "  restat = a\n" +
                    "  rspfile = a\n" +
                    "  rspfile_content = a\n")
        assertThat(writeNinjaToString(ninja)).isEqualTo("""
            rule cat
              command = a
              depfile = a
              deps = a
              description = a
              generator = a
              restat = a
              rspfile = a
              rspfile_content = a
        """.trimIndent())
    }

    @Test
    fun buildWithNoInputs() {
        parseNinja("build cat : Rule")
    }

    @Test
    fun indentedCommentsAfterRule() {
        parseNinja("rule cat\n" +
                    "  #command = a")
    }

    @Test
    fun backslash() {
        val ninja = parseNinja("foo = bar\\baz\n" +
                    "foo2 = bar\\ baz\n")
        val assign = ninja.tops[1] as Assignment
        val literal = assign.value
        assertThat(literal).isEqualTo("bar\\ baz")
    }

    @Test
    fun indentedCommentsAfterBuild() {
        parseNinja("build cat: Rule\n" +
                    "  #command = a")
    }

    @Test
    fun commentNoComment() {
        val ninja = parseNinja("# this is a comment\n" +
                    "foo = not # a comment\n")
        val assignment = ninja.tops[0] as Assignment
        val literal = assignment.value
        assertThat(literal).isEqualTo("not # a comment")
    }

    @Test
    fun indentedBlankLine() {
        parseNinja("build cat: Rule\n" +
                    "  \n" +
                    "  command = a")
    }

    @Test
    fun dollars() {
        val ninja = parseNinja("rule foo\n" +
                    "  command = \${out}bar\$\$baz\$\$\$\n" +
                    "blah\n" +
                    "x = \$\$dollar\n" +
                    "build \$x: foo y\n")
        val rule = ninja.tops[0] as RuleDef
        val literal = rule.properties.values.first()
        assertThat(literal).isEqualTo("\${out}bar$\$baz$\$blah")
    }

    @Test
    fun `build with variable input`() {
        parseNinja("build \$x: foo y\n")
    }

    @Test
    fun `build with escaped spaces in file names`() {
        val ninja = parseNinja("build a$ b|c$ d:ru$ le e$ f|g$ h||i$ j")
        assertThat(ninja.tops).hasSize(1)
        val buildDef = ninja.tops[0] as BuildDef
        assertThat(buildDef.rule).isEqualTo("ru$ le")
        assertThat(buildDef.explicitOutputs).containsExactly("a$ b")
        assertThat(buildDef.implicitOutputs).containsExactly("c$ d")
        assertThat(buildDef.explicitInputs).containsExactly("e$ f")
        assertThat(buildDef.implicitInputs).containsExactly("g$ h")
        assertThat(buildDef.orderOnlyInputs).containsExactly("i$ j")
    }

    @Test
    fun `build with escaped dollars in file names`() {
        val ninja = parseNinja("build a\$\$b|c\$\$d:ru\$\$le e\$\$f|g\$\$h||i\$\$j")
        assertThat(ninja.tops).hasSize(1)
        val buildDef = ninja.tops[0] as BuildDef
        assertThat(buildDef.rule).isEqualTo("ru$\$le")
        assertThat(buildDef.explicitOutputs).containsExactly("a$\$b")
        assertThat(buildDef.implicitOutputs).containsExactly("c$\$d")
        assertThat(buildDef.explicitInputs).containsExactly("e$\$f")
        assertThat(buildDef.implicitInputs).containsExactly("g$\$h")
        assertThat(buildDef.orderOnlyInputs).containsExactly("i$\$j")
    }

    @Test
    fun `build with escaped colons in file names`() {
        val ninja = parseNinja("build a\$:b|c\$:d:ru\$:le e\$:f|g\$:h||i\$:j")
        assertThat(ninja.tops).hasSize(1)
        val buildDef = ninja.tops[0] as BuildDef
        assertThat(buildDef.rule).isEqualTo("ru$:le")
        assertThat(buildDef.explicitOutputs).containsExactly("a$:b")
        assertThat(buildDef.implicitOutputs).containsExactly("c$:d")
        assertThat(buildDef.explicitInputs).containsExactly("e$:f")
        assertThat(buildDef.implicitInputs).containsExactly("g$:h")
        assertThat(buildDef.orderOnlyInputs).containsExactly("i$:j")
    }

    @Test
    fun continuation() {
        parseNinja("rule link\n" +
                    "  command = foo bar $\n" +
                    "    baz\n" +
                    "\n" +
                    "build a: link c $\n" +
                    " d e f\n")
    }

    @Test
    fun ignoreTrailingComment() {
        parseNinja("rule cat # My comment")
    }

    @Test
    fun assignment() {
        val ninja = parseNinja("a=b")
        assertThat(ninja).isEqualTo(NinjaFileDef(
            listOf(Assignment("a", "b"))))
    }

    @Test
    fun twoAssign() {
        val ninja = parseNinja(
            """
                a=b
                x=y
            """.trimIndent())
        assertThat(ninja.tops[1]).isEqualTo(
            Assignment("x", "y"))
    }

    @Test
    fun include() {
        val ninja = parseNinja("include xyz")
        assertThat(writeNinjaToString(ninja)).isEqualTo("include xyz")
    }

    @Test
    fun subninja() {
        val ninja = parseNinja("subninja xyz")
        assertThat(writeNinjaToString(ninja)).isEqualTo("subninja xyz")
    }

    @Test
    fun default() {
        val ninja = parseNinja("default abc xyz")
        assertThat(ninja.tops[0].toString()).isEqualTo("Default(targets=[abc, xyz])")
    }

    @Test
    fun build() {
        val ninja = parseNinja("build output.txt: RULE input.txt")
        assertThat(writeNinjaToString(ninja))
            .isEqualTo("build output.txt : RULE input.txt")
    }

    @Test
    fun buildProp() {
        val ninja = parseNinja(
            """
                build output.txt: RULE input.txt
                  property = value""".trimIndent())
        assertThat(writeNinjaToString(ninja))
            .isEqualTo("""
                    build output.txt : RULE input.txt
                      property = value""".trimIndent())
    }

    @Test
    fun buildTwoProperties() {
        val ninja = parseNinja(
            """
                build output.txt: RULE input.txt
                  property = value
                  property2 = value2""".trimIndent())
        assertThat(writeNinjaToString(ninja))
            .isEqualTo("""
            build output.txt : RULE input.txt
              property = value
              property2 = value2""".trimIndent())
    }

    @Test
    fun continuedPastEol() {
        val ninja = parseNinja(
            """
                    build ${'$'}
                      a: ${'$'}
                        RULE ${'$'}
                          b ${'$'}

                    build ${'$'}
                      A: ${'$'}
                        RULE ${'$'}
                          B ${'$'}
                          """.trimIndent())
        assertThat(writeNinjaToString(ninja))
            .isEqualTo("""
            build a : RULE b
            build A : RULE B""".trimIndent())
    }

    @Test
    fun buildTwoInputs() {
        val ninja = parseNinja(
            """
                build output.txt: RULE input1.txt input2.txt
                  property = value""".trimIndent())
        assertThat(writeNinjaToString(ninja))
            .isEqualTo("""
                    build output.txt : RULE input1.txt input2.txt
                      property = value
                """.trimIndent())
    }

    @Test
    fun buildTwoOutputs() {
        val ninja = parseNinja("build output1.txt output2.txt: RULE input1.txt")
        assertThat(writeNinjaToString(ninja))
            .isEqualTo("build output1.txt output2.txt : RULE input1.txt")
    }

    @Test
    fun colonInBuildOutput() {
        val ninja = parseNinja("build build.ninja: RERUN_CMAKE C\$:/abc")
        assertThat(writeNinjaToString(ninja))
            .isEqualTo("build build.ninja : RERUN_CMAKE C\$:/abc")
    }

    @Test
    fun propertyWithSpaces() {
        val ninja = parseNinja(
            """
                build CMakeFiles/edit_cache.util: CUSTOM_COMMAND
                  COMMAND = cmd.exe /C "cd /D C:\a\b\c && C:\x\y\z\cmake.exe -E echo "No interactive CMake dialog available.""
                  DESC = No interactive CMake dialog available...
                  restat = 1""".trimIndent())
        assertThat(writeNinjaToString(ninja))
            .isEqualTo("""
                    build CMakeFiles/edit_cache.util : CUSTOM_COMMAND
                      COMMAND = cmd.exe /C "cd /D C:\a\b\c && C:\x\y\z\cmake.exe -E echo "No interactive CMake dialog available.""
                      DESC = No interactive CMake dialog available...
                      restat = 1
                """.trimIndent())
    }

    @Test
    fun emptyRule() {
        val ninja = parseNinja("rule my_rule")
        assertThat(writeNinjaToString(ninja))
            .isEqualTo("rule my_rule")
    }

    @Test
    fun fuzz() {
        RandomInstanceGenerator().strings(10000).forEach { text ->
            try {
                parseNinja(StringReader(text))
            } catch (e : Throwable) {
                println("\'$text\'")
                throw e
            }
        }
    }

    @Test
    fun sampleRulesNinja() {
        parseNinja(
            """
            # CMAKE generated file: DO NOT EDIT!
            # Generated by "Ninja" Generator, CMake Version 3.10

            # This file contains all the rules used to get the explicitOutputs files
            # built from the input files.
            # It is included in the main 'build.ninja'.

            # =============================================================================
            # Project: Project
            # Configuration: Release
            # =============================================================================
            # =============================================================================

            #############################################
            # Rule for running custom commands.

            rule CUSTOM_COMMAND
              command = ${'$'}COMMAND
              description = ${'$'}DESC


            #############################################
            # Rule for compiling CXX files.

            rule CXX_COMPILER__native-lib
              depfile = ${'$'}DEP_FILE
              deps = gcc
              command = C:\Users\Jomo\AppData\Local\Android\Sdk\ndk-bundle\toolchains\llvm\prebuilt\windows-x86_64\bin\clang++.exe --target=i686-none-linux-android16 --gcc-toolchain=C:/Users/Jomo/AppData/Local/Android/Sdk/ndk-bundle/toolchains/llvm/prebuilt/windows-x86_64  ${'$'}DEFINES ${'$'}INCLUDES ${'$'}FLAGS -MD -MT ${'$'}out -MF ${'$'}DEP_FILE -o ${'$'}out -c ${'$'}in
              description = Building CXX object ${'$'}out


            #############################################
            # Rule for linking CXX shared library.

            rule CXX_SHARED_LIBRARY_LINKER__native-lib
              command = cmd.exe /C "${'$'}PRE_LINK && C:\Users\Jomo\AppData\Local\Android\Sdk\ndk-bundle\toolchains\llvm\prebuilt\windows-x86_64\bin\clang++.exe --target=i686-none-linux-android16 --gcc-toolchain=C:/Users/Jomo/AppData/Local/Android/Sdk/ndk-bundle/toolchains/llvm/prebuilt/windows-x86_64 -fPIC ${'$'}LANGUAGE_COMPILE_FLAGS ${'$'}ARCH_FLAGS ${'$'}LINK_FLAGS -shared ${'$'}SONAME_FLAG${'$'}SONAME -o ${'$'}TARGET_FILE ${'$'}in ${'$'}LINK_PATH ${'$'}LINK_LIBRARIES && ${'$'}POST_BUILD"
              description = Linking CXX shared library ${'$'}TARGET_FILE
              restat = ${'$'}RESTAT


            #############################################
            # Rule for re-running cmake.

            rule RERUN_CMAKE
              command = C:\Users\Jomo\AppData\Local\Android\Sdk\cmake\3.10.2.4988404\bin\cmake.exe -HC:\Users\Jomo\AndroidStudioProjects\MyApplication\app\.externalNativeBuild\cxx\release\x86 -BC:\Users\Jomo\AndroidStudioProjects\MyApplication\app\.externalNativeBuild\cmake\release\x86
              description = Re-running CMake...
              generator = 1


            #############################################
            # Rule for cleaning all built files.

            rule CLEAN
              command = C:\Users\Jomo\AppData\Local\Android\Sdk\cmake\3.10.2.4988404\bin\ninja.exe -t clean
              description = Cleaning all built files...


            #############################################
            # Rule for printing all primary targets available.

            rule HELP
              command = C:\Users\Jomo\AppData\Local\Android\Sdk\cmake\3.10.2.4988404\bin\ninja.exe -t targets
              description = All primary targets available:
            """.trimIndent())
    }

    @Test
    fun `comment between two build statements`() {
        parseNinja(
            """
                build e1: e2 a/b.c
                # utility
                build x/y.z: e3
            """.trimIndent())
    }

    @Test
    fun sampleBuildNinja() {
        parseNinja(
            """
                # CMAKE generated file: DO NOT EDIT!
                # Generated by "Ninja" Generator, CMake Version 3.10

                # This file contains all the build statements describing the
                # compilation DAG.

                # =============================================================================
                # Write statements declared in CMakeLists.txt:
                #
                # Which is the root file.
                # =============================================================================

                # =============================================================================
                # Project: Project
                # Configuration: Release
                # =============================================================================

                #############################################
                # Minimal version of Ninja required by this file

                ninja_required_version = 1.5

                # =============================================================================
                # Include auxiliary files.


                #############################################
                # Include rules file.

                include rules.ninja


                #############################################
                # utility command for edit_cache

                build CMakeFiles/edit_cache.util: CUSTOM_COMMAND
                  COMMAND = cmd.exe /C "cd /D C:\Users\Jomo\AndroidStudioProjects\MyApplication\app\.externalNativeBuild\cmake\release\arm64-v8a && C:\Users\Jomo\AppData\Local\Android\Sdk\cmake\3.10.2.4988404\bin\cmake.exe -E echo "No interactive CMake dialog available.""
                  DESC = No interactive CMake dialog available...
                  restat = 1
                build edit_cache: phony CMakeFiles/edit_cache.util

                #############################################
                # utility command for rebuild_cache

                build CMakeFiles/rebuild_cache.util: CUSTOM_COMMAND
                  COMMAND = cmd.exe /C "cd /D C:\Users\Jomo\AndroidStudioProjects\MyApplication\app\.externalNativeBuild\cmake\release\arm64-v8a && C:\Users\Jomo\AppData\Local\Android\Sdk\cmake\3.10.2.4988404\bin\cmake.exe -HC:\Users\Jomo\AndroidStudioProjects\MyApplication\app\.externalNativeBuild\cxx\release\arm64-v8a -BC:\Users\Jomo\AndroidStudioProjects\MyApplication\app\.externalNativeBuild\cmake\release\arm64-v8a"
                  DESC = Running CMake to regenerate build system...
                  pool = console
                  restat = 1
                build rebuild_cache: phony CMakeFiles/rebuild_cache.util
                # =============================================================================
                # Write statements declared in CMakeLists.txt:
                # C:/Users/Jomo/AndroidStudioProjects/MyApplication/app/.externalNativeBuild/cxx/release/arm64-v8a/CMakeLists.txt
                # =============================================================================

                # =============================================================================
                # Object build statements for SHARED_LIBRARY target native-lib


                #############################################
                # Order-only phony target for native-lib

                build cmake_object_order_depends_target_native-lib: phony
                build C${'$'}:/Users/Jomo/AndroidStudioProjects/MyApplication/app/.externalNativeBuild/cxx/release/arm64-v8a/CMakeFiles/native-lib.dir/native-lib.cpp.o: CXX_COMPILER__native-lib C${'$'}:/Users/Jomo/AndroidStudioProjects/MyApplication/app/src/main/cpp/native-lib.cpp || cmake_object_order_depends_target_native-lib
                  DEFINES = -Dnative_lib_EXPORTS
                  DEP_FILE = C:\Users\Jomo\AndroidStudioProjects\MyApplication\app\.externalNativeBuild\cxx\release\arm64-v8a\CMakeFiles\native-lib.dir\native-lib.cpp.o.d
                  FLAGS = --sysroot C:/Users/Jomo/AppData/Local/Android/Sdk/ndk-bundle/toolchains/llvm/prebuilt/windows-x86_64/sysroot -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -Wa,--noexecstack -Wformat -Werror=format-security -stdlib=libc++ -std=c++11  -O2 -DNDEBUG  -fPIC
                  OBJECT_DIR = C:\Users\Jomo\AndroidStudioProjects\MyApplication\app\.externalNativeBuild\cxx\release\arm64-v8a\CMakeFiles\native-lib.dir
                  OBJECT_FILE_DIR = C:\Users\Jomo\AndroidStudioProjects\MyApplication\app\.externalNativeBuild\cxx\release\arm64-v8a\CMakeFiles\native-lib.dir

                # =============================================================================
                # Link build statements for SHARED_LIBRARY target native-lib


                #############################################
                # Link the shared library C:\Users\Jomo\AndroidStudioProjects\MyApplication\app\build\intermediates\cmake\release\obj\arm64-v8a\libnative-lib.so

                build C${'$'}:/Users/Jomo/AndroidStudioProjects/MyApplication/app/build/intermediates/cmake/release/obj/arm64-v8a/libnative-lib.so: CXX_SHARED_LIBRARY_LINKER__native-lib C${'$'}:/Users/Jomo/AndroidStudioProjects/MyApplication/app/.externalNativeBuild/cxx/release/arm64-v8a/CMakeFiles/native-lib.dir/native-lib.cpp.o | C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/ndk-bundle/toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/lib/aarch64-linux-android/21/liblog.so
                  LANGUAGE_COMPILE_FLAGS = --sysroot C:/Users/Jomo/AppData/Local/Android/Sdk/ndk-bundle/toolchains/llvm/prebuilt/windows-x86_64/sysroot -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -Wa,--noexecstack -Wformat -Werror=format-security -stdlib=libc++ -std=c++11  -O2 -DNDEBUG
                  LINK_FLAGS = -Wl,--exclude-libs,libgcc.a -Wl,--exclude-libs,libatomic.a -static-libstdc++ -Wl,--build-id -Wl,--warn-shared-textrel -Wl,--fatal-warnings -Wl,--no-undefined -Qunused-arguments -Wl,-z,noexecstack -Wl,-z,relro -Wl,-z,now
                  LINK_LIBRARIES = -llog -latomic -lm
                  LINK_PATH = -LC:/Users/Jomo/AppData/Local/Android/Sdk/ndk-bundle/toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/lib/aarch64-linux-android/21
                  OBJECT_DIR = C:\Users\Jomo\AndroidStudioProjects\MyApplication\app\.externalNativeBuild\cxx\release\arm64-v8a\CMakeFiles\native-lib.dir
                  POST_BUILD = cd .
                  PRE_LINK = cd .
                  SONAME = libnative-lib.so
                  SONAME_FLAG = -Wl,-soname,
                  TARGET_FILE = C:\Users\Jomo\AndroidStudioProjects\MyApplication\app\build\intermediates\cmake\release\obj\arm64-v8a\libnative-lib.so
                  TARGET_PDB = native-lib.so.dbg

                #############################################
                # utility command for edit_cache

                build C${'$'}:/Users/Jomo/AndroidStudioProjects/MyApplication/app/.externalNativeBuild/cxx/release/arm64-v8a/CMakeFiles/edit_cache.util: CUSTOM_COMMAND
                  COMMAND = cmd.exe /C "cd /D C:\Users\Jomo\AndroidStudioProjects\MyApplication\app\.externalNativeBuild\cxx\release\arm64-v8a && C:\Users\Jomo\AppData\Local\Android\Sdk\cmake\3.10.2.4988404\bin\cmake.exe -E echo "No interactive CMake dialog available.""
                  DESC = No interactive CMake dialog available...
                  restat = 1
                build C${'$'}:/Users/Jomo/AndroidStudioProjects/MyApplication/app/.externalNativeBuild/cxx/release/arm64-v8a/edit_cache: phony C${'$'}:/Users/Jomo/AndroidStudioProjects/MyApplication/app/.externalNativeBuild/cxx/release/arm64-v8a/CMakeFiles/edit_cache.util

                #############################################
                # utility command for rebuild_cache

                build C${'$'}:/Users/Jomo/AndroidStudioProjects/MyApplication/app/.externalNativeBuild/cxx/release/arm64-v8a/CMakeFiles/rebuild_cache.util: CUSTOM_COMMAND
                  COMMAND = cmd.exe /C "cd /D C:\Users\Jomo\AndroidStudioProjects\MyApplication\app\.externalNativeBuild\cxx\release\arm64-v8a && C:\Users\Jomo\AppData\Local\Android\Sdk\cmake\3.10.2.4988404\bin\cmake.exe -HC:\Users\Jomo\AndroidStudioProjects\MyApplication\app\.externalNativeBuild\cxx\release\arm64-v8a -BC:\Users\Jomo\AndroidStudioProjects\MyApplication\app\.externalNativeBuild\cmake\release\arm64-v8a"
                  DESC = Running CMake to regenerate build system...
                  pool = console
                  restat = 1
                build C${'$'}:/Users/Jomo/AndroidStudioProjects/MyApplication/app/.externalNativeBuild/cxx/release/arm64-v8a/rebuild_cache: phony C${'$'}:/Users/Jomo/AndroidStudioProjects/MyApplication/app/.externalNativeBuild/cxx/release/arm64-v8a/CMakeFiles/rebuild_cache.util
                # =============================================================================
                # Target aliases.

                build libnative-lib.so: phony C${'$'}:/Users/Jomo/AndroidStudioProjects/MyApplication/app/build/intermediates/cmake/release/obj/arm64-v8a/libnative-lib.so
                build native-lib: phony C${'$'}:/Users/Jomo/AndroidStudioProjects/MyApplication/app/build/intermediates/cmake/release/obj/arm64-v8a/libnative-lib.so
                # =============================================================================
                # Folder targets.

                # =============================================================================
                # =============================================================================
                # =============================================================================
                # Built-in targets


                #############################################
                # The main all target.

                build all: phony C${'$'}:/Users/Jomo/AndroidStudioProjects/MyApplication/app/build/intermediates/cmake/release/obj/arm64-v8a/libnative-lib.so

                #############################################
                # Make the all target the default.

                default all

                #############################################
                # Re-run CMake if any of its explicitInputs changed.

                build build.ninja: RERUN_CMAKE | C${'$'}:/Users/Jomo/AndroidStudioProjects/MyApplication/app/.externalNativeBuild/cxx/release/arm64-v8a/CMakeLists.txt C${'$'}:/Users/Jomo/AndroidStudioProjects/MyApplication/app/src/main/cpp/CMakeLists.txt C${'$'}:/Users/Jomo/AndroidStudioProjects/MyApplication/app/src/main/cpp/muh_chain.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeCCompiler.cmake.in C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeCCompilerABI.c C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeCInformation.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeCXXCompiler.cmake.in C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeCXXCompilerABI.cpp C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeCXXInformation.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeCommonLanguageInclude.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeDetermineCCompiler.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeDetermineCXXCompiler.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeDetermineCompileFeatures.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeDetermineCompiler.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeDetermineCompilerABI.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeDetermineSystem.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeFindBinUtils.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeGenericSystem.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeLanguageInformation.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeParseImplicitLinkInfo.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeSystem.cmake.in C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeSystemSpecificInformation.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeSystemSpecificInitialize.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeTestCCompiler.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeTestCXXCompiler.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeTestCompilerCommon.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Compiler/CMakeCommonCompilerMacros.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Compiler/Clang-C-FeatureTests.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Compiler/Clang-C.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Compiler/Clang-CXX-FeatureTests.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Compiler/Clang-CXX-TestableFeatures.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Compiler/Clang-CXX.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Compiler/Clang-FindBinUtils.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Compiler/Clang.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Compiler/GNU.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Internal/FeatureTesting.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Platform/Android-Clang-C.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Platform/Android-Clang-CXX.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Platform/Android-Clang.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Platform/Android-Determine-C.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Platform/Android-Determine-CXX.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Platform/Android-Determine.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Platform/Android-Initialize.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Platform/Android.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Platform/Android/Determine-Compiler.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Platform/Linux.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Platform/UnixPaths.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/ndk-bundle/build/cmake/android.toolchain.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/ndk-bundle/build/cmake/platforms.cmake CMakeCache.txt CMakeFiles/3.10.2/CMakeCCompiler.cmake CMakeFiles/3.10.2/CMakeCXXCompiler.cmake CMakeFiles/3.10.2/CMakeSystem.cmake CMakeFiles/feature_tests.c CMakeFiles/feature_tests.cxx
                  pool = console

                #############################################
                # A missing CMake input file is not an error.

                build C${'$'}:/Users/Jomo/AndroidStudioProjects/MyApplication/app/.externalNativeBuild/cxx/release/arm64-v8a/CMakeLists.txt C${'$'}:/Users/Jomo/AndroidStudioProjects/MyApplication/app/src/main/cpp/CMakeLists.txt C${'$'}:/Users/Jomo/AndroidStudioProjects/MyApplication/app/src/main/cpp/muh_chain.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeCCompiler.cmake.in C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeCCompilerABI.c C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeCInformation.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeCXXCompiler.cmake.in C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeCXXCompilerABI.cpp C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeCXXInformation.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeCommonLanguageInclude.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeDetermineCCompiler.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeDetermineCXXCompiler.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeDetermineCompileFeatures.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeDetermineCompiler.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeDetermineCompilerABI.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeDetermineSystem.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeFindBinUtils.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeGenericSystem.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeLanguageInformation.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeParseImplicitLinkInfo.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeSystem.cmake.in C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeSystemSpecificInformation.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeSystemSpecificInitialize.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeTestCCompiler.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeTestCXXCompiler.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/CMakeTestCompilerCommon.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Compiler/CMakeCommonCompilerMacros.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Compiler/Clang-C-FeatureTests.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Compiler/Clang-C.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Compiler/Clang-CXX-FeatureTests.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Compiler/Clang-CXX-TestableFeatures.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Compiler/Clang-CXX.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Compiler/Clang-FindBinUtils.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Compiler/Clang.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Compiler/GNU.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Internal/FeatureTesting.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Platform/Android-Clang-C.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Platform/Android-Clang-CXX.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Platform/Android-Clang.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Platform/Android-Determine-C.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Platform/Android-Determine-CXX.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Platform/Android-Determine.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Platform/Android-Initialize.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Platform/Android.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Platform/Android/Determine-Compiler.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Platform/Linux.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/cmake/3.10.2.4988404/share/cmake-3.10/Modules/Platform/UnixPaths.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/ndk-bundle/build/cmake/android.toolchain.cmake C${'$'}:/Users/Jomo/AppData/Local/Android/Sdk/ndk-bundle/build/cmake/platforms.cmake CMakeCache.txt CMakeFiles/3.10.2/CMakeCCompiler.cmake CMakeFiles/3.10.2/CMakeCXXCompiler.cmake CMakeFiles/3.10.2/CMakeSystem.cmake CMakeFiles/feature_tests.c CMakeFiles/feature_tests.cxx: phony

                #############################################
                # Clean all the built files.

                build clean: CLEAN

                #############################################
                # Print all primary targets available.

                build help: HELP

            """.trimIndent())
    }

    @Test
    fun continuedLineTest() {
        val ninja = parseNinja(
            """
                    build ${'$'}
                      a: ${'$'}
                        RULE ${'$'}
                          b ${'$'}

                    build ${'$'}
                      A: ${'$'}
                        RULE ${'$'}
                          B ${'$'}
                          """.trimIndent())
       assertThat(writeNinjaToString(ninja))
           .isEqualTo("""
               build a : RULE b
               build A : RULE B
           """.trimIndent())
    }


    private data class NinjaFileDef(val tops: List<NinjaStatement>)

    private fun parseNinja(text: String) : NinjaFileDef {
        ThrowOnErrorLoggingEnvironment().use {
            return parseNinja(StringReader(text))
        }
    }

    private fun parseNinjaExpectError(text: String) : NinjaFileDef {
        ExpectErrorLoggingEnvironment().use {
            return parseNinja(StringReader(text))
        }
    }

    private fun parseNinja(reader: Reader) : NinjaFileDef {
        val expressions = mutableListOf<NinjaStatement>()
        reader.streamNinjaStatements { expression ->
            expressions += expression
        }
        return NinjaFileDef(expressions)
    }

    class ThrowOnErrorLoggingEnvironment() : ThreadLoggingEnvironment() {
        override fun log(message: LoggingMessage) {
            if (message.level == LoggingMessage.LoggingLevel.ERROR) error(message.message)
        }
    }

    class ExpectErrorLoggingEnvironment : ThreadLoggingEnvironment() {
        private var errors = 0
        override fun log(message: LoggingMessage) {
            if (message.level == LoggingMessage.LoggingLevel.ERROR) ++errors
        }

        override fun close() {
            if (errors == 0) error("Expected at least one error")
        }
    }

    private fun writeNinjaToString(ninja : NinjaFileDef) : String {
        val sb = StringBuilder()
        var indent = ""

        fun write(node : NinjaStatement) {
            when(node) {
                is RuleDef -> with(node) {
                    sb.append("rule ")
                    sb.append(name)
                    sb.append("\n")
                    indent = "  "
                    properties.onEach { (name, value) ->
                        sb.append(indent)
                        sb.append(name)
                        sb.append(" = ")
                        sb.append(value)
                        sb.append("\n")
                    }
                    indent = ""
                }
                is PoolDef -> with(node) {
                    sb.append("pool ")
                    sb.append(name)
                    sb.append("\n")
                    indent = "  "
                    properties.onEach { (name, value) ->
                        sb.append(indent)
                        sb.append(name)
                        sb.append(" = ")
                        sb.append(value)
                        sb.append("\n")
                    }
                    indent = ""
                }
                is BuildDef -> with(node) {
                    sb.append("build ")
                    explicitOutputs.onEach {
                        sb.append(it)
                        sb.append(" ")
                    }
                    if (implicitOutputs.isNotEmpty()) {
                        sb.append("| ")
                        implicitOutputs.onEach {
                            sb.append(it)
                            sb.append(" ")
                        }
                    }
                    sb.append(": ")
                    sb.append(rule)
                    sb.append(" ")
                    explicitInputs.onEach {
                        sb.append(it)
                        sb.append(" ")
                    }
                    if (implicitInputs.isNotEmpty()) {
                        sb.append("| ")
                        implicitInputs.onEach {
                            sb.append(it)
                            sb.append(" ")
                        }
                    }
                    if (orderOnlyInputs.isNotEmpty()) {
                        sb.append("|| ")
                        orderOnlyInputs.onEach {
                            sb.append(it)
                            sb.append(" ")
                        }
                    }
                    sb.append("\n")
                    indent = "  "
                    properties.onEach { (name, value) ->
                        sb.append(indent)
                        sb.append(name)
                        sb.append(" = ")
                        sb.append(value)
                        sb.append("\n")
                    }
                    indent = ""
                }
                is SubNinja -> with(node) {
                    sb.append("subninja ")
                    sb.append(file)
                    sb.append("\n")
                }
                is Include -> with(node) {
                    sb.append("include ")
                    sb.append(file)
                    sb.append("\n")
                }
                is Default -> with(node) {
                    sb.append("default ")
                    targets.onEach {
                        sb.append(it)
                        sb.append(" ")
                    }
                    sb.append("\n")
                }
                else -> throw RuntimeException(node.toString())
            }

        }
        indent = ""
        ninja.tops.onEach { write(it) }
        val result : String = sb.toString().replace(" \n", "\n")
        return result.trim('\n')
    }
}

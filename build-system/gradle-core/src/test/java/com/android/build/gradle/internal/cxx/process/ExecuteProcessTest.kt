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

package com.android.build.gradle.internal.cxx.process

import com.android.SdkConstants.CURRENT_PLATFORM
import com.android.SdkConstants.PLATFORM_WINDOWS
import com.android.build.gradle.internal.cxx.os.bat
import org.gradle.api.Action
import org.gradle.internal.file.PathToFileResolver
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec
import org.gradle.process.internal.DefaultExecSpec
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.Serializable
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.lang.System.err
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit.SECONDS

class ExecuteProcessTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val workingDir by lazy { tempFolder.newFolder().resolve("scripts").also { it.mkdir() } }
    private val command by lazy { workingDir.resolve("command$bat") }
    private val stdout by lazy { workingDir.resolve("stdout.txt") }
    private val stderr by lazy { workingDir.resolve("stderr.txt") }

    /**
     * This test creates two scripts:
     * - 'my-script' to execute on Linux\Mac
     * - 'my-script.bat' to execute on Windows
     * It is meant to prove that these two scripts can coexist and the correct one is executed on
     * the current platform.
     */
    @Test
    fun `dual script and script dot bat works`() {
        val script = workingDir.resolve("my-script")

        createCallbackShellScripts(script) { args ->
            err.println("stderr with args: ${args.joinToString(" ") {"'$it'"}}")
        }

        createCommand(script)
            .copy(useScript = true)
            .addArgs("arg1", "arg2", "arg3")
            .execute(ops)

        assertStderr("stderr with args: 'arg1' 'arg2' 'arg3'")
    }

    @Test
    fun `check problematic strings can round trip`() {
        checkRoundtrip("=")
        checkRoundtrip(" ")
        checkRoundtrip(",")
        checkRoundtrip("?")
        checkRoundtrip("*")
        checkRoundtrip("\"")
        checkRoundtrip("\\")
        checkRoundtrip("\\\\")
        checkRoundtrip(";")
        checkRoundtrip("{")
        checkRoundtrip("}")
        checkRoundtrip("<")
        checkRoundtrip(">")
        checkRoundtrip("%")
        checkRoundtrip("$")
        checkRoundtrip("~")
        checkRoundtrip("|")
        checkRoundtrip("/")
        checkRoundtrip("&")
        checkRoundtrip("!")
        checkRoundtrip("^")
        checkRoundtrip("[")
        checkRoundtrip("]")
        checkRoundtrip("'")
        checkRoundtrip("\u0008")
        checkRoundtrip(
            "\u0000",
            posix = "argument had embedded 0x0000",
            windows = "argument had embedded 0x0000")
        checkRoundtrip(
            "\r",
            windows = "argument had embedded line-feed (\\r)")
        checkRoundtrip(
            "\n",
            windows = "argument had embedded carriage-return (\\n)")
        checkRoundtrip(
            "`",
            windows = "argument had embedded tick-mark (`)")
    }

    private fun checkRoundtrip(value : String, posix : String? = null, windows : String? = null) {
        println("Checking via script: [$value]")
        val script = workingDir.resolve("my-script")
        val expectedException = if (CURRENT_PLATFORM == PLATFORM_WINDOWS) windows else posix

        createCallbackShellScripts(script) { args ->
            err.println("stderr with args: ${args.joinToString(" ") {"'$it'"}}")
        }

        try {
            createCommand(script).copy(useScript = true)
                .addArgs(value, "$value$value", "\"$value\"")
                .execute(ops)
        } catch (e : Exception) {
            if (e.message == expectedException) return
            throw(e)
        }

        if (expectedException != null) error("Expected exception: $expectedException")
        assertStderr("stderr with args: '$value' '$value$value' '\"$value\"'")
    }

    private fun assertStderr(expectedStderr : String) {
        val actualStderr = stderr.readText().trim('\r', '\n')
        if (expectedStderr != actualStderr) {
            println("expected: $expectedStderr")
            println("  actual: $actualStderr")
            println(" working: $workingDir")
            error("")
        }
    }

    /**
     * Create a [ExecuteProcessCommand] initialized with test defaults.
     */
    private fun createCommand(script : File) = createExecuteProcessCommand(script).copy(
        commandFile = command,
        stdout = stdout,
        stderr = stderr)

    /**
     * Implementation of [ExecOperations]
     */
    val ops = object : ExecOperations {
        override fun exec(setSpec: Action<in ExecSpec>): ExecResult {
            val spec = DefaultExecSpec(TestPathToFileResolver(workingDir))
            setSpec.execute(spec)
            val proc = ProcessBuilder(spec.commandLine)
                .directory(workingDir)
                .redirectOutput(stdout)
                .redirectError(stderr)
                .start()

            proc.waitFor(6, SECONDS)
            return object : ExecResult {
                override fun getExitValue() = proc.exitValue()
                override fun assertNormalExitValue() = this
                override fun rethrowFailure() = this
            }
        }
        override fun javaexec(p0: Action<in JavaExecSpec>?) = error("notimpl")
    }

    /**
     * Write a pair of shell scripts to call back into the main(...) function on T.
     * - The Linux and Mac script will be written to [posixScript]
     * - The Windows script will be written to [posixScript].bat
     * When the script is invoked by the shell then [callback] will be called with the command-line
     * arguments.
     *
     * Usage,
     * ```
     *  createCallbackShellScripts(configureScript) { args ->
     *      System.err.println(args.joinToString(" "))
     *  }
     * ```
     */
    private fun createCallbackShellScripts(posixScript: File, callback : (args:Array<String>) -> Unit) {
        val context = callback as Serializable
        val contextFile = File.createTempFile("write-main-callback-shell-script", "bin")
        ObjectOutputStream(FileOutputStream(contextFile)).use { objects ->
            objects.writeObject(context)
        }
        val runtime = ManagementFactory.getRuntimeMXBean()
        val sb = StringBuilder()
        val java = File(runtime.systemProperties["java.home"]!!).resolve("bin/java")
        val classPath = runtime.systemProperties["java.class.path"]!!
        val mainClass = ShellScriptCallback::class.java.name
        val windowsScript = File("${posixScript.path}.bat")
        sb.append("$java --class-path ${classPath.replace("\\\\", "/")} ")
        sb.append("$mainClass $contextFile ")
        windowsScript.writeText("@echo off\r\n")
        windowsScript.appendText(sb.toString())
        windowsScript.appendText("%1 %2 %3")
        posixScript.writeText(sb.toString())
        posixScript.appendText("\"$1\" \"$2\" \"$3\"")
        posixScript.setExecutable(true)
    }

    private class TestPathToFileResolver(val working : File) : PathToFileResolver {
        override fun resolve(file: Any) = working.resolve("$file")
        override fun newResolver(working: File) = TestPathToFileResolver(working)
        override fun canResolveRelativePath() = true
    }
}

@Suppress("UNCHECKED_CAST")
class ShellScriptCallback {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ObjectInputStream(FileInputStream(File(args[0]))).use { objects ->
                val callback = objects.readObject() as (args:Array<String>) -> Unit
                callback(args.drop(1).toTypedArray())
            }
        }
    }
}

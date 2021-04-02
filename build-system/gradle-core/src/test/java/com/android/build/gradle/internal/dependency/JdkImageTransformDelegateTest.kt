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

package com.android.build.gradle.internal.dependency

import com.android.SdkConstants
import com.android.ide.common.process.CachedProcessOutputHandler
import com.android.ide.common.process.DefaultProcessExecutor
import com.android.ide.common.process.LoggedProcessOutputHandler
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.testutils.TestResources
import com.android.testutils.TestUtils
import com.android.testutils.truth.PathSubject.assertThat
import com.android.testutils.truth.ZipFileSubject
import com.android.utils.FileUtils
import com.android.utils.ILogger
import com.android.utils.StdLogger
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JdkImageTransformDelegateTest {

    @JvmField
    @Rule
    val tmp = TemporaryFolder()

    val javaHome = TestUtils.getJava11Jdk().toFile()

    fun copyCfsmJarToTempFolder(): File {
        val originalCfsmJar =
                TestUtils.resolvePlatformPath(SdkConstants.FN_CORE_FOR_SYSTEM_MODULES).toFile()
        val cfsmJar = tmp.newFile(originalCfsmJar.name)
        FileUtils.copyFile(originalCfsmJar, cfsmJar)
        return cfsmJar
    }

    @Test
    fun testGenerateModuleDescriptorMethod() {
        val cfsmJar = copyCfsmJarToTempFolder()

        val result = generateModuleDescriptor("java.base", listOf(cfsmJar))
        Truth.assertThat(result).isEqualTo(
            """
            module java.base {
                exports android.icu.lang;
                exports android.icu.math;
                exports android.icu.number;
                exports android.icu.text;
                exports android.icu.util;
                exports android.net.ssl;
                exports android.system;
                exports dalvik.annotation;
                exports dalvik.bytecode;
                exports dalvik.system;
                exports java.awt.font;
                exports java.beans;
                exports java.io;
                exports java.lang;
                exports java.lang.annotation;
                exports java.lang.invoke;
                exports java.lang.ref;
                exports java.lang.reflect;
                exports java.math;
                exports java.net;
                exports java.nio;
                exports java.nio.channels;
                exports java.nio.channels.spi;
                exports java.nio.charset;
                exports java.nio.charset.spi;
                exports java.nio.file;
                exports java.nio.file.attribute;
                exports java.nio.file.spi;
                exports java.security;
                exports java.security.acl;
                exports java.security.cert;
                exports java.security.interfaces;
                exports java.security.spec;
                exports java.sql;
                exports java.text;
                exports java.time;
                exports java.time.chrono;
                exports java.time.format;
                exports java.time.temporal;
                exports java.time.zone;
                exports java.util;
                exports java.util.concurrent;
                exports java.util.concurrent.atomic;
                exports java.util.concurrent.locks;
                exports java.util.function;
                exports java.util.jar;
                exports java.util.logging;
                exports java.util.prefs;
                exports java.util.regex;
                exports java.util.stream;
                exports java.util.zip;
                exports javax.annotation.processing;
                exports javax.crypto;
                exports javax.crypto.interfaces;
                exports javax.crypto.spec;
                exports javax.net;
                exports javax.net.ssl;
                exports javax.security.auth;
                exports javax.security.auth.callback;
                exports javax.security.auth.login;
                exports javax.security.auth.x500;
                exports javax.security.cert;
                exports javax.sql;
                exports javax.xml;
                exports javax.xml.datatype;
                exports javax.xml.namespace;
                exports javax.xml.parsers;
                exports javax.xml.transform;
                exports javax.xml.transform.dom;
                exports javax.xml.transform.sax;
                exports javax.xml.transform.stream;
                exports javax.xml.validation;
                exports javax.xml.xpath;
                exports org.json;
                exports org.w3c.dom;
                exports org.w3c.dom.ls;
                exports org.xml.sax;
                exports org.xml.sax.ext;
                exports org.xml.sax.helpers;
                exports org.xmlpull.v1;
                exports org.xmlpull.v1.sax2;
            }

        """.trimIndent().replace("\n", System.lineSeparator())
        )
    }

    @Test
    fun testModuleDescriptorCompilation() {
        val cfsmJar = copyCfsmJarToTempFolder()
        val logger = StdLogger(StdLogger.Level.VERBOSE)
        val outDir = FileUtils.join(tmp.newFolder(), "system")

        val delegate =
            JdkImageTransformDelegate(
                cfsmJar,
                tmp.newFolder(),
                outDir,
                JdkTools(
                    javaHome,
                    DefaultProcessExecutor(logger),
                    logger
                )
            )

        val moduleInfoClass = delegate.makeModuleInfoClass()

        //  validate moduleInfoClass content via "javap" command
        val pib = ProcessInfoBuilder().apply {
            setExecutable(javaHome.resolve("bin").resolve("javap".optionalExe()))
            addArgs(moduleInfoClass.absolutePath)
        }
        val processHandler = CachedProcessOutputHandler()
        DefaultProcessExecutor(logger).execute(
            pib.createProcess(),
            processHandler
        ).rethrowFailure().assertNormalExitValue()

        Truth.assertThat(processHandler.processOutput.standardOutputAsString).apply {
            startsWith("Compiled from \"module-info.java\"")
            contains("exports java.lang;")
            contains("exports android.icu.lang;")
        }
    }

    @Test
    fun testModuleJarCreation() {
        val cfsmJar = copyCfsmJarToTempFolder()
        val logger = StdLogger(StdLogger.Level.VERBOSE)
        val outDir = FileUtils.join(tmp.newFolder(), "system")

        val delegate =
            JdkImageTransformDelegate(
                cfsmJar,
                tmp.newFolder(),
                outDir,
                JdkTools(
                    javaHome,
                    DefaultProcessExecutor(logger),
                    logger
                )
            )

        val moduleJar = delegate.makeModuleJar()

        assertThat(moduleJar).exists()
        ZipFileSubject.assertThat(moduleJar) {
            it.contains("module-info.class")
            it.contains("java/lang")
            it.contains("android/icu")
        }
    }

    @Test
    fun testJmodFileCreation() {
        val cfsmJar = copyCfsmJarToTempFolder()
        val logger = StdLogger(StdLogger.Level.VERBOSE)
        val outDir = FileUtils.join(tmp.newFolder(), "system")

        val delegate =
            JdkImageTransformDelegate(
                cfsmJar,
                tmp.newFolder(),
                outDir,
                JdkTools(
                    javaHome,
                    DefaultProcessExecutor(logger),
                    logger
                )
            )

        val jmodFile = delegate.makeJmodFile()

        // validate jmodFile content via "jmod describe" command
        val pib = ProcessInfoBuilder().apply {
            setExecutable(javaHome.resolve("bin").resolve("jmod"))
            addArgs("describe")
            addArgs(jmodFile.absolutePath)
        }
        val processHandler = CachedProcessOutputHandler()
        DefaultProcessExecutor(logger).execute(
            pib.createProcess(),
            processHandler
        ).rethrowFailure().assertNormalExitValue()

        Truth.assertThat(processHandler.processOutput.standardOutputAsString).apply {
            startsWith("java.base@11.0")
            contains("exports java.lang")
            contains("exports android.icu.lang")
            contains("exports dalvik.system")
            contains("contains ojluni.src.lambda.java.java.lang.invoke")
            contains("platform android")
        }
    }

    @Test
    fun testDelegateRunMethod() {
        val cfsmJar = copyCfsmJarToTempFolder()

        val logger = StdLogger(StdLogger.Level.VERBOSE)
        val outDir = FileUtils.join(tmp.newFolder(), "system")

        val delegate =
            JdkImageTransformDelegate(
                cfsmJar,
                tmp.newFolder(),
                outDir,
                JdkTools(
                    javaHome,
                    DefaultProcessExecutor(logger),
                    logger
                )
            )

        delegate.run()
        Truth.assertThat(outDir.list()).asList().containsExactly("release", "lib")

        val jrtFsJar = outDir.resolve("lib").resolve("jrt-fs.jar")
        assertThat(jrtFsJar).exists()

        val compiledJava11Dir = tmp.newFolder("compiledJava11")

        compileTestClass(
            outDir, logger, TestResources.getFile(
                JdkImageTransformDelegateTest::class.java,
                "Java11Class.java"
            ), compiledJava11Dir
        )
        compileTestClass(
            outDir, logger, TestResources.getFile(
                JdkImageTransformDelegateTest::class.java,
                "Java11ClassWithAndroidApis.java"
            ), compiledJava11Dir
        )
    }

    private fun compileTestClass(
        systemImageDir: File,
        logger: ILogger,
        java11SourceFile: File,
        compiledJava11Dir: File
    ) {
        //compile a java 11 source file against the system image
        assertThat(java11SourceFile).exists()

        val androidJar = TestUtils.resolvePlatformPath(SdkConstants.FN_FRAMEWORK_LIBRARY)

        val pib = ProcessInfoBuilder().apply {
            setExecutable(javaHome.resolve("bin").resolve("javac".optionalExe()))
            addArgs("--system", systemImageDir.absolutePath)
            addArgs("-d", compiledJava11Dir.absolutePath)
            addArgs("-cp", androidJar.toString())
            addArgs(java11SourceFile.absolutePath)

        }
        DefaultProcessExecutor(logger).execute(
            pib.createProcess(),
            LoggedProcessOutputHandler(logger)
        ).rethrowFailure().assertNormalExitValue()

        val compiledJava11File = FileUtils.join(
            compiledJava11Dir,
            "${java11SourceFile.nameWithoutExtension}.class"
        )
        assertThat(compiledJava11File).exists()
    }
}

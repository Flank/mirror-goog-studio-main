/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.lint

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.GradleStyleMessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys

object UastEnvironmentFactory {
    private fun useFirUast(): Boolean =
        System.getProperty(FIR_UAST_KEY, "false").toBoolean()

    internal fun createCommonKotlinCompilerConfig(): CompilerConfiguration {
        val config = CompilerConfiguration()

        config.put(CommonConfigurationKeys.MODULE_NAME, "lint-module")

        // We're not running compiler checks, but we still want to register a logger
        // in order to see warnings related to misconfiguration.
        val logger = PrintingMessageCollector(System.err, GradleStyleMessageRenderer(), false)
        config.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, logger)

        // The Kotlin compiler uses a fast, ASM-based class file reader.
        // However, Lint still relies on representing class files with PSI.
        config.put(JVMConfigurationKeys.USE_PSI_CLASS_FILES_READING, true)

        return config
    }

    /**
     * Creates a new [UastEnvironment.Configuration] that specifies
     * project structure, classpath, compiler flags, etc.
     */
    fun createConfiguration(): UastEnvironment.Configuration {
        return if (useFirUast())
            FirUastEnvironment.Configuration.create()
        else
            Fe10UastEnvironment.Configuration.create()
    }

    /**
     * Creates a new [UastEnvironment] suitable for analyzing both
     * Java and Kotlin code. You must still call [UastEnvironment.analyzeFiles]
     * before doing anything with PSI/UAST. When finished using the
     * environment, call [UastEnvironment.dispose].
     */
    fun create(
        config: UastEnvironment.Configuration
    ): UastEnvironment {
       return if (useFirUast())
           FirUastEnvironment.create(config)
       else
           Fe10UastEnvironment.create(config)
    }
}

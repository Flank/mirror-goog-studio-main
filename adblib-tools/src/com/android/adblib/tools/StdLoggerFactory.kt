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
package com.android.adblib.tools

import com.android.adblib.AdbLogger
import com.android.adblib.AdbLoggerFactory

internal class StdLoggerFactory : AdbLoggerFactory {

    private val log = StdLogger()

    override val logger: AdbLogger
        get() = log

    override fun createLogger(cls: Class<*>): AdbLogger {
        return log
    }

    override fun createLogger(category: String): AdbLogger {
        return log
    }
}

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

import com.android.adblib.AdbSession

/**
 * Creates an [AdbSession] to use with Console/Command Line Interface tools,
 * using standard Kotlin dispatchers and JVM stdout/stderr streams for I/O.
 *
 * ** WARNING **. Use this function with care. The philosophy of adblib is to have a
 * single AdbSession and a single AdbHost per VM. This is currently used in adblib CLI and
 * deployerlib CLI.
 * Anywhere else, it is very likely you should obtain the Session from where it was already
 * created.
 */

fun createStandaloneSession() : AdbSession {
    // TODO Move to an AdbChannelProvider that knows how to spawn and ADB server.
    // This one assume it is already up and running which is fine for our current needs.
    val host = StandaloneHost(StdLoggerFactory())
    val session = AdbSession.create(host)
    return session
}

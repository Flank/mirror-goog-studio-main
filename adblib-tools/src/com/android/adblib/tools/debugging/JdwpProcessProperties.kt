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
package com.android.adblib.tools.debugging

import com.android.adblib.tools.debugging.impl.JdwpSessionProxy
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkTypes
import com.android.adblib.tools.debugging.packets.ddms.chunks.DdmsFeatChunk
import com.android.adblib.tools.debugging.packets.ddms.chunks.DdmsReaqChunk

/**
 * List of known properties corresponding to a [JdwpProcess] instance.
 */
data class JdwpProcessProperties(

    /**
     * The process ID. This is the only property that is guaranteed to be valid, all other
     * properties can be `null` or have default value until more is known about a process.
     */
    val pid: Int,

    /**
     * The process name that uniquely identifies the process on the device, or `null` if the process
     * name is not known (yet) due to debugger latency or an error connecting to the process and
     * retrieving data about it.
     *
     * The process name is often equal to [packageName], except when a `android:process`
     * process name entry is specified in the
     * [AndroidManifest.xml](https://developer.android.com/guide/topics/manifest/application-element)
     * file.
     */
    val processName: String? = null,

    /**
     * The User ID this process is running in context of, or `null` if the value is not known yet or
     * the device does not support retrieving this information (R+ only).
     */
    val userId: Int? = null,

    /**
     * The package name of the process, or `null` if the value is not known yet or if the device
     * does not support retrieving this information (R+ only)
     */
    val packageName: String? = null,

    /**
     * The Android VM identifier, or `null` if the value is not known yet.
     */
    val vmIdentifier: String? = null,

    /**
     * The ABI identifier, or `null` if the value is not known yet.
     */
    val abi: String? = null,

    /**
     * The JVM flags, or `null` if the value is not known yet.
     */
    val jvmFlags: String? = null,

    /**
     * Whether legacy native debugging is supported.
     */
    @Deprecated("This property was never fully supported and is now completely deprecated")
    val isNativeDebuggable: Boolean = false,

    /**
     * `true` if the process is waiting for a debugger to attach.
     * `false` if we don't know or if a debugger is already attached.
     */
    var isWaitingForDebugger: Boolean = false,

    /**
     * The status of JDWP session proxy between an external debugger and the Android Process.
     *
     * @see JdwpSessionProxy
     */
    val jdwpSessionProxyStatus: JdwpSessionProxyStatus = JdwpSessionProxyStatus(),

    /**
     * List of features reported by the [DdmsFeatChunk] packet
     */
    val features: List<String> = emptyList(),

    /**
     * Whether "REcent Allocation Query" is enabled for this process.
     *
     * @see DdmsChunkTypes.REAQ
     * @see DdmsReaqChunk
     */
    val reaqEnabled: Boolean = false,

    /**
     * Captures an error related to retrieving properties other than [pid].
     *
     * For example, it is sometimes not possible to retrieve any information about a process ID
     * from the Android VM if there is already a JDWP session active for that process.
     */
    val exception: Throwable? = null,
)

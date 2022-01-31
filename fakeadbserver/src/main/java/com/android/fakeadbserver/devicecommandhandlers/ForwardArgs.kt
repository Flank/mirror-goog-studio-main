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
package com.android.fakeadbserver.devicecommandhandlers

data class ForwardArgs(
    val norebind: Boolean,
    val fromTransport: String,
    val fromTransportArg: String,
    val toTransport: String,
    val toTransportArg: String
) {

    companion object {

        /**
         * Parses a `forward` or `reverse` command into a [ForwardArgs], throwing
         * [IllegalArgumentException] if the format is incorrect.
         */
        @JvmStatic
        fun parse(input: String): ForwardArgs {
            var argsString = input

            // Scan `norebind` option
            val noRebind = if (argsString.startsWith("norebind:")) {
                argsString = argsString.split(":".toRegex(), 2).toTypedArray()[1]
                true
            } else {
                false
            }

            // Scan server socket spec string
            val addressStrings = argsString.split(";".toRegex()).toTypedArray()
            if (addressStrings.size != 2) {
                throw IllegalArgumentException("Forward query should contain 2 addresses: $argsString")
            }

            // Scan destination socket spec string
            val fromAddress = addressStrings[0].split(":".toRegex()).toTypedArray()
            if (fromAddress.size != 2) {
                throw IllegalArgumentException("Source address format is not supported: " + addressStrings[0])
            }
            val fromTransport = fromAddress[0]
            val fromTransportArg = fromAddress[1]

            val toAddress = addressStrings[1].split(":".toRegex()).toTypedArray()
            if (toAddress.size != 2) {
                throw IllegalArgumentException("Destination address format is not supported: " + addressStrings[1])
            }
            val toTransport = toAddress[0]
            val toTransportArg = toAddress[1]

            return ForwardArgs(
                noRebind,
                fromTransport,
                fromTransportArg,
                toTransport,
                toTransportArg
            )
        }
    }
}

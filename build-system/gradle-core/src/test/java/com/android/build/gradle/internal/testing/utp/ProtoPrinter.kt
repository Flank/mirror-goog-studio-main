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

package com.android.build.gradle.internal.testing.utp

import com.google.protobuf.Descriptors
import com.google.protobuf.MessageOrBuilder
import com.google.protobuf.Parser
import com.google.protobuf.TextFormat

/**
 * A utility class for printing protobuf message to a string for testing.
 *
 * Unlike [MessageOrBuilder.toString], it unpacks [com.google.protobuf.Any] fields
 * and prints them in human readable format instead of escaped byte string.
 *
 * @param knownProtoMessages a list of [MessageOrBuilder] classes to be registered. Those proto
 * message classes are used for unpacking [com.google.protobuf.Any] fields.
 */
class ProtoPrinter(knownProtoMessages: List<Class<*>>) {

    private val typeUrlToParser: Map<String, Parser<*>> = knownProtoMessages.map {
        val descriptor = it.getMethod("getDescriptor").invoke(null) as Descriptors.Descriptor
        val parser = it.getMethod("parser").invoke(null) as Parser<*>
        "type.googleapis.com/${descriptor.fullName}" to parser
    }.toMap()

    /**
     * Prints a given proto to a string.
     */
    fun printToString(proto: MessageOrBuilder): String {
        val protoString = proto.toString()
        return unpackKnownAnyProto(protoString).trim()
    }

    private fun unpackKnownAnyProto(protoString: String): String {
        var last_type_url = ""
        return protoString.lineSequence().map { line ->
            val trimmedLine = line.trimStart()
            if (trimmedLine.startsWith("type_url:")) {
                last_type_url = trimmedLine.removePrefix("type_url:").trim().removeSurrounding("\"")
                line
            } else if (trimmedLine.startsWith("value:")) {
                val indent = line.length - trimmedLine.length
                val value = trimmedLine.removePrefix("value:").trim().removeSurrounding("\"")
                printAnyProtoToString(last_type_url, value, indent)
            } else {
                line
            }
        }.joinToString("\n")
    }

    private fun printAnyProtoToString(type_url: String, value: String, indent: Int): String {
        val parser = typeUrlToParser[type_url]
        val result = if (parser == null) {
            """value: "${value}""""
        } else {
            val valueString = parser.parseFrom(TextFormat.unescapeBytes(value)).toString().trim()
            unpackKnownAnyProto("value {\n${valueString.prependIndent("  ")}\n}")
        }
        return result.prependIndent(" ".repeat(indent))
    }
}

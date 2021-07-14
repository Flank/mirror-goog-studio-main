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

package com.android.tools.appinspection.network.rules

import java.io.InputStream

class NetworkConnection(
    val url: String,
)

data class NetworkResponse(
    val responseMessage: String,
    val responseHeaders: Map<String, List<String>>,
    val body: InputStream
)

/**
 * A service class that maintains and applies a list of rules that intercept network requests and
 * responses.
 */
interface InterceptionRuleService {

    fun interceptResponse(connection: NetworkConnection, response: NetworkResponse): NetworkResponse
    fun addRule(rule: InterceptionRule)
    fun removeRule(rule: InterceptionRule)
}

class InterceptionRuleServiceImpl : InterceptionRuleService {

    private val rules = mutableListOf<InterceptionRule>()

    override fun interceptResponse(
        connection: NetworkConnection,
        response: NetworkResponse
    ): NetworkResponse {
        return rules.fold(response) { intermediateResponse, rule ->
            rule.transform(
                connection,
                intermediateResponse
            )
        }
    }

    override fun addRule(rule: InterceptionRule) {
        rules.add(rule)
    }

    override fun removeRule(rule: InterceptionRule) {
        rules.remove(rule)
    }
}

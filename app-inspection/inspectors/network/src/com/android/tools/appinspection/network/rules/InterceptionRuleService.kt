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

data class NetworkConnection(
    val url: String,
    val method: String,
)

data class NetworkResponse(
    val responseHeaders: Map<String?, List<String>>,
    val body: InputStream,
    val interception: NetworkInterceptionMetrics = NetworkInterceptionMetrics()
)

data class NetworkInterceptionMetrics(
    val criteriaMatched: Boolean = false,
    val statusCode: Boolean = false,
    val headerAdded: Boolean = false,
    val headerReplaced: Boolean = false,
    val bodyReplaced: Boolean = false,
    val bodyModified: Boolean = false
)

/**
 * A service class that maintains and applies a list of rules that intercept network requests and
 * responses.
 */
interface InterceptionRuleService {

    /**
     * Intercepts the provided [response] with the current rules.
     */
    fun interceptResponse(connection: NetworkConnection, response: NetworkResponse): NetworkResponse

    /**
     * Adds a new rule to the service. If the rule id already exists, overwrite the existing one.
     */
    fun addRule(ruleId: Int, rule: InterceptionRule)

    /**
     * Removes a rule from the service.
     */
    fun removeRule(ruleId: Int)

    /**
     * Reorders rules according to the [ruleIdList].
     */
    fun reorderRules(ruleIdList: List<Int>)
}

class InterceptionRuleServiceImpl : InterceptionRuleService {

    private val rules = mutableMapOf<Int, InterceptionRule>()
    private var ruleIdList = mutableListOf<Int>()

    @Synchronized
    override fun interceptResponse(
        connection: NetworkConnection,
        response: NetworkResponse
    ): NetworkResponse = ruleIdList.mapNotNull { id -> rules[id] }
        .filter { it.isEnabled }
        .fold(response) { intermediateResponse, rule ->
            rule.transform(
                connection,
                intermediateResponse
            )
        }

    @Synchronized
    override fun addRule(ruleId: Int, rule: InterceptionRule) {
        if (!rules.containsKey(ruleId)) {
            ruleIdList.add(ruleId)
        }
        rules[ruleId] = rule
    }

    @Synchronized
    override fun removeRule(ruleId: Int) {
        ruleIdList.remove(ruleId)
        rules.remove(ruleId)
    }

    @Synchronized
    override fun reorderRules(ruleIdList: List<Int>) {
        this.ruleIdList = ruleIdList.toMutableList()
    }
}

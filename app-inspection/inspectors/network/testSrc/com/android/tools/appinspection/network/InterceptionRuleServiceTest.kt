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

package com.android.tools.appinspection.network

import com.android.tools.appinspection.network.rules.InterceptionRule
import com.android.tools.appinspection.network.rules.InterceptionRuleServiceImpl
import com.android.tools.appinspection.network.rules.NetworkConnection
import com.android.tools.appinspection.network.rules.NetworkResponse
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InterceptionRuleServiceTest {

    // Does not matter what the connection is.
    private val defaultConnection = NetworkConnection("", "")

    private val startingResponse = NetworkResponse(emptyMap(), ByteArray(0).inputStream())

    // A rule that adds a letter to the end of the body. For testing purposes.
    private fun testRule(c: Char, isEnabled: Boolean = true) = object : InterceptionRule {
        override val isEnabled = isEnabled

        override fun transform(
            connection: NetworkConnection,
            response: NetworkResponse
        ) = response.copy(
            body = "${response.body.bufferedReader().use { it.readText() }}${c}".toByteArray()
                .inputStream()
        )
    }

    private fun NetworkResponse.assertBodyEquals(expected: String) {
        val result = body.bufferedReader().use { it.readText() }
        assertThat(result).isEqualTo(expected)
    }

    private fun intercept(service: InterceptionRuleServiceImpl) =
        service.interceptResponse(defaultConnection, startingResponse)

    @Test
    fun `add rule adds new rules in service`() {
        val service = InterceptionRuleServiceImpl()
        service.addRule(1, testRule('a'))
        service.addRule(2, testRule('b'))
        intercept(service)
            .assertBodyEquals("ab")
    }

    @Test
    fun `add rule with existing id overwrites the older rule`() {
        val service = InterceptionRuleServiceImpl()
        service.addRule(1, testRule('a'))
        service.addRule(1, testRule('b'))
        intercept(service)
            .assertBodyEquals("b")
    }

    @Test
    fun `reorder rules should change order in which the rules are applied`() {
        val service = InterceptionRuleServiceImpl()
        service.addRule(1, testRule('a'))
        service.addRule(2, testRule('b'))
        service.addRule(3, testRule('c'))

        service.reorderRules(listOf(2, 3, 1))
        intercept(service).assertBodyEquals("bca")
    }

    @Test
    fun `remove rule from service`() {
        val service = InterceptionRuleServiceImpl()
        service.addRule(1, testRule('a'))
        service.addRule(2, testRule('b'))
        service.addRule(3, testRule('c'))

        service.removeRule(2)
        intercept(service).assertBodyEquals("ac")
    }

    @Test
    fun `remove rule that does not exist results in no-op`() {
        val service = InterceptionRuleServiceImpl()
        service.addRule(1, testRule('a'))
        service.addRule(2, testRule('b'))

        service.removeRule(3)
        intercept(service).assertBodyEquals("ab")
    }

    @Test
    fun `interception response should not trigger disabled rules`() {
        val service = InterceptionRuleServiceImpl()
        service.addRule(1, testRule('a'))
        service.addRule(2, testRule('b', false))

        intercept(service).assertBodyEquals("a")
    }
}

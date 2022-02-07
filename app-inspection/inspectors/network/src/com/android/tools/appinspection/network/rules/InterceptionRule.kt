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

package com.android.tools.appinspection.network.rules

/**
 * A rule class that intercepts connections and their responses that matches certain [criteria].
 */
abstract class InterceptionRule(private val criteria: InterceptionCriteria, val id: Int) {

    protected abstract fun doTransform(
        connection: NetworkConnection,
        response: NetworkResponse
    ): NetworkResponse

    fun transform(
        connection: NetworkConnection,
        response: NetworkResponse
    ): NetworkResponse {
        if (criteria.appliesTo(connection)) {
            return doTransform(connection, response)
        }
        return response
    }
}

class BodyInterceptionRule(
    id: Int,
    criteria: InterceptionCriteria,
    private val body: ByteArray
) : InterceptionRule(criteria, id) {

    override fun doTransform(
        connection: NetworkConnection,
        response: NetworkResponse
    ) = response.copy(body = body.inputStream())
}

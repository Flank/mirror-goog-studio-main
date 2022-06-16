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

import studio.network.inspection.NetworkInspectorProtocol.InterceptRule

/**
 * A rule class that intercepts connections and their responses that matches certain [criteria].
 */
interface InterceptionRule {
    val isEnabled: Boolean
    fun transform(
        connection: NetworkConnection,
        response: NetworkResponse
    ): NetworkResponse
}

class InterceptionRuleImpl(proto: InterceptRule) : InterceptionRule {

    override val isEnabled = proto.enabled
    private val criteria: InterceptionCriteria
    private val transformations: List<InterceptionTransformation>

    init {
        criteria = InterceptionCriteria(proto.criteria)
        transformations = proto.transformationList.mapNotNull { transformationProto ->
            when {
                transformationProto.hasStatusCodeReplaced() ->
                    StatusCodeReplacedTransformation(transformationProto.statusCodeReplaced)
                transformationProto.hasHeaderAdded() ->
                    HeaderAddedTransformation(transformationProto.headerAdded)
                transformationProto.hasHeaderReplaced() ->
                    HeaderReplacedTransformation(transformationProto.headerReplaced)
                transformationProto.hasBodyReplaced() ->
                    BodyReplacedTransformation(transformationProto.bodyReplaced)
                transformationProto.hasBodyModified() ->
                    BodyModifiedTransformation(transformationProto.bodyModified)
                else -> null
            }
        }
    }

    override fun transform(
        connection: NetworkConnection,
        response: NetworkResponse
    ): NetworkResponse {
        if (criteria.appliesTo(connection)) {
            return transformations.fold(
                response.copy(interception = response.interception.copy(criteriaMatched = true))
            ) { intermediateResponse, transformation ->
                transformation.transform(intermediateResponse)
            }
        }
        return response
    }
}

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

import studio.network.inspection.NetworkInspectorProtocol.InterceptCriteria
import java.net.URL

/**
 * A criteria class that checks if a connection should be intercepted.
 */
class InterceptionCriteria(private val interceptCriteria: InterceptCriteria) {

    fun appliesTo(connection: NetworkConnection): Boolean {
        if (interceptCriteria.method.isNotBlank() && connection.method != interceptCriteria.method) {
            return false
        }
        val url = URL(connection.url)
        return interceptCriteria.protocol == url.protocol &&
                wildCardMatches(
                    interceptCriteria.port,
                    if (url.port == -1) "" else url.port.toString()
                ) &&
                wildCardMatches(interceptCriteria.host, url.host) &&
                wildCardMatches(interceptCriteria.path, url.path) &&
                wildCardMatches(interceptCriteria.query, url.query)
    }
}

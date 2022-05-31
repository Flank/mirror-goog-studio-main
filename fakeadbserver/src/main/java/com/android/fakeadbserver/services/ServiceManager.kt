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
package com.android.fakeadbserver.services

import java.util.Collections

class ServiceManager {

    companion object {

        val ACTIVITY_MANAGER_SERVICE_NAME = "activity"
    }

    private val packageManager = PackageManager()
    private var activityManager: Service? = null
    private val log = java.util.Collections.synchronizedList(mutableListOf<List<String>>())

    // Returns a list of all service request received.
    // Each entry is a list of all parameters for that request.
    fun getLogs(): List<ServiceRequest> {
        return Collections.unmodifiableList(log)
    }

    fun processCommand(args: List<String>, output: ServiceOutput) {
        // We log received commands to allow tests to inspect call history
        log.add(Collections.unmodifiableList(args))

        val serviceName = args[0]
        val service = getService(serviceName)

        if (service == null) {
            output.writeStderr("Error: Service '$serviceName' is not supported")
            output.writeExitCode(5)
            return;
        }

        service.process(args.slice(1 until args.size), output)
    }

    fun setActivityManager(newActivityManager: Service) {
        activityManager = newActivityManager
    }

    private fun getService(name: String): Service? = when (name) {
        PackageManager.SERVICE_NAME -> packageManager
        ACTIVITY_MANAGER_SERVICE_NAME -> activityManager
        else -> null
    }
}

typealias ServiceRequest = List<String>

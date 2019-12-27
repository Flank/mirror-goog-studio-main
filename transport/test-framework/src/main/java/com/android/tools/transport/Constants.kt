/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.transport

const val LOCAL_HOST = "127.0.0.1"

/**
 * System properties which should be configured for the test framework to work correctly.
 *
 * These will be set for you automatically by the `transport_test` rule in `transport_test.bzl`
 */
object SystemProperties {
    /**
     * The path to the transport daemon binary.
     *
     * Ex: `path/to/transportbinary`
     */
    val TRANSPORT_DAEMON_LOCATION = "transport.daemon.location"

    /**
     * A list of zero or more paths pointing to library dependencies (both native .so and .jar
     * libraries are expected here).
     *
     * If there are multiple paths, they should be separated by the ':' character.
     */
    val APP_LIBS = "app.libs"

    /**
     * A list of one or more paths to dexed Jars which will be loaded onto a device *if* it
     * supports JVMTI (O or newer).
     *
     * If there are multiple paths, they should be separated by the ':' character.
     *
     * Ex: "path/to/jar1.dex:path/to/jar2.dex"
     */
    val APP_DEXES_JVMTI = "app.dexes.jvmti"

    /**
     * Similar to [APP_DEXES_JVMTI], except for paths to dexed Jars which will be loaded onto a
     * device if it does NOT support JVMTI (pre-O).
     *
     * If there are multiple paths, they should be separated by the ':' character.
     */
    val APP_DEXES_NOJVMTI = "app.dexes.nojvmti"
}

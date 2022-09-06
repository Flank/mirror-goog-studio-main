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
package com.google.firebase.testlab.gradle

import com.android.build.api.dsl.Device
import org.gradle.api.Incubating

/**
 * Device type for devices that are run through Firebase Testlab.
 *
 * These APIs are experimental and may change without notice.
 */
@Incubating
interface ManagedDevice : Device {

    /**
     * The model id of the device to be run.
     *
     * Specifies the model id to be run in firebase test lab. For a
     * list of model ids run:
     *
     *     gcloud firebase test android models list
     */
    var device: String

    /**
     * The api level of Android to be run on the device.
     *
     * This argument is optional and only some apiLevels are available
     * for each hardware profile. If no value is specified the latest
     * available api is used.
     */
    var apiLevel: Int

    /**
     * The orientation the device should have when tests are run.
     */
    var orientation: Orientation

    /**
     * The locale that the device should be set to.
     */
    var locale: String
}

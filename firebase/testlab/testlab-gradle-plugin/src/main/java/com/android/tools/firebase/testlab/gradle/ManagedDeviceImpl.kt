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

package com.android.tools.firebase.testlab.gradle

import com.google.firebase.testlab.gradle.ManagedDevice
import com.google.firebase.testlab.gradle.Orientation
import javax.inject.Inject

/**
 * Implementation for [ManagedDevice] to be registered with the
 * Android Plugin for Gradle
 */
open class ManagedDeviceImpl @Inject constructor(private val name: String): ManagedDevice {
    override fun getName(): String = name

    override var device = ""

    override var apiLevel = -1

    override var orientation = Orientation.DEFAULT

    override var locale = "en-US"
}

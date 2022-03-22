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

package com.android.build.gradle.internal.plugins

import com.android.build.api.dsl.SettingsExtension
import com.android.build.gradle.internal.dsl.SettingsExtensionImpl
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

class SettingsPlugin @Inject constructor (private val objectFactory: ObjectFactory): Plugin<Settings> {

    override fun apply(settings: Settings) {
        val settingsExtension = settings.extensions.create(
            SettingsExtension::class.java,
            "android",
            SettingsExtensionImpl::class.java,
            objectFactory
        )

        // as Project objects cannot query for the Settings object (and its extensions), we
        // deposit the extension instance into each project using the extra Properties.
        settings.gradle.beforeProject { project ->
            project.extensions.extraProperties["_android_settings"] = settingsExtension
        }
    }
}

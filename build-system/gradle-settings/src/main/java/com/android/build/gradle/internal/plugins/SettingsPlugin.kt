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

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.DynamicFeatureExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.TestExtension
import com.android.build.api.dsl.SettingsExtension
import com.android.build.gradle.internal.dsl.SettingsExtensionImpl
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

class SettingsPlugin: Plugin<Settings> {

    override fun apply(settings: Settings) {

        // create the extension to collect the values
        val defaults = settings.extensions.create(
            SettingsExtension::class.java,
            "android",
            SettingsExtensionImpl::class.java
        ) as SettingsExtensionImpl

        // register a beforeProject on all the project that will apply a bit of code
        settings.gradle.beforeProject { project ->
            // register a call back on plugin application
            // If the applied plugin is an Android Plugin then we apply the default coming from
            // the settings DSL.
            project.plugins.whenPluginAdded { plugin: Plugin<Any> ->

                // For now we don't want to use the Plugin class directly because they are not
                // available in gradle-api and we want to potentially move this plugin to a
                // different artifact.
                // Once the plugin classes move to gradle-api (8.0?), then we can use these
                // public classes directly.

                when (plugin.javaClass.name) {
                    "com.android.build.gradle.AppPlugin" -> {
                        val extension =
                            project.extensions.findByType(ApplicationExtension::class.java)
                                ?: throw RuntimeException("Failed to find extension of type ApplicationExtension on project '${project.path}' despite plugin 'com.android.build.gradle.AppPlugin' being applied.")
                        configureApp(extension, defaults)
                    }

                    "com.android.build.gradle.LibraryPlugin" -> {
                        val extension =
                            project.extensions.findByType(LibraryExtension::class.java)
                                ?: throw RuntimeException("Failed to find extension of type LibraryExtension on project '${project.path}' despite plugin 'com.android.build.gradle.LibraryPlugin' being applied.")
                        configureLib(extension, defaults)
                    }

                    "com.android.build.gradle.TestPlugin" -> {
                        val extension =
                            project.extensions.findByType(TestExtension::class.java)
                                ?: throw RuntimeException("Failed to find extension of type TestExtension on project '${project.path}' despite plugin 'com.android.build.gradle.TestPlugin' being applied.")
                        configureTest(extension, defaults)
                    }

                    "com.android.build.gradle.DynamicFeaturePlugin" -> {
                        val extension =
                            project.extensions.findByType(DynamicFeatureExtension::class.java)
                                ?: throw RuntimeException("Failed to find extension of type DynamicFeatureExtension on project '${project.path}' despite plugin 'com.android.build.gradle.DynamicFeaturePlugin' being applied.")
                        configureDynamicFeature(extension, defaults)
                    }
                }
            }
        }
    }

    private fun configureCommon(android: CommonExtension<*,*,*,*>, defaults: SettingsExtensionImpl) {
        with(android) {
            defaults.compileSdk?.let { compileSdk = it }
            defaults.compileSdkPreview?.let { compileSdkPreview = it }
            if (defaults.hasAddOn) {
                compileSdkAddon(defaults.addOnVendor!!, defaults.addOnName!!, defaults.addOnApiLevel!!)
            }

            defaults.minSdk?.let { defaultConfig.minSdk = it }
            defaults.minSdkPreview?.let { defaultConfig.minSdkPreview = it }
        }
    }

    private fun configureApp(android: ApplicationExtension, defaults: SettingsExtensionImpl) {
        configureCommon(android, defaults)
    }

    private fun configureLib(android: LibraryExtension, defaults: SettingsExtensionImpl) {
        configureCommon(android, defaults)
    }

    private fun configureTest(android: TestExtension, defaults: SettingsExtensionImpl) {
        configureCommon(android, defaults)
    }

    private fun configureDynamicFeature(
        android: DynamicFeatureExtension,
        defaults: SettingsExtensionImpl
    ) {
        configureCommon(android, defaults)
    }
}

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
package com.android.prefs

import com.android.utils.EnvironmentProvider
import org.junit.rules.ExternalResource
import java.nio.file.FileSystem
import java.nio.file.Files

/**
 * Initial implementation of a rule that should allow [AndroidLocationsSingleton] to refer to an
 * in-memory filesystem during tests.
 */
class AndroidLocationsSingletonRule(private val fileSystem: FileSystem) : ExternalResource() {

    private var prevAndroidUserHome: String? = null

    override fun before() {
        EnvironmentProvider.DIRECT.fileSystemOverrideForTests = fileSystem
        val androidHome = fileSystem.rootDirectories.first().resolve("home/user/.android")
        prevAndroidUserHome = System.getProperty(AbstractAndroidLocations.ANDROID_USER_HOME)
        System.setProperty(AbstractAndroidLocations.ANDROID_USER_HOME, androidHome.toString())
        Files.createDirectories(androidHome)
        AndroidLocationsSingleton.resetPathsForTest()
    }

    override fun after() {
        EnvironmentProvider.DIRECT.fileSystemOverrideForTests = null
        prevAndroidUserHome?.let {
            System.setProperty(AbstractAndroidLocations.ANDROID_USER_HOME, it)
        } ?: System.clearProperty(AbstractAndroidLocations.ANDROID_USER_HOME)
        AndroidLocationsSingleton.resetPathsForTest()
    }
}

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

package com.android.tools.appinspection.common

import androidx.inspection.ArtTooling

/**
 * Provides useful implementations for simple use cases of entry and exit hooks.
 *
 * Allows users to provide their own entryhook arguments and exithook results in tests.
 */
abstract class FakeArtTooling : ArtTooling {

    private val entryHooks = mutableMapOf<String, ArtTooling.EntryHook>()
    private val exitHooks = mutableMapOf<String, ArtTooling.ExitHook<*>>()

    override fun registerEntryHook(
        originClass: Class<*>,
        originMethod: String,
        entryHook: ArtTooling.EntryHook
    ) {
        entryHooks["${originClass.name}:$originMethod"] = entryHook
    }

    override fun <T> registerExitHook(
        originClass: Class<*>,
        originMethod: String,
        exitHook: ArtTooling.ExitHook<T>
    ) {
        exitHooks["${originClass.name}:$originMethod"] = exitHook
    }

    fun triggerEntryHook(
        originClass: Class<*>,
        originMethod: String,
        thisObject: Any?,
        args: List<Any>
    ) {
        entryHooks["${originClass.name}:$originMethod"]!!.onEntry(thisObject, args)
    }

    fun <T> triggerExitHook(originClass: Class<*>, originMethod: String, obj: T): T {
        return (exitHooks
            .filterKeys {
                it == "${originClass.name}:$originMethod"
            }.values.first() as ArtTooling.ExitHook<T>)
            .onExit(obj)
    }
}

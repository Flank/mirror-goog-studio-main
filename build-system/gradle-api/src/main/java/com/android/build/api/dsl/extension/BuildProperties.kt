/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.api.dsl.extension

import com.android.build.api.sourcesets.AndroidSourceSet
import com.android.build.api.transform.SecondaryFile
import com.android.build.api.transform.Transform
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.FileCollection

/** Partial extension properties for modules that build a variant aware android artifact  */
interface BuildProperties {
    /** Build tools version. If null, uses the default value  */
    var buildToolsVersion: String?

    /** Compile SDK version.  */
    var compileSdkVersion: String?

    /** @see [compileSdkVersion]
     */
    fun setCompileSdkVersion(apiLevel: Int)

    /**
     * Configures source sets.
     *
     *
     * Note that the Android plugin uses its own implementation of source sets, [ ].
     */
    fun sourceSets(action: Action<NamedDomainObjectContainer<AndroidSourceSet>>)

    /** Source sets for all variants.  */
    val sourceSets: NamedDomainObjectContainer<AndroidSourceSet>

    /**
     * Request the use a of Library. The library is then added to the classpath.
     *
     * @param name the name of the library.
     */
    fun useLibrary(name: String)

    /**
     * Request the use a of Library. The library is then added to the classpath.
     *
     * @param name the name of the library.
     * @param required if using the library requires a manifest entry, the entry will indicate that
     * the library is not required.
     */
    fun useLibrary(name: String, required: Boolean)

    /** A prefix to be used when creating new resources. Used by Android Studio.  */
    var resourcePrefix: String?

    /** Registers a Transform.  */
    fun registerTransform(transform: Transform)

    val transforms: List<Transform>

    val transformsDependencies: List<List<Any>>

    // --- DEPRECATED

    /**
     * @Deprecated use [.registerTransform] and ensure that [SecondaryFile]
     * use [FileCollection] with task dependency
     */
    @Deprecated("")
    fun registerTransform(transform: Transform, vararg dependencies: Any)
}

/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.builder.model.v2

import java.io.File

/**
 * Represent a variant/module/artifact dependency.
 * @since 2.3
 */
interface Library {
    /**
     * The type of the dependency
     *
     * @return the type
     * @see .LIBRARY_ANDROID
     *
     * @see .LIBRARY_JAVA
     *
     * @see .LIBRARY_MODULE
     */
    val type: Int

    /**
     * Returns the artifact address in a unique way.
     *
     * This is either a module path for sub-modules (with optional variant name), or a maven
     * coordinate for external dependencies.
     */
    val artifactAddress: String

    /**
     * Returns the artifact location.
     */
    val artifact: File

    /**
     * Returns the build id.
     *
     *
     * This is only valid if the [.getProjectPath] is not null. However this can still be
     * null if this is the root project.
     *
     * @return the build id or null.
     */
    val buildId: String?

    /**
     * Returns the gradle path.
     *
     * Only valid for Android Library where [.getType] is [.LIBRARY_MODULE]
     */
    val projectPath: String?

    /**
     * Returns an optional variant name if the consumed artifact of the library is associated
     * to one.
     *
     * Only valid for Android Library where [.getType] is [.LIBRARY_MODULE]
     */
    val variant: String?

    /**
     * Returns the location of the unzipped bundle folder.
     *
     * Only valid for Android Library where [.getType] is [.LIBRARY_ANDROID]
     */
    val folder: File

    /**
     * Returns the location of the manifest relative to the folder.
     *
     * Only valid for Android Library where [.getType] is [.LIBRARY_ANDROID]
     */
    val manifest: String

    /**
     * Returns the location of the jar file to use for compiling and packaging.
     *
     *
     * Only valid for Android Library where [.getType] is [.LIBRARY_ANDROID].
     *
     * @return the path to the jar file. The path may not point to an existing file.
     */
    val jarFile: String

    /**
     * Returns the location of the jar file to use for compilation.
     *
     *
     * Only valid for Android Library where [.getType] is [.LIBRARY_ANDROID].
     *
     * @return path to the jar file used for compilation. The path may not point to an existing
     * file.
     */
    val compileJarFile: String

    /**
     * Returns the location of the res folder.
     *
     * Only valid for Android Library where [.getType] is [.LIBRARY_ANDROID]
     *
     * @return a File for the res folder. The file may not point to an existing folder.
     */
    val resFolder: String

    /**
     * Returns the location of the namespaced resources static library (res.apk).
     *
     *
     * Only valid for Android Library where [.getType] is [.LIBRARY_ANDROID]
     *
     *
     * TODO(b/109854607): When rewriting dependencies, this should be populated with the
     * rewritten artifact, which will not be in the exploded AAR directory.
     *
     * @return the static library apk. Null if the library is not namespaced.
     */
    val resStaticLibrary: File?

    /**
     * Returns the location of the assets folder.
     *
     *
     * Only valid for Android Library where [.getType] is [.LIBRARY_ANDROID]
     *
     * @return a File for the assets folder. The file may not point to an existing folder.
     */
    val assetsFolder: String

    /**
     * Returns the list of local Jar files that are included in the dependency.
     *
     * Only valid for Android Library where [.getType] is [.LIBRARY_ANDROID]
     *
     * @return a list of File. May be empty but not null.
     */
    val localJars: Collection<String>

    /**
     * Returns the location of the jni libraries folder.
     *
     * Only valid for Android Library where [.getType] is [.LIBRARY_ANDROID]
     *
     * @return a File for the folder. The file may not point to an existing folder.
     */
    val jniFolder: String

    /**
     * Returns the location of the aidl import folder.
     *
     * Only valid for Android Library where [.getType] is [.LIBRARY_ANDROID]
     *
     * @return a File for the folder. The file may not point to an existing folder.
     */
    val aidlFolder: String

    /**
     * Returns the location of the renderscript import folder.
     *
     * Only valid for Android Library where [.getType] is [.LIBRARY_ANDROID]
     *
     * @return a File for the folder. The file may not point to an existing folder.
     */
    val renderscriptFolder: String

    /**
     * Returns the location of the proguard files.
     *
     * Only valid for Android Library where [.getType] is [.LIBRARY_ANDROID]
     *
     * @return a File for the file. The file may not point to an existing file.
     */
    val proguardRules: String

    /**
     * Returns the location of the lint jar.
     *
     * Only valid for Android Library where [.getType] is [.LIBRARY_ANDROID]
     *
     * @return a File for the jar file. The file may not point to an existing file.
     */
    val lintJar: String

    /**
     * Returns the location of the external annotations zip file (which may not exist)
     *
     * Only valid for Android Library where [.getType] is [.LIBRARY_ANDROID]
     *
     * @return a File for the zip file. The file may not point to an existing file.
     */
    val externalAnnotations: String

    /**
     * Returns the location of an optional file that lists the only
     * resources that should be considered public.
     *
     * Only valid for Android Library where [.getType] is [.LIBRARY_ANDROID]
     *
     * @return a File for the file. The file may not point to an existing file.
     */
    val publicResources: String

    /**
     * Returns the location of the text symbol file
     *
     * Only valid for Android Library where [.getType] is [.LIBRARY_ANDROID]
     */
    val symbolFile: String

    companion object {
        const val LIBRARY_ANDROID = 1
        const val LIBRARY_JAVA = 2
        const val LIBRARY_MODULE = 3
    }
}
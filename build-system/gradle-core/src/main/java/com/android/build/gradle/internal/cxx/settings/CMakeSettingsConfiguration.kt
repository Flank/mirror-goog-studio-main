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

package com.android.build.gradle.internal.cxx.settings

import com.google.gson.annotations.JsonAdapter

/**
 * A 'configurations' element from CMakeSettings.json.
 */
data class CMakeSettingsConfiguration(
    /**
     * The configuration name.
     */
    val name: String = "",
    /**
     * Description of this configuration
     */
    val description: String = "",
    /**
     * The CMake generator name. Example: Ninja
     */
    val generator: String = "",
    /**
     * Specifies build type configuration for the selected generator.
     * Example, MinSizeRel
     */
    val configurationType: String = "",
    /**
     * The environments this configuration depends on.
     * Any custom environment can be used.
     */
    val inheritEnvironments: List<EnvironmentName> = listOf(),
    /**
     * The directory in which CMake generates build scripts for the chosen
     * generator. Supported macros include ${workspaceRoot},
     * ${workspaceHash}, ${projectFile}, ${projectDir}, ${thisFile},
     * ${thisFileDir}, ${name}, ${generator}, ${env.VARIABLE}.
     */
    val buildRoot: String = "",
    /**
     * The directory in which CMake generates install targets for the chosen
     * generator. Supported macros include ${workspaceRoot}, ${workspaceHash},
     * ${projectFile}, ${projectDir}, ${thisFile}, ${thisFileDir}, ${name},
     * ${generator}, ${env.VARIABLE}.
     */
    val installRoot: String = "",
    /**
     * Additional command line options passed to CMake when invoked to generate
     * the cache.
     */
    val cmakeCommandArgs: String = "",
    /**
     * Native build switches passed to CMake after --build --.
     */
    val buildCommandArgs: String = "",
    /**
     * Additional command line options passed to CTest when running the tests.
     */
    val ctestCommandArgs: String = "",
    /**
     * A list of CMake variables. The name value pairs are passed to CMake
     * as -Dname1=value1 -Dname2=value2, etc.
     */
    val variables: List<CMakeSettingsVariable> = listOf()
)
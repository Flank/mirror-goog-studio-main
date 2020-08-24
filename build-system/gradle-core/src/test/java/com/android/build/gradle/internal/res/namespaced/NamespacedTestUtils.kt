/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.res.namespaced

import com.android.ide.common.symbols.Symbol
import com.android.resources.ResourceType
import com.google.common.collect.ImmutableList
import java.io.File
import javax.tools.ToolProvider

/**
 * Compiles given Java sources and outputs them into the given java output directory.
 */
fun compileSources(sources: ImmutableList<File>, javacOutput: File) {
    val javac = ToolProvider.getSystemJavaCompiler()
    val manager = javac.getStandardFileManager(null, null, null)

    javac.getTask(
        null,
        manager, null,
        ImmutableList.of("-d", javacOutput.absolutePath), null,
        manager.getJavaFileObjectsFromFiles(sources)
    )
        .call()
}

/**
 * Creates a test only symbol with the given type and name, with a default value (empty list for
 * declare-styleables, '0' for other types). Does not verify the correctness of the resource name.
 */
fun symbol(type: String, name: String, maybeDefinition: Boolean = false): Symbol {
    val resType = ResourceType.fromClassName(type)!!
    if (resType == ResourceType.STYLEABLE) {
        return Symbol.styleableSymbol(name, ImmutableList.of(), ImmutableList.of())
    } else if (resType == ResourceType.ATTR) {
        return Symbol.attributeSymbol(name, 0, isMaybeDefinition = maybeDefinition)
    }
    return Symbol.normalSymbol(resType, name, 0)
}

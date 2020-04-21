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

package com.android.build.gradle.internal.res.shrinker.usages

import com.android.build.gradle.internal.res.shrinker.ResourceShrinkerModel
import com.android.build.gradle.internal.res.shrinker.obfuscation.ClassAndMethod
import com.android.build.gradle.internal.res.shrinker.usages.AppCompat.isAppCompatClass
import com.android.ide.common.resources.usage.ResourceUsageModel
import com.android.resources.ResourceType
import java.nio.file.Path

/**
 *  Common methods that are shared between Dex and Jvm classes usage recorders and allow to
 *  record resource usages from compiled code.
 */
internal class ClassesUsageSupport(private val model: ResourceShrinkerModel) {
    companion object {
        const val ANDROID_RES = "android_res/"
    }

    /** Returns whether the given class file name points to an aapt-generated compiled R class. */
    fun isResourceClass(internalName: String): Boolean {
        val realClassName = model.obfuscatedClasses.resolveOriginalClass(internalName.toSourceClassName())
        val lastPart = realClassName.substringAfterLast('.')
        if (lastPart.startsWith("R$")) {
            val typeName = lastPart.substring(2)
            return ResourceType.fromClassName(typeName) != null
        }
        return false
    }

    fun referencedInt(context: String, value: Int, path: Path, currentClass: String) {
        val resource = model.usageModel.getResource(value)
        if (ResourceUsageModel.markReachable(resource)) {
            model.debugReporter.debug {
                "Marking $resource reachable: referenced from $context in $path: $currentClass"
            }
        }
    }

    fun referencedStaticField(internalClassName: String, fieldName: String) {
        val realMethod = model.obfuscatedClasses.resolveOriginalMethod(
            ClassAndMethod(internalClassName.toSourceClassName(), fieldName))

        if (isValidResourceType(realMethod.className)) {
            val typePart = realMethod.className.substringAfterLast('$')
            ResourceType.fromClassName(typePart)?.let { type ->
                model.usageModel.getResource(type, realMethod.methodName)?.let {
                    ResourceUsageModel.markReachable(it)
                }
            }
        }
    }

    fun referencedString(string: String) {
        // See if the string is at all eligible; ignore strings that aren't identifiers (has java
        // identifier chars and nothing but .:/), or are empty or too long.
        // We also allow "%", used for formatting strings.
        if (string.isEmpty() || string.length > 80) {
            return
        }
        fun isSpecialCharacter(c: Char) = c == '.' || c == ':' || c == '/' || c == '%'

        if (string.all { Character.isJavaIdentifierPart(it) || isSpecialCharacter(it) } &&
            string.any { Character.isJavaIdentifierPart(it) }) {
            model.addStringConstant(string)
            model.isFoundWebContent = model.isFoundWebContent || string.contains(ANDROID_RES)
        }
    }

    fun referencedMethodInvocation(
        owner: String,
        name: String,
        desc: String,
        currentClass: String
    ) {
        if (owner == "android/content/res/Resources" &&
            name == "getIdentifier" &&
            desc == "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I"
        ) {
            // "benign" usages: don't trigger reflection mode just because the user has included
            // appcompat
            if (isAppCompatClass(currentClass.toSourceClassName(), model.obfuscatedClasses)) {
                return
            }
            model.isFoundGetIdentifier = true
            // TODO: Check previous instruction and see if we can find a literal String; if so, we
            // can more accurately dispatch the resource here rather than having to check the whole
            // string pool!
        }
        if (owner == "android/webkit/WebView" && name.startsWith("load")) {
            model.isFoundWebContent = true
        }
    }

    private fun isValidResourceType(className: String): Boolean =
        className.substringAfterLast('.').startsWith("R$")
}

internal fun String.toSourceClassName(): String {
    return this.replace('/', '.')
}

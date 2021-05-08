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

package com.android.build.api.component.analytics

import com.google.common.truth.Truth
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier
import java.net.URL
import java.util.jar.JarFile
import kotlin.test.fail

class AnalyticsEnabledGenericTest {

    @Test
    fun testAllClassesAreOpenOrAbstract() {
        val packageName = AnalyticsEnabledComponent::class.java.`package`.name
        val name = packageName.replace('.', '/')

        // Get a File object for the package
        val url: URL? = AnalyticsEnabledComponent::class.java.classLoader.getResource(
                "$name/${AnalyticsEnabledComponent::class.java.simpleName}.class")
        Truth.assertThat(url).isNotNull()
        getListOfClasses(url!!, name)
            .forEach {
                val clazz = Class.forName(it)
                if (!Modifier.isAbstract(clazz.modifiers) && Modifier.isFinal(clazz.modifiers)) {
                    fail("Class $it is neither abstract nor open.\n" +
                            "All AnalyticsEnabled types must not be final as they are " +
                            "subclassed by Gradle at runtime.")
                }
            }
        }

    private fun getListOfClasses(url: URL, packageName: String): List<String> {
        return if (url.protocol == "jar") {
            val jarFile = url.path.substring("file:".length, url.path.indexOf("!"))
            val entryNames = mutableListOf<String>()
            JarFile(jarFile).use {
                it.entries().iterator()
                        .forEach { jarEntry ->
                    if (!jarEntry.isDirectory
                            && jarEntry.name.startsWith(packageName)
                            && !jarEntry.name.contains('$')
                    ) {
                        entryNames.add(
                                jarEntry.name.dropLast(6)
                                        .replace('/', '.')
                        )
                    }
                }
            }
            entryNames
        } else {
            val directory = File(url.file).parentFile

            Truth.assertThat(directory.exists()).isTrue()

            // Get the list of the files contained in the package
            directory.walk()
                    .filter { f ->
                        f.isFile && !f.name.contains('$') && f.name.endsWith(".class")
                    }
                    .map{ packageName.plus(it.canonicalPath.removePrefix(directory.canonicalPath))
                            .dropLast(6) // remove .class
                            .replace('/', '.') }
                    .toList()
        }
    }
}

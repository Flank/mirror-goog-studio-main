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
package com.android.build.gradle.internal.instrumentation

import com.android.testutils.TestUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Type
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.FileInputStream
import java.io.ObjectInput
import java.io.ObjectOutput
import java.io.OutputStream
import java.net.URLClassLoader

class FixFramesClassWriterTest {

    private val androidJar = TestUtils.resolvePlatformPath("android.jar")

    private lateinit var fixFramesClassWriter: FixFramesClassWriterWrapper
    private lateinit var classWriter: ClassWriterWrapper

    @Before
    fun setUp() {
        fixFramesClassWriter = FixFramesClassWriterWrapper(
                ClassesHierarchyResolver.Builder(ClassesDataCache()).addDependenciesSources(androidJar.toFile()).build())
        classWriter = ClassWriterWrapper(URLClassLoader(arrayOf(androidJar.toUri().toURL()), null))
    }

    @Test
    fun testCommonSuperClassCalculation() {
        assertCommonSuperClassIsTheSame(String::class.java, CharSequence::class.java)
        assertCommonSuperClassIsTheSame(String::class.java, OutputStream::class.java)
        assertCommonSuperClassIsTheSame(FileInputStream::class.java, BufferedInputStream::class.java)
        assertCommonSuperClassIsTheSame(ObjectInput::class.java, ObjectOutput::class.java)
        assertCommonSuperClassIsTheSame(Closeable::class.java, AutoCloseable::class.java)
        assertCommonSuperClassIsTheSame(Array<String>::class.java, Array<CharSequence>::class.java)
        assertCommonSuperClassIsTheSame(Array<String>::class.java, Array<OutputStream>::class.java)
        assertCommonSuperClassIsTheSame(Array<Int>::class.java, Array<Boolean>::class.java)
        assertCommonSuperClassIsTheSame(Array<FileInputStream>::class.java,
                Array<BufferedInputStream>::class.java)
        assertCommonSuperClassIsTheSame(Array<ObjectInput>::class.java,
                Array<ObjectOutput>::class.java)
        assertCommonSuperClassIsTheSame(Array<Closeable>::class.java,
                Array<AutoCloseable>::class.java)

        assertCommonSuperClassIsTheSame(Array<Array<Array<Closeable>>>::class.java,
                Array<Array<Array<AutoCloseable>>>::class.java)
        assertCommonSuperClassIsTheSame(Array<Array<Closeable>>::class.java,
                Array<Array<Array<AutoCloseable>>>::class.java)
        assertCommonSuperClassIsTheSame(Array<Array<Array<FileInputStream>>>::class.java,
                Array<Array<Array<BufferedInputStream>>>::class.java)
        assertCommonSuperClassIsTheSame(Array<Array<Array<Int>>>::class.java,
                Array<Array<Array<Boolean>>>::class.java)
    }

    private fun assertCommonSuperClassIsTheSame(firstClass: Class<*>, secondClass: Class<*>) {
        assertThat(
                fixFramesClassWriter.findCommonSuperClass(
                        Type.getInternalName(firstClass),
                        Type.getInternalName(secondClass)
                )
        ).isEqualTo(
                classWriter.findCommonSuperClass(
                        Type.getInternalName(firstClass),
                        Type.getInternalName(secondClass)
                )
        )
    }

    private class ClassWriterWrapper(val customClassLoader: ClassLoader) : ClassWriter(0) {

        fun findCommonSuperClass(firstType: String, secondType: String): String {
            return getCommonSuperClass(firstType, secondType)
        }

        override fun getClassLoader(): ClassLoader {
            return customClassLoader
        }
    }

    private class FixFramesClassWriterWrapper(classesHierarchyResolver: ClassesHierarchyResolver) :
            FixFramesClassWriter(0, classesHierarchyResolver) {

        fun findCommonSuperClass(firstType: String, secondType: String): String {
            return getCommonSuperClass(firstType, secondType)
        }
    }
}

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
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.TraceClassVisitor
import java.io.PrintWriter
import java.io.StringWriter

fun dumpClassContent(classByteCode: ByteArray?): Array<String> {
    val classReader = ClassReader(classByteCode)
    val out = StringWriter()
    val traceClassVisitor = TraceClassVisitor(PrintWriter(out))
    classReader.accept(traceClassVisitor, 0)
    return out.toString().split("\n").toTypedArray()
}

fun getClassContentDiff(before: ByteArray?, after: ByteArray?): String {
    return TestUtils.getDiff(dumpClassContent(before), dumpClassContent(after))
}

// test data

@Target(AnnotationTarget.CLASS)
annotation class Instrument

interface I {
    fun f1()
}

@Instrument
interface InterfaceExtendsI : I {
    fun f2()
}

@Instrument
class ClassImplementsI : I {
    override fun f1() {}
    fun f2() {}
}

open class ClassWithNoInterfacesOrSuperclasses {
    fun f1() {}
}

open class ClassExtendsOneClassAndImplementsTwoInterfaces : InterfaceExtendsI,
    ClassWithNoInterfacesOrSuperclasses() {
    override fun f2() {}
    fun f3() {}
}

@Instrument
class ClassExtendsAClassThatExtendsAnotherClassAndImplementsTwoInterfaces :
    ClassExtendsOneClassAndImplementsTwoInterfaces() {
    fun f4() {}
}
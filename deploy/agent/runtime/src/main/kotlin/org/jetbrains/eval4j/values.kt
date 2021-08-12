/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.eval4j

import com.android.tools.deploy.interpreter.DoubleValue
import com.android.tools.deploy.interpreter.FloatValue
import com.android.tools.deploy.interpreter.IntValue
import com.android.tools.deploy.interpreter.LongValue
import com.android.tools.deploy.interpreter.ObjectValue
import com.android.tools.deploy.interpreter.Value
import org.jetbrains.org.objectweb.asm.Type

fun makeNotInitializedValue(t: Type): Value? {
    return when (t.sort) {
        Type.VOID -> null
        else -> NotInitialized(t)
    }
}

class NotInitialized(asmType: Type) : Value(asmType, false) {
    override fun toString() = "NotInitialized: $asmType"
}

fun boolean(v: Boolean) = IntValue(if (v) 1 else 0, Type.BOOLEAN_TYPE)
fun byte(v: Byte) = IntValue(v.toInt(), Type.BYTE_TYPE)
fun short(v: Short) = IntValue(v.toInt(), Type.SHORT_TYPE)
fun char(v: Char) = IntValue(v.toInt(), Type.CHAR_TYPE)
fun int(v: Int) = IntValue(v, Type.INT_TYPE)
fun long(v: Long) = LongValue(v)
fun float(v: Float) = FloatValue(v)
fun double(v: Double) = DoubleValue(v)

val NULL_VALUE = ObjectValue(null, Type.getObjectType("null"))

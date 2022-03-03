/*
 * Copyright (C) 2008 Google Inc.
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
package com.android.tools.perflib.heap

class RootObj @JvmOverloads constructor(
    val rootType: RootType,
    id: Long = 0,
    val thread: Int = 0,
    stack: StackTrace? = null) : Instance(id, stack) {

    fun getClassName(snapshot: Snapshot): String {
        val theClass: ClassObj? = when (rootType) {
            RootType.SYSTEM_CLASS -> snapshot.findClass(id)
            else -> snapshot.findInstance(id)!!.classObj
        }
        return theClass?.className ?: UNDEFINED_CLASS_NAME
    }

    override fun resolveReferences() { } // Do nothing.

    override fun accept(visitor: Visitor) {
        visitor.visitRootObj(this)
        val instance = referredInstance
        if (instance != null) {
            visitor.visitLater(null, instance)
        }
    }

    override fun toString() = String.format("%s@0x%08x", rootType.getName(), id)

    val referredInstance: Instance? get() = when (rootType) {
        RootType.SYSTEM_CLASS -> heap!!.mSnapshot.findClass(id)
        else -> heap!!.mSnapshot.findInstance(id)
    }

    companion object {
        const val UNDEFINED_CLASS_NAME = "no class defined!!"
    }
}

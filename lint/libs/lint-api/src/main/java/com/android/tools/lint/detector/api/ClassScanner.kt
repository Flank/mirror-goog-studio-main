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

package com.android.tools.lint.detector.api

import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

/** Specialized interface for detectors that scan bytecode / compiled class files  */
interface ClassScanner : FileScanner {

    /**
     * Returns the list of node types (corresponding to the constants in the
     * [AbstractInsnNode] class) that this scanner applies to. The
     * [.checkInstruction] method will be called for each match.
     *
     * @return an array containing all the node types this detector should be
     * called for, or null if none.
     */
    fun getApplicableAsmNodeTypes(): IntArray?

    /**
     * Process a given instruction node, and register lint issues if
     * applicable.
     *
     * @param context the context of the lint check, pointing to for example
     * the file
     * @param classNode the root class node
     * @param method the method node containing the call
     * @param instruction the actual instruction
     */
    fun checkInstruction(
        context: ClassContext,
        classNode: ClassNode,
        method: MethodNode,
        instruction: AbstractInsnNode
    )

    /**
     * Return the list of method call names (in VM format, e.g. `"<init>"` for
     * constructors, etc) for method calls this detector is interested in,
     * or null. This will be used to dispatch calls to
     * [.checkCall]
     * for only the method calls in owners that the detector is interested
     * in.
     *
     * **NOTE**: If you return non null from this method, then **only**
     * [.checkCall] will be called if a suitable method is found;
     * [.checkClass] will not be called under any circumstances.
     *
     * This makes it easy to write detectors that focus on some fixed calls,
     * and allows lint to make a single pass over the bytecode over a class,
     * and efficiently dispatch method calls to any detectors that are
     * interested in it. Without this, each new lint check interested in a
     * single method, would be doing a complete pass through all the
     * bytecode instructions of the class via the
     * [.checkClass] method, which would make
     * each newly added lint check make lint slower. Now a single dispatch
     * map is used instead, and for each encountered call in the single
     * dispatch, it looks up in the map which if any detectors are
     * interested in the given call name, and dispatches to each one in
     * turn.
     *
     * @return a list of applicable method names, or null.
     */
    fun getApplicableCallNames(): List<String>?

    /**
     * Just like [Detector.getApplicableCallNames], but for the owner
     * field instead. The [.checkCall]
     * method will be called for all [MethodInsnNode] instances where the
     * owner field matches any of the members returned in this node.
     *
     *
     * Note that if your detector provides both a name and an owner, the
     * method will be called for any nodes matching either the name **or**
     * the owner, not only where they match **both**. Note also that it will
     * be called twice - once for the name match, and (at least once) for the owner
     * match.
     *
     * @return a list of applicable owner names, or null.
     */
    fun getApplicableCallOwners(): List<String>?

    /**
     * Process a given method call node, and register lint issues if
     * applicable. This is similar to the
     * [.checkInstruction]
     * method, but has the additional advantage that it is only called for known
     * method names or method owners, according to
     * [.getApplicableCallNames] and [.getApplicableCallOwners].
     *
     * @param context the context of the lint check, pointing to for example
     * the file
     * @param classNode the root class node
     * @param method the method node containing the call
     * @param call the actual method call node
     */
    fun checkCall(
        context: ClassContext,
        classNode: ClassNode,
        method: MethodNode,
        call: MethodInsnNode
    )

    /**
     * Checks the given class' bytecode for issues.
     *
     * @param context the context of the lint check, pointing to for example
     * the file
     * @param classNode the root class node
     */
    fun checkClass(context: ClassContext, classNode: ClassNode)
}

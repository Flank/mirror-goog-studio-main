/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;

/**
 * A {@linkplain ControlFlowGraph} is a graph containing a node for each instruction in a method,
 * and an edge for each possible control flow; usually just "next" for the instruction following the
 * current instruction, but in the case of a branch such as an "if", multiple edges to each
 * successive location, or with a "goto", a single edge to the jumped-to instruction.
 *
 * <p>It also adds edges for abnormal control flow, such as the possibility of a method call
 * throwing a runtime exception.
 */
public class ControlFlowGraph {
    /** Map from instructions to nodes */
    private Map<AbstractInsnNode, Node> mNodeMap;

    private MethodNode mMethod;

    /**
     * Creates a new {@link ControlFlowGraph} and populates it with the flow control for the given
     * method. If the optional {@code initial} parameter is provided with an existing graph, then
     * the graph is simply populated, not created. This allows subclassing of the graph instance, if
     * necessary.
     *
     * @param initial usually null, but can point to an existing instance of a {@link
     *     ControlFlowGraph} in which that graph is reused (but populated with new edges)
     * @param classNode the class containing the method to be analyzed
     * @param method the method to be analyzed
     * @return a {@link ControlFlowGraph} with nodes for the control flow in the given method
     * @throws AnalyzerException if the underlying bytecode library is unable to analyze the method
     *     bytecode
     */
    @NonNull
    public static ControlFlowGraph create(
            @Nullable ControlFlowGraph initial,
            @NonNull ClassNode classNode,
            @NonNull MethodNode method)
            throws AnalyzerException {
        final ControlFlowGraph graph = initial != null ? initial : new ControlFlowGraph();
        final InsnList instructions = method.instructions;
        graph.mNodeMap = Maps.newHashMapWithExpectedSize(instructions.size());
        graph.mMethod = method;

        // Create a flow control graph using ASM5's analyzer. According to the ASM 4 guide
        // (download.forge.objectweb.org/asm/asm4-guide.pdf) there are faster ways to construct
        // it, but those require a lot more code.
        Analyzer analyzer =
                new Analyzer(new BasicInterpreter()) {
                    @Override
                    protected void newControlFlowEdge(int insn, int successor) {
                        // Update the information as of whether the this object has been
                        // initialized at the given instruction.
                        AbstractInsnNode from = instructions.get(insn);
                        AbstractInsnNode to = instructions.get(successor);
                        graph.add(from, to);
                    }

                    @Override
                    protected boolean newControlFlowExceptionEdge(int insn, TryCatchBlockNode tcb) {
                        AbstractInsnNode from = instructions.get(insn);
                        graph.exception(from, tcb);
                        return super.newControlFlowExceptionEdge(insn, tcb);
                    }

                    @Override
                    protected boolean newControlFlowExceptionEdge(int insn, int successor) {
                        AbstractInsnNode from = instructions.get(insn);
                        AbstractInsnNode to = instructions.get(successor);
                        graph.exception(from, to);
                        return super.newControlFlowExceptionEdge(insn, successor);
                    }
                };

        analyzer.analyze(classNode.name, method);
        return graph;
    }

    /**
     * A {@link Node} is a node in the control flow graph for a method, pointing to the instruction
     * and its possible successors
     */
    public static class Node {
        /** The instruction */
        public final AbstractInsnNode instruction;
        /** Any normal successors (e.g. following instruction, or goto or conditional flow) */
        public final List<Node> successors = new ArrayList<>(2);
        /** Any abnormal successors (e.g. the handler to go to following an exception) */
        public final List<Node> exceptions = new ArrayList<>(1);

        /** A tag for use during depth-first-search iteration of the graph etc */
        public int visit;

        /**
         * Constructs a new control graph node
         *
         * @param instruction the instruction to associate with this node
         */
        public Node(@NonNull AbstractInsnNode instruction) {
            this.instruction = instruction;
        }

        void addSuccessor(@NonNull Node node) {
            if (!successors.contains(node)) {
                successors.add(node);
            }
        }

        void addExceptionPath(@NonNull Node node) {
            if (!exceptions.contains(node)) {
                exceptions.add(node);
            }
        }
    }

    /** Adds an exception flow to this graph */
    protected void add(@NonNull AbstractInsnNode from, @NonNull AbstractInsnNode to) {
        getNode(from).addSuccessor(getNode(to));
    }

    /** Adds an exception flow to this graph */
    protected void exception(@NonNull AbstractInsnNode from, @NonNull AbstractInsnNode to) {
        // For now, these edges appear useless; we also get more specific
        // information via the TryCatchBlockNode which we use instead.
        // getNode(from).addExceptionPath(getNode(to));
    }

    /** Adds an exception try block node to this graph */
    protected void exception(@NonNull AbstractInsnNode from, @NonNull TryCatchBlockNode tcb) {
        // Add tcb's to all instructions in the range
        LabelNode start = tcb.start;
        LabelNode end = tcb.end; // exclusive

        // Add exception edges for all method calls in the range
        AbstractInsnNode curr = start;
        Node handlerNode = getNode(tcb.handler);
        while (curr != end && curr != null) {
            // A method can throw can exception, or a throw instruction directly
            if (curr.getType() == AbstractInsnNode.METHOD_INSN
                    || (curr.getType() == AbstractInsnNode.INSN
                            && curr.getOpcode() == Opcodes.ATHROW)) {
                // Method call; add exception edge to handler
                if (tcb.type == null) {
                    // finally block: not an exception path
                    getNode(curr).addSuccessor(handlerNode);
                }
                getNode(curr).addExceptionPath(handlerNode);
            }
            curr = curr.getNext();
        }
    }

    /**
     * Looks up (and if necessary) creates a graph node for the given instruction
     *
     * @param instruction the instruction
     * @return the control flow graph node corresponding to the given instruction
     */
    @NonNull
    public Node getNode(@NonNull AbstractInsnNode instruction) {
        Node node = mNodeMap.get(instruction);
        if (node == null) {
            node = new Node(instruction);
            mNodeMap.put(instruction, node);
        }

        return node;
    }
}

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

package com.android.tools.lint.client.api;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.ClassScanner;
import com.android.tools.lint.detector.api.Detector;
import com.google.common.annotations.Beta;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Specialized visitor for running detectors on a class object model.
 *
 * <p>It operates in two phases:
 *
 * <ol>
 *   <li>First, it computes a set of maps where it generates a map from each significant method name
 *       to a list of detectors to consult for that method name. The set of method names that a
 *       detector is interested in is provided by the detectors themselves.
 *   <li>Second, it iterates over the DOM a single time. For each method call it finds, it
 *       dispatches to any check that has registered interest in that method name.
 *   <li>Finally, it runs a full check on those class scanners that do not register specific method
 *       names to be checked. This is intended for those detectors that do custom work, not related
 *       specifically to method calls.
 * </ol>
 *
 * It also notifies all the detectors before and after the document is processed such that they can
 * do pre- and post-processing.
 *
 * <p><b>NOTE: This is not a public or final API; if you rely on this be prepared to adjust your
 * code for the next tools release.</b>
 */
@Beta
class AsmVisitor {
    /**
     * Number of distinct node types specified in {@link AbstractInsnNode}. Sadly there isn't a
     * max-constant there, so update this along with ASM library updates.
     */
    private static final int TYPE_COUNT = AbstractInsnNode.LINE + 1;

    private final Map<String, List<ClassScanner>> methodNameToChecks = new HashMap<>();
    private final Map<String, List<ClassScanner>> methodOwnerToChecks = new HashMap<>();
    private final List<Detector> fullClassChecks = new ArrayList<>();

    private final List<? extends Detector> allDetectors;
    private List<ClassScanner>[] nodeTypeDetectors;

    // Really want this:
    //<T extends List<Detector> & ClassScanner> ClassVisitor(T xmlDetectors) {
    // but it makes client code tricky and ugly.
    @SuppressWarnings("unchecked")
    AsmVisitor(@NonNull LintClient client, @NonNull List<? extends Detector> classDetectors) {
        allDetectors = classDetectors;

        // TODO: Check appliesTo() for files, and find a quick way to enable/disable
        // rules when running through a full project!
        for (Detector detector : classDetectors) {
            ClassScanner scanner = (ClassScanner) detector;

            boolean checkFullClass = true;

            Collection<String> names = scanner.getApplicableCallNames();
            if (names != null) {
                checkFullClass = false;
                for (String element : names) {
                    List<ClassScanner> list = methodNameToChecks.get(element);
                    if (list == null) {
                        list = new ArrayList<>();
                        methodNameToChecks.put(element, list);
                    }
                    list.add(scanner);
                }
            }

            Collection<String> owners = scanner.getApplicableCallOwners();
            if (owners != null) {
                checkFullClass = false;
                for (String element : owners) {
                    List<ClassScanner> list = methodOwnerToChecks.get(element);
                    if (list == null) {
                        list = new ArrayList<>();
                        methodOwnerToChecks.put(element, list);
                    }
                    list.add(scanner);
                }
            }

            int[] types = scanner.getApplicableAsmNodeTypes();
            if (types != null) {
                checkFullClass = false;
                for (int type : types) {
                    if (type < 0 || type >= TYPE_COUNT) {
                        // Can't support this node type: looks like ASM wasn't updated correctly.
                        client.log(
                                null,
                                "Out of range node type %1$d from detector %2$s",
                                type,
                                scanner);
                        continue;
                    }
                    if (nodeTypeDetectors == null) {
                        nodeTypeDetectors = new List[TYPE_COUNT];
                    }
                    List<ClassScanner> checks = nodeTypeDetectors[type];
                    if (checks == null) {
                        checks = new ArrayList<>();
                        nodeTypeDetectors[type] = checks;
                    }
                    checks.add(scanner);
                }
            }

            if (checkFullClass) {
                fullClassChecks.add(detector);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    // ASM API uses raw types
    void runClassDetectors(ClassContext context) {
        ClassNode classNode = context.getClassNode();

        for (Detector detector : allDetectors) {
            detector.beforeCheckFile(context);
        }

        for (Detector detector : fullClassChecks) {
            ClassScanner scanner = (ClassScanner) detector;
            scanner.checkClass(context, classNode);
            detector.afterCheckFile(context);
        }

        if (!methodNameToChecks.isEmpty()
                || !methodOwnerToChecks.isEmpty()
                || nodeTypeDetectors != null && nodeTypeDetectors.length > 0) {
            List methodList = classNode.methods;
            for (Object m : methodList) {
                MethodNode method = (MethodNode) m;
                InsnList nodes = method.instructions;
                for (int i = 0, n = nodes.size(); i < n; i++) {
                    AbstractInsnNode instruction = nodes.get(i);
                    int type = instruction.getType();
                    if (type == AbstractInsnNode.METHOD_INSN) {
                        MethodInsnNode call = (MethodInsnNode) instruction;

                        String owner = call.owner;
                        List<ClassScanner> scanners = methodOwnerToChecks.get(owner);
                        if (scanners != null) {
                            for (ClassScanner scanner : scanners) {
                                scanner.checkCall(context, classNode, method, call);
                            }
                        }

                        String name = call.name;
                        scanners = methodNameToChecks.get(name);
                        if (scanners != null) {
                            for (ClassScanner scanner : scanners) {
                                scanner.checkCall(context, classNode, method, call);
                            }
                        }
                    }

                    if (nodeTypeDetectors != null && type < nodeTypeDetectors.length) {
                        List<ClassScanner> scanners = nodeTypeDetectors[type];
                        if (scanners != null) {
                            for (ClassScanner scanner : scanners) {
                                scanner.checkInstruction(context, classNode, method, instruction);
                            }
                        }
                    }
                }
            }
        }

        for (Detector detector : allDetectors) {
            detector.afterCheckFile(context);
        }
    }
}

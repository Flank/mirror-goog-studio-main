/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.deploy.liveedit;

import com.android.annotations.VisibleForTesting;
import com.android.deploy.asm.Type;
import com.android.deploy.asm.tree.AbstractInsnNode;
import com.android.deploy.asm.tree.FieldNode;
import com.android.deploy.asm.tree.LineNumberNode;
import com.android.deploy.asm.tree.MethodNode;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Contains logic to verify that classes passed to LiveEdit have only been modified in ways
 * supported by the LE interpreter/runtime. Updates to classes are compared with the version of the
 * class currently in the VM to ensure that all changes are supported.
 *
 * <p>The current set of checks ensures: - No methods have been added or removed - No method
 * signatures have been changed - No new classes have been added - No fields have been added or
 * removed - the inheritance hierarchy has not been changed.
 */
public class BytecodeValidator {
    static List<UnsupportedChange> validateBytecode(Interpretable bytecode, ClassLoader loader) {
        String className = bytecode.getInternalName().replace('/', '.');
        ArrayList<UnsupportedChange> errors = new ArrayList<>();
        try {
            Class<?> original = Class.forName(className, true, loader);
            errors.addAll(validateBytecode(bytecode, original));
        } catch (ClassNotFoundException e) {
            UnsupportedChange change = new UnsupportedChange();
            change.type = UnsupportedChange.Type.ADDED_CLASS.name();
            change.className = className;
            change.fileName = bytecode.getFilename();
            errors.add(change);
        }

        return errors;
    }

    @VisibleForTesting
    static List<UnsupportedChange> validateBytecode(Interpretable bytecode, Class<?> original) {
        ArrayList<UnsupportedChange> errors = new ArrayList<>();
        validateMethods(bytecode, original, errors);
        validateFields(bytecode, original, errors);
        validateInheritance(bytecode, original, errors);
        return errors;
    }

    private static void validateMethods(
            Interpretable bytecode, Class<?> original, ArrayList<UnsupportedChange> errors) {
        String className = bytecode.getInternalName().replace('/', '.');
        Map<String, Executable> originalMethods = new HashMap<>();

        Arrays.stream(original.getDeclaredMethods())
                .forEach(m -> originalMethods.put(m.getName() + Type.getMethodDescriptor(m), m));

        Arrays.stream(original.getDeclaredConstructors())
                .forEach(c -> originalMethods.put("<init>" + Type.getConstructorDescriptor(c), c));

        Map<String, MethodNode> newMethods =
                bytecode.getMethods().stream()
                        .collect(Collectors.toMap(m -> m.name + m.desc, m -> m));

        for (Map.Entry<String, Executable> entry : originalMethods.entrySet()) {
            if (isLikelySynthetic(entry.getKey())) {
                continue;
            }

            MethodNode newMethod = newMethods.get(entry.getKey());
            if (newMethod == null) {
                UnsupportedChange change = new UnsupportedChange();
                change.type = UnsupportedChange.Type.REMOVED_METHOD.name();
                change.className = className;
                change.targetName = entry.getKey();
                change.fileName = bytecode.getFilename();
                errors.add(change);
            }
        }

        for (String method : newMethods.keySet()) {
            // We have no way of finding the static initializer in the original class, so we'll
            // always flag <clinit> as 'added' unless we skip it.
            if (method.startsWith("<clinit>")) {
                continue;
            }
            if (!originalMethods.containsKey(method) && !isLikelySynthetic(method)) {
                UnsupportedChange change = new UnsupportedChange();
                change.type = UnsupportedChange.Type.ADDED_METHOD.name();
                change.className = className;
                change.targetName = method;
                change.fileName = bytecode.getFilename();
                change.lineNumber = getLineNumber((newMethods.get(method)));
                errors.add(change);
            }
        }
    }

    private static void validateFields(
            Interpretable bytecode, Class<?> original, ArrayList<UnsupportedChange> errors) {
        String className = bytecode.getInternalName().replace('/', '.');

        Map<String, Field> originalFields =
                Arrays.stream(original.getDeclaredFields())
                        .collect(Collectors.toMap(Field::getName, Function.identity()));

        Map<String, FieldNode> newFields =
                bytecode.getFields().stream()
                        .collect(Collectors.toMap(f -> f.name, Function.identity()));

        for (Map.Entry<String, Field> entry : originalFields.entrySet()) {
            if (isLikelySynthetic(entry.getKey())) {
                continue;
            }

            Field originalField = entry.getValue();
            FieldNode newField = newFields.get(entry.getKey());
            if (newField == null) {
                UnsupportedChange change = new UnsupportedChange();
                change.type = UnsupportedChange.Type.REMOVED_FIELD.name();
                change.className = className;
                change.targetName = entry.getKey();
                change.fileName = bytecode.getFilename();
                errors.add(change);
                continue;
            }

            boolean isSameType = newField.desc.equals(Type.getDescriptor(originalField.getType()));
            boolean hasSameAccess = newField.access == originalField.getModifiers();
            if (!isSameType || !hasSameAccess) {
                UnsupportedChange change = new UnsupportedChange();
                change.type = UnsupportedChange.Type.MODIFIED_FIELD.name();
                change.className = className;
                change.targetName = entry.getKey();
                change.fileName = bytecode.getFilename();
                errors.add(change);
            }
        }

        for (String field : newFields.keySet()) {
            if (!originalFields.containsKey(field) && !isLikelySynthetic(field)) {
                UnsupportedChange change = new UnsupportedChange();
                change.type = UnsupportedChange.Type.ADDED_FIELD.name();
                change.className = className;
                change.targetName = field;
                change.fileName = bytecode.getFilename();
                errors.add(change);
            }
        }
    }

    private static void validateInheritance(
            Interpretable bytecode, Class<?> clazz, ArrayList<UnsupportedChange> errors) {
        String className = bytecode.getInternalName().replace('/', '.');

        if (!Type.getInternalName(clazz.getSuperclass()).equals(bytecode.getSuperName())) {
            UnsupportedChange change = new UnsupportedChange();
            change.type = UnsupportedChange.Type.MODIFIED_SUPER.name();
            change.className = className;
            change.targetName = bytecode.getSuperName();
            change.fileName = bytecode.getFilename();
            errors.add(change);
        }

        Set<String> originalInterfaces =
                Arrays.stream(clazz.getInterfaces())
                        .map(Type::getInternalName)
                        .collect(Collectors.toSet());
        Set<String> newInterfaces =
                Arrays.stream(bytecode.getInterfaces()).collect(Collectors.toSet());

        for (String original : originalInterfaces) {
            if (!newInterfaces.contains(original)) {
                UnsupportedChange change = new UnsupportedChange();
                change.type = UnsupportedChange.Type.REMOVED_INTERFACE.name();
                change.className = className;
                change.targetName = original;
                change.fileName = bytecode.getFilename();
                errors.add(change);
            }
        }

        for (String next : newInterfaces) {
            if (!originalInterfaces.contains(next)) {
                UnsupportedChange change = new UnsupportedChange();
                change.type = UnsupportedChange.Type.ADDED_INTERFACE.name();
                change.className = className;
                change.targetName = next;
                change.fileName = bytecode.getFilename();
                errors.add(change);
            }
        }
    }

    private static int getLineNumber(MethodNode node) {
        for (AbstractInsnNode instr : node.instructions) {
            if (instr instanceof LineNumberNode) {
                return ((LineNumberNode) instr).line;
            }
        }
        return -1;
    }

    private static boolean isLikelySynthetic(String methodDesc) {
        return methodDesc.contains("$");
    }

    // In a perfect world, we'd just use the proto for all of this. However, using the proto across
    // the jni -> java boundary in the agent caused issues with the proto classes not being found;
    // if we had infinite time to troubleshoot that, maybe we could resolve it, but we don't, so
    // this is good enough for now.
    public static class UnsupportedChange {
        // Must be kept in sync with the conversion map in agent/native/live_edit.cc as well as the
        // enum in the UnsupportedChange proto. This is because we need to use strings to convert
        // from the java enum to the c++ proto enum; relying on ordinal means future metrics might
        // break if enums are re-ordered.
        public enum Type {
            UNKNOWN,
            ADDED_METHOD,
            REMOVED_METHOD,
            ADDED_CLASS,
            ADDED_FIELD,
            REMOVED_FIELD,
            MODIFIED_FIELD,
            MODIFIED_SUPER,
            ADDED_INTERFACE,
            REMOVED_INTERFACE,
        }

        public String type;
        public String className;
        public String targetName;
        public String fileName;
        public int lineNumber;
    }
}

/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.testutils.truth;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.jf.dexlib2.DebugItemType;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedField;
import org.jf.dexlib2.dexbacked.DexBackedMethod;
import org.jf.dexlib2.iface.debug.DebugItem;

@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public class DexClassSubject extends Subject<DexClassSubject, DexBackedClassDef> {

    public static final SubjectFactory<DexClassSubject, DexBackedClassDef> FACTORY
            = new SubjectFactory<DexClassSubject, DexBackedClassDef>() {
        @Override
        public DexClassSubject getSubject(
                @NonNull FailureStrategy failureStrategy,
                @Nullable DexBackedClassDef subject) {
            return new DexClassSubject(failureStrategy, subject);
        }
    };

    private DexClassSubject(
            @NonNull FailureStrategy failureStrategy,
            @Nullable DexBackedClassDef subject) {
        super(failureStrategy, subject);
    }

    public void hasSuperclass(@NonNull String name) {
        if (assertSubjectIsNonNull() && !name.equals(actual().getSuperclass())) {
            fail("has superclass", name);
        }
    }

    public void hasMethod(@NonNull String name) {
        if (assertSubjectIsNonNull() && !checkHasMethod(name)) {
            fail("contains method", name);
        }
    }

    public void hasMethods(@NonNull String... names) {
        if (assertSubjectIsNonNull()) {
            for (String name : names) {
                hasMethod(name);
            }
        }
    }

    public void hasMethodWithLineInfoCount(@NonNull String name, int lineInfoCount) {
        assertSubjectIsNonNull();
        for (DexBackedMethod method : getSubject().getMethods()) {
            if (method.getName().equals(name)) {
                if (method.getImplementation() == null) {
                    fail("contain method implementation for method " + name);
                    return;
                }
                int actualLineCnt = 0;
                for (DebugItem debugItem : method.getImplementation().getDebugItems()) {
                    if (debugItem.getDebugItemType() == DebugItemType.LINE_NUMBER) {
                        actualLineCnt++;
                    }
                }
                if (actualLineCnt != lineInfoCount) {
                    fail(
                            "method has "
                                    + lineInfoCount
                                    + " debug items, "
                                    + actualLineCnt
                                    + " are found.");
                }
                return;
            }
        }
        fail("contains method", name);
    }

    public void hasExactFields(@NonNull Set<String> names) {
        if (assertSubjectIsNonNull() && !checkHasExactFields(names)) {
            fail("Expected exactly " + names + " fields but have " + getAllFieldNames());
        }
    }

    public void hasField(@NonNull String name) {
        if (assertSubjectIsNonNull() && !checkHasField(name)) {
            fail("contains field", name);
        }
    }

    public void hasFieldWithType(@NonNull String name, @NonNull String type) {
        if (assertSubjectIsNonNull() && !checkHasField(name, type)) {
            fail("contains field ", name + ":" + type);
        }
    }

    public void doesNotHaveField(@NonNull String name) {
        if (assertSubjectIsNonNull() && checkHasField(name)) {
            fail("does not contain field", name);
        }
    }

    public void doesNotHaveFieldWithType(@NonNull String name, @NonNull String type) {
        if (assertSubjectIsNonNull() && checkHasField(name, type)) {
            fail("does not contain field ", name + ":" + type);
        }
    }

    public void doesNotHaveMethod(@NonNull String name) {
        if (assertSubjectIsNonNull() && checkHasMethod(name)) {
            fail("does not contain method", name);
        }
    }

    public void hasAnnotations() {
        if (assertSubjectIsNonNull() && !checkHasAnnotations()) {
            fail("has annotations");
        }
    }

    public void doesNotHaveAnnotations() {
        if (assertSubjectIsNonNull() && checkHasAnnotations()) {
            fail(" does not have annotations");
        }
    }

    private boolean checkHasAnnotations() {
        return !actual().getAnnotations().isEmpty();
    }

    /** Check if the class has method with the specified name. */
    private boolean checkHasMethod(@NonNull String name) {
        for (DexBackedMethod method : getSubject().getMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Check if the class has field with the specified name. */
    private boolean checkHasField(@NonNull String name) {
        for (DexBackedField field : getSubject().getFields()) {
            if (field.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Check if the class has field with the specified name and type. */
    private boolean checkHasField(@NonNull String name, @NonNull String type) {
        for (DexBackedField field : getSubject().getFields()) {
            if (field.getName().equals(name) && field.getType().equals(type)) {
                return true;
            }
        }
        return false;
    }

    /** Checks the subject has the given fields and no other fields. */
    private boolean checkHasExactFields(@NonNull Set<String> names) {
        return getAllFieldNames().equals(names);
    }

    /** Returns all of the field names */
    private Set<String> getAllFieldNames() {
        return StreamSupport.stream(getSubject().getFields().spliterator(), false)
                .map(DexBackedField::getName)
                .collect(Collectors.toSet());
    }

    private boolean assertSubjectIsNonNull() {
        if (getSubject() == null) {
            fail("Cannot assert about the contents of a dex class that does not exist.");
            return false;
        }
        return true;
    }

    @Override
    protected String getDisplaySubject() {
        String subjectName = null;
        if (getSubject() != null) {
            subjectName = getSubject().getType();
        }
        if (internalCustomName() != null) {
            return internalCustomName() + " (<" + subjectName + ">)";
        } else {
            return "<" + subjectName + ">";
        }
    }
}

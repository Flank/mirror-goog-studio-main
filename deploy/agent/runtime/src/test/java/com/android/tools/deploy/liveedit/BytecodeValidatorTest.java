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

import static com.android.tools.deploy.liveedit.Utils.buildClass;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;

public class BytecodeValidatorTest {
    static {
        LiveEditStubs.init(BytecodeValidatorTest.class.getClassLoader());
    }

    @Test
    public void testMethodValidation() throws Exception {
        // Note: we're using B's bytecode to compare to A.class, so all error messages are expected
        // to refer to "B" as the class name.
        List<BytecodeValidator.UnsupportedChange> errors =
                BytecodeValidator.validateBytecode(new Interpretable(buildClass(B.class)), A.class);
        Assert.assertEquals(2, errors.size());

        Optional<BytecodeValidator.UnsupportedChange> addedMethod =
                errors.stream()
                        .filter(
                                e ->
                                        e.type.equals(
                                                BytecodeValidator.UnsupportedChange.Type
                                                        .ADDED_METHOD
                                                        .name()))
                        .findFirst();
        Assert.assertTrue(addedMethod.isPresent());
        Assert.assertEquals(
                "com.android.tools.deploy.liveedit.BytecodeValidatorTest$B",
                addedMethod.get().className);
        Assert.assertEquals("method(I)V", addedMethod.get().targetName);
        Assert.assertEquals("BytecodeValidatorTest.java", addedMethod.get().fileName);

        Optional<BytecodeValidator.UnsupportedChange> removedMethod =
                errors.stream()
                        .filter(
                                e ->
                                        e.type.equals(
                                                BytecodeValidator.UnsupportedChange.Type
                                                        .REMOVED_METHOD
                                                        .name()))
                        .findFirst();
        Assert.assertTrue(removedMethod.isPresent());
        Assert.assertEquals(
                "com.android.tools.deploy.liveedit.BytecodeValidatorTest$B",
                removedMethod.get().className);
        Assert.assertEquals("method()V", removedMethod.get().targetName);
        Assert.assertEquals("BytecodeValidatorTest.java", removedMethod.get().fileName);
    }

    @Test
    public void testFieldValidation() throws Exception {
        // Note: we're using D's bytecode to compare to C.class, so all error messages are expected
        // to refer to "D" as the class name.
        List<BytecodeValidator.UnsupportedChange> errors =
                BytecodeValidator.validateBytecode(new Interpretable(buildClass(D.class)), C.class);
        Assert.assertEquals(5, errors.size());

        BytecodeValidator.UnsupportedChange field1 = getChangeByName(errors, "field1");
        Assert.assertEquals(
                BytecodeValidator.UnsupportedChange.Type.MODIFIED_FIELD.name(), field1.type);
        Assert.assertEquals(
                "com.android.tools.deploy.liveedit.BytecodeValidatorTest$D", field1.className);

        BytecodeValidator.UnsupportedChange field2 = getChangeByName(errors, "field2");
        Assert.assertEquals(
                BytecodeValidator.UnsupportedChange.Type.MODIFIED_FIELD.name(), field2.type);
        Assert.assertEquals(
                "com.android.tools.deploy.liveedit.BytecodeValidatorTest$D", field2.className);

        BytecodeValidator.UnsupportedChange field3 = getChangeByName(errors, "field3");
        Assert.assertEquals(
                BytecodeValidator.UnsupportedChange.Type.REMOVED_FIELD.name(), field3.type);
        Assert.assertEquals(
                "com.android.tools.deploy.liveedit.BytecodeValidatorTest$D", field3.className);

        BytecodeValidator.UnsupportedChange field4 = getChangeByName(errors, "field4");
        Assert.assertEquals(
                BytecodeValidator.UnsupportedChange.Type.ADDED_FIELD.name(), field4.type);
        Assert.assertEquals(
                "com.android.tools.deploy.liveedit.BytecodeValidatorTest$D", field4.className);

        BytecodeValidator.UnsupportedChange field5 = getChangeByName(errors, "field5");
        Assert.assertEquals(
                BytecodeValidator.UnsupportedChange.Type.MODIFIED_FIELD.name(), field5.type);
        Assert.assertEquals(
                "com.android.tools.deploy.liveedit.BytecodeValidatorTest$D", field5.className);
    }

    @Test
    public void testInheritanceValidation() throws Exception {
        // Note: we're using F's bytecode to compare to E.class, so all error messages are expected
        // to refer to "F" as the class name.
        List<BytecodeValidator.UnsupportedChange> errors =
                BytecodeValidator.validateBytecode(new Interpretable(buildClass(F.class)), E.class);

        Assert.assertEquals(2, errors.size());
        Assert.assertTrue(
                errors.stream()
                        .anyMatch(
                                e ->
                                        e.type.equals(
                                                        BytecodeValidator.UnsupportedChange.Type
                                                                .ADDED_INTERFACE
                                                                .name())
                                                && e.className.equals(
                                                        "com.android.tools.deploy.liveedit.BytecodeValidatorTest$F")));
        Assert.assertTrue(
                errors.stream()
                        .anyMatch(
                                e ->
                                        e.type.equals(
                                                        BytecodeValidator.UnsupportedChange.Type
                                                                .MODIFIED_SUPER
                                                                .name())
                                                && e.className.equals(
                                                        "com.android.tools.deploy.liveedit.BytecodeValidatorTest$F")));
    }

    private static BytecodeValidator.UnsupportedChange getChangeByName(
            Collection<BytecodeValidator.UnsupportedChange> errors, String targetName) {
        Optional<BytecodeValidator.UnsupportedChange> error =
                errors.stream().filter(e -> e.targetName.equals(targetName)).findFirst();
        Assert.assertTrue(error.isPresent());
        return error.get();
    }

    class A {
        void method() {}
    }

    class B {
        void method(int x) {}
    }

    static class C {
        int field1;
        String field2;
        double field3;
        int field5;
    }

    static class D {
        String field1;
        private String field2;
        double field4;
        static int field5;
    }

    interface I1 {}

    interface I2 {}

    static class E extends D implements I1 {}

    static class F extends C implements I1, I2 {}
}

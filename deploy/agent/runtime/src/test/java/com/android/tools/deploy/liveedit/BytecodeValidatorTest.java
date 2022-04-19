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

import java.util.List;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;

public class BytecodeValidatorTest {
    static {
        LiveEditStubs.init(BytecodeValidatorTest.class.getClassLoader());
    }

    @Test
    public void testValidation() throws Exception {
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
        Assert.assertEquals("method(I)V", addedMethod.get().methodName);
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
        Assert.assertEquals("method()V", removedMethod.get().methodName);
        Assert.assertEquals("BytecodeValidatorTest.java", removedMethod.get().fileName);
    }

    class A {
        void method() {}
    }

    class B {
        void method(int x) {}
    }
}

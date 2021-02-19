/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.testutils.ignore;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.annotation.Annotation;

import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Tests {@link IgnoreTestRule}.
 */
public class IgnoreTestRuleTest {

    private static final Annotation IGNORE_WITH_CONDITION_TRUE_ANNOTATION;

    static {
        try {
            IGNORE_WITH_CONDITION_TRUE_ANNOTATION
                    = IgnoreTestRuleTest.class.getDeclaredMethod("ignoredMethod")
                    .getAnnotation(IgnoreWithCondition.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private TestStatement statement;

    @Before
    public void buildStatement() {
        statement = new TestStatement();
    }

    @Test
    public void apply_ignoreWithConditionTrue_skipsTest() throws Throwable {
        IgnoreTestRule ignoreTestRule = new IgnoreTestRule();
        Description annotatedTestCase = Description.createTestDescription("FooClass", "methodBar",
                IGNORE_WITH_CONDITION_TRUE_ANNOTATION);

        try {
            ignoreTestRule.apply(statement, annotatedTestCase).evaluate();
            fail("Expected AssumptionViolatedException to be thrown.");
        } catch (AssumptionViolatedException ignored) {
            // expected
        }
    }

    @Test
    public void apply_ignoreWithConditionFalse_runsTest() throws Throwable {
        IgnoreTestRule ignoreTestRule = new IgnoreTestRule();
        Description annotatedTestCase = Description.createTestDescription("FooClass", "methodBar",
                IGNORE_WITH_CONDITION_TRUE_ANNOTATION);

        ignoreTestRule.apply(statement, annotatedTestCase).evaluate();
        assertTrue(statement.evaluated);
    }

    @Test
    public void apply_usingAllTestsMatchingCondition_ignoresTest() throws Throwable {
        IgnoreTestRule
                ignoreTestRule
                = IgnoreTestRule.allTestsMatching(IgnoreConditionAlwaysTrue.class);
        Description unannotatedTestCase = Description.createTestDescription("FooClass",
                "methodBar");

        try {
            ignoreTestRule.apply(statement, unannotatedTestCase).evaluate();
            fail("Expected AssumptionViolatedException to be thrown.");
        } catch (AssumptionViolatedException ignored) {
            // expected
        }
    }

    @Test
    public void apply_unannotatedTest_runsTest() throws Throwable {
        IgnoreTestRule ignoreTestRule = new IgnoreTestRule();
        Description unannotatedTestCase = Description.createTestDescription("FooClass",
                "methodBar");

        ignoreTestRule.apply(statement, unannotatedTestCase).evaluate();
        assertTrue(statement.evaluated);
    }

    @IgnoreWithCondition(reason = "false condition test", condition = IgnoreConditionAlwaysFalse.class)
    private void annotatedMethod() {
    }

    @IgnoreWithCondition(reason = "ignored test", condition = IgnoreConditionAlwaysTrue.class)
    private void ignoredMethod() {
    }

    static class IgnoreConditionAlwaysTrue implements IgnoreCondition {

        @Override
        public boolean present() {
            return true;
        }
    }

    static class IgnoreConditionAlwaysFalse implements IgnoreCondition {

        @Override
        public boolean present() {
            return false;
        }
    }

    static class TestStatement extends Statement {

        boolean evaluated;

        @Override
        public void evaluate() throws Throwable {
            evaluated = true;
        }
    }
}

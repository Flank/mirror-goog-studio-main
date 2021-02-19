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

import org.junit.Assume;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Allows ignoring of test methods using {@link IgnoreWithCondition}, and all tests within a class
 * using {@link #allTestsMatching(Class)}.
 */
public class IgnoreTestRule implements TestRule {
    private final Class<? extends IgnoreCondition> ignoreConditionClass;

    public static IgnoreTestRule allTestsMatching(Class<? extends IgnoreCondition> ignoreConditionClass) {
        return new IgnoreTestRule(ignoreConditionClass);
    }

    IgnoreTestRule(Class<? extends IgnoreCondition> ignoreConditionClass) {
        this.ignoreConditionClass = ignoreConditionClass;
    }

    public IgnoreTestRule() {
        this.ignoreConditionClass = null;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                if (ignoreConditionClass != null) {
                    Assume.assumeFalse(shouldIgnore(ignoreConditionClass));
                }
                IgnoreWithCondition annotation = description.getAnnotation(IgnoreWithCondition.class);
                if (annotation != null) {
                    Assume.assumeFalse(shouldIgnore(annotation.condition()));
                }
                base.evaluate();
            }
        };
    }

    private static boolean shouldIgnore(Class<? extends IgnoreCondition> ignorableConditionClass) {
        try {
            return ignorableConditionClass.newInstance().present();
        } catch (IllegalAccessException | InstantiationException e) {
            throw new RuntimeException(e);
        }
    }
}

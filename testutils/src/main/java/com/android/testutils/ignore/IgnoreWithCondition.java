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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a JUnit4 test to be conditionally ignored. Requires using {@link IgnoreTestRule} to take
 * effect.
 *
 * <p>Example usage:
 * <pre>{@code
 *  @Rule
 *  public Rule ignoreTests = new IgnoreTestRule();
 *
 * @Test
 * @IgnoreWithCondition(reason = "b/00000000", condition = OnWindows.class)
 * public void foo_succeeds() {
 *     // test content
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface IgnoreWithCondition {
    /** The reason why a test needs to be ignored. */
    String reason() default "";

    /** The condition indicating whether a test should be ignored. */
    Class<? extends IgnoreCondition> condition();
}

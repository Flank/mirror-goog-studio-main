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
package com.android.java.model.builder;

import static com.android.java.model.builder.JavaModelBuilder.isGradleAtLeast;

import org.junit.Assert;
import org.junit.Test;

/** Test class for {@link JavaModelBuilder}. */
public class JavaModelBuilderTest {
    @Test
    public void gradleVersionCheck() {
        Assert.assertFalse(isGradleAtLeast("2.2", "2.12"));
        Assert.assertFalse(isGradleAtLeast("2.2", "2.6"));
        Assert.assertTrue(isGradleAtLeast("4.2", "2.6"));
        Assert.assertTrue(isGradleAtLeast("4.2", "2.12"));
        Assert.assertTrue(isGradleAtLeast("4.2", "4.2"));
    }
}

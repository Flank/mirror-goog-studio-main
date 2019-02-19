/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.deployer;

import org.junit.Assert;
import org.junit.Test;

public class DeployerErrorMessagePresenterTest {

    @Test
    public void testJvmtiFailure() {
        DeployerException deployerException =
                DeployerException.jvmtiError(
                        "JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_MODIFIERS_CHANGED");
        Assert.assertEquals(
                deployerException.getError(),
                DeployerException.Error.CANNOT_CHANGE_METHOD_MODIFIERS);
    }

    @Test
    public void testInternalFailure() {
        DeployerException deployerException = DeployerException.dumpFailed("ABCD");
        String message = deployerException.getDetails();
        Assert.assertTrue(message.contains("ABCD"));
    }
}

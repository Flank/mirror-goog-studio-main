/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.internal.fixtures.FakeObjectFactory;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.internal.services.FakeServices;
import com.android.testutils.internal.CopyOfTester;
import groovy.util.Eval;
import org.junit.Test;

public class SigningConfigTest {

    @Test
    public void testInitWith() throws Exception {
        CopyOfTester.assertAllGettersCalled(
                SigningConfig.class,
                signingConfig("original"),
                original -> {
                    // Manually call getters that are not called by _initWith:
                    original.getName();
                    original.isSigningReady();

                    signingConfig("copy").initWith(original);
                });
    }

    @Test
    public void testGroovyInitWith() throws Exception {
        SigningConfig original = signingConfig("original");
        original.setEnableV1Signing(false);
        SigningConfig copy =signingConfig("copy");
        // Check that groovy can invoke initWith
        Eval.xy(copy, original, "x.initWith(y)");
        assertThat(copy.getEnableV1Signing()).isFalse();
    }

    private SigningConfig signingConfig(String name) {
        DslServices dslServices = FakeServices.createDslServices();
        return dslServices.newDecoratedInstance(SigningConfig.class, name, dslServices);
    }
}

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

package com.android.build.gradle.integration.instantapp;

import com.android.build.gradle.integration.common.category.SmokeTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.integration.common.truth.AtomBundleSubject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Tests that non-default variants for atoms work as expected. */
@Category(SmokeTests.class)
public class NonDefaultVariantAtomTest {
    @ClassRule
    public static GradleTestProject sProject =
            GradleTestProject.builder()
                    .fromTestProject("publishNonDefaultAtom")
                    .withoutNdk()
                    .create();

    @AfterClass
    public static void cleanUp() {
        sProject = null;
    }

    @Test
    public void build() throws Exception {
        // TODO: add feature-split support for AAPT2
        sProject.executor().withEnabledAapt2(false).run("clean");
        sProject.executor().withEnabledAapt2(false).run(":instantApp:assembleRelease");

        // Tests that the proper sources are compiled.
        AtomBundleSubject releaseAtomBundle =
                TruthHelper.assertThat(sProject.getSubproject("atom").getAtomBundle("release"));
        releaseAtomBundle.containsClass("Lcom/android/tests/publishatom/atom/AtomRelease;");
        releaseAtomBundle.doesNotContainClass("Lcom/android/tests/publishatom/atom/AtomDebug;");

        ApkSubject releaseAtom =
                TruthHelper.assertThat(
                        sProject.getSubproject("instantApp").getAtom("atom", "release"));
        releaseAtom.containsClass("Lcom/android/tests/publishatom/atom/AtomRelease;");
        releaseAtom.doesNotContainClass("Lcom/android/tests/publishatom/atom/AtomDebug;");

        // Same tests for the debug variant.
        sProject.executor().withEnabledAapt2(false).run(":instantApp:assembleDebug");

        AtomBundleSubject debugAtomBundle =
                TruthHelper.assertThat(sProject.getSubproject("atom").getAtomBundle("debug"));
        debugAtomBundle.containsClass("Lcom/android/tests/publishatom/atom/AtomDebug;");
        debugAtomBundle.doesNotContainClass("Lcom/android/tests/publishatom/atom/AtomRelease;");

        ApkSubject debugAtom =
                TruthHelper.assertThat(
                        sProject.getSubproject("instantApp").getAtom("atom", "debug"));
        debugAtom.containsClass("Lcom/android/tests/publishatom/atom/AtomDebug;");
        debugAtom.doesNotContainClass("Lcom/android/tests/publishatom/atom/AtomRelease;");
    }
}

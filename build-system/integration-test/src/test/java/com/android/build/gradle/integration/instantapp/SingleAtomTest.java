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

package com.android.build.gradle.integration.instantapp;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.testutils.truth.MoreTruth.assertThatZip;

import com.android.build.gradle.integration.common.category.SmokeTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.integration.common.truth.AtomBundleSubject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.testutils.truth.ZipFileSubject;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Basic instantApp test with a single atom. */
@Category(SmokeTests.class)
public class SingleAtomTest {
    @ClassRule
    public static GradleTestProject sProject = GradleTestProject.builder()
            .fromTestProject("singleAtom")
            .withoutNdk()
            .create();

    @AfterClass
    public static void cleanUp() {
        sProject = null;
    }

    @Test
    public void build() throws Exception {
        sProject.execute("clean");
        sProject.execute(":instantApp:assembleRelease");

        // Tests that the BuildConfig and R class are generated in the proper package.
        AtomBundleSubject atomBundle =
                TruthHelper.assertThat(sProject.getSubproject("atom").getAtomBundle("release"));
        atomBundle.containsClass("Lcom/android/tests/singleatom/BuildConfig;");
        atomBundle.containsClass("Lcom/android/tests/singleatom/R;");
        atomBundle.doesNotContainClass("Lcom/android/tests/singleatom/atom/BuildConfig;");
        atomBundle.doesNotContainClass("Lcom/android/tests/singleatom/atom/R;");

        // Check the atom contains expected APK contents.
        ApkSubject atom =
                assertThatApk(sProject.getSubproject("instantApp").getAtom("atom", "release"));
        atom.contains("AndroidManifest.xml");
        atom.contains("resources.arsc");

        // Check that the output bundle file contains the one atom.
        ZipFileSubject outputPackage =
                assertThatZip(sProject.getSubproject("instantApp").getInstantAppBundle("release"));
        outputPackage.contains("atom.apk");
    }
}

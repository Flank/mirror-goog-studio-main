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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAtomBundle;

import com.android.build.gradle.integration.common.category.SmokeTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.AtomBundleSubject;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.ide.common.process.ProcessException;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Basic instantApp test with a single atom.
 */
@Category(SmokeTests.class)
public class SingleAtomTest {
    @ClassRule
    public static GradleTestProject sProject = GradleTestProject.builder()
            .fromTestProject("singleAtom")
            .withoutNdk()
            .create();

    @BeforeClass
    public static void setUp() {
        // b.android.com/227451
        AssumeUtil.assumeResolveDependencyOnConfiguration();
    }

    @AfterClass
    public static void cleanUp() {
        sProject = null;
    }

    @Test
    public void build() throws IOException, ProcessException {
        sProject.execute("clean");
        sProject.execute(":instantApp:assembleRelease");

        // Tests that the BuildConfig and R class are generated in the proper package.
        AtomBundleSubject atomBundle =
                assertThatAtomBundle(sProject.getSubproject("atom").getAtomBundle("release"));
        atomBundle.containsClass("Lcom/android/tests/singleatom/atom/BuildConfig;");
        atomBundle.containsClass("Lcom/android/tests/singleatom/atom/R;");
        atomBundle.doesNotContainClass("Lcom/android/tests/singleatom/BuildConfig;");
        atomBundle.doesNotContainClass("Lcom/android/tests/singleatom/R;");
    }
}
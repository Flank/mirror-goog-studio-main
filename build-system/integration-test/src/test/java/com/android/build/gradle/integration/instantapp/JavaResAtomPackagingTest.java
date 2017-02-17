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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.utils.FileUtils;
import java.io.File;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Tests atoms with Java Resources are packaged properly. */
public class JavaResAtomPackagingTest {
    @ClassRule
    public static GradleTestProject sProject =
            GradleTestProject.builder().fromTestProject("singleAtom").withoutNdk().create();

    @BeforeClass
    public static void setUp() throws Exception {
        GradleTestProject atomProject = sProject.getSubproject(":atom");
        File atomAssetDir =
                FileUtils.join(atomProject.getTestDir(), "src", "main", "resources", "com", "foo");
        FileUtils.mkdirs(atomAssetDir);
        FileUtils.createFile(new File(atomAssetDir, "atom.txt"), "atom:abcd");
    }

    @AfterClass
    public static void cleanUp() {
        sProject = null;
    }

    @Test
    public void javaResArePresent() throws Exception {
        sProject.execute("clean", ":instantApp:assembleRelease");

        TruthHelper.assertThat(sProject.getSubproject(":atom").getAtomBundle("release"))
                .containsJavaResourceWithContent("com/foo/atom.txt", "atom:abcd");

        assertThatApk(sProject.getSubproject(":instantApp").getAtom("atom", "release"))
                .containsJavaResourceWithContent("com/foo/atom.txt", "atom:abcd");
    }
}

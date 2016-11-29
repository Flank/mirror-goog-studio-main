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

package com.android.build.gradle.integration.databinding;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAtomBundle;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.truth.AtomBundleSubject;
import com.android.ide.common.process.ProcessException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** DataBinding tests for atom projects. */
@RunWith(FilterableParameterized.class)
public class DataBindingAtomTest {

    @Parameterized.Parameters(name = "withoutAdapters={0}")
    public static Collection<Object[]> getParameters() {
        List<Object[]> options = new ArrayList<>();
        options.add(new Object[] {true});
        options.add(new Object[] {false});
        return options;
    }

    private final boolean mWithoutAdapters;
    private final String mBuildFile;

    public DataBindingAtomTest(boolean withoutAdapters) {
        mWithoutAdapters = withoutAdapters;
        String options = "build.atom";
        if (withoutAdapters) {
            options += "-withoutadapters";
        }
        project = GradleTestProject.builder().fromTestProject("databinding").create();
        mBuildFile = options + ".gradle";
    }

    @Rule public final GradleTestProject project;

    @Test
    public void checkDexContainsDataBindingClasses() throws IOException, ProcessException {
        project.setBuildFile(mBuildFile);
        project.execute("assembleDebug");

        final GradleBuildResult buildResult = project.getBuildResult();
        assertThat(buildResult.getTask(":dataBindingProcessLayoutsDebugAtom")).wasExecuted();

        AtomBundleSubject atombundle = assertThatAtomBundle(project.getAtomBundle("debug"));
        atombundle.containsClass("Landroid/databinding/testapp/databinding/ActivityMainBinding;");
        atombundle.containsClass("Landroid/databinding/DataBindingComponent;");
        if (mWithoutAdapters) {
            atombundle.doesNotContainClass("Landroid/databinding/adapters/Converters;");
        } else {
            atombundle.containsClass("Landroid/databinding/adapters/Converters;");
        }
    }
}

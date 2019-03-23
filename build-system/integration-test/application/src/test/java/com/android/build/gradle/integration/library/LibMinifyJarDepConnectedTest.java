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

package com.android.build.gradle.integration.library;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.internal.scope.CodeShrinker;
import com.android.build.gradle.options.OptionalBooleanOption;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(FilterableParameterized.class)
public class LibMinifyJarDepConnectedTest {

    @Parameterized.Parameters(name = "codeShrinker = {0}")
    public static CodeShrinker[] getShrinkers() {
        return new CodeShrinker[] {CodeShrinker.PROGUARD, CodeShrinker.R8};
    }

    @Parameterized.Parameter public CodeShrinker codeShrinker;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("libMinifyJarDep").create();

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() throws IOException, InterruptedException {
        project.executor()
                .with(OptionalBooleanOption.ENABLE_R8, codeShrinker == CodeShrinker.R8)
                .executeConnectedCheck();
    }
}

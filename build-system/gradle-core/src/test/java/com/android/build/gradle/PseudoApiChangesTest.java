/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle;

import com.android.testutils.ApiTester;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.google.common.reflect.ClassPath;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class PseudoApiChangesTest {

    private static final URL API_LIST_URL =
            Resources.getResource(PseudoApiChangesTest.class, "pseudo-api.txt");

    @Test
    public void stableImplementationClassesTest() throws IOException {
        getApiTester().checkApiElements();
    }

    static ApiTester getApiTester() throws IOException {

        ImmutableSet<ClassPath.ClassInfo> allClasses =
                ClassPath.from(BaseExtension.class.getClassLoader())
                        .getTopLevelClasses("com.android.build.gradle");

        List<ClassPath.ClassInfo> classes =
                allClasses
                        .stream()
                        .filter(
                                classInfo ->
                                        !classInfo.getSimpleName().endsWith("Test")
                                                && !classInfo
                                                        .getSimpleName()
                                                        .equals("StableApiUpdater"))
                        .collect(Collectors.toList());

        return new ApiTester(
                "The Android Gradle Plugin internal implementation classes.",
                classes,
                ApiTester.Filter.ALL,
                "The internal implementation classes"
                        + " have changed, either revert "
                        + "the change or re-run DslExtensionClassStableApiUpdater.main[] from the IDE "
                        + "to update the API file.\n"
                        + "DslExtensionClassStableApiUpdater will apply the following changes if run:\n",
                API_LIST_URL);
    }
}

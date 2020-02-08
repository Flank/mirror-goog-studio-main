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

package com.android.build.api;


import com.android.annotations.NonNull;
import com.android.build.api.transform.Transform;
import com.android.testutils.ApiTester;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.google.common.reflect.ClassPath;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * Test that tries to ensure that our public API remains stable.
 */
public class StableApiTest {

    private static final URL STABLE_API_URL =
            Resources.getResource(StableApiTest.class, "stable-api.txt");

    private static final URL INCUBATING_API_URL =
            Resources.getResource(StableApiTest.class, "incubating-api.txt");

    @Test
    public void stableApiElements() throws Exception {
        getStableApiTester().checkApiElements();
    }

    @Test
    public void incubatingApiElements() throws Exception {
        getIncubatingApiTester().checkApiElements();
    }

    protected static ApiTester getStableApiTester() throws IOException {
        return getApiTester(ApiTester.Filter.STABLE_ONLY, STABLE_API_URL);

    }

    protected static ApiTester getIncubatingApiTester() throws IOException {
        return getApiTester(
                ApiTester.Filter.INCUBATING_ONLY, INCUBATING_API_URL, ApiTester.Flag.OMIT_HASH);
    }

    private static ApiTester getApiTester(
            @NonNull ApiTester.Filter filter,
            @NonNull URL expectedFileUrl,
            @NonNull ApiTester.Flag... flags)
            throws IOException {

        String type = filter == ApiTester.Filter.STABLE_ONLY ? "Stable" : "Incubating";
        ImmutableSet<ClassPath.ClassInfo> allClasses =
                ClassPath.from(Transform.class.getClassLoader())
                        .getTopLevelClassesRecursive("com.android.build.api");

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
                type + " Android Gradle Plugin API.",
                classes,
                filter,
                "The "
                        + type
                        + " API has changed, either revert "
                        + "the api change or re-run StableApiUpdater.main[] from the IDE "
                        + "to update the API file.\n"
                        + "StableApiUpdater will apply the following changes if run:\n",
                expectedFileUrl,
                flags);
    }
}

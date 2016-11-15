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
package com.android.tools.bazel;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

public class StudioConfiguration implements Configuration {

    @Override
    public String nameRule(String rel, String name) {
        String prefix = "";
        if (rel.startsWith("tools/idea")) {
            prefix = "idea.";
        } else if (rel.startsWith("tools/base")) {
            prefix = "studio.";
        } else if (rel.startsWith("tools/data-binding")) {
            prefix = "studio.";
            if (name.startsWith("db-")) {
                name = name.replace("db-", "");
            }
        }

        return prefix + name;
    }

    @Override
    public String mapImportJar(String jar) {
        switch (jar) {
            case "out/studio/grpc-java/jarjar/studio-profiler-grpc-1.0-jarjar.jar":
                return "//tools/base/profiler:studio-profiler-grpc-1.0-jarjar";
            default:
                return null;
        }
    }

    @Override
    public List<String> getAdditionalImports() {
        return ImmutableList.of();
    }

    @Override
    public List<String> getLabelsToExport() {
        return ImmutableList.of(
            "//tools/adt/idea:uitest-framework",
            "//tools/adt/idea/android-gradle-jps",
            "//tools/vendor/google/android-apk");
    }

    @Override
    public Map<String, String> getCopySpec() {
        return ImmutableMap.of(
                "tools/BUILD", "tools/base/bazel/tools.idea.BUILD"
        );
    }
}

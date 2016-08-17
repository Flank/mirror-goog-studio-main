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

import com.android.tools.bazel.model.Workspace;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

public class StudioConfiguration implements Configuration {

    @Override
    public void configureWorkspace(Workspace bazel) {
        // Create packages in order of priority
        bazel.createPackagesInDirectory("prebuilts/studio");
        bazel.createPackage("prebuilts/tools/common/m2");
        bazel.createPackagesInDirectory("prebuilts/tools/common");
        bazel.createPackage("prebuilts");
        bazel.createPackage("tools/analytics-library");
        bazel.createPackage("tools/adt/idea/adt-ui");
        bazel.createPackage("tools/adt/idea");
        bazel.createPackage("tools/data-binding");

        // Ideally we'd do this, but we repo copy it instead
        // bazel.createPackage("tools/idea");
        bazel.createPackage("tools/sherpa");
        bazel.createPackage("tools/studio/google/cloud/tools");
        bazel.createPackage("tools/studio/google/cloud/testing");
        bazel.createPackagesInDirectory("tools/studio/google");
        bazel.createPackage("tools/vendor/google");
        bazel.createPackage("tools/vendor/google3/blaze");
        bazel.createPackage("tools/vendor/intellij/cidr");
        bazel.createPackagesInDirectory("tools/base");
        bazel.createPackage("tools");

        // TODO Fix this mapping of a prebuilt jar, to be built with bazel.
        bazel.createPackage("out/studio");
    }

    @Override
    public String nameRule(String rel, String name) {
        return rel.startsWith("tools/idea") ? "idea." + name : name;
    }

    @Override
    public String mapImportJar(String jar) {
        if (jar.equals("tools/vendor/google3/blaze/third_party/trickle/trickle-0.6.1.jar")) {
            return "tools/vendor/google3/blaze/third_party:trickle";
        } else if (jar.equals("tools/vendor/google3/blaze/blaze-base/lib/proto_deps.jar")) {
            return "tools/vendor/google3/blaze/blaze-base:proto_deps";
        } else if (jar.equals("out/studio/grpc-java/jarjar/studio-profiler-grpc-1.0-jarjar.jar")) {
            return "tools/base/bazel:prebuilts/studio-profiler-grpc-1.0-jarjar";
        } else {
            return null;
        }
    }

    @Override
    public List<String> getAdditionalImports() {
        return ImmutableList.of("prebuilts/tools/common/m2:repository/org/codehaus/groovy/groovy-all/2.3.6/groovy-all-2.3.6");
    }

    @Override
    public List<String> getLabelsToExport() {
        return ImmutableList.of("tools/adt/idea:android");
    }

    @Override
    public Map<String, String> getCopySpec() {
        return ImmutableMap.of(
                "tools/BUILD", "tools/base/bazel/tools.idea.BUILD"
        );
    }

    @Override
    public Map<String, List<String>> getTestData() {
        return ImmutableMap.of(
                "android", ImmutableList.of("android/testData/**/*", "designer/testData/**/*")
        );
    }

    @Override
    public Map<String, String> getTestTimeout() {
        return ImmutableMap.of("android", "long");
    }

    @Override
    public Map<String, String> getTestClass() {
        return ImmutableMap.of("android", "com.android.tools.idea.IdeaTestSuite");
    }
}

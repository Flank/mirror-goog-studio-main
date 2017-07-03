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

import com.android.tools.bazel.model.BazelRule;
import com.google.common.collect.ImmutableList;

import java.util.List;

public class StudioConfiguration implements Configuration {

    @Override
    public String nameRule(String pkg, String rel, String name) {
        String prefix = "";
        if (rel.startsWith("tools/base")) {
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
    public List<String> getAdditionalImports() {
        return ImmutableList.of();
    }

    @Override
    public boolean shouldSuppress(BazelRule rule) {
        return rule.getLabel().startsWith("//prebuilts/tools/common/m2/repository/")
            || rule.getLabel().startsWith("//tools/vendor/google3/blaze/")
            || rule.getName().endsWith("devkit")  // Kotlin compilation fails
            || rule.getName().endsWith("devkit-tests")  // depends on devkit
            || rule.getName().endsWith("community-main")  // big nasty not needed module
            || rule.getName().endsWith("community-main-tests")  // depends on community-main
            || rule.getName().endsWith("android-uitests")  // depends on community-main
            || rule.getName().endsWith("lldb-integration-tests");  // depends on community-main
    }
}

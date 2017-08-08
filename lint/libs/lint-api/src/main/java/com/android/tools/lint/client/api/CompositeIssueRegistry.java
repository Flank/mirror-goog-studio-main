/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.lint.client.api;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Issue;
import com.google.common.collect.Lists;
import java.util.List;

/**
 * Registry which merges many issue registries into one, and presents a unified list
 * of issues.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
class CompositeIssueRegistry extends IssueRegistry {
    private final List<IssueRegistry> registries;
    private List<Issue> issues;

    public CompositeIssueRegistry(@NonNull List<IssueRegistry> registries) {
        this.registries = registries;
    }

    @NonNull
    @Override
    public List<Issue> getIssues() {
        if (issues == null) {
            int capacity = 0;
            for (IssueRegistry registry : registries) {
                capacity += registry.getIssues().size();
            }
            List<Issue> issues = Lists.newArrayListWithExpectedSize(capacity);
            for (IssueRegistry registry : registries) {
                issues.addAll(registry.getIssues());
            }
            this.issues = issues;
        }

        return issues;
    }

    @Override
    public boolean isUpToDate() {
        for (IssueRegistry registry : registries) {
            if (!registry.isUpToDate()) {
                return false;
            }
        }

        return true;
    }
}
